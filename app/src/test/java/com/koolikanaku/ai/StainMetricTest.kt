package com.koolikanaku.ai

import com.koolikanaku.domain.SeverityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StainMetricTest {

    private val size = 64
    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test fun flatSurfaceScoresZero() {
        val r = StainMetric.analyze(IntArray(size * size) { grey(200) }, size)
        assertEquals(0f, r.score, 0.001f)
        assertEquals(SeverityLevel.LIGHT, r.severity)
    }

    @Test fun darkBlotchesScoreHigherAndOrderBySeverity() {
        fun withBlotch(fraction: Double): Float {
            val px = IntArray(size * size) { grey(200) }
            val rows = (size * fraction).toInt()
            for (y in 0 until rows) for (x in 0 until size) px[y * size + x] = grey(60)
            return StainMetric.analyze(px, size).score
        }
        val light = withBlotch(0.0); val some = withBlotch(0.3); val lots = withBlotch(0.45)
        assertTrue(light < some && some < lots)
        assertEquals(SeverityLevel.HEAVY, StainMetric.severityFor(lots))
    }

    @Test fun labOfWhiteAndBlack() {
        val white = StainMetric.rgbToLab(0xFFFFFFFF.toInt()); val black = StainMetric.rgbToLab(0xFF000000.toInt())
        assertEquals(100f, white[0], 0.5f); assertEquals(0f, black[0], 0.5f)
        assertEquals(0f, white[1], 0.5f); assertEquals(0f, white[2], 0.5f)
    }
}
