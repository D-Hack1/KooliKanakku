package com.koolikanaku.capture

import com.koolikanaku.domain.Job
import com.koolikanaku.domain.JobQuote
import com.koolikanaku.domain.PhotoRole
import java.util.Locale

/**
 * One SHA-256 over the whole bill: the job, every room's size and ticked tasks, the price range and
 * every photo hash. Change any of them and the fingerprint changes, so a printed fingerprint vouches
 * for the entire bill, not just single photos. Pure and offline.
 */
object BillFingerprint {

    fun compute(job: Job, quote: JobQuote): String = CryptoUtils.sha256(canonicalText(job, quote))

    internal fun canonicalText(job: Job, quote: JobQuote): String {
        val lines = mutableListOf<String>()
        lines += "KKB1"
        lines += "job|${job.id}|${job.mode.name}|${String.format(Locale.US, "%.2f", job.hourlyRateInr)}"
        lines += "total|${quote.totalPriceInr.low.toLong()}|${quote.totalPriceInr.high.toLong()}"
        job.rooms.forEachIndexed { index, room ->
            val area = String.format(Locale.US, "%.1f", room.dimensions?.computedAreaSqFt ?: 0.0)
            val tasks = room.tasks.filter { it.isSelected }.sortedBy { it.catalogKey }.joinToString(",") { "${it.catalogKey}:${it.quantity}" }
            val severity = (room.severityOverride ?: room.stain?.severity)?.name ?: "NONE"
            lines += "room|$index|${room.roomType.name}|$area|$severity|${room.clutter.name}|$tasks"
        }
        // photos sorted so the order they were taken in does not matter
        job.rooms.flatMap { it.photos }
            .sortedWith(compareBy({ it.role.name }, { it.sha256Hash }))
            .forEach { lines += "photo|${it.role.name}|${it.sha256Hash}" }
        return lines.joinToString("\n")
    }

    /** Hashes of the AFTER photos, which make a booking's evidence count as "verified". */
    fun afterPhotoHashes(job: Job): List<String> =
        job.rooms.flatMap { it.photos }.filter { it.role == PhotoRole.AFTER }.map { it.sha256Hash }.sorted()
}
