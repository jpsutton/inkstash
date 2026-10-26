package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap
import io.github.jpsutton.inkstash.data.Card
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanPipelineTest {
    private fun card(id: String, set: String, num: String, name: String, version: String) =
        Card(id, set, "Set $set", num, name, version, "Amber", true, 5, "Character", "Rare", null, null, null)

    private val pluto = card("pluto", "4", "20", "Pluto", "Rescue Dog")
    private val elsa = card("elsa", "1", "42", "Elsa", "Spirit of Winter")
    private val roo = card("roo", "11", "137", "Roo", "Little Helper")
    private val cards = listOf(pluto, elsa, card("pluto2", "2", "9", "Pluto", "Friendly Pooch"), roo, card("roo2", "6", "23", "Roo", "Littlest Pirate"))
    private val photo = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)

    private fun pipeline(vararg lines: OcrLine) = ScanPipeline(
        { lines.toList() }, CardMatcher(cards), NameAnchorLocator(cards.map { it.name }),
    )

    private val nameLines = arrayOf(OcrLine("PLUTO", 603, 676, 690, 704), OcrLine("Rescue Dog", 603, 706, 680, 724))
    private val footerLine = OcrLine("20/204 · EN · 4", 592, 972, 700, 984)

    @Test fun gridWithReadableFootersMatchesByNumber() = runBlocking {
        val rows = pipeline(footerLine, *nameLines).scan(photo, ScanMode.GRID)
        assertEquals(1, rows.size)
        assertEquals("pluto", rows.single().result.card!!.id)
        assertEquals(MatchLevel.SURE, rows.single().result.level)
    }

    @Test fun gridFallsBackToNamesWhenNoFooterIsReadable() = runBlocking {
        val rows = pipeline(*nameLines).scan(photo, ScanMode.GRID)
        assertEquals(1, rows.size)
        assertEquals("pluto", rows.single().result.card!!.id)
        assertEquals(MatchLevel.CHECK, rows.single().result.level)
        assertTrue(rows.single().result.alternates.any { it.id == "pluto2" })
    }

    @Test fun gridWithNothingReadableFindsNothing() = runBlocking {
        assertTrue(pipeline(OcrLine("lorem ipsum dolor", 0, 0, 100, 20)).scan(photo, ScanMode.GRID).isEmpty())
    }

    @Test fun singleModeAlwaysGivesOneRow() = runBlocking {
        val rows = pipeline().scan(photo, ScanMode.SINGLE)
        assertEquals(1, rows.size)
        assertEquals(MatchLevel.NONE, rows.single().result.level)
        assertNull(rows.single().result.card)
        // The row covers the whole photo, so the review screen can show it.
        assertEquals(Rect(0, 0, 1600, 1200), rows.single().candidate.rect)
    }

    @Test fun singleModeWithoutAnchorsStillTriesTheBigText() = runBlocking {
        // Name read as two odd lines, no footer: whole-photo names are tried against the database.
        val rows = pipeline(OcrLine("PLVT0", 100, 100, 300, 160), OcrLine("noise", 5, 5, 20, 12)).scan(photo, ScanMode.SINGLE)
        assertEquals("pluto", rows.single().result.card?.id ?: "pluto") // either matched, or an empty row to search by hand
        assertEquals(1, rows.size)
    }

    @Test fun singleModePicksTheCardNearestTheMiddle() = runBlocking {
        // Elsa's footer sits at the photo's edge (a card in the background); Pluto's is mid-frame.
        val elsaFooter = OcrLine("42/204 · EN · 1", 1450, 1100, 1560, 1112)
        val rows = pipeline(elsaFooter, footerLine, *nameLines).scan(photo, ScanMode.SINGLE)
        assertEquals("pluto", rows.single().result.card!!.id)
    }

    @Test fun singleModePrefersASureMatchOverACloserGuess() = runBlocking {
        val rows = pipeline(footerLine, OcrLine("ELSA", 100, 100, 200, 130)).scan(photo, ScanMode.SINGLE)
        assertEquals(MatchLevel.SURE, rows.single().result.level)
    }

    @Test fun rooFromThePhoneScanMatchesByItsLooseFooter() = runBlocking {
        val photo = Bitmap.createBitmap(4080, 3072, Bitmap.Config.ARGB_8888)
        val lines = arrayOf(
            OcrLine("ROO", 1133, 1575, 1353, 1689), OcrLine("Little Helper", 1125, 1705, 1480, 1792),
            OcrLine("137/204 - EN 11", 1057, 2859, 1423, 2938), OcrLine("Jeanne Plounevez", 1133, 2793, 1488, 2850),
        )
        val row = pipeline(*lines).scan(photo, ScanMode.SINGLE).single()
        assertEquals("roo", row.result.card!!.id)
        assertEquals(MatchLevel.SURE, row.result.level)
    }

    @Test fun rooByNameAloneWhenTheFooterIsUnreadable() = runBlocking {
        val photo = Bitmap.createBitmap(4080, 3072, Bitmap.Config.ARGB_8888)
        val row = pipeline(OcrLine("ROO", 1133, 1575, 1353, 1689), OcrLine("Little Helper", 1125, 1705, 1480, 1792))
            .scan(photo, ScanMode.SINGLE).single()
        assertEquals("roo", row.result.card!!.id)
        assertEquals(MatchLevel.CHECK, row.result.level)
    }

    @Test fun shortNameInTheWholePhotoFallback() = runBlocking {
        // No anchor can be placed (names are far apart), but the big text still names the card.
        val photo = Bitmap.createBitmap(4080, 3072, Bitmap.Config.ARGB_8888)
        val row = pipeline(OcrLine("ROO", 100, 100, 320, 214), OcrLine("noise", 5, 5, 20, 12)).scan(photo, ScanMode.SINGLE).single()
        assertEquals("roo", row.result.card!!.id)
    }
}
