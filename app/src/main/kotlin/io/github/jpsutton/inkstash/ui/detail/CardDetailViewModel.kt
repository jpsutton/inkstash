package io.github.jpsutton.inkstash.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.data.Card
import io.github.jpsutton.inkstash.data.CardRepository
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.Holding
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One "Your copies" row. */
data class CopyRow(val holding: Holding, val place: Location?, val sourceName: String?)

data class CardDetailState(
    val card: Card? = null,
    val rows: List<CopyRow> = emptyList(),
    val locations: List<Location> = emptyList(),
) {
    val total: Int get() = rows.sumOf { it.holding.quantity }
}

class CardDetailViewModel(
    private val cardId: String,
    cards: CardRepository,
    private val collection: CollectionRepository,
) : ViewModel() {
    val state: StateFlow<CardDetailState> = combine(
        cards.observe(cardId), collection.observeHoldingsForCard(cardId), collection.observeLocations(),
    ) { card, holdings, locations ->
        val byId = locations.associateBy { it.id }
        CardDetailState(
            card = card,
            rows = holdings.map { CopyRow(it, byId[it.locationId], byId[it.sourceLocationId]?.name) },
            locations = locations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDetailState())

    fun move(holdingId: Long, qty: Int, toLocationId: Long) = viewModelScope.launch { collection.move(holdingId, qty, toLocationId) }
    fun removeOne(holdingId: Long) = viewModelScope.launch { collection.remove(holdingId, 1) }
    fun addCopy(foil: Boolean, locationId: Long) = viewModelScope.launch { collection.add(cardId, foil, 1, locationId) }

    companion object {
        fun kindText(l: Location) = when (l.kind) {
            LocationKind.GENERAL -> "Main collection"
            LocationKind.POOL -> "Pool"
            LocationKind.TEMP_SET -> "Temp set"
        }
    }
}
