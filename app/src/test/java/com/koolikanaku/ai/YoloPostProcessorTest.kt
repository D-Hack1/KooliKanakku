package com.koolikanaku.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class YoloPostProcessorTest {

    // 4 box rows + 3 classes, 3 anchors, features-first layout
    private fun tensor() = arrayOf(
        floatArrayOf(0.5f, 0.52f, 0.2f),   // cx
        floatArrayOf(0.5f, 0.50f, 0.2f),   // cy
        floatArrayOf(0.4f, 0.40f, 0.1f),   // w
        floatArrayOf(0.4f, 0.40f, 0.1f),   // h
        floatArrayOf(0.9f, 0.0f, 0.1f),    // class 0
        floatArrayOf(0.0f, 0.8f, 0.1f),    // class 1 (same object as anchor 0, other label)
        floatArrayOf(0.0f, 0.0f, 0.6f)     // class 2
    )

    @Test fun parseDropsLowConfidenceAndPicksBestClass() {
        val c = YoloPostProcessor.parse(tensor(), true, 7, 3, 0.30f)
        assertEquals(3, c.size)
        assertEquals(listOf(0, 1, 2), c.map { it.classIndex })
        assertEquals(0.3f, c[0].left, 1e-4f)
    }

    @Test fun nmsIsClassAgnostic() {
        val kept = YoloPostProcessor.nms(YoloPostProcessor.parse(tensor(), true, 7, 3, 0.30f), 0.5f)
        assertEquals(listOf(0, 2), kept.map { it.classIndex })
    }

    @Test fun transposedLayoutMatches() {
        val t = tensor()
        val tr = Array(3) { a -> FloatArray(7) { f -> t[f][a] } }
        val c = YoloPostProcessor.parse(tr, false, 7, 3, 0.30f)
        assertEquals(listOf(0, 1, 2), c.map { it.classIndex })
    }
}
