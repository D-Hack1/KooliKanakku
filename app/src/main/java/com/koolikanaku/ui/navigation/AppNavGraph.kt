package com.koolikanaku.ui.navigation

import kotlinx.coroutines.launch
import com.koolikanaku.ui.screens.BeforeAfterUi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.collectAsState
import com.koolikanaku.capture.rememberCameraFallbackLauncher
import com.koolikanaku.connect.ConnectViewModel
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Role
import com.koolikanaku.connect.ui.BookingStatusScreen
import com.koolikanaku.connect.ui.HiredWorkerSummary
import com.koolikanaku.connect.ui.HiredWorkersScreen
import com.koolikanaku.connect.ui.NearbyWorkersScreen
import com.koolikanaku.connect.ui.RateWorkerScreen
import com.koolikanaku.connect.ui.RequestInboxScreen
import com.koolikanaku.connect.ui.RoleSelectScreen
import com.koolikanaku.connect.ui.WelcomeSignInScreen
import com.koolikanaku.connect.ui.WorkerDetailScreen
import com.koolikanaku.connect.ui.WorkerHistoryScreen
import com.koolikanaku.connect.ui.WorkerHomeScreen
import com.koolikanaku.connect.ui.WorkerProfileScreen
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.BillFingerprint
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
    const val WELCOME = "welcome"
    const val ROLE_SELECT = "role_select"
    const val WORKER_PROFILE = "worker_profile"
    const val WORKER_HOME = "worker_home"
    const val REQUEST_INBOX = "request_inbox"
    const val NEARBY_WORKERS = "nearby_workers"
    const val WORKER_DETAIL = "worker_detail/{workerId}"
    fun workerDetail(workerId: String) = "worker_detail/$workerId"
    const val BOOKING_STATUS = "booking_status/{bookingId}"
    fun bookingStatus(bookingId: String) = "booking_status/$bookingId"
    const val RATE_WORKER = "rate_worker/{bookingId}"
    fun rateWorker(bookingId: String) = "rate_worker/$bookingId"
    const val WORKER_RATINGS = "worker_ratings"
    const val HIRED_WORKERS = "hired_workers"
    const val WORKER_HISTORY = "worker_history/{workerId}"
    fun workerHistory(workerId: String) = "worker_history/$workerId"

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
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    vm: AppViewModel = viewModel(),
    cvm: ConnectViewModel = viewModel()
) {
    val storageContext = LocalContext.current
    remember(vm) { vm.attachStorage(storageContext) }   // restore a saved job before any screen reads state
    remember(cvm) { cvm.attach(storageContext) }

    // Bookings this owner has already been prompted about (or finished themselves), so the
    // "work is done, rate now?" dialog below fires exactly once per booking, wherever they are.
    var completionPromptSeen by remember { mutableStateOf(setOf<String>()) }

    // A failed booking action (accept/cancel/finish/rate/...) used to fail silently, leaving the
    // booking stuck at its old status with no sign anything went wrong. Surface it instead.
    val toastContext = LocalContext.current
    LaunchedEffect(cvm.bookingActionError) {
        cvm.bookingActionError?.let { message ->
            android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_LONG).show()
            cvm.clearBookingActionError()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME,
        enterTransition = { slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(260)) }
    ) {

        composable(Routes.SETUP) {
            LaunchedEffect(cvm.user?.role) {
                when (cvm.user?.role) {
                    Role.WORKER -> {
                        vm.changeMode(AppMode.WORKER_ESTIMATE)
                        cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                    }
                    Role.OWNER -> {
                        vm.changeMode(AppMode.OWNER_QUOTE)
                    }
                    null -> {}
                }
            }
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
                resumeRoomCount = vm.job?.rooms?.size ?: 0,
                onResumeJob = {
                    navController.navigate(if (vm.quote != null) Routes.BILL else Routes.JOB_HUB)
                },
                onStartJob = {
                    val bookingId = vm.pendingBookingId
                    vm.setPendingBooking(null)
                    vm.startJob(bookingId)
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
                    name = if (vm.language == AppLanguage.ML) "മുറി ${index + 1} - ${room.roomType.displayName(vm.language)}"
                           else "Room ${index + 1} - ${room.roomType.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }}",
                    metaLine1 = room.dimensions?.let {
                        if (vm.language == AppLanguage.ML) "${it.computedAreaSqFt.toInt()} ച. അടി" else "${it.computedAreaSqFt.toInt()} sq ft"
                    } ?: (if (vm.language == AppLanguage.ML) "വലിപ്പം നൽകിയിട്ടില്ല" else "Size not set"),
                    metaLine2 = if (vm.language == AppLanguage.ML) "${room.tasks.count { it.isSelected }} ജോലികൾ തിരഞ്ഞെടുത്തു"
                                else "${room.tasks.count { it.isSelected }} tasks selected",
                    timeText = quote?.let { "${formatHours(it.expectedMinutes.low)}-${formatHours(it.expectedMinutes.high)} ${if (vm.language == AppLanguage.ML) "മണി" else "hr"}" } ?: "-",
                    payText = quote?.let { "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}" } ?: "-",
                    analyzed = (room.dimensions?.computedAreaSqFt ?: 0.0) > 0.0 && (room.photoAnalyses.isNotEmpty() || room.stain != null)
                )
            }
            val totalLow = job?.rooms.orEmpty().sumOf { room -> job?.let { PricingEngine.quoteRoom(room, it).priceInr.low } ?: 0.0 }
            val totalHigh = job?.rooms.orEmpty().sumOf { room -> job?.let { PricingEngine.quoteRoom(room, it).priceInr.high } ?: 0.0 }

            JobHubScreen(
                modeLabel = if (job?.mode == AppMode.OWNER_QUOTE) (if (vm.language == AppLanguage.ML) "ഉടമ ക്വട്ടേഷൻ" else "Owner quote")
                            else (if (vm.language == AppLanguage.ML) "തൊഴിലാളി എസ്റ്റിമേറ്റ്" else "Worker estimate"),
                rateLabel = if (vm.language == AppLanguage.ML) "₹${job?.hourlyRateInr?.toInt() ?: 0}/മണി" else "₹${job?.hourlyRateInr?.toInt() ?: 0}/hr",
                paceLabel = if (vm.language == AppLanguage.ML) "${job?.paceFactor ?: 1.0}x വേഗത" else "${job?.paceFactor ?: 1.0}x pace",
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
                },
                language = vm.language,
                onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme
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
                    if (l != null && w != null) {
                        if (vm.language == AppLanguage.ML) "${(l * w).toInt()} ച. അടി" else "${(l * w).toInt()} sq ft"
                    } else null
                },
                presetIndex = vm.presetIndex,
                presetLabels = vm.presetOptions.map { (if (vm.language == AppLanguage.ML) it.labelMl else it.labelEn) to it.labelEn },
                onPresetChange = vm::changePresetIndex,
                widePhotos = widePhotoList,
                toughPhotos = toughPhotoList,
                onDeletePhoto = { photoId -> vm.removePhoto(roomId, photoId) },
                canAddWide = true,
                canAddTough = true,
                readyToAnalyze = readyToAnalyze,
                statusText = if (readyToAnalyze) (if (vm.language == AppLanguage.ML) "വിശകലനം ചെയ്യാൻ തയ്യാറാണ്" else "Ready to analyze")
                             else (if (vm.language == AppLanguage.ML) "തുടരാൻ മുകളിലെ ഘട്ടങ്ങൾ പൂർത്തിയാക്കുക" else "Finish the steps above to continue"),
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
                onBack = { navController.popBackStack() },
                language = vm.language,
                onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme
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
                onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme,
                allSelected = room.tasks.isNotEmpty() && room.tasks.all { it.isSelected },
                onToggleTask = { vm.toggleTask(roomId, it) },
                onIncrementTask = { vm.incrementTask(roomId, it) },
                onDecrementTask = { vm.decrementTask(roomId, it) },
                onSelectAll = { vm.selectAllTasks(roomId, it) },
                availableCatalog = availableCatalog,
                onAddCatalogTask = { vm.addCatalogTask(roomId, it.key) },
                onAddCustomTask = { name, minutes -> vm.addCustomTask(roomId, name, minutes) },
                countRows = emptyList(),
                estimateHoursText = "${formatHours(quote.expectedMinutes.low)}-${formatHours(quote.expectedMinutes.high)} ${if (vm.language == AppLanguage.ML) "മണി" else "hr"}",
                estimatePayText = "₹${quote.priceInr.low.toInt()}-₹${quote.priceInr.high.toInt()}",
                mathLine1 = if (vm.language == AppLanguage.ML) "സാധാരണ: ${quote.standardMinutes.low.toInt()}-${quote.standardMinutes.high.toInt()} മിനിറ്റ്"
                            else "Standard: ${quote.standardMinutes.low.toInt()}-${quote.standardMinutes.high.toInt()} min",
                mathLine2 = if (vm.language == AppLanguage.ML) "നിരക്ക്: ₹${job.hourlyRateInr.toInt()}/മണി"
                            else "Rate: ₹${job.hourlyRateInr.toInt()}/hr",
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
            var finishedBookingIds by remember { mutableStateOf(setOf<String>()) }

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
                    name = if (vm.language == AppLanguage.ML) "മുറി ${index + 1}" else "Room ${index + 1}",
                    sizeText = room.dimensions?.let {
                        if (vm.language == AppLanguage.ML) "${it.computedAreaSqFt.toInt()} ച. അടി" else "${it.computedAreaSqFt.toInt()} sq ft"
                    } ?: "-",
                    payText = roomQuote?.let { "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}" } ?: "-",
                    timeText = roomQuote?.let { "${formatHours(it.expectedMinutes.low)}-${formatHours(it.expectedMinutes.high)} ${if (vm.language == AppLanguage.ML) "മണി" else "hr"}" } ?: "-",
                    tasks = room.tasks.filter { it.isSelected }.map {
                        BillTaskUi(
                            label = it.titleFor(vm.language),
                            qty = it.quantity,
                            minutesText = "${it.quantity * it.minutesPerUnit} ${if (vm.language == AppLanguage.ML) "മിനിറ്റ്" else "min"}",
                            sourceLabel = when (it.source) {
                                TaskSource.DETECTED -> if (vm.language == AppLanguage.ML) "ഫോട്ടോയിൽ കണ്ടത്" else "Seen in photo"
                                TaskSource.TEMPLATE -> if (vm.language == AppLanguage.ML) "ഈ മുറിക്ക് നിർദ്ദേശിച്ചത്" else "Suggested for this room"
                                TaskSource.MANUAL -> if (vm.language == AppLanguage.ML) "നിങ്ങൾ ചേർത്തത്" else "Added by you"
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
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "മുറിയുടെ വലിപ്പം" else "Room size", "${areaSqFt.toInt()} sq ft × $minutesPerSqFt min/sq ft"),
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "തറ സമയം" else "Base floor minutes", "${baseFloorMinutes.toInt()} min"),
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "ജോലി സമയം" else "Selected task minutes", "$taskMinutes min (${selectedTasks.size})"),
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "സാധനങ്ങളുടെ ഗുണകം" else "Clutter multiplier", "x$mClutter"),
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "അഴുക്കിന്റെ ഗുണകം" else "Grime multiplier", "x$mGrime"),
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "ആകെ ഗുണകം" else "Combined condition multiplier", "x${"%.2f".format(mCond)}"),
                            roomQuote?.let { BillMathRowUi(if (vm.language == AppLanguage.ML) "സാധാരണ സമയം" else "Standard minutes", "${it.standardMinutes.low.toInt()}-${it.standardMinutes.high.toInt()} (±$spreadPct%)") },
                            roomQuote?.let { BillMathRowUi(if (vm.language == AppLanguage.ML) "പ്രതീക്ഷിക്കുന്ന സമയം" else "Expected minutes", "${it.expectedMinutes.low.toInt()}-${it.expectedMinutes.high.toInt()}") },
                            if (materialsInr > 0) BillMathRowUi(if (vm.language == AppLanguage.ML) "സാധനങ്ങൾ" else "Materials", "₹${materialsInr.toInt()}") else null,
                            BillMathRowUi(if (vm.language == AppLanguage.ML) "മണിക്കൂർ നിരക്ക്" else "Hourly rate", "₹${job.hourlyRateInr.toInt()}/hr"),
                            roomQuote?.let { BillMathRowUi(if (vm.language == AppLanguage.ML) "വില" else "Price", "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}", emphasized = true) }
                        )
                    }
                )
            }

            val photoGroups = job.rooms.mapIndexed { index, room ->
                BillPhotoGroupUi(
                    title = if (vm.language == AppLanguage.ML) "മുറി ${index + 1}" else "Room ${index + 1}",
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
                    roomName = if (vm.language == AppLanguage.ML) "മുറി ${index + 1}" else "Room ${index + 1}",
                    beforeUri = room.photos.firstOrNull { it.id == beforeId }?.uri,
                    afterUri = after?.uri,
                    afterHashShort = after?.sha256Hash?.take(10)
                )
            }

            BillScreen(
                beforeAfter = beforeAfter,
                onAddAfterPhoto = { roomId ->
                    pendingAfterRoom = roomId
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPermission) afterLauncher() else afterPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                },
                onVerifyPhotos = { navController.navigate(Routes.VERIFY) },
                title = if (job.mode == AppMode.OWNER_QUOTE) (if (vm.language == AppLanguage.ML) "ശുചീകരണ ക്വട്ടേഷൻ" else "Cleaning Quotation")
                        else (if (vm.language == AppLanguage.ML) "ശുചീകരണ എസ്റ്റിമേറ്റ്" else "Cleaning Estimate"),
                subtitleLine = (if (vm.language == AppLanguage.ML) "${job.rooms.size} മുറികൾ · " else "${job.rooms.size} room(s) · ") + stringResource(
                    if (job.mode == AppMode.OWNER_QUOTE) R.string.bill_rate_market else R.string.bill_rate_per_hour,
                    job.hourlyRateInr.toInt().toString()
                ),
                totalTimeText = "${formatHours(jobQuote.totalExpectedMinutes.low)}-${formatHours(jobQuote.totalExpectedMinutes.high)}",
                payLabel = if (job.mode == AppMode.OWNER_QUOTE) (if (vm.language == AppLanguage.ML) "ക്വട്ടേഷൻ തുക" else "Quoted")
                           else (if (vm.language == AppLanguage.ML) "നിങ്ങളുടെ വരുമാനം" else "You earn"),
                totalPayText = "₹${jobQuote.totalPriceInr.low.toInt()}-₹${jobQuote.totalPriceInr.high.toInt()}",
                rangeNote = if (vm.language == AppLanguage.ML) "മുറികളുടെ വലിപ്പത്തിൽ എത്ര ഉറപ്പുണ്ടെന്ന് ഈ പരിധി കാണിക്കുന്നു."
                            else "Range reflects how each room's size was obtained.",
                rooms = billRooms,
                photoGroups = photoGroups,
                disclaimer = stringResource(R.string.bill_disclaimer),
                exportSheetVisible = exportSheetVisible,
                fileNameText = lastPdfUri?.lastPathSegment ?: "cleaning_estimate.pdf",
                pdfMetaText = if (vm.language == AppLanguage.ML) "${job.rooms.size} മുറികൾ" else "${job.rooms.size} rooms",
                onExportPdf = {
                    val uri = PdfExporter.exportBillPdf(context, job, jobQuote, worstToughByRoom)
                    lastPdfUri = uri
                    exportSheetVisible = true
                },
                onShare = {
                    lastPdfUri?.let { PdfExporter.sharePdf(context, it) }
                },
                onDismissExportSheet = { exportSheetVisible = false },
                bookingLabel = job.bookingId?.let { "#" + it.takeLast(6) },
                bookingFinished = job.bookingId != null && job.bookingId in finishedBookingIds,
                onFinishBooking = {
                    val bookingId = job.bookingId ?: return@BillScreen
                    val summary = EvidenceSummary(
                        billFingerprint = BillFingerprint.compute(job, jobQuote),
                        totalPriceLowInr = jobQuote.totalPriceInr.low,
                        totalPriceHighInr = jobQuote.totalPriceInr.high,
                        roomCount = job.rooms.size,
                        afterPhotoHashes = job.rooms.flatMap { r -> r.photos.filter { it.role == PhotoRole.AFTER }.map { it.sha256Hash } },
                        photoCount = job.rooms.sumOf { it.photos.size },
                        generatedAtEpochMs = System.currentTimeMillis()
                    )
                    cvm.completeBooking(bookingId, summary) {
                        finishedBookingIds = finishedBookingIds + bookingId
                        if (cvm.user?.role == Role.OWNER) {
                            // Only the owner rates (a worker can't rate themselves - BookingStatusScreen
                            // hides that button for them too), so only the owner is bounced there - and
                            // marked seen so the global "work is done" dialog doesn't also pop up on top of it.
                            completionPromptSeen = completionPromptSeen + bookingId
                            navController.navigate(Routes.rateWorker(bookingId))
                        } else {
                            vm.setPendingBooking(null)
                            navController.navigate(Routes.WORKER_HOME) {
                                popUpTo(Routes.WORKER_HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
                language = vm.language,
                onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme
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
                onBack = { navController.popBackStack() },
                language = vm.language,
                onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme,
                onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        // ---------------- Find a worker (extra_feature.md) ----------------

        composable(Routes.WELCOME) {
            // A previous session's sign-in survives process death (DemoConnectRepository persists it),
            // so don't make a returning signed-in user re-enter their phone and PIN every cold start.
            LaunchedEffect(Unit) {
                val existing = cvm.user
                if (existing != null) {
                    if (existing.role == Role.WORKER) {
                        vm.changeMode(AppMode.WORKER_ESTIMATE)
                        cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                    } else if (existing.role == Role.OWNER) {
                        vm.changeMode(AppMode.OWNER_QUOTE)
                    }
                    val dest = if (existing.role == Role.WORKER) Routes.WORKER_HOME else Routes.NEARBY_WORKERS
                    navController.navigate(dest) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            }
            WelcomeSignInScreen(
                isSignUp = cvm.isSignUp,
                onToggleMode = cvm::toggleAuthMode,
                phoneText = cvm.phoneText, onPhoneChange = cvm::changePhone,
                pinText = cvm.pinText, onPinChange = cvm::changePin,
                nameText = cvm.nameText, onNameChange = cvm::changeName,
                error = cvm.authError,
                phoneAlreadyRegistered = cvm.phoneAlreadyRegistered,
                onSubmit = {
                    if (cvm.isSignUp) {
                        if (cvm.validateFormForRoleSelect()) navController.navigate(Routes.ROLE_SELECT)
                    } else {
                        cvm.submitAuth {
                            val role = cvm.user?.role
                            if (role == Role.WORKER) {
                                vm.changeMode(AppMode.WORKER_ESTIMATE)
                                cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                            } else if (role == Role.OWNER) {
                                vm.changeMode(AppMode.OWNER_QUOTE)
                            }
                            val dest = if (role == Role.WORKER) Routes.WORKER_HOME else Routes.NEARBY_WORKERS
                            navController.navigate(dest) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onUseWithoutAccount = {
                    // Not tied to any booking - clear any stale pendingBookingId left over from a
                    // booking flow the user backed out of, so this job doesn't get silently attached
                    // to (and later "finish" into completing) an unrelated old booking.
                    vm.setPendingBooking(null)
                    navController.navigate(Routes.SETUP)
                },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen(
                onPick = { role ->
                    cvm.choosePendingRole(role)
                    cvm.submitAuth {
                        if (role == Role.WORKER) {
                            vm.changeMode(AppMode.WORKER_ESTIMATE)
                            cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                        } else if (role == Role.OWNER) {
                            vm.changeMode(AppMode.OWNER_QUOTE)
                        }
                        val dest = if (role == Role.WORKER) Routes.WORKER_PROFILE else Routes.NEARBY_WORKERS
                        navController.navigate(dest) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                },
                error = cvm.authError,
                onBack = { navController.popBackStack() },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.WORKER_PROFILE) {
            LaunchedEffect(Unit) { cvm.loadProfileIfAny() }
            WorkerProfileScreen(
                languagesText = cvm.profileLanguages, onLanguagesChange = cvm::changeProfileLanguages,
                availableServices = cvm.availableServices, selectedServices = cvm.profileServices, onToggleService = cvm::toggleProfileService,
                rateMin = cvm.profileRateMin, onRateMinChange = cvm::changeProfileRateMin,
                rateMax = cvm.profileRateMax, onRateMaxChange = cvm::changeProfileRateMax,
                bio = cvm.profileBio, onBioChange = cvm::changeProfileBio,
                onSave = {
                    cvm.saveProfile {
                        navController.navigate(Routes.WORKER_HOME) {
                            popUpTo(Routes.WORKER_HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.WORKER_HOME) {
            val context = LocalContext.current
            val user = cvm.user
            if (user == null) {
                LaunchedEffect(Unit) { navController.navigate(Routes.WELCOME) { popUpTo(0) } }
                return@composable
            }
            val allBookings by cvm.bookingsForUser().collectAsState(initial = emptyList())
            val incoming = allBookings.filter { it.status == BookingStatus.REQUESTED }
            val active = allBookings.firstOrNull { it.status in setOf(BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS) }
            // Loaded here (not just on the Profile screen) so the saved rate is available for the
            // "Open offline estimator" prefill below even if this session never opened Edit profile.
            LaunchedEffect(Unit) { cvm.loadProfileIfAny() }
            WorkerHomeScreen(
                workerName = user.name,
                openForWork = cvm.openForWork,
                onToggleOpen = { cvm.toggleOpenForWork(context) },
                areaLabel = cvm.areaLabel,
                incoming = incoming,
                active = active,
                onAccept = { cvm.respondToBooking(it, true) },
                onDecline = { cvm.respondToBooking(it, false) },
                onOpenBooking = { navController.navigate(Routes.bookingStatus(it)) },
                onEditProfile = { navController.navigate(Routes.WORKER_PROFILE) },
                onOpenEstimator = {
                    vm.changeMode(AppMode.WORKER_ESTIMATE)
                    cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                    // Not tied to any booking - see onUseWithoutAccount's comment above.
                    vm.setPendingBooking(null)
                    navController.navigate(Routes.SETUP)
                },
                onSeeAllRequests = { navController.navigate(Routes.REQUEST_INBOX) },
                onViewMyRatings = { navController.navigate(Routes.WORKER_RATINGS) },
                onSignOut = {
                    cvm.signOut()
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                },
                busy = cvm.bookingActionBusy,
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.WORKER_RATINGS) {
            val uid = cvm.user?.uid
            // Built from the profile + rating list directly, not workerCard()/cardFor() - those only
            // return a row while the worker is open for work, but a worker must see their own ratings
            // whether they're currently visible to owners or not.
            var profile by remember { mutableStateOf<com.koolikanaku.connect.model.WorkerProfile?>(null) }
            LaunchedEffect(uid) { profile = uid?.let { cvm.profileFor(it) } }
            val recentRatings by (uid?.let { cvm.ratingsFor(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                .collectAsState(initial = emptyList())
            val worker = profile?.let { p ->
                com.koolikanaku.connect.model.WorkerCard(
                    uid = p.uid, name = p.name, languages = p.languages, services = p.services,
                    rateMinInr = p.rateMinInr, rateMaxInr = p.rateMaxInr, bio = p.bio,
                    ratingAvg = if (recentRatings.isEmpty()) 0.0 else recentRatings.sumOf { it.stars } / recentRatings.size.toDouble(),
                    ratingCount = recentRatings.size, verifiedCount = recentRatings.count { it.verified },
                    distanceBandLabel = ""
                )
            }
            WorkerDetailScreen(
                worker = worker,
                recentRatings = recentRatings,
                onRequestBooking = {},
                showRequestButton = false,
                onBack = { navController.popBackStack() },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.REQUEST_INBOX) {
            val allBookings by cvm.bookingsForUser().collectAsState(initial = emptyList())
            val incoming = allBookings.filter { it.status == BookingStatus.REQUESTED }
            RequestInboxScreen(
                requests = incoming,
                onAccept = { cvm.respondToBooking(it, true) },
                onDecline = { cvm.respondToBooking(it, false) },
                onBack = { navController.popBackStack() },
                busy = cvm.bookingActionBusy,
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.NEARBY_WORKERS) {
            val context = LocalContext.current
            val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { cvm.resolveOwnerArea(context) }
            LaunchedEffect(Unit) {
                if (com.koolikanaku.connect.location.CoarseLocation.hasPermission(context)) cvm.resolveOwnerArea(context)
                else locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            val workers by cvm.nearbyWorkersFlow().collectAsState(initial = emptyList())
            val allBookingsForOwner by cvm.bookingsForUser().collectAsState(initial = emptyList())
            // REQUESTED included too: the owner should see a worker is assigned and the job is
            // under way the moment they hire someone, not only once the worker gets around to
            // accepting - unlike the worker's own "active" card, which keeps REQUESTED separate
            // under "Incoming requests" since that side has something to act on (accept/decline).
            val activeBooking = allBookingsForOwner.firstOrNull {
                it.status in setOf(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS)
            }
            NearbyWorkersScreen(
                areaLabel = cvm.ownerAreaLabel,
                workers = workers,
                onWorkerClick = { navController.navigate(Routes.workerDetail(it)) },
                onOpenEstimator = {
                    vm.changeMode(AppMode.OWNER_QUOTE)
                    // Not tied to any booking - see onUseWithoutAccount's comment above.
                    vm.setPendingBooking(null)
                    navController.navigate(Routes.SETUP)
                },
                active = activeBooking,
                onOpenBooking = { navController.navigate(Routes.bookingStatus(it)) },
                onViewHiredWorkers = { navController.navigate(Routes.HIRED_WORKERS) },
                onSignOut = {
                    cvm.signOut()
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(Routes.HIRED_WORKERS) {
            val uid = cvm.user?.uid
            val allBookings by cvm.bookingsForUser().collectAsState(initial = emptyList())
            val hired = allBookings
                .filter { it.ownerId == uid }
                .groupBy { it.workerId }
                .map { (workerId, bookingsWithWorker) ->
                    val latest = bookingsWithWorker.maxBy { it.createdAtEpochMs }
                    latest.createdAtEpochMs to HiredWorkerSummary(
                        workerId = workerId,
                        workerName = latest.workerName,
                        bookingCount = bookingsWithWorker.size,
                        lastStatus = latest.status,
                        lastDateText = java.text.DateFormat.getDateInstance().format(java.util.Date(latest.createdAtEpochMs))
                    )
                }
                .sortedByDescending { it.first }
                .map { it.second }
            HiredWorkersScreen(
                workers = hired,
                onWorkerClick = { navController.navigate(Routes.workerHistory(it)) },
                onBack = { navController.popBackStack() },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(
            Routes.WORKER_HISTORY,
            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workerId = backStackEntry.arguments?.getString("workerId") ?: return@composable
            val uid = cvm.user?.uid
            val allBookings by cvm.bookingsForUser().collectAsState(initial = emptyList())
            val withThisWorker = allBookings
                .filter { it.ownerId == uid && it.workerId == workerId }
                .sortedByDescending { it.createdAtEpochMs }
            WorkerHistoryScreen(
                workerName = withThisWorker.firstOrNull()?.workerName ?: "worker",
                bookings = withThisWorker,
                onOpenBooking = { navController.navigate(Routes.bookingStatus(it)) },
                onBack = { navController.popBackStack() },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(
            Routes.WORKER_DETAIL,
            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workerId = backStackEntry.arguments?.getString("workerId") ?: return@composable
            var worker by remember(workerId) { mutableStateOf<com.koolikanaku.connect.model.WorkerCard?>(null) }
            LaunchedEffect(workerId) { worker = cvm.workerCard(workerId) }
            val recentRatings by cvm.ratingsFor(workerId).collectAsState(initial = emptyList())
            WorkerDetailScreen(
                worker = worker,
                recentRatings = recentRatings,
                onRequestBooking = { cvm.requestBooking(workerId) { bookingId -> navController.navigate(Routes.bookingStatus(bookingId)) } },
                onBack = { navController.popBackStack() },
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(
            Routes.BOOKING_STATUS,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: return@composable
            val context = LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val booking by cvm.booking(bookingId).collectAsState(initial = null)
            val meIsWorker = cvm.user?.uid == booking?.workerId
            BookingStatusScreen(
                booking = booking,
                meIsWorker = meIsWorker,
                onCall = {
                    val myUid = cvm.user?.uid ?: return@BookingStatusScreen
                    scope.launch {
                        val phone = cvm.phoneFor(bookingId, myUid)
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
                    }
                },
                onArrived = { cvm.setBookingStatus(bookingId, BookingStatus.ARRIVED) },
                onStartEstimate = {
                    val b = booking ?: return@BookingStatusScreen
                    if (meIsWorker) {
                        vm.changeMode(AppMode.WORKER_ESTIMATE)
                        cvm.workerRateMidpoint().takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                        vm.setPendingBooking(bookingId)
                        cvm.setBookingStatus(bookingId, BookingStatus.IN_PROGRESS)
                        navController.navigate(Routes.SETUP)
                    } else {
                        // The quotation is for THIS worker's job, so it should default to what they
                        // themselves already set as their rate, not a generic owner-market default.
                        vm.changeMode(AppMode.OWNER_QUOTE)
                        scope.launch {
                            val workerRate = cvm.profileFor(b.workerId)?.let { p ->
                                if (p.rateMinInr > 0 && p.rateMaxInr > 0) (p.rateMinInr + p.rateMaxInr) / 2
                                else if (p.rateMinInr > 0) p.rateMinInr else p.rateMaxInr
                            }
                            workerRate?.takeIf { it > 0 }?.let { vm.changeRateText(it.toInt().toString()) }
                            vm.setPendingBooking(bookingId)
                            cvm.setBookingStatus(bookingId, BookingStatus.IN_PROGRESS)
                            navController.navigate(Routes.SETUP)
                        }
                    }
                },
                onCancel = { cvm.cancelBooking(bookingId, "Cancelled by ${if (meIsWorker) "worker" else "owner"}") },
                onRate = { navController.navigate(Routes.rateWorker(bookingId)) },
                onBack = { navController.popBackStack() },
                busy = cvm.bookingActionBusy,
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }

        composable(
            Routes.RATE_WORKER,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: return@composable
            val booking by cvm.booking(bookingId).collectAsState(initial = null)
            var stars by remember { mutableStateOf(0) }
            var selectedTags by remember { mutableStateOf(setOf<String>()) }
            var comment by remember { mutableStateOf("") }
            RateWorkerScreen(
                workerName = booking?.workerName ?: "worker",
                stars = stars, onStarsChange = { stars = it },
                availableTags = com.koolikanaku.connect.model.ConnectConstants.RATING_TAGS,
                selectedTags = selectedTags, onToggleTag = { selectedTags = if (it in selectedTags) selectedTags - it else selectedTags + it },
                comment = comment, onCommentChange = { comment = it },
                willBeVerified = booking?.evidence != null,
                onSubmit = {
                    cvm.submitRating(bookingId, stars, selectedTags.toList(), comment) {
                        navController.navigate(Routes.NEARBY_WORKERS) {
                            popUpTo(Routes.NEARBY_WORKERS) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                busy = cvm.bookingActionBusy,
                language = vm.language, onLanguageChange = vm::changeLanguage,
                darkTheme = vm.darkTheme, onToggleDarkTheme = vm::toggleDarkTheme
            )
        }
    }

    // Fires on whatever screen the owner is on, as soon as a poll picks up that a worker finished
    // the job (status -> COMPLETED) - they shouldn't have to go find the booking themselves to rate it.
    if (cvm.user?.role == Role.OWNER) {
        val ownerBookings by cvm.bookingsForUser().collectAsState(initial = emptyList())
        val toPrompt = ownerBookings.firstOrNull { it.status == BookingStatus.COMPLETED && it.id !in completionPromptSeen }
        if (toPrompt != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { completionPromptSeen = completionPromptSeen + toPrompt.id },
                title = { androidx.compose.material3.Text(if (vm.language == AppLanguage.ML) "ജോലി പൂർത്തിയായി!" else "Work is done!") },
                text = {
                    androidx.compose.material3.Text(
                        if (vm.language == AppLanguage.ML) "${toPrompt.workerName} ജോലി പൂർത്തിയാക്കി. ഇപ്പോൾ റേറ്റിംഗ് നൽകണോ?"
                        else "${toPrompt.workerName} has finished the job. Rate them now?"
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        completionPromptSeen = completionPromptSeen + toPrompt.id
                        navController.navigate(Routes.rateWorker(toPrompt.id))
                    }) { androidx.compose.material3.Text(if (vm.language == AppLanguage.ML) "റേറ്റിംഗ് നൽകുക" else "Rate now") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { completionPromptSeen = completionPromptSeen + toPrompt.id }) {
                        androidx.compose.material3.Text(if (vm.language == AppLanguage.ML) "പിന്നീട്" else "Later")
                    }
                }
            )
        }
    }
}
