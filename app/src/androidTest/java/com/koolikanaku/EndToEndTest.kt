package com.koolikanaku

import androidx.test.platform.app.InstrumentationRegistry
import com.koolikanaku.ai.initDetector
import com.koolikanaku.capture.HashVerifier
import com.koolikanaku.capture.JobStorage
import com.koolikanaku.capture.PhotoStore
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SizeSource
import com.koolikanaku.domain.TaskSource
import com.koolikanaku.export.PdfExporter
import com.koolikanaku.ui.AppViewModel
import com.koolikanaku.ui.SizeMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Full 2-room job through the real ViewModel, detector, grime analyzer, pricing, hashing and PDF.
 * Needs bathroom.jpg and kitchen.jpg in /data/local/tmp/kk_e2e/ (adb push); skipped otherwise.
 */
class EndToEndTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val photoDir = File("/data/local/tmp/kk_e2e")

    @Before fun setUp() {
        initDetector(context)
        JobStorage.clear(context)
    }

    private fun addPhoto(vm: AppViewModel, roomId: String, name: String, role: PhotoRole, depressionDeg: Float? = null, holdHeightM: Float? = null) {
        val (file, _) = PhotoStore.createCaptureTarget(context)
        File(photoDir, name).copyTo(file, overwrite = true)
        vm.addPhoto(roomId, PhotoStore.processCapturedFile(context, file, role, depressionDeg, holdHeightM))
    }

    @Test fun savedJobResumesLikeAfterProcessDeath() {
        JobStorage.clear(context)
        val vm = AppViewModel()
        vm.attachStorage(context)
        vm.changeLanguage(com.koolikanaku.ui.AppLanguage.ML)
        vm.changeRateText("300")
        vm.startJob()
        val id = vm.addRoom()
        vm.addPhoto(id, com.koolikanaku.domain.EvidencePhoto("p1", PhotoRole.WIDE, android.net.Uri.parse("content://com.koolikanaku.fileprovider/evidence_photos/x.jpg"), "2026-01-01T00:00:00Z", "ab".repeat(32), 19.3f, 1.4f))
        vm.setManualSizeSqFt(id, 150.0)
        vm.selectAllTasks(id, true)
        assertTrue(vm.finishJob())
        vm.awaitPendingSave()

        val resumed = AppViewModel()
        resumed.attachStorage(context)
        assertEquals(vm.job, resumed.job)
        assertEquals(vm.quote, resumed.quote)
        assertEquals(vm.currentRoomId, resumed.currentRoomId)
        assertEquals(com.koolikanaku.ui.AppLanguage.ML, resumed.language)
        assertEquals("300", resumed.rateText)
        assertTrue(resumed.hasResumableJob())
        JobStorage.clear(context)
        val fresh = AppViewModel()
        fresh.attachStorage(context)
        assertEquals(null, fresh.job)
    }

    @Test fun afterPhotoVerificationAndTamperDetection(): Unit = runBlocking {
        assumeTrue("Missing test photos", File(photoDir, "bathroom.jpg").exists() && File(photoDir, "kitchen.jpg").exists())
        val vm = AppViewModel()
        vm.startJob()
        val id = vm.addRoom()
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.WIDE)
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.TOUGHEST)
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.AFTER)
        addPhoto(vm, id, "kitchen.jpg", PhotoRole.AFTER)   // retake replaces the first after photo
        vm.setManualSizeSqFt(id, 60.0)
        vm.goToRoomConfirmFromCapture(id)
        vm.analyzeRoom(context, id)
        val room = vm.job!!.rooms.single()
        assertEquals("one after photo per room", 1, room.photos.count { it.role == PhotoRole.AFTER })
        assertEquals("after photos are never analyzed", 1, room.photoAnalyses.size)
        assertTrue(room.photoAnalyses.none { a -> room.photos.first { it.id == a.photoId }.role == PhotoRole.AFTER })

        vm.selectAllTasks(id, true)
        assertTrue(vm.finishJob())
        val worst = room.photos.first { it.role == PhotoRole.TOUGHEST }.id
        val pdf = PdfExporter.exportBillPdf(context, vm.job!!, vm.quote!!, mapOf(id to worst))
        val bytes = context.contentResolver.openInputStream(pdf)!!.use { it.readBytes() }
        assertEquals("%PDF", String(bytes.copyOf(4)))

        // every saved photo still matches its recorded hash
        val checks = HashVerifier.checkSavedPhotos(context, vm.job!!)
        assertEquals(room.photos.size, checks.size)
        assertTrue(checks.all { it.intact })

        // the original file matches by hash; an unrelated file does not
        val after = room.photos.first { it.role == PhotoRole.AFTER }
        val match = HashVerifier.findMatch(vm.job!!, HashVerifier.hashOf(context, after.uri)!!)
        assertNotNull(match)
        assertEquals(PhotoRole.AFTER, match!!.photo.role)
        val unrelated = File(context.cacheDir, "unrelated.bin").apply { writeBytes(ByteArray(2048) { (it * 31).toByte() }) }
        assertEquals(null, HashVerifier.findMatch(vm.job!!, com.koolikanaku.capture.CryptoUtils.sha256(unrelated)))

        // editing a saved photo is detected
        val file = PhotoStore.getFileFromUri(context, after.uri)!!
        file.appendBytes(byteArrayOf(0))
        val rechecked = HashVerifier.checkSavedPhotos(context, vm.job!!)
        assertEquals(false, rechecked.first { it.photo.id == after.id }.intact)
        assertEquals(room.photos.size - 1, rechecked.count { it.intact })
    }

    @Test fun finishIsBlockedUntilEveryRoomHasASize() {
        val vm = AppViewModel()
        vm.startJob()
        assertEquals("no rooms", false, vm.finishJob())
        val id = vm.addRoom()
        assertEquals("room without size", false, vm.allRoomsHaveSize())
        assertEquals(false, vm.finishJob())
        assertEquals(null, vm.quote)
        vm.setManualSizeSqFt(id, 0.0)
        assertEquals("zero size is not a size", false, vm.finishJob())
        vm.setManualSizeSqFt(id, 120.0)
        assertEquals(true, vm.finishJob())
        assertTrue(vm.quote!!.totalPriceInr.low > 0)
    }

    @Test fun measureShotsAndSeveralCloseUps(): Unit = runBlocking {
        assumeTrue("Missing test photos", File(photoDir, "bathroom.jpg").exists() && File(photoDir, "kitchen.jpg").exists())
        val vm = AppViewModel()
        vm.startJob()
        val id = vm.addRoom()
        vm.changeSizeMethod(SizeMethod.MEASURE)
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.MEASURE_LENGTH, 30f, 1.4f)   // stale shot, retaken below
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.MEASURE_LENGTH, 19.3f, 1.4f)
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.MEASURE_WIDTH, 25f, 1.4f)
        assertEquals("retake replaces", 1, vm.job!!.rooms.single().photos.count { it.role == PhotoRole.MEASURE_LENGTH })
        addPhoto(vm, id, "bathroom.jpg", PhotoRole.WIDE)
        listOf("bathroom.jpg", "kitchen.jpg", "bathroom.jpg").forEach { addPhoto(vm, id, it, PhotoRole.TOUGHEST) }
        vm.goToRoomConfirmFromCapture(id)
        vm.analyzeRoom(context, id)
        assertEquals(null, vm.analysisError)
        val room = vm.job!!.rooms.single()
        val dims = room.dimensions!!
        assertEquals(SizeSource.MEASURED, dims.source)
        assertEquals(1.4 / Math.tan(Math.toRadians(19.3)) + 0.3, dims.length, 0.01)
        assertEquals(1.4 / Math.tan(Math.toRadians(25.0)) + 0.3, dims.width, 0.01)
        val toughIds = room.photos.filter { it.role == PhotoRole.TOUGHEST }.map { it.id }
        assertEquals("every close-up scored", toughIds.toSet(), vm.toughScores.keys.intersect(toughIds.toSet()))
        val worst = toughIds.map { vm.toughScores.getValue(it) }.maxWith(compareBy({ it.severity.ordinal }, { it.score }))
        assertEquals(worst, room.stain)
    }

    @Test fun twoRoomJobFlowsToBillAndPdf(): Unit = runBlocking {
        assumeTrue("Missing test photos", File(photoDir, "bathroom.jpg").exists() && File(photoDir, "kitchen.jpg").exists())
        val vm = AppViewModel()
        vm.attachStorage(context)
        vm.startJob()

        // Room 1: bathroom, preset size
        val r1 = vm.addRoom()
        addPhoto(vm, r1, "bathroom.jpg", PhotoRole.WIDE)
        addPhoto(vm, r1, "bathroom.jpg", PhotoRole.TOUGHEST)
        vm.changeSizeMethod(SizeMethod.PRESET); vm.changePresetIndex(0)
        vm.goToRoomConfirmFromCapture(r1)
        vm.analyzeRoom(context, r1)
        assertEquals(null, vm.analysisError)
        val room1 = vm.job!!.rooms.first { it.id == r1 }
        assertEquals(RoomType.BATHROOM, room1.suggestedRoomType)
        assertEquals("analysis ran on the real photo", 1280, room1.photoAnalyses.single().imageWidth)
        assertEquals(RoomType.BATHROOM, room1.roomType)
        assertTrue("toilet detected", room1.tasks.any { it.catalogKey == "toilet" && it.source == TaskSource.DETECTED })
        assertNotNull("grime score", room1.stain)
        assertTrue(room1.photoAnalyses.single().detections.none { it.label == "person" })
        assertTrue(room1.tasks.none { it.isSelected })   // opt-in
        vm.selectAllTasks(r1, true)
        vm.saveRoom()

        // Room 2: kitchen, manual size
        val r2 = vm.addRoom()
        addPhoto(vm, r2, "kitchen.jpg", PhotoRole.WIDE)
        addPhoto(vm, r2, "kitchen.jpg", PhotoRole.TOUGHEST)
        vm.changeSizeMethod(SizeMethod.ENTER); vm.changeEnterLengthFt("10"); vm.changeEnterWidthFt("12")
        vm.goToRoomConfirmFromCapture(r2)
        vm.analyzeRoom(context, r2)
        assertEquals(null, vm.analysisError)
        val room2 = vm.job!!.rooms.first { it.id == r2 }
        assertEquals(RoomType.KITCHEN, room2.roomType)
        assertEquals(SizeSource.MANUAL, room2.dimensions!!.source)
        assertEquals(120.0, room2.dimensions!!.computedAreaSqFt, 0.01)
        assertTrue(room2.tasks.any { it.catalogKey == "refrigerator" && it.source == TaskSource.DETECTED })
        vm.selectAllTasks(r2, true)
        vm.saveRoom()

        // Bill
        vm.finishJob()
        val quote = vm.quote!!
        assertEquals(2, quote.rooms.size)
        assertTrue(quote.totalPriceInr.low > 0 && quote.totalPriceInr.high > quote.totalPriceInr.low)
        assertEquals("same inputs, same price", quote, com.koolikanaku.domain.PricingEngine.quoteJob(vm.job!!))
        vm.job!!.rooms.flatMap { it.photos }.forEach {
            assertEquals(64, it.sha256Hash.length)
            assertEquals(it.sha256Hash, com.koolikanaku.capture.CryptoUtils.sha256(PhotoStore.getFileFromUri(context, it.uri)!!))
        }

        // PDF
        val pdf = PdfExporter.exportBillPdf(context, vm.job!!, quote)
        val bytes = context.contentResolver.openInputStream(pdf)!!.use { it.readBytes() }
        assertTrue("pdf size ${bytes.size}", bytes.size > 5_000)
        assertEquals("%PDF", String(bytes.copyOf(4)))

        // Resume: a new ViewModel loads the same analyzed job (detections, grime, tasks, quote) from disk.
        vm.awaitPendingSave()
        val resumed = AppViewModel()
        resumed.attachStorage(context)
        assertEquals(vm.job, resumed.job)
        assertEquals(vm.toughScores, resumed.toughScores)
        assertEquals(vm.quote, resumed.quote)
    }
}
