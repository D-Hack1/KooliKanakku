package com.koolikanaku.domain

data class CatalogItem(
    val key: String,
    val titleEn: String,
    val titleMl: String,
    val minutesPerUnit: Int,
    val materialCostInr: Double
)

object TaskCatalog {

    val ITEMS: Map<String, CatalogItem> = listOf(
        CatalogItem("toilet", "Scrub & sanitize toilet", "ടോയ്ലറ്റ് കഴുകി വൃത്തിയാക്കുക", 15, 25.0),
        CatalogItem("bathtub", "Scrub bathtub / bathing area", "ബാത്ത്ടബ് / കുളിക്കുന്ന ഭാഗം കഴുകുക", 15, 20.0),
        CatalogItem("shower", "Descale shower fittings & wall tiles", "ഷവറും ടൈലുകളും വൃത്തിയാക്കുക", 10, 15.0),
        CatalogItem("sink", "Scrub washbasin / sink & tap", "സിങ്കും ടാപ്പും കഴുകുക", 8, 10.0),
        CatalogItem("bathroom cabinet", "Wipe bathroom shelf / cabinet", "കുളിമുറിയിലെ ഷെൽഫ് തുടയ്ക്കുക", 6, 5.0),
        CatalogItem("mirror", "Clean mirror glass", "കണ്ണാടി തുടച്ചു വൃത്തിയാക്കുക", 4, 5.0),
        CatalogItem("gas stove", "Degrease gas stove / hob", "ഗ്യാസ് സ്റ്റൗവിലെ എണ്ണമയം നീക്കുക", 15, 25.0),
        CatalogItem("oven", "Clean oven / cooking range", "ഓവൻ / അടുപ്പ് വൃത്തിയാക്കുക", 15, 25.0),
        CatalogItem("countertop", "Clear & scrub kitchen slab", "അടുക്കള സ്ലാബ് കഴുകി തുടയ്ക്കുക", 12, 15.0),
        CatalogItem("cabinetry", "Wipe cabinet doors & handles", "അലമാരയുടെ വാതിലുകൾ തുടയ്ക്കുക", 12, 12.0),
        CatalogItem("microwave oven", "Wipe microwave inside & out", "മൈക്രോവേവ് ഓവൻ തുടയ്ക്കുക", 8, 10.0),
        CatalogItem("refrigerator", "Wipe refrigerator exterior", "ഫ്രിഡ്ജിന്റെ പുറംഭാഗം തുടയ്ക്കുക", 6, 8.0),
        CatalogItem("bed", "Dust bed frame & under-bed area", "കട്ടിലും അടിഭാഗവും വൃത്തിയാക്കുക", 10, 5.0),
        CatalogItem("couch", "Vacuum / brush sofa & cushions", "സോഫയും കുഷ്യനുകളും വൃത്തിയാക്കുക", 12, 15.0),
        CatalogItem("wardrobe", "Wipe wardrobe exterior & top", "വാർഡ്രോബ് തുടച്ചു വൃത്തിയാക്കുക", 8, 8.0),
        CatalogItem("table", "Wipe table surface & legs", "മേശ തുടച്ചു വൃത്തിയാക്കുക", 6, 5.0),
        CatalogItem("chair", "Wipe chair", "കസേര തുടയ്ക്കുക", 2, 2.0),
        CatalogItem("television", "Microfiber wipe TV / display screen", "ടിവി സ്ക്രീൻ തുടയ്ക്കുക", 3, 3.0),
        CatalogItem("window", "Clean window glass, sill & grill", "ജനൽ ചില്ലും ഗ്രില്ലും തുടയ്ക്കുക", 8, 8.0),
        CatalogItem("ceiling fan", "Dust ceiling fan blades", "സീലിംഗ് ഫാൻ തുടയ്ക്കുക", 6, 5.0),
        CatalogItem("curtain", "Dust or wash curtains", "കർട്ടൻ തുടയ്ക്കുക / കഴുകുക", 10, 5.0),
        CatalogItem("exhaust fan", "Clean exhaust fan blades & grille", "എക്സോസ്റ്റ് ഫാൻ വൃത്തിയാക്കുക", 10, 8.0),
        CatalogItem("switchboard", "Wipe switches & sockets", "സ്വിച്ച്ബോർഡ് തുടയ്ക്കുക", 4, 2.0),
        CatalogItem("door", "Wipe door & handles", "വാതിലും ഹാൻഡിലും തുടയ്ക്കുക", 6, 3.0),
        CatalogItem("ac unit", "Wipe AC unit & vents", "എസി യൂണിറ്റ് തുടയ്ക്കുക", 10, 5.0),
        CatalogItem("rug", "Vacuum / shake out rug", "പരവതാനി വൃത്തിയാക്കുക", 8, 5.0),
        CatalogItem("balcony", "Sweep & wipe balcony floor & railing", "ബാൽക്കണി തുടച്ചു വൃത്തിയാക്കുക", 12, 5.0),
        CatalogItem("bookshelf", "Dust bookshelf & items", "പുസ്തക അലമാര തുടയ്ക്കുക", 8, 5.0),
        CatalogItem("trash bin", "Empty & wipe trash bin", "ചവർ ബിൻ ഒഴിച്ച് തുടയ്ക്കുക", 4, 2.0),
        CatalogItem("wall", "Spot clean wall marks", "ചുമരിലെ പാടുകൾ വൃത്തിയാക്കുക", 10, 3.0)
    ).associateBy { it.key }

    private val SYNONYMS: Map<String, String> = mapOf(
        "microwave" to "microwave oven",
        "dining table" to "table",
        "kitchen & dining room table" to "table",
        "coffee table" to "table",
        "desk" to "table",
        "tv" to "television",
        "computer monitor" to "television",
        "sofa bed" to "couch",
        "studio couch" to "couch",
        "loveseat" to "couch",
        "closet" to "wardrobe",
        "chest of drawers" to "wardrobe",
        "filing cabinet" to "wardrobe",
        "cupboard" to "cabinetry",
        "shelf" to "cabinetry",
        "kitchen appliance" to "cabinetry",
        "home appliance" to "cabinetry",
        "mechanical fan" to "ceiling fan",
        "tap" to "sink",
        "plumbing fixture" to "sink",
        "bidet" to "sink"
    )

    /**
     * Labels the bundled detector (COCO yolov8n, see assets/labels.txt) can emit that become checklist rows.
     * Catalog keys with no COCO equivalent (gas stove, countertop, shower...) are still offered as
     * TEMPLATE tasks and via "+ Add task"; they are just never DETECTED.
     */
    val TASK_LABELS: Set<String> = setOf(
        "toilet", "sink", "oven", "microwave", "refrigerator",
        "bed", "couch", "chair", "dining table", "tv"
    )

    fun resolveCatalogKey(rawLabel: String): String? {
        val cleaned = rawLabel.trim().lowercase()
        if (ITEMS.containsKey(cleaned)) {
            return cleaned
        }
        return SYNONYMS[cleaned]
    }

    fun getItem(key: String): CatalogItem? = ITEMS[key]

    fun templateKeysForRoom(roomType: RoomType): List<String> = when (roomType) {
        RoomType.BATHROOM -> listOf("toilet", "sink", "shower", "mirror", "window")
        RoomType.KITCHEN -> listOf("gas stove", "sink", "countertop", "cabinetry", "refrigerator", "window")
        RoomType.BEDROOM -> listOf("bed", "wardrobe", "table", "ceiling fan", "window")
        RoomType.LIVING_ROOM -> listOf("couch", "table", "chair", "television", "ceiling fan", "window")
        RoomType.OTHER -> listOf("table", "chair", "ceiling fan", "window")
    }
}
