package com.ocr.fastscan.ui

import java.util.Locale

internal object ClientCodeVoiceParser {
    private const val MIN_DIGITS = 4

    private val digitWords = mapOf(
        "ноль" to '0', "нуль" to '0',
        "один" to '1', "одна" to '1',
        "два" to '2', "две" to '2', "три" to '3', "четыре" to '4',
        "пять" to '5', "шесть" to '6', "семь" to '7', "восемь" to '8', "девять" to '9',
        "zero" to '0', "oh" to '0', "o" to '0',
        "one" to '1', "two" to '2', "three" to '3', "four" to '4',
        "five" to '5', "six" to '6', "seven" to '7', "eight" to '8', "nine" to '9',
        "cero" to '0', "uno" to '1', "una" to '1', "dos" to '2', "tres" to '3',
        "cuatro" to '4', "cinco" to '5', "seis" to '6', "siete" to '7',
        "ocho" to '8', "nueve" to '9'
    )

    private val ignoredWords = setOf(
        "kg", "кг", "code", "код", "client", "клиента", "codigo", "código", "cliente"
    )

    fun parse(candidates: List<String>): String? = candidates
        .asSequence()
        .mapNotNull(::parseCandidate)
        .firstOrNull()

    private fun parseCandidate(candidate: String): String? {
        val normalized = candidate.lowercase(Locale.ROOT).replace('ё', 'е')
        val tokens = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        val digits = buildString {
            for (token in tokens) {
                when {
                    token.all(Char::isDigit) -> token.forEach { append(it.digitToInt()) }
                    token in digitWords -> append(digitWords.getValue(token))
                    token in ignoredWords -> Unit
                    else -> return null
                }
            }
        }
        return digits.takeIf { it.length >= MIN_DIGITS }?.let { "KG$it" }
    }
}
