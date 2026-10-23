package com.koolikanaku.connect

import com.koolikanaku.connect.logic.TrustScore
import com.koolikanaku.connect.model.WorkerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustScoreTest {

    private fun rated(count: Int, stars: Int, verified: Boolean, base: WorkerStats = WorkerStats()): WorkerStats =
        (1..count).fold(base) { s, _ -> TrustScore.withRating(s, stars, verified) }

    @Test fun anUnratedWorkerSitsAtTheNeutralPrior() {
        assertEquals(3.5, TrustScore.score(WorkerStats()), 1e-9)
    }

    @Test fun oneLuckyFiveStarDoesNotBeatALongVerifiedRecord() {
        val lucky = rated(1, 5, verified = false)
        val solid = rated(20, 4, verified = true)
        assertTrue("lucky=${TrustScore.score(lucky)} solid=${TrustScore.score(solid)}", TrustScore.score(solid) > TrustScore.score(lucky))
    }

    @Test fun aVerifiedRatingCountsMoreThanAnUnverifiedOne() {
        assertTrue(TrustScore.score(rated(5, 5, verified = true)) > TrustScore.score(rated(5, 5, verified = false)))
    }

    @Test fun badRatingsPullTheScoreBelowNeutral() {
        assertTrue(TrustScore.score(rated(10, 1, verified = true)) < 3.5)
    }

    @Test fun cancellationsLowerTheScore() {
        val base = rated(10, 5, verified = true).copy(completedCount = 10)
        val flaky = base.copy(cancelCount = 10)
        assertTrue(TrustScore.score(flaky) < TrustScore.score(base))
        assertTrue(TrustScore.score(flaky) >= 0.0)
    }

    @Test fun addingARatingUpdatesCountsAndWeights() {
        val s = TrustScore.withRating(TrustScore.withRating(WorkerStats(), 5, true), 3, false)
        assertEquals(2, s.ratingCount)
        assertEquals(1, s.verifiedCount)
        assertEquals(1.5, s.weightSum, 1e-9)
        assertEquals(5 * 1.0 + 3 * 0.5, s.weightedStarSum, 1e-9)
        assertEquals((5.0 + 1.5) / 1.5, s.averageStars!!, 1e-9)
    }

    @Test fun scoreStaysWithinOneToFiveStars() {
        assertTrue(TrustScore.score(rated(50, 5, verified = true)) <= 5.0)
        assertTrue(TrustScore.score(rated(50, 1, verified = true).copy(cancelCount = 50)) >= 0.0)
    }
}
