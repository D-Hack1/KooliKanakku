package com.koolikanaku.domain

import com.koolikanaku.ai.DetectionMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the contract between the two labels files (COCO main model, Open Images secondary) and the allow-lists. */
class LabelIntegrityTest {

    private fun readLabels(name: String): Set<String> {
        val file = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
            File("../app/src/main/assets/$name")
        ).first { it.exists() }
        return file.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    private val coco by lazy { readLabels("labels.txt") }
    private val openImages by lazy { readLabels("labels_oiv7.txt") }

    @Test
    fun labelsFilesHaveTheExpectedClassCounts() {
        assertEquals(80, coco.size)
        assertEquals(601, openImages.size)
    }

    @Test
    fun everyTaskLabelExistsInOneLabelsFileAndResolvesToACatalogItem() {
        val missing = TaskCatalog.TASK_LABELS.filter { it !in coco && it !in openImages }
        assertTrue("TASK_LABELS missing from both labels files: $missing", missing.isEmpty())
        TaskCatalog.TASK_LABELS.forEach { label ->
            val key = TaskCatalog.resolveCatalogKey(label)
            assertNotNull("No catalog item for detector label '$label'", key)
            assertNotNull(TaskCatalog.getItem(key!!))
        }
    }

    @Test
    fun everyClutterLabelExistsInOneLabelsFile() {
        val missing = ClutterRules.CLUTTER_LABELS.filter { it !in coco && it !in openImages }
        assertTrue("CLUTTER_LABELS missing from both labels files: $missing", missing.isEmpty())
    }

    @Test
    fun secondaryOnlyClassesExistInTheOpenImagesFile() {
        val missing = (DetectionMerger.SECONDARY_FIXTURES + DetectionMerger.SECONDARY_CLUTTER).filter { it !in openImages }
        assertTrue("Secondary labels missing from labels_oiv7.txt: $missing", missing.isEmpty())
        // they must really be extras, otherwise the main model would be doubled up on
        val overlap = (DetectionMerger.SECONDARY_FIXTURES + DetectionMerger.SECONDARY_CLUTTER).filter { it in coco }
        assertTrue("Secondary labels the main model already has: $overlap", overlap.isEmpty())
    }

    @Test
    fun roomTypeRulesAreReachableFromDetectorLabels() {
        fun suggest(vararg labels: String): RoomType? =
            RoomMerger.suggestRoomType(labels.associate { TaskCatalog.resolveCatalogKey(it)!! to 1 })

        assertEquals(RoomType.BATHROOM, suggest("toilet"))
        assertEquals(RoomType.BATHROOM, suggest("bathtub"))
        assertEquals(RoomType.BATHROOM, suggest("shower"))
        assertEquals(RoomType.BEDROOM, suggest("bed"))
        assertEquals(RoomType.KITCHEN, suggest("oven"))
        assertEquals(RoomType.KITCHEN, suggest("microwave"))
        assertEquals(RoomType.KITCHEN, suggest("refrigerator"))
        assertEquals(RoomType.KITCHEN, suggest("sink"))
        assertEquals(RoomType.KITCHEN, suggest("gas stove"))
        assertEquals(RoomType.KITCHEN, suggest("countertop"))
        assertEquals(RoomType.LIVING_ROOM, suggest("couch"))
        assertEquals(RoomType.LIVING_ROOM, suggest("tv"))
    }
}
