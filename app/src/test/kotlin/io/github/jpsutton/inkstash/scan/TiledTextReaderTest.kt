package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TiledTextReaderTest {
    private fun line(text: String, l: Int, t: Int, r: Int, b: Int) = OcrLine(text, l, t, r, b)

    @Test fun overlapIsIntersectionOverUnion() {
        assertEquals(1f, TiledTextReader.overlap(line("a", 0, 0, 10, 10), line("b", 0, 0, 10, 10)), 1e-6f)
        assertEquals(0f, TiledTextReader.overlap(line("a", 0, 0, 10, 10), line("b", 20, 0, 30, 10)), 0f)
        assertEquals(1f / 3f, TiledTextReader.overlap(line("a", 0, 0, 10, 10), line("b", 5, 0, 15, 10)), 1e-6f)
    }

    @Test fun mergeKeepsNewLinesAndDropsRepeats() {
        val whole = listOf(line("Elsa", 0, 0, 100, 30))
        val extra = listOf(line("Elsa", 2, 1, 101, 31), line("Moana", 0, 100, 100, 130))
        assertEquals(listOf("Elsa", "Moana"), TiledTextReader.merge(whole, extra).map { it.text })
    }

    @Test fun mergeLetsATileFooterReplaceAGarbledWholeRead() {
        val whole = listOf(line("2O/2O4 EN4", 50, 900, 200, 912))
        val extra = listOf(line("20/204 · EN · 4", 50, 900, 200, 912))
        assertEquals(listOf("20/204 · EN · 4"), TiledTextReader.merge(whole, extra).map { it.text })
    }

    @Test fun mergeKeepsAGoodWholeFooterOverATileRepeat() {
        val whole = listOf(line("20/204 · EN · 4", 50, 900, 200, 912))
        val extra = listOf(line("20/204 • EN • 4", 51, 900, 200, 913))
        assertEquals(listOf("20/204 · EN · 4"), TiledTextReader.merge(whole, extra).map { it.text })
    }

    /** Fake recognizer: sees footers only in enlarged images, and reports boxes in the image it was given. */
    private class FakeReader : TextReader {
        val sizes = mutableListOf<Pair<Int, Int>>()
        override suspend fun read(bitmap: Bitmap): List<OcrLine> {
            sizes += bitmap.width to bitmap.height
            // First call is the whole photo: only the name. Later calls are enlarged tiles: they also see
            // the footer, at (60,100) in the tile bitmap whichever tile it is, which is enough to check the
            // mapping back.
            return if (sizes.size == 1) listOf(OcrLine("PLUTO", 600, 680, 690, 704))
            else listOf(OcrLine("20/204 · EN · 4", 60, 100, 300, 130))
        }
    }

    @Test fun smallPhotosGetATiledPassAndBoxesMapBack() = runBlocking {
        val reader = FakeReader()
        val photo = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val lines = TiledTextReader(reader).read(photo)
        assertEquals(1600 to 1200, reader.sizes.first())
        assertEquals(1 + 4, reader.sizes.size) // whole + 2x2 tiles
        assertTrue(reader.sizes.drop(1).all { (w, _) -> w > 1600 }) // enlarged
        assertTrue(lines.any { it.text == "PLUTO" })
        val footers = lines.filter { CollectorLineParser.parse(it.text) != null }
        assertEquals(4, footers.size) // one per tile, far apart so none is a repeat
        // Boxes are back in photo pixels: inside the photo, and smaller than the enlarged tile text.
        assertTrue(footers.all { it.left in 0..1600 && it.right in 0..1600 && it.top in 0..1200 && it.right - it.left < 240 })
    }

    @Test fun bigPhotosSkipTheTiledPass() = runBlocking {
        val reader = FakeReader()
        TiledTextReader(reader).read(Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888))
        assertEquals(1, reader.sizes.size)
    }
}
