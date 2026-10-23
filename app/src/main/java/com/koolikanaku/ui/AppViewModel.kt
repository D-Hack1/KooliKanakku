package com.koolikanaku.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.koolikanaku.ai.analyzeStain
import com.koolikanaku.ai.detectObjects
import com.koolikanaku.ai.initDetector
import com.koolikanaku.ai.measureRoom
import com.koolikanaku.capture.JobStorage
import com.koolikanaku.capture.PhotoStore
import com.koolikanaku.capture.SavedState
import com.koolikanaku.domain.AnalyzeRoom
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.ClutterLevel
import com.koolikanaku.domain.Constants
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.Job
import com.koolikanaku.domain.JobQuote
import com.koolikanaku.domain.LengthUnit
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.PricingEngine
import com.koolikanaku.domain.RoomDimensions
import com.koolikanaku.domain.RoomEntry
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.SizePreset
import com.koolikanaku.domain.StainResult
import com.koolikanaku.domain.SizeSource
import com.koolikanaku.domain.TaskCatalog
import com.koolikanaku.domain.TaskMapper
import com.koolikanaku.domain.TaskSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Drives the real screens. [analyzeRoom] runs the on-device pipeline (YOLO detection, grime
 * statistics, and camera measurement when the measure shots carry sensor angles); until it
 * has run, tasks are the room-type template (TaskMapper with an empty merged-count map).
 */
class AppViewModel : ViewModel() {

    // ---- Setup ----
    var mode by mutableStateOf(AppMode.WORKER_ESTIMATE)
        private set
    var language by mutableStateOf(AppLanguage.EN)
        private set
    var rateText by mutableStateOf("250")
        private set
    var paceFactor by mutableStateOf(1.0)
        private set
    var heightCm by mutableStateOf("")
        private set
    var darkTheme by mutableStateOf(false)
        private set

    fun changeMode(value: AppMode) { mode = value; persist() }
    fun changeLanguage(value: AppLanguage) { language = value; persist() }
    fun changeRateText(value: String) { rateText = value; persist() }
    fun changePaceFactor(value: Double) { paceFactor = value; persist() }
    fun changeHeightCm(value: String) { heightCm = value; persist() }
    fun changeDarkTheme(value: Boolean) { darkTheme = value; persist() }
    fun toggleDarkTheme() { darkTheme = !darkTheme; persist() }

    // ---- Job state ----
    // Mutating these saves the job to disk (see persist()), so a killed process can resume.
    private val jobState = mutableStateOf<Job?>(null)
    var job: Job?
        get() = jobState.value
        private set(value) { jobState.value = value; persist() }
    private val currentRoomIdState = mutableStateOf<String?>(null)
    var currentRoomId: String?
        get() = currentRoomIdState.value
        private set(value) { currentRoomIdState.value = value; persist() }
    private val quoteState = mutableStateOf<JobQuote?>(null)
    var quote: JobQuote?
        get() = quoteState.value
        private set(value) { quoteState.value = value; persist() }

    // ---- Room Capture transient UI state (per current room) ----
    var sizeMethod by mutableStateOf(SizeMethod.ENTER)
        private set
    var enterLengthFt by mutableStateOf("")
        private set
    var enterWidthFt by mutableStateOf("")
        private set
    var presetIndex by mutableStateOf<Int?>(null)
        private set
    var captureError by mutableStateOf<String?>(null)
        private set
    var analysisError by mutableStateOf<String?>(null)
        private set
    /** Grime result of each close-up photo (by photo id) from the last analysis; the room keeps only the worst. */
    private val toughScoresState = mutableStateOf<Map<String, StainResult>>(emptyMap())
    var toughScores: Map<String, StainResult>
        get() = toughScoresState.value
        private set(value) { toughScoresState.value = value; persist() }

    // ---- Saving and resuming the in-progress job ----
    private var storageContext: Context? = null
    private var restoring = false
    private val saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Loads a saved job (once) and starts saving every change. Call before the first screen reads state. */
    fun attachStorage(context: Context) {
        if (storageContext != null) return
        val appContext = context.applicationContext
        storageContext = appContext
        if (job != null) return   // the in-memory state is newer than whatever is on disk
        val saved = JobStorage.load(appContext) ?: return
        restoring = true
        try {
            mode = saved.mode
            language = runCatching { AppLanguage.valueOf(saved.language) }.getOrDefault(AppLanguage.EN)
            rateText = saved.rateText
            paceFactor = saved.paceFactor
            heightCm = saved.heightCm
            darkTheme = saved.darkTheme
            sizeMethod = runCatching { SizeMethod.valueOf(saved.sizeMethod) }.getOrDefault(SizeMethod.ENTER)
            enterLengthFt = saved.enterLengthFt
            enterWidthFt = saved.enterWidthFt
            presetIndex = saved.presetIndex
            toughScores = saved.toughScores
            job = saved.job
            currentRoomId = saved.currentRoomId
            quote = if (saved.finished && saved.job != null && allRoomsHaveSize()) PricingEngine.quoteJob(saved.job) else null
        } finally {
            restoring = false
        }
    }

    /** True when a saved job with at least one room is loaded, so Setup can offer "Resume". */
    fun hasResumableJob(): Boolean = job?.rooms?.isNotEmpty() == true

    private fun persist() {
        val context = storageContext ?: return
        if (restoring) return
        val json = JobStorage.toJson(
            SavedState(
                job = job, currentRoomId = currentRoomId, finished = quote != null, toughScores = toughScores,
                mode = mode, language = language.name, rateText = rateText, paceFactor = paceFactor, heightCm = heightCm,
                darkTheme = darkTheme, sizeMethod = sizeMethod.name, enterLengthFt = enterLengthFt, enterWidthFt = enterWidthFt,
                presetIndex = presetIndex
            )
        )
        saveExecutor.execute { runCatching { JobStorage.save(context, json) } }
    }

    /** Blocks until every queued save has reached disk (used by tests). */
    fun awaitPendingSave() { saveExecutor.submit {}.get() }

    override fun onCleared() {
        saveExecutor.shutdown()
    }

    val presetOptions = listOf(SizePreset.SMALL, SizePreset.MEDIUM, SizePreset.LARGE)

    fun changeSizeMethod(value: SizeMethod) { sizeMethod = value; persist() }
    fun changeEnterLengthFt(value: String) { enterLengthFt = value; persist() }
    fun changeEnterWidthFt(value: String) { enterWidthFt = value; persist() }
    fun changePresetIndex(value: Int) { presetIndex = value; persist() }
    fun clearCaptureError() { captureError = null }

    fun startJob() {
        val rate = rateText.toDoubleOrNull()
            ?: if (mode == AppMode.OWNER_QUOTE) Constants.DEFAULT_MARKET_RATE_INR_PER_HR else Constants.DEFAULT_WORKER_RATE_INR_PER_HR
        val height = heightCm.toFloatOrNull()
        quote = null
        currentRoomId = null
        toughScores = emptyMap()
        job = Job(
            id = UUID.randomUUID().toString(),
            mode = mode,
            createdIso = nowIso(),
            hourlyRateInr = rate,
            paceFactor = if (mode == AppMode.OWNER_QUOTE) 1.0 else paceFactor,
            holdHeightM = if (height != null) height * Constants.HOLD_HEIGHT_FACTOR / 100f else Constants.DEFAULT_HOLD_HEIGHT_M,
            rooms = emptyList()
        )
    }

    /** Returns the new room's id and makes it current. */
    fun addRoom(): String {
        val newRoom = RoomEntry(id = UUID.randomUUID().toString(), roomType = RoomType.OTHER)
        job = job?.copy(rooms = job!!.rooms + newRoom)
        currentRoomId = newRoom.id
        resetCaptureState()
        return newRoom.id
    }

    fun selectRoom(roomId: String) {
        currentRoomId = roomId
        resetCaptureState()
    }

    private fun resetCaptureState() {
        sizeMethod = SizeMethod.ENTER
        enterLengthFt = ""
        enterWidthFt = ""
        presetIndex = null
        captureError = null
    }

    private fun updateRoom(roomId: String, transform: (RoomEntry) -> RoomEntry) {
        val currentJob = job ?: return
        job = currentJob.copy(rooms = currentJob.rooms.map { if (it.id == roomId) transform(it) else it })
    }

    fun currentRoom(): RoomEntry? = job?.rooms?.find { it.id == currentRoomId }

    fun addPhoto(roomId: String, photo: EvidencePhoto) {
        // A retaken measure shot (or after photo) replaces the old one: the measurer reads one shot per side, and a room has one after photo.
        val single = photo.role == PhotoRole.MEASURE_LENGTH || photo.role == PhotoRole.MEASURE_WIDTH || photo.role == PhotoRole.AFTER
        updateRoom(roomId) { room ->
            room.copy(photos = (if (single) room.photos.filter { it.role != photo.role } else room.photos) + photo)
        }
    }

    fun onCaptureError(message: String) {
        captureError = message
    }

    /** Builds the RoomDimensions for the current size method, called when leaving Room Capture. */
    fun resolveDimensions(): RoomDimensions? {
        return when (sizeMethod) {
            SizeMethod.PRESET -> {
                val index = presetIndex ?: return null
                val room = currentRoom() ?: return null
                val preset = presetOptions[index]
                val areaSqFt = Constants.PRESET_AREA_SQ_FT[room.roomType]?.get(preset) ?: return null
                RoomDimensions(length = 0.0, width = 0.0, unit = LengthUnit.FEET, computedAreaSqFt = areaSqFt, source = SizeSource.PRESET)
            }
            SizeMethod.ENTER -> {
                val length = enterLengthFt.toDoubleOrNull() ?: return null
                val width = enterWidthFt.toDoubleOrNull() ?: return null
                Constants.computeDimensions(length, width, LengthUnit.FEET, SizeSource.MANUAL)
            }
            SizeMethod.MEASURE -> null // Nived's RoomMeasurer isn't wired in; fall through to manual entry on Confirm
        }
    }

    fun goToRoomConfirmFromCapture(roomId: String) {
        val dims = resolveDimensions()
        updateRoom(roomId) { room ->
            val withDims = if (dims != null) room.copy(dimensions = dims) else room
            val tasks = TaskMapper.buildTasksFromAnalysis(
                roomId = roomId,
                roomType = withDims.roomType,
                mergedCounts = emptyMap(),
                existingTasks = withDims.tasks
            )
            withDims.copy(tasks = tasks)
        }
    }

    /**
     * Runs the on-device analysis for a room (detection, grime, measurement, merge, tasks).
     * On failure the room keeps its template tasks and the error is kept in [analysisError].
     */
    suspend fun analyzeRoom(context: Context, roomId: String) {
        val room = job?.rooms?.find { it.id == roomId } ?: return
        analysisError = null
        val scores = mutableMapOf<String, StainResult>()
        val appContext = context.applicationContext
        val detectorReady = initDetector(appContext)   // retries if the launch-time load failed
        var detectionProblem: String? = null
        val useCase = AnalyzeRoom(
            loadBitmap = { photo -> withContext(Dispatchers.IO) { PhotoStore.getDownscaledBitmap(appContext, photo.uri) } },
            // A detector failure must not lose grime or measurement results: fall back to no detections.
            detectObjectsFn = { bitmap ->
                if (!detectorReady) {
                    detectionProblem = "the object detector could not be loaded"
                    emptyList()
                } else try {
                    detectObjects(bitmap)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Detection failed", e)
                    detectionProblem = "object detection failed (${e.message ?: e.javaClass.simpleName})"
                    emptyList()
                }
            },
            analyzeStainFn = ::analyzeStain,
            measureRoomFn = ::measureRoom,
            dummyFallback = false,
            onStainResult = { photo, result -> scores[photo.id] = result }
        )
        try {
            val analyzed = useCase(room)
            toughScores = toughScores + scores
            updateRoom(roomId) { current ->
                // Keep what the user entered while analysis ran; adopt the suggested type only if none was chosen.
                val type = if (current.roomType == RoomType.OTHER) analyzed.suggestedRoomType ?: current.roomType else current.roomType
                analyzed.copy(roomType = type, dimensions = analyzed.dimensions ?: current.dimensions)
            }
            analysisError = detectionProblem
        } catch (e: Exception) {
            Log.e("AppViewModel", "Room analysis failed", e)
            analysisError = e.message ?: e.javaClass.simpleName
        }
    }

    fun setRoomType(roomId: String, type: RoomType) {
        updateRoom(roomId) { room ->
            val regenerated = TaskMapper.updateRoomTypeTemplates(roomId, type, room.tasks)
            room.copy(roomType = type, tasks = regenerated)
        }
    }

    fun setManualSizeSqFt(roomId: String, sqFt: Double) {
        updateRoom(roomId) { room ->
            room.copy(
                dimensions = RoomDimensions(
                    length = 0.0, width = 0.0, unit = LengthUnit.FEET,
                    computedAreaSqFt = sqFt,
                    source = room.dimensions?.source ?: SizeSource.MANUAL
                )
            )
        }
    }

    fun overrideSeverity(roomId: String, level: SeverityLevel?) {
        updateRoom(roomId) { it.copy(severityOverride = level) }
    }

    fun toggleTask(roomId: String, taskId: String) {
        updateRoom(roomId) { room ->
            room.copy(tasks = room.tasks.map { if (it.id == taskId) it.copy(isSelected = !it.isSelected) else it })
        }
    }

    fun selectAllTasks(roomId: String, selected: Boolean) {
        updateRoom(roomId) { room ->
            room.copy(tasks = room.tasks.map { it.copy(isSelected = selected) })
        }
    }

    fun incrementTask(roomId: String, taskId: String) {
        updateRoom(roomId) { room ->
            room.copy(tasks = room.tasks.map { if (it.id == taskId) it.copy(quantity = it.quantity + 1) else it })
        }
    }

    fun decrementTask(roomId: String, taskId: String) {
        updateRoom(roomId) { room ->
            room.copy(tasks = room.tasks.map {
                if (it.id == taskId && it.quantity > 1) it.copy(quantity = it.quantity - 1) else it
            })
        }
    }

    fun addCatalogTask(roomId: String, catalogKey: String) {
        val item = TaskCatalog.getItem(catalogKey) ?: return
        updateRoom(roomId) { room ->
            if (room.tasks.any { it.catalogKey == catalogKey }) return@updateRoom room
            val task = CleaningTask(
                id = "${roomId}_${catalogKey}_manual",
                catalogKey = catalogKey,
                titleEn = item.titleEn,
                titleMl = item.titleMl,
                quantity = 1,
                minutesPerUnit = item.minutesPerUnit,
                materialCostInr = item.materialCostInr,
                isSelected = true,
                source = TaskSource.MANUAL
            )
            room.copy(tasks = room.tasks + task)
        }
    }

    fun saveRoom() {
        currentRoomId = null
    }

    /** True when every room has a usable size. A room without one would be billed for tasks only. */
    fun allRoomsHaveSize(): Boolean {
        val rooms = job?.rooms ?: return false
        return rooms.isNotEmpty() && rooms.all { (it.dimensions?.computedAreaSqFt ?: 0.0) > 0.0 }
    }

    /** Returns false (and computes nothing) while any room is missing its size. */
    fun finishJob(): Boolean {
        val currentJob = job ?: return false
        if (!allRoomsHaveSize()) return false
        quote = PricingEngine.quoteJob(currentJob)
        return true
    }

    private fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}

/** How a Room Capture photo count should be worded for the WIDE / TOUGHEST step hints. */
fun RoomEntry.widePhotoCount(): Int = photos.count { it.role == PhotoRole.WIDE }
fun RoomEntry.toughPhotoCount(): Int = photos.count { it.role == PhotoRole.TOUGHEST }
