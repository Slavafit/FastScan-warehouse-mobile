package com.ocr.fastscan.ui

import com.google.firebase.Timestamp
import com.ocr.fastscan.data.model.BoxInventoryItem
import com.ocr.fastscan.data.model.BoxInventoryReport
import com.ocr.fastscan.data.model.OrderContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object BoxInventoryFormatter {
    fun createReport(
        partyGuid: String,
        partyName: String,
        boxTracking: String,
        contents: List<OrderContent>,
        createdAt: Date = Date()
    ): BoxInventoryReport {
        val aggregated = contents
            .flatMap { it.items }
            .filter { it.quantity > 0 && it.reportNameEn.isNotBlank() }
            .groupBy {
                listOf(
                    it.productId.ifBlank { it.reportNameEn.trim().lowercase(Locale.ENGLISH) },
                    it.unitSingular,
                    it.unitPlural
                ).joinToString("|")
            }
            .map { (_, grouped) ->
                val first = grouped.first()
                BoxInventoryItem(
                    productId = first.productId,
                    reportNameEn = first.reportNameEn.trim(),
                    quantity = grouped.sumOf { it.quantity },
                    unitSingular = first.unitSingular,
                    unitPlural = first.unitPlural
                )
            }
            .sortedBy { it.reportNameEn.lowercase(Locale.ENGLISH) }

        val reportText = buildString {
            if (partyName.isNotBlank()) {
                append("BATCH ").append(partyName).append('\n')
            }
            append("BOX ").append(boxTracking).append('\n')
            append(SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).format(createdAt))
            append("\n\n")
            if (aggregated.isEmpty()) {
                append("No declared contents\n")
            } else {
                aggregated.forEach { item ->
                    val unit = if (item.quantity == 1) item.unitSingular else item.unitPlural
                    append(item.reportNameEn)
                        .append(" — ")
                        .append(item.quantity)
                        .append(' ')
                        .append(unit)
                        .append('\n')
                }
            }
        }.trimEnd()

        return BoxInventoryReport(
            partyGuid = partyGuid,
            partyName = partyName,
            boxTracking = boxTracking,
            items = aggregated,
            reportText = reportText,
            createdAt = Timestamp(createdAt)
        )
    }
}
