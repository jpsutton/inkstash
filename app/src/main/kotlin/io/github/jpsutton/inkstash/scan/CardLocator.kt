package io.github.jpsutton.inkstash.scan

/** One recognized line of text with its box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}

/** One card found in the photo: its footer, estimated outline and the other text inside it. */
data class CardCandidate(val footer: FooterInfo?, val rect: Rect, val nameCandidates: List<String>)

/**
 * Each footer line anchors one card. The footer sits at the card's bottom-left, so the card's outline
 * comes from the footer's position plus a card size. The size is the grid pitch when the photo has
 * several cards in a row/column, else it is estimated from the footer's text height.
 */
object CardLocator {
    /** Card height / footer line height. From a real photo (Pluto - Rescue Dog): 700 px card, ~12 px footer line. */
    const val HEIGHT_PER_TEXT = 55f
    const val ASPECT = 63f / 88f // width / height of a Lorcana card
    private const val FOOTER_INSET_X = 0.06f // footer left edge, as a fraction of card width from the card's left
    private const val FOOTER_INSET_Y = 0.035f // footer bottom, as a fraction of card height from the card's bottom

    private class Anchor(val line: OcrLine, val info: FooterInfo)

    fun locate(lines: List<OcrLine>, imageWidth: Int, imageHeight: Int): List<CardCandidate> {
        val parsed = lines.mapNotNull { l -> CollectorLineParser.parse(l.text)?.let { Anchor(l, it) } }
        if (parsed.isEmpty()) return emptyList()
        val anchors = dedupe(parsed)

        val textHeight = anchors.map { it.line.height }.sorted()[anchors.size / 2].coerceAtLeast(1)
        var cardH = textHeight * HEIGHT_PER_TEXT
        var cardW = cardH * ASPECT
        pitch(anchors.map { it.line.left }, cardW)?.let { cardW = minOf(it * 0.96f, cardW * 1.4f) }
        pitch(anchors.map { it.line.bottom }, cardH)?.let { cardH = minOf(it * 0.96f, cardH * 1.4f) }

        val rects = anchors.map { a ->
            val left = a.line.left - FOOTER_INSET_X * cardW
            val bottom = a.line.bottom + FOOTER_INSET_Y * cardH
            Rect(
                left.toInt().coerceAtLeast(0),
                (bottom - cardH).toInt().coerceAtLeast(0),
                (left + cardW).toInt().coerceAtMost(imageWidth),
                bottom.toInt().coerceAtMost(imageHeight),
            )
        }
        val anchorLines = anchors.map { it.line }.toSet()
        return anchors.indices
            .sortedWith(compareBy({ rects[it].top / (cardH.toInt().coerceAtLeast(1)) }, { rects[it].left }))
            .map { i ->
                val rect = rects[i]
                val names = lines.filter { it !in anchorLines && rect.contains(it.centerX, it.centerY) }
                    .filter { l -> l.text.count { it.isLetter() } >= 3 }
                    .sortedByDescending { it.height }
                    .map { it.text.trim() }
                CardCandidate(anchors[i].info, rect, names)
            }
    }

    /**
     * The same footer read twice (whole photo and an enlarged tile, or one footer cut in two) gives
     * two anchors a few pixels apart. Keep the first of any pair with the same number and set that
     * lie within a card's width of each other.
     */
    private fun dedupe(anchors: List<Anchor>): List<Anchor> {
        val kept = mutableListOf<Anchor>()
        for (a in anchors) {
            val reach = a.line.height * HEIGHT_PER_TEXT * ASPECT
            val twin = kept.any {
                it.info.collectorNumber == a.info.collectorNumber && it.info.setCode == a.info.setCode &&
                    kotlin.math.abs(it.line.left - a.line.left) < reach && kotlin.math.abs(it.line.top - a.line.top) < reach
            }
            if (!twin) kept += a
        }
        return kept
    }

    /**
     * Smallest gap between distinct positions that fits the expected card size (within 0.6x–1.6x),
     * or null when all positions are in one row/column. Positions of one column differ by a few
     * pixels, so those are grouped first.
     */
    internal fun pitch(positions: List<Int>, expected: Float): Float? {
        val sorted = positions.sorted()
        val groups = mutableListOf(mutableListOf(sorted.first()))
        for (p in sorted.drop(1)) {
            if (p - groups.last().last() < expected * 0.3f) groups.last().add(p) else groups.add(mutableListOf(p))
        }
        if (groups.size < 2) return null
        val centers = groups.map { it.average() }
        val gaps = centers.zipWithNext { a, b -> (b - a).toFloat() }.filter { it in expected * 0.6f..expected * 1.6f }
        return gaps.minOrNull()
    }
}
