package com.koolikanaku.connect.data

import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerProfile
import com.koolikanaku.connect.model.WorkerStats
import org.json.JSONArray
import org.json.JSONObject

/** Small JSON helpers shared by the demo backend's saved state and the server client. */
internal object ConnectJson {

    fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

    fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }
    fun JSONArray.toStrings(): List<String> = strings(this)
    fun jsonArray(items: List<String>): JSONArray = JSONArray().also { a -> items.forEach { a.put(it) } }

    fun services(names: List<String>): List<ServiceType> = names.mapNotNull { n -> ServiceType.values().firstOrNull { it.name == n } }

    inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    // ---- evidence (the same shape is sent to the server and checked by its SQL) ----

    fun evidenceToJson(e: EvidenceSummary): JSONObject = JSONObject().apply {
        put("billFingerprint", e.billFingerprint)
        put("totalPriceLowInr", e.totalPriceLowInr)
        put("totalPriceHighInr", e.totalPriceHighInr)
        put("roomCount", e.roomCount)
        put("photoCount", e.photoCount)
        put("afterPhotoHashes", jsonArray(e.afterPhotoHashes))
        put("generatedAtIso", e.generatedAtIso)
    }

    fun evidenceFromJson(o: JSONObject): EvidenceSummary = EvidenceSummary(
        billFingerprint = o.optString("billFingerprint"),
        totalPriceLowInr = o.optInt("totalPriceLowInr"),
        totalPriceHighInr = o.optInt("totalPriceHighInr"),
        roomCount = o.optInt("roomCount"),
        photoCount = o.optInt("photoCount"),
        afterPhotoHashes = strings(o.optJSONArray("afterPhotoHashes")),
        generatedAtIso = o.optString("generatedAtIso")
    )

    // ---- the demo backend's saved state (camelCase) ----

    fun profileToJson(p: WorkerProfile) = JSONObject().apply {
        put("uid", p.uid); put("name", p.name); put("phone", p.phone)
        put("languages", jsonArray(p.languages)); put("services", jsonArray(p.services.map { it.name }))
        put("rateMin", p.rateMinInr); put("rateMax", p.rateMaxInr); put("bio", p.bio); put("isDemo", p.isDemo)
    }

    fun profileFromJson(o: JSONObject) = WorkerProfile(
        uid = o.getString("uid"), name = o.getString("name"), phone = o.getString("phone"),
        languages = strings(o.optJSONArray("languages")), services = services(strings(o.optJSONArray("services"))),
        rateMinInr = o.getInt("rateMin"), rateMaxInr = o.getInt("rateMax"), bio = o.optString("bio"), isDemo = o.optBoolean("isDemo")
    )

    fun statsToJson(s: WorkerStats) = JSONObject().apply {
        put("ratingCount", s.ratingCount); put("verifiedCount", s.verifiedCount)
        put("weightedStarSum", s.weightedStarSum); put("weightSum", s.weightSum)
        put("completedCount", s.completedCount); put("cancelCount", s.cancelCount)
    }

    fun statsFromJson(o: JSONObject) = WorkerStats(
        o.optInt("ratingCount"), o.optInt("verifiedCount"), o.optDouble("weightedStarSum"), o.optDouble("weightSum"),
        o.optInt("completedCount"), o.optInt("cancelCount")
    )

    fun bookingToJson(b: Booking) = JSONObject().apply {
        put("id", b.id); put("ownerId", b.ownerId); put("workerId", b.workerId)
        put("ownerName", b.ownerName); put("workerName", b.workerName); put("status", b.status.name)
        put("requestedForIso", b.requestedForIso); put("areaLabel", b.areaLabel); put("sizeHint", b.sizeHint)
        put("createdAtMs", b.createdAtMs); put("updatedAtMs", b.updatedAtMs)
        put("ownerPhone", b.ownerPhone ?: JSONObject.NULL); put("workerPhone", b.workerPhone ?: JSONObject.NULL)
        put("cancelledBy", b.cancelledBy?.name ?: JSONObject.NULL)
        put("evidence", b.evidence?.let(::evidenceToJson) ?: JSONObject.NULL)
        put("jobId", b.jobId ?: JSONObject.NULL)
    }

    fun bookingFromJson(o: JSONObject) = Booking(
        id = o.getString("id"), ownerId = o.getString("ownerId"), workerId = o.getString("workerId"),
        ownerName = o.getString("ownerName"), workerName = o.getString("workerName"),
        status = enumOr(o.getString("status"), BookingStatus.CANCELLED),
        requestedForIso = o.getString("requestedForIso"), areaLabel = o.getString("areaLabel"), sizeHint = o.optString("sizeHint"),
        createdAtMs = o.getLong("createdAtMs"), updatedAtMs = o.getLong("updatedAtMs"),
        ownerPhone = o.stringOrNull("ownerPhone"), workerPhone = o.stringOrNull("workerPhone"),
        cancelledBy = o.stringOrNull("cancelledBy")?.let { enumOr(it, UserRole.OWNER) },
        evidence = o.optJSONObject("evidence")?.let(::evidenceFromJson), jobId = o.stringOrNull("jobId")
    )

    fun ratingToJson(r: Rating) = JSONObject().apply {
        put("bookingId", r.bookingId); put("workerId", r.workerId); put("ownerId", r.ownerId); put("stars", r.stars)
        put("tags", jsonArray(r.tags)); put("comment", r.comment); put("verified", r.verified); put("createdAtMs", r.createdAtMs)
    }

    fun ratingFromJson(o: JSONObject) = Rating(
        o.getString("bookingId"), o.getString("workerId"), o.getString("ownerId"), o.getInt("stars"),
        strings(o.optJSONArray("tags")), o.optString("comment"), o.optBoolean("verified"), o.getLong("createdAtMs")
    )
}
