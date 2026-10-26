package io.github.jpsutton.inkstash.data

/** What the owned copies of one card are worth, by the card's estimated prices (US dollars). */
data class CardValue(val total: Double, val pricedCopies: Int, val unpricedCopies: Int) {
    companion object {
        /** A normal copy uses [Card.priceUsd], a foil copy [Card.priceFoilUsd]. A copy with no price adds nothing. */
        fun of(card: Card, holdings: List<Holding>): CardValue {
            var total = 0.0
            var priced = 0
            var unpriced = 0
            for (h in holdings) {
                val price = if (h.foil) card.priceFoilUsd else card.priceUsd
                if (price == null) {
                    unpriced += h.quantity
                } else {
                    total += price * h.quantity
                    priced += h.quantity
                }
            }
            return CardValue(total, priced, unpriced)
        }
    }
}
