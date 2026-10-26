package io.github.jpsutton.inkstash.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.HoldingWithCard
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.data.LocationSummary
import io.github.jpsutton.inkstash.ui.InkColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A temp set's list card on the Places screen. */
data class TempSetSummary(val location: Location, val count: Int, val sources: String, val inks: List<Pair<InkColor, Int>>)

data class PlacesState(
    val total: Int = 0,
    val general: LocationSummary? = null,
    val pools: List<LocationSummary> = emptyList(),
    val temps: List<TempSetSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModel(private val collection: CollectionRepository) : ViewModel() {
    val state: StateFlow<PlacesState> = combine(collection.observeSummaries(), collection.observeLocations()) { sums, locs -> sums to locs }
        .flatMapLatest { (sums, locs) ->
            val temps = sums.filter { it.location.kind == LocationKind.TEMP_SET }
            if (temps.isEmpty()) flowOf(buildState(sums, locs, emptyMap()))
            else combine(temps.map { t -> collection.observeInLocation(t.location.id).map { t.location.id to it } }) { pairs ->
                buildState(sums, locs, pairs.toMap())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesState())

    private fun buildState(sums: List<LocationSummary>, locs: List<Location>, tempRows: Map<Long, List<HoldingWithCard>>): PlacesState {
        val names = locs.associate { it.id to it.name }
        return PlacesState(
            total = sums.sumOf { it.cardCount },
            general = sums.firstOrNull { it.location.kind == LocationKind.GENERAL },
            pools = sums.filter { it.location.kind == LocationKind.POOL },
            temps = sums.filter { it.location.kind == LocationKind.TEMP_SET }.map { t ->
                val rows = tempRows[t.location.id].orEmpty()
                val sourceIds = rows.map { it.holding.sourceLocationId }.filter { it != 0L }.distinct()
                TempSetSummary(
                    t.location, t.cardCount,
                    when {
                        sourceIds.isEmpty() -> "nowhere yet"
                        sourceIds.size == 1 -> names[sourceIds.single()] ?: "a deleted place"
                        else -> "${sourceIds.size} places"
                    },
                    inkMix(rows),
                )
            },
        )
    }

    fun create(name: String, kind: LocationKind) = viewModelScope.launch { if (name.isNotBlank()) collection.createLocation(name, kind) }
    fun rename(id: Long, name: String) = viewModelScope.launch { if (name.isNotBlank()) collection.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { collection.deleteLocation(id) }
}

fun inkMix(rows: List<HoldingWithCard>): List<Pair<InkColor, Int>> =
    rows.groupBy { InkColor.of(it.card.ink) }.map { (ink, r) -> ink to r.sumOf { it.holding.quantity } }.sortedByDescending { it.second }
