package com.koolikanaku.connect.model

enum class UserRole { OWNER, WORKER }

enum class ServiceType { KITCHEN, BATHROOM, BEDROOM, LIVING_ROOM, GENERAL }

enum class BookingStatus {
    REQUESTED, ACCEPTED, DECLINED, EXPIRED, CANCELLED, ARRIVED, IN_PROGRESS, COMPLETED, RATED;

    /** No further transition is possible. */
    val isFinal: Boolean get() = this == DECLINED || this == EXPIRED || this == CANCELLED || this == RATED

    /** Phone numbers are shared only once a worker has said yes. */
    val revealsPhones: Boolean get() = this == ACCEPTED || this == ARRIVED || this == IN_PROGRESS || this == COMPLETED || this == RATED
}

data class ConnectUser(
    val uid: String,
    val role: UserRole,
    val name: String,
    val phone: String
)

data class WorkerProfile(
    val uid: String,
    val name: String,
    val phone: String,
    val languages: List<String>,
    val services: List<ServiceType>,
    val rateMinInr: Int,
    val rateMaxInr: Int,
    val bio: String = "",
    /** Sample workers for demos; the UI must always label them. */
    val isDemo: Boolean = false
)

/** Only a coarse geohash is stored, never exact coordinates. */
data class WorkerAvailability(
    val open: Boolean,
    val geohash: String?,
    val expiresAtMs: Long?
) {
    fun isVisibleAt(nowMs: Long): Boolean = open && geohash != null && (expiresAtMs == null || nowMs < expiresAtMs)
}

/** Running totals kept on the worker so the trust score never needs every rating. */
data class WorkerStats(
    val ratingCount: Int = 0,
    val verifiedCount: Int = 0,
    val weightedStarSum: Double = 0.0,
    val weightSum: Double = 0.0,
    val completedCount: Int = 0,
    val cancelCount: Int = 0
) {
    /** Plain average of stars, weighted the same way as the trust score. Null with no ratings. */
    val averageStars: Double? get() = if (weightSum > 0) weightedStarSum / weightSum else null
}

/** What an owner sees about one nearby worker. */
data class WorkerListing(
    val profile: WorkerProfile,
    val availability: WorkerAvailability,
    val stats: WorkerStats
)

data class WorkerCard(
    val profile: WorkerProfile,
    val stats: WorkerStats,
    val trustScore: Double,
    val distanceKm: Double,
    val distanceBand: String
)

/** Hashes and totals only. No photos, tasks or addresses ever leave the phone. */
data class EvidenceSummary(
    val billFingerprint: String,
    val totalPriceLowInr: Int,
    val totalPriceHighInr: Int,
    val roomCount: Int,
    val photoCount: Int,
    val afterPhotoHashes: List<String>,
    val generatedAtIso: String
) {
    /** A rating counts as verified only when the job was properly documented. */
    val isVerified: Boolean get() = billFingerprint.length == 64 && afterPhotoHashes.isNotEmpty()
}

data class Booking(
    val id: String,
    val ownerId: String,
    val workerId: String,
    val ownerName: String,
    val workerName: String,
    val status: BookingStatus,
    val requestedForIso: String,
    val areaLabel: String,
    val sizeHint: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** Filled only once the booking is accepted. */
    val ownerPhone: String? = null,
    val workerPhone: String? = null,
    val cancelledBy: UserRole? = null,
    val evidence: EvidenceSummary? = null,
    /** The offline job started for this booking, if any. */
    val jobId: String? = null
)

data class Rating(
    val bookingId: String,
    val workerId: String,
    val ownerId: String,
    val stars: Int,
    val tags: List<String>,
    val comment: String,
    val verified: Boolean,
    val createdAtMs: Long
)
