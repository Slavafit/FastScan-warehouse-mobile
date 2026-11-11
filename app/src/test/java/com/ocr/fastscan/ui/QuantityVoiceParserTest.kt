package com.ocr.fastscan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityVoiceParserTest {

    @Test
    fun `parses Russian quantities through ten`() {
        assertEquals(7, QuantityVoiceParser.parse("семь пар"))
        assertEquals(8, QuantityVoiceParser.parse("восемь упаковок"))
        assertEquals(9, QuantityVoiceParser.parse("девять"))
        assertEquals(10, QuantityVoiceParser.parse("десять штук"))
    }

    @Test
    fun `parses English and Spanish quantities through ten`() {
        assertEquals(7, QuantityVoiceParser.parse("seven pairs"))
        assertEquals(10, QuantityVoiceParser.parse("ten packages"))
        assertEquals(8, QuantityVoiceParser.parse("ocho paquetes"))
        assertEquals(10, QuantityVoiceParser.parse("diez unidades"))
    }

    @Test
    fun `parses spoken quantities from eleven through twenty`() {
        assertEquals(11, QuantityVoiceParser.parse("одиннадцать"))
        assertEquals(17, QuantityVoiceParser.parse("seventeen"))
        assertEquals(11, QuantityVoiceParser.parse("once"))
        assertEquals(16, QuantityVoiceParser.parse("dieciséis"))
        assertEquals(20, QuantityVoiceParser.parse("veinte"))
    }

    @Test
    fun `prefers spoken digits and rejects unrelated speech`() {
        assertEquals(26, QuantityVoiceParser.parse("26 pieces"))
        assertNull(QuantityVoiceParser.parse("muchas unidades"))
    }
}
