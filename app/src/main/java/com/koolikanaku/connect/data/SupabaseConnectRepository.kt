package com.koolikanaku.connect.data

import com.koolikanaku.connect.data.ConnectJson.longOrNull
import com.koolikanaku.connect.data.ConnectJson.stringOrNull
import com.koolikanaku.connect.logic.BookingStateMachine
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerAvailability
import com.koolikanaku.connect.model.WorkerListing
import com.koolikanaku.connect.model.WorkerProfile
import com.koolikanaku.connect.model.WorkerStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/**
 * The real backend: a Supabase project (PostgREST + GoTrue). The booking rules are enforced in the
 * database (see supabase/schema.sql); this class only sends requests and reads results.
 * Plain HttpURLConnection and org.json, so there is no extra library to break the build.
 * Live updates are done by polling every few seconds.
 */
class SupabaseConnectRepository(
    baseUrl: String,
    private val anonKey: String,
    private val sessions: SessionStore,
    private val pollIntervalMs: Long = 3_000,
    private val clock: () -> Long = System::currentTimeMillis
) : ConnectBackend {

    private val baseUrl = baseUrl.trim().trimEnd('/')
    override val isDemo: Boolean = false
    private val error = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = error

    @Volatile private var session: Session? = sessions.load()

    private class Response(val status: Int, val body: String) {
        val ok get() = status in 200..299
        fun json(): Any? = body.trim().let { t ->
            when {
                t.startsWith("{") -> JSONObject(t)
                t.startsWith("[") -> JSONArray(t)
                else -> null
            }
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun rawCall(method: String, path: String, token: String?, body: String?, extraHeaders: Map<String, String>): Response {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            conn.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Response(status, text)
        } finally {
            conn.disconnect()
        }
    }

    /** A request that carries the signed-in user's token, and renews it once if the server says it expired. */
    private suspend fun call(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap(), allowRefresh: Boolean = true): Response =
        withContext(Dispatchers.IO) {
            val current = session
            val response = rawCall(method, path, current?.accessToken, body, headers)
            if (response.status == 401 && current != null && allowRefresh) {
                if (refreshSession(current)) return@withContext call(method, path, body, headers, allowRefresh = false)
            }
            response
        }

    private fun refreshSession(old: Session): Boolean = try {
        val resp = rawCall("POST", "/auth/v1/token?grant_type=refresh_token", null, JSONObject().put("refresh_token", old.refreshToken).toString(), emptyMap())
        if (resp.ok) {
            val next = sessionFrom(resp.json() as JSONObject, old.user)
            session = next; sessions.save(next); true
        } else {
            session = null; sessions.save(null); false
        }
    } catch (e: IOException) { false }

    private fun message(resp: Response): String {
        val json = runCatching { resp.json() as? JSONObject }.getOrNull()
        val text = json?.let { it.stringOrNull("message") ?: it.stringOrNull("msg") ?: it.stringOrNull("error_description") ?: it.stringOrNull("error") }
        return text?.takeIf { it.isNotBlank() } ?: "Server error (${resp.status})"
    }

    /** Runs blocking request code off the main thread and turns failures into a readable [Result]. */
    private suspend fun <T> network(block: () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block().also { if (it.isSuccess) error.value = null }
        } catch (e: IOException) {
            error.value = "No connection to the server"
            Result.failure(ConnectException("No connection to the server"))
        } catch (e: Exception) {
            Result.failure(ConnectException(e.message ?: "Something went wrong"))
        }
    }

    private fun q(value: String) = URLEncoder.encode(value, "UTF-8")

    // ---------------------------------------------------------------- auth

    private fun emailFor(phone: String) = "${phone}@users.koolikanaku.app"
    private fun passwordFor(pin: String) = "KK$pin"      // GoTrue wants at least 6 characters

    override suspend fun signIn(phone: String, pin: String, name: String, role: UserRole): Result<ConnectUser> {
        val digits = phone.filter { it.isDigit() }.let { if (it.length > 10) it.takeLast(10) else it }
        if (digits.length < 10) return Result.failure(ConnectException("Enter a valid phone number"))
        if (!Pin.isValid(pin)) return Result.failure(ConnectException("The PIN must be ${Pin.LENGTH} digits"))
        return run {
            network {
                val creds = JSONObject().put("email", emailFor(digits)).put("password", passwordFor(pin))
                val login = rawCall("POST", "/auth/v1/token?grant_type=password", null, creds.toString(), emptyMap())
                if (login.ok) return@network finishSignIn(login, digits, role, null)

                // not an account yet, or the wrong PIN: try to create it
                if (name.isBlank()) return@network Result.failure(ConnectException(
                    if (login.status == 400) "Wrong PIN, or this number is new: enter your name to create an account" else message(login)))
                val meta = JSONObject().put("name", name.trim().take(60)).put("role", role.name).put("phone", digits)
                val signup = rawCall("POST", "/auth/v1/signup", null, JSONObject(creds.toString()).put("data", meta).toString(), emptyMap())
                when {
                    !signup.ok -> {
                        val text = message(signup)
                        Result.failure(ConnectException(if (text.contains("already", ignoreCase = true)) "Wrong PIN for this number" else text))
                    }
                    (signup.json() as? JSONObject)?.has("access_token") != true -> Result.failure(ConnectException(
                        "The server needs 'Confirm email' turned off (Supabase > Authentication > Providers > Email)"))
                    else -> finishSignIn(signup, digits, role, name.trim())
                }
            }
        }
    }

    private fun finishSignIn(resp: Response, phone: String, requestedRole: UserRole, newName: String?): Result<ConnectUser> {
        val body = resp.json() as JSONObject
        val meta = body.optJSONObject("user")?.optJSONObject("user_metadata")
        val role = ConnectJson.enumOr(meta?.stringOrNull("role"), requestedRole)
        if (role != requestedRole) return Result.failure(ConnectException("This number is registered as a ${role.name.lowercase()}"))
        val uid = body.optJSONObject("user")?.getString("id") ?: return Result.failure(ConnectException("Unexpected server answer"))
        val user = ConnectUser(uid, role, meta?.stringOrNull("name") ?: newName ?: "", meta?.stringOrNull("phone") ?: phone)
        val s = sessionFrom(body, user)
        session = s; sessions.save(s)
        return Result.success(user)
    }

    private fun sessionFrom(body: JSONObject, user: ConnectUser) = Session(body.getString("access_token"), body.optString("refresh_token"), user)

    override fun currentUser(): ConnectUser? = session?.user
    override fun signOut() { session = null; sessions.save(null) }

    // ---------------------------------------------------------------- workers

    override suspend fun saveProfile(profile: WorkerProfile): Result<Unit> = network {
        val body = JSONObject()
            .put("id", profile.uid).put("name", profile.name)
            .put("languages", ConnectJson.jsonArray(profile.languages))
            .put("services", ConnectJson.jsonArray(profile.services.map { it.name }))
            .put("rate_min", profile.rateMinInr).put("rate_max", profile.rateMaxInr).put("bio", profile.bio)
        val r = callBlocking("POST", "/rest/v1/workers?on_conflict=id", body.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        if (r.ok) Result.success(Unit) else Result.failure(ConnectException(message(r)))
    }

    override suspend fun profileOf(uid: String): WorkerProfile? = try {
        val r = call("GET", "/rest/v1/workers?id=eq.${q(uid)}&select=*")
        (r.json() as? JSONArray)?.takeIf { it.length() > 0 }?.getJSONObject(0)?.let { parseListing(it, phoneFor = null).profile }
    } catch (e: IOException) { null }

    override suspend fun availabilityOf(uid: String): WorkerAvailability? = try {
        val r = call("GET", "/rest/v1/workers?id=eq.${q(uid)}&select=*")
        (r.json() as? JSONArray)?.takeIf { it.length() > 0 }?.getJSONObject(0)?.let { parseListing(it, phoneFor = null).availability }
    } catch (e: IOException) { null }

    override suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, expiresAtMs: Long?): Result<Unit> = network {
        val body = JSONObject().put("p_open", open)
            .put("p_geohash", if (open && geohash != null) geohash else JSONObject.NULL)
            .put("p_expires", if (open && expiresAtMs != null) Instant.ofEpochMilli(expiresAtMs).toString() else JSONObject.NULL)
        val r = callBlocking("POST", "/rest/v1/rpc/set_availability", body.toString())
        if (r.ok) Result.success(Unit) else Result.failure(ConnectException(message(r)))
    }

    override fun observeOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerListing>> = poll {
        if (geohashPrefixes.isEmpty()) return@poll emptyList()
        val or = geohashPrefixes.joinToString(",", "(", ")") { "geohash.like.$it*" }
        val now = q(Instant.ofEpochMilli(clock()).toString())
        val r = call("GET", "/rest/v1/workers?select=*&open=eq.true&expires_at=gt.$now&or=${q(or)}")
        if (!r.ok) throw ConnectException(message(r))
        (r.json() as JSONArray).let { a -> (0 until a.length()).map { parseListing(a.getJSONObject(it), phoneFor = null) } }
    }

    // ---------------------------------------------------------------- bookings

    override suspend fun request(ownerId: String, workerId: String, requestedForIso: String, areaLabel: String, sizeHint: String): Result<Booking> = network {
        val body = JSONObject().put("p_worker", workerId).put("p_requested_for", requestedForIso).put("p_area", areaLabel).put("p_size", sizeHint)
        val r = callBlocking("POST", "/rest/v1/rpc/request_booking", body.toString())
        if (r.ok) Result.success(parseBooking(r.json() as JSONObject)) else Result.failure(ConnectException(message(r)))
    }

    override fun observe(bookingId: String): Flow<Booking?> = poll {
        runCatching { call("POST", "/rest/v1/rpc/expire_stale_bookings", "{}") }
        val r = call("GET", "/rest/v1/bookings?id=eq.${q(bookingId)}&select=*")
        if (!r.ok) throw ConnectException(message(r))
        (r.json() as JSONArray).takeIf { it.length() > 0 }?.let { parseBooking(it.getJSONObject(0)) }
    }

    override fun observeFor(uid: String): Flow<List<Booking>> = poll {
        runCatching { call("POST", "/rest/v1/rpc/expire_stale_bookings", "{}") }
        val r = call("GET", "/rest/v1/bookings?select=*&order=created_at.desc")
        if (!r.ok) throw ConnectException(message(r))
        (r.json() as JSONArray).let { a -> (0 until a.length()).map { parseBooking(a.getJSONObject(it)) } }
            .filter { it.ownerId == uid || it.workerId == uid }
    }

    override suspend fun perform(bookingId: String, uid: String, action: BookingStateMachine.Action, evidence: EvidenceSummary?, jobId: String?): Result<Booking> = network {
        val body = JSONObject().put("p_booking", bookingId).put("p_action", action.name)
        if (evidence != null) body.put("p_evidence", ConnectJson.evidenceToJson(evidence))
        if (jobId != null) body.put("p_job", jobId)
        val r = callBlocking("POST", "/rest/v1/rpc/booking_action", body.toString())
        if (r.ok) Result.success(parseBooking(r.json() as JSONObject)) else Result.failure(ConnectException(message(r)))
    }

    // ---------------------------------------------------------------- ratings

    override suspend fun rate(bookingId: String, ownerUid: String, stars: Int, tags: List<String>, comment: String): Result<Rating> = network {
        val body = JSONObject().put("p_booking", bookingId).put("p_stars", stars).put("p_tags", ConnectJson.jsonArray(tags)).put("p_comment", comment)
        val r = callBlocking("POST", "/rest/v1/rpc/rate_booking", body.toString())
        if (r.ok) Result.success(parseRating(r.json() as JSONObject)) else Result.failure(ConnectException(message(r)))
    }

    override fun ratingsFor(workerId: String): Flow<List<Rating>> = poll {
        val r = call("GET", "/rest/v1/ratings?worker_id=eq.${q(workerId)}&select=*&order=created_at.desc")
        if (!r.ok) throw ConnectException(message(r))
        (r.json() as JSONArray).let { a -> (0 until a.length()).map { parseRating(a.getJSONObject(it)) } }
    }

    // ---------------------------------------------------------------- helpers

    /** The suspend-free path used inside [network] (which is already on the IO dispatcher). */
    private fun callBlocking(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()): Response {
        val current = session
        val response = rawCall(method, path, current?.accessToken, body, headers)
        if (response.status == 401 && current != null && refreshSession(current)) {
            return rawCall(method, path, session?.accessToken, body, headers)
        }
        return response
    }

    /** Re-reads from the server every few seconds and only emits when something changed. */
    private fun <T> poll(fetch: suspend () -> T): Flow<T> = flow {
        while (true) {
            try {
                emit(fetch())
                error.value = null
            } catch (e: IOException) {
                error.value = "No connection to the server"
            } catch (e: ConnectException) {
                error.value = e.message
            } catch (e: org.json.JSONException) {
                error.value = "Unexpected answer from the server"
            }
            delay(pollIntervalMs)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    internal companion object {
        fun parseTime(text: String?): Long? = text?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

        fun parseListing(o: JSONObject, phoneFor: String?): WorkerListing {
            val profile = WorkerProfile(
                uid = o.getString("id"), name = o.getString("name"), phone = phoneFor ?: "",
                languages = ConnectJson.strings(o.optJSONArray("languages")),
                services = ConnectJson.services(ConnectJson.strings(o.optJSONArray("services"))),
                rateMinInr = o.getInt("rate_min"), rateMaxInr = o.getInt("rate_max"),
                bio = o.optString("bio"), isDemo = o.optBoolean("is_demo")
            )
            val availability = WorkerAvailability(o.optBoolean("open"), o.stringOrNull("geohash"), parseTime(o.stringOrNull("expires_at")))
            val stats = WorkerStats(
                ratingCount = o.optInt("rating_count"), verifiedCount = o.optInt("verified_count"),
                weightedStarSum = o.optDouble("weighted_star_sum"), weightSum = o.optDouble("weight_sum"),
                completedCount = o.optInt("completed_count"), cancelCount = o.optInt("cancel_count")
            )
            return WorkerListing(profile, availability, stats)
        }

        fun parseBooking(o: JSONObject): Booking = Booking(
            id = o.getString("id"), ownerId = o.getString("owner_id"), workerId = o.getString("worker_id"),
            ownerName = o.getString("owner_name"), workerName = o.getString("worker_name"),
            status = ConnectJson.enumOr(o.getString("status"), BookingStatus.CANCELLED),
            requestedForIso = o.optString("requested_for"), areaLabel = o.optString("area_label"), sizeHint = o.optString("size_hint"),
            createdAtMs = parseTime(o.stringOrNull("created_at")) ?: 0L, updatedAtMs = parseTime(o.stringOrNull("updated_at")) ?: 0L,
            ownerPhone = o.stringOrNull("owner_phone"), workerPhone = o.stringOrNull("worker_phone"),
            cancelledBy = o.stringOrNull("cancelled_by")?.let { ConnectJson.enumOr(it, UserRole.OWNER) },
            evidence = o.optJSONObject("evidence")?.let(ConnectJson::evidenceFromJson), jobId = o.stringOrNull("job_id")
        )

        fun parseRating(o: JSONObject): Rating = Rating(
            bookingId = o.getString("booking_id"), workerId = o.getString("worker_id"), ownerId = o.getString("owner_id"),
            stars = o.getInt("stars"), tags = ConnectJson.strings(o.optJSONArray("tags")), comment = o.optString("comment"),
            verified = o.optBoolean("verified"), createdAtMs = parseTime(o.stringOrNull("created_at")) ?: 0L
        )
    }
}
