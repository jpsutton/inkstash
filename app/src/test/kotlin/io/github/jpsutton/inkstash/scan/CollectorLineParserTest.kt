package io.github.jpsutton.inkstash.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectorLineParserTest {
    private fun parse(s: String) = CollectorLineParser.parse(s)

    @Test fun cleanFooter() {
        val f = parse("42/204 · EN · 1")!!
        assertEquals("42", f.collectorNumber)
        assertEquals("204", f.total)
        assertEquals("EN", f.lang)
        assertEquals("1", f.setCode)
    }

    @Test fun bulletSeparators() = assertEquals("42", parse("42/204 • EN • 1")!!.collectorNumber)

    @Test fun separatorsReadAsDotsOrDashes() {
        assertEquals("7", parse("7/204 . EN . 3")!!.collectorNumber)
        assertEquals("3", parse("7/204 - EN - 3")!!.setCode)
    }

    @Test fun enchantedNumberAboveTotal() {
        val f = parse("205/204 · EN · 1")!!
        assertEquals("205", f.collectorNumber)
        assertEquals("204", f.total)
    }

    @Test fun letterSwapsInNumbers() {
        assertEquals("102", parse("lO2/2O4 · EN · 1")!!.collectorNumber)
        assertEquals("204", parse("lO2/2O4 · EN · 1")!!.total)
    }

    @Test fun setOneReadAsLetter() {
        assertEquals("1", parse("42/204 · EN · l")!!.setCode)
        assertEquals("1", parse("42/204 · EN · I")!!.setCode)
        assertEquals("11", parse("42/204 · EN · II")!!.setCode)
    }

    @Test fun lettersInSetCodeStay() {
        assertEquals("P1", parse("5/P1 · EN · P1")!!.setCode)
        assertEquals("D23", parse("12/31 · EN · D23")!!.setCode)
    }

    @Test fun promoWithoutTotal() {
        val f = parse("5 · EN · P1")!!
        assertEquals("5", f.collectorNumber)
        assertNull(f.total)
        assertEquals("P1", f.setCode)
    }

    @Test fun footerInsideALongerLine() = assertEquals("42", parse("Illus. Jane Doe  42/204 · EN · 1")!!.collectorNumber)

    @Test fun suffixLetterKept() = assertEquals("12a", parse("12a/204 · EN · 1")!!.collectorNumber)

    @Test fun lowercaseLanguageFixed() = assertEquals("EN", parse("42/204 · en · 1")!!.lang)

    @Test fun ordinaryTextIsNotAFooter() {
        assertNull(parse("Elsa"))
        assertNull(parse("Spirit of Winter"))
        assertNull(parse("Draw 2 cards. 3 / 4"))
        assertNull(parse("0/204 · EN · 1"))
    }
}
