package com.ocr.fastscan.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class WarehousePrintIdentityResolverTest {

    @Test
    fun `Spain identity is used for ES and as fallback`() {
        assertEquals(
            WarehousePrintIdentity("Onza España", "Packed in Malaga"),
            WarehousePrintIdentityResolver.resolve("ES")
        )
        assertEquals(
            WarehousePrintIdentity("Onza España", "Packed in Malaga"),
            WarehousePrintIdentityResolver.resolve("")
        )
    }

    @Test
    fun `USA identity is selected case insensitively`() {
        assertEquals(
            WarehousePrintIdentity("Onza USA", "Packed in USA"),
            WarehousePrintIdentityResolver.resolve("us")
        )
    }
}
