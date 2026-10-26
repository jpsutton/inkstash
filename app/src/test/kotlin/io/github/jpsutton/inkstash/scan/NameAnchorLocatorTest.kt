package io.github.jpsutton.inkstash.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameAnchorLocatorTest {
    private val locator = NameAnchorLocator(listOf("Pluto", "Elsa", "Mickey Mouse", "Be"))

    // Lines as ML Kit reads the real Pluto - Rescue Dog photo (1600x1200; card at 570,305 to 1080,1005).
    private val pluto = listOf(
        OcrLine("PLUTO", 603, 676, 690, 704),
        OcrLine("Rescue Dog", 603, 706, 680, 724),
        OcrLine("TO THE RESCUE When you play this character,", 600, 780, 970, 800),
        OcrLine("a long line of rules text that is not a name", 600, 810, 990, 830),
    )

    @Test fun nameAnchorsOneCardAndOutlinesIt() {
        val found = locator.locate(pluto, emptyList(), 1600, 1200).single()
        assertEquals(null, found.footer)
        // Outline lands near the real card: left 570, top 305, about 510 x 700.
        assertTrue("left ${found.rect.left}", found.rect.left in 540..600)
        assertTrue("top ${found.rect.top}", found.rect.top in 270..340)
        assertTrue("height ${found.rect.height}", found.rect.height in 600..800)
        assertEquals("PLUTO", found.nameCandidates.first())
        assertTrue("Rescue Dog" in found.nameCandidates)
    }

    @Test fun nameCloseToACardNameStillAnchors() {
        val lines = listOf(OcrLine("PLUT0", 603, 676, 690, 704))
        assertEquals(1, locator.locate(lines, emptyList(), 1600, 1200).size)
    }

    @Test fun rulesTextAndShortWordsDoNotAnchor() {
        val lines = listOf(OcrLine("Be", 10, 10, 30, 30), OcrLine("When you play this character", 100, 100, 400, 130), OcrLine("5", 0, 0, 10, 10))
        assertTrue(locator.locate(lines, emptyList(), 800, 600).isEmpty())
    }

    @Test fun namesInsideAFooterCardAreLeftToIt() {
        val taken = listOf(Rect(560, 300, 1090, 1010))
        assertTrue(locator.locate(pluto, taken, 1600, 1200).isEmpty())
    }

    @Test fun twoCardsInARowUseTheGridPitch() {
        val lines = listOf(
            OcrLine("PLUTO", 100, 400, 190, 428), OcrLine("ELSA", 700, 400, 780, 428),
        )
        val found = locator.locate(lines, emptyList(), 1400, 1000)
        assertEquals(2, found.size)
        // Cards are 600 px apart, so each outline is narrower than the pitch.
        assertTrue(found.all { it.rect.width in 400..600 })
    }
}
