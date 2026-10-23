package com.koolikanaku

import androidx.test.platform.app.InstrumentationRegistry
import com.koolikanaku.ai.initDetector
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
    }

    private fun addPhoto(vm: AppViewModel, roomId: String, name: String, role: PhotoRole) {
        val (file, _) = PhotoStore.createCaptureTarget(context)
        File(photoDir, name).copyTo(file, overwrite = true)
        vm.addPhoto(roomId, PhotoStore.processCapturedFile(context, file, role))
    }

    @Test fun twoRoomJobFlowsToBillAndPdf(): Unit = runBlocking {
        assumeTrue("Missing test photos", File(photoDir, "bathroom.jpg").exists() && File(photoDir, "kitchen.jpg").exists())
        val vm = AppViewModel()
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
    }
}
