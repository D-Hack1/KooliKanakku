package com.koolikanaku.domain

import com.koolikanaku.capture.CryptoUtils

/**
 * A SHA-256 fingerprint over a finished bill's totals and every photo hash in it. Printed on the
 * PDF and, when a job came from a Find-a-worker booking, sent as part of the evidence summary
 * (never the photos themselves) so a rating can be marked verified. Deterministic: the same job
 * and quote always produce the same fingerprint, which is what makes it checkable by either side.
 */
object BillFingerprint {

    fun compute(job: Job, quote: JobQuote): String {
        val sb = StringBuilder()
        sb.append(job.id).append('|').append(job.mode.name).append('|')
        sb.append("%.2f".format(quote.totalPriceInr.low)).append('-')
        sb.append("%.2f".format(quote.totalPriceInr.high)).append('|')
        job.rooms.sortedBy { it.id }.forEach { room ->
            sb.append(room.id).append('[')
            room.photos.sortedBy { it.id }.forEach { sb.append(it.sha256Hash).append(',') }
            sb.append(']')
        }
        return CryptoUtils.sha256(sb.toString())
    }
}
