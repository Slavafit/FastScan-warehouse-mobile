package com.ocr.fastscan.ui

import com.ocr.fastscan.data.model.OrderContent
import com.ocr.fastscan.data.model.OrderContentItem
import com.ocr.fastscan.utils.FirebaseManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BoxInventoryFormatterTest {
    @Test
    fun aggregatesSameProductAcrossOrdersWithoutOrderNumbers() {
        val shoes = OrderContentItem(
            productId = "shoes",
            reportNameEn = "Shoes",
            quantity = 2,
            unitSingular = "pair",
            unitPlural = "pairs"
        )
        val report = BoxInventoryFormatter.createReport(
            partyGuid = "party-guid",
            partyName = "152",
            boxTracking = "BMG000123",
            contents = listOf(
                OrderContent(tracking = "TRACK-1", items = listOf(shoes)),
                OrderContent(tracking = "TRACK-2", items = listOf(shoes.copy(quantity = 1)))
            ),
            createdAt = Date(0)
        )

        assertEquals(1, report.items.size)
        assertEquals(3, report.items.single().quantity)
        assertTrue(report.reportText.contains("Shoes — 3 pairs"))
        assertFalse(report.reportText.contains("TRACK-1"))
        assertFalse(report.reportText.contains("TRACK-2"))
    }

    @Test
    fun usesSingularUnitForOneItem() {
        val report = BoxInventoryFormatter.createReport(
            partyGuid = "party-guid",
            partyName = "152",
            boxTracking = "BOX-1",
            contents = listOf(
                OrderContent(
                    tracking = "TRACK",
                    items = listOf(
                        OrderContentItem(
                            productId = "cosmetics",
                            reportNameEn = "Cosmetics",
                            quantity = 1,
                            unitSingular = "box",
                            unitPlural = "boxes"
                        )
                    )
                )
            ),
            createdAt = Date(0)
        )

        assertTrue(report.reportText.contains("Cosmetics — 1 box"))
    }

    @Test
    fun omitsBatchLineForStandaloneBoxView() {
        val report = BoxInventoryFormatter.createReport(
            partyGuid = "",
            partyName = "",
            boxTracking = "BOX-1",
            contents = emptyList(),
            createdAt = Date(0)
        )

        assertTrue(report.reportText.startsWith("BOX BOX-1\n"))
        assertFalse(report.reportText.contains("BATCH"))
    }

    @Test
    fun trackingComparisonHandlesScannerFormatting() {
        assertEquals(
            FirebaseManager.comparableTracking("00317 4458-1220"),
            FirebaseManager.comparableTracking("31744581220")
        )
        assertEquals(
            FirebaseManager.comparableTracking(" rr-123 456-es "),
            FirebaseManager.comparableTracking("RR123456ES")
        )
    }

    @Test
    fun combinedReportShowsBatchAndDateOnlyOnce() {
        val first = BoxInventoryFormatter.createReport(
            partyGuid = "party-guid",
            partyName = "157",
            boxTracking = "BOX-1",
            contents = emptyList(),
            createdAt = Date(0)
        )
        val second = first.copy(boxTracking = "BOX-2")

        val combined = BoxInventoryFormatter.formatCombinedReport("157", listOf(first, second))

        assertEquals(1, Regex("BATCH 157").findAll(combined).count())
        assertEquals(
            1,
            Regex("\\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}")
                .findAll(combined)
                .count()
        )
        assertTrue(combined.contains("BOX BOX-1\n\nNo declared contents"))
        assertTrue(combined.contains("BOX BOX-2\n\nNo declared contents"))
    }
}
