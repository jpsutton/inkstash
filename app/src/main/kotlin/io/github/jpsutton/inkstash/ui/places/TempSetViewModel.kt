package io.github.jpsutton.inkstash.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.HoldingWithCard
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.OwnedCard
import io.github.jpsutton.inkstash.ui.InkColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TempSetState(
    val location: Location? = null,
    val rows: List<HoldingWithCard> = emptyList(),
    val sourceNames: Map<Long, String> = emptyMap(),
    val owned: List<OwnedCard> = emptyList(),
) {
    val total: Int get() = rows.sumOf { it.holding.quantity }
    val different: Int get() = rows.map { it.card.id }.distinct().size
    val inkable: Int get() = rows.filter { it.card.inkable }.sumOf { it.holding.quantity }
    val inks: List<Pair<InkColor, Int>> get() = inkMix(rows)

    /** Copies per cost 1..7+ (cost 0 counts as 1). */
    val curve: List<Int>
        get() = (1..7).map { cost ->
            rows.filter { r -> r.card.cost.coerceIn(1, 7) == cost }.sumOf { it.holding.quantity }
        }

    /** (place name, copies) for each place the cards were borrowed from. */
    val borrowedFrom: List<Pair<String, Int>>
        get() = rows.groupBy { it.holding.sourceLocationId }.map { (src, r) ->
            (sourceNames[src] ?: "Nowhere (added directly)") to r.sumOf { it.holding.quantity }
        }.sortedByDescending { it.second }
}

class TempSetViewModel(private val id: Long, private val collection: CollectionRepository) : ViewModel() {
    val state: StateFlow<TempSetState> = combine(
        collection.observeLocation(id), collection.observeInLocation(id), collection.observeLocations(), collection.observeOwned(-1),
    ) { loc, rows, locs, owned ->
        TempSetState(loc, rows, locs.associate { it.id to it.name }, owned)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempSetState())

    fun rename(name: String) = viewModelScope.launch { if (name.isNotBlank()) collection.rename(id, name) }
    fun dissolve(onDone: () -> Unit) = viewModelScope.launch { collection.dissolveTempSet(id); onDone() }
    fun borrow(cardId: String) = viewModelScope.launch { collection.borrowOne(cardId, id) }
    fun giveBack(holdingId: Long) = viewModelScope.launch {
        // Sending one copy back = moving it to its source (General when the source is gone).
        collection.returnOne(holdingId)
    }
}
