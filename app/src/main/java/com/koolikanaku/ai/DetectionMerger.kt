package com.koolikanaku.ai

import com.koolikanaku.domain.ClutterRules
import com.koolikanaku.domain.TaskCatalog

/** Combines the main (COCO) and secondary (Open Images) detections without double counting. */
object DetectionMerger {

    /** Fixtures only the secondary model knows. Lower-case names exactly as in labels_oiv7.txt. */
    val SECONDARY_FIXTURES: Set<String> = setOf(
        "countertop", "cabinetry", "cupboard", "bathtub", "shower", "window",
        "mirror", "gas stove", "bathroom cabinet", "ceiling fan", "wardrobe"
    )

    /** Small loose items only the secondary model knows (they feed the clutter count). */
    val SECONDARY_CLUTTER: Set<String> = setOf(
        "mug", "plate", "footwear", "toy", "towel", "plastic bag", "tin can",
        "clothing", "box", "container", "pillow", "kitchen utensil",
        "waste container", "paper towel", "toilet paper", "luggage and bags"
    )

    /** One long cabinet run or counter is seen as several boxes; checklist quantity should stay 1. */
    private val MAX_PER_KEY: Map<String, Int> = mapOf("cabinetry" to 1, "countertop" to 1)

    private const val SAME_OBJECT_IOU = 0.5f

    fun merge(main: List<Labeled>, secondary: List<Labeled>): List<Labeled> {
        val owned = secondary
            .filter { it.label.lowercase() in SECONDARY_FIXTURES || it.label.lowercase() in SECONDARY_CLUTTER }
            // an object the main model already named is not counted again under another label
            .filter { s -> main.none { m -> iou(m, s) > SAME_OBJECT_IOU && sameKind(m.label, s.label) } }
            .sortedByDescending { it.confidence }

        val kept = ArrayList<Labeled>()
        val perKey = HashMap<String, Int>()
        for (d in owned) {
            val key = keyOf(d.label)
            val cap = MAX_PER_KEY[key]
            if (cap != null && (perKey[key] ?: 0) >= cap) continue
            perKey[key] = (perKey[key] ?: 0) + 1
            kept.add(d)
        }
        return main + kept
    }

    /** Two labels name the same kind of thing: the same checklist row, or both loose clutter. */
    private fun sameKind(a: String, b: String): Boolean {
        val keyA = TaskCatalog.resolveCatalogKey(a)
        val keyB = TaskCatalog.resolveCatalogKey(b)
        if (keyA != null && keyA == keyB) return true
        return ClutterRules.isClutterLabel(a) && ClutterRules.isClutterLabel(b)
    }

    private fun keyOf(label: String): String = TaskCatalog.resolveCatalogKey(label) ?: label.lowercase()

    fun iou(a: Labeled, b: Labeled): Float {
        val iw = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val ih = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val inter = iw * ih
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun area(d: Labeled) = (d.right - d.left).coerceAtLeast(0f) * (d.bottom - d.top).coerceAtLeast(0f)
}
