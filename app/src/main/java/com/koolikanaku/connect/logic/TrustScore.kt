package com.koolikanaku.connect.logic

import com.koolikanaku.connect.model.WorkerStats

/**
 * A smoothed, evidence-weighted rating so one lucky five-star cannot beat a long record,
 * and a rating from a documented job counts more than one without photos.
 */
object TrustScore {

    const val VERIFIED_WEIGHT = 1.0
    const val UNVERIFIED_WEIGHT = 0.5
    const val PRIOR_STARS = 3.5
    const val PRIOR_WEIGHT = 3.0
    /** Stars lost when every booking ends in a cancellation. */
    const val MAX_RELIABILITY_PENALTY = 1.0

    fun weightFor(verified: Boolean): Double = if (verified) VERIFIED_WEIGHT else UNVERIFIED_WEIGHT

    fun score(stats: WorkerStats): Double {
        val base = (stats.weightedStarSum + PRIOR_WEIGHT * PRIOR_STARS) / (stats.weightSum + PRIOR_WEIGHT)
        val attempts = stats.completedCount + stats.cancelCount
        val cancelRate = if (attempts == 0) 0.0 else stats.cancelCount.toDouble() / attempts
        return (base - MAX_RELIABILITY_PENALTY * cancelRate).coerceIn(0.0, 5.0)
    }

    /** Adds one rating to the running totals. */
    fun withRating(stats: WorkerStats, stars: Int, verified: Boolean): WorkerStats {
        val weight = weightFor(verified)
        return stats.copy(
            ratingCount = stats.ratingCount + 1,
            verifiedCount = stats.verifiedCount + if (verified) 1 else 0,
            weightedStarSum = stats.weightedStarSum + weight * stars,
            weightSum = stats.weightSum + weight
        )
    }
}
