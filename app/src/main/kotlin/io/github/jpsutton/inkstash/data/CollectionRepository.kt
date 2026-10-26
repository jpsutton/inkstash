package io.github.jpsutton.inkstash.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * All changes to the collection. Each operation is one Room transaction, and none creates or
 * destroys copies except [add] and [remove]: moves, dissolves and deletes conserve the total.
 */
class CollectionRepository(
    private val db: InkstashDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val holdings = db.holdings()
    private val locations = db.locations()

    fun observeOwned(locationId: Long = -1): Flow<List<OwnedCard>> = holdings.observeOwned(locationId)
    fun observeHoldingsForCard(cardId: String): Flow<List<Holding>> = holdings.observeForCard(cardId)
    fun observeInLocation(locationId: Long): Flow<List<HoldingWithCard>> = holdings.observeInLocation(locationId)
    fun observeLocations(): Flow<List<Location>> = locations.observeAll()
    fun observeSummaries(): Flow<List<LocationSummary>> = locations.observeSummaries()
    fun observeLocation(id: Long): Flow<Location?> = locations.observe(id)

    suspend fun totalCopies(): Int = holdings.totalCopies()

    suspend fun createLocation(name: String, kind: LocationKind): Long {
        require(kind != LocationKind.GENERAL) { "There is only one General location" }
        return locations.insert(Location(name = name.trim(), kind = kind, createdAt = now()))
    }

    suspend fun rename(locationId: Long, name: String) {
        val loc = locations.byId(locationId) ?: return
        if (loc.kind == LocationKind.GENERAL) return
        locations.update(loc.copy(name = name.trim()))
    }

    /** Adds copies to a location (a normal pool, General, or a temp set with no source). */
    suspend fun add(cardId: String, foil: Boolean, qty: Int, locationId: Long) {
        require(qty > 0)
        db.withTransaction { addRow(cardId, locationId, foil, qty, Holding.NO_SOURCE) }
    }

    suspend fun addAll(entries: List<NewCopies>, locationId: Long) {
        db.withTransaction {
            for (e in entries) if (e.qty > 0) addRow(e.cardId, locationId, e.foil, e.qty, Holding.NO_SOURCE)
        }
    }

    /** Removes `qty` copies from a holding row. Removing all of them deletes the row. */
    suspend fun remove(holdingId: Long, qty: Int) {
        require(qty > 0)
        db.withTransaction {
            val h = holdings.byId(holdingId) ?: return@withTransaction
            if (qty >= h.quantity) holdings.delete(h.id) else holdings.update(h.copy(quantity = h.quantity - qty))
        }
    }

    /**
     * Moves `qty` copies of a holding to another location. Moving into a TEMP_SET records where they
     * came from: the row's own source if it is already borrowed, else the place they leave.
     */
    suspend fun move(holdingId: Long, qty: Int, toLocationId: Long) {
        require(qty > 0)
        db.withTransaction {
            val h = holdings.byId(holdingId) ?: return@withTransaction
            if (h.locationId == toLocationId) return@withTransaction
            val to = locations.byId(toLocationId) ?: return@withTransaction
            val n = minOf(qty, h.quantity)
            val source = if (to.kind == LocationKind.TEMP_SET) {
                if (h.sourceLocationId != Holding.NO_SOURCE) h.sourceLocationId else h.locationId
            } else {
                Holding.NO_SOURCE
            }
            takeFrom(h, n)
            addRow(h.cardId, toLocationId, h.foil, n, source)
        }
    }

    /**
     * Borrows one copy of a card into a temp set, from where the most copies are: General first, then
     * pools. Copies already in a temp set are left alone. Returns false if no copy is free.
     */
    suspend fun borrowOne(cardId: String, tempSetId: Long): Boolean = db.withTransaction {
        val rows = holdings.forCard(cardId).filter { it.locationId != tempSetId }
        val free = rows.mapNotNull { h -> locations.byId(h.locationId)?.takeIf { it.kind != LocationKind.TEMP_SET }?.let { h to it } }
        val pick = free.maxWithOrNull(compareBy({ it.second.kind == LocationKind.GENERAL }, { it.first.quantity }))?.first
            ?: return@withTransaction false
        move(pick.id, 1, tempSetId)
        true
    }

    /** Sends one copy of a temp set holding back to its source (General if gone). */
    suspend fun returnOne(holdingId: Long) = db.withTransaction {
        val h = holdings.byId(holdingId) ?: return@withTransaction
        val target = h.sourceLocationId.takeIf { it != Holding.NO_SOURCE && locations.byId(it) != null } ?: GENERAL_ID
        move(holdingId, 1, target)
    }

    /** Returns every copy to its source (General if the source is gone) and deletes the temp set. */
    suspend fun dissolveTempSet(id: Long) {
        db.withTransaction {
            val set = locations.byId(id) ?: return@withTransaction
            if (set.kind != LocationKind.TEMP_SET) return@withTransaction
            for (h in holdings.inLocation(id)) {
                val target = h.sourceLocationId.takeIf { it != Holding.NO_SOURCE && locations.byId(it) != null }
                    ?: GENERAL_ID
                holdings.delete(h.id)
                addRow(h.cardId, target, h.foil, h.quantity, Holding.NO_SOURCE)
            }
            locations.delete(id)
        }
    }

    /**
     * Deletes a pool (its cards go to General) or a temp set (dissolved). General cannot be deleted.
     * Temp sets that borrowed from the pool now point at General.
     */
    suspend fun deleteLocation(id: Long) {
        db.withTransaction {
            val loc = locations.byId(id) ?: return@withTransaction
            when (loc.kind) {
                LocationKind.GENERAL -> return@withTransaction
                LocationKind.TEMP_SET -> dissolveTempSet(id)
                LocationKind.POOL -> {
                    for (h in holdings.inLocation(id)) {
                        holdings.delete(h.id)
                        addRow(h.cardId, GENERAL_ID, h.foil, h.quantity, Holding.NO_SOURCE)
                    }
                    for (h in holdings.borrowedFrom(id)) {
                        holdings.delete(h.id)
                        addRow(h.cardId, h.locationId, h.foil, h.quantity, GENERAL_ID)
                    }
                    locations.delete(id)
                }
            }
        }
    }

    private suspend fun takeFrom(h: Holding, n: Int) {
        if (n >= h.quantity) holdings.delete(h.id) else holdings.update(h.copy(quantity = h.quantity - n))
    }

    /** Adds to the matching row, or inserts one. Must run inside a transaction. */
    private suspend fun addRow(cardId: String, locationId: Long, foil: Boolean, qty: Int, source: Long) {
        val existing = holdings.find(cardId, locationId, foil, source)
        if (existing != null) {
            holdings.update(existing.copy(quantity = existing.quantity + qty))
        } else {
            holdings.insert(Holding(cardId = cardId, locationId = locationId, foil = foil, quantity = qty, sourceLocationId = source))
        }
    }

    // ---- backup ----

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(locations.all(), holdings.all())
    }

    /** Replaces all locations and holdings. Holdings of unknown cards are skipped; returns how many. */
    suspend fun restore(data: BackupData): Int = db.withTransaction {
        holdings.deleteAll()
        locations.deleteAllButGeneral()
        for (l in data.locations) {
            if (l.id == GENERAL_ID || l.kind == LocationKind.GENERAL) continue
            locations.insertWithId(l)
        }
        val known = locations.all().map { it.id }.toSet()
        var skipped = 0
        for (h in data.holdings) {
            if (h.locationId !in known || db.cards().byId(h.cardId) == null) {
                skipped += h.quantity
                continue
            }
            val source = h.sourceLocationId.takeIf { it in known } ?: Holding.NO_SOURCE
            addRow(h.cardId, h.locationId, h.foil, h.quantity, source)
        }
        skipped
    }
}

data class NewCopies(val cardId: String, val foil: Boolean, val qty: Int)

data class BackupData(val locations: List<Location>, val holdings: List<Holding>)
