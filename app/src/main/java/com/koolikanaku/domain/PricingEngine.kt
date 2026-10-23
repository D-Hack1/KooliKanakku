package com.koolikanaku.domain

object PricingEngine {

    fun quoteRoom(room: RoomEntry, job: Job): RoomQuote {
        val areaSqFt = room.dimensions?.computedAreaSqFt ?: 0.0
        val minutesPerSqFt = Constants.MINUTES_PER_SQ_FT[room.roomType] ?: 0.25
        val baseFloorMinutes = areaSqFt * minutesPerSqFt

        val selectedTasks = room.tasks.filter { it.isSelected && it.quantity > 0 }
        val taskMinutes = selectedTasks.sumOf { it.quantity * it.minutesPerUnit }.toDouble()

        val mClutter = Constants.CLUTTER_MULTIPLIER[room.clutter] ?: 1.0
        val effectiveSeverity = room.severityOverride ?: room.stain?.severity ?: SeverityLevel.LIGHT
        val mGrime = Constants.GRIME_MULTIPLIER[effectiveSeverity] ?: 1.0
        val mCond = 1.0 + (mClutter - 1.0) + (mGrime - 1.0)

        val standardMinutesPoint = (baseFloorMinutes + taskMinutes) * mCond
        val effectivePace = if (job.mode == AppMode.OWNER_QUOTE) 1.0 else job.paceFactor
        val expectedMinutesPoint = standardMinutesPoint * effectivePace
        val effectiveRate = job.hourlyRateInr
        val materialsInr = selectedTasks.sumOf { it.quantity * it.materialCostInr }
        val pricePoint = (standardMinutesPoint / 60.0) * effectiveRate + materialsInr

        val spread = room.dimensions?.source?.let { Constants.SPREAD_BY_SOURCE[it] } ?: 0.25

        fun makeMinMax(value: Double): MinMax = MinMax(
            low = value * (1.0 - spread),
            high = value * (1.0 + spread)
        )

        return RoomQuote(
            roomId = room.id,
            standardMinutes = makeMinMax(standardMinutesPoint),
            expectedMinutes = makeMinMax(expectedMinutesPoint),
            priceInr = makeMinMax(pricePoint)
        )
    }

    fun quoteJob(job: Job): JobQuote {
        if (job.rooms.isEmpty()) {
            val zero = MinMax(0.0, 0.0)
            return JobQuote(
                rooms = emptyList(),
                totalStandardMinutes = zero,
                totalExpectedMinutes = zero,
                totalPriceInr = zero
            )
        }

        val roomQuotes = job.rooms.map { quoteRoom(it, job) }

        val totalStandardMinutes = MinMax(
            low = roomQuotes.sumOf { it.standardMinutes.low },
            high = roomQuotes.sumOf { it.standardMinutes.high }
        )

        val totalExpectedMinutes = MinMax(
            low = roomQuotes.sumOf { it.expectedMinutes.low },
            high = roomQuotes.sumOf { it.expectedMinutes.high }
        )

        val totalPriceInr = MinMax(
            low = roomQuotes.sumOf { it.priceInr.low },
            high = roomQuotes.sumOf { it.priceInr.high }
        )

        return JobQuote(
            rooms = roomQuotes,
            totalStandardMinutes = totalStandardMinutes,
            totalExpectedMinutes = totalExpectedMinutes,
            totalPriceInr = totalPriceInr
        )
    }
}
