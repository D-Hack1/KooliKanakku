package com.koolikanaku.capture

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.ClutterLevel
import com.koolikanaku.domain.DetectedObject
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.Job
import com.koolikanaku.domain.LengthUnit
import com.koolikanaku.domain.PhotoAnalysis
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.RoomDimensions
import com.koolikanaku.domain.RoomEntry
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.SeverityLevel
import com.koolikanaku.domain.SizeSource
import com.koolikanaku.domain.StainResult
import com.koolikanaku.domain.TaskSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the app needs to pick a job back up after Android kills the process. */
data class SavedState(
    val job: Job?,
    val currentRoomId: String?,
    val finished: Boolean,
    val toughScores: Map<String, StainResult>,
    val mode: AppMode,
    val language: String,
    val rateText: String,
    val paceFactor: Double,
    val heightCm: String,
    val darkTheme: Boolean,
    val sizeMethod: String,
    val enterLengthFt: String,
    val enterWidthFt: String,
    val presetIndex: Int?
)

/** JSON file persistence for the in-progress job. Photos stay as the files PhotoStore already wrote. */
object JobStorage {

    private const val FILE_NAME = "current_job.json"
    private const val VERSION = 1

    fun save(context: Context, json: String) {
        val dir = context.filesDir
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(File(dir, FILE_NAME))) {
            File(dir, FILE_NAME).writeText(json)
            tmp.delete()
        }
    }

    fun load(context: Context): SavedState? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try { fromJson(file.readText()) } catch (e: Exception) { null }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    // ---------- encode ----------

    fun toJson(s: SavedState): String = JSONObject().apply {
        put("version", VERSION)
        put("job", s.job?.let(::jobToJson) ?: JSONObject.NULL)
        put("currentRoomId", s.currentRoomId ?: JSONObject.NULL)
        put("finished", s.finished)
        put("toughScores", JSONObject().also { o -> s.toughScores.forEach { (id, r) -> o.put(id, stainToJson(r)) } })
        put("mode", s.mode.name)
        put("language", s.language)
        put("rateText", s.rateText)
        put("paceFactor", s.paceFactor)
        put("heightCm", s.heightCm)
        put("darkTheme", s.darkTheme)
        put("sizeMethod", s.sizeMethod)
        put("enterLengthFt", s.enterLengthFt)
        put("enterWidthFt", s.enterWidthFt)
        put("presetIndex", s.presetIndex ?: JSONObject.NULL)
    }.toString()

    private fun jobToJson(j: Job) = JSONObject().apply {
        put("id", j.id); put("mode", j.mode.name); put("createdIso", j.createdIso)
        put("hourlyRateInr", j.hourlyRateInr); put("paceFactor", j.paceFactor); put("holdHeightM", j.holdHeightM.toDouble())
        put("rooms", JSONArray().also { a -> j.rooms.forEach { a.put(roomToJson(it)) } })
        put("bookingId", j.bookingId ?: JSONObject.NULL)
    }

    private fun roomToJson(r: RoomEntry) = JSONObject().apply {
        put("id", r.id); put("roomType", r.roomType.name)
        put("suggestedRoomType", r.suggestedRoomType?.name ?: JSONObject.NULL)
        put("dimensions", r.dimensions?.let(::dimsToJson) ?: JSONObject.NULL)
        put("photos", JSONArray().also { a -> r.photos.forEach { a.put(photoToJson(it)) } })
        put("photoAnalyses", JSONArray().also { a -> r.photoAnalyses.forEach { a.put(analysisToJson(it)) } })
        put("stain", r.stain?.let(::stainToJson) ?: JSONObject.NULL)
        put("severityOverride", r.severityOverride?.name ?: JSONObject.NULL)
        put("clutter", r.clutter.name)
        put("tasks", JSONArray().also { a -> r.tasks.forEach { a.put(taskToJson(it)) } })
    }

    private fun dimsToJson(d: RoomDimensions) = JSONObject().apply {
        put("length", d.length); put("width", d.width); put("unit", d.unit.name)
        put("computedAreaSqFt", d.computedAreaSqFt); put("source", d.source.name)
    }

    private fun photoToJson(p: EvidencePhoto) = JSONObject().apply {
        put("id", p.id); put("role", p.role.name); put("uri", p.uri.toString())
        put("timestampIso", p.timestampIso); put("sha256Hash", p.sha256Hash)
        put("depressionDeg", p.depressionDeg?.toDouble() ?: JSONObject.NULL)
        put("holdHeightM", p.holdHeightM?.toDouble() ?: JSONObject.NULL)
    }

    private fun analysisToJson(a: PhotoAnalysis) = JSONObject().apply {
        put("photoId", a.photoId); put("imageWidth", a.imageWidth); put("imageHeight", a.imageHeight)
        put("detections", JSONArray().also { arr ->
            a.detections.forEach { d ->
                arr.put(JSONObject().apply {
                    put("label", d.label); put("confidence", d.confidence.toDouble())
                    put("l", d.boundingBox.left.toDouble()); put("t", d.boundingBox.top.toDouble())
                    put("r", d.boundingBox.right.toDouble()); put("b", d.boundingBox.bottom.toDouble())
                })
            }
        })
    }

    private fun stainToJson(s: StainResult) = JSONObject().apply { put("severity", s.severity.name); put("score", s.score.toDouble()) }

    private fun taskToJson(t: CleaningTask) = JSONObject().apply {
        put("id", t.id); put("catalogKey", t.catalogKey); put("titleEn", t.titleEn); put("titleMl", t.titleMl)
        put("quantity", t.quantity); put("minutesPerUnit", t.minutesPerUnit); put("materialCostInr", t.materialCostInr)
        put("isSelected", t.isSelected); put("source", t.source.name)
    }

    // ---------- decode ----------

    fun fromJson(text: String): SavedState {
        val o = JSONObject(text)
        val scores = o.optJSONObject("toughScores")
        return SavedState(
            job = o.optJSONObject("job")?.let(::jobFromJson),
            currentRoomId = o.optStringOrNull("currentRoomId"),
            finished = o.optBoolean("finished", false),
            toughScores = scores?.keys()?.asSequence()?.associateWith { stainFromJson(scores.getJSONObject(it)) } ?: emptyMap(),
            mode = enumOr(o.optString("mode"), AppMode.WORKER_ESTIMATE),
            language = o.optString("language", "EN"),
            rateText = o.optString("rateText", "250"),
            paceFactor = o.optDouble("paceFactor", 1.0),
            heightCm = o.optString("heightCm", ""),
            darkTheme = o.optBoolean("darkTheme", false),
            sizeMethod = o.optString("sizeMethod", "ENTER"),
            enterLengthFt = o.optString("enterLengthFt", ""),
            enterWidthFt = o.optString("enterWidthFt", ""),
            presetIndex = if (o.isNull("presetIndex")) null else o.optInt("presetIndex")
        )
    }

    private fun jobFromJson(o: JSONObject): Job {
        val rooms = o.getJSONArray("rooms")
        return Job(
            id = o.getString("id"),
            mode = enumOr(o.getString("mode"), AppMode.WORKER_ESTIMATE),
            createdIso = o.getString("createdIso"),
            hourlyRateInr = o.getDouble("hourlyRateInr"),
            paceFactor = o.getDouble("paceFactor"),
            holdHeightM = o.getDouble("holdHeightM").toFloat(),
            rooms = (0 until rooms.length()).map { roomFromJson(rooms.getJSONObject(it)) },
            bookingId = o.optStringOrNull("bookingId")
        )
    }

    private fun roomFromJson(o: JSONObject): RoomEntry {
        val photos = o.getJSONArray("photos")
        val analyses = o.getJSONArray("photoAnalyses")
        val tasks = o.getJSONArray("tasks")
        return RoomEntry(
            id = o.getString("id"),
            roomType = enumOr(o.getString("roomType"), RoomType.OTHER),
            suggestedRoomType = o.optStringOrNull("suggestedRoomType")?.let { enumOr(it, RoomType.OTHER) },
            dimensions = o.optJSONObject("dimensions")?.let(::dimsFromJson),
            photos = (0 until photos.length()).map { photoFromJson(photos.getJSONObject(it)) },
            photoAnalyses = (0 until analyses.length()).map { analysisFromJson(analyses.getJSONObject(it)) },
            stain = o.optJSONObject("stain")?.let(::stainFromJson),
            severityOverride = o.optStringOrNull("severityOverride")?.let { enumOr(it, SeverityLevel.LIGHT) },
            clutter = enumOr(o.optString("clutter"), ClutterLevel.LOW),
            tasks = (0 until tasks.length()).map { taskFromJson(tasks.getJSONObject(it)) }
        )
    }

    private fun dimsFromJson(o: JSONObject) = RoomDimensions(
        length = o.getDouble("length"), width = o.getDouble("width"),
        unit = enumOr(o.getString("unit"), LengthUnit.FEET),
        computedAreaSqFt = o.getDouble("computedAreaSqFt"),
        source = enumOr(o.getString("source"), SizeSource.MANUAL)
    )

    private fun photoFromJson(o: JSONObject) = EvidencePhoto(
        id = o.getString("id"), role = enumOr(o.getString("role"), PhotoRole.WIDE), uri = Uri.parse(o.getString("uri")),
        timestampIso = o.getString("timestampIso"), sha256Hash = o.getString("sha256Hash"),
        depressionDeg = if (o.isNull("depressionDeg")) null else o.getDouble("depressionDeg").toFloat(),
        holdHeightM = if (o.isNull("holdHeightM")) null else o.getDouble("holdHeightM").toFloat()
    )

    private fun analysisFromJson(o: JSONObject): PhotoAnalysis {
        val dets = o.getJSONArray("detections")
        return PhotoAnalysis(
            photoId = o.getString("photoId"), imageWidth = o.getInt("imageWidth"), imageHeight = o.getInt("imageHeight"),
            detections = (0 until dets.length()).map {
                val d = dets.getJSONObject(it)
                DetectedObject(
                    label = d.getString("label"), confidence = d.getDouble("confidence").toFloat(),
                    boundingBox = RectF(d.getDouble("l").toFloat(), d.getDouble("t").toFloat(), d.getDouble("r").toFloat(), d.getDouble("b").toFloat())
                )
            }
        )
    }

    private fun stainFromJson(o: JSONObject) = StainResult(enumOr(o.getString("severity"), SeverityLevel.LIGHT), o.getDouble("score").toFloat())

    private fun taskFromJson(o: JSONObject) = CleaningTask(
        id = o.getString("id"), catalogKey = o.getString("catalogKey"), titleEn = o.getString("titleEn"), titleMl = o.getString("titleMl"),
        quantity = o.getInt("quantity"), minutesPerUnit = o.getInt("minutesPerUnit"), materialCostInr = o.getDouble("materialCostInr"),
        isSelected = o.getBoolean("isSelected"), source = enumOr(o.getString("source"), TaskSource.MANUAL)
    )

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
