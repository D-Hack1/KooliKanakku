package com.koolikanaku.domain

object ClutterRules {

    val CLUTTER_LABELS: Set<String> = setOf(
        "bottle",
        "cup",
        "bowl",
        "wine glass",
        "vase",
        "book",
        "backpack",
        "handbag",
        "suitcase",
        "remote",
        "scissors",
        "fork",
        "knife",
        "spoon",
        "cell phone",
        "teddy bear"
    )

    fun isClutterLabel(label: String): Boolean = label.trim().lowercase() in CLUTTER_LABELS

    fun computeClutterLevel(totalClutterCount: Int): ClutterLevel = when (totalClutterCount) {
        in 0..2 -> ClutterLevel.LOW
        in 3..6 -> ClutterLevel.MEDIUM
        else -> ClutterLevel.HIGH
    }
}
