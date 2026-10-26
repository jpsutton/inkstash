package io.github.jpsutton.inkstash.scan

/**
 * Finds cards by their printed name when the collector footer can't be read. The name is the biggest
 * text on a card, so it survives photos where the footer (about 10 px tall) does not.
 *
 * A line that matches a known card name anchors one card. The card's outline comes from where the
 * name sits (left edge and just over half way down the card) and how tall the text is, or from the
 * grid pitch when several cards line up.
 */
class NameAnchorLocator(cardNames: Collection<String>) {
    private val names: List<String> = cardNames.map(CardMatcher::normalize).filter { it.length >= MIN_LENGTH }.distinct()

    private class Anchor(val line: OcrLine, val version: OcrLine?)

    /** Cards named in [lines], skipping any whose name line already lies inside one of [taken]. */
    fun locate(lines: List<OcrLine>, taken: List<Rect>, imageWidth: Int, imageHeight: Int): List<CardCandidate> {
        val anchors = lines.filter { l -> taken.none { it.contains(l.centerX, l.centerY) } }
            .filter { isName(it.text) }
            .map { Anchor(it, versionBelow(it, lines)) }
        if (anchors.isEmpty()) return emptyList()

        val textHeight = anchors.map { it.line.height }.sorted()[anchors.size / 2].coerceAtLeast(1)
        var cardH = textHeight * HEIGHT_PER_NAME
        var cardW = cardH * CardLocator.ASPECT
        CardLocator.pitch(anchors.map { it.line.left }, cardW)?.let { cardW = minOf(it * 0.96f, cardW * 1.4f) }
        CardLocator.pitch(anchors.map { it.line.top }, cardH)?.let { cardH = minOf(it * 0.96f, cardH * 1.4f) }

        val rects = anchors.map { a ->
            val left = a.line.left - NAME_INSET_X * cardW
            val top = a.line.top - NAME_TOP * cardH
            Rect(
                left.toInt().coerceAtLeast(0), top.toInt().coerceAtLeast(0),
                (left + cardW).toInt().coerceAtMost(imageWidth), (top + cardH).toInt().coerceAtMost(imageHeight),
            )
        }
        val used = anchors.flatMap { listOfNotNull(it.line, it.version) }.toSet()
        return anchors.indices.map { i ->
            val a = anchors[i]
            val extra = lines.filter { it !in used && rects[i].contains(it.centerX, it.centerY) && it.text.count(Char::isLetter) >= 3 }
                .sortedByDescending { it.height }.take(3).map { it.text.trim() }
            CardCandidate(null, rects[i], listOfNotNull(a.line.text.trim(), a.version?.text?.trim()) + extra)
        }
    }

    private fun isName(text: String): Boolean {
        val n = CardMatcher.normalize(text)
        if (n.length < MIN_LENGTH) return false
        return names.any { CardMatcher.similarity(n, it) >= NAME_SIMILARITY }
    }

    /** The version sits right under the name, in smaller text, starting at about the same left edge. */
    private fun versionBelow(name: OcrLine, lines: List<OcrLine>): OcrLine? = lines.filter {
        it !== name && it.top >= name.centerY && it.top - name.bottom < name.height * 1.2f &&
            kotlin.math.abs(it.left - name.left) < name.height * 2f && it.height <= name.height * 1.1f
    }.minByOrNull { it.top }

    companion object {
        private const val MIN_LENGTH = 4
        private const val NAME_SIMILARITY = 0.85
        /** Card height / name text height. Tuned on a photo of Pluto - Rescue Dog (700 px card, 28 px name). */
        const val HEIGHT_PER_NAME = 25f
        private const val NAME_INSET_X = 0.065f // name's left edge, as a fraction of card width from the card's left
        private const val NAME_TOP = 0.53f // name's top, as a fraction of card height from the card's top
    }
}
