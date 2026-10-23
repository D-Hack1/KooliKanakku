package com.koolikanaku.connect

import com.koolikanaku.connect.data.FakeConnectRepository
import com.koolikanaku.connect.logic.BookingStateMachine
import com.koolikanaku.connect.logic.BookingStateMachine.Action
import com.koolikanaku.connect.logic.Geo
import com.koolikanaku.connect.model.BookingStatus
import com.koolikanaku.connect.model.ConnectUser
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.connect.model.ServiceType
import com.koolikanaku.connect.model.UserRole
import com.koolikanaku.connect.model.WorkerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeConnectRepositoryTest {

    private var now = 1_000_000L
    private val repo = FakeConnectRepository(clock = { now })
    private val kochi = Geo.LatLng(9.9312, 76.2673)
    private val cell = Geo.encode(kochi.lat, kochi.lng)

    private fun signUp(phone: String, name: String, role: UserRole): ConnectUser = runBlocking {
        repo.verifyCode(phone, FakeConnectRepository.DEMO_OTP, name, role).getOrThrow()
    }

    private fun worker(phone: String = "9000000001", name: String = "Asha"): ConnectUser {
        val u = signUp(phone, name, UserRole.WORKER)
        runBlocking {
            repo.saveProfile(WorkerProfile(u.uid, name, u.phone, listOf("Malayalam"), listOf(ServiceType.GENERAL), 200, 300)).getOrThrow()
            repo.setAvailability(u.uid, true, cell, now + 3_600_000).getOrThrow()
        }
        return u
    }

    private fun verifiedEvidence() = EvidenceSummary("a".repeat(64), 400, 520, 1, 4, listOf("b".repeat(64)), "2026-10-24T05:00:00Z")

    @Test fun signInNeedsTheRightCodeAndAValidNumber() = runBlocking {
        assertTrue(repo.verifyCode("9000000001", "000000", "A", UserRole.OWNER).isFailure)
        assertTrue(repo.verifyCode("123", FakeConnectRepository.DEMO_OTP, "A", UserRole.OWNER).isFailure)
        assertTrue(repo.requestCode("12").isFailure)
        assertTrue(repo.requestCode("9000000001").isSuccess)
    }

    @Test fun anExistingNumberKeepsItsRoleAndSignsBackIn() {
        val first = signUp("9000000001", "Asha", UserRole.WORKER)
        repo.signOut()
        assertNull(repo.currentUser())
        val again = signUp("+91 90000 00001", "ignored", UserRole.WORKER)
        assertEquals(first.uid, again.uid)
        assertEquals(first.uid, repo.currentUser()?.uid)
        val wrongRole = runBlocking { repo.verifyCode("9000000001", FakeConnectRepository.DEMO_OTP, "Asha", UserRole.OWNER) }
        assertTrue(wrongRole.isFailure)
    }

    @Test fun onlyAWorkerAccountHasAProfileAndGoesOpen() = runBlocking {
        val owner = signUp("9000000002", "Omar", UserRole.OWNER)
        val profile = WorkerProfile(owner.uid, "Omar", owner.phone, emptyList(), emptyList(), 200, 300)
        assertTrue(repo.saveProfile(profile).isFailure)
        val w = signUp("9000000003", "Asha", UserRole.WORKER)
        assertTrue("bad rate range", repo.saveProfile(profile.copy(uid = w.uid, rateMinInr = 300, rateMaxInr = 200)).isFailure)
        assertTrue("open before profile exists", repo.setAvailability(signUp("9000000004", "Bina", UserRole.WORKER).uid, true, cell, null).isFailure)
    }

    @Test fun goingOpenNeedsALocationAndNeverStoresAnExactOne() = runBlocking {
        val w = worker()
        assertTrue(repo.setAvailability(w.uid, true, null, null).isFailure)
        assertTrue("exact-looking geohash is rejected", repo.setAvailability(w.uid, true, Geo.encode(9.93121, 76.26731, 9), null).isFailure)
        assertTrue(repo.setAvailability(w.uid, true, cell, now + 1000).isSuccess)
    }

    @Test fun ownersOnlySeeOpenUnexpiredWorkersInTheSearchCells() = runBlocking {
        val w = worker()
        val prefixes = Geo.searchPrefixes(kochi.lat, kochi.lng)
        assertEquals(listOf(w.uid), repo.observeOpenWorkers(prefixes).first().map { it.profile.uid })
        assertTrue("far away search", repo.observeOpenWorkers(Geo.searchPrefixes(28.6, 77.2)).first().isEmpty())
        now += 3_600_001                       // availability has expired
        assertTrue(repo.observeOpenWorkers(prefixes).first().isEmpty())
        repo.setAvailability(w.uid, true, cell, now + 1000)
        repo.setAvailability(w.uid, false, null, null)
        assertTrue("switched off", repo.observeOpenWorkers(prefixes).first().isEmpty())
    }

    @Test fun fullBookingFlowRevealsPhonesOnlyAfterAcceptanceAndEndsInAVerifiedRating() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val booking = repo.request(o.uid, w.uid, "2026-10-25T10:00:00Z", "Edappally", "2 bedroom flat").getOrThrow()
        assertEquals(BookingStatus.REQUESTED, booking.status)
        assertNull("no phones before acceptance", booking.workerPhone)
        assertNull(booking.ownerPhone)

        val accepted = repo.perform(booking.id, w.uid, Action.ACCEPT).getOrThrow()
        assertEquals(BookingStatus.ACCEPTED, accepted.status)
        assertEquals(w.phone, accepted.workerPhone)
        assertEquals(o.phone, accepted.ownerPhone)

        assertEquals(BookingStatus.ARRIVED, repo.perform(booking.id, o.uid, Action.ARRIVE).getOrThrow().status)
        assertEquals(BookingStatus.IN_PROGRESS, repo.perform(booking.id, w.uid, Action.START, jobId = "job-1").getOrThrow().also { assertEquals("job-1", it.jobId) }.status)
        val done = repo.perform(booking.id, w.uid, Action.COMPLETE, evidence = verifiedEvidence()).getOrThrow()
        assertEquals(BookingStatus.COMPLETED, done.status)

        val rating = repo.rate(booking.id, o.uid, 5, listOf("on time"), "Great").getOrThrow()
        assertTrue("documented job gives a verified rating", rating.verified)
        assertEquals(BookingStatus.RATED, repo.observe(booking.id).first()!!.status)

        val listing = repo.observeOpenWorkers(Geo.searchPrefixes(kochi.lat, kochi.lng)).first().single()
        assertEquals(1, listing.stats.ratingCount)
        assertEquals(1, listing.stats.verifiedCount)
        assertEquals(1, listing.stats.completedCount)
    }

    @Test fun aJobWithoutEvidenceGivesAnUnverifiedRatingThatCountsLess() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val b = repo.request(o.uid, w.uid, "now", "Kakkanad", "1 room").getOrThrow()
        repo.perform(b.id, w.uid, Action.ACCEPT)
        repo.perform(b.id, w.uid, Action.ARRIVE)
        repo.perform(b.id, o.uid, Action.START)
        repo.perform(b.id, o.uid, Action.COMPLETE)               // no evidence summary
        val rating = repo.rate(b.id, o.uid, 5, emptyList(), "").getOrThrow()
        assertFalse(rating.verified)
        // evidence without an after photo is not "verified" either
        assertFalse(verifiedEvidence().copy(afterPhotoHashes = emptyList()).isVerified)
        assertFalse(verifiedEvidence().copy(billFingerprint = "short").isVerified)
    }

    @Test fun ratingsAreOnePerBookingOwnerOnlyAndOnlyAfterCompletion() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val other = signUp("9000000010", "Eve", UserRole.OWNER)
        val b = repo.request(o.uid, w.uid, "now", "Kakkanad", "1 room").getOrThrow()
        assertTrue("too early", repo.rate(b.id, o.uid, 5, emptyList(), "").isFailure)
        repo.perform(b.id, w.uid, Action.ACCEPT); repo.perform(b.id, w.uid, Action.ARRIVE)
        repo.perform(b.id, w.uid, Action.START); repo.perform(b.id, w.uid, Action.COMPLETE)
        assertTrue("stranger", repo.rate(b.id, other.uid, 5, emptyList(), "").isFailure)
        assertTrue("worker rating themselves", repo.rate(b.id, w.uid, 5, emptyList(), "").isFailure)
        assertTrue("six stars", repo.rate(b.id, o.uid, 6, emptyList(), "").isFailure)
        assertTrue(repo.rate(b.id, o.uid, 4, emptyList(), "").isSuccess)
        assertTrue("twice", repo.rate(b.id, o.uid, 5, emptyList(), "").isFailure)
        assertEquals(1, repo.ratingsFor(w.uid).first().size)
    }

    @Test fun strangersAndWrongRolesCannotDriveABooking() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val stranger = signUp("9000000011", "Eve", UserRole.OWNER)
        val b = repo.request(o.uid, w.uid, "now", "Kakkanad", "1 room").getOrThrow()
        assertTrue(repo.perform(b.id, stranger.uid, Action.CANCEL).isFailure)
        assertTrue("owner cannot accept their own request", repo.perform(b.id, o.uid, Action.ACCEPT).isFailure)
        assertTrue(repo.perform("missing", o.uid, Action.CANCEL).isFailure)
        assertTrue("rate goes through rate()", repo.perform(b.id, o.uid, Action.RATE).isFailure)
        assertTrue("a worker cannot book", repo.request(w.uid, w.uid, "now", "x", "y").isFailure)
    }

    @Test fun anOwnerCannotBookTheSameWorkerTwiceOrAnUnavailableWorker() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        repo.request(o.uid, w.uid, "now", "a", "b").getOrThrow()
        assertTrue(repo.request(o.uid, w.uid, "now", "a", "b").isFailure)
        repo.setAvailability(w.uid, false, null, null)
        val o2 = signUp("9000000012", "Ravi", UserRole.OWNER)
        assertTrue(repo.request(o2.uid, w.uid, "now", "a", "b").isFailure)
    }

    @Test fun aBusyWorkerCannotAcceptASecondJob() = runBlocking {
        val w = worker()
        val o1 = signUp("9000000009", "Omar", UserRole.OWNER)
        val o2 = signUp("9000000012", "Ravi", UserRole.OWNER)
        val b1 = repo.request(o1.uid, w.uid, "now", "a", "b").getOrThrow()
        val b2 = repo.request(o2.uid, w.uid, "now", "a", "b").getOrThrow()
        assertTrue(repo.perform(b1.id, w.uid, Action.ACCEPT).isSuccess)
        assertTrue(repo.perform(b2.id, w.uid, Action.ACCEPT).isFailure)
        repo.perform(b1.id, w.uid, Action.CANCEL)
        assertTrue("free again after cancelling", repo.perform(b2.id, w.uid, Action.ACCEPT).isSuccess)
    }

    @Test fun unansweredRequestsExpireAndCannotBeAcceptedLate() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val b = repo.request(o.uid, w.uid, "now", "a", "b").getOrThrow()
        now += BookingStateMachine.REQUEST_TTL_MS
        assertTrue(repo.perform(b.id, w.uid, Action.ACCEPT).isFailure)
        assertEquals(BookingStatus.EXPIRED, repo.observe(b.id).first()!!.status)
    }

    @Test fun aWorkerWhoCancelsAfterAcceptingLosesReliability() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val b = repo.request(o.uid, w.uid, "now", "a", "b").getOrThrow()
        repo.perform(b.id, w.uid, Action.ACCEPT)
        val cancelled = repo.perform(b.id, w.uid, Action.CANCEL).getOrThrow()
        assertEquals(UserRole.WORKER, cancelled.cancelledBy)
        assertEquals(1, repo.observeOpenWorkers(Geo.searchPrefixes(kochi.lat, kochi.lng)).first().single().stats.cancelCount)
        // an owner cancelling does not hurt the worker
        val o2 = signUp("9000000012", "Ravi", UserRole.OWNER)
        val b2 = repo.request(o2.uid, w.uid, "now", "a", "b").getOrThrow()
        repo.perform(b2.id, w.uid, Action.ACCEPT)
        repo.perform(b2.id, o2.uid, Action.CANCEL)
        assertEquals(1, repo.observeOpenWorkers(Geo.searchPrefixes(kochi.lat, kochi.lng)).first().single().stats.cancelCount)
    }

    @Test fun bookingsListForBothSidesNewestFirst() = runBlocking {
        val w = worker()
        val o = signUp("9000000009", "Omar", UserRole.OWNER)
        val first = repo.request(o.uid, w.uid, "now", "a", "b").getOrThrow()
        repo.perform(first.id, o.uid, Action.CANCEL)
        now += 10
        val second = repo.request(o.uid, w.uid, "now", "a", "b").getOrThrow()
        assertEquals(listOf(second.id, first.id), repo.observeFor(o.uid).first().map { it.id })
        assertEquals(listOf(second.id, first.id), repo.observeFor(w.uid).first().map { it.id })
        assertTrue(repo.observeFor("nobody").first().isEmpty())
    }

    @Test fun demoWorkersAreAlwaysFlaggedAndVisibleNearTheSeedPoint() = runBlocking {
        repo.seedDemoWorkers(kochi)
        val listings = repo.observeOpenWorkers(Geo.searchPrefixes(kochi.lat, kochi.lng)).first()
        assertTrue(listings.isNotEmpty())
        assertTrue(listings.all { it.profile.isDemo && it.profile.name.startsWith("Demo") })
        assertNotNull(listings.firstOrNull { it.stats.verifiedCount > 0 })
    }
}
