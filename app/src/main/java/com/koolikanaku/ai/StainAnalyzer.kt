package com.koolikanaku.ai

import android.graphics.Bitmap
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.StainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

// All tunables live here; retune against the grime acceptance set (plan.md 4B).
const val STAIN_ANALYSIS_SIZE = 256
const val STAIN_BORDER_FRACTION = 0.10f   // ignore this share of each edge
const val STAIN_DELTA_E = 18f             // colour distance from the median that counts as "stained"
const val DARK_DELTA_L = 15f              // lightness below the median that counts as "dark"
const val W_STAIN = 1.2f
const val W_DARK = 0.8f
const val MODERATE_CUTOFF = 20f
const val HEAVY_CUTOFF = 45f

/** Image statistics on one TOUGHEST photo; callers keep the worst score across photos. */
suspend fun analyzeStain(bitmap: Bitmap): StainResult = withContext(Dispatchers.Default) {
    val scaled = Bitmap.createScaledBitmap(bitmap, STAIN_ANALYSIS_SIZE, STAIN_ANALYSIS_SIZE, true)
    val pixels = IntArray(STAIN_ANALYSIS_SIZE * STAIN_ANALYSIS_SIZE)
    scaled.getPixels(pixels, 0, STAIN_ANALYSIS_SIZE, 0, 0, STAIN_ANALYSIS_SIZE, STAIN_ANALYSIS_SIZE)
    if (scaled !== bitmap) scaled.recycle()
    StainMetric.analyze(pixels, STAIN_ANALYSIS_SIZE)
}

object StainMetric {

    fun analyze(argb: IntArray, size: Int): StainResult {
        val margin = (size * STAIN_BORDER_FRACTION).toInt()
        val n = (size - 2 * margin) * (size - 2 * margin)
        if (n <= 0) return StainResult(SeverityLevel.LIGHT, 0f)

        val ls = FloatArray(n); val as_ = FloatArray(n); val bs = FloatArray(n)
        var i = 0
        for (y in margin until size - margin) for (x in margin until size - margin) {
            val lab = rgbToLab(argb[y * size + x])
            ls[i] = lab[0]; as_[i] = lab[1]; bs[i] = lab[2]; i++
        }
        val medL = median(ls); val medA = median(as_); val medB = median(bs)

        var stained = 0; var dark = 0
        for (k in 0 until n) {
            val dl = ls[k] - medL; val da = as_[k] - medA; val db = bs[k] - medB
            if (sqrt(dl * dl + da * da + db * db) > STAIN_DELTA_E) stained++
            if (-dl > DARK_DELTA_L) dark++
        }
        val stainFraction = stained.toFloat() / n
        val darkFraction = dark.toFloat() / n
        val score = 100f * minOf(1f, W_STAIN * stainFraction + W_DARK * darkFraction)
        return StainResult(severityFor(score), score)
    }

    fun severityFor(score: Float): SeverityLevel = when {
        score >= HEAVY_CUTOFF -> SeverityLevel.HEAVY
        score >= MODERATE_CUTOFF -> SeverityLevel.MODERATE
        else -> SeverityLevel.LIGHT
    }

    private fun median(values: FloatArray): Float {
        val sorted = values.copyOf().also { it.sort() }
        return sorted[sorted.size / 2]
    }

    /** sRGB (D65) -> CIE Lab. Returns [L, a, b]. */
    fun rgbToLab(argb: Int): FloatArray {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((argb shr 16) and 0xFF); val g = lin((argb shr 8) and 0xFF); val b = lin(argb and 0xFF)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf((116 * fy - 16).toFloat(), (500 * (fx - fy)).toFloat(), (200 * (fy - fz)).toFloat())
    }
}
