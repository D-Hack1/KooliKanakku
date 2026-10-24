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
        "teddy bear",
        // Open Images only (secondary detector)
        "mug",
        "plate",
        "footwear",
        "toy",
        "towel",
        "plastic bag",
        "tin can",
        "clothing",
        "box",
        "container",
        "pillow",
        "kitchen utensil",
        "waste container",
        "paper towel",
        "toilet paper",
        "luggage and bags",
        // Clothes & Personal Accessories
        "shirt",
        "trousers",
        "jeans",
        "dress",
        "skirt",
        "coat",
        "jacket",
        "sock",
        "sandal",
        "boot",
        "hat",
        "glove",
        "belt",
        "scarf",
        "sunglasses",
        "glasses",
        // Kitchen Utensils & Mess
        "frying pan",
        "wok",
        "pressure cooker",
        "kettle",
        "teapot",
        "cutting board",
        "serving tray",
        "mixing bowl",
        "saucer",
        "jug",
        "chopsticks",
        "pitcher (container)",
        // Toiletries & Hygiene
        "soap dispenser",
        "cosmetics",
        "perfume",
        "hair spray",
        "diaper",
        // Paper & Stationery
        "pen",
        "pencil case",
        "envelope",
        "calculator",
        "ruler",
        // Toys & Living Clutter
        "doll",
        "balloon",
        "dice",
        "candle",
        "picnic basket"
    )

    fun isClutterLabel(label: String): Boolean = label.trim().lowercase() in CLUTTER_LABELS

    fun computeClutterLevel(totalClutterCount: Int): ClutterLevel = when (totalClutterCount) {
        in 0..2 -> ClutterLevel.LOW
        in 3..6 -> ClutterLevel.MEDIUM
        else -> ClutterLevel.HIGH
    }
}
