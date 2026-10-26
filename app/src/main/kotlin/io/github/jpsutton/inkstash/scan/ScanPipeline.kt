package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap

/** One row of the review screen: a card found in the photo and what it matched. */
data class ScanRow(val candidate: CardCandidate, val result: MatchResult)

/** photo -> OCR -> locate cards -> match each against the card DB. */
class ScanPipeline(private val reader: TextReader, private val matcher: CardMatcher) {
    suspend fun scan(photo: Bitmap): List<ScanRow> {
        val lines = reader.read(photo)
        return CardLocator.locate(lines, photo.width, photo.height).map { ScanRow(it, matcher.match(it)) }
    }
}
