package com.koolikanaku.domain

enum class SizePreset(val labelEn: String, val labelMl: String) {
    SMALL("Small", "ചെറുത്"),
    MEDIUM("Medium", "ഇടത്തരം"),
    LARGE("Large", "വലുത്")
}

object Constants {
    const val DEFAULT_MARKET_RATE_INR_PER_HR: Double = 250.0
    const val DEFAULT_WORKER_RATE_INR_PER_HR: Double = 250.0
    const val DEFAULT_HOLD_HEIGHT_M: Float = 1.25f
    const val HOLD_HEIGHT_FACTOR: Float = 0.75f
    const val NEAR_WALL_OFFSET_M: Double = 0.30
    const val SQ_M_TO_SQ_FT: Double = 10.7639
    const val PACE_TO_FEET: Double = 2.5

    val MINUTES_PER_SQ_FT: Map<RoomType, Double> = mapOf(
        RoomType.KITCHEN to 0.35,
        RoomType.BATHROOM to 0.45,
        RoomType.BEDROOM to 0.20,
        RoomType.LIVING_ROOM to 0.20,
        RoomType.OTHER to 0.25
    )

    val CLUTTER_MULTIPLIER: Map<ClutterLevel, Double> = mapOf(
        ClutterLevel.LOW to 1.0,
        ClutterLevel.MEDIUM to 1.10,
        ClutterLevel.HIGH to 1.25
    )

    val GRIME_MULTIPLIER: Map<SeverityLevel, Double> = mapOf(
        SeverityLevel.LIGHT to 1.0,
        SeverityLevel.MODERATE to 1.15,
        SeverityLevel.HEAVY to 1.35
    )

    val SPREAD_BY_SOURCE: Map<SizeSource, Double> = mapOf(
        SizeSource.MANUAL to 0.10,
        SizeSource.MEASURED to 0.20,
        SizeSource.PRESET to 0.25
    )

    val PRESET_AREA_SQ_FT: Map<RoomType, Map<SizePreset, Double>> = mapOf(
        RoomType.KITCHEN to mapOf(
            SizePreset.SMALL to 65.0,
            SizePreset.MEDIUM to 100.0,
            SizePreset.LARGE to 150.0
        ),
        RoomType.BATHROOM to mapOf(
            SizePreset.SMALL to 35.0,
            SizePreset.MEDIUM to 50.0,
            SizePreset.LARGE to 80.0
        ),
        RoomType.BEDROOM to mapOf(
            SizePreset.SMALL to 100.0,
            SizePreset.MEDIUM to 150.0,
            SizePreset.LARGE to 220.0
        ),
        RoomType.LIVING_ROOM to mapOf(
            SizePreset.SMALL to 140.0,
            SizePreset.MEDIUM to 220.0,
            SizePreset.LARGE to 340.0
        ),
        RoomType.OTHER to mapOf(
            SizePreset.SMALL to 80.0,
            SizePreset.MEDIUM to 150.0,
            SizePreset.LARGE to 300.0
        )
    )

    const val MIN_PLAUSIBLE_AREA_SQ_FT: Double = 15.0
    const val MAX_PLAUSIBLE_AREA_SQ_FT: Double = 1600.0

    fun computeDimensions(
        length: Double,
        width: Double,
        unit: LengthUnit,
        source: SizeSource
    ): RoomDimensions {
        val areaSqFt = when (unit) {
            LengthUnit.FEET -> length * width
            LengthUnit.METRES -> (length * width) * SQ_M_TO_SQ_FT
            LengthUnit.PACES -> (length * PACE_TO_FEET) * (width * PACE_TO_FEET)
        }
        return RoomDimensions(
            length = length,
            width = width,
            unit = unit,
            computedAreaSqFt = areaSqFt,
            source = source
        )
    }
}
