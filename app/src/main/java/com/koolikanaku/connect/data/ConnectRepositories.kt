package com.koolikanaku.connect.data

import com.koolikanaku.connect.logic.BookingStateMachine
import com.koolikanaku.connect.model.Booking
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.Rating
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerAvailability
import com.koolikanaku.connect.model.WorkerListing
import com.koolikanaku.connect.model.WorkerProfile
import com.koolikanaku.connect.logic.Geo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Backend-neutral contracts for the connected layer. The app talks only to these, so the in-memory
 * fake (demo and tests) and a real server can be swapped without touching any screen.
 */
interface AuthRepository {
    /**
     * Signs in, or creates the account the first time this phone number is used. [pin] is a 6-digit PIN
     * (no SMS is sent; a real SMS code is the upgrade path). The role cannot be changed later.
     */
    suspend fun signIn(phone: String, pin: String, name: String, role: UserRole): Result<ConnectUser>
    fun currentUser(): ConnectUser?
    fun signOut()
}

interface WorkerRepository {
    suspend fun saveProfile(profile: WorkerProfile): Result<Unit>
    suspend fun profileOf(uid: String): WorkerProfile?
    suspend fun availabilityOf(uid: String): WorkerAvailability?
    /** [geohash] is the coarse cell only; exact coordinates never reach the backend. */
    suspend fun setAvailability(uid: String, open: Boolean, geohash: String?, expiresAtMs: Long?): Result<Unit>
    fun observeOpenWorkers(geohashPrefixes: List<String>): Flow<List<WorkerListing>>
}

interface BookingRepository {
    suspend fun request(ownerId: String, workerId: String, requestedForIso: String, areaLabel: String, sizeHint: String): Result<Booking>
    fun observe(bookingId: String): Flow<Booking?>
    /** Every booking the user is part of, newest first. */
    fun observeFor(uid: String): Flow<List<Booking>>
    suspend fun perform(
        bookingId: String,
        uid: String,
        action: BookingStateMachine.Action,
        evidence: EvidenceSummary? = null,
        jobId: String? = null
    ): Result<Booking>
}

interface RatingRepository {
    suspend fun rate(bookingId: String, ownerUid: String, stars: Int, tags: List<String>, comment: String): Result<Rating>
    fun ratingsFor(workerId: String): Flow<List<Rating>>
}

class ConnectException(message: String) : Exception(message)

/** Everything the screens need from a backend, plus a way to tell the person what is going on. */
interface ConnectBackend : AuthRepository, WorkerRepository, BookingRepository, RatingRepository {
    /** True for the in-memory demo backend: sample workers, nothing leaves the phone. */
    val isDemo: Boolean
    /** Last network or server problem seen while polling, or null when things are fine. */
    val lastError: StateFlow<String?>
    /** Lets the demo backend place its sample workers near the owner. A real backend ignores it. */
    fun prepareForLocation(center: Geo.LatLng) {}
}

object Pin {
    const val LENGTH = 6
    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it.isDigit() }
}
