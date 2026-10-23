package com.koolikanaku.domain

object ClutterRules {

    val CLUTTER_LABELS: Set<String> = setOf(
        "bottle",
        "cup",
        "mug",
        "coffee cup",
        "bowl",
        "plate",
        "platter",
        "wine glass",
        "vase",
        "book",
        "tin can",
        "plastic bag",
        "backpack",
        "handbag",
        "suitcase",
        "footwear",
        "shoe",
        "toy",
        "remote",
        "scissors",
        "towel"
    )

    fun isClutterLabel(label: String): Boolean = label.trim().lowercase() in CLUTTER_LABELS

    fun computeClutterLevel(totalClutterCount: Int): ClutterLevel = when (totalClutterCount) {
        in 0..2 -> ClutterLevel.LOW
        in 3..6 -> ClutterLevel.MEDIUM
        else -> ClutterLevel.HIGH
    }
}
