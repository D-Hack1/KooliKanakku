package com.koolikanaku.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CryptoUtilsTest {

    @Test
    fun testEmptyStringHashMatchesNistVector() {
        val input = ""
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actual = CryptoUtils.sha256(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testAbcHashMatchesNistVector() {
        val input = "abc"
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val actual = CryptoUtils.sha256(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testLongVectorMatchesNist() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val expected = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        val actual = CryptoUtils.sha256(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testFileStreamMatchesByteHash() {
        val content = "KooliKanakku Offline Evidence Verification 2026"
        val tempFile = File.createTempFile("evidence_test", ".dat")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            val byteHash = CryptoUtils.sha256(content.toByteArray(Charsets.UTF_8))
            val fileHash = CryptoUtils.sha256(tempFile)
            assertEquals(byteHash, fileHash)
        } finally {
            tempFile.delete()
        }
    }
}
