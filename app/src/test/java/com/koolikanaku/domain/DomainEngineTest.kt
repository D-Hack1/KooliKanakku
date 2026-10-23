package com.koolikanaku.domain

import android.graphics.RectF
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainEngineTest {

    private val dummyUri: Uri = org.mockito.Mockito.mock(Uri::class.java)

    private fun makePhoto(id: String, role: PhotoRole): EvidencePhoto =
        EvidencePhoto(
            id = id,
            role = role,
            uri = dummyUri,
            timestampIso = "2026-10-23T12:00:00Z",
            sha256Hash = "dummyhash"
        )

    private fun makeAnalysis(photoId: String, labels: List<String>): PhotoAnalysis =
        PhotoAnalysis(
            photoId = photoId,
            imageWidth = 640,
            imageHeight = 640,
            detections = labels.map { label ->
                DetectedObject(
                    label = label,
                    confidence = 0.85f,
                    boundingBox = RectF(0f, 0f, 100f, 100f)
                )
            }
        )

    // ==========================================
    // 1. RoomMerger Tests
    // ==========================================

    @Test
    fun testRoomMerger_widePhotoWith2BedsPlusWidePhotoWith1Bed_returns2Beds() {
        val p1 = makePhoto("w1", PhotoRole.WIDE)
        val p2 = makePhoto("w2", PhotoRole.WIDE)

        val a1 = makeAnalysis("w1", listOf("bed", "bed"))
        val a2 = makeAnalysis("w2", listOf("bed"))

        val counts = RoomMerger.mergedCounts(listOf(p1, p2), listOf(a1, a2))
        assertEquals(2, counts["bed"])
    }

    @Test
    fun testRoomMerger_twoMeasureShotsWith1BedEachNoWide_returns2Beds() {
        val pLength = makePhoto("m_len", PhotoRole.MEASURE_LENGTH)
        val pWidth = makePhoto("m_wid", PhotoRole.MEASURE_WIDTH)

        val aLength = makeAnalysis("m_len", listOf("bed"))
        val aWidth = makeAnalysis("m_wid", listOf("bed"))

        val counts = RoomMerger.mergedCounts(listOf(pLength, pWidth), listOf(aLength, aWidth))
        assertEquals(2, counts["bed"])
    }

    @Test
    fun testRoomMerger_oneMeasureShotWith1BedPlusWidePhotoWith2Beds_returns2Beds() {
        val pLength = makePhoto("m_len", PhotoRole.MEASURE_LENGTH)
        val pWide = makePhoto("w1", PhotoRole.WIDE)

        val aLength = makeAnalysis("m_len", listOf("bed"))
        val aWide = makeAnalysis("w1", listOf("bed", "bed"))

        val counts = RoomMerger.mergedCounts(listOf(pLength, pWide), listOf(aLength, aWide))
        assertEquals(2, counts["bed"])
    }

    @Test
    fun testRoomMerger_ignoresToughestAndAfterPhotos() {
        val pToughest = makePhoto("t1", PhotoRole.TOUGHEST)
        val pAfter = makePhoto("a1", PhotoRole.AFTER)

        val aT = makeAnalysis("t1", listOf("toilet", "toilet"))
        val aA = makeAnalysis("a1", listOf("toilet"))

        val counts = RoomMerger.mergedCounts(listOf(pToughest, pAfter), listOf(aT, aA))
        assertTrue(counts.isEmpty())
    }

    @Test
    fun testRoomMerger_clutterAndRoomTypeSuggestion() {
        val counts = mapOf(
            "toilet" to 1,
            "bottle" to 3,
            "cup" to 2
        )
        // 3 + 2 = 5 clutter -> MEDIUM (3..6)
        assertEquals(ClutterLevel.MEDIUM, RoomMerger.clutterLevel(counts))
        assertEquals(RoomType.BATHROOM, RoomMerger.suggestRoomType(counts))
    }

    // ==========================================
    // 2. PricingEngine Tests
    // ==========================================

    @Test
    fun testPricingEngine_handComputedKitchenComparison() {
        // Hand-computed room: 100 sq.ft Kitchen
        val dimensionsManual = RoomDimensions(
            length = 10.0,
            width = 10.0,
            unit = LengthUnit.FEET,
            computedAreaSqFt = 100.0,
            source = SizeSource.MANUAL
        )

        val gasStove = CleaningTask(
            id = "k_gas",
            catalogKey = "gas stove",
            titleEn = "Gas Stove",
            titleMl = "ഗ്യാസ് സ്റ്റൗ",
            quantity = 1,
            minutesPerUnit = 15,
            materialCostInr = 25.0,
            isSelected = true,
            source = TaskSource.DETECTED
        )

        val countertop = CleaningTask(
            id = "k_counter",
            catalogKey = "countertop",
            titleEn = "Countertop",
            titleMl = "സ്ലാബ്",
            quantity = 1,
            minutesPerUnit = 12,
            materialCostInr = 15.0,
            isSelected = true,
            source = TaskSource.DETECTED
        )

        val unselectedTask = CleaningTask(
            id = "k_sink",
            catalogKey = "sink",
            titleEn = "Sink",
            titleMl = "സിങ്ക്",
            quantity = 1,
            minutesPerUnit = 8,
            materialCostInr = 10.0,
            isSelected = false,
            source = TaskSource.TEMPLATE
        )

        val room = RoomEntry(
            id = "room_kitchen",
            roomType = RoomType.KITCHEN,
            dimensions = dimensionsManual,
            stain = StainResult(severity = SeverityLevel.MODERATE, score = 55f),
            clutter = ClutterLevel.MEDIUM,
            tasks = listOf(gasStove, countertop, unselectedTask)
        )

        // Worker Job: rate = 250, pace = 1.15
        val workerJob = Job(
            id = "job_worker",
            mode = AppMode.WORKER_ESTIMATE,
            createdIso = "2026-10-23T12:00:00Z",
            hourlyRateInr = 250.0,
            paceFactor = 1.15,
            holdHeightM = 1.25f,
            rooms = listOf(room)
        )

        val quote = PricingEngine.quoteRoom(room, workerJob)

        // Hand calculation:
        // baseFloor = 100 * 0.35 = 35.0
        // taskMinutes = (1 * 15) + (1 * 12) = 27.0
        // mClutter = 1.10, mGrime = 1.15 -> mCond = 1.0 + 0.10 + 0.15 = 1.25
        // standardMinutes = (35.0 + 27.0) * 1.25 = 77.5
        // expectedMinutes = 77.5 * 1.15 = 89.125
        // materials = 25.0 + 15.0 = 40.0
        // price = (77.5 / 60.0) * 250.0 + 40.0 = 362.9166666666667
        // spread for MANUAL = 0.10 -> low = 362.9167 * 0.9, high = 362.9167 * 1.1
        val expectedStandard = 77.5
        val expectedMinutesWorker = 89.125
        val expectedPrice = (77.5 / 60.0) * 250.0 + 40.0

        assertEquals(expectedStandard * 0.90, quote.standardMinutes.low, 0.001)
        assertEquals(expectedStandard * 1.10, quote.standardMinutes.high, 0.001)

        assertEquals(expectedMinutesWorker * 0.90, quote.expectedMinutes.low, 0.001)
        assertEquals(expectedMinutesWorker * 1.10, quote.expectedMinutes.high, 0.001)

        assertEquals(expectedPrice * 0.90, quote.priceInr.low, 0.01)
        assertEquals(expectedPrice * 1.10, quote.priceInr.high, 0.01)

        // Owner Mode verification: pace forced to 1.0
        val ownerJob = Job(
            id = "job_owner",
            mode = AppMode.OWNER_QUOTE,
            createdIso = "2026-10-23T12:00:00Z",
            hourlyRateInr = 250.0,
            paceFactor = 1.15, // should be ignored in OWNER_QUOTE
            holdHeightM = 1.25f,
            rooms = listOf(room)
        )
        val ownerQuote = PricingEngine.quoteRoom(room, ownerJob)
        assertEquals(expectedStandard * 0.90, ownerQuote.expectedMinutes.low, 0.001)
        assertEquals(expectedStandard * 1.10, ownerQuote.expectedMinutes.high, 0.001)

        // Test Spreads: MEASURED (20%) and PRESET (25%)
        val roomMeasured = room.copy(dimensions = dimensionsManual.copy(source = SizeSource.MEASURED))
        val quoteMeasured = PricingEngine.quoteRoom(roomMeasured, workerJob)
        assertEquals(expectedPrice * 0.80, quoteMeasured.priceInr.low, 0.01)
        assertEquals(expectedPrice * 1.20, quoteMeasured.priceInr.high, 0.01)

        val roomPreset = room.copy(dimensions = dimensionsManual.copy(source = SizeSource.PRESET))
        val quotePreset = PricingEngine.quoteRoom(roomPreset, workerJob)
        assertEquals(expectedPrice * 0.75, quotePreset.priceInr.low, 0.01)
        assertEquals(expectedPrice * 1.25, quotePreset.priceInr.high, 0.01)
    }

    @Test
    fun testPricingEngine_zeroTasks_fallsBackToBaseFloor() {
        val room = RoomEntry(
            id = "empty_room",
            roomType = RoomType.BEDROOM,
            dimensions = RoomDimensions(10.0, 10.0, LengthUnit.FEET, 100.0, SizeSource.MANUAL),
            tasks = emptyList()
        )
        val job = Job("j1", AppMode.WORKER_ESTIMATE, "iso", 250.0, 1.0, 1.25f, listOf(room))

        val quote = PricingEngine.quoteRoom(room, job)
        // 100 sq ft bedroom @ 0.20 min/sqft = 20 mins. price = (20/60) * 250 = 83.33
        val expectedStandard = 20.0
        val expectedPrice = (20.0 / 60.0) * 250.0

        assertEquals(expectedStandard * 0.90, quote.standardMinutes.low, 0.01)
        assertEquals(expectedPrice * 0.90, quote.priceInr.low, 0.01)
    }

    @Test
    fun testPricingEngine_zeroRooms_returnsZeroTotals() {
        val emptyJob = Job("j_empty", AppMode.WORKER_ESTIMATE, "iso", 250.0, 1.0, 1.25f, emptyList())
        val jobQuote = PricingEngine.quoteJob(emptyJob)

        assertEquals(0.0, jobQuote.totalStandardMinutes.low, 0.001)
        assertEquals(0.0, jobQuote.totalStandardMinutes.high, 0.001)
        assertEquals(0.0, jobQuote.totalPriceInr.low, 0.001)
        assertEquals(0.0, jobQuote.totalPriceInr.high, 0.001)
    }

    // ==========================================
    // 3. TaskMapper Tests
    // ==========================================

    @Test
    fun testTaskMapper_detectedReplacesTemplateForSameKey() {
        val mergedCounts = mapOf("toilet" to 2)
        val tasks = TaskMapper.buildTasksFromAnalysis(
            roomId = "r1",
            roomType = RoomType.BATHROOM,
            mergedCounts = mergedCounts,
            existingTasks = emptyList()
        )

        val toiletTask = tasks.find { it.catalogKey == "toilet" }
        assertNotNull(toiletTask)
        assertEquals(TaskSource.DETECTED, toiletTask?.source)
        assertEquals(2, toiletTask?.quantity)
        assertFalse(toiletTask?.isSelected ?: true)

        // Ensure other bathroom template tasks exist with source TEMPLATE
        val sinkTask = tasks.find { it.catalogKey == "sink" }
        assertNotNull(sinkTask)
        assertEquals(TaskSource.TEMPLATE, sinkTask?.source)
        assertEquals(1, sinkTask?.quantity)
    }

    @Test
    fun testTaskMapper_changingRoomTypeRegeneratesTemplatesOnly() {
        val detectedToilet = CleaningTask(
            id = "r1_toilet",
            catalogKey = "toilet",
            titleEn = "Toilet",
            titleMl = "ടോയ്ലറ്റ്",
            quantity = 2,
            minutesPerUnit = 15,
            materialCostInr = 25.0,
            isSelected = true,
            source = TaskSource.DETECTED
        )
        val manualTask = CleaningTask(
            id = "r1_manual_fan",
            catalogKey = "ceiling fan",
            titleEn = "Fan",
            titleMl = "ഫാൻ",
            quantity = 1,
            minutesPerUnit = 6,
            materialCostInr = 5.0,
            isSelected = true,
            source = TaskSource.MANUAL
        )
        val oldTemplateSink = CleaningTask(
            id = "r1_sink",
            catalogKey = "sink",
            titleEn = "Sink",
            titleMl = "സിങ്ക്",
            quantity = 1,
            minutesPerUnit = 8,
            materialCostInr = 10.0,
            isSelected = true,
            source = TaskSource.TEMPLATE
        )

        val initialTasks = listOf(detectedToilet, manualTask, oldTemplateSink)

        // Change from BATHROOM to BEDROOM
        val updatedTasks = TaskMapper.updateRoomTypeTemplates(
            roomId = "r1",
            newRoomType = RoomType.BEDROOM,
            currentTasks = initialTasks
        )

        // Old TEMPLATE (sink) must be dropped
        assertNull(updatedTasks.find { it.catalogKey == "sink" })

        // DETECTED (toilet) and MANUAL (fan) must be preserved with their selections
        val keptToilet = updatedTasks.find { it.catalogKey == "toilet" }
        assertNotNull(keptToilet)
        assertEquals(TaskSource.DETECTED, keptToilet?.source)
        assertTrue(keptToilet?.isSelected ?: false)

        val keptFan = updatedTasks.find { it.catalogKey == "ceiling fan" }
        assertNotNull(keptFan)
        assertEquals(TaskSource.MANUAL, keptFan?.source)
        assertTrue(keptFan?.isSelected ?: false)

        // New BEDROOM templates should be added (e.g., "bed", "wardrobe")
        val bedTask = updatedTasks.find { it.catalogKey == "bed" }
        assertNotNull(bedTask)
        assertEquals(TaskSource.TEMPLATE, bedTask?.source)
        assertFalse(bedTask?.isSelected ?: true)
    }
}
