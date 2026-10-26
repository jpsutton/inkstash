package io.github.jpsutton.inkstash.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLocatorTest {
    // A 3-card row, cards ~ 360 x 502 px, 400 px apart; footer text 14 px tall at the bottom left.
    private fun footer(col: Int, row: Int, text: String) =
        OcrLine(text, 20 + col * 400, 470 + row * 540, 140 + col * 400, 484 + row * 540)

    @Test fun oneAnchorPerFooter() {
        val lines = listOf(footer(0, 0, "1/204 · EN · 1"), footer(1, 0, "2/204 · EN · 1"), footer(2, 0, "3/204 · EN · 1"))
        val found = CardLocator.locate(lines, 1300, 600)
        assertEquals(listOf("1", "2", "3"), found.map { it.footer!!.collectorNumber })
    }

    @Test fun rowsComeBeforeColumns() {
        val lines = listOf(
            footer(1, 1, "5/204 · EN · 1"), footer(0, 0, "1/204 · EN · 1"), footer(1, 0, "2/204 · EN · 1"), footer(0, 1, "4/204 · EN · 1"),
        )
        assertEquals(listOf("1", "2", "4", "5"), CardLocator.locate(lines, 900, 1100).map { it.footer!!.collectorNumber })
    }

    @Test fun nameLinesInsideTheRectBelongToTheCard() {
        val lines = listOf(
            footer(0, 0, "1/204 · EN · 1"), footer(1, 0, "2/204 · EN · 1"),
            OcrLine("ELSA", 120, 330, 260, 370), OcrLine("Spirit of Winter", 100, 380, 280, 400),
            OcrLine("MOANA", 520, 330, 660, 370),
        )
        val found = CardLocator.locate(lines, 900, 600)
        assertTrue("ELSA" in found[0].nameCandidates)
        assertTrue("MOANA" !in found[0].nameCandidates)
        assertEquals(listOf("MOANA"), found[1].nameCandidates)
        // Biggest text first.
        assertEquals("ELSA", found[0].nameCandidates.first())
    }

    @Test fun noFooterNoCards() = assertTrue(CardLocator.locate(listOf(OcrLine("Elsa", 0, 0, 50, 20)), 100, 100).isEmpty())

    @Test fun rectStaysInsideTheImage() {
        val r = CardLocator.locate(listOf(footer(0, 0, "1/204 · EN · 1")), 300, 500).single().rect
        assertTrue(r.left >= 0 && r.top >= 0 && r.right <= 300 && r.bottom <= 500)
    }
}
