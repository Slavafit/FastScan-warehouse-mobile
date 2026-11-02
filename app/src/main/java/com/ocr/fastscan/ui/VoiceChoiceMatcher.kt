package com.ocr.fastscan.ui

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

internal object VoiceChoiceMatcher {
    fun bestMatches(
        spokenCandidates: Collection<String>,
        aliasesById: Map<String, Collection<String>>
    ): List<String> {
        val candidates = spokenCandidates.map(::normalize).filter(String::isNotBlank)
        if (candidates.isEmpty()) return emptyList()

        val ranked = aliasesById.mapNotNull { (id, aliases) ->
            val score = candidates.maxOfOrNull { candidate ->
                aliases.maxOfOrNull { alias -> score(candidate, normalize(alias)) } ?: NO_MATCH
            } ?: NO_MATCH
            if (score == NO_MATCH) null else id to score
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

        val bestScore = ranked.firstOrNull()?.second ?: return emptyList()
        return ranked
            .takeWhile { (_, score) -> score >= bestScore - AMBIGUITY_RANGE }
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    private fun score(candidate: String, alias: String): Int {
        if (candidate.isBlank() || alias.isBlank()) return NO_MATCH
        if (candidate == alias) return 1000
        if (candidate.contains(alias)) return 900
        if (candidate.length >= 3 && alias.contains(candidate)) return 800

        val candidateParts = (candidate.split(' ') + candidate).filter { it.length >= 3 }
        val distance = candidateParts.minOfOrNull { levenshtein(it, alias) } ?: return NO_MATCH
        val allowedDistance = max(1, alias.length / 3)
        return if (distance <= allowedDistance) 600 - distance * 20 else NO_MATCH
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private const val NO_MATCH = Int.MIN_VALUE
    private const val AMBIGUITY_RANGE = 20
    private const val MAX_SUGGESTIONS = 3
}
