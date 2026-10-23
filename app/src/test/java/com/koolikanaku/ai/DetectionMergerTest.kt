package com.koolikanaku.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionMergerTest {

    private fun box(label: String, conf: Float, l: Float, t: Float, r: Float, b: Float) = Labeled(label, conf, l, t, r, b)

    @Test fun mainDetectionsAreAlwaysKept() {
        val main = listOf(box("toilet", 0.9f, 0.1f, 0.1f, 0.3f, 0.4f))
        assertEquals(main, DetectionMerger.merge(main, emptyList()))
    }

    @Test fun secondaryOnlyKeepsClassesTheMainModelLacks() {
        val secondary = listOf(
            box("Window", 0.5f, 0.6f, 0.1f, 0.9f, 0.5f),     // extra fixture: kept
            box("House", 0.6f, 0.0f, 0.0f, 1.0f, 1.0f),      // whole-room junk: dropped
            box("Chair", 0.4f, 0.2f, 0.5f, 0.3f, 0.9f),      // main model already covers chairs: dropped
            box("Mug", 0.3f, 0.4f, 0.6f, 0.45f, 0.7f)        // extra clutter: kept
        )
        val labels = DetectionMerger.merge(emptyList(), secondary).map { it.label }
        assertEquals(setOf("Window", "Mug"), labels.toSet())
    }

    @Test fun anObjectNamedByTheMainModelIsNotCountedAgain() {
        val main = listOf(box("cup", 0.8f, 0.40f, 0.60f, 0.50f, 0.75f))
        val secondary = listOf(box("Mug", 0.5f, 0.41f, 0.60f, 0.50f, 0.76f))   // same object, other name
        assertEquals(1, DetectionMerger.merge(main, secondary).size)
    }

    @Test fun aDifferentObjectAtADistanceIsStillKept() {
        val main = listOf(box("cup", 0.8f, 0.40f, 0.60f, 0.50f, 0.75f))
        val secondary = listOf(box("Mug", 0.5f, 0.70f, 0.60f, 0.80f, 0.76f))
        assertEquals(2, DetectionMerger.merge(main, secondary).size)
    }

    @Test fun aCounterOverlappingATableIsNotMistakenForTheSameThing() {
        val main = listOf(box("dining table", 0.7f, 0.1f, 0.5f, 0.9f, 0.9f))
        val secondary = listOf(box("Countertop", 0.5f, 0.1f, 0.5f, 0.9f, 0.9f))
        assertEquals(setOf("dining table", "Countertop"), DetectionMerger.merge(main, secondary).map { it.label }.toSet())
    }

    @Test fun cabinetryAndCountertopCountOnceEvenWhenSeenAsSeveralBoxes() {
        val secondary = listOf(
            box("Cabinetry", 0.3f, 0.0f, 0.0f, 0.2f, 0.3f),
            box("Cabinetry", 0.5f, 0.3f, 0.0f, 0.5f, 0.3f),
            box("Cupboard", 0.4f, 0.6f, 0.0f, 0.8f, 0.3f),
            box("Countertop", 0.5f, 0.0f, 0.5f, 0.5f, 0.6f),
            box("Countertop", 0.4f, 0.5f, 0.5f, 1.0f, 0.6f),
            box("Window", 0.5f, 0.0f, 0.7f, 0.1f, 0.9f),
            box("Window", 0.4f, 0.2f, 0.7f, 0.3f, 0.9f)
        )
        val merged = DetectionMerger.merge(emptyList(), secondary)
        assertEquals(1, merged.count { it.label.lowercase() in setOf("cabinetry", "cupboard") })
        assertEquals(0.5f, merged.first { it.label.lowercase() == "cabinetry" || it.label.lowercase() == "cupboard" }.confidence, 0f)
        assertEquals(1, merged.count { it.label == "Countertop" })
        assertEquals("windows are not capped", 2, merged.count { it.label == "Window" })
    }

    @Test fun iouOfIdenticalAndDisjointBoxes() {
        val a = box("a", 1f, 0f, 0f, 0.5f, 0.5f)
        assertEquals(1f, DetectionMerger.iou(a, a), 1e-5f)
        assertEquals(0f, DetectionMerger.iou(a, box("b", 1f, 0.6f, 0.6f, 1f, 1f)), 1e-5f)
        assertTrue(DetectionMerger.iou(a, box("c", 1f, 0.25f, 0f, 0.75f, 0.5f)) in 0.30f..0.35f)
    }
}
