package com.koolikanaku.connect.data

import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.Role
import com.koolikanaku.connect.model.WorkerCard
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Backend-agnostic contracts for the connect layer (extra_feature.md Section 5.3). [DemoConnectRepositories]
 * is the in-memory/on-device implementation used today; a real backend (Supabase, per the spec) can
 * implement the same interfaces later without touching any calling code.
 */
interface AuthRepository {
    suspend fun signUp(phone: String, pin: String, role: Role, name: String): Result<ConnectUser>
    suspend fun signIn(phone: String, pin: String): Result<ConnectUser>
    fun currentUser(): ConnectUser?
    fun signOut()
    /** True if [phone] already has an account, so the sign-up form can point the user to Sign in instead. */
    suspend fun phoneExists(phone: String): Boolean
}

interface WorkerRepository {
    suspend fun saveProfile(profile: WorkerProfile)
    suspend fun getProfile(uid: String): WorkerProfile?
    suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, areaLabel: String?, expiresAtEpochMs: Long?)
    suspend fun isOpen(uid: String): Boolean
    /** Workers currently open for work. [geohashPrefixes] narrows the search; the demo backend ignores it and just filters by [open]. */
    fun nearbyOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerCard>>
    suspend fun cardFor(uid: String): WorkerCard?
}

interface BookingRepository {
    suspend fun request(ownerId: String, workerId: String, whenIso: String, areaLabel: String, sizeHint: String): Booking
    fun observeForUser(uid: String): Flow<List<Booking>>
    fun observe(bookingId: String): Flow<Booking?>
    suspend fun respond(bookingId: String, accept: Boolean)
    suspend fun setStatus(bookingId: String, status: BookingStatus)
    suspend fun complete(bookingId: String, summary: EvidenceSummary)
    suspend fun cancel(bookingId: String, reason: String)
    /** The counterpart's phone number for the Call button, once the booking is accepted (extra_feature.md Section 6.1). */
    suspend fun counterpartPhone(bookingId: String, requesterId: String): String?
}

interface RatingRepository {
    suspend fun rate(bookingId: String, stars: Int, tags: List<String>, comment: String)
    fun ratingsFor(workerId: String): Flow<List<Rating>>
}
