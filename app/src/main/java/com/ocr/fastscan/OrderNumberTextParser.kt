package com.ocr.fastscan

import java.util.Locale

internal object OrderNumberTextParser {
    private val marker = Regex("ORDER\\s*(?:NUM(?:BER)?|NO)\\.?", RegexOption.IGNORE_CASE)
    private val substitutions = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'L' to '1', 'Z' to '2',
        'S' to '5', 'G' to '6', 'B' to '8'
    )

    fun parse(text: String): String? {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        lines.forEachIndexed { index, line ->
            val match = marker.find(line) ?: return@forEachIndexed
            extractNumber(line.substring(match.range.last + 1))?.let { return it }
            lines.getOrNull(index + 1)?.let(::extractNumber)?.let { return it }
        }
        return null
    }

    private fun extractNumber(value: String): String? {
        val normalized = value.uppercase(Locale.ROOT)
        val candidate = Regex("(?<![0-9OQDILZSBG])[0-9OQDILZSBG]{6,20}(?![0-9OQDILZSBG])")
            .find(normalized)
            ?.value
            ?: return null
        return buildString {
            candidate.forEach { character ->
                when {
                    character.isDigit() -> append(character.digitToInt())
                    character in substitutions -> append(substitutions.getValue(character))
                }
            }
        }
    }
}
