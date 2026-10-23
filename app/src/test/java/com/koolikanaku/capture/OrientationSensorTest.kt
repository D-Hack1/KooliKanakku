package com.koolikanaku.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.tan

class OrientationSensorTest {

    @Test
    fun testUprightPositionGivesZeroDegrees() {
        // Upright vertical: gravity vector aligns with negative Y axis
        val angle = OrientationSensor.computeDepressionAngle(0f, -9.81f, 0f)
        assertEquals(0.0f, angle, 0.05f)
    }

    @Test
    fun testFlatFaceUpPositionGivesNinetyDegrees() {
        // Lay flat face-up on table: gravity vector aligns with positive Z axis
        val angle = OrientationSensor.computeDepressionAngle(0f, 0f, 9.81f)
        assertEquals(90.0f, angle, 0.05f)
    }

    @Test
    fun testKnownTiltAngleThirtyDegrees() {
        // When gz / |g| = 0.5, asin(0.5) = 30 degrees
        val angle = OrientationSensor.computeDepressionAngle(0f, 8.66f, 5.0f) // sqrt(0^2 + 8.66^2 + 5^2) = 10, gz/|g| = 0.5
        assertEquals(30.0f, angle, 0.1f)
    }

    @Test
    fun testSyntheticDistanceTrigonometryMatchesSpec() {
        // From worksplit.md line 67: h = 1.4m and a = 19.3 degrees gives d ≈ 4.0m
        val holdHeightM = 1.4f
        val depressionDeg = 19.3f
        val d = holdHeightM / tan(Math.toRadians(depressionDeg.toDouble()))
        assertEquals(4.0, d, 0.05)
    }
}
