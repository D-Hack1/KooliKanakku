package com.koolikanaku.capture

import android.net.Uri
import com.koolikanaku.connect.logic.EvidenceSummaryBuilder
import com.koolikanaku.domain.AppMode
import com.koolikanaku.domain.CleaningTask
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.Job
import com.koolikanaku.domain.JobQuote
import com.koolikanaku.domain.MinMax
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.RoomEntry
import com.koolikanaku.domain.RoomType
import com.koolikanaku.domain.TaskSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class BillFingerprintTest {

    private val uri = Mockito.mock(Uri::class.java)
    private fun photo(role: PhotoRole, hash: String) = EvidencePhoto(hash.take(6), role, uri, "2026-10-24T05:00:00Z", hash)
    private fun hash(c: Char) = c.toString().repeat(64)
    private fun task(key: String, qty: Int, selected: Boolean = true) =
        CleaningTask("t-$key", key, key, key, qty, 10, 5.0, selected, TaskSource.DETECTED)

    private fun job(photos: List<EvidencePhoto> = listOf(photo(PhotoRole.WIDE, hash('a')), photo(PhotoRole.TOUGHEST, hash('b'))),
                    tasks: List<CleaningTask> = listOf(task("toilet", 1), task("sink", 2))) =
        Job("job-1", AppMode.WORKER_ESTIMATE, "2026-10-24T05:00:00Z", 250.0, 1.0, 1.25f,
            listOf(RoomEntry("r1", RoomType.BATHROOM, photos = photos, tasks = tasks)))

    private val quote = JobQuote(emptyList(), MinMax(0.0, 0.0), MinMax(0.0, 0.0), MinMax(400.4, 519.6))

    @Test fun sameBillGivesTheSameFingerprint() {
        val a = BillFingerprint.compute(job(), quote)
        assertEquals(64, a.length)
        assertEquals(a, BillFingerprint.compute(job(), quote))
    }

    @Test fun changingAPhotoTheTasksOrThePriceChangesIt() {
        val base = BillFingerprint.compute(job(), quote)
        assertNotEquals(base, BillFingerprint.compute(job(photos = listOf(photo(PhotoRole.WIDE, hash('a')), photo(PhotoRole.TOUGHEST, hash('c')))), quote))
        assertNotEquals(base, BillFingerprint.compute(job(tasks = listOf(task("toilet", 1))), quote))
        assertNotEquals(base, BillFingerprint.compute(job(tasks = listOf(task("toilet", 1), task("sink", 3))), quote))
        assertNotEquals(base, BillFingerprint.compute(job(), quote.copy(totalPriceInr = MinMax(400.4, 600.0))))
    }

    @Test fun anUntickedTaskDoesNotChangeTheBill() {
        assertEquals(
            BillFingerprint.compute(job(), quote),
            BillFingerprint.compute(job(tasks = listOf(task("toilet", 1), task("sink", 2), task("mirror", 1, selected = false))), quote)
        )
    }

    @Test fun theOrderPhotosWereTakenInDoesNotMatter() {
        val a = listOf(photo(PhotoRole.WIDE, hash('a')), photo(PhotoRole.TOUGHEST, hash('b')))
        assertEquals(BillFingerprint.compute(job(photos = a), quote), BillFingerprint.compute(job(photos = a.reversed()), quote))
    }

    @Test fun anAfterPhotoIsPartOfTheFingerprintAndOfTheEvidence() {
        val without = job()
        val withAfter = job(photos = without.rooms[0].photos + photo(PhotoRole.AFTER, hash('d')))
        assertNotEquals(BillFingerprint.compute(without, quote), BillFingerprint.compute(withAfter, quote))
        assertTrue(BillFingerprint.afterPhotoHashes(without).isEmpty())
        assertEquals(listOf(hash('d')), BillFingerprint.afterPhotoHashes(withAfter))
    }

    @Test fun evidenceSummaryCarriesOnlyHashesAndTotals() {
        val withAfter = job(photos = job().rooms[0].photos + photo(PhotoRole.AFTER, hash('d')))
        val e = EvidenceSummaryBuilder.from(withAfter, quote, "2026-10-24T06:00:00Z")
        assertEquals(BillFingerprint.compute(withAfter, quote), e.billFingerprint)
        assertEquals(400, e.totalPriceLowInr)       // rounded outwards so the range never shrinks
        assertEquals(520, e.totalPriceHighInr)
        assertEquals(1, e.roomCount)
        assertEquals(3, e.photoCount)
        assertEquals(listOf(hash('d')), e.afterPhotoHashes)
        assertTrue("a documented job counts as verified", e.isVerified)
        assertFalse("no after photo, not verified", EvidenceSummaryBuilder.from(job(), quote, "x").isVerified)
    }

    @Test fun aJobStartedFromABookingKnowsIt() {
        assertNull(job().bookingId)
        assertEquals("b-1", job().copy(bookingId = "b-1").bookingId)
    }
}
