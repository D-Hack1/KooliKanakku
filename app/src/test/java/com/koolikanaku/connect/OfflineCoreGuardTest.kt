package com.koolikanaku.connect

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pitch says the estimate and evidence engine has no network code. This test makes that a fact:
 * the offline core packages must never import the connected layer or any networking library.
 */
class OfflineCoreGuardTest {

    private val corePackages = listOf("ai", "domain", "capture", "export")
    private val forbidden = listOf(
        "import com.koolikanaku.connect",
        "import com.google.firebase",
        "import com.google.android.gms",
        "import okhttp3",
        "import retrofit2",
        "import java.net.",
        "import javax.net.",
        "import android.net.http",
        "import io.ktor"
    )

    private fun sourceRoot(): File = listOf("src/main/java/com/koolikanaku", "app/src/main/java/com/koolikanaku")
        .map(::File).first { it.isDirectory }

    @Test fun offlineCoreHasNoNetworkImports() {
        val root = sourceRoot()
        val offenders = corePackages.flatMap { pkg ->
            File(root, pkg).walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val trimmed = line.trim()
                    if (forbidden.any { trimmed.startsWith(it) }) "${file.relativeTo(root)}:${i + 1}: $trimmed" else null
                }
            }.toList()
        }
        assertTrue("Offline core must not use the network:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test fun theGuardActuallyScansTheCorePackages() {
        val root = sourceRoot()
        val files = corePackages.flatMap { File(root, it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue("expected core source files, found ${files.size}", files.size > 15)
    }
}
