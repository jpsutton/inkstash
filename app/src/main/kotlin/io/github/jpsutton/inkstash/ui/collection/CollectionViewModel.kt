package io.github.jpsutton.inkstash.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.data.CardRepository
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.OwnedCard
import io.github.jpsutton.inkstash.ui.InkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class CollectionState(
    val items: List<OwnedCard> = emptyList(),
    val hasAny: Boolean = false,
    val totalCopies: Int = 0,
    val distinct: Int = 0,
    val ink: InkColor? = null,
    val setFilter: String? = null,
    val rarity: String? = null,
    val type: String? = null,
    val query: String = "",
    val locationId: Long = -1,
    val locationName: String = "Everywhere",
    val locations: List<Location> = emptyList(),
    val setOptions: List<Pair<String, String>> = emptyList(),
    val rarityOptions: List<String> = emptyList(),
    val typeOptions: List<String> = emptyList(),
)

private data class Filters(
    val ink: InkColor?, val set: String?, val rarity: String?, val type: String?, val query: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(collection: CollectionRepository, cards: CardRepository) : ViewModel() {
    private val ink = MutableStateFlow<InkColor?>(null)
    private val set = MutableStateFlow<String?>(null)
    private val rarity = MutableStateFlow<String?>(null)
    private val type = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val location = MutableStateFlow(-1L)

    private val filters = combine(ink, set, rarity, type, query) { i, s, r, t, q -> Filters(i, s, r, t, q) }
    private val owned = location.flatMapLatest { collection.observeOwned(it) }

    val state: StateFlow<CollectionState> =
        combine(owned, filters, location, collection.observeLocations(), cards.sets) { all, f, loc, locations, sets ->
            val q = f.query.trim()
            val items = all.filter { o ->
                (f.ink == null || o.card.ink.equals(f.ink.label, true)) &&
                    (f.set == null || o.card.setCode == f.set) &&
                    (f.rarity == null || o.card.rarity == f.rarity) &&
                    (f.type == null || o.card.type.split(' ').contains(f.type)) &&
                    (q.isEmpty() || o.card.name.contains(q, true) || o.card.version.contains(q, true))
            }
            CollectionState(
                items = items,
                hasAny = all.isNotEmpty(),
                totalCopies = items.sumOf { it.qty },
                distinct = items.size,
                ink = f.ink, setFilter = f.set, rarity = f.rarity, type = f.type, query = f.query,
                locationId = loc,
                locationName = locations.firstOrNull { it.id == loc }?.name ?: "Everywhere",
                locations = locations,
                setOptions = sets.map { it.setCode to it.setName },
                rarityOptions = all.map { it.card.rarity }.filter { it.isNotBlank() }.distinct().sorted(),
                typeOptions = all.flatMap { it.card.type.split(' ') }.filter { it.isNotBlank() }.distinct().sorted(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionState())

    fun setInk(v: InkColor?) { ink.value = v }
    fun setSet(v: String?) { set.value = v }
    fun setRarity(v: String?) { rarity.value = v }
    fun setType(v: String?) { type.value = v }
    fun setQuery(v: String) { query.value = v }
    fun setLocation(id: Long) { location.value = id }
}
