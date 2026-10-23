package com.koolikanaku.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.koolikanaku.domain.ClutterRules
import java.util.Locale

/** One detection with edges normalized to 0..1 of the analyzed image, so models can be merged before scaling. */
data class Labeled(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** One exported Ultralytics YOLOv8 TFLite model (any class count) plus its labels file. */
class YoloModel(
    private val modelAsset: String,
    private val labelsAsset: String,
    private val confidenceThreshold: Float
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var channelsFirstInput = true   // [1,3,H,W] vs [1,H,W,3]
    private var featuresFirst = true
    private var numFeatures = 0
    private var numAnchors = 0
    private var raw: Array<Array<FloatArray>> = emptyArray()

    // interp.run() is a blocking native call coroutine cancellation can't interrupt; submitting it
    // here lets a stuck call be abandoned (via Future.get timeout) instead of hanging the UI forever.
    // A cached pool (not a single thread) so one stuck call never blocks later calls from even starting.
    private val inferenceExecutor = Executors.newCachedThreadPool()

    val isLoaded: Boolean get() = interpreter != null

    @Synchronized
    fun init(context: Context) {
        if (interpreter != null) return
        labels = LabelLoader.load(context, labelsAsset)
        val options = Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
        val interp = Interpreter(loadModel(context), options)
        channelsFirstInput = interp.getInputTensor(0).shape()[1] == 3
        // Output is [1, 4 + numClasses, numAnchors] for Ultralytics exports; accept the transposed layout too.
        val shape = interp.getOutputTensor(0).shape()
        featuresFirst = shape[1] < shape[2]
        numFeatures = if (featuresFirst) shape[1] else shape[2]
        numAnchors = if (featuresFirst) shape[2] else shape[1]
        raw = Array(1) { Array(shape[1]) { FloatArray(shape[2]) } }
        interpreter = interp
    }

    /** Blocking; call off the main thread. Returns an empty list if inference is abandoned after a timeout. */
    fun detect(bitmap: Bitmap): List<Labeled> {
        val interp = checkNotNull(interpreter) { "$modelAsset was not loaded" }
        val input = preprocess(bitmap)
        val rawCandidates = try {
            inferenceExecutor.submit<List<YoloPostProcessor.Candidate>> {
                // One lock covers run + parse: the output buffer is reused by the next call.
                synchronized(this) {
                    interp.run(input, raw)
                    YoloPostProcessor.parse(raw[0], featuresFirst, numFeatures, numAnchors, RAW_LOG_THRESHOLD)
                }
            }.get(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Log.e("YoloModel", "$modelAsset timed out after ${INFERENCE_TIMEOUT_SECONDS}s; skipping this photo")
            return emptyList()
        }

        // Temporarily log all raw detections above 0.10 confidence
        for (c in rawCandidates) {
            val label = labels.getOrNull(c.classIndex) ?: "unknown"
            Log.d(
                "YoloRaw",
                "[$modelAsset RAW > $RAW_LOG_THRESHOLD] '$label' conf=${String.format(Locale.US, "%.3f", c.confidence)} box=[${String.format(Locale.US, "%.2f", c.left)}, ${String.format(Locale.US, "%.2f", c.top)}, ${String.format(Locale.US, "%.2f", c.right)}, ${String.format(Locale.US, "%.2f", c.bottom)}]"
            )
        }

        // Class-specific confidence filtering:
        // Fixtures (sink, toilet, bed, etc.) keep confidenceThreshold (0.30+),
        // while loose clutter items trigger down to CLUTTER_CONFIDENCE_THRESHOLD (0.16).
        val filteredCandidates = rawCandidates.filter { c ->
            val label = labels.getOrNull(c.classIndex) ?: return@filter false
            val minConf = if (ClutterRules.isClutterLabel(label)) {
                CLUTTER_CONFIDENCE_THRESHOLD
            } else {
                confidenceThreshold
            }
            c.confidence >= minConf
        }

        return YoloPostProcessor.nms(filteredCandidates, NMS_IOU_THRESHOLD).mapNotNull { c ->
            val label = labels.getOrNull(c.classIndex) ?: return@mapNotNull null
            Labeled(label, c.confidence, c.left, c.top, c.right, c.bottom)
        }
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) scaled.recycle()

        val buf = ByteBuffer.allocateDirect(4 * 3 * pixels.size).order(ByteOrder.nativeOrder())
        if (channelsFirstInput) {
            for (shift in intArrayOf(16, 8, 0)) {          // planar R, G, B
                for (p in pixels) buf.putFloat(((p shr shift) and 0xFF) / 255f)
            }
        } else {
            for (p in pixels) {                            // interleaved RGB
                buf.putFloat(((p shr 16) and 0xFF) / 255f)
                buf.putFloat(((p shr 8) and 0xFF) / 255f)
                buf.putFloat((p and 0xFF) / 255f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun loadModel(context: Context): ByteBuffer =
        context.assets.openFd(modelAsset).use { fd ->
            FileInputStream(fd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }

    private companion object {
        const val INPUT_SIZE = 640
        const val NUM_THREADS = 4
        const val INFERENCE_TIMEOUT_SECONDS = 12L
    }
}
