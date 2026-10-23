package com.koolikanaku.domain

enum class AppMode { WORKER_ESTIMATE, OWNER_QUOTE }
enum class RoomType { KITCHEN, BATHROOM, BEDROOM, LIVING_ROOM, OTHER }
enum class SeverityLevel { LIGHT, MODERATE, HEAVY }
enum class ClutterLevel { LOW, MEDIUM, HIGH }
enum class LengthUnit { PACES, FEET, METRES }
enum class SizeSource { MEASURED, MANUAL, PRESET }
enum class PhotoRole { MEASURE_LENGTH, MEASURE_WIDTH, WIDE, TOUGHEST, AFTER }
enum class TaskSource { DETECTED, TEMPLATE, MANUAL }
enum class MeasureWarning { TOO_FAR, TOO_CLOSE, IMPLAUSIBLE_SIZE }

data class RoomDimensions(
    val length: Double,
    val width: Double,
    val unit: LengthUnit,
    val computedAreaSqFt: Double,
    val source: SizeSource
)

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: android.graphics.RectF
)

data class StainResult(
    val severity: SeverityLevel,
    val score: Float
)

data class EvidencePhoto(
    val id: String,
    val role: PhotoRole,
    val uri: android.net.Uri,
    val timestampIso: String,
    val sha256Hash: String,
    val depressionDeg: Float? = null,
    val holdHeightM: Float? = null
)

data class PhotoAnalysis(
    val photoId: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val detections: List<DetectedObject>
)

data class CleaningTask(
    val id: String,
    val catalogKey: String,
    val titleEn: String,
    val titleMl: String,
    val quantity: Int,
    val minutesPerUnit: Int,
    val materialCostInr: Double,
    val isSelected: Boolean = false,
    val source: TaskSource
)

data class RoomEntry(
    val id: String,
    val roomType: RoomType,
    val suggestedRoomType: RoomType? = null,
    val dimensions: RoomDimensions? = null,
    val photos: List<EvidencePhoto> = emptyList(),
    val photoAnalyses: List<PhotoAnalysis> = emptyList(),
    val stain: StainResult? = null,
    val severityOverride: SeverityLevel? = null,
    val clutter: ClutterLevel = ClutterLevel.LOW,
    val tasks: List<CleaningTask> = emptyList()
)

data class Job(
    val id: String,
    val mode: AppMode,
    val createdIso: String,
    val hourlyRateInr: Double,
    val paceFactor: Double,
    val holdHeightM: Float,
    val rooms: List<RoomEntry> = emptyList(),
    /** The find-a-worker booking this job was started from, if any. Contract addition (optional, defaults to null). */
    val bookingId: String? = null
)

data class MinMax(val low: Double, val high: Double)

data class RoomQuote(
    val roomId: String,
    val standardMinutes: MinMax,
    val expectedMinutes: MinMax,
    val priceInr: MinMax
)

data class JobQuote(
    val rooms: List<RoomQuote>,
    val totalStandardMinutes: MinMax,
    val totalExpectedMinutes: MinMax,
    val totalPriceInr: MinMax
)

data class MeasureResult(
    val dimensions: RoomDimensions?,
    val warnings: List<MeasureWarning>
)

sealed class AnalysisState {
    object Idle : AnalysisState()
    data class Running(val roomId: String) : AnalysisState()
    data class Done(val roomId: String) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}
