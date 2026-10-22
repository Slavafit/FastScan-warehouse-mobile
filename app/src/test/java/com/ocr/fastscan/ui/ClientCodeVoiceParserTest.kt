package com.ocr.fastscan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientCodeVoiceParserTest {
    @Test
    fun `preserves leading zero in recognized digits`() {
        assertEquals("KG01234", ClientCodeVoiceParser.parse(listOf("0 1 2 3 4")))
    }

    @Test
    fun `parses Russian digit words`() {
        assertEquals("KG0123", ClientCodeVoiceParser.parse(listOf("ноль один два три")))
    }

    @Test
    fun `parses English digit words`() {
        assertEquals("KG0123", ClientCodeVoiceParser.parse(listOf("oh one two three")))
    }

    @Test
    fun `parses Spanish digit words`() {
        assertEquals("KG0123", ClientCodeVoiceParser.parse(listOf("cero uno dos tres")))
    }

    @Test
    fun `uses next recognition candidate when first is invalid`() {
        assertEquals("KG1234", ClientCodeVoiceParser.parse(listOf("unknown", "one two three four")))
    }

    @Test
    fun `rejects code shorter than four digits`() {
        assertNull(ClientCodeVoiceParser.parse(listOf("one two three")))
    }
}
