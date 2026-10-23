package com.koolikanaku.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.koolikanaku.ai.analyzeStain
import com.koolikanaku.ai.detectObjects
import com.koolikanaku.ai.measureRoom
import com.koolikanaku.capture.PhotoStore
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

    fun changeMode(value: AppMode) { mode = value }
    fun changeLanguage(value: AppLanguage) { language = value }
    fun changeRateText(value: String) { rateText = value }
    fun changePaceFactor(value: Double) { paceFactor = value }
    fun changeHeightCm(value: String) { heightCm = value }
    fun changeDarkTheme(value: Boolean) { darkTheme = value }
    fun toggleDarkTheme() { darkTheme = !darkTheme }

    // ---- Job state ----
    var job by mutableStateOf<Job?>(null)
        private set
    var currentRoomId by mutableStateOf<String?>(null)
        private set
    var quote by mutableStateOf<JobQuote?>(null)
        private set

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
    var toughScores by mutableStateOf<Map<String, StainResult>>(emptyMap())
        private set

    val presetOptions = listOf(SizePreset.SMALL, SizePreset.MEDIUM, SizePreset.LARGE)

    fun changeSizeMethod(value: SizeMethod) { sizeMethod = value }
    fun changeEnterLengthFt(value: String) { enterLengthFt = value }
    fun changeEnterWidthFt(value: String) { enterWidthFt = value }
    fun changePresetIndex(value: Int) { presetIndex = value }
    fun clearCaptureError() { captureError = null }

    fun startJob() {
        val rate = rateText.toDoubleOrNull()
            ?: if (mode == AppMode.OWNER_QUOTE) Constants.DEFAULT_MARKET_RATE_INR_PER_HR else Constants.DEFAULT_WORKER_RATE_INR_PER_HR
        val height = heightCm.toFloatOrNull()
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
        // A retaken measure shot replaces the old one; the measurer reads exactly one shot per side.
        val single = photo.role == PhotoRole.MEASURE_LENGTH || photo.role == PhotoRole.MEASURE_WIDTH
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
        val useCase = AnalyzeRoom(
            loadBitmap = { photo ->
                withContext(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    val bmp = PhotoStore.getDownscaledBitmap(appContext, photo.uri)
                    Log.d("AnalyzeFlow", "loadBitmap(${photo.role}) -> ${if (bmp != null) "${bmp.width}x${bmp.height}" else "null"} in ${System.currentTimeMillis() - t0}ms")
                    bmp
                }
            },
            detectObjectsFn = { bmp ->
                val t0 = System.currentTimeMillis()
                Log.d("AnalyzeFlow", "detectObjects starting on ${bmp.width}x${bmp.height}")
                val result = detectObjects(bmp)
                Log.d("AnalyzeFlow", "detectObjects -> ${result.size} raw detections in ${System.currentTimeMillis() - t0}ms: ${result.map { it.label }}")
                result
            },
            analyzeStainFn = { bmp ->
                val t0 = System.currentTimeMillis()
                val result = analyzeStain(bmp)
                Log.d("AnalyzeFlow", "analyzeStain -> $result in ${System.currentTimeMillis() - t0}ms")
                result
            },
            measureRoomFn = ::measureRoom,
            dummyFallback = false,
            onStainResult = { photo, result -> scores[photo.id] = result }
        )
        try {
            val analyzed = useCase(room)
            updateRoom(roomId) { current ->
                // Keep what the user entered while analysis ran; adopt the suggested type only if none was chosen.
                val type = if (current.roomType == RoomType.OTHER) analyzed.suggestedRoomType ?: current.roomType else current.roomType
                analyzed.copy(roomType = type, dimensions = analyzed.dimensions ?: current.dimensions)
            }
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

    fun finishJob() {
        val currentJob = job ?: return
        quote = PricingEngine.quoteJob(currentJob)
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
