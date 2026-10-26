package io.github.jpsutton.inkstash.scan

import io.github.jpsutton.inkstash.data.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMatcherTest {
    private fun card(id: String, set: String, num: String, name: String, version: String) = Card(
        id, set, "Set $set", num, name, version, "Amber", true, 3, "Character", "Rare", null, null, null,
    )

    private val elsa = card("elsa", "1", "42", "Elsa", "Spirit of Winter")
    private val elsaQueen = card("elsa2", "3", "10", "Elsa", "Snow Queen")
    private val moana = card("moana1", "1", "5", "Moana", "Of Motunui")
    private val moana2 = card("moana2", "2", "6", "Moana", "Born Leader")
    private val moana3 = card("moana3", "4", "7", "Moana", "Chosen by the Ocean")
    private val enchanted = card("ench", "1", "205", "Elsa", "Spirit of Winter Enchanted")
    private val matcher = CardMatcher(listOf(elsa, elsaQueen, moana, moana2, moana3, enchanted))

    private fun candidate(num: String?, set: String?, vararg names: String) = CardCandidate(
        if (num == null) null else FooterInfo(num, "204", "EN", set ?: "1"),
        Rect(0, 0, 100, 140), names.toList(),
    )

    @Test fun exactSetAndNumber() {
        val r = matcher.match(candidate("42", "1"))
        assertEquals(MatchLevel.SURE, r.level)
        assertEquals("elsa", r.card!!.id)
        assertTrue(r.byNumber)
    }

    @Test fun exactMatchAgreeingWithNameStaysSure() {
        assertEquals(MatchLevel.SURE, matcher.match(candidate("42", "1", "Elsa", "Spirit of Winter")).level)
    }

    @Test fun enchantedNumberAboveTotal() = assertEquals("ench", matcher.match(candidate("205", "1")).card!!.id)

    @Test fun setCodeIsCaseInsensitive() = assertEquals("elsa", matcher.match(candidate("42", "1")).card!!.id)

    @Test fun footerContradictedByNameIsFlagged() {
        val r = matcher.match(candidate("42", "1", "Moana"))
        assertEquals(MatchLevel.CHECK, r.level)
    }

    @Test fun fuzzyNameWhenNumberMissing() {
        val r = matcher.match(candidate("99", "1", "Elsa", "Spirit of Wintor"))
        assertEquals(MatchLevel.CHECK, r.level)
        assertEquals("elsa", r.card!!.id)
        assertEquals(false, r.byNumber)
    }

    @Test fun fuzzyNameWithoutAnyFooter() {
        val r = matcher.match(candidate(null, null, "Mo0na", "Of Motunui"))
        assertEquals("moana1", r.card!!.id)
    }

    @Test fun nameOnlyOffersOtherVersions() {
        val r = matcher.match(candidate(null, null, "Moana"))
        assertEquals(MatchLevel.CHECK, r.level)
        assertNotNull(r.card)
        assertTrue(r.alternates.size >= 2)
        assertTrue(r.alternates.all { it.name == "Moana" })
        assertTrue(r.card !in r.alternates)
    }

    @Test fun fuzzyIsRestrictedToKnownSet() {
        // Elsa "Snow Queen" lives in set 3; with footer set 1 and a wrong number it must not win.
        val r = matcher.match(candidate("99", "1", "Elsa", "Snow Queen"))
        assertEquals("elsa", r.card!!.id)
    }

    @Test fun nothingReadableIsNotFound() {
        val r = matcher.match(candidate("99", "1"))
        assertEquals(MatchLevel.NONE, r.level)
        assertNull(r.card)
    }

    @Test fun garbageNameIsNotFound() = assertEquals(MatchLevel.NONE, matcher.match(candidate(null, null, "zzzzzz qqqq")).level)

    @Test fun similarity() {
        assertEquals(1.0, CardMatcher.similarity("elsa", "elsa"), 0.0)
        assertEquals(0.75, CardMatcher.similarity("elsa", "elsb"), 1e-9)
        assertEquals(0.0, CardMatcher.similarity("", "elsa"), 0.0)
    }
}
