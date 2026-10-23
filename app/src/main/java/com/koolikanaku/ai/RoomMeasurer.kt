package com.koolikanaku.ai

import com.koolikanaku.domain.Constants
import com.koolikanaku.domain.EvidencePhoto
import com.koolikanaku.domain.LengthUnit
import com.koolikanaku.domain.MeasureResult
import com.koolikanaku.domain.MeasureWarning
import com.koolikanaku.domain.SizeSource
import kotlin.math.tan

const val TOO_FAR_ANGLE_DEG = 10.0
const val TOO_CLOSE_ANGLE_DEG = 70.0
const val MIN_SIDE_M = 1.2
const val MAX_SIDE_M = 12.0

/**
 * Each shot aims the crosshair at a floor/wall line `a` degrees below the horizon from height `h`,
 * so that point is `d = h / tan(a)` away; add [Constants.NEAR_WALL_OFFSET_M] for the near wall.
 */
fun measureRoom(lengthShot: EvidencePhoto, widthShot: EvidencePhoto): MeasureResult {
    val warnings = LinkedHashSet<MeasureWarning>()
    val length = sideFromShot(lengthShot, warnings)
    val width = sideFromShot(widthShot, warnings)
    if (length == null || width == null) return MeasureResult(null, warnings.toList())

    if (length < MIN_SIDE_M || length > MAX_SIDE_M || width < MIN_SIDE_M || width > MAX_SIDE_M) {
        warnings.add(MeasureWarning.IMPLAUSIBLE_SIZE)
    }
    val dims = Constants.computeDimensions(length, width, LengthUnit.METRES, SizeSource.MEASURED)
    return MeasureResult(dims, warnings.toList())
}

private fun sideFromShot(shot: EvidencePhoto, warnings: MutableSet<MeasureWarning>): Double? {
    val angle = shot.depressionDeg?.toDouble() ?: return null
    val height = shot.holdHeightM?.toDouble() ?: return null
    if (angle < TOO_FAR_ANGLE_DEG) warnings.add(MeasureWarning.TOO_FAR)
    if (angle > TOO_CLOSE_ANGLE_DEG) warnings.add(MeasureWarning.TOO_CLOSE)
    if (angle <= 0.0 || angle >= 90.0) return null   // no usable floor intersection
    val d = height / tan(Math.toRadians(angle))
    return d + Constants.NEAR_WALL_OFFSET_M
}
