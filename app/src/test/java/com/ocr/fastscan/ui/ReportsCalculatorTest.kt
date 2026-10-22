package com.ocr.fastscan.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

class ReportsCalculatorTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val timeZone = TimeZone.getTimeZone("Europe/Paris")

    @Test
    fun `monthly report groups weights and separates packed orders`() {
        val shipments = listOf(
            shipment("TRACK-1", "KG100", 10.5, 2026, 10, 1),
            shipment("TRACK-2", "KG100", 4.5, 2026, 10, 31),
            shipment("TRACK-3", "KG200", 7.0, 2026, 9, 15)
        )

        val result = ReportsCalculator.monthlyWeight(
            shipments,
            packedTrackings = setOf(" track-1 ", "TRACK-3"),
            year = 2026,
            clientCodeFilter = "",
            timeZone = timeZone
        )

        assertEquals(listOf(10, 9), result.map { it.month })
        assertEquals(15.0, result[0].totalWeight, 0.001)
        assertEquals(10.5, result[0].packedWeight, 0.001)
        assertEquals(4.5, result[0].unpackedWeight, 0.001)
    }

    @Test
    fun `duplicate tracking is counted once using newest record`() {
        val shipments = listOf(
            shipment("TRACK-1", "KG100", 3.0, 2026, 9, 1),
            shipment(" track-1 ", "KG100", 8.0, 2026, 10, 1)
        )

        val result = ReportsCalculator.monthlyWeight(
            shipments,
            packedTrackings = emptySet(),
            year = 2026,
            clientCodeFilter = "",
            timeZone = timeZone
        )

        assertEquals(1, result.single().orderCount)
        assertEquals(10, result.single().month)
        assertEquals(8.0, result.single().totalWeight, 0.001)
    }

    @Test
    fun `year boundary and client filter are respected`() {
        val shipments = listOf(
            shipment("A", "kg100", 5.0, 2025, 12, 31),
            shipment("B", "KG100", 6.0, 2026, 1, 1),
            shipment("C", "KG200", 9.0, 2026, 1, 2)
        )

        val result = ReportsCalculator.monthlyWeight(
            shipments,
            packedTrackings = emptySet(),
            year = 2026,
            clientCodeFilter = " kg100 ",
            timeZone = timeZone
        )

        assertEquals(1, result.size)
        assertEquals(1, result.single().month)
        assertEquals(6.0, result.single().totalWeight, 0.001)
    }

    @Test
    fun `negative weight does not reduce monthly total`() {
        val result = ReportsCalculator.monthlyWeight(
            listOf(shipment("A", "KG100", -2.0, 2026, 5, 1)),
            packedTrackings = emptySet(),
            year = 2026,
            clientCodeFilter = "",
            timeZone = timeZone
        )

        assertEquals(0.0, result.single().totalWeight, 0.001)
    }

    private fun shipment(
        tracking: String,
        clientCode: String,
        weight: Double,
        year: Int,
        month: Int,
        day: Int
    ) = ReportShipmentRecord(
        tracking = tracking,
        clientCode = clientCode,
        weight = weight,
        registeredAt = Date.from(
            ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant()
        )
    )
}
