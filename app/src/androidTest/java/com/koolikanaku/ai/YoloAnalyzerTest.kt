package com.koolikanaku.ai

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YoloAnalyzerTest {

    @Before fun setUp() {
        initDetector(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun labelsFileHas80CocoClasses() {
        val labels = LabelLoader.load(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals(80, labels.size)
        assertEquals("person", labels.first())
    }

    @Test fun bothDetectorsLoadAndTheSecondaryHas601Classes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("secondary (Open Images) detector loaded", YoloAnalyzer.hasSecondary)
        assertEquals(601, LabelLoader.load(context, "labels_oiv7.txt").size)
    }

    @Test fun blankImageProducesNoCrashAndBoxesStayInBounds() = runBlocking {
        val bmp = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val dets = detectObjects(bmp)
        dets.forEach {
            assertTrue(it.confidence >= CONFIDENCE_THRESHOLD)
            assertTrue(it.boundingBox.left >= 0f && it.boundingBox.right <= 1280f)
            assertTrue(it.boundingBox.top >= 0f && it.boundingBox.bottom <= 960f)
        }
    }

    @Test fun stainOnFlatSurfaceIsLight() = runBlocking {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 200, 200)) }
        assertEquals(com.koolikanaku.domain.SeverityLevel.LIGHT, analyzeStain(bmp).severity)
    }
}
