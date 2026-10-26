package io.github.jpsutton.inkstash.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardValueTest {
    private fun card(price: Double?, foil: Double?) = Card(
        "a", "1", "S", "1", "Anna", "v", "Amber", true, 2, "Character", "Rare", null, null, null, price, foil,
    )

    private fun held(qty: Int, foil: Boolean = false, location: Long = 1) =
        Holding(cardId = "a", locationId = location, foil = foil, quantity = qty)

    @Test fun normalAndFoilCopiesUseTheirOwnPrice() {
        val v = CardValue.of(card(0.5, 4.0), listOf(held(3), held(2, foil = true)))
        assertEquals(3 * 0.5 + 2 * 4.0, v.total, 1e-9)
        assertEquals(5, v.pricedCopies)
        assertEquals(0, v.unpricedCopies)
    }

    @Test fun copiesInEveryPlaceCount() {
        val v = CardValue.of(card(1.0, null), listOf(held(1, location = 1), held(2, location = 7)))
        assertEquals(3.0, v.total, 1e-9)
    }

    @Test fun aCopyWithoutAPriceAddsNothingAndIsCounted() {
        val v = CardValue.of(card(null, 10.0), listOf(held(2), held(1, foil = true)))
        assertEquals(10.0, v.total, 1e-9)
        assertEquals(1, v.pricedCopies)
        assertEquals(2, v.unpricedCopies)
    }

    @Test fun noCopiesIsWorthNothing() {
        assertEquals(CardValue(0.0, 0, 0), CardValue.of(card(1.0, 2.0), emptyList()))
    }
}
