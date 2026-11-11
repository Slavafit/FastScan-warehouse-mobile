package com.ocr.fastscan.ui

import java.util.Locale

object QuantityVoiceParser {
    private const val MAX_QUANTITY = 9999

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "один" to 1, "одна" to 1, "одно" to 1,
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10
    )

    fun parse(value: String): Int? {
        Regex("\\d+").find(value)?.value?.toIntOrNull()?.let {
            return it.coerceIn(1, MAX_QUANTITY)
        }
        return value
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .firstNotNullOfOrNull(numberWords::get)
    }
}
