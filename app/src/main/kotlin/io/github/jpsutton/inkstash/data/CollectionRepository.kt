package io.github.jpsutton.inkstash.data

import androidx.room.withTransaction
import io.github.jpsutton.inkstash.data.SyncPayload.LocCreate
import io.github.jpsutton.inkstash.data.SyncPayload.LocDelete
import io.github.jpsutton.inkstash.data.SyncPayload.LocRename
import io.github.jpsutton.inkstash.data.SyncPayload.Qty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * All changes to the collection. Each operation is one Room transaction. It is recorded in the operation
 * log ([SyncStore]) and applied through the same code that applies other devices' changes, so devices in a
 * mesh converge. Nothing creates or destroys copies except [add], [remove] and [restore]: moves, dissolves
 * and deletes conserve the total.
 */
class CollectionRepository(
    private val db: InkstashDatabase,
    val sync: SyncStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val holdings = db.holdings()
    private val locations = db.locations()

    private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after every change made on this device, so the mesh can send it on. */
    val localChanges: SharedFlow<Unit> = _localChanges

    fun observeOwned(locationId: Long = -1): Flow<List<OwnedCard>> = holdings.observeOwned(locationId)
    fun observeHoldingsForCard(cardId: String): Flow<List<Holding>> = holdings.observeForCard(cardId)
    fun observeInLocation(locationId: Long): Flow<List<HoldingWithCard>> = holdings.observeInLocation(locationId)
    fun observeLocations(): Flow<List<Location>> = locations.observeAll()
    fun observeSummaries(): Flow<List<LocationSummary>> = locations.observeSummaries()
    fun observeLocation(id: Long): Flow<Location?> = locations.observe(id)

    suspend fun totalCopies(): Int = holdings.totalCopies()

    suspend fun createLocation(name: String, kind: LocationKind): Long {
        require(kind != LocationKind.GENERAL) { "There is only one General location" }
        val uid = java.util.UUID.randomUUID().toString()
        db.withTransaction { sync.record(listOf(LocCreate(uid, name.trim(), kind, now()))) }
        changed()
        return locations.byUid(uid)!!.id
    }

    suspend fun rename(locationId: Long, name: String) {
        db.withTransaction {
            val loc = locations.byId(locationId) ?: return@withTransaction
            if (loc.kind == LocationKind.GENERAL || loc.deleted) return@withTransaction
            sync.record(listOf(LocRename(loc.uid, name.trim())))
        }
        changed()
    }

    /** Adds copies to a location (a normal pool, General, or a temp set with no source). */
    suspend fun add(cardId: String, foil: Boolean, qty: Int, locationId: Long) {
        require(qty > 0)
        addAll(listOf(NewCopies(cardId, foil, qty)), locationId)
    }

    suspend fun addAll(entries: List<NewCopies>, locationId: Long) {
        db.withTransaction {
            val loc = locations.byId(locationId) ?: return@withTransaction
            sync.record(entries.filter { it.qty > 0 }.map { Qty(it.cardId, loc.uid, it.foil, "", it.qty) })
        }
        changed()
    }

    /** Removes `qty` copies from a holding row. */
    suspend fun remove(holdingId: Long, qty: Int) {
        require(qty > 0)
        db.withTransaction {
            val h = holdings.byId(holdingId) ?: return@withTransaction
            if (h.quantity <= 0) return@withTransaction
            sync.record(listOf(qty(h, -minOf(qty, h.quantity))))
        }
        changed()
    }

    /**
     * Moves `qty` copies of a holding to another location. Moving into a TEMP_SET records where they
     * came from: the row's own source if it is already borrowed, else the place they leave.
     */
    suspend fun move(holdingId: Long, qty: Int, toLocationId: Long) {
        require(qty > 0)
        db.withTransaction { moveInTransaction(holdingId, qty, toLocationId) }
        changed()
    }

    private suspend fun moveInTransaction(holdingId: Long, qty: Int, toLocationId: Long) {
        val h = holdings.byId(holdingId) ?: return
        if (h.locationId == toLocationId || h.quantity <= 0) return
        val to = locations.byId(toLocationId)?.takeIf { !it.deleted } ?: return
        val n = minOf(qty, h.quantity)
        val source = if (to.kind == LocationKind.TEMP_SET) {
            if (h.sourceLocationId != Holding.NO_SOURCE) h.sourceLocationId else h.locationId
        } else {
            Holding.NO_SOURCE
        }
        sync.record(listOf(qty(h, -n), Qty(h.cardId, to.uid, h.foil, uid(source), n)))
    }

    /**
     * Borrows one copy of a card into a temp set, from where the most copies are: General first, then
     * pools. Copies already in a temp set are left alone. Returns false if no copy is free.
     */
    suspend fun borrowOne(cardId: String, tempSetId: Long): Boolean {
        val ok = db.withTransaction {
            val rows = holdings.forCard(cardId).filter { it.locationId != tempSetId }
            val free = rows.mapNotNull { h -> locations.byId(h.locationId)?.takeIf { it.kind != LocationKind.TEMP_SET }?.let { h to it } }
            val pick = free.maxWithOrNull(compareBy({ it.second.kind == LocationKind.GENERAL }, { it.first.quantity }))?.first
                ?: return@withTransaction false
            moveInTransaction(pick.id, 1, tempSetId)
            true
        }
        if (ok) changed()
        return ok
    }

    /** Sends one copy of a temp set holding back to its source (General if gone). */
    suspend fun returnOne(holdingId: Long) {
        db.withTransaction {
            val h = holdings.byId(holdingId) ?: return@withTransaction
            moveInTransaction(holdingId, 1, homeOf(h))
        }
        changed()
    }

    /** Returns every copy to its source (General if the source is gone) and deletes the temp set. */
    suspend fun dissolveTempSet(id: Long) {
        db.withTransaction { dissolveInTransaction(id) }
        changed()
    }

    private suspend fun homeOf(h: Holding): Long {
        val s = if (h.sourceLocationId == Holding.NO_SOURCE) null else locations.byId(h.sourceLocationId)
        return if (s != null && !s.deleted) s.id else GENERAL_ID
    }

    private suspend fun dissolveInTransaction(id: Long) {
        val set = locations.byId(id) ?: return
        if (set.kind != LocationKind.TEMP_SET || set.deleted) return
        val payloads = mutableListOf<SyncPayload>()
        for (h in holdings.inLocation(id)) {
            payloads += qty(h, -h.quantity)
            payloads += Qty(h.cardId, uid(homeOf(h)), h.foil, "", h.quantity)
        }
        payloads += LocDelete(set.uid)
        sync.record(payloads)
    }

    /**
     * Deletes a pool (its cards go to General) or a temp set (dissolved). General cannot be deleted.
     * Temp sets that borrowed from the pool now point at General.
     */
    suspend fun deleteLocation(id: Long) {
        db.withTransaction {
            val loc = locations.byId(id) ?: return@withTransaction
            if (loc.deleted) return@withTransaction
            when (loc.kind) {
                LocationKind.GENERAL -> return@withTransaction
                LocationKind.TEMP_SET -> dissolveInTransaction(id)
                LocationKind.POOL -> {
                    val payloads = mutableListOf<SyncPayload>()
                    for (h in holdings.inLocation(id)) {
                        payloads += qty(h, -h.quantity)
                        payloads += Qty(h.cardId, GENERAL_UID, h.foil, "", h.quantity)
                    }
                    for (h in holdings.borrowedFrom(id)) {
                        payloads += qty(h, -h.quantity)
                        payloads += Qty(h.cardId, uid(h.locationId), h.foil, GENERAL_UID, h.quantity)
                    }
                    payloads += LocDelete(loc.uid)
                    sync.record(payloads)
                }
            }
        }
        changed()
    }

    // ---- backup ----

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(locations.all(), holdings.all())
    }

    /**
     * Replaces all locations and holdings, as changes that every device in a mesh then applies too.
     * Holdings of unknown cards are skipped; returns how many copies.
     */
    suspend fun restore(data: BackupData): Int {
        val skipped = db.withTransaction {
            val payloads = mutableListOf<SyncPayload>()
            for (h in holdings.all()) payloads += qty(h, -h.quantity)
            for (l in locations.all()) if (l.kind != LocationKind.GENERAL) payloads += LocDelete(l.uid)
            // Backup ids are local to the device that saved it: give every place a new uid.
            val uids = mutableMapOf<Long, String>()
            for (l in data.locations) {
                if (l.kind == LocationKind.GENERAL || l.id == GENERAL_ID) {
                    uids[l.id] = GENERAL_UID
                } else {
                    val u = java.util.UUID.randomUUID().toString()
                    uids[l.id] = u
                    payloads += LocCreate(u, l.name, l.kind, l.createdAt)
                }
            }
            uids[GENERAL_ID] = GENERAL_UID
            var skipped = 0
            for (h in data.holdings) {
                val loc = uids[h.locationId]
                if (loc == null || db.cards().byId(h.cardId) == null) {
                    skipped += h.quantity
                    continue
                }
                val source = if (h.sourceLocationId == Holding.NO_SOURCE) "" else uids[h.sourceLocationId] ?: ""
                payloads += Qty(h.cardId, loc, h.foil, source, h.quantity)
            }
            sync.record(payloads)
            skipped
        }
        changed()
        return skipped
    }

    // ---- helpers ----

    private suspend fun uid(locationId: Long): String =
        if (locationId == Holding.NO_SOURCE) "" else locations.byId(locationId)?.uid ?: ""

    private suspend fun qty(h: Holding, delta: Int): Qty = Qty(h.cardId, uid(h.locationId), h.foil, uid(h.sourceLocationId), delta)

    private fun changed() {
        _localChanges.tryEmit(Unit)
    }
}

data class NewCopies(val cardId: String, val foil: Boolean, val qty: Int)

data class BackupData(val locations: List<Location>, val holdings: List<Holding>)
