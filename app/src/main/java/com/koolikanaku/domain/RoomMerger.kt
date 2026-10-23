package com.koolikanaku.domain

object RoomMerger {

    fun mergedCounts(photos: List<EvidencePhoto>, analyses: List<PhotoAnalysis>): Map<String, Int> {
        val roleByPhotoId = photos.associate { it.id to it.role }

        // Filter out analyses from TOUGHEST or AFTER photos
        val validAnalyses = analyses.filter {
            val role = roleByPhotoId[it.photoId]
            role != PhotoRole.TOUGHEST && role != PhotoRole.AFTER
        }

        // Map each analysis to counts of normalized keys
        val countsByAnalysis = validAnalyses.map { analysis ->
            val counts = mutableMapOf<String, Int>()
            for (det in analysis.detections) {
                val key = TaskCatalog.resolveCatalogKey(det.label) ?: det.label.trim().lowercase()
                counts[key] = (counts[key] ?: 0) + 1
            }
            analysis to counts
        }

        val allKeys = countsByAnalysis.flatMap { it.second.keys }.toSet()
        val result = mutableMapOf<String, Int>()

        for (key in allKeys) {
            val lengthCount = countsByAnalysis
                .filter { roleByPhotoId[it.first.photoId] == PhotoRole.MEASURE_LENGTH }
                .sumOf { it.second[key] ?: 0 }

            val widthCount = countsByAnalysis
                .filter { roleByPhotoId[it.first.photoId] == PhotoRole.MEASURE_WIDTH }
                .sumOf { it.second[key] ?: 0 }

            val measureSum = lengthCount + widthCount

            val maxWide = countsByAnalysis
                .filter { roleByPhotoId[it.first.photoId] == PhotoRole.WIDE }
                .maxOfOrNull { it.second[key] ?: 0 } ?: 0

            val merged = maxOf(measureSum, maxWide)
            if (merged > 0) {
                result[key] = merged
            }
        }

        return result
    }

    fun clutterLevel(counts: Map<String, Int>): ClutterLevel {
        val totalClutter = counts.entries
            .filter { ClutterRules.isClutterLabel(it.key) }
            .sumOf { it.value }
        return ClutterRules.computeClutterLevel(totalClutter)
    }

    fun suggestRoomType(counts: Map<String, Int>): RoomType? {
        val has = { key: String -> (counts[key.trim().lowercase()] ?: 0) > 0 }

        return when {
            has("toilet") || has("bathtub") || has("shower") -> RoomType.BATHROOM
            has("bed") -> RoomType.BEDROOM
            has("gas stove") || has("oven") || has("microwave oven") || has("refrigerator") || has("countertop") || has("sink") -> RoomType.KITCHEN
            has("couch") || has("television") -> RoomType.LIVING_ROOM
            else -> null
        }
    }
}
