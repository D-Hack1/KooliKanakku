package com.koolikanaku.connect.data

import android.content.Context
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.Role
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Real backend (extra_feature.md Section 16: Supabase/Postgrest + SQL-function rules, see
 * supabase/schema.sql) behind the same interfaces [DemoConnectRepository] implements, so the rest
 * of the app never knows which one is active (picked by [ConnectBackend]).
 *
 * Identity is REAL Supabase Auth, not a client-trusted uid: Supabase has no SMS provider
 * (Section 16), so "phone + 6-digit PIN" on screen is implemented underneath as email+password,
 * with a synthetic, non-deliverable email built from the phone number. Every schema.sql function
 * reads `auth.uid()` from the signed request's JWT, so this repository's whole job is keeping that
 * JWT (access token) current - see [ensureFreshSession] - and letting [SupabaseHttp] attach it.
 */
object SupabaseConnectRepository : AuthRepository, WorkerRepository, BookingRepository, RatingRepository {

    private const val PREFS = "connect_session_v2"
    private const val POLL_INTERVAL_MS = 3000L
    private const val EMAIL_DOMAIN = "kooli.koolikanaku.app"

    private lateinit var prefs: android.content.SharedPreferences
    private var cachedUser: ConnectUser? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uid = prefs.getString("uid", null)
        if (uid != null) {
            SupabaseHttp.sessionAccessToken = prefs.getString("accessToken", null)
            cachedUser = ConnectUser(
                uid = uid, phone = prefs.getString("phone", "") ?: "",
                role = runCatching { Role.valueOf(prefs.getString("role", "OWNER") ?: "OWNER") }.getOrDefault(Role.OWNER),
                name = prefs.getString("name", "") ?: "", createdAtEpochMs = 0L
            )
        }
    }

    private fun emailFor(phone: String) = "p$phone@$EMAIL_DOMAIN"

    /** Refreshes the access token if it's expired (or about to), so every authenticated call carries a live JWT. */
    private suspend fun ensureFreshSession() {
        val refreshToken = prefs.getString("refreshToken", null) ?: return
        val expiresAt = prefs.getLong("expiresAt", 0L)
        if (System.currentTimeMillis() < expiresAt - 30_000) return
        val session = runCatching { SupabaseHttp.authRefresh(refreshToken) }.getOrNull() ?: return
        storeSession(session)
    }

    private fun storeSession(session: JSONObject) {
        val accessToken = if (session.has("access_token")) session.optString("access_token") else return
        val refreshToken = session.optString("refresh_token", accessToken)
        val expiresIn = session.optLong("expires_in", 3600L)
        SupabaseHttp.sessionAccessToken = accessToken
        prefs.edit()
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .putLong("expiresAt", System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
    }

    private fun rememberProfile(uid: String, phone: String, role: Role, name: String) {
        cachedUser = ConnectUser(uid, phone, role, name, System.currentTimeMillis())
        prefs.edit().putString("uid", uid).putString("phone", phone).putString("role", role.name).putString("name", name).apply()
    }

    // ---------------- AuthRepository ----------------

    override suspend fun signUp(phone: String, pin: String, role: Role, name: String): Result<ConnectUser> = runCatching {
        val authResult = SupabaseHttp.authSignUp(emailFor(phone), pin)
        val uid = authResult.optJSONObject("user")?.optString("id") ?: authResult.optString("id", "")
        if (uid.isBlank()) throw SupabaseHttp.ApiException("Sign-up didn't return a user - check the project's Auth settings")
        if (!authResult.has("access_token")) {
            throw SupabaseHttp.ApiException("Signed up, but no session came back - turn off \"Confirm email\" in the Supabase project's Auth settings (see supabase/schema.sql)")
        }
        storeSession(authResult)
        val profileRow = SupabaseHttp.rpc("complete_signup", mapOf("p_phone" to phone, "p_role" to role.name, "p_name" to name)) as JSONObject
        rememberProfile(uid, phone, role, profileRow.optString("name", name))
        cachedUser!!
    }

    override suspend fun signIn(phone: String, pin: String): Result<ConnectUser> = runCatching {
        val authResult = SupabaseHttp.authSignIn(emailFor(phone), pin)
        val uid = authResult.optJSONObject("user")?.optString("id") ?: authResult.optString("id", "")
        if (uid.isBlank() || !authResult.has("access_token")) throw SupabaseHttp.ApiException("Wrong phone number or PIN")
        storeSession(authResult)
        val profileRow = SupabaseHttp.rpc("my_profile", emptyMap()) as? JSONObject
        if (profileRow == null || !profileRow.has("uid")) throw SupabaseHttp.ApiException("Account has no profile - try creating the account again")
        rememberProfile(uid, profileRow.optString("phone", phone), enumOr(profileRow.getString("role"), Role.OWNER), profileRow.getString("name"))
        cachedUser!!
    }

    override fun currentUser(): ConnectUser? = cachedUser

    override suspend fun phoneExists(phone: String): Boolean =
        runCatching { SupabaseHttp.rpc("phone_exists", mapOf("p_phone" to phone), expectSingleObject = false) as? Boolean }
            .getOrNull() ?: false

    override fun signOut() {
        cachedUser = null
        SupabaseHttp.sessionAccessToken = null
        prefs.edit().clear().apply()
    }

    // ---------------- WorkerRepository ----------------

    override suspend fun saveProfile(profile: WorkerProfile) {
        ensureFreshSession()
        SupabaseHttp.rpc(
            "save_worker_profile",
            mapOf(
                "p_name" to profile.name, "p_languages" to profile.languages, "p_services" to profile.services,
                "p_rate_min" to profile.rateMinInr, "p_rate_max" to profile.rateMaxInr, "p_bio" to profile.bio
            ),
            expectSingleObject = false
        )
    }

    override suspend fun getProfile(uid: String): WorkerProfile? {
        ensureFreshSession()
        val row = SupabaseHttp.rpc("get_worker_profile", mapOf("p_uid" to uid)) as? JSONObject ?: return null
        if (!row.has("uid")) return null
        return WorkerProfile(
            uid = row.getString("uid"), name = row.getString("name"),
            languages = row.optJSONArray("languages").toStringList(), services = row.optJSONArray("services").toStringList(),
            rateMinInr = row.optDouble("rate_min", 0.0), rateMaxInr = row.optDouble("rate_max", 0.0), bio = row.optString("bio", "")
        )
    }

    override suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, areaLabel: String?, expiresAtEpochMs: Long?) {
        ensureFreshSession()
        SupabaseHttp.rpc(
            "set_availability",
            mapOf(
                "p_open" to open, "p_geohash" to geohash, "p_area_label" to areaLabel,
                "p_expires_at" to expiresAtEpochMs?.let { java.time.Instant.ofEpochMilli(it).toString() }
            ),
            expectSingleObject = false
        )
    }

    override suspend fun isOpen(uid: String): Boolean {
        ensureFreshSession()
        val row = SupabaseHttp.rpc("my_availability", emptyMap()) as? JSONObject ?: return false
        if (!row.has("open")) return false
        val open = row.optBoolean("open", false)
        val expiresAt = row.optStringOrNull("expires_at")?.let { parseInstant(it) }
        return open && (expiresAt == null || expiresAt > System.currentTimeMillis())
    }

    override fun nearbyOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerCard>> = pollingFlow {
        val ownerGeohash5 = geohashPrefixes.firstOrNull()?.take(5)
        val rows = SupabaseHttp.get("open_workers", "select=*")
        (0 until rows.length()).map { i -> cardFromOpenWorkerRow(rows.getJSONObject(i), ownerGeohash5) }
            .sortedByDescending { it.ratingAvg * it.ratingCount + it.verifiedCount }
    }

    override suspend fun cardFor(uid: String): WorkerCard? {
        val rows = SupabaseHttp.get("open_workers", "uid=eq.${SupabaseHttp.encode(uid)}&select=*")
        if (rows.length() == 0) return null
        return cardFromOpenWorkerRow(rows.getJSONObject(0), null)
    }

    private fun cardFromOpenWorkerRow(o: JSONObject, ownerGeohash5: String?): WorkerCard {
        val workerGeohash5 = o.optStringOrNull("geohash5")
        val shared = if (ownerGeohash5 != null && workerGeohash5 != null) ownerGeohash5.commonPrefixWith(workerGeohash5).length else 0
        val band = when { ownerGeohash5 == null || workerGeohash5 == null -> "nearby"; shared >= 5 -> "under 5 km"; shared >= 4 -> "about 10 km"; else -> "in your area" }
        return WorkerCard(
            uid = o.getString("uid"), name = o.getString("name"),
            languages = o.optJSONArray("languages").toStringList(), services = o.optJSONArray("services").toStringList(),
            rateMinInr = o.optDouble("rate_min", 0.0), rateMaxInr = o.optDouble("rate_max", 0.0), bio = o.optString("bio", ""),
            ratingAvg = o.optDouble("rating_avg", 0.0), ratingCount = o.optInt("rating_count", 0), verifiedCount = o.optInt("verified_count", 0),
            distanceBandLabel = band
        )
    }

    // ---------------- BookingRepository ----------------

    override suspend fun request(ownerId: String, workerId: String, whenIso: String, areaLabel: String, sizeHint: String): Booking {
        ensureFreshSession()
        val row = SupabaseHttp.rpc("request_booking", mapOf("p_worker_id" to workerId, "p_when" to whenIso, "p_area_label" to areaLabel, "p_size_hint" to sizeHint)) as JSONObject
        return bookingFromRow(row)
    }

    override fun observeForUser(uid: String): Flow<List<Booking>> = pollingFlow {
        ensureFreshSession()
        val rows = SupabaseHttp.rpc("get_bookings_for_user", emptyMap(), expectSingleObject = false) as JSONArray
        (0 until rows.length()).map { bookingFromRow(rows.getJSONObject(it)) }
    }

    override fun observe(bookingId: String): Flow<Booking?> = pollingFlow {
        ensureFreshSession()
        val result = SupabaseHttp.rpc("get_booking", mapOf("p_booking_id" to bookingId))
        if (result is JSONObject && result.has("id")) bookingFromRow(result) else null
    }

    override suspend fun respond(bookingId: String, accept: Boolean) {
        ensureFreshSession()
        SupabaseHttp.rpc("booking_action", mapOf("p_booking_id" to bookingId, "p_action" to if (accept) "accept" else "decline"))
    }

    override suspend fun setStatus(bookingId: String, status: BookingStatus) {
        ensureFreshSession()
        val action = when (status) { BookingStatus.ARRIVED -> "arrived"; BookingStatus.IN_PROGRESS -> "in_progress"; else -> return }
        SupabaseHttp.rpc("booking_action", mapOf("p_booking_id" to bookingId, "p_action" to action))
    }

    override suspend fun complete(bookingId: String, summary: EvidenceSummary) {
        ensureFreshSession()
        val evidence = mapOf(
            "billFingerprint" to summary.billFingerprint, "totalPriceLowInr" to summary.totalPriceLowInr,
            "totalPriceHighInr" to summary.totalPriceHighInr, "roomCount" to summary.roomCount,
            "afterPhotoHashes" to summary.afterPhotoHashes, "photoCount" to summary.photoCount, "generatedAtEpochMs" to summary.generatedAtEpochMs
        )
        SupabaseHttp.rpc("complete_booking", mapOf("p_booking_id" to bookingId, "p_evidence" to evidence))
    }

    override suspend fun cancel(bookingId: String, reason: String) {
        ensureFreshSession()
        SupabaseHttp.rpc("cancel_booking", mapOf("p_booking_id" to bookingId, "p_reason" to reason))
    }

    override suspend fun counterpartPhone(bookingId: String, requesterId: String): String? {
        ensureFreshSession()
        val result = runCatching { SupabaseHttp.rpc("get_counterpart_phone", mapOf("p_booking_id" to bookingId), expectSingleObject = false) }.getOrNull()
        return result as? String
    }

    private fun bookingFromRow(o: JSONObject): Booking {
        val evidenceObj = o.optJSONObject("evidence")
        val evidence = evidenceObj?.let { e ->
            EvidenceSummary(
                billFingerprint = e.optString("billFingerprint", ""), totalPriceLowInr = e.optDouble("totalPriceLowInr", 0.0),
                totalPriceHighInr = e.optDouble("totalPriceHighInr", 0.0), roomCount = e.optInt("roomCount", 0),
                afterPhotoHashes = e.optJSONArray("afterPhotoHashes").toStringList(), photoCount = e.optInt("photoCount", 0),
                generatedAtEpochMs = e.optLong("generatedAtEpochMs", 0L)
            )
        }
        return Booking(
            id = o.getString("id"), ownerId = o.getString("owner_id"), workerId = o.getString("worker_id"),
            workerName = o.getString("worker_name"), ownerName = o.getString("owner_name"),
            status = enumOr(o.getString("status"), BookingStatus.REQUESTED), requestedForIso = o.getString("requested_for"),
            areaLabel = o.getString("area_label"), sizeHint = o.getString("size_hint"),
            createdAtEpochMs = o.optStringOrNull("created_at")?.let { parseInstant(it) } ?: 0L,
            acceptedAtEpochMs = o.optStringOrNull("accepted_at")?.let { parseInstant(it) },
            arrivedAtEpochMs = o.optStringOrNull("arrived_at")?.let { parseInstant(it) },
            completedAtEpochMs = o.optStringOrNull("completed_at")?.let { parseInstant(it) },
            evidence = evidence, cancelReason = o.optStringOrNull("cancel_reason")
        )
    }

    // ---------------- RatingRepository ----------------

    override suspend fun rate(bookingId: String, stars: Int, tags: List<String>, comment: String) {
        ensureFreshSession()
        SupabaseHttp.rpc("rate_booking", mapOf("p_booking_id" to bookingId, "p_stars" to stars, "p_tags" to tags, "p_comment" to comment))
    }

    override fun ratingsFor(workerId: String): Flow<List<Rating>> = pollingFlow {
        val rows = SupabaseHttp.get("ratings", "worker_id=eq.${SupabaseHttp.encode(workerId)}&select=*&order=created_at.desc")
        (0 until rows.length()).map { ratingFromRow(rows.getJSONObject(it)) }
    }

    private fun ratingFromRow(o: JSONObject) = Rating(
        bookingId = o.getString("booking_id"), workerId = o.getString("worker_id"), ownerId = o.getString("owner_id"),
        stars = o.getInt("stars"), tags = o.optJSONArray("tags").toStringList(), comment = o.optString("comment", ""),
        verified = o.getBoolean("verified"), createdAtEpochMs = o.optStringOrNull("created_at")?.let { parseInstant(it) } ?: 0L
    )

    // ---------------- helpers ----------------

    private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
        while (true) {
            runCatching { fetch() }.onSuccess { emit(it) }
            delay(POLL_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else if (has(key)) optString(key) else null
    private fun parseInstant(iso: String): Long = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)
    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
