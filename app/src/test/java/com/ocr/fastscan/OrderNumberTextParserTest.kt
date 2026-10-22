package com.ocr.fastscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderNumberTextParserTest {
    @Test
    fun `reads number after order num on same line`() {
        assertEquals("90086957604", OrderNumberTextParser.parse("Order num. 90086957604"))
    }

    @Test
    fun `reads number from line following marker`() {
        assertEquals("90086957604", OrderNumberTextParser.parse("ORDER NUMBER\n90086957604"))
    }

    @Test
    fun `corrects common OCR substitutions only in order number`() {
        assertEquals("90086957604", OrderNumberTextParser.parse("Order num. 9OO869576O4"))
    }

    @Test
    fun `ignores unrelated barcode digits without marker`() {
        assertNull(OrderNumberTextParser.parse("3999285063451300200030"))
    }

    @Test
    fun `takes order number instead of barcode printed on next line`() {
        assertEquals(
            "90086957604",
            OrderNumberTextParser.parse("Order num. 90086957604\n3999285063451300200030")
        )
    }

    @Test
    fun `rejects implausibly short order number`() {
        assertNull(OrderNumberTextParser.parse("Order num. 1234"))
    }
}
