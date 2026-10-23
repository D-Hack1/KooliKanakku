package com.koolikanaku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the contract between assets/labels.txt (COCO yolov8n, 80 classes) and the allow-lists. */
class LabelIntegrityTest {

    private val assetLabels: Set<String> by lazy {
        val file = listOf(
            File("src/main/assets/labels.txt"),
            File("app/src/main/assets/labels.txt"),
            File("../app/src/main/assets/labels.txt")
        ).first { it.exists() }
        file.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    @Test
    fun labelsFileIsTheCoco80ClassSet() {
        assertEquals(80, assetLabels.size)
    }

    @Test
    fun everyTaskLabelExistsInLabelsFileAndResolvesToACatalogItem() {
        val missing = TaskCatalog.TASK_LABELS.filter { it.lowercase() !in assetLabels }
        assertTrue("TASK_LABELS missing from labels.txt: $missing", missing.isEmpty())
        TaskCatalog.TASK_LABELS.forEach { label ->
            val key = TaskCatalog.resolveCatalogKey(label)
            assertNotNull("No catalog item for detector label '$label'", key)
            assertNotNull(TaskCatalog.getItem(key!!))
        }
    }

    @Test
    fun everyClutterLabelExistsInLabelsFile() {
        val missing = ClutterRules.CLUTTER_LABELS.filter { it.lowercase() !in assetLabels }
        assertTrue("CLUTTER_LABELS missing from labels.txt: $missing", missing.isEmpty())
    }

    @Test
    fun roomTypeRulesAreReachableFromDetectorLabels() {
        fun suggest(vararg labels: String): RoomType? =
            RoomMerger.suggestRoomType(labels.associate { TaskCatalog.resolveCatalogKey(it)!! to 1 })

        assertEquals(RoomType.BATHROOM, suggest("toilet"))
        assertEquals(RoomType.BEDROOM, suggest("bed"))
        assertEquals(RoomType.KITCHEN, suggest("oven"))
        assertEquals(RoomType.KITCHEN, suggest("microwave"))
        assertEquals(RoomType.KITCHEN, suggest("refrigerator"))
        assertEquals(RoomType.KITCHEN, suggest("sink"))
        assertEquals(RoomType.LIVING_ROOM, suggest("couch"))
        assertEquals(RoomType.LIVING_ROOM, suggest("tv"))
    }
}
