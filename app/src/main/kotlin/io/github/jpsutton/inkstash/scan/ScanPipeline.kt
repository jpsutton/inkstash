package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap

/** One row of the review screen: a card found in the photo and what it matched. */
data class ScanRow(val candidate: CardCandidate, val result: MatchResult)

enum class ScanMode {
    /** One card fills the frame. Always gives exactly one row, even when nothing is read. */
    SINGLE,

    /** A grid of cards. Gives one row per card found, or none. */
    GRID,
}

/** photo -> OCR -> locate cards (by footer, else by name) -> match each against the card DB. */
class ScanPipeline(
    private val reader: TextReader,
    private val matcher: CardMatcher,
    private val nameLocator: NameAnchorLocator,
) {
    suspend fun scan(photo: Bitmap, mode: ScanMode): List<ScanRow> {
        val lines = reader.read(photo)
        ScanLog.i("scan $mode: photo ${photo.width}x${photo.height}, ${lines.size} lines")
        for (l in lines) ScanLog.i("  line [${l.left},${l.top} ${l.right - l.left}x${l.height}] '${l.text}'")

        val byFooter = CardLocator.locate(lines, photo.width, photo.height)
        val byName = nameLocator.locate(lines, byFooter.map { it.rect }, photo.width, photo.height)
        ScanLog.i("cards: ${byFooter.size} by footer, ${byName.size} by name")
        var found = readingOrder(byFooter + byName)

        if (mode == ScanMode.SINGLE && found.isEmpty()) found = listOf(wholePhoto(lines, photo))
        val rows = found.map { c -> ScanRow(c, matcher.match(c)).also { logRow(it) } }
        return if (mode == ScanMode.SINGLE) listOf(bestNearCenter(rows, photo)) else rows
    }

    /** A photo with one card and no anchor: every readable big line is a name candidate. */
    private fun wholePhoto(lines: List<OcrLine>, photo: Bitmap): CardCandidate {
        val names = lines.filter { it.text.count(Char::isLetter) >= 3 }.sortedByDescending { it.height }.take(10).map { it.text.trim() }
        return CardCandidate(null, Rect(0, 0, photo.width, photo.height), names)
    }

    /** The match the user most likely meant: sure over check over nothing, then nearest the middle. */
    private fun bestNearCenter(rows: List<ScanRow>, photo: Bitmap): ScanRow {
        fun rank(r: ScanRow) = when (r.result.level) { MatchLevel.SURE -> 2; MatchLevel.CHECK -> 1; MatchLevel.NONE -> 0 }
        fun offCenter(r: ScanRow): Float {
            val cx = (r.candidate.rect.left + r.candidate.rect.right) / 2f
            val cy = (r.candidate.rect.top + r.candidate.rect.bottom) / 2f
            return kotlin.math.hypot((cx - photo.width / 2f) / photo.width, (cy - photo.height / 2f) / photo.height)
        }
        return rows.maxWith(compareBy<ScanRow>({ rank(it) }, { -offCenter(it) }))
    }

    private fun readingOrder(cards: List<CardCandidate>): List<CardCandidate> {
        if (cards.isEmpty()) return cards
        val h = cards.map { it.rect.height }.sorted()[cards.size / 2].coerceAtLeast(1)
        return cards.sortedWith(compareBy({ it.rect.top / h }, { it.rect.left }))
    }

    private fun logRow(r: ScanRow) {
        val f = r.candidate.footer
        ScanLog.i(
            "card [${r.candidate.rect.left},${r.candidate.rect.top} ${r.candidate.rect.width}x${r.candidate.rect.height}] " +
                "footer=${f?.let { "${it.collectorNumber}/${it.total} ${it.setCode}" }} names=${r.candidate.nameCandidates} " +
                "-> ${r.result.level} ${r.result.card?.fullName} (${"%.2f".format(r.result.confidence)})",
        )
    }
}
