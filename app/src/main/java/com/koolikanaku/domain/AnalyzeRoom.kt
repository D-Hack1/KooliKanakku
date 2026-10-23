package com.koolikanaku.domain

import android.graphics.Bitmap
import android.graphics.RectF

class AnalyzeRoom(
    private val loadBitmap: suspend (EvidencePhoto) -> Bitmap? = { null },
    private val detectObjectsFn: suspend (Bitmap) -> List<DetectedObject> = { emptyList() },
    private val analyzeStainFn: suspend (Bitmap) -> StainResult = {
        StainResult(severity = SeverityLevel.MODERATE, score = 42f)
    },
    /** True only for dummy-data runs; the real app passes false so an unreadable photo never yields fake results. */
    private val dummyFallback: Boolean = true,
    private val measureRoomFn: (EvidencePhoto, EvidencePhoto) -> MeasureResult = { _, _ ->
        MeasureResult(
            dimensions = Constants.computeDimensions(3.6, 3.0, LengthUnit.METRES, SizeSource.MEASURED),
            warnings = emptyList()
        )
    }
) {

    suspend operator fun invoke(room: RoomEntry): RoomEntry = execute(room)

    suspend fun execute(room: RoomEntry): RoomEntry {
        // 1. Identify all detection-eligible photos
        val detectionEligiblePhotos = room.photos.filter {
            it.role == PhotoRole.MEASURE_LENGTH ||
            it.role == PhotoRole.MEASURE_WIDTH ||
            it.role == PhotoRole.WIDE
        }

        // 2. For each detection-eligible photo, load bitmap, detect, and filter
        val photoAnalyses = detectionEligiblePhotos.map { photo ->
            val bitmap = loadBitmap(photo)
            val rawDetections = if (bitmap != null) {
                detectObjectsFn(bitmap)
            } else {
                if (dummyFallback) generateDummyDetections(room.roomType) else emptyList()
            }

            // CRITICAL ALLOW-LIST FILTER (plan.md Section 4A)
            val filteredDetections = rawDetections.filter { det ->
                TaskCatalog.resolveCatalogKey(det.label) != null ||
                ClutterRules.isClutterLabel(det.label)
            }

            PhotoAnalysis(
                photoId = photo.id,
                imageWidth = bitmap?.width ?: 640,
                imageHeight = bitmap?.height ?: 640,
                detections = filteredDetections
            )
        }

        // 3. For all PhotoRole.TOUGHEST photos in room.photos, run analyzeStainFn and keep the worst
        val toughestPhotos = room.photos.filter { it.role == PhotoRole.TOUGHEST }
        val worstStain = if (toughestPhotos.isNotEmpty()) {
            val stainComparator = compareBy<StainResult> { it.severity.ordinal }.thenBy { it.score }
            toughestPhotos.mapNotNull { photo ->
                val bitmap = loadBitmap(photo)
                if (bitmap != null) {
                    analyzeStainFn(bitmap)
                } else if (dummyFallback) {
                    StainResult(severity = SeverityLevel.MODERATE, score = 42f)
                } else {
                    null
                }
            }.maxWithOrNull(stainComparator) ?: room.stain
        } else {
            room.stain
        }

        // 4. If room.dimensions == null (or room.dimensions.source == SizeSource.MEASURED), check length and width shots
        val updatedDimensions = if (room.dimensions == null || room.dimensions.source == SizeSource.MEASURED) {
            val lengthShot = room.photos.find { it.role == PhotoRole.MEASURE_LENGTH }
            val widthShot = room.photos.find { it.role == PhotoRole.MEASURE_WIDTH }
            if (lengthShot != null && widthShot != null) {
                measureRoomFn(lengthShot, widthShot).dimensions ?: room.dimensions
            } else {
                room.dimensions
            }
        } else {
            room.dimensions
        }

        // 5. Call mergedCounts
        val mergedCounts = RoomMerger.mergedCounts(room.photos, photoAnalyses)

        // 6. Call clutterLevel
        val clutter = RoomMerger.clutterLevel(mergedCounts)

        // 7. Call suggestRoomType
        val suggestedType = RoomMerger.suggestRoomType(mergedCounts)

        // 8. Determine effective room type for templates
        val effectiveRoomType = suggestedType ?: room.roomType

        // 9. Build tasks via TaskMapper (re-analysis resets isSelected to false on all tasks)
        val tasks = TaskMapper.buildTasksFromAnalysis(
            roomId = room.id,
            roomType = effectiveRoomType,
            mergedCounts = mergedCounts,
            existingTasks = room.tasks
        ).map { it.copy(isSelected = false) }

        // 10. Return the updated room.copy(...)
        return room.copy(
            photoAnalyses = photoAnalyses,
            stain = worstStain,
            clutter = clutter,
            suggestedRoomType = suggestedType,
            dimensions = updatedDimensions,
            tasks = tasks
        )
    }

    private fun generateDummyDetections(roomType: RoomType): List<DetectedObject> = when (roomType) {
        RoomType.BATHROOM -> listOf(
            DetectedObject("toilet", 0.91f, RectF(100f, 200f, 300f, 500f)),
            DetectedObject("sink", 0.87f, RectF(350f, 150f, 500f, 350f))
        )
        RoomType.KITCHEN -> listOf(
            DetectedObject("gas stove", 0.94f, RectF(120f, 250f, 350f, 450f)),
            DetectedObject("countertop", 0.88f, RectF(50f, 400f, 600f, 550f)),
            DetectedObject("bottle", 0.82f, RectF(400f, 220f, 450f, 320f))
        )
        RoomType.BEDROOM -> listOf(
            DetectedObject("bed", 0.95f, RectF(150f, 200f, 550f, 550f)),
            DetectedObject("table", 0.85f, RectF(50f, 300f, 200f, 500f))
        )
        RoomType.LIVING_ROOM -> listOf(
            DetectedObject("couch", 0.92f, RectF(100f, 250f, 500f, 500f)),
            DetectedObject("television", 0.89f, RectF(200f, 80f, 440f, 240f))
        )
        RoomType.OTHER -> listOf(
            DetectedObject("table", 0.86f, RectF(150f, 250f, 450f, 500f))
        )
    }
}
