package com.koolikanaku.connect.model

/**
 * The online "Find a worker" layer's data model (extra_feature.md). Deliberately separate from
 * com.koolikanaku.domain: nothing here is required to produce an estimate, and nothing from here
 * is allowed to be imported by ai/domain/capture/export (enforced by OfflineCoreGuardTest).
 */
enum class Role { OWNER, WORKER }

enum class BookingStatus {
    REQUESTED, ACCEPTED, DECLINED, EXPIRED, CANCELLED, ARRIVED, IN_PROGRESS, COMPLETED, RATED
}

data class ConnectUser(
    val uid: String,
    val phone: String,
    val role: Role,
    val name: String,
    val createdAtEpochMs: Long
)

data class WorkerProfile(
    val uid: String,
    val name: String,
    val languages: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val rateMinInr: Double = 0.0,
    val rateMaxInr: Double = 0.0,
    val bio: String = ""
)

data class WorkerAvailability(
    val open: Boolean = false,
    val geohash: String? = null,
    val areaLabel: String? = null,
    val expiresAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0L
)

/** What an owner sees in the nearby list and on the worker-detail screen. Never an exact location. */
data class WorkerCard(
    val uid: String,
    val name: String,
    val languages: List<String>,
    val services: List<String>,
    val rateMinInr: Double,
    val rateMaxInr: Double,
    val bio: String,
    val ratingAvg: Double,
    val ratingCount: Int,
    val verifiedCount: Int,
    val distanceBandLabel: String,
    val cancelCount: Int = 0
)

data class EvidenceSummary(
    val billFingerprint: String,
    val totalPriceLowInr: Double,
    val totalPriceHighInr: Double,
    val roomCount: Int,
    val afterPhotoHashes: List<String>,
    val photoCount: Int,
    val generatedAtEpochMs: Long
)

data class Booking(
    val id: String,
    val ownerId: String,
    val workerId: String,
    val workerName: String,
    val ownerName: String,
    val status: BookingStatus,
    val requestedForIso: String,
    val areaLabel: String,
    val sizeHint: String,
    val createdAtEpochMs: Long,
    val acceptedAtEpochMs: Long? = null,
    val arrivedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val evidence: EvidenceSummary? = null,
    val cancelReason: String? = null
)

data class Rating(
    val bookingId: String,
    val workerId: String,
    val ownerId: String,
    val stars: Int,
    val tags: List<String>,
    val comment: String,
    val verified: Boolean,
    val createdAtEpochMs: Long
)

/** Request-expiry and smoothed-trust-score constants (extra_feature.md Section 4.4, 7.3). */
object ConnectConstants {
    const val REQUEST_EXPIRY_MS: Long = 10 * 60 * 1000L
    const val AVAILABILITY_WINDOW_MS: Long = 4 * 60 * 60 * 1000L
    const val VERIFIED_RATING_WEIGHT: Double = 1.0
    const val UNVERIFIED_RATING_WEIGHT: Double = 0.5
    const val TRUST_PRIOR: Double = 3.5
    const val TRUST_PRIOR_WEIGHT: Double = 3.0
    val RATING_TAGS = listOf("On time", "Careful", "Fair price", "Friendly", "Thorough")
}
