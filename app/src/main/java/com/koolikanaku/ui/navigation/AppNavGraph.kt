package com.koolikanaku.ui.navigation

import androidx.compose.runtime.Composable
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
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    vm: AppViewModel = viewModel()
) {
    NavHost(navController = navController, startDestination = Routes.SETUP) {

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
                    payText = quote?.let { "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}" } ?: "-"
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
                onFinish = {
                    vm.finishJob()
                    navController.navigate(Routes.BILL)
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
            val onLength = rememberCameraFallbackLauncher(
                role = PhotoRole.MEASURE_LENGTH,
                onPhotoCaptured = { vm.addPhoto(roomId, it) },
                onError = { vm.onCaptureError(it) }
            )
            val onWidth = rememberCameraFallbackLauncher(
                role = PhotoRole.MEASURE_WIDTH,
                onPhotoCaptured = { vm.addPhoto(roomId, it) },
                onError = { vm.onCaptureError(it) }
            )

            fun launchFor(target: CaptureTarget) {
                when (target) {
                    CaptureTarget.WIDE -> onWide()
                    CaptureTarget.TOUGHEST -> onTough()
                    CaptureTarget.LENGTH -> onLength()
                    CaptureTarget.WIDTH -> onWidth()
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

            val widePhotoUris = room.photos.filter { it.role == PhotoRole.WIDE }.map { it.uri }
            val toughPhotoUris = room.photos.filter { it.role == PhotoRole.TOUGHEST }.map { it.uri }
            val wideCount = widePhotoUris.size
            val toughCount = toughPhotoUris.size
            val lengthTaken = room.photos.any { it.role == PhotoRole.MEASURE_LENGTH }
            val widthTaken = room.photos.any { it.role == PhotoRole.MEASURE_WIDTH }
            val sizeStepDone = when (vm.sizeMethod) {
                SizeMethod.MEASURE -> lengthTaken && widthTaken
                SizeMethod.ENTER -> vm.enterLengthFt.toDoubleOrNull() != null && vm.enterWidthFt.toDoubleOrNull() != null
                SizeMethod.PRESET -> vm.presetIndex != null
            }
            val readyToAnalyze = sizeStepDone && wideCount >= 1 && toughCount >= 1

            RoomCaptureScreen(
                roomNumber = (vm.job?.rooms?.indexOfFirst { it.id == roomId } ?: 0) + 1,
                sizeMethod = vm.sizeMethod,
                onSizeMethodChange = vm::changeSizeMethod,
                lengthShotTaken = lengthTaken,
                widthShotTaken = widthTaken,
                measureResultText = if (lengthTaken && widthTaken) "Shots captured - enter exact size on the next screen" else null,
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
                widePhotos = widePhotoUris,
                toughPhotos = toughPhotoUris,
                canAddWide = wideCount < 4,
                canAddTough = toughCount < 4,
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
                    vm.goToRoomConfirmFromCapture(roomId)
                    navController.navigate(Routes.analyzing(roomId))
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
            val stageLabels = listOf("Merging photos", "Building checklist", "Ready")

            LaunchedEffect(roomId) {
                stageIndex = 0
                delay(300)
                stageIndex = 1
                delay(300)
                stageIndex = 2
            }

            AnalyzingScreen(
                stages = stageLabels.mapIndexed { i, label ->
                    AnalysisStageUi(label = label, done = i < stageIndex, current = i == stageIndex, durationText = "")
                },
                isDone = stageIndex >= stageLabels.lastIndex,
                onReviewRoom = {
                    navController.navigate(Routes.roomConfirm(roomId)) {
                        popUpTo(Routes.roomCapture(roomId)) { inclusive = true }
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
                widePhotoUri = room.photos.lastOrNull { it.role == PhotoRole.WIDE }?.uri,
                toughPhotoUri = room.photos.lastOrNull { it.role == PhotoRole.TOUGHEST }?.uri,
                toughSummaryText = "${room.toughPhotoCount()} close-up photo(s). No automatic grime score - set severity below.",
                roomType = room.roomType,
                suggestedRoomType = null,
                onRoomTypeChange = { vm.setRoomType(roomId, it) },
                sizeSqFt = sizeText,
                onSizeChange = { value ->
                    sizeText = value
                    value.toDoubleOrNull()?.let { vm.setManualSizeSqFt(roomId, it) }
                },
                sizeSourceLabel = room.dimensions?.source?.name ?: "MANUAL",
                dimsNote = null,
                severity = room.severityOverride ?: SeverityLevel.LIGHT,
                severityOverridden = room.severityOverride != null,
                suggestedSeverityLabel = "N/A",
                onSeverityChange = { vm.overrideSeverity(roomId, it) },
                severityMultiplier = { level -> com.koolikanaku.domain.Constants.GRIME_MULTIPLIER[level] ?: 1.0 },
                clutterLevel = room.clutter,
                looseItemCount = 0,
                tasks = room.tasks,
                language = vm.language,
                allSelected = room.tasks.isNotEmpty() && room.tasks.all { it.isSelected },
                onToggleTask = { vm.toggleTask(roomId, it) },
                onIncrementTask = { vm.incrementTask(roomId, it) },
                onDecrementTask = { vm.decrementTask(roomId, it) },
                onSelectAll = { vm.selectAllTasks(roomId, it) },
                availableCatalog = availableCatalog,
                onAddCatalogTask = { vm.addCatalogTask(roomId, it.key) },
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
                    severityLabel = (room.severityOverride ?: SeverityLevel.LIGHT).name,
                    severityMultText = "x${com.koolikanaku.domain.Constants.GRIME_MULTIPLIER[room.severityOverride ?: SeverityLevel.LIGHT]}",
                    severityEdited = room.severityOverride != null,
                    aiSaidText = "manual",
                    clutterLabel = room.clutter.name,
                    clutterMultText = "x${com.koolikanaku.domain.Constants.CLUTTER_MULTIPLIER[room.clutter]}",
                    mathRows = listOfNotNull(
                        roomQuote?.let { BillMathRowUi("Standard minutes", "${it.standardMinutes.low.toInt()}-${it.standardMinutes.high.toInt()}") },
                        roomQuote?.let { BillMathRowUi("Price", "₹${it.priceInr.low.toInt()}-₹${it.priceInr.high.toInt()}", emphasized = true) }
                    )
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

            BillScreen(
                title = if (job.mode == AppMode.OWNER_QUOTE) "Cleaning Quotation" else "Cleaning Estimate",
                subtitleLine = "${job.rooms.size} room(s)",
                totalTimeText = "${formatHours(jobQuote.totalExpectedMinutes.low)}-${formatHours(jobQuote.totalExpectedMinutes.high)}",
                payLabel = if (job.mode == AppMode.OWNER_QUOTE) "Quoted" else "You earn",
                totalPayText = "₹${jobQuote.totalPriceInr.low.toInt()}-₹${jobQuote.totalPriceInr.high.toInt()}",
                rangeNote = "Range reflects how each room's size was obtained.",
                rooms = billRooms,
                photoGroups = photoGroups,
                disclaimer = "Estimate computed on-device. Hashes are of the original captures.",
                exportSheetVisible = exportSheetVisible,
                fileNameText = lastPdfUri?.lastPathSegment ?: "cleaning_estimate.pdf",
                pdfMetaText = "${job.rooms.size} rooms",
                onExportPdf = {
                    val uri = PdfExporter.exportBillPdf(context, job, jobQuote)
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
    }
}
