package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap

/**
 * Reads a photo twice over: once whole, then in overlapping tiles enlarged until the text is big
 * enough for the recognizer. The footer line of a card (the collector number) is only ~10 px tall in
 * a photo from a cheap tablet camera, which ML Kit misses at that size.
 *
 * The enlarged pass adds lines; where it overlaps a line from the whole pass, a collector footer
 * from the enlarged pass wins (it is usually the cleaner read), anything else is dropped.
 */
class TiledTextReader(
    private val reader: TextReader,
    private val grid: Int = 2,
    /** The long side tiles are enlarged to. */
    private val targetSide: Int = 2000,
    private val maxScale: Float = 3f,
) : TextReader {
    override suspend fun read(bitmap: Bitmap): List<OcrLine> {
        val whole = reader.read(bitmap)
        val tw = bitmap.width / grid
        val th = bitmap.height / grid
        val scale = (targetSide.toFloat() / maxOf(tw, th)).coerceAtMost(maxScale)
        if (scale < 1.3f) {
            ScanLog.i("tiles skipped: image ${bitmap.width}x${bitmap.height} is already big enough")
            return whole
        }
        // 20% overlap so a footer cut by one tile edge sits whole in the next.
        val padX = (tw * 0.2f).toInt()
        val padY = (th * 0.2f).toInt()
        val extra = mutableListOf<OcrLine>()
        for (row in 0 until grid) for (col in 0 until grid) {
            val x0 = (col * tw - padX).coerceAtLeast(0)
            val y0 = (row * th - padY).coerceAtLeast(0)
            val x1 = ((col + 1) * tw + padX).coerceAtMost(bitmap.width)
            val y1 = ((row + 1) * th + padY).coerceAtMost(bitmap.height)
            val tile = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
            val big = Bitmap.createScaledBitmap(tile, (tile.width * scale).toInt(), (tile.height * scale).toInt(), true)
            try {
                for (l in reader.read(big)) {
                    extra += OcrLine(
                        l.text,
                        x0 + (l.left / scale).toInt(), y0 + (l.top / scale).toInt(),
                        x0 + (l.right / scale).toInt(), y0 + (l.bottom / scale).toInt(),
                    )
                }
            } finally {
                if (big !== tile) big.recycle()
                tile.recycle()
            }
        }
        val merged = merge(whole, extra)
        ScanLog.i("ocr: ${whole.size} lines whole + ${extra.size} from tiles (x${"%.1f".format(scale)}) -> ${merged.size}")
        return merged
    }

    companion object {
        fun merge(whole: List<OcrLine>, extra: List<OcrLine>): List<OcrLine> {
            val out = whole.toMutableList()
            for (e in extra) {
                val clash = out.indexOfFirst { overlap(it, e) > 0.3f }
                if (clash < 0) {
                    // A tile edge can repeat a line that is cut in two there; skip same-text neighbours.
                    if (out.none { it.text == e.text && overlap(it, e) > 0f }) out += e
                } else if (CollectorLineParser.parse(e.text) != null && CollectorLineParser.parse(out[clash].text) == null) {
                    out[clash] = e
                }
            }
            return out
        }

        /** Intersection over union of two line boxes. */
        fun overlap(a: OcrLine, b: OcrLine): Float {
            val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (w <= 0 || h <= 0) return 0f
            val inter = w.toFloat() * h
            val union = (a.right - a.left).toFloat() * a.height + (b.right - b.left).toFloat() * b.height - inter
            return inter / union
        }
    }
}
