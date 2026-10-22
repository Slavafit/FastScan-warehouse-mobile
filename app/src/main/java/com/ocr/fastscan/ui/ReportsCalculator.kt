package com.ocr.fastscan.ui

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class ReportShipmentRecord(
    val tracking: String,
    val clientCode: String,
    val weight: Double,
    val registeredAt: Date
)

internal data class MonthlyWeightSummary(
    val month: Int,
    val orderCount: Int,
    val totalWeight: Double,
    val packedWeight: Double,
    val unpackedWeight: Double
)

internal object ReportsCalculator {
    fun monthlyWeight(
        shipments: List<ReportShipmentRecord>,
        packedTrackings: Set<String>,
        year: Int,
        clientCodeFilter: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<MonthlyWeightSummary> {
        val normalizedClientFilter = normalize(clientCodeFilter)
        val normalizedPackedTrackings = packedTrackings.mapTo(mutableSetOf(), ::normalize)
        val uniqueShipments = shipments
            .asSequence()
            .filter { it.tracking.isNotBlank() }
            .filter {
                normalizedClientFilter.isBlank() || normalize(it.clientCode) == normalizedClientFilter
            }
            .sortedByDescending { it.registeredAt.time }
            .distinctBy { normalize(it.tracking) }
            .filter { calendarField(it.registeredAt, Calendar.YEAR, timeZone) == year }
            .toList()

        return uniqueShipments
            .groupBy { calendarField(it.registeredAt, Calendar.MONTH, timeZone) + 1 }
            .map { (month, monthShipments) ->
                val packed = monthShipments.filter {
                    normalize(it.tracking) in normalizedPackedTrackings
                }
                val unpacked = monthShipments.filterNot {
                    normalize(it.tracking) in normalizedPackedTrackings
                }
                MonthlyWeightSummary(
                    month = month,
                    orderCount = monthShipments.size,
                    totalWeight = monthShipments.sumOf { it.weight.coerceAtLeast(0.0) },
                    packedWeight = packed.sumOf { it.weight.coerceAtLeast(0.0) },
                    unpackedWeight = unpacked.sumOf { it.weight.coerceAtLeast(0.0) }
                )
            }
            .sortedByDescending { it.month }
    }

    fun normalize(value: String): String = value.trim().uppercase(Locale.ROOT)

    private fun calendarField(date: Date, field: Int, timeZone: TimeZone): Int =
        Calendar.getInstance(timeZone).run {
            time = date
            get(field)
        }
}
