package com.koolikanaku.ai

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Logs detections, grime score and latency for room photos pushed to PHOTO_DIR (tag "KK_BENCH").
 * Skipped when the folder is empty. Push photos with: adb push *.jpg /data/local/tmp/kk_photos/
 */
class DetectionBenchmarkTest {

    private val photoDir = File("/data/local/tmp/kk_photos")

    @Before fun setUp() {
        initDetector(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun logDetectionsAndLatency(): Unit = runBlocking {
        val photos = photoDir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }?.sorted().orEmpty()
        assumeTrue("No photos in $photoDir", photos.isNotEmpty())
        val times = mutableListOf<Long>()
        for (file in photos) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(file.path, opts) ?: continue
            detectObjects(bmp)  // warm-up
            val t0 = SystemClock.elapsedRealtime()
            val dets = detectObjects(bmp)
            val ms = SystemClock.elapsedRealtime() - t0
            times += ms
            val stain = analyzeStain(bmp)
            Log.i("KK_BENCH", "${file.name} ${bmp.width}x${bmp.height} detect=${ms}ms stain=${stain.severity}/${stain.score.toInt()} " +
                dets.joinToString { "${it.label}:${"%.2f".format(it.confidence)}" })
        }
        if (times.isNotEmpty()) Log.i("KK_BENCH", "median detect latency ${times.sorted()[times.size / 2]}ms over ${times.size} photos")
    }
}
