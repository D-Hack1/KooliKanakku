package com.koolikanaku.ai

/** Pure parsing and NMS for the YOLOv8 output tensor, kept free of Android types so it is unit-testable. */
object YoloPostProcessor {

    /** Box edges normalized to 0..1 of the model input (the exported model emits normalized cx, cy, w, h). */
    data class Candidate(
        val classIndex: Int,
        val confidence: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    }

    fun parse(
        output: Array<FloatArray>,
        featuresFirst: Boolean,
        numFeatures: Int,
        numAnchors: Int,
        confidenceThreshold: Float
    ): List<Candidate> {
        fun value(feature: Int, anchor: Int) =
            if (featuresFirst) output[feature][anchor] else output[anchor][feature]

        val numClasses = numFeatures - 4
        val result = ArrayList<Candidate>()
        for (a in 0 until numAnchors) {
            var best = -1
            var bestScore = confidenceThreshold
            for (c in 0 until numClasses) {
                val s = value(4 + c, a)
                if (s >= bestScore) { bestScore = s; best = c }
            }
            if (best < 0) continue
            val cx = value(0, a); val cy = value(1, a)
            val w = value(2, a); val h = value(3, a)
            result.add(
                Candidate(
                    best, bestScore,
                    (cx - w / 2).coerceIn(0f, 1f), (cy - h / 2).coerceIn(0f, 1f),
                    (cx + w / 2).coerceIn(0f, 1f), (cy + h / 2).coerceIn(0f, 1f)
                )
            )
        }
        return result
    }

    /** Class-agnostic: one object never survives under two labels. */
    fun nms(candidates: List<Candidate>, iouThreshold: Float): List<Candidate> {
        val kept = ArrayList<Candidate>()
        for (c in candidates.sortedByDescending { it.confidence }) {
            if (kept.none { iou(it, c) > iouThreshold }) kept.add(c)
        }
        return kept
    }

    fun iou(a: Candidate, b: Candidate): Float {
        val iw = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val ih = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val inter = iw * ih
        val union = a.area + b.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}
