package io.github.jpsutton.inkstash.data

import androidx.room.withTransaction
import io.github.jpsutton.inkstash.data.SyncPayload.LocCreate
import io.github.jpsutton.inkstash.data.SyncPayload.LocDelete
import io.github.jpsutton.inkstash.data.SyncPayload.LocRename
import io.github.jpsutton.inkstash.data.SyncPayload.Qty

/**
 * The operation log and the rules that turn it into the collection tables.
 *
 * Every change, local or from another device, goes through [apply], so all devices run the same code on
 * the same operations and converge, whatever order operations arrive in:
 * - **Quantities** are added up per (card, place, foil, source). Rows may go to 0 or below for a while.
 * - **Rename**: the newest (clock, device) wins.
 * - **Delete**: the place is marked deleted. Copies still in it, and copies added to it later, go home: to the
 *   place a temp set borrowed them from, else General.
 * - **A change that refers to something not here yet** (a place created on a third device) waits, unapplied,
 *   and is retried after every batch.
 *
 * Devices exchange operations by version vector: for each device, the highest `seq` held. Operations are
 * only accepted in order per device, so a vector always means "everything up to this".
 */
class SyncStore(private val db: InkstashDatabase, val nodeId: String, private val clock: () -> Long = System::currentTimeMillis) {
    private val ops = db.syncOps()
    private val locations = db.locations()
    private val holdings = db.holdings()

    /** Records changes made on this device and applies them. Call inside a transaction (see [CollectionRepository]). */
    suspend fun record(payloads: List<SyncPayload>, applyNow: Boolean = true) {
        var seq = ops.maxSeq(nodeId)
        var hlc = ops.maxHlc()
        for (p in payloads) {
            seq += 1
            hlc = maxOf(hlc + 1, clock() shl 10)
            val op = SyncOp(nodeId, seq, hlc, SyncPayload.encode(p))
            // Not applying means the tables already show this change (see baseline). If it can't be applied,
            // keep it pending: it retries after the next batch.
            ops.insert(if (!applyNow || apply(op)) op else op.copy(applied = false))
        }
    }

    /**
     * Takes operations received from a peer: new ones, in order per device, are stored and applied.
     * Returns how many were new.
     */
    suspend fun receive(incoming: List<SyncOp>): Int = db.withTransaction {
        var fresh = 0
        for (op in incoming.sortedWith(compareBy({ it.origin }, { it.seq }))) {
            if (op.origin == nodeId) continue
            if (op.seq != ops.maxSeq(op.origin) + 1) continue
            ops.insert(op.copy(applied = false))
            fresh++
        }
        if (fresh > 0) retryPending()
        fresh
    }

    /**
     * Writes the collection as it is now into the log, without applying it, for a device that had a collection
     * before it kept a log. Run once, before the first change is recorded or received.
     */
    suspend fun baseline() = db.withTransaction {
        if (ops.count() > 0) return@withTransaction
        val all = db.locations().all()
        val byId = all.associateBy { it.id }
        val payloads = mutableListOf<SyncPayload>()
        for (l in all) if (l.kind != LocationKind.GENERAL) payloads += LocCreate(l.uid, l.name, l.kind, l.createdAt)
        for (h in holdings.all()) {
            val loc = byId[h.locationId] ?: continue
            val src = if (h.sourceLocationId == Holding.NO_SOURCE) "" else byId[h.sourceLocationId]?.uid ?: ""
            payloads += Qty(h.cardId, loc.uid, h.foil, src, h.quantity)
        }
        record(payloads, applyNow = false)
    }

    /** Applies waiting operations until none more can be applied. */
    suspend fun retryPending() {
        do {
            var progressed = false
            for (op in ops.pending()) {
                if (apply(op)) {
                    ops.markApplied(op.origin, op.seq)
                    progressed = true
                }
            }
        } while (progressed)
    }

    suspend fun vector(): Map<String, Long> = ops.vector().associate { it.origin to it.seq }

    /** What a peer with [theirs] is missing, up to [limit] operations. */
    suspend fun missingFor(theirs: Map<String, Long>, limit: Int = 500): List<SyncOp> {
        val out = mutableListOf<SyncOp>()
        for ((origin, mine) in vector()) {
            val have = theirs[origin] ?: 0
            if (mine > have) out += ops.after(origin, have, limit - out.size)
            if (out.size >= limit) break
        }
        return out
    }

    /** Applies one operation to the tables. Returns false when it must wait for something. */
    private suspend fun apply(op: SyncOp): Boolean = when (val p = SyncPayload.decode(op.payload)) {
        is LocCreate -> {
            if (locations.byUid(p.uid) == null) {
                locations.insert(
                    Location(name = p.name, kind = p.kind, createdAt = p.createdAt, uid = p.uid, nameHlc = op.hlc, nameOrigin = op.origin),
                )
            }
            true
        }
        is LocRename -> {
            val loc = locations.byUid(p.uid)
            if (loc == null) false else {
                if (loc.kind != LocationKind.GENERAL && newer(op.hlc, op.origin, loc.nameHlc, loc.nameOrigin)) {
                    locations.update(loc.copy(name = p.name, nameHlc = op.hlc, nameOrigin = op.origin))
                }
                true
            }
        }
        is LocDelete -> {
            val loc = locations.byUid(p.uid)
            when {
                loc == null -> false
                loc.kind == LocationKind.GENERAL || loc.deleted -> true
                else -> {
                    locations.update(loc.copy(deleted = true))
                    sweep(loc)
                    true
                }
            }
        }
        is Qty -> {
            val loc = locations.byUid(p.loc)
            val src = if (p.src.isEmpty()) null else locations.byUid(p.src)
            if (loc == null || (p.src.isNotEmpty() && src == null)) false else {
                addRow(p.card, resolve(loc, src), p.foil, p.delta)
                true
            }
        }
    }

    /** Where a quantity change for [loc] really lands: in [loc], or home when [loc] is deleted. */
    private suspend fun resolve(loc: Location, src: Location?): Target {
        val sourceId = when {
            src == null -> Holding.NO_SOURCE
            src.deleted -> GENERAL_ID // the place it was borrowed from is gone
            else -> src.id
        }
        if (!loc.deleted) return Target(loc.id, sourceId)
        return Target(home(loc, sourceId), Holding.NO_SOURCE)
    }

    /** A deleted temp set sends copies back to the place they were borrowed from, if it is still there; else General. */
    private suspend fun home(deleted: Location, sourceId: Long): Long {
        if (deleted.kind == LocationKind.TEMP_SET && sourceId != Holding.NO_SOURCE) {
            val s = locations.byId(sourceId)
            if (s != null && !s.deleted) return s.id
        }
        return GENERAL_ID
    }

    /** Moves everything left in a place that was just deleted to where it belongs. */
    private suspend fun sweep(loc: Location) {
        for (h in holdings.nonZeroInLocation(loc.id)) {
            addRowRaw(h.cardId, h.locationId, h.foil, h.sourceLocationId, -h.quantity)
            addRowRaw(h.cardId, home(loc, h.sourceLocationId), h.foil, Holding.NO_SOURCE, h.quantity)
        }
        // Temp sets that borrowed from this place now point at General.
        for (h in holdings.borrowedFrom(loc.id)) {
            addRowRaw(h.cardId, h.locationId, h.foil, h.sourceLocationId, -h.quantity)
            addRowRaw(h.cardId, h.locationId, h.foil, GENERAL_ID, h.quantity)
        }
    }

    private class Target(val locationId: Long, val sourceId: Long)

    private suspend fun addRow(card: String, t: Target, foil: Boolean, delta: Int) =
        addRowRaw(card, t.locationId, foil, t.sourceId, delta)

    private suspend fun addRowRaw(card: String, locationId: Long, foil: Boolean, sourceId: Long, delta: Int) {
        if (delta == 0) return
        val existing = holdings.find(card, locationId, foil, sourceId)
        if (existing != null) holdings.update(existing.copy(quantity = existing.quantity + delta))
        else holdings.insert(Holding(cardId = card, locationId = locationId, foil = foil, quantity = delta, sourceLocationId = sourceId))
    }

    private fun newer(hlc: Long, origin: String, otherHlc: Long, otherOrigin: String) =
        hlc > otherHlc || (hlc == otherHlc && origin > otherOrigin)
}
