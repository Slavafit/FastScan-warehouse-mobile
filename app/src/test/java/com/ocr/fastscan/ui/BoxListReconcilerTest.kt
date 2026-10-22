package com.ocr.fastscan.ui

import com.ocr.fastscan.data.model.BoxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxListReconcilerTest {
    @Test
    fun `optimistic and server boxes with same tracking are merged`() {
        val optimistic = box(
            guid = null,
            codeId = "temporary-id",
            tracking = " NBX102011556894 ",
            grossWeight = 1.0
        )
        val server = box(
            guid = "server-guid",
            codeId = "server-id",
            tracking = "nbx102011556894",
            grossWeight = 0.0
        )

        val result = BoxListReconciler.deduplicateAndPrioritize(
            listOf(optimistic, server),
            preferredTrackingCode = "NBX102011556894"
        )

        assertEquals(1, result.size)
        assertEquals("server-guid", result.single().guid)
        assertTrue(BoxListReconciler.isSameBox(optimistic, server))
    }

    @Test
    fun `new box is placed first without changing remaining order`() {
        val first = box(guid = "1", codeId = "1", tracking = "OLD-1")
        val created = box(guid = "2", codeId = "2", tracking = "NEW-BOX")
        val last = box(guid = "3", codeId = "3", tracking = "OLD-2")

        val result = BoxListReconciler.deduplicateAndPrioritize(
            listOf(first, created, last),
            preferredTrackingCode = " new-box "
        )

        assertEquals(listOf("NEW-BOX", "OLD-1", "OLD-2"), result.map { it.trackingCode })
    }

    @Test
    fun `different boxes are not merged`() {
        val first = box(guid = "1", codeId = "1", tracking = "BOX-1")
        val second = box(guid = "2", codeId = "2", tracking = "BOX-2")

        val result = BoxListReconciler.deduplicateAndPrioritize(
            listOf(first, second),
            preferredTrackingCode = null
        )

        assertEquals(2, result.size)
    }

    private fun box(
        guid: String?,
        codeId: String?,
        tracking: String,
        grossWeight: Double = 0.0
    ) = BoxItem(
        guid = guid,
        codeid = codeId,
        trackingCode = tracking,
        warehouse = null,
        warehouseGuid = null,
        width = 70,
        height = 50,
        length = 45,
        vWeight = 26.25,
        grossWeight = grossWeight,
        dateCreated = "2026-10-20T11:55:00",
        orderCount = 0
    )
}
