package com.ocr.fastscan.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Date

class ShipmentDailyAggregationTest {
    @Test
    fun `new order increments count and full weight`() {
        assertEquals(ShipmentDailyDelta(1, 4.25), ShipmentDailyAggregation.delta(null, 4.25))
    }

    @Test
    fun `repeated tracking with same weight changes nothing`() {
        assertEquals(ShipmentDailyDelta(0, 0.0), ShipmentDailyAggregation.delta(4.25, 4.25))
    }

    @Test
    fun `existing tracking applies only weight difference`() {
        assertEquals(ShipmentDailyDelta(0, 1.75), ShipmentDailyAggregation.delta(4.25, 6.0))
    }

    @Test
    fun `Madrid day follows local midnight across DST`() {
        assertEquals(
            "2026-03-30",
            ShipmentDailyAggregation.day(Date.from(Instant.parse("2026-03-29T22:30:00Z")))
        )
        assertEquals(
            "2026-10-25",
            ShipmentDailyAggregation.day(Date.from(Instant.parse("2026-10-25T22:30:00Z")))
        )
    }
}
