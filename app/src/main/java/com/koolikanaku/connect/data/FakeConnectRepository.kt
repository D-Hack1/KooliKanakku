package com.koolikanaku.connect.data

import com.koolikanaku.connect.logic.BookingStateMachine
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.logic.Geo
import com.koolikanaku.connect.logic.TrustScore
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerAvailability
import com.koolikanaku.connect.model.WorkerListing
import com.koolikanaku.connect.model.WorkerProfile
import com.koolikanaku.connect.model.WorkerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory backend for tests and the offline demo mode. It enforces the same rules a real
 * server must: one rating per booking, phones revealed only after acceptance, role checks,
 * and no double-booking a busy worker. Sample workers are always flagged [WorkerProfile.isDemo].
 */
class FakeConnectRepository(
    private val clock: () -> Long = System::currentTimeMillis
) : AuthRepository, WorkerRepository, BookingRepository, RatingRepository {

    private data class State(
        val users: Map<String, ConnectUser> = emptyMap(),
        val profiles: Map<String, WorkerProfile> = emptyMap(),
        val availability: Map<String, WorkerAvailability> = emptyMap(),
        val stats: Map<String, WorkerStats> = emptyMap(),
        val bookings: Map<String, Booking> = emptyMap(),
        val ratings: List<Rating> = emptyList(),
        val currentUid: String? = null
    )

    private val state = MutableStateFlow(State())

    companion object {
        /** The demo sign-in code; a real backend sends an SMS. */
        const val DEMO_OTP = "123456"
    }

    // ---------- auth ----------

    override suspend fun requestCode(phone: String): Result<Unit> =
        if (normalize(phone).length < 10) Result.failure(ConnectException("Enter a valid phone number")) else Result.success(Unit)

    override suspend fun verifyCode(phone: String, code: String, name: String, role: UserRole): Result<ConnectUser> {
        if (code != DEMO_OTP) return Result.failure(ConnectException("Wrong code"))
        val normalized = normalize(phone)
        if (normalized.length < 10) return Result.failure(ConnectException("Enter a valid phone number"))
        var outcome: Result<ConnectUser>? = null
        state.update { s ->
            val existing = s.users.values.firstOrNull { it.phone == normalized }
            if (existing != null) {
                outcome = if (existing.role != role) Result.failure(ConnectException("This number is registered as a ${existing.role.name.lowercase()}"))
                else Result.success(existing)
                if (existing.role == role) s.copy(currentUid = existing.uid) else s
            } else {
                if (name.isBlank()) { outcome = Result.failure(ConnectException("Enter your name")); return@update s }
                val user = ConnectUser(UUID.randomUUID().toString(), role, name.trim(), normalized)
                outcome = Result.success(user)
                s.copy(users = s.users + (user.uid to user), currentUid = user.uid)
            }
        }
        return outcome!!
    }

    override fun currentUser(): ConnectUser? = state.value.currentUid?.let { state.value.users[it] }
    override fun signOut() { state.update { it.copy(currentUid = null) } }

    private fun normalize(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    // ---------- workers ----------

    override suspend fun saveProfile(profile: WorkerProfile): Result<Unit> {
        val user = state.value.users[profile.uid]
        if (user == null || user.role != UserRole.WORKER) return Result.failure(ConnectException("Only a worker account can have a profile"))
        if (profile.rateMinInr <= 0 || profile.rateMaxInr < profile.rateMinInr) return Result.failure(ConnectException("Check the rate range"))
        state.update { it.copy(profiles = it.profiles + (profile.uid to profile), stats = it.stats.withDefault(profile.uid)) }
        return Result.success(Unit)
    }

    private fun Map<String, WorkerStats>.withDefault(uid: String) = if (containsKey(uid)) this else this + (uid to WorkerStats())

    override suspend fun profileOf(uid: String): WorkerProfile? = state.value.profiles[uid]

    override suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, expiresAtMs: Long?): Result<Unit> {
        if (!state.value.profiles.containsKey(uid)) return Result.failure(ConnectException("Create your profile first"))
        if (open && geohash == null) return Result.failure(ConnectException("Location is needed to be visible nearby"))
        if (geohash != null && geohash.length > Geo.STORED_PRECISION) return Result.failure(ConnectException("Location is too precise"))
        state.update { it.copy(availability = it.availability + (uid to WorkerAvailability(open, if (open) geohash else null, if (open) expiresAtMs else null))) }
        return Result.success(Unit)
    }

    override fun observeOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerListing>> = state.map { s ->
        val now = clock()
        s.availability.entries.mapNotNull { (uid, av) ->
            val hash = av.geohash ?: return@mapNotNull null
            val profile = s.profiles[uid] ?: return@mapNotNull null
            if (!av.isVisibleAt(now) || geohashPrefixes.none { hash.startsWith(it) }) null
            else WorkerListing(profile, av, s.stats[uid] ?: WorkerStats())
        }
    }

    // ---------- bookings ----------

    private val activeStatuses = setOf(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ARRIVED, BookingStatus.IN_PROGRESS)

    override suspend fun request(ownerId: String, workerId: String, requestedForIso: String, areaLabel: String, sizeHint: String): Result<Booking> {
        var outcome: Result<Booking>? = null
        state.update { s ->
            val owner = s.users[ownerId]
            val worker = s.users[workerId]
            val av = s.availability[workerId]
            when {
                owner == null || owner.role != UserRole.OWNER -> { outcome = Result.failure(ConnectException("Only an owner account can book")); s }
                worker == null || s.profiles[workerId] == null -> { outcome = Result.failure(ConnectException("Worker not found")); s }
                av == null || !av.isVisibleAt(clock()) -> { outcome = Result.failure(ConnectException("This worker is no longer available")); s }
                s.bookings.values.any { it.ownerId == ownerId && it.workerId == workerId && it.status in activeStatuses } ->
                    { outcome = Result.failure(ConnectException("You already have a booking with this worker")); s }
                else -> {
                    val now = clock()
                    val booking = Booking(
                        id = UUID.randomUUID().toString(), ownerId = ownerId, workerId = workerId,
                        ownerName = owner.name, workerName = worker.name, status = BookingStatus.REQUESTED,
                        requestedForIso = requestedForIso, areaLabel = areaLabel, sizeHint = sizeHint,
                        createdAtMs = now, updatedAtMs = now
                    )
                    outcome = Result.success(booking)
                    s.copy(bookings = s.bookings + (booking.id to booking))
                }
            }
        }
        return outcome!!
    }

    override fun observe(bookingId: String): Flow<Booking?> = state.map { it.bookings[bookingId] }

    override fun observeFor(uid: String): Flow<List<Booking>> = state.map { s ->
        s.bookings.values.filter { it.ownerId == uid || it.workerId == uid }.sortedByDescending { it.createdAtMs }
    }

    /** Marks requests nobody answered in time as expired. A real backend runs this on a timer. */
    fun expireStaleRequests() {
        val now = clock()
        state.update { s ->
            s.copy(bookings = s.bookings.mapValues { (_, b) ->
                if (BookingStateMachine.isRequestExpired(b.status, b.createdAtMs, now)) b.copy(status = BookingStatus.EXPIRED, updatedAtMs = now) else b
            })
        }
    }

    override suspend fun perform(bookingId: String, uid: String, action: Action, evidence: EvidenceSummary?, jobId: String?): Result<Booking> {
        expireStaleRequests()
        var outcome: Result<Booking>? = null
        state.update { s ->
            val booking = s.bookings[bookingId]
            val user = s.users[uid]
            if (booking == null) { outcome = Result.failure(ConnectException("Booking not found")); return@update s }
            if (user == null || (uid != booking.ownerId && uid != booking.workerId)) {
                outcome = Result.failure(ConnectException("Not your booking")); return@update s
            }
            if (action == Action.RATE) { outcome = Result.failure(ConnectException("Use rate() to rate")); return@update s }
            val role = if (uid == booking.ownerId) UserRole.OWNER else UserRole.WORKER
            val next = BookingStateMachine.next(booking.status, action, BookingStateMachine.actorFor(role))
            if (next == null) { outcome = Result.failure(ConnectException("${action.name.lowercase()} is not allowed while ${booking.status.name.lowercase()}")); return@update s }
            if (action == Action.ACCEPT) {
                val busy = s.bookings.values.any { it.workerId == booking.workerId && it.id != booking.id &&
                    (it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.ARRIVED || it.status == BookingStatus.IN_PROGRESS) }
                if (busy) { outcome = Result.failure(ConnectException("Finish your current job before accepting another")); return@update s }
            }
            var updated = booking.copy(status = next, updatedAtMs = clock())
            if (next.revealsPhones) {
                updated = updated.copy(ownerPhone = s.users[booking.ownerId]?.phone, workerPhone = s.users[booking.workerId]?.phone)
            }
            if (next == BookingStatus.CANCELLED) updated = updated.copy(cancelledBy = role)
            if (action == Action.START && jobId != null) updated = updated.copy(jobId = jobId)
            if (action == Action.COMPLETE) updated = updated.copy(evidence = evidence)
            var stats = s.stats
            if (next == BookingStatus.CANCELLED && role == UserRole.WORKER && booking.status == BookingStatus.ACCEPTED) {
                stats = stats + (booking.workerId to (stats[booking.workerId] ?: WorkerStats()).let { it.copy(cancelCount = it.cancelCount + 1) })
            }
            if (next == BookingStatus.COMPLETED) {
                stats = stats + (booking.workerId to (stats[booking.workerId] ?: WorkerStats()).let { it.copy(completedCount = it.completedCount + 1) })
            }
            outcome = Result.success(updated)
            s.copy(bookings = s.bookings + (bookingId to updated), stats = stats)
        }
        return outcome!!
    }

    // ---------- ratings ----------

    override suspend fun rate(bookingId: String, ownerUid: String, stars: Int, tags: List<String>, comment: String): Result<Rating> {
        if (stars !in 1..5) return Result.failure(ConnectException("Stars must be 1 to 5"))
        var outcome: Result<Rating>? = null
        state.update { s ->
            val booking = s.bookings[bookingId]
            when {
                booking == null -> { outcome = Result.failure(ConnectException("Booking not found")); s }
                booking.ownerId != ownerUid -> { outcome = Result.failure(ConnectException("Only the owner can rate this worker")); s }
                s.ratings.any { it.bookingId == bookingId } -> { outcome = Result.failure(ConnectException("Already rated")); s }
                BookingStateMachine.next(booking.status, Action.RATE, BookingStateMachine.Actor.OWNER) == null ->
                    { outcome = Result.failure(ConnectException("You can rate after the job is completed")); s }
                else -> {
                    val verified = booking.evidence?.isVerified == true
                    val rating = Rating(bookingId, booking.workerId, ownerUid, stars, tags, comment.trim(), verified, clock())
                    val stats = TrustScore.withRating(s.stats[booking.workerId] ?: WorkerStats(), stars, verified)
                    outcome = Result.success(rating)
                    s.copy(
                        ratings = s.ratings + rating,
                        stats = s.stats + (booking.workerId to stats),
                        bookings = s.bookings + (bookingId to booking.copy(status = BookingStatus.RATED, updatedAtMs = clock()))
                    )
                }
            }
        }
        return outcome!!
    }

    override fun ratingsFor(workerId: String): Flow<List<Rating>> = state.map { s -> s.ratings.filter { it.workerId == workerId }.sortedByDescending { it.createdAtMs } }

    // ---------- demo data ----------

    /** Adds clearly flagged sample workers around [center] so the list is not empty in a demo. */
    fun seedDemoWorkers(center: Geo.LatLng) {
        data class Sample(val name: String, val dLat: Double, val dLng: Double, val services: List<ServiceType>, val min: Int, val max: Int,
                          val ratings: Int, val avg: Double, val verified: Int, val cancels: Int, val completed: Int)
        val samples = listOf(
            Sample("Demo: Lakshmi", 0.004, 0.003, listOf(ServiceType.KITCHEN, ServiceType.BATHROOM), 220, 300, 24, 4.8, 19, 0, 30),
            Sample("Demo: Rajan", -0.012, 0.006, listOf(ServiceType.GENERAL), 200, 260, 9, 4.4, 5, 1, 12),
            Sample("Demo: Meena", 0.020, -0.010, listOf(ServiceType.BEDROOM, ServiceType.LIVING_ROOM), 240, 320, 2, 5.0, 0, 0, 2),
            Sample("Demo: Suresh", -0.030, -0.020, listOf(ServiceType.GENERAL, ServiceType.KITCHEN), 180, 240, 41, 4.1, 12, 4, 52)
        )
        val now = clock()
        state.update { s ->
            var next = s
            samples.forEachIndexed { i, sample ->
                val uid = "demo-worker-$i"
                val user = ConnectUser(uid, UserRole.WORKER, sample.name, "90000000%02d".format(i))
                val profile = WorkerProfile(uid, sample.name, user.phone, listOf("Malayalam", "English"), sample.services, sample.min, sample.max, "Sample worker for demos", isDemo = true)
                val verifiedWeight = sample.verified * TrustScore.VERIFIED_WEIGHT
                val unverifiedWeight = (sample.ratings - sample.verified) * TrustScore.UNVERIFIED_WEIGHT
                val stats = WorkerStats(
                    ratingCount = sample.ratings, verifiedCount = sample.verified,
                    weightedStarSum = (verifiedWeight + unverifiedWeight) * sample.avg, weightSum = verifiedWeight + unverifiedWeight,
                    completedCount = sample.completed, cancelCount = sample.cancels
                )
                next = next.copy(
                    users = next.users + (uid to user),
                    profiles = next.profiles + (uid to profile),
                    stats = next.stats + (uid to stats),
                    availability = next.availability + (uid to WorkerAvailability(true, Geo.encode(center.lat + sample.dLat, center.lng + sample.dLng), now + 8 * 3_600_000L))
                )
            }
            next
        }
    }
}
