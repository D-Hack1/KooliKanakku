package com.koolikanaku.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.koolikanaku.domain.DetectedObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val CONFIDENCE_THRESHOLD = 0.30f
const val NMS_IOU_THRESHOLD = 0.50f
/** The Open Images model scores lower than COCO for the same object, so it gets a lower bar. */
const val SECONDARY_CONFIDENCE_THRESHOLD = 0.20f
/** Clutter-specific confidence threshold: allow loose clutter items to trigger down to 0.16. */
const val CLUTTER_CONFIDENCE_THRESHOLD = 0.16f
/** Lower parsing threshold to capture and log all raw detections for debugging/analysis. */
const val RAW_LOG_THRESHOLD = 0.10f

/** Call once (e.g. from MainActivity.onCreate) before the first [detectObjects]. Returns false if the main model failed. */
fun initDetector(context: Context): Boolean = try {
    YoloAnalyzer.init(context.applicationContext)
    true
} catch (e: Exception) {
    Log.e("YoloAnalyzer", "Detector failed to load", e)
    false
} catch (e: UnsatisfiedLinkError) {
    Log.e("YoloAnalyzer", "LiteRT native library failed to load", e)
    false
}

suspend fun detectObjects(bitmap: Bitmap): List<DetectedObject> = YoloAnalyzer.detect(bitmap)

/**
 * Two detectors, one result list. The main model (COCO yolov8n, 80 classes) is reliable for
 * common household objects. The secondary model (Open Images yolov8n, 601 classes) is only
 * trusted for the extra classes COCO lacks (countertop, cabinetry, window, mug, towel...).
 * If the secondary model is missing or fails to load, the main model works alone.
 */
object YoloAnalyzer {

    private val primary = YoloModel("detector.tflite", "labels.txt", CONFIDENCE_THRESHOLD)
    private val secondary = YoloModel("detector_oiv7.tflite", "labels_oiv7.txt", SECONDARY_CONFIDENCE_THRESHOLD)

    val hasSecondary: Boolean get() = secondary.isLoaded

    @Synchronized
    fun init(context: Context) {
        primary.init(context)
        if (!secondary.isLoaded) {
            try {
                secondary.init(context)
            } catch (e: Exception) {
                Log.w("YoloAnalyzer", "Secondary detector unavailable; continuing with the main model only", e)
            } catch (e: UnsatisfiedLinkError) {
                Log.w("YoloAnalyzer", "Secondary detector unavailable; continuing with the main model only", e)
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<DetectedObject> = withContext(Dispatchers.Default) {
        check(primary.isLoaded) { "YoloAnalyzer.init(context) was not called" }
        val main = primary.detect(bitmap)
        val extra = if (secondary.isLoaded) {
            try { secondary.detect(bitmap) } catch (e: Exception) {
                Log.w("YoloAnalyzer", "Secondary detection failed; using the main model only", e)
                emptyList()
            }
        } else emptyList()

        DetectionMerger.merge(main, extra).map { d ->
            DetectedObject(
                label = d.label,
                confidence = d.confidence,
                boundingBox = RectF(
                    (d.left * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                    (d.top * bitmap.height).coerceIn(0f, bitmap.height.toFloat()),
                    (d.right * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                    (d.bottom * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
                )
            )
        }
    }
}
