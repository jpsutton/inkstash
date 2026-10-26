package io.github.jpsutton.inkstash.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jpsutton.inkstash.AppContainer
import io.github.jpsutton.inkstash.data.Card
import io.github.jpsutton.inkstash.data.GENERAL_ID
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.data.NewCopies
import io.github.jpsutton.inkstash.scan.MatchLevel
import io.github.jpsutton.inkstash.scan.ScanRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row on the review screen. `card` is null while nothing matched ("Not found"). */
data class ReviewRow(
    val id: Int,
    val crop: Bitmap?,
    val card: Card?,
    val level: MatchLevel,
    val detail: String,
    val alternates: List<Card>,
    val qty: Int = 1,
    val foil: Boolean = false,
)

data class ReviewState(
    val rows: List<ReviewRow> = emptyList(),
    val destinationId: Long = GENERAL_ID,
    val destinations: List<Location> = emptyList(),
) {
    val destination: Location? get() = destinations.firstOrNull { it.id == destinationId }
    val matched: List<ReviewRow> get() = rows.filter { it.card != null }
    val totalCards: Int get() = matched.sumOf { it.qty }
    val toCheck: Int get() = rows.count { it.level == MatchLevel.CHECK && it.card != null }
    val notFound: Int get() = rows.count { it.card == null }
    val summary: String
        get() = buildList {
            add("${matched.size} matched")
            if (toCheck > 0) add("$toCheck to check")
            if (notFound > 0) add("$notFound not found")
        }.joinToString(" · ")
}

class ReviewViewModel(private val app: AppContainer) : ViewModel() {
    private val rows = MutableStateFlow(initialRows())
    private val destinationId = MutableStateFlow(GENERAL_ID)
    private var nextId = rows.value.size + 1

    val state: StateFlow<ReviewState> = kotlinx.coroutines.flow.combine(rows, destinationId, app.collection.observeLocations()) { r, d, locs ->
        ReviewState(r, d, locs.filter { it.kind != LocationKind.TEMP_SET })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewState(rows.value))

    private fun initialRows(): List<ReviewRow> {
        val session = app.scanSession.value ?: return emptyList()
        return session.rows.mapIndexed { i, r -> toRow(i + 1, session.photo, r) }
    }

    private fun toRow(id: Int, photo: Bitmap, r: ScanRow): ReviewRow {
        val rect = r.candidate.rect
        val crop = runCatching {
            if (rect.width > 8 && rect.height > 8) Bitmap.createBitmap(photo, rect.left, rect.top, rect.width.coerceAtMost(photo.width - rect.left), rect.height.coerceAtMost(photo.height - rect.top)) else null
        }.getOrNull()
        val f = r.candidate.footer
        val detail = when {
            r.result.card == null -> "Tap Find it to search by name"
            r.result.byNumber -> "${f?.collectorNumber}${f?.total?.let { "/$it" } ?: ""} · Set ${f?.setCode}"
            else -> "Number was blurry, matched by name"
        }
        return ReviewRow(id, crop, r.result.card, r.result.level, detail, r.result.alternates)
    }

    private fun edit(id: Int, fn: (ReviewRow) -> ReviewRow) { rows.value = rows.value.map { if (it.id == id) fn(it) else it } }

    fun toggleFoil(id: Int) = edit(id) { it.copy(foil = !it.foil) }
    fun inc(id: Int) = edit(id) { it.copy(qty = it.qty + 1) }
    fun dec(id: Int) = edit(id) { it.copy(qty = (it.qty - 1).coerceAtLeast(1)) }
    fun remove(id: Int) { rows.value = rows.value.filter { it.id != id } }
    fun pickAlternate(id: Int, card: Card) = edit(id) { it.copy(card = card, level = MatchLevel.SURE, detail = "Picked by you", alternates = emptyList()) }
    fun setDestination(id: Long) { destinationId.value = id }

    /** Sets the card of a "Not found" row, or adds a new row when [id] is null. */
    fun choose(id: Int?, card: Card) {
        if (id == null) rows.value = rows.value + ReviewRow(nextId++, null, card, MatchLevel.SURE, "Added by you", emptyList())
        else pickAlternate(id, card)
    }

    suspend fun search(q: String) = app.cards.search(q)

    fun confirm(onDone: () -> Unit) {
        val s = state.value
        val entries = s.matched.map { NewCopies(it.card!!.id, it.foil, it.qty) }
        viewModelScope.launch {
            app.collection.addAll(entries, s.destinationId)
            app.scanSession.value = null
            onDone()
        }
    }

    fun discard() { app.scanSession.value = null }
}
