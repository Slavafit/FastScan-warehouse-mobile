package com.ocr.fastscan

import com.google.gson.Gson
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.data.model.SaveOrderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationDescriptionTest {
    @Test
    fun `serializes recognized order number into stock comment`() {
        val tracking = "3999285063451300200030"
        val orderNumber = "90086957604"
        val request = SaveOrderRequest(
            clientCode = "KG1010",
            clientName = "Client",
            trackingNumber = tracking,
            grossWeight = 1.5,
            withBox = 0,
            recieverGuid = "receiver",
            userGuid = "receiver",
            length = 0,
            width = 0,
            height = 0,
            stockComment = RegistrationDescription.resolve(orderNumber)
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"tracking_number\":\"$tracking\""))
        assertTrue(json.contains("\"stock_comment\":\"Номер заказа: $orderNumber\""))
        assertTrue(!json.contains("\"comment\""))
        assertTrue(!json.contains("\"description\""))
    }

    @Test
    fun `leaves comment null when order number was not recognized`() {
        assertNull(RegistrationDescription.resolve(null))
    }

    @Test
    fun `extracts order number from stock comment`() {
        assertEquals(
            "90086957604",
            RegistrationDescription.extractOrderNumber("Номер заказа: 90086957604")
        )
    }

    @Test
    fun `reads stock comment from order details response`() {
        val details = Gson().fromJson(
            """{"tracking_number":"36538641827300100032","stock_comment":"Номер заказа: 90086957604"}""",
            OrderDetails::class.java
        )

        assertEquals("Номер заказа: 90086957604", details.stockComment)
        assertEquals("90086957604", RegistrationDescription.extractOrderNumber(details.stockComment))
    }
}
