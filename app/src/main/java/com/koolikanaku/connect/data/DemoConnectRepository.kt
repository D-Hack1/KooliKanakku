package com.koolikanaku.connect.data

import android.content.Context
import com.koolikanaku.capture.CryptoUtils
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectConstants
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.Role
import com.koolikanaku.connect.model.WorkerAvailability
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The demo/in-memory backend (extra_feature.md Section 16: "used when .env has no Supabase keys,
 * so the feature demos on one phone with no setup"). State lives in a single process-wide store,
 * persisted to a JSON file so it survives app restarts, with a handful of clearly-labelled sample
 * workers seeded in so the owner's Nearby list is never empty on a fresh phone (Section 2, cold start).
 *
 * This is local to one device: two phones running this backend do NOT see each other's bookings.
 * A real two-phone demo needs the real Supabase backend behind the same interfaces (Section 5.3).
 *
 * Never imported by ai/domain/capture/export (OfflineCoreGuardTest enforces this).
 */
private data class Store(
    val users: Map<String, ConnectUser> = emptyMap(),
    val pinHashes: Map<String, String> = emptyMap(), // uid -> sha256(phone + ":" + pin)
    val profiles: Map<String, WorkerProfile> = emptyMap(),
    val availability: Map<String, WorkerAvailability> = emptyMap(),
    val bookings: Map<String, Booking> = emptyMap(),
    val ratings: List<Rating> = emptyList(),
    val currentUid: String? = null
)

object DemoConnectRepository : AuthRepository, WorkerRepository, BookingRepository, RatingRepository {

    private const val FILE_NAME = "connect_demo_store.json"
    private const val DEMO_TAG = " (sample)"

    private lateinit var appContext: Context
    private var initialized = false
    private val storeFlow = MutableStateFlow(Store())
    private val store: Store get() = storeFlow.value

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        val loaded = load()
        storeFlow.value = if (loaded != null) loaded else seeded()
        if (loaded == null) persist()
    }

    private fun seeded(): Store {
        val now = System.currentTimeMillis()
        fun demoProfile(uid: String, name: String, langs: List<String>, services: List<String>, min: Double, max: Double, bio: String) =
            WorkerProfile(uid, name + DEMO_TAG, langs, services, min, max, bio)
        val demos = listOf(
            demoProfile("demo_w1", "Sreeja", listOf("Malayalam", "English"), listOf("kitchen", "bathroom", "general"), 220.0, 280.0, "8 years' experience, Kochi."),
            demoProfile("demo_w2", "Baiju", listOf("Malayalam"), listOf("general", "bedroom", "living room"), 200.0, 250.0, "Careful with fragile items."),
            demoProfile("demo_w3", "Anitha", listOf("Malayalam", "English", "Tamil"), listOf("kitchen", "bathroom"), 250.0, 320.0, "Deep-cleaning specialist.")
        )
        val availability = demos.associate {
            it.uid to WorkerAvailability(open = true, geohash = "tdr1v", areaLabel = "Kochi", expiresAtEpochMs = now + ConnectConstants.AVAILABILITY_WINDOW_MS, updatedAtEpochMs = now)
        }
        val ratings = listOf(
            Rating("seed1", "demo_w1", "seed_owner", 5, listOf("On time", "Careful"), "Great job", true, now),
            Rating("seed2", "demo_w1", "seed_owner", 5, listOf("Thorough"), "", true, now),
            Rating("seed3", "demo_w2", "seed_owner", 4, listOf("Fair price"), "", false, now),
            Rating("seed4", "demo_w3", "seed_owner", 5, listOf("Careful", "On time"), "Excellent", true, now)
        )
        return Store(profiles = demos.associateBy { it.uid }, availability = availability, ratings = ratings)
    }

    // ---------------- AuthRepository ----------------

    override suspend fun signUp(phone: String, pin: String, role: Role, name: String): Result<ConnectUser> {
        if (store.users.values.any { it.phone == phone }) return Result.failure(IllegalStateException("An account already exists for this number"))
        val uid = "u_${UUID.randomUUID()}"
        val user = ConnectUser(uid, phone, role, name, System.currentTimeMillis())
        mutate { it.copy(users = it.users + (uid to user), pinHashes = it.pinHashes + (uid to pinHash(phone, pin)), currentUid = uid) }
        return Result.success(user)
    }

    override suspend fun signIn(phone: String, pin: String): Result<ConnectUser> {
        val user = store.users.values.find { it.phone == phone } ?: return Result.failure(IllegalStateException("No account for this number"))
        if (store.pinHashes[user.uid] != pinHash(phone, pin)) return Result.failure(IllegalStateException("Wrong PIN"))
        mutate { it.copy(currentUid = user.uid) }
        return Result.success(user)
    }

    override fun currentUser(): ConnectUser? = store.currentUid?.let { store.users[it] }

    override suspend fun phoneExists(phone: String): Boolean = store.users.values.any { it.phone == phone }

    override fun signOut() {
        mutate { it.copy(currentUid = null) }
    }

    private fun pinHash(phone: String, pin: String) = CryptoUtils.sha256("$phone:$pin")

    // ---------------- WorkerRepository ----------------

    override suspend fun saveProfile(profile: WorkerProfile) {
        mutate { it.copy(profiles = it.profiles + (profile.uid to profile)) }
    }

    override suspend fun getProfile(uid: String): WorkerProfile? = store.profiles[uid]

    override suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, areaLabel: String?, expiresAtEpochMs: Long?) {
        mutate {
            it.copy(availability = it.availability + (uid to WorkerAvailability(open, geohash, areaLabel, expiresAtEpochMs, System.currentTimeMillis())))
        }
    }

    override suspend fun isOpen(uid: String): Boolean {
        val a = store.availability[uid] ?: return false
        return a.open && (a.expiresAtEpochMs == null || a.expiresAtEpochMs > System.currentTimeMillis())
    }

    override fun nearbyOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerCard>> =
        storeFlow.map { s ->
            val now = System.currentTimeMillis()
            val ownerGeohash = geohashPrefixes.firstOrNull()
            s.availability.entries
                .filter { (_, a) -> a.open && (a.expiresAtEpochMs == null || a.expiresAtEpochMs > now) }
                .mapNotNull { (uid, a) -> s.profiles[uid]?.let { toCard(it, a, s.ratings, ownerGeohash) to proximityRank(ownerGeohash, a.geohash) } }
                // Nearest first (longer shared geohash prefix = closer, Section 5.1's own precision guidance);
                // trust score (Section 7.3) only breaks ties between workers in the same distance band.
                .sortedWith(compareByDescending<Pair<WorkerCard, Int>> { it.second }.thenByDescending { trustScore(it.first) })
                .map { it.first }
        }

    /** Length of the shared geohash prefix: the standard coarse-proximity measure this app uses (Section 5.1) - more shared characters means a smaller, closer cell. */
    private fun proximityRank(ownerGeohash: String?, workerGeohash: String?): Int =
        if (ownerGeohash.isNullOrEmpty() || workerGeohash.isNullOrEmpty()) -1 else ownerGeohash.commonPrefixWith(workerGeohash).length

    override suspend fun cardFor(uid: String): WorkerCard? {
        val profile = store.profiles[uid] ?: return null
        val avail = store.availability[uid] ?: WorkerAvailability()
        return toCard(profile, avail, store.ratings, null)
    }

    private fun toCard(profile: WorkerProfile, avail: WorkerAvailability, allRatings: List<Rating>, ownerGeohash: String?): WorkerCard {
        val ratings = allRatings.filter { it.workerId == profile.uid }
        val avg = if (ratings.isEmpty()) 0.0 else ratings.sumOf { it.stars } / ratings.size.toDouble()
        val verified = ratings.count { it.verified }
        val band = distanceBand(ownerGeohash, avail.geohash)
        return WorkerCard(
            uid = profile.uid, name = profile.name, languages = profile.languages, services = profile.services,
            rateMinInr = profile.rateMinInr, rateMaxInr = profile.rateMaxInr, bio = profile.bio,
            ratingAvg = avg, ratingCount = ratings.size, verifiedCount = verified, distanceBandLabel = band
        )
    }

    private fun distanceBand(ownerGeohash: String?, workerGeohash: String?): String {
        if (ownerGeohash.isNullOrEmpty() || workerGeohash.isNullOrEmpty()) return "nearby"
        val shared = ownerGeohash.commonPrefixWith(workerGeohash).length
        return when {
            shared >= 6 -> "under 1 km"
            shared >= 5 -> "about 3 km"
            shared >= 4 -> "about 10 km"
            else -> "in your area"
        }
    }

    /** Smoothed trust score, extra_feature.md Section 7.3. */
    private fun trustScore(card: WorkerCard): Double {
        val weight = ConnectConstants.VERIFIED_RATING_WEIGHT * card.verifiedCount +
            ConnectConstants.UNVERIFIED_RATING_WEIGHT * (card.ratingCount - card.verifiedCount)
        val sumWeightedStars = card.ratingAvg * card.ratingCount // approximation: avg already folds verified+unverified equally
        val m = ConnectConstants.TRUST_PRIOR_WEIGHT
        return (sumWeightedStars + m * ConnectConstants.TRUST_PRIOR) / (card.ratingCount + m).let { if (it == 0.0) 1.0 else it }
    }

    // ---------------- BookingRepository ----------------

    override suspend fun request(ownerId: String, workerId: String, whenIso: String, areaLabel: String, sizeHint: String): Booking {
        val ownerName = store.users[ownerId]?.name ?: "Owner"
        val workerName = store.profiles[workerId]?.name ?: store.users[workerId]?.name ?: "Worker"
        val booking = Booking(
            id = "b_${UUID.randomUUID()}", ownerId = ownerId, workerId = workerId, workerName = workerName, ownerName = ownerName,
            status = BookingStatus.REQUESTED, requestedForIso = whenIso, areaLabel = areaLabel, sizeHint = sizeHint,
            createdAtEpochMs = System.currentTimeMillis()
        )
        mutate { it.copy(bookings = it.bookings + (booking.id to booking)) }
        return booking
    }

    override fun observeForUser(uid: String): Flow<List<Booking>> =
        storeFlow.map { s -> s.bookings.values.filter { it.ownerId == uid || it.workerId == uid }.sortedByDescending { it.createdAtEpochMs } }

    override fun observe(bookingId: String): Flow<Booking?> = storeFlow.map { it.bookings[bookingId] }

    override suspend fun respond(bookingId: String, accept: Boolean) {
        mutate { s ->
            val b = s.bookings[bookingId] ?: return@mutate s
            val updated = b.copy(
                status = if (accept) BookingStatus.ACCEPTED else BookingStatus.DECLINED,
                acceptedAtEpochMs = if (accept) System.currentTimeMillis() else b.acceptedAtEpochMs
            )
            s.copy(bookings = s.bookings + (bookingId to updated))
        }
    }

    override suspend fun setStatus(bookingId: String, status: BookingStatus) {
        mutate { s ->
            val b = s.bookings[bookingId] ?: return@mutate s
            val now = System.currentTimeMillis()
            val updated = b.copy(
                status = status,
                arrivedAtEpochMs = if (status == BookingStatus.ARRIVED) now else b.arrivedAtEpochMs,
                completedAtEpochMs = if (status == BookingStatus.COMPLETED) now else b.completedAtEpochMs
            )
            s.copy(bookings = s.bookings + (bookingId to updated))
        }
    }

    override suspend fun complete(bookingId: String, summary: EvidenceSummary) {
        mutate { s ->
            val b = s.bookings[bookingId] ?: return@mutate s
            val updated = b.copy(status = BookingStatus.COMPLETED, completedAtEpochMs = System.currentTimeMillis(), evidence = summary)
            s.copy(bookings = s.bookings + (bookingId to updated))
        }
    }

    override suspend fun cancel(bookingId: String, reason: String) {
        mutate { s ->
            val b = s.bookings[bookingId] ?: return@mutate s
            s.copy(bookings = s.bookings + (bookingId to b.copy(status = BookingStatus.CANCELLED, cancelReason = reason)))
        }
    }

    override suspend fun counterpartPhone(bookingId: String, requesterId: String): String? {
        val b = store.bookings[bookingId] ?: return null
        val counterpart = if (b.ownerId == requesterId) b.workerId else if (b.workerId == requesterId) b.ownerId else return null
        // Seeded sample workers have no real account/phone; fall back to a clearly-fake demo number.
        return store.users[counterpart]?.phone ?: "9000000000"
    }

    // ---------------- RatingRepository ----------------

    override suspend fun rate(bookingId: String, stars: Int, tags: List<String>, comment: String) {
        val b = store.bookings[bookingId] ?: return
        val verified = b.evidence != null
        val rating = Rating(bookingId, b.workerId, b.ownerId, stars.coerceIn(1, 5), tags, comment, verified, System.currentTimeMillis())
        mutate { s ->
            s.copy(
                ratings = s.ratings.filter { it.bookingId != bookingId } + rating,
                bookings = s.bookings[bookingId]?.let { existing -> s.bookings + (bookingId to existing.copy(status = BookingStatus.RATED)) } ?: s.bookings
            )
        }
    }

    override fun ratingsFor(workerId: String): Flow<List<Rating>> =
        storeFlow.map { s -> s.ratings.filter { it.workerId == workerId }.sortedByDescending { it.createdAtEpochMs } }

    // ---------------- persistence ----------------

    private fun mutate(transform: (Store) -> Store) {
        storeFlow.value = transform(store)
        persist()
    }

    private fun persist() {
        if (!initialized) return
        val json = toJson(store)
        runCatching {
            val dir = appContext.filesDir
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(File(dir, FILE_NAME))) { File(dir, FILE_NAME).writeText(json); tmp.delete() }
        }
    }

    private fun load(): Store? {
        val file = File(appContext.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { fromJson(file.readText()) }.getOrNull()
    }

    private fun toJson(s: Store): String = JSONObject().apply {
        put("users", JSONObject().also { o -> s.users.forEach { (k, v) -> o.put(k, userToJson(v)) } })
        put("pinHashes", JSONObject().also { o -> s.pinHashes.forEach { (k, v) -> o.put(k, v) } })
        put("profiles", JSONObject().also { o -> s.profiles.forEach { (k, v) -> o.put(k, profileToJson(v)) } })
        put("availability", JSONObject().also { o -> s.availability.forEach { (k, v) -> o.put(k, availToJson(v)) } })
        put("bookings", JSONObject().also { o -> s.bookings.forEach { (k, v) -> o.put(k, bookingToJson(v)) } })
        put("ratings", JSONArray().also { a -> s.ratings.forEach { a.put(ratingToJson(it)) } })
        put("currentUid", s.currentUid ?: JSONObject.NULL)
    }.toString()

    private fun userToJson(u: ConnectUser) = JSONObject().apply {
        put("uid", u.uid); put("phone", u.phone); put("role", u.role.name); put("name", u.name); put("createdAtEpochMs", u.createdAtEpochMs)
    }

    private fun profileToJson(p: WorkerProfile) = JSONObject().apply {
        put("uid", p.uid); put("name", p.name)
        put("languages", JSONArray(p.languages)); put("services", JSONArray(p.services))
        put("rateMinInr", p.rateMinInr); put("rateMaxInr", p.rateMaxInr); put("bio", p.bio)
    }

    private fun availToJson(a: WorkerAvailability) = JSONObject().apply {
        put("open", a.open); put("geohash", a.geohash ?: JSONObject.NULL); put("areaLabel", a.areaLabel ?: JSONObject.NULL)
        put("expiresAtEpochMs", a.expiresAtEpochMs ?: JSONObject.NULL); put("updatedAtEpochMs", a.updatedAtEpochMs)
    }

    private fun bookingToJson(b: Booking) = JSONObject().apply {
        put("id", b.id); put("ownerId", b.ownerId); put("workerId", b.workerId); put("workerName", b.workerName); put("ownerName", b.ownerName)
        put("status", b.status.name); put("requestedForIso", b.requestedForIso); put("areaLabel", b.areaLabel); put("sizeHint", b.sizeHint)
        put("createdAtEpochMs", b.createdAtEpochMs)
        put("acceptedAtEpochMs", b.acceptedAtEpochMs ?: JSONObject.NULL)
        put("arrivedAtEpochMs", b.arrivedAtEpochMs ?: JSONObject.NULL)
        put("completedAtEpochMs", b.completedAtEpochMs ?: JSONObject.NULL)
        put("evidence", b.evidence?.let { e ->
            JSONObject().apply {
                put("billFingerprint", e.billFingerprint); put("totalPriceLowInr", e.totalPriceLowInr); put("totalPriceHighInr", e.totalPriceHighInr)
                put("roomCount", e.roomCount); put("afterPhotoHashes", JSONArray(e.afterPhotoHashes)); put("photoCount", e.photoCount)
                put("generatedAtEpochMs", e.generatedAtEpochMs)
            }
        } ?: JSONObject.NULL)
        put("cancelReason", b.cancelReason ?: JSONObject.NULL)
    }

    private fun ratingToJson(r: Rating) = JSONObject().apply {
        put("bookingId", r.bookingId); put("workerId", r.workerId); put("ownerId", r.ownerId); put("stars", r.stars)
        put("tags", JSONArray(r.tags)); put("comment", r.comment); put("verified", r.verified); put("createdAtEpochMs", r.createdAtEpochMs)
    }

    private fun fromJson(text: String): Store {
        val o = JSONObject(text)
        val users = o.getJSONObject("users").let { obj -> obj.keys().asSequence().associateWith { userFromJson(obj.getJSONObject(it)) } }
        val pinHashes = o.getJSONObject("pinHashes").let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        val profiles = o.getJSONObject("profiles").let { obj -> obj.keys().asSequence().associateWith { profileFromJson(obj.getJSONObject(it)) } }
        val availability = o.getJSONObject("availability").let { obj -> obj.keys().asSequence().associateWith { availFromJson(obj.getJSONObject(it)) } }
        val bookings = o.getJSONObject("bookings").let { obj -> obj.keys().asSequence().associateWith { bookingFromJson(obj.getJSONObject(it)) } }
        val ratingsArr = o.getJSONArray("ratings")
        val ratings = (0 until ratingsArr.length()).map { ratingFromJson(ratingsArr.getJSONObject(it)) }
        return Store(
            users = users, pinHashes = pinHashes, profiles = profiles, availability = availability,
            bookings = bookings, ratings = ratings, currentUid = o.optStringOrNull("currentUid")
        )
    }

    private fun userFromJson(o: JSONObject) = ConnectUser(
        uid = o.getString("uid"), phone = o.getString("phone"), role = enumOr(o.getString("role"), Role.OWNER),
        name = o.getString("name"), createdAtEpochMs = o.getLong("createdAtEpochMs")
    )

    private fun profileFromJson(o: JSONObject): WorkerProfile {
        val langs = o.getJSONArray("languages"); val services = o.getJSONArray("services")
        return WorkerProfile(
            uid = o.getString("uid"), name = o.getString("name"),
            languages = (0 until langs.length()).map { langs.getString(it) },
            services = (0 until services.length()).map { services.getString(it) },
            rateMinInr = o.getDouble("rateMinInr"), rateMaxInr = o.getDouble("rateMaxInr"), bio = o.optString("bio", "")
        )
    }

    private fun availFromJson(o: JSONObject) = WorkerAvailability(
        open = o.getBoolean("open"), geohash = o.optStringOrNull("geohash"), areaLabel = o.optStringOrNull("areaLabel"),
        expiresAtEpochMs = if (o.isNull("expiresAtEpochMs")) null else o.getLong("expiresAtEpochMs"),
        updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L)
    )

    private fun bookingFromJson(o: JSONObject): Booking {
        val evidenceObj = o.optJSONObject("evidence")
        val evidence = evidenceObj?.let { e ->
            val hashesArr = e.getJSONArray("afterPhotoHashes")
            EvidenceSummary(
                billFingerprint = e.getString("billFingerprint"), totalPriceLowInr = e.getDouble("totalPriceLowInr"),
                totalPriceHighInr = e.getDouble("totalPriceHighInr"), roomCount = e.getInt("roomCount"),
                afterPhotoHashes = (0 until hashesArr.length()).map { hashesArr.getString(it) },
                photoCount = e.getInt("photoCount"), generatedAtEpochMs = e.getLong("generatedAtEpochMs")
            )
        }
        return Booking(
            id = o.getString("id"), ownerId = o.getString("ownerId"), workerId = o.getString("workerId"),
            workerName = o.getString("workerName"), ownerName = o.getString("ownerName"),
            status = enumOr(o.getString("status"), BookingStatus.REQUESTED), requestedForIso = o.getString("requestedForIso"),
            areaLabel = o.getString("areaLabel"), sizeHint = o.getString("sizeHint"), createdAtEpochMs = o.getLong("createdAtEpochMs"),
            acceptedAtEpochMs = o.optLongOrNull("acceptedAtEpochMs"), arrivedAtEpochMs = o.optLongOrNull("arrivedAtEpochMs"),
            completedAtEpochMs = o.optLongOrNull("completedAtEpochMs"), evidence = evidence, cancelReason = o.optStringOrNull("cancelReason")
        )
    }

    private fun ratingFromJson(o: JSONObject): Rating {
        val tagsArr = o.getJSONArray("tags")
        return Rating(
            bookingId = o.getString("bookingId"), workerId = o.getString("workerId"), ownerId = o.getString("ownerId"),
            stars = o.getInt("stars"), tags = (0 until tagsArr.length()).map { tagsArr.getString(it) },
            comment = o.optString("comment", ""), verified = o.getBoolean("verified"), createdAtEpochMs = o.getLong("createdAtEpochMs")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
