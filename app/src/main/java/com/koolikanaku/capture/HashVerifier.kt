package com.koolikanaku.capture

import android.content.Context
import android.net.Uri
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.Job

/** Re-checks evidence: do the saved photos still hash to what was recorded, and does a given file match one of them? */
object HashVerifier {

    data class PhotoCheck(val roomNumber: Int, val photo: EvidencePhoto, val intact: Boolean)
    data class FileMatch(val roomNumber: Int, val photo: EvidencePhoto)

    fun checkSavedPhotos(context: Context, job: Job): List<PhotoCheck> =
        job.rooms.flatMapIndexed { index, room ->
            room.photos.map { PhotoCheck(index + 1, it, JobRepository.verifyPhotoIntegrity(context, it)) }
        }

    /** SHA-256 of the exact bytes behind [uri], or null if it cannot be read. */
    fun hashOf(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { CryptoUtils.sha256(it) }
    } catch (e: Exception) {
        null
    }

    fun findMatch(job: Job, hash: String): FileMatch? {
        job.rooms.forEachIndexed { index, room ->
            room.photos.firstOrNull { it.sha256Hash.equals(hash, ignoreCase = true) }?.let { return FileMatch(index + 1, it) }
        }
        return null
    }
}
