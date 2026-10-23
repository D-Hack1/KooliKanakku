package com.koolikanaku.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.sqrt

data class OrientationState(
    val depressionDeg: Float = 0f,
    val isSteady: Boolean = false,
    val isAvailable: Boolean = true,
    val sampleCount: Int = 0
)

/**
 * Manages device orientation for distance-to-wall measurement.
 *
 * Physics:
 * Measures depression angle below horizontal using the Gravity sensor (TYPE_GRAVITY).
 * TYPE_GRAVITY is chosen over TYPE_ROTATION_VECTOR because rotation vectors use the
 * magnetometer, which suffers from severe magnetic distortions indoors (appliances,
 * rebar, pipes). The gravity sensor isolates the down-vector purely and reliably.
 *
 * Trigonometry:
 * Angle a = asin(g_z / |g|)
 * - Vertical upright (looking level): a ≈ 0°
 * - Flat face-up (looking straight down): a ≈ 90°
 * Distance d = h / tan(a), where h is the hold height off the floor.
 */
class OrientationSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _orientationState = MutableStateFlow(
        OrientationState(isAvailable = gravitySensor != null)
    )
    val orientationState: StateFlow<OrientationState> = _orientationState.asStateFlow()

    // Ring buffer for smoothing & variance calculation
    private val windowSize = 10
    private val angleWindow = FloatArray(windowSize)
    private var windowIndex = 0
    private var windowCount = 0

    // Threshold in degrees standard deviation for the "steady" indicator
    private val steadyThresholdDeg = 0.65f

    private var isListening = false

    fun startListening() {
        if (isListening || gravitySensor == null) return
        resetBuffer()
        sensorManager.registerListener(
            this,
            gravitySensor,
            SensorManager.SENSOR_DELAY_UI
        )
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_GRAVITY && event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]

        val rawAngle = computeDepressionAngle(gx, gy, gz)

        // Store into rolling window
        angleWindow[windowIndex] = rawAngle
        windowIndex = (windowIndex + 1) % windowSize
        if (windowCount < windowSize) {
            windowCount++
        }

        // Calculate moving average
        var sum = 0f
        for (i in 0 until windowCount) {
            sum += angleWindow[i]
        }
        val smoothedAngle = sum / windowCount

        // Calculate variance / standard deviation
        var varianceSum = 0f
        for (i in 0 until windowCount) {
            val diff = angleWindow[i] - smoothedAngle
            varianceSum += diff * diff
        }
        val stdDev = sqrt(varianceSum / windowCount)
        val steady = windowCount >= windowSize && stdDev < steadyThresholdDeg

        _orientationState.value = OrientationState(
            depressionDeg = smoothedAngle,
            isSteady = steady,
            isAvailable = true,
            sampleCount = windowCount
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun resetBuffer() {
        windowIndex = 0
        windowCount = 0
        for (i in 0 until windowSize) {
            angleWindow[i] = 0f
        }
    }

    companion object {
        /**
         * Pure function to calculate depression angle from gravity vector coordinates.
         * Exposed statically for unit testing and offline verification.
         *
         * @return Depression angle in degrees:
         *         0° when upright (level),
         *         90° when face-up (aiming at floor).
         */
        fun computeDepressionAngle(gx: Float, gy: Float, gz: Float): Float {
            val norm = sqrt(gx * gx + gy * gy + gz * gz)
            if (norm <= 0.0001f) return 0f

            // Clamp to [-1.0, 1.0] to prevent NaN near extremes
            val ratio = (gz / norm).coerceIn(-1.0f, 1.0f)
            val rad = asin(ratio)
            return Math.toDegrees(rad.toDouble()).toFloat()
        }
    }
}
