package com.koolikanaku.ui.navigation

import kotlinx.coroutines.launch
import com.koolikanaku.ui.screens.BeforeAfterUi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.koolikanaku.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.koolikanaku.capture.rememberCameraFallbackLauncher
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.PricingEngine
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.TaskSource
import com.koolikanaku.export.PdfExporter
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.AppViewModel
import com.koolikanaku.ui.CaptureTarget
import com.koolikanaku.ui.SizeMethod
import com.koolikanaku.ui.components.CatalogEntry
import com.koolikanaku.ui.displayName
import com.koolikanaku.ui.screens.AnalysisStageUi
import com.koolikanaku.ui.screens.AnalyzingScreen
import com.koolikanaku.ui.screens.ConfirmPhoto
import com.koolikanaku.ui.screens.BillMathRowUi
import com.koolikanaku.ui.screens.BillPhotoGroupUi
import com.koolikanaku.ui.screens.BillPhotoUi
import com.koolikanaku.ui.screens.BillRoomUi
import com.koolikanaku.ui.screens.BillScreen
import com.koolikanaku.ui.screens.BillTaskUi
import com.koolikanaku.ui.screens.ConfirmPhotoTab
import com.koolikanaku.ui.screens.JobHubScreen
import com.koolikanaku.ui.screens.RoomCaptureScreen
import com.koolikanaku.ui.screens.RoomConfirmScreen
import com.koolikanaku.ui.screens.RoomSummaryUi
import com.koolikanaku.ui.screens.SetupScreen
import com.koolikanaku.ui.titleFor
import com.koolikanaku.ui.widePhotoCount
import com.koolikanaku.ui.toughPhotoCount
import kotlinx.coroutines.delay
import java.util.Locale

private fun formatHours(minutes: Double): String = String.format(Locale.US, "%.1f", minutes / 60.0)

object Routes {
    const val SETUP = "setup"
    const val JOB_HUB = "job_hub"
    const val ROOM_CAPTURE = "room_capture/{roomId}"
    fun roomCapture(roomId: String) = "room_capture/$roomId"
    const val ANALYZING = "analyzing/{roomId}"
    fun analyzing(roomId: String) = "analyzing/$roomId"
    const val ROOM_CONFIRM = "room_confirm/{roomId}"
    fun roomConfirm(roomId: String) = "room_confirm/$roomId"
    const val BILL = "bill"
    const val VERIFY = "verify"
    const val CONNECT_AUTH = "connect_auth"
    const val CONNECT_HOME = "connect_home"
    const val CONNECT_BOOKING = "connect_booking/{bookingId}"
    fun connectBooking(id: String) = "connect_booking/$id"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    vm: AppViewModel = viewModel()
) {
    val storageContext = LocalContext.current
    remember(vm) { vm.attachStorage(storageContext) }   // restore a saved job before any screen reads state
    val connectVm: com.koolikanaku.connect.ui.ConnectViewModel = viewModel()
    val connectScope = androidx.compose.runtime.rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Routes.SETUP,
        enterTransition = { slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(260)) }
    ) {

        composable(Routes.SETUP) {
            SetupScreen(
                mode = vm.mode,
                onModeChange = vm::changeMode,
                language = vm.language,
                onLanguageChange = vm::changeLanguage,
                rateText = vm.rateText,
                onRateChange = vm::changeRateText,
                paceFactor = vm.paceFactor,
                onPaceChange = vm::changePaceFactor,
                heightCm = vm.heightCm,
                onHeightChange = vm::changeHeightCm,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme,
                onFindWorker = { navController.navigate(if (connectVm.user != null) Routes.CONNECT_HOME else Routes.CONNECT_AUTH) },
                resumeRoomCount = vm.job?.rooms?.size ?: 0,
                onResumeJob = {
                    navController.navigate(if (vm.quote != null) Routes.BILL else Routes.JOB_HUB)
                },
                onStartJob = {
                    vm.startJob()
                    navController.navigate(Routes.JOB_HUB)
                }
            )
        }

        composable(Routes.JOB_HUB) {
            val job = vm.job
            val rooms = job?.rooms.orEmpty().mapIndexed { index, room ->
                val quote = job?.let { PricingEngine.quoteRoom(room, it) }
                RoomSummaryUi(
                    id = room.id,
                    name = "Room ${index + 1} - ${room.roomType.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }}",
                    metaLine1 = room.dimensions?.let { "${it.computedAreaSqFt.toInt()} sq ft" } ?: "Size not set",
                    metaLine2 = "${room.tasks.count { it.isSelected }} tasks selected",
                    timeText = quote?.let { "${formatHours(it.expectedMinutes.low)}-${formatHours(it.expectedMinutes.high)} hr" } ?: "-",
                    payText = quote?.let { "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}" } ?: "-",
                    analyzed = (room.dimensions?.computedAreaSqFt ?: 0.0) > 0.0 && (room.photoAnalyses.isNotEmpty() || room.stain != null)
                )
            }
            val totalLow = job?.rooms.orEmpty().sumOf { room -> job?.let { PricingEngine.quoteRoom(room, it).priceInr.low } ?: 0.0 }
            val totalHigh = job?.rooms.orEmpty().sumOf { room -> job?.let { PricingEngine.quoteRoom(room, it).priceInr.high } ?: 0.0 }

            JobHubScreen(
                modeLabel = if (job?.mode == AppMode.OWNER_QUOTE) "Owner quote" else "Worker estimate",
                rateLabel = "₹${job?.hourlyRateInr?.toInt() ?: 0}/hr",
                paceLabel = "${job?.paceFactor ?: 1.0}x pace",
                rooms = rooms,
                totalText = "₹${totalLow.toInt()}-₹${totalHigh.toInt()}",
                onAddRoom = {
                    val roomId = vm.addRoom()
                    navController.navigate(Routes.roomCapture(roomId))
                },
                onRoomClick = { roomId ->
                    vm.selectRoom(roomId)
                    navController.navigate(Routes.roomCapture(roomId))
                },
                canFinish = vm.allRoomsHaveSize(),
                onFinish = {
                    if (vm.finishJob()) navController.navigate(Routes.BILL)
                }
            )
        }

        composable(
            Routes.ROOM_CAPTURE,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val room = vm.job?.rooms?.find { it.id == roomId } ?: return@composable
            val context = LocalContext.current

            val onWide = rememberCameraFallbackLauncher(
                role = PhotoRole.WIDE,
                onPhotoCaptured = { vm.addPhoto(roomId, it) },
                onError = { vm.onCaptureError(it) }
            )
            val onTough = rememberCameraFallbackLauncher(
                role = PhotoRole.TOUGHEST,
                onPhotoCaptured = { vm.addPhoto(roomId, it) },
                onError = { vm.onCaptureError(it) }
            )
            var liveMeasureTarget by remember { mutableStateOf<CaptureTarget?>(null) }
            var confirmReanalyze by remember { mutableStateOf(false) }

            fun launchFor(target: CaptureTarget) {
                when (target) {
                    CaptureTarget.WIDE -> onWide()
                    CaptureTarget.TOUGHEST -> onTough()
                    CaptureTarget.LENGTH, CaptureTarget.WIDTH -> liveMeasureTarget = target
                }
            }

            var pendingCaptureTarget by remember { mutableStateOf<CaptureTarget?>(null) }
            val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                val target = pendingCaptureTarget
                pendingCaptureTarget = null
                if (!granted) {
                    vm.onCaptureError("Camera permission is required to take photos")
                } else if (target != null) {
                    launchFor(target)
                }
            }

            fun requestCapture(target: CaptureTarget) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    launchFor(target)
                } else {
                    pendingCaptureTarget = target
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }

            // Measure shots use the live CameraX viewfinder so each photo carries the gravity-sensor angle.
            val liveTarget = liveMeasureTarget
            if (liveTarget != null) {
                androidx.activity.compose.BackHandler { liveMeasureTarget = null }
                com.koolikanaku.capture.CameraCaptureScreen(
                    role = if (liveTarget == CaptureTarget.LENGTH) PhotoRole.MEASURE_LENGTH else PhotoRole.MEASURE_WIDTH,
                    holdHeightM = vm.job?.holdHeightM,
                    onPhotoCaptured = { vm.addPhoto(roomId, it); liveMeasureTarget = null },
                    onError = { vm.onCaptureError(it); liveMeasureTarget = null }
                )
                return@composable
            }

            val widePhotoList = room.photos.filter { it.role == PhotoRole.WIDE }
            val toughPhotoList = room.photos.filter { it.role == PhotoRole.TOUGHEST }
            val wideCount = widePhotoList.size
            val toughCount = toughPhotoList.size
            val lengthTaken = room.photos.any { it.role == PhotoRole.MEASURE_LENGTH }
            val widthTaken = room.photos.any { it.role == PhotoRole.MEASURE_WIDTH }
            val sizeStepDone = when (vm.sizeMethod) {
                SizeMethod.MEASURE -> lengthTaken && widthTaken
                SizeMethod.ENTER -> vm.enterLengthFt.toDoubleOrNull() != null && vm.enterWidthFt.toDoubleOrNull() != null
                SizeMethod.PRESET -> vm.presetIndex != null
            }
            val readyToAnalyze = sizeStepDone && wideCount >= 1 && toughCount >= 1

            if (confirmReanalyze) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmReanalyze = false },
                    title = { androidx.compose.material3.Text(stringResource(R.string.reanalyze_title)) },
                    text = { androidx.compose.material3.Text(stringResource(R.string.reanalyze_body)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            confirmReanalyze = false
                            vm.goToRoomConfirmFromCapture(roomId)
                            navController.navigate(Routes.analyzing(roomId))
                        }) { androidx.compose.material3.Text(stringResource(R.string.reanalyze_confirm)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmReanalyze = false }) {
                            androidx.compose.material3.Text(stringResource(R.string.reanalyze_cancel))
                        }
                    }
                )
            }

            RoomCaptureScreen(
                roomNumber = (vm.job?.rooms?.indexOfFirst { it.id == roomId } ?: 0) + 1,
                sizeMethod = vm.sizeMethod,
                onSizeMethodChange = vm::changeSizeMethod,
                lengthShotTaken = lengthTaken,
                widthShotTaken = widthTaken,
                measureResultText = run {
                    val lengthShot = room.photos.lastOrNull { it.role == PhotoRole.MEASURE_LENGTH }
                    val widthShot = room.photos.lastOrNull { it.role == PhotoRole.MEASURE_WIDTH }
                    if (lengthShot == null || widthShot == null) null else {
                        val result = com.koolikanaku.ai.measureRoom(lengthShot, widthShot)
                        val dims = result.dimensions
                        val warnings = result.warnings.joinToString { it.name.lowercase().replace('_', ' ') }
                        when {
                            dims == null -> "Couldn't measure from these shots - retake them or enter the size instead"
                            else -> "About ${"%.1f".format(dims.length)} x ${"%.1f".format(dims.width)} m (${dims.computedAreaSqFt.toInt()} sq ft)" +
                                if (warnings.isNotEmpty()) " - check: $warnings" else ""
                        }
                    }
                },
                enterLengthFt = vm.enterLengthFt,
                onEnterLengthChange = vm::changeEnterLengthFt,
                enterWidthFt = vm.enterWidthFt,
                onEnterWidthChange = vm::changeEnterWidthFt,
                enterResultText = run {
                    val l = vm.enterLengthFt.toDoubleOrNull()
                    val w = vm.enterWidthFt.toDoubleOrNull()
                    if (l != null && w != null) "${(l * w).toInt()} sq ft" else null
                },
                presetIndex = vm.presetIndex,
                presetLabels = vm.presetOptions.map { it.labelEn to it.labelEn },
                onPresetChange = vm::changePresetIndex,
                widePhotos = widePhotoList,
                toughPhotos = toughPhotoList,
                onDeletePhoto = { photoId -> vm.removePhoto(roomId, photoId) },
                canAddWide = true,
                canAddTough = true,
                readyToAnalyze = readyToAnalyze,
                statusText = if (readyToAnalyze) "Ready to analyze" else "Finish the steps above to continue",
                activeCaptureTarget = null,
                isSteady = true,
                tiltText = "",
                distanceText = "",
                sideText = "",
                onOpenCamera = { target ->
                    requestCapture(target)
                },
                onCloseCamera = { },
                onShutter = { },
                onAnalyzeRoom = {
                    if (room.tasks.any { it.isSelected }) confirmReanalyze = true
                    else {
                        vm.goToRoomConfirmFromCapture(roomId)
                        navController.navigate(Routes.analyzing(roomId))
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.ANALYZING,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            var stageIndex by remember { mutableStateOf(0) }
            val stageLabels = listOf("Detecting objects and grime", "Building checklist", "Ready")
            val analyzeContext = LocalContext.current

            LaunchedEffect(roomId) {
                stageIndex = 0
                android.util.Log.d("AnalyzeFlow", "Starting analyzeRoom for $roomId")
                val result = kotlinx.coroutines.withTimeoutOrNull(25_000) {
                    vm.analyzeRoom(analyzeContext, roomId)
                }
                if (result == null) {
                    android.util.Log.e("AnalyzeFlow", "analyzeRoom timed out after 25s for $roomId")
                } else {
                    android.util.Log.d("AnalyzeFlow", "Finished analyzeRoom for $roomId")
                }
                stageIndex = 1
                delay(200)
                stageIndex = 2
            }

            AnalyzingScreen(
                stages = stageLabels.mapIndexed { i, label ->
                    AnalysisStageUi(label = label, done = i < stageIndex, current = i == stageIndex, durationText = "")
                },
                isDone = stageIndex >= stageLabels.lastIndex,
                errorText = vm.analysisError,
                onReviewRoom = {
                    // Only drop the Analyzing screen itself; keep Room Capture on the stack so the
                    // back button from Confirm can return there to retake photos or change the size.
                    navController.navigate(Routes.roomConfirm(roomId)) {
                        popUpTo(Routes.analyzing(roomId)) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.ROOM_CONFIRM,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val job = vm.job ?: return@composable
            val room = job.rooms.find { it.id == roomId } ?: return@composable
            var photoTab by remember { mutableStateOf(ConfirmPhotoTab.WIDE) }
            var sizeText by remember(roomId) { mutableStateOf(room.dimensions?.computedAreaSqFt?.toInt()?.toString() ?: "") }

            val analysisByPhoto = room.photoAnalyses.associateBy { it.photoId }
            val detectionPhotos = room.photos
                .filter { it.role == PhotoRole.WIDE || it.role == PhotoRole.MEASURE_LENGTH || it.role == PhotoRole.MEASURE_WIDTH }
                .sortedBy { if (it.role == PhotoRole.WIDE) 0 else 1 }
            val confirmWidePhotos = detectionPhotos.map { photo ->
                val analysis = analysisByPhoto[photo.id]
                ConfirmPhoto(
                    uri = photo.uri,
                    detections = analysis?.detections.orEmpty(),
                    imageWidth = analysis?.imageWidth ?: 0,
                    imageHeight = analysis?.imageHeight ?: 0
                )
            }
            val toughPhotosList = room.photos.filter { it.role == PhotoRole.TOUGHEST }
            val worstToughId = toughPhotosList
                .filter { vm.toughScores.containsKey(it.id) }
                .maxWithOrNull(compareBy({ vm.toughScores.getValue(it.id).severity.ordinal }, { vm.toughScores.getValue(it.id).score }))?.id
            val confirmToughPhotos = toughPhotosList.mapIndexed { index, photo ->
                val result = vm.toughScores[photo.id]
                ConfirmPhoto(
                    uri = photo.uri,
                    caption = if (result == null) "Close-up ${index + 1}: not scored"
                    else "Close-up ${index + 1}: ${result.severity.name.lowercase()} (score ${result.score.toInt()})" +
                        if (photo.id == worstToughId && toughPhotosList.size > 1) " - worst, used for the estimate" else "",
                    isWorst = photo.id == worstToughId
                )
            }
            val quote = PricingEngine.quoteRoom(room, job)
            val availableCatalog = com.koolikanaku.domain.TaskCatalog.ITEMS.values
                .filter { item -> room.tasks.none { it.catalogKey == item.key } }
                .map { CatalogEntry(it.key, it.titleEn, it.titleMl, it.minutesPerUnit) }

            RoomConfirmScreen(
                roomNumber = (job.rooms.indexOfFirst { it.id == roomId }) + 1,
                photoTab = photoTab,
                onPhotoTabChange = { photoTab = it },
                wideDetections = emptyList(),
                wideImageWidth = 0,
                wideImageHeight = 0,
                widePhotos = confirmWidePhotos,
                toughPhotos = confirmToughPhotos,
                toughSummaryText = room.stain?.let { "${room.toughPhotoCount()} close-up photo(s) scored. Worst: ${it.severity.name.lowercase()} (score ${it.score.toInt()}) sets the grime level" }
                    ?: "${room.toughPhotoCount()} close-up photo(s). No automatic grime score - set severity below.",
                roomType = room.roomType,
                suggestedRoomType = room.suggestedRoomType,
                onRoomTypeChange = { vm.setRoomType(roomId, it) },
                sizeSqFt = sizeText,
                onSizeChange = { value ->
                    sizeText = value
                    value.toDoubleOrNull()?.let { vm.setManualSizeSqFt(roomId, it) }
                },
                sizeSourceLabel = room.dimensions?.source?.name ?: "MANUAL",
                dimsNote = null,
                sizeProblem = when {
                    (room.dimensions?.computedAreaSqFt ?: 0.0) <= 0.0 -> stringResource(R.string.confirm_size_required)
                    room.dimensions!!.computedAreaSqFt < com.koolikanaku.domain.Constants.MIN_PLAUSIBLE_AREA_SQ_FT ||
                        room.dimensions!!.computedAreaSqFt > com.koolikanaku.domain.Constants.MAX_PLAUSIBLE_AREA_SQ_FT -> stringResource(R.string.confirm_size_implausible)
                    else -> null
                },
                canSave = (room.dimensions?.computedAreaSqFt ?: 0.0) > 0.0,
                grimeScoreText = room.stain?.let {
                    stringResource(
                        R.string.confirm_grime_score,
                        it.score.toInt(),
                        com.koolikanaku.ai.MODERATE_CUTOFF.toInt(),
                        com.koolikanaku.ai.HEAVY_CUTOFF.toInt()
                    )
                },
                severity = room.severityOverride ?: room.stain?.severity ?: SeverityLevel.LIGHT,
                severityOverridden = room.severityOverride != null,
                suggestedSeverityLabel = room.stain?.severity?.name?.lowercase() ?: "N/A",
                onSeverityChange = { vm.overrideSeverity(roomId, it) },
                severityMultiplier = { level -> com.koolikanaku.domain.Constants.GRIME_MULTIPLIER[level] ?: 1.0 },
                clutterLevel = room.clutter,
                looseItemCount = room.photoAnalyses.maxOfOrNull { a -> a.detections.count { com.koolikanaku.domain.ClutterRules.isClutterLabel(it.label) } } ?: 0,
                tasks = room.tasks,
                language = vm.language,
                allSelected = room.tasks.isNotEmpty() && room.tasks.all { it.isSelected },
                onToggleTask = { vm.toggleTask(roomId, it) },
                onIncrementTask = { vm.incrementTask(roomId, it) },
                onDecrementTask = { vm.decrementTask(roomId, it) },
                onSelectAll = { vm.selectAllTasks(roomId, it) },
                availableCatalog = availableCatalog,
                onAddCatalogTask = { vm.addCatalogTask(roomId, it.key) },
                onAddCustomTask = { name, minutes -> vm.addCustomTask(roomId, name, minutes) },
                countRows = emptyList(),
                estimateHoursText = "${formatHours(quote.expectedMinutes.low)}-${formatHours(quote.expectedMinutes.high)} hr",
                estimatePayText = "₹${quote.priceInr.low.toInt()}-₹${quote.priceInr.high.toInt()}",
                mathLine1 = "Standard: ${quote.standardMinutes.low.toInt()}-${quote.standardMinutes.high.toInt()} min",
                mathLine2 = "Rate: ₹${job.hourlyRateInr.toInt()}/hr",
                onSaveRoom = {
                    vm.saveRoom()
                    navController.navigate(Routes.JOB_HUB) {
                        popUpTo(Routes.JOB_HUB) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BILL) {
            val context = LocalContext.current
            val job = vm.job
            val jobQuote = vm.quote
            var exportSheetVisible by remember { mutableStateOf(false) }
            var lastPdfUri by remember { mutableStateOf<android.net.Uri?>(null) }

            var pendingAfterRoom by remember { mutableStateOf<String?>(null) }
            val afterLauncher = rememberCameraFallbackLauncher(
                role = PhotoRole.AFTER,
                onPhotoCaptured = { photo ->
                    pendingAfterRoom?.let { vm.addPhoto(it, photo) }
                    pendingAfterRoom = null
                },
                onError = { pendingAfterRoom = null; vm.onCaptureError(it) }
            )
            val afterPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) afterLauncher() else { pendingAfterRoom = null; vm.onCaptureError("Camera permission is required to take photos") }
            }

            if (job == null || jobQuote == null) return@composable

            val billRooms = job.rooms.mapIndexed { index, room ->
                val roomQuote = jobQuote.rooms.find { it.roomId == room.id }
                BillRoomUi(
                    name = "Room ${index + 1}",
                    sizeText = room.dimensions?.let { "${it.computedAreaSqFt.toInt()} sq ft" } ?: "-",
                    payText = roomQuote?.let { "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}" } ?: "-",
                    timeText = roomQuote?.let { "${formatHours(it.expectedMinutes.low)}-${formatHours(it.expectedMinutes.high)} hr" } ?: "-",
                    tasks = room.tasks.filter { it.isSelected }.map {
                        BillTaskUi(
                            label = it.titleFor(vm.language),
                            qty = it.quantity,
                            minutesText = "${it.quantity * it.minutesPerUnit} min",
                            sourceLabel = when (it.source) {
                                TaskSource.DETECTED -> "Seen in photo"
                                TaskSource.TEMPLATE -> "Suggested for this room"
                                TaskSource.MANUAL -> "Added by you"
                            }
                        )
                    },
                    severityLabel = (room.severityOverride ?: room.stain?.severity ?: SeverityLevel.LIGHT).name,
                    severityMultText = "x${com.koolikanaku.domain.Constants.GRIME_MULTIPLIER[room.severityOverride ?: room.stain?.severity ?: SeverityLevel.LIGHT]}",
                    severityEdited = room.severityOverride != null,
                    aiSaidText = room.stain?.severity?.name?.lowercase() ?: "manual",
                    clutterLabel = room.clutter.name,
                    clutterMultText = "x${com.koolikanaku.domain.Constants.CLUTTER_MULTIPLIER[room.clutter]}",
                    mathRows = run {
                        val areaSqFt = room.dimensions?.computedAreaSqFt ?: 0.0
                        val minutesPerSqFt = com.koolikanaku.domain.Constants.MINUTES_PER_SQ_FT[room.roomType] ?: 0.25
                        val baseFloorMinutes = areaSqFt * minutesPerSqFt
                        val selectedTasks = room.tasks.filter { it.isSelected && it.quantity > 0 }
                        val taskMinutes = selectedTasks.sumOf { it.quantity * it.minutesPerUnit }
                        val mClutter = com.koolikanaku.domain.Constants.CLUTTER_MULTIPLIER[room.clutter] ?: 1.0
                        val effectiveSeverity = room.severityOverride ?: room.stain?.severity ?: SeverityLevel.LIGHT
                        val mGrime = com.koolikanaku.domain.Constants.GRIME_MULTIPLIER[effectiveSeverity] ?: 1.0
                        val mCond = 1.0 + (mClutter - 1.0) + (mGrime - 1.0)
                        val materialsInr = selectedTasks.sumOf { it.quantity * it.materialCostInr }
                        val spreadPct = room.dimensions?.source?.let { com.koolikanaku.domain.Constants.SPREAD_BY_SOURCE[it] }?.times(100)?.toInt() ?: 25
                        listOfNotNull(
                            BillMathRowUi("Room size", "${areaSqFt.toInt()} sq ft × $minutesPerSqFt min/sq ft"),
                            BillMathRowUi("Base floor minutes", "${baseFloorMinutes.toInt()} min"),
                            BillMathRowUi("Selected task minutes", "$taskMinutes min (${selectedTasks.size} task(s))"),
                            BillMathRowUi("Clutter multiplier", "x$mClutter (${room.clutter.name.lowercase()})"),
                            BillMathRowUi("Grime multiplier", "x$mGrime (${effectiveSeverity.name.lowercase()})"),
                            BillMathRowUi("Combined condition multiplier", "x${"%.2f".format(mCond)}"),
                            roomQuote?.let { BillMathRowUi("Standard minutes", "${it.standardMinutes.low.toInt()}-${it.standardMinutes.high.toInt()} (±$spreadPct%)") },
                            roomQuote?.let { BillMathRowUi("Expected minutes (your pace)", "${it.expectedMinutes.low.toInt()}-${it.expectedMinutes.high.toInt()}") },
                            if (materialsInr > 0) BillMathRowUi("Materials", "₹${materialsInr.toInt()}") else null,
                            BillMathRowUi("Hourly rate", "₹${job.hourlyRateInr.toInt()}/hr"),
                            roomQuote?.let { BillMathRowUi("Price", "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}", emphasized = true) }
                        )
                    }
                )
            }

            val photoGroups = job.rooms.mapIndexed { index, room ->
                BillPhotoGroupUi(
                    title = "Room ${index + 1}",
                    photos = room.photos.map {
                        BillPhotoUi(
                            label = it.role.name,
                            hashShort = it.sha256Hash.take(10),
                            fullHash = it.sha256Hash,
                            timeText = it.timestampIso,
                            uri = it.uri
                        )
                    }
                )
            }

            val worstToughByRoom = job.rooms.mapNotNull { room ->
                val worst = room.photos.filter { it.role == PhotoRole.TOUGHEST && vm.toughScores.containsKey(it.id) }
                    .maxWithOrNull(compareBy({ vm.toughScores.getValue(it.id).severity.ordinal }, { vm.toughScores.getValue(it.id).score }))
                    ?: room.photos.lastOrNull { it.role == PhotoRole.TOUGHEST }
                worst?.let { room.id to it.id }
            }.toMap()
            val beforeAfter = job.rooms.mapIndexedNotNull { index, room ->
                val beforeId = worstToughByRoom[room.id] ?: return@mapIndexedNotNull null
                val after = room.photos.lastOrNull { it.role == PhotoRole.AFTER }
                BeforeAfterUi(
                    roomId = room.id,
                    roomName = "Room ${index + 1}",
                    beforeUri = room.photos.firstOrNull { it.id == beforeId }?.uri,
                    afterUri = after?.uri,
                    afterHashShort = after?.sha256Hash?.take(10)
                )
            }

            BillScreen(
                bookingFooter = job.bookingId?.let { id ->
                    {
                        val booking = connectVm.booking(id)
                        if (booking != null && booking.status == com.koolikanaku.connect.model.BookingStatus.IN_PROGRESS) {
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    modifier = androidx.compose.ui.Modifier.padding(14.dp),
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.Text(stringResource(R.string.connect_complete_booking), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                                    androidx.compose.material3.Text(stringResource(R.string.connect_complete_booking_note), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                    if (com.koolikanaku.capture.BillFingerprint.afterPhotoHashes(job).isEmpty()) {
                                        androidx.compose.material3.Text(stringResource(R.string.connect_complete_need_after), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            val evidence = com.koolikanaku.connect.logic.EvidenceSummaryBuilder.from(job, jobQuote, java.time.Instant.now().toString())
                                            connectVm.perform(id, com.koolikanaku.connect.logic.BookingStateMachine.Action.COMPLETE, evidence = evidence) {
                                                navController.navigate(Routes.connectBooking(id))
                                            }
                                        },
                                        enabled = !connectVm.busy,
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                    ) { androidx.compose.material3.Text(stringResource(R.string.connect_complete_booking)) }
                                }
                            }
                        } else if (booking != null) {
                            androidx.compose.material3.Text(stringResource(R.string.connect_booking_done))
                        }
                    }
                },
                beforeAfter = beforeAfter,
                onAddAfterPhoto = { roomId ->
                    pendingAfterRoom = roomId
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPermission) afterLauncher() else afterPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                },
                onVerifyPhotos = { navController.navigate(Routes.VERIFY) },
                title = if (job.mode == AppMode.OWNER_QUOTE) "Cleaning Quotation" else "Cleaning Estimate",
                subtitleLine = "${job.rooms.size} room(s) · " + stringResource(
                    if (job.mode == AppMode.OWNER_QUOTE) R.string.bill_rate_market else R.string.bill_rate_per_hour,
                    job.hourlyRateInr.toInt().toString()
                ),
                totalTimeText = "${formatHours(jobQuote.totalExpectedMinutes.low)}-${formatHours(jobQuote.totalExpectedMinutes.high)}",
                payLabel = if (job.mode == AppMode.OWNER_QUOTE) "Quoted" else "You earn",
                totalPayText = "₹${jobQuote.totalPriceInr.low.toInt()}-₹${jobQuote.totalPriceInr.high.toInt()}",
                rangeNote = "Range reflects how each room's size was obtained.",
                rooms = billRooms,
                photoGroups = photoGroups,
                disclaimer = stringResource(R.string.bill_disclaimer),
                exportSheetVisible = exportSheetVisible,
                fileNameText = lastPdfUri?.lastPathSegment ?: "cleaning_estimate.pdf",
                pdfMetaText = "${job.rooms.size} rooms",
                onExportPdf = {
                    val uri = PdfExporter.exportBillPdf(context, job, jobQuote, worstToughByRoom)
                    lastPdfUri = uri
                    exportSheetVisible = true
                },
                onShare = {
                    lastPdfUri?.let { PdfExporter.sharePdf(context, it) }
                },
                onDismissExportSheet = { exportSheetVisible = false },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONNECT_AUTH) {
            com.koolikanaku.connect.ui.ConnectAuthScreen(
                vm = connectVm,
                onBack = { navController.popBackStack() },
                onSignedIn = { navController.navigate(Routes.CONNECT_HOME) { popUpTo(Routes.CONNECT_AUTH) { inclusive = true } } }
            )
        }

        composable(Routes.CONNECT_HOME) {
            if (connectVm.user == null) {
                LaunchedEffect(Unit) { navController.navigate(Routes.CONNECT_AUTH) { popUpTo(Routes.CONNECT_HOME) { inclusive = true } } }
            } else {
                com.koolikanaku.connect.ui.ConnectHomeScreen(
                    vm = connectVm,
                    onBack = { navController.popBackStack() },
                    onOpenBooking = { navController.navigate(Routes.connectBooking(it)) }
                )
            }
        }

        composable(
            Routes.CONNECT_BOOKING,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: return@composable
            com.koolikanaku.connect.ui.BookingScreen(
                vm = connectVm,
                bookingId = bookingId,
                estimateStartedHere = vm.job?.bookingId == bookingId,
                onBack = { navController.popBackStack() },
                onStartEstimate = { booking ->
                    connectScope.launch {
                        val role = connectVm.user?.role ?: return@launch
                        val rate = connectVm.workerRateFor(booking)
                        val mode = if (role == com.koolikanaku.connect.model.UserRole.OWNER) AppMode.OWNER_QUOTE else AppMode.WORKER_ESTIMATE
                        val jobId = vm.startJobForBooking(mode, rate, booking.id)
                        connectVm.perform(booking.id, com.koolikanaku.connect.logic.BookingStateMachine.Action.START, jobId = jobId) {
                            navController.navigate(Routes.JOB_HUB)
                        }
                    }
                },
                onContinueEstimate = { navController.navigate(Routes.JOB_HUB) }
            )
        }

        composable(Routes.VERIFY) {
            val context = LocalContext.current
            val job = vm.job ?: return@composable
            var savedChecks by remember { mutableStateOf<List<com.koolikanaku.capture.HashVerifier.PhotoCheck>?>(null) }
            var fileResult by remember { mutableStateOf<com.koolikanaku.ui.screens.FileCheckResult?>(null) }
            LaunchedEffect(job) {
                savedChecks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.koolikanaku.capture.HashVerifier.checkSavedPhotos(context, job)
                }
            }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) scope.launch {
                    val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.koolikanaku.capture.HashVerifier.hashOf(context, uri)
                    }
                    fileResult = when {
                        hash == null -> com.koolikanaku.ui.screens.FileCheckResult.Unreadable
                        else -> com.koolikanaku.capture.HashVerifier.findMatch(job, hash)
                            ?.let { com.koolikanaku.ui.screens.FileCheckResult.Match(it, hash) }
                            ?: com.koolikanaku.ui.screens.FileCheckResult.NoMatch(hash)
                    }
                }
            }
            com.koolikanaku.ui.screens.VerifyHashScreen(
                savedChecks = savedChecks,
                fileResult = fileResult,
                onPickFile = { picker.launch(arrayOf("image/*")) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
