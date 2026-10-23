package com.koolikanaku.connect

import com.koolikanaku.connect.logic.Geo
import com.koolikanaku.connect.logic.NearbyRanking
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.WorkerAvailability
import com.koolikanaku.connect.model.WorkerListing
import com.koolikanaku.connect.model.WorkerProfile
import com.koolikanaku.connect.model.WorkerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRankingTest {

    private val owner = Geo.LatLng(9.9312, 76.2673)
    private val now = 1_000L

    private fun listing(uid: String, dLat: Double, stats: WorkerStats = WorkerStats(), services: List<ServiceType> = listOf(ServiceType.GENERAL),
                        open: Boolean = true, expires: Long? = null) =
        WorkerListing(
            WorkerProfile(uid, uid, "p", emptyList(), services, 200, 300),
            WorkerAvailability(open, Geo.encode(owner.lat + dLat, owner.lng), expires),
            stats
        )

    private fun good() = WorkerStats(ratingCount = 20, verifiedCount = 20, weightedStarSum = 20 * 4.8, weightSum = 20.0, completedCount = 20)

    @Test fun closeAndHighlyRatedBeatsFarAndAverage() {
        val ranked = NearbyRanking.rank(listOf(listing("far", 0.05), listing("near", 0.005, good())), owner, now)
        assertEquals(listOf("near", "far"), ranked.map { it.profile.uid })
    }

    @Test fun aMuchBetterWorkerCanOutrankACloserOne() {
        val ranked = NearbyRanking.rank(listOf(listing("close-new", 0.004), listing("a-bit-further-great", 0.03, good())), owner, now)
        assertEquals("a-bit-further-great", ranked.first().profile.uid)
    }

    @Test fun workersOutsideTheRadiusAreLeftOut() {
        val ranked = NearbyRanking.rank(listOf(listing("in", 0.01), listing("out", 0.5)), owner, now, radiusKm = 8.0)
        assertEquals(listOf("in"), ranked.map { it.profile.uid })
    }

    @Test fun closedOrExpiredWorkersNeverAppear() {
        val ranked = NearbyRanking.rank(listOf(listing("closed", 0.01, open = false), listing("expired", 0.01, expires = now), listing("ok", 0.01, expires = now + 1)), owner, now)
        assertEquals(listOf("ok"), ranked.map { it.profile.uid })
    }

    @Test fun serviceFilterKeepsMatchingAndGeneralWorkers() {
        val ranked = NearbyRanking.rank(
            listOf(listing("kitchen", 0.01, services = listOf(ServiceType.KITCHEN)), listing("bath", 0.01, services = listOf(ServiceType.BATHROOM)), listing("general", 0.01)),
            owner, now, service = ServiceType.KITCHEN
        )
        assertEquals(setOf("kitchen", "general"), ranked.map { it.profile.uid }.toSet())
    }

    @Test fun cardsCarryADistanceBandNotAnExactDistance() {
        val card = NearbyRanking.rank(listOf(listing("w", 0.02)), owner, now).single()
        assertTrue(card.distanceBand == "under 1 km" || card.distanceBand.startsWith("about "))
        assertTrue(card.distanceKm > 0)
    }
}
