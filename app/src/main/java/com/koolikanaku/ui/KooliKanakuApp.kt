package com.koolikanaku.ui

import android.content.res.Configuration
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import java.util.Locale
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.ClutterLevel
import com.koolikanaku.domain.DetectedObject
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.TaskSource
import com.koolikanaku.ui.components.CatalogEntry
import com.koolikanaku.ui.screens.AnalysisStageUi
import com.koolikanaku.ui.screens.AnalyzingScreen
import com.koolikanaku.ui.screens.BillMathRowUi
import com.koolikanaku.ui.screens.BillPhotoGroupUi
import com.koolikanaku.ui.screens.BillPhotoUi
import com.koolikanaku.ui.screens.BillRoomUi
import com.koolikanaku.ui.screens.BillScreen
import com.koolikanaku.ui.screens.BillTaskUi
import com.koolikanaku.ui.screens.ConfirmPhotoTab
import com.koolikanaku.ui.screens.CountRowUi
import com.koolikanaku.ui.screens.JobHubScreen
import com.koolikanaku.ui.screens.RoomCaptureScreen
import com.koolikanaku.ui.screens.RoomConfirmScreen
import com.koolikanaku.ui.screens.RoomSummaryUi
import com.koolikanaku.ui.screens.SetupScreen
import com.koolikanaku.ui.theme.KooliKanakuTheme
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Wires Aadi's six screens together behind a lightweight, ui-package-only navigation host
 * and dummy state, so the screens can be reviewed end to end before Abhinav's real
 * MainViewModel / AppNavGraph land. No ai/capture/measure/pricing logic is called here;
 * all numbers below are plain demo arithmetic standing in for the real engines.
 */
@Composable
fun KooliKanakuApp() {
    KooliKanakuTheme {
        val navController = rememberNavController()
        val demo = remember { DemoAppState() }
        // stringResource() reads LocalContext.current.resources, which otherwise follows
        // the system locale; this lets Setup's language toggle switch values/values-ml
        // immediately instead of requiring a device-wide language change.
        val baseContext = LocalContext.current
        val localizedContext = remember(demo.language) {
            val locale = if (demo.language == AppLanguage.ML) Locale("ml") else Locale.ENGLISH
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        }
        CompositionLocalProvider(LocalContext provides localizedContext) {
            NavHost(navController = navController, startDestination = "setup") {
                composable("setup") { SetupRoute(demo, navController) }
                composable("job_hub") { JobHubRoute(demo, navController) }
                composable("capture") { RoomCaptureRoute(demo, navController) }
                composable("analyzing") { AnalyzingRoute(demo, navController) }
                composable("confirm") { RoomConfirmRoute(demo, navController) }
                composable("bill") { BillRoute(demo, navController) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Demo-only catalog (bilingual), standing in for Abhinav's TaskCatalog at runtime.
// ---------------------------------------------------------------------------
private val DEMO_CATALOG = listOf(
    CatalogEntry("sink", "Sink & tap", "സിങ്കും ടാപ്പും", 10),
    CatalogEntry("oven", "Oven & hob", "ഓവനും ഹോബും", 15),
    CatalogEntry("cabinets", "Cabinet fronts", "അലമാര വാതിലുകൾ", 8),
    CatalogEntry("window", "Window glass", "ജനൽ ഗ്ലാസ്", 5),
    CatalogEntry("fridge", "Fridge, outside", "ഫ്രിഡ്ജ് (പുറം)", 8),
    CatalogEntry("counter", "Countertop", "കൗണ്ടർടോപ്പ്", 6),
    CatalogEntry("bed", "Bed & bedding area", "കട്ടിലും പരിസരവും", 10),
    CatalogEntry("shelves", "Dust shelves", "ഷെൽഫ് പൊടി തുടയ്ക്കൽ", 4)
)

private const val DEMO_MATERIAL_COST = 5.0

private fun CatalogEntry.toTask(source: TaskSource, quantity: Int = 1) = CleaningTask(
    id = UUID.randomUUID().toString(),
    catalogKey = key,
    titleEn = titleEn,
    titleMl = titleMl,
    quantity = quantity,
    minutesPerUnit = minutesPerUnit,
    materialCostInr = DEMO_MATERIAL_COST,
    isSelected = false,
    source = source
)

/** A room already saved to the job (precomputed display strings; no shared business logic). */
private class CompletedRoomDemo(
    val roomNumber: Int,
    val roomType: RoomType,
    val sqFt: Int,
    val sizeSourceLabel: String,
    val severity: SeverityLevel,
    val tasks: List<CleaningTask>,
    val payLow: Double,
    val payHigh: Double,
    val hoursLow: Double,
    val hoursHigh: Double
)

/** All mutable UI-only demo state for the whole flow, kept in the ui package. */
private class DemoAppState {
    var mode by mutableStateOf(AppMode.WORKER_ESTIMATE)
    var language by mutableStateOf(AppLanguage.EN)
    var rateWorker by mutableStateOf("160")
    var rateOwner by mutableStateOf("150")
    var paceFactor by mutableStateOf(1.0)
    var heightCm by mutableStateOf("")

    val completedRooms = mutableStateListOf<CompletedRoomDemo>()
    var nextRoomNumber by mutableStateOf(1)

    // --- Room Capture draft ---
    var sizeMethod by mutableStateOf(SizeMethod.MEASURE)
    var lengthShotTaken by mutableStateOf(false)
    var widthShotTaken by mutableStateOf(false)
    var enterLengthFt by mutableStateOf("11.7")
    var enterWidthFt by mutableStateOf("9.2")
    var presetIndex by mutableStateOf<Int?>(null)
    var widePhotoCount by mutableStateOf(0)
    var toughPhotoCount by mutableStateOf(0)
    var activeCaptureTarget by mutableStateOf<CaptureTarget?>(null)
    var isSteady by mutableStateOf(false)

    // --- Room Confirm draft ---
    var confirmPhotoTab by mutableStateOf(ConfirmPhotoTab.WIDE)
    var roomType by mutableStateOf(RoomType.KITCHEN)
    var sizeSqFt by mutableStateOf("107")
    var severity by mutableStateOf(SeverityLevel.HEAVY)
    var severityOverridden by mutableStateOf(false)
    val tasks = mutableStateListOf<CleaningTask>()

    // --- Bill ---
    var exportSheetVisible by mutableStateOf(false)

    fun resetCaptureDraft() {
        sizeMethod = SizeMethod.MEASURE
        lengthShotTaken = false
        widthShotTaken = false
        enterLengthFt = "11.7"
        enterWidthFt = "9.2"
        presetIndex = null
        widePhotoCount = 0
        toughPhotoCount = 0
        activeCaptureTarget = null
        isSteady = false
    }

    fun seedConfirmDraft() {
        confirmPhotoTab = ConfirmPhotoTab.WIDE
        roomType = RoomType.KITCHEN
        sizeSqFt = if (sizeMethod == SizeMethod.ENTER) {
            val l = enterLengthFt.toDoubleOrNull() ?: 0.0
            val w = enterWidthFt.toDoubleOrNull() ?: 0.0
            (l * w).roundToInt().toString()
        } else "107"
        severity = SeverityLevel.HEAVY
        severityOverridden = false
        tasks.clear()
        tasks.add(DEMO_CATALOG[0].toTask(TaskSource.DETECTED))
        tasks.add(DEMO_CATALOG[1].toTask(TaskSource.DETECTED))
        tasks.add(DEMO_CATALOG[2].toTask(TaskSource.TEMPLATE, quantity = 2))
    }

    fun sizeSourceLabel(): SizeSourceLabel = when (sizeMethod) {
        SizeMethod.MEASURE -> SizeSourceLabel.MEASURED
        SizeMethod.ENTER -> SizeSourceLabel.MANUAL
        SizeMethod.PRESET -> SizeSourceLabel.PRESET
    }
}

private enum class SizeSourceLabel { MEASURED, MANUAL, PRESET }

private fun severityMultiplier(level: SeverityLevel) = when (level) {
    SeverityLevel.LIGHT -> 1.0
    SeverityLevel.MODERATE -> 1.15
    SeverityLevel.HEAVY -> 1.35
}

private fun minutesPerSqFt(type: RoomType) = when (type) {
    RoomType.KITCHEN -> 0.35
    RoomType.BATHROOM -> 0.45
    RoomType.BEDROOM -> 0.20
    RoomType.LIVING_ROOM -> 0.20
    RoomType.OTHER -> 0.25
}

private fun round1(value: Double) = (Math.round(value * 10) / 10.0)
private fun round5(value: Double) = (Math.round(value / 5) * 5)

@Composable
private fun SetupRoute(demo: DemoAppState, nav: NavHostController) {
    SetupScreen(
        mode = demo.mode,
        onModeChange = { demo.mode = it },
        language = demo.language,
        onLanguageChange = { demo.language = it },
        rateText = if (demo.mode == AppMode.WORKER_ESTIMATE) demo.rateWorker else demo.rateOwner,
        onRateChange = { if (demo.mode == AppMode.WORKER_ESTIMATE) demo.rateWorker = it else demo.rateOwner = it },
        paceFactor = demo.paceFactor,
        onPaceChange = { demo.paceFactor = it },
        heightCm = demo.heightCm,
        onHeightChange = { demo.heightCm = it },
        onStartJob = { nav.navigate("job_hub") }
    )
}

@Composable
private fun JobHubRoute(demo: DemoAppState, nav: NavHostController) {
    val rooms = demo.completedRooms.map { room ->
        RoomSummaryUi(
            id = room.roomNumber.toString(),
            name = "Room ${room.roomNumber}",
            metaLine1 = "${room.sqFt} sq ft · ${room.sizeSourceLabel}",
            metaLine2 = "${room.tasks.size} tasks",
            timeText = "${round1(room.hoursLow)}–${round1(room.hoursHigh)} hrs",
            payText = "₹${round5(room.payLow)}–${round5(room.payHigh)}"
        )
    }
    val rate = if (demo.mode == AppMode.WORKER_ESTIMATE) demo.rateWorker else demo.rateOwner
    JobHubScreen(
        modeLabel = if (demo.mode == AppMode.WORKER_ESTIMATE) "Worker estimate" else "Owner quote",
        rateLabel = "₹$rate per hour",
        paceLabel = "Pace ${demo.paceFactor}×",
        rooms = rooms,
        totalText = if (rooms.isEmpty()) "—" else {
            val low = demo.completedRooms.sumOf { it.payLow }
            val high = demo.completedRooms.sumOf { it.payHigh }
            "₹${round5(low)}–${round5(high)}"
        },
        onAddRoom = {
            demo.resetCaptureDraft()
            nav.navigate("capture")
        },
        onRoomClick = { nav.navigate("confirm") },
        onFinish = { nav.navigate("bill") }
    )
}

@Composable
private fun RoomCaptureRoute(demo: DemoAppState, nav: NavHostController) {
    androidx.compose.runtime.LaunchedEffect(demo.activeCaptureTarget) {
        val target = demo.activeCaptureTarget
        if (target == CaptureTarget.LENGTH || target == CaptureTarget.WIDTH) {
            kotlinx.coroutines.delay(900)
            demo.isSteady = true
        }
    }
    val lengthResult = if (demo.lengthShotTaken) "21.0° → 3.56 m" else null
    val widthResult = if (demo.widthShotTaken) "26.5° → 2.81 m" else null
    val measureResultText = if (demo.lengthShotTaken && demo.widthShotTaken) "≈ 3.56 × 2.81 m · 107 sq ft" else null
    val enterArea = ((demo.enterLengthFt.toDoubleOrNull() ?: 0.0) * (demo.enterWidthFt.toDoubleOrNull() ?: 0.0)).roundToInt()
    val enterResultText = if (enterArea > 0 && demo.sizeMethod == SizeMethod.ENTER) "≈ $enterArea sq ft" else null

    val sizeReady = when (demo.sizeMethod) {
        SizeMethod.MEASURE -> demo.lengthShotTaken && demo.widthShotTaken
        SizeMethod.ENTER -> enterArea > 0
        SizeMethod.PRESET -> demo.presetIndex != null
    }
    val wideOk = demo.sizeMethod == SizeMethod.MEASURE || demo.widePhotoCount >= 1
    val toughOk = demo.toughPhotoCount >= 1
    val ready = sizeReady && wideOk && toughOk
    val totalPhotos = (if (demo.sizeMethod == SizeMethod.MEASURE) (if (demo.lengthShotTaken) 1 else 0) + (if (demo.widthShotTaken) 1 else 0) else 0) +
        demo.widePhotoCount + demo.toughPhotoCount
    val status = when {
        !sizeReady -> "Add the room size to continue"
        !wideOk -> "Add a wide photo to continue"
        !toughOk -> "Add a close-up of the toughest spot to continue"
        else -> "$totalPhotos photos ready · scanned after you tap Analyze"
    }

    RoomCaptureScreen(
        roomNumber = demo.nextRoomNumber,
        sizeMethod = demo.sizeMethod,
        onSizeMethodChange = { demo.sizeMethod = it },
        lengthShotTaken = demo.lengthShotTaken,
        widthShotTaken = demo.widthShotTaken,
        measureResultText = measureResultText,
        enterLengthFt = demo.enterLengthFt,
        onEnterLengthChange = { demo.enterLengthFt = it },
        enterWidthFt = demo.enterWidthFt,
        onEnterWidthChange = { demo.enterWidthFt = it },
        enterResultText = enterResultText,
        presetIndex = demo.presetIndex,
        presetLabels = listOf("Small" to "~60 sq ft", "Medium" to "~100 sq ft", "Large" to "~150 sq ft"),
        onPresetChange = { demo.presetIndex = it },
        widePhotos = List(demo.widePhotoCount) { android.net.Uri.EMPTY },
        toughPhotos = List(demo.toughPhotoCount) { android.net.Uri.EMPTY },
        canAddWide = demo.widePhotoCount < 3,
        canAddTough = demo.toughPhotoCount < 3,
        readyToAnalyze = ready,
        statusText = status,
        activeCaptureTarget = demo.activeCaptureTarget,
        isSteady = demo.isSteady,
        tiltText = "21.0°",
        distanceText = "3.26 m",
        sideText = "3.56 m",
        onOpenCamera = { target ->
            demo.activeCaptureTarget = target
            demo.isSteady = target != CaptureTarget.LENGTH && target != CaptureTarget.WIDTH
        },
        onCloseCamera = { demo.activeCaptureTarget = null },
        onShutter = {
            when (demo.activeCaptureTarget) {
                CaptureTarget.LENGTH -> demo.lengthShotTaken = true
                CaptureTarget.WIDTH -> demo.widthShotTaken = true
                CaptureTarget.WIDE -> demo.widePhotoCount = (demo.widePhotoCount + 1).coerceAtMost(3)
                CaptureTarget.TOUGHEST -> demo.toughPhotoCount = (demo.toughPhotoCount + 1).coerceAtMost(3)
                null -> {}
            }
            demo.activeCaptureTarget = null
        },
        onAnalyzeRoom = { nav.navigate("analyzing") },
        onBack = { nav.popBackStack() }
    )
}

@Composable
private fun AnalyzingRoute(demo: DemoAppState, nav: NavHostController) {
    val stages = listOf(
        AnalysisStageUi("Length shot: find objects", done = true, current = false, durationText = "0.4 s"),
        AnalysisStageUi("Width shot: find objects", done = true, current = false, durationText = "0.4 s"),
        AnalysisStageUi("Wide photo: find objects", done = true, current = false, durationText = "0.5 s"),
        AnalysisStageUi("Toughest spot: stain check", done = true, current = false, durationText = "0.3 s"),
        AnalysisStageUi("Merge counts across photos", done = true, current = false, durationText = "0.1 s")
    )
    AnalyzingScreen(
        stages = stages,
        isDone = true,
        onReviewRoom = {
            demo.seedConfirmDraft()
            nav.navigate("confirm")
        }
    )
}

@Composable
private fun RoomConfirmRoute(demo: DemoAppState, nav: NavHostController) {
    val wideDetections = listOf(
        DetectedObject("Cabinetry", 0.82f, RectF(0f, 2f, 300f, 170f)),
        DetectedObject("Window", 0.77f, RectF(330f, 4f, 570f, 160f)),
        DetectedObject("Sink", 0.69f, RectF(100f, 330f, 380f, 480f))
    )
    val available = DEMO_CATALOG.filter { entry -> demo.tasks.none { it.catalogKey == entry.key } }

    val floorMinutes = (demo.sizeSqFt.toDoubleOrNull() ?: 0.0) * minutesPerSqFt(demo.roomType)
    val selected = demo.tasks.filter { it.isSelected }
    val taskMinutes = selected.sumOf { it.quantity * it.minutesPerUnit }
    val materialCost = selected.sumOf { it.quantity * it.materialCostInr }
    val cond = severityMultiplier(demo.severity)
    val standardMinutes = (floorMinutes + taskMinutes) * cond
    val rate = (if (demo.mode == AppMode.WORKER_ESTIMATE) demo.rateWorker else demo.rateOwner).toDoubleOrNull() ?: 160.0
    val labour = standardMinutes / 60 * rate
    val price = labour + materialCost
    val expectedMinutes = standardMinutes * demo.paceFactor
    val spread = when (demo.sizeSourceLabel()) {
        SizeSourceLabel.MEASURED -> 0.2
        SizeSourceLabel.MANUAL -> 0.1
        SizeSourceLabel.PRESET -> 0.25
    }

    RoomConfirmScreen(
        roomNumber = demo.nextRoomNumber,
        photoTab = demo.confirmPhotoTab,
        onPhotoTabChange = { demo.confirmPhotoTab = it },
        wideDetections = wideDetections,
        wideImageWidth = 900,
        wideImageHeight = 600,
        toughSummaryText = "Stained 46% · Darker 18% · Score 71/100",
        roomType = demo.roomType,
        suggestedRoomType = RoomType.KITCHEN,
        onRoomTypeChange = { demo.roomType = it },
        sizeSqFt = demo.sizeSqFt,
        onSizeChange = { demo.sizeSqFt = it },
        sizeSourceLabel = when (demo.sizeSourceLabel()) {
            SizeSourceLabel.MEASURED -> "Measured · ±20%"
            SizeSourceLabel.MANUAL -> "Typed in · ±10%"
            SizeSourceLabel.PRESET -> "Preset · ±25%"
        },
        dimsNote = if (demo.sizeSourceLabel() == SizeSourceLabel.MEASURED) "3.56 × 2.81 m from your two shots" else null,
        severity = demo.severity,
        severityOverridden = demo.severityOverridden,
        suggestedSeverityLabel = "Heavy",
        onSeverityChange = {
            demo.severity = it
            demo.severityOverridden = it != SeverityLevel.HEAVY
        },
        severityMultiplier = { severityMultiplier(it) },
        clutterLevel = ClutterLevel.LOW,
        looseItemCount = 0,
        tasks = demo.tasks.toList(),
        language = demo.language,
        allSelected = demo.tasks.isNotEmpty() && demo.tasks.all { it.isSelected },
        onToggleTask = { id ->
            val idx = demo.tasks.indexOfFirst { it.id == id }
            if (idx >= 0) demo.tasks[idx] = demo.tasks[idx].copy(isSelected = !demo.tasks[idx].isSelected)
        },
        onIncrementTask = { id ->
            val idx = demo.tasks.indexOfFirst { it.id == id }
            if (idx >= 0) demo.tasks[idx] = demo.tasks[idx].copy(quantity = (demo.tasks[idx].quantity + 1).coerceAtMost(9))
        },
        onDecrementTask = { id ->
            val idx = demo.tasks.indexOfFirst { it.id == id }
            if (idx >= 0) demo.tasks[idx] = demo.tasks[idx].copy(quantity = (demo.tasks[idx].quantity - 1).coerceAtLeast(1))
        },
        onSelectAll = { select ->
            for (i in demo.tasks.indices) demo.tasks[i] = demo.tasks[i].copy(isSelected = select)
        },
        availableCatalog = available,
        onAddCatalogTask = { entry -> demo.tasks.add(entry.toTask(TaskSource.MANUAL)) },
        countRows = listOf(
            CountRowUi("Cabinetry", length = 1, width = 1, wide = 2, count = 2),
            CountRowUi("Window", length = 1, width = 0, wide = 1, count = 1),
            CountRowUi("Sink", length = 1, width = 0, wide = 1, count = 1)
        ),
        estimateHoursText = "${round1(expectedMinutes / 60 * (1 - spread))}–${round1(expectedMinutes / 60 * (1 + spread))} hrs",
        estimatePayText = "₹${round5(price * (1 - spread))}–${round5(price * (1 + spread))}",
        mathLine1 = "(${floorMinutes.roundToInt()} floor + $taskMinutes tasks) min × ${"%.2f".format(cond)} dirt",
        mathLine2 = "₹${labour.roundToInt()} labour + ₹${materialCost.roundToInt()} materials · ±${(spread * 100).roundToInt()}%",
        onSaveRoom = {
            demo.completedRooms.add(
                CompletedRoomDemo(
                    roomNumber = demo.nextRoomNumber,
                    roomType = demo.roomType,
                    sqFt = demo.sizeSqFt.toIntOrNull() ?: 0,
                    sizeSourceLabel = when (demo.sizeSourceLabel()) {
                        SizeSourceLabel.MEASURED -> "measured ±20%"
                        SizeSourceLabel.MANUAL -> "typed in ±10%"
                        SizeSourceLabel.PRESET -> "preset ±25%"
                    },
                    severity = demo.severity,
                    tasks = demo.tasks.toList(),
                    payLow = price * (1 - spread),
                    payHigh = price * (1 + spread),
                    hoursLow = expectedMinutes / 60 * (1 - spread),
                    hoursHigh = expectedMinutes / 60 * (1 + spread)
                )
            )
            demo.nextRoomNumber += 1
            nav.navigate("job_hub") { popUpTo("job_hub") { inclusive = true } }
        },
        onBack = { nav.popBackStack() }
    )
}

@Composable
private fun BillRoute(demo: DemoAppState, nav: NavHostController) {
    val isOwner = demo.mode != AppMode.WORKER_ESTIMATE
    val rate = if (isOwner) demo.rateOwner else demo.rateWorker
    val totalLow = demo.completedRooms.sumOf { it.payLow }
    val totalHigh = demo.completedRooms.sumOf { it.payHigh }
    val hoursLow = demo.completedRooms.sumOf { it.hoursLow }
    val hoursHigh = demo.completedRooms.sumOf { it.hoursHigh }

    val billRooms = demo.completedRooms.map { room ->
        BillRoomUi(
            name = "Room ${room.roomNumber}",
            sizeText = "${room.sqFt} sq ft · ${room.sizeSourceLabel}",
            payText = "₹${round5(room.payLow)}–${round5(room.payHigh)}",
            timeText = "${round1(room.hoursLow)}–${round1(room.hoursHigh)} h",
            tasks = room.tasks.filter { it.isSelected }.map {
                BillTaskUi(it.titleFor(demo.language), it.quantity, "${it.quantity * it.minutesPerUnit} min", it.source.displayName())
            },
            severityLabel = room.severity.displayName(),
            severityMultText = "×${"%.2f".format(severityMultiplier(room.severity))}",
            severityEdited = false,
            aiSaidText = "",
            clutterLabel = ClutterLevel.LOW.displayName(),
            clutterMultText = "×1.00",
            mathRows = listOf(
                BillMathRowUi("Floor", "${room.sqFt} sq ft × ${minutesPerSqFt(room.roomType)}"),
                BillMathRowUi("Price", "₹${round5(room.payLow)}–${round5(room.payHigh)}", emphasized = true)
            )
        )
    }

    BillScreen(
        title = if (isOwner) "Cleaning Quotation" else "Cleaning Estimate",
        subtitleLine = "23 Oct 2026 · ${if (isOwner) "Owner quote" else "Worker estimate"} · ₹$rate per hour",
        totalTimeText = "${round1(hoursLow)}–${round1(hoursHigh)}",
        payLabel = if (isOwner) "Quote" else "Pay",
        totalPayText = "₹${round5(totalLow)}–${round5(totalHigh)}",
        rangeNote = "ranges show how sure we are of the room sizes",
        rooms = billRooms,
        photoGroups = demo.completedRooms.map { room ->
            BillPhotoGroupUi(
                title = "Room ${room.roomNumber}",
                photos = listOf(
                    BillPhotoUi("Wide", "9f3c2a7e…", "9f3c2a7e41b8d065f7a1c3e92b4d806a5e17c9f3b2a4d68e0c71f5a93b2e6d14", "23 Oct 2026 · 15:50 IST"),
                    BillPhotoUi("Toughest", "4e81b7c0…", "4e81b7c03d9a2f56e1c8b4a7093d5f2e6b1a8c4d7f30e925b6a1d3c8e7f04b29", "23 Oct 2026 · 15:52 IST")
                )
            )
        },
        disclaimer = "Estimate computed on-device. Hashes are of the original captures. Tamper-evident, not tamper-proof: a phone clock can be changed.",
        exportSheetVisible = demo.exportSheetVisible,
        fileNameText = "${if (isOwner) "Cleaning-Quotation" else "Cleaning-Estimate"}-2026-10-23.pdf",
        pdfMetaText = "2 pages · English only",
        onExportPdf = { demo.exportSheetVisible = true },
        onShare = { },
        onDismissExportSheet = { demo.exportSheetVisible = false },
        onBack = { nav.popBackStack() }
    )
}
