package com.koolikanaku

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * extra_feature.md Section 3.1 and Section 16: the connect layer is online (location, a demo/real
 * backend), but the offline core (ai/domain/capture/export) must never depend on it or on any
 * networking library. This is the actual enforcement behind the pitch claim "the estimate and
 * evidence engine has no network code and works in Airplane Mode" now that `INTERNET` is no longer
 * simply absent from the manifest project-wide.
 */
class OfflineCoreGuardTest {

    private val GUARDED_PACKAGES = listOf("ai", "domain", "capture", "export")
    private val FORBIDDEN_IMPORTS = listOf(
        "import com.koolikanaku.connect",
        "import okhttp3", "import retrofit2", "import com.google.firebase", "import io.supabase",
        "import java.net.HttpURLConnection", "import java.net.URL", "import javax.net.ssl"
    )

    private val srcRoot: File by lazy {
        listOf(
            File("src/main/java/com/koolikanaku"),
            File("app/src/main/java/com/koolikanaku"),
            File("../app/src/main/java/com/koolikanaku")
        ).first { it.exists() }
    }

    @Test
    fun guardedPackagesNeverImportTheConnectLayerOrNetworkingLibraries() {
        val violations = mutableListOf<String>()
        GUARDED_PACKAGES.forEach { pkg ->
            val dir = File(srcRoot, pkg)
            if (!dir.exists()) return@forEach
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEach { line ->
                    val trimmed = line.trim()
                    FORBIDDEN_IMPORTS.forEach { forbidden ->
                        if (trimmed.startsWith(forbidden)) {
                            violations += "${file.path}: $trimmed"
                        }
                    }
                }
            }
        }
        assertTrue("Offline-core files import the connect layer or a networking library:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun connectPackageExistsAndIsSeparateFromTheCore() {
        val connectDir = File(srcRoot, "connect")
        assertTrue("connect/ package not found at ${connectDir.path}", connectDir.exists())
    }
}
