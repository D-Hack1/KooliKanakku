package com.koolikanaku.ai

import android.net.Uri
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.MeasureWarning
import com.koolikanaku.domain.PhotoRole
import com.koolikanaku.domain.SizeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class RoomMeasurerTest {

    private fun shot(role: PhotoRole, deg: Float?, h: Float? = 1.4f) =
        EvidencePhoto("id", role, Mockito.mock(Uri::class.java), "t", "hash", deg, h)

    @Test fun knownGeometry() {
        // h = 1.4 m, a = 19.3 deg -> d ~ 4.0 m, side = d + 0.3
        val r = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 19.3f), shot(PhotoRole.MEASURE_WIDTH, 19.3f))
        val dims = r.dimensions!!
        assertEquals(4.3, dims.length, 0.03)
        assertEquals(4.3, dims.width, 0.03)
        assertEquals(SizeSource.MEASURED, dims.source)
        assertEquals(4.3 * 4.3 * 10.7639, dims.computedAreaSqFt, 0.8)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun missingShotGivesNullDimensions() {
        val r = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 20f), shot(PhotoRole.MEASURE_WIDTH, null))
        assertNull(r.dimensions)
    }

    @Test fun tooFarAndTooClose() {
        val far = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 8f), shot(PhotoRole.MEASURE_WIDTH, 20f))
        assertTrue(MeasureWarning.TOO_FAR in far.warnings)
        assertNotNull(far.dimensions)
        val close = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 75f), shot(PhotoRole.MEASURE_WIDTH, 20f))
        assertTrue(MeasureWarning.TOO_CLOSE in close.warnings)
    }

    @Test fun implausibleSizes() {
        val tiny = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 65f), shot(PhotoRole.MEASURE_WIDTH, 65f))
        assertTrue(MeasureWarning.IMPLAUSIBLE_SIZE in tiny.warnings)
        val huge = measureRoom(shot(PhotoRole.MEASURE_LENGTH, 5f), shot(PhotoRole.MEASURE_WIDTH, 20f))
        assertTrue(MeasureWarning.IMPLAUSIBLE_SIZE in huge.warnings)
    }
}
