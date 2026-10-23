package com.koolikanaku.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.PhotoRole
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.max

object PhotoStore {

    private const val PHOTO_DIR_NAME = "photos"
    private const val DEFAULT_ANALYSIS_LONG_EDGE = 1280

    /**
     * Creates a new destination file and FileProvider Uri for camera captures.
     */
    fun createCaptureTarget(context: Context): Pair<File, Uri> {
        val photosDir = getPhotosDir(context)
        val file = File(photosDir, "evidence_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    /**
     * Processes an already saved photo file (e.g. from CameraX or TakePicture fallback),
     * computes the SHA-256 hash of the exact saved bytes, and constructs an [EvidencePhoto].
     */
    fun processCapturedFile(
        context: Context,
        file: File,
        role: PhotoRole,
        depressionDeg: Float? = null,
        holdHeightM: Float? = null
    ): EvidencePhoto {
        require(file.exists() && file.length() > 0) { "Captured file does not exist or is empty: ${file.absolutePath}" }

        // Hash the EXACT saved bytes on disk
        val sha256 = CryptoUtils.sha256(file)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return EvidencePhoto(
            id = UUID.randomUUID().toString(),
            role = role,
            uri = uri,
            timestampIso = currentIsoTimestamp(),
            sha256Hash = sha256,
            depressionDeg = depressionDeg,
            holdHeightM = holdHeightM
        )
    }

    /**
     * Saves raw JPEG byte array directly to disk, hashes the exact saved file,
     * and constructs an [EvidencePhoto].
     */
    fun saveAndHashBytes(
        context: Context,
        jpegBytes: ByteArray,
        role: PhotoRole,
        depressionDeg: Float? = null,
        holdHeightM: Float? = null
    ): EvidencePhoto {
        val (file, _) = createCaptureTarget(context)
        file.outputStream().use { it.write(jpegBytes) }
        return processCapturedFile(context, file, role, depressionDeg, holdHeightM)
    }

    /**
     * Demo fallback path: import an image from a Gallery-picked Uri.
     * Streams bytes directly to internal storage, hashes the exact saved disk bytes,
     * and produces an [EvidencePhoto] identical in integrity to a camera capture.
     */
    fun importFromGalleryUri(
        context: Context,
        sourceUri: Uri,
        role: PhotoRole,
        depressionDeg: Float? = null,
        holdHeightM: Float? = null
    ): EvidencePhoto {
        val (destFile, _) = createCaptureTarget(context)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open stream for URI: $sourceUri")

        return processCapturedFile(context, destFile, role, depressionDeg, holdHeightM)
    }

    /**
     * Efficiently decodes and downscales a photo for AI analyzers (YOLO / Stain analysis).
     * Long edge will be ~[maxLongEdge] (default 1280px) to prevent Out-Of-Memory errors on 12MP photos.
     * Handles EXIF orientation to ensure upright orientation.
     * The original full-resolution file on disk remains completely untouched.
     */
    fun getDownscaledBitmap(
        context: Context,
        photoUri: Uri,
        maxLongEdge: Int = DEFAULT_ANALYSIS_LONG_EDGE
    ): Bitmap? {
        val inputStreamSupplier: () -> InputStream? = {
            context.contentResolver.openInputStream(photoUri)
        }

        // 1. Read image dimensions without allocating full pixel memory
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        // decodeStream returns null when inJustDecodeBounds is set, so only the options carry the result.
        val stream = inputStreamSupplier() ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // 2. Calculate sub-sampling factor
        val currentLongEdge = max(origWidth, origHeight)
        var sampleSize = 1
        while (currentLongEdge / (sampleSize * 2) >= maxLongEdge) {
            sampleSize *= 2
        }

        // 3. Decode sub-sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = inputStreamSupplier()?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // 4. Handle EXIF orientation
        val rotationDegrees = getExifOrientationDegrees(inputStreamSupplier)
        val rotatedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val transformed = Bitmap.createBitmap(
                sampledBitmap, 0, 0,
                sampledBitmap.width, sampledBitmap.height,
                matrix, true
            )
            if (transformed != sampledBitmap) {
                sampledBitmap.recycle()
            }
            transformed
        } else {
            sampledBitmap
        }

        // 5. Final precision scale down to exactly maxLongEdge if needed
        val longEdge = max(rotatedBitmap.width, rotatedBitmap.height)
        if (longEdge <= maxLongEdge) {
            return rotatedBitmap
        }

        val scale = maxLongEdge.toFloat() / longEdge
        val targetWidth = (rotatedBitmap.width * scale).toInt()
        val targetHeight = (rotatedBitmap.height * scale).toInt()

        val finalBitmap = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
        if (finalBitmap != rotatedBitmap) {
            rotatedBitmap.recycle()
        }
        return finalBitmap
    }

    /**
     * Resolves the underlying File from a FileProvider or file Uri if available locally.
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it) }
        }
        // Check files in photos dir by matching filename
        val lastSegment = uri.lastPathSegment ?: return null
        val photosDir = getPhotosDir(context)
        val candidate = File(photosDir, lastSegment)
        if (candidate.exists()) return candidate

        // Search within photosDir
        return photosDir.listFiles()?.firstOrNull { it.name == lastSegment }
    }

    private fun getPhotosDir(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getExifOrientationDegrees(streamSupplier: () -> InputStream?): Float {
        return try {
            streamSupplier()?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
}
