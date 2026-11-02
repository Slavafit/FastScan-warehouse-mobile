package com.ocr.fastscan.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceChoiceMatcherTest {
    @Test
    fun `matches inflected Russian category`() {
        val result = VoiceChoiceMatcher.bestMatches(
            listOf("выбрать одежду"),
            mapOf("clothing" to listOf("одежда"), "footwear" to listOf("обувь"))
        )

        assertEquals(listOf("clothing"), result)
    }

    @Test
    fun `matches Spanish name without accents`() {
        val result = VoiceChoiceMatcher.bestMatches(
            listOf("electronica"),
            mapOf("electronics" to listOf("Electrónica"), "food" to listOf("Alimentos"))
        )

        assertEquals(listOf("electronics"), result)
    }

    @Test
    fun `matches custom item inside a phrase`() {
        val result = VoiceChoiceMatcher.bestMatches(
            listOf("добавить пальто"),
            mapOf("coat" to listOf("Пальто", "Coat"), "hat" to listOf("Шапка", "Hat"))
        )

        assertEquals(listOf("coat"), result)
    }
}
