package io.github.jpsutton.inkstash.scan

import io.github.jpsutton.inkstash.data.Card

enum class MatchLevel { SURE, CHECK, NONE }

data class MatchResult(
    val level: MatchLevel,
    val card: Card?,
    val confidence: Double,
    /** Other likely cards (e.g. other versions of the same character), best first. */
    val alternates: List<Card>,
    /** True when the card came from the collector number rather than from its name. */
    val byNumber: Boolean,
)

/** Matches a located card against the known-card DB: collector line first, then the printed name. */
class CardMatcher(allCards: List<Card>) {
    private class Entry(val card: Card, val name: String, val version: String)

    private val entries = allCards.map { Entry(it, normalize(it.name), normalize(it.version)) }
    private val bySetNumber = allCards.associateBy { it.setCode.lowercase() to it.collectorNumber }

    fun match(candidate: CardCandidate): MatchResult {
        val footer = candidate.footer
        val names = candidate.nameCandidates.map(::normalize).filter { it.length >= 3 }

        var knownSet: String? = null
        if (footer != null) {
            val exact = lookup(footer)
            if (exact != null) {
                val agree = if (names.isEmpty()) null else nameScore(names, exact)
                // A footer read that the printed name contradicts is probably a misread: flag it.
                val level = if (agree != null && agree < 0.4) MatchLevel.CHECK else MatchLevel.SURE
                return MatchResult(level, exact, if (level == MatchLevel.SURE) 0.97 else 0.6, sameName(exact), byNumber = true)
            }
            knownSet = footer.setCode.lowercase().takeIf { s -> entries.any { it.card.setCode.lowercase() == s } }
        }

        if (names.isEmpty()) return NONE
        val pool = if (knownSet != null) entries.filter { it.card.setCode.lowercase() == knownSet } else entries
        val scored = pool.map { it to scoreEntry(names, it) }.filter { it.second.first >= NAME_THRESHOLD }
            .sortedByDescending { it.second.second }
        val best = scored.firstOrNull() ?: return NONE
        val alts = scored.drop(1).map { it.first.card }.take(3)
            .ifEmpty { sameName(best.first.card) }
        return MatchResult(MatchLevel.CHECK, best.first.card, best.second.second, alts, byNumber = false)
    }

    private fun lookup(f: FooterInfo): Card? =
        bySetNumber[f.setCode.lowercase() to f.collectorNumber]
            ?: bySetNumber[f.setCode.lowercase() to f.collectorNumber.trimEnd { it.isLetter() }]

    private fun sameName(card: Card): List<Card> =
        entries.filter { it.card.name == card.name && it.card.id != card.id }.map { it.card }.take(3)

    private fun nameScore(names: List<String>, card: Card): Double =
        scoreEntry(names, entries.first { it.card.id == card.id }).first

    /** Returns (name similarity, combined score with version). */
    private fun scoreEntry(names: List<String>, e: Entry): Pair<Double, Double> {
        val nameSim = names.maxOf { similarity(it, e.name) }
        if (e.version.isEmpty()) return nameSim to nameSim
        val versionSim = names.maxOf { similarity(it, e.version) }
        return nameSim to (0.6 * nameSim + 0.4 * versionSim)
    }

    companion object {
        private const val NAME_THRESHOLD = 0.8
        private val NONE = MatchResult(MatchLevel.NONE, null, 0.0, emptyList(), byNumber = false)

        fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        /** 1 - Levenshtein distance / longer length, on normalized strings. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            val longer = maxOf(a.length, b.length)
            if (kotlin.math.abs(a.length - b.length).toDouble() / longer > 0.4) return 0.0
            return 1.0 - levenshtein(a, b).toDouble() / longer
        }

        private fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
