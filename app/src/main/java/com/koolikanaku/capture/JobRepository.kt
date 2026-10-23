package com.koolikanaku.capture

import android.content.Context
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.Job
import java.io.File

/**
 * P2 persistence and integrity verification module for Jobs and Evidence.
 */
object JobRepository {

    /**
     * Verifies if an [EvidencePhoto]'s underlying file on disk still matches
     * its recorded cryptographic SHA-256 hash. Proves tamper-evidence on stage.
     */
    fun verifyPhotoIntegrity(context: Context, photo: EvidencePhoto): Boolean {
        return try {
            val file = PhotoStore.getFileFromUri(context, photo.uri)
                ?: return false
            val currentHash = CryptoUtils.sha256(file)
            currentHash.equals(photo.sha256Hash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Recalculates and returns the live SHA-256 hash of a photo's current file on disk.
     */
    fun recomputeLiveHash(context: Context, photo: EvidencePhoto): String? {
        return try {
            val file = PhotoStore.getFileFromUri(context, photo.uri) ?: return null
            CryptoUtils.sha256(file)
        } catch (e: Exception) {
            null
        }
    }
}
