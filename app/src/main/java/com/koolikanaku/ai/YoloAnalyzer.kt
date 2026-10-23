package com.koolikanaku.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import com.koolikanaku.domain.DetectedObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Inference size of the exported yolov8n (COCO, 80 classes) model. */
private const val INPUT_SIZE = 640
private const val MODEL_ASSET = "detector.tflite"
private const val NUM_THREADS = 4

const val CONFIDENCE_THRESHOLD = 0.30f
const val NMS_IOU_THRESHOLD = 0.50f

/** Call once (e.g. from MainActivity.onCreate) before the first [detectObjects]. */
fun initDetector(context: Context) = YoloAnalyzer.init(context.applicationContext)

suspend fun detectObjects(bitmap: Bitmap): List<DetectedObject> = YoloAnalyzer.detect(bitmap)

object YoloAnalyzer {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var channelsFirstInput = true   // [1,3,H,W] vs [1,H,W,3]
    private var outputShape = intArrayOf()

    @Synchronized
    fun init(context: Context) {
        if (interpreter != null) return
        labels = LabelLoader.load(context)
        val options = Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
        val interp = Interpreter(loadModel(context), options)
        channelsFirstInput = interp.getInputTensor(0).shape()[1] == 3
        outputShape = interp.getOutputTensor(0).shape()
        interpreter = interp
    }

    suspend fun detect(bitmap: Bitmap): List<DetectedObject> = withContext(Dispatchers.Default) {
        val interp = checkNotNull(interpreter) { "YoloAnalyzer.init(context) was not called" }
        val input = preprocess(bitmap)

        // Output is [1, 4 + numClasses, numAnchors] for Ultralytics exports; accept the transposed layout too.
        val (d1, d2) = outputShape[1] to outputShape[2]
        val featuresFirst = d1 < d2
        val numFeatures = if (featuresFirst) d1 else d2
        val numAnchors = if (featuresFirst) d2 else d1
        val raw = Array(1) { Array(d1) { FloatArray(d2) } }
        synchronized(this@YoloAnalyzer) { interp.run(input, raw) }

        val candidates = YoloPostProcessor.parse(
            output = raw[0],
            featuresFirst = featuresFirst,
            numFeatures = numFeatures,
            numAnchors = numAnchors,
            confidenceThreshold = CONFIDENCE_THRESHOLD
        )
        YoloPostProcessor.nms(candidates, NMS_IOU_THRESHOLD).mapNotNull { c ->
            val label = labels.getOrNull(c.classIndex) ?: return@mapNotNull null
            DetectedObject(
                label = label,
                confidence = c.confidence,
                boundingBox = RectF(
                    (c.left * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                    (c.top * bitmap.height).coerceIn(0f, bitmap.height.toFloat()),
                    (c.right * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                    (c.bottom * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
                )
            )
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
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
}
