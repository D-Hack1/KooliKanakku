package com.koolikanaku.connect.logic

import com.koolikanaku.capture.BillFingerprint
import com.koolikanaku.connect.model.EvidenceSummary
import com.koolikanaku.domain.Job
import com.koolikanaku.domain.JobQuote
import kotlin.math.ceil
import kotlin.math.floor

/** Turns a finished bill into the small summary a booking carries: hashes and totals only. */
object EvidenceSummaryBuilder {

    fun from(job: Job, quote: JobQuote, nowIso: String): EvidenceSummary = EvidenceSummary(
        billFingerprint = BillFingerprint.compute(job, quote),
        totalPriceLowInr = floor(quote.totalPriceInr.low).toInt(),
        totalPriceHighInr = ceil(quote.totalPriceInr.high).toInt(),
        roomCount = job.rooms.size,
        photoCount = job.rooms.sumOf { it.photos.size },
        afterPhotoHashes = BillFingerprint.afterPhotoHashes(job),
        generatedAtIso = nowIso
    )
}
