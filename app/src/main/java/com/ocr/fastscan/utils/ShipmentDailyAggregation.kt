package com.ocr.fastscan.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ShipmentDailyDelta(val count: Long, val weight: Double)

internal object ShipmentDailyAggregation {
    private const val TIMEZONE = "Europe/Madrid"

    fun day(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone(TIMEZONE)
    }.format(date)

    fun delta(previousWeight: Double?, newWeight: Double): ShipmentDailyDelta =
        if (previousWeight == null) {
            ShipmentDailyDelta(1, newWeight.coerceAtLeast(0.0))
        } else {
            ShipmentDailyDelta(0, newWeight.coerceAtLeast(0.0) - previousWeight.coerceAtLeast(0.0))
        }
}
