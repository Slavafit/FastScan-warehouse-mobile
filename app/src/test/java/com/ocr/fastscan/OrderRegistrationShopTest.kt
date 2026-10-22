package com.ocr.fastscan

import com.google.gson.Gson
import com.ocr.fastscan.data.model.SaveOrderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRegistrationShopTest {
    @Test
    fun `sets Zara for unchanged order number tracking`() {
        assertEquals("Zara", OrderRegistrationShop.resolve("90086957604", "90086957604"))
    }

    @Test
    fun `does not set shop for barcode or manual tracking`() {
        assertNull(OrderRegistrationShop.resolve("90086957604", null))
    }

    @Test
    fun `does not set shop after recognized tracking was edited`() {
        assertNull(OrderRegistrationShop.resolve("90086957605", "90086957604"))
    }

    @Test
    fun `serializes Zara into registration request`() {
        val request = SaveOrderRequest(
            clientCode = "KG1010",
            clientName = "Client",
            trackingNumber = "90086957604",
            grossWeight = 1.5,
            withBox = 0,
            recieverGuid = "receiver",
            userGuid = "receiver",
            length = 0,
            width = 0,
            height = 0,
            shop = "Zara"
        )

        assertTrue(Gson().toJson(request).contains("\"shop\":\"Zara\""))
    }
}
