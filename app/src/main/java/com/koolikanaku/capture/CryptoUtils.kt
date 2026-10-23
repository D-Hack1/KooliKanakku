package com.koolikanaku.capture

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object CryptoUtils {

    private const val ALGORITHM = "SHA-256"

    /**
     * Computes the SHA-256 hash of a byte array and returns a lowercase hex string.
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(bytes)
        return hashBytes.toHexString()
    }

    /**
     * Computes the SHA-256 hash of a String (UTF-8) and returns a lowercase hex string.
     */
    fun sha256(text: String): String {
        return sha256(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Computes the SHA-256 hash of an InputStream using a buffered stream.
     * Does NOT load the entire stream into memory, preventing OOM on large captures.
     */
    fun sha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().toHexString()
    }

    /**
     * Computes the SHA-256 hash of the exact bytes saved on disk for a File.
     */
    fun sha256(file: File): String {
        return file.inputStream().use { sha256(it) }
    }

    /**
     * Formats bytes as a 64-character lowercase hex string.
     */
    private fun ByteArray.toHexString(): String {
        val hexChars = CharArray(size * 2)
        val digits = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }
}
