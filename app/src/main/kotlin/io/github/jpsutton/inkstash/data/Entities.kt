package io.github.jpsutton.inkstash.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A card in the known-card DB. `id` is the Lorcast card id. */
@Entity(tableName = "Card", indices = [Index(value = ["setCode", "collectorNumber"], unique = true), Index("name")])
data class Card(
    @PrimaryKey val id: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val name: String,
    val version: String,
    val ink: String,
    val inkable: Boolean,
    val cost: Int,
    val type: String,
    val rarity: String,
    val imageSmall: String?,
    val imageNormal: String?,
    val imageLarge: String?,
    /** Estimated price in US dollars per copy (Lorcast), or null when it has none. */
    val priceUsd: Double? = null,
    val priceFoilUsd: Double? = null,
) {
    /** "Name - Version", or just the name for cards without a version. */
    val fullName: String get() = if (version.isBlank()) name else "$name – $version"
}

enum class LocationKind { GENERAL, POOL, TEMP_SET }

/**
 * A place that holds cards. `uid` is the same on every device in a mesh (the local `id` is not).
 * Deleting a place never removes the row: it sets `deleted`, so late changes from other devices can
 * still find it and be redirected (see [SyncStore]).
 */
@Entity(tableName = "Location", indices = [Index(value = ["uid"], unique = true)])
data class Location(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: LocationKind,
    val notes: String = "",
    val createdAt: Long = 0,
    val uid: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    /** Clock of the newest rename applied, so the newest rename wins on every device. */
    val nameHlc: Long = 0,
    val nameOrigin: String = "",
)

/**
 * `copies` of one card (one foil state) in one location. `sourceLocationId` is only set (non-zero) in
 * TEMP_SET rows: the place the copies were borrowed from. 0 stands for "none" (not NULL, so the
 * unique index below also catches duplicates).
 */
@Entity(
    tableName = "Holding",
    // No foreign key to Card: a device whose card database lacks a card still keeps its copies, which show
    // once the database knows the card. Quantities may sit at 0 or below until other devices' changes arrive.
    foreignKeys = [ForeignKey(Location::class, ["id"], ["locationId"])],
    indices = [
        Index(value = ["cardId", "locationId", "foil", "sourceLocationId"], unique = true),
        Index("locationId"),
    ],
)
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val locationId: Long,
    val foil: Boolean,
    val quantity: Int,
    @ColumnInfo(defaultValue = "0") val sourceLocationId: Long = NO_SOURCE,
) {
    companion object {
        const val NO_SOURCE = 0L
    }
}

/** A card with how many copies are owned (all locations or one). */
data class OwnedCard(
    @Embedded val card: Card,
    val qty: Int,
    val foilQty: Int,
)

/** A holding joined with its card, for lists. */
data class HoldingWithCard(
    @Embedded val holding: Holding,
    @Embedded(prefix = "c_") val card: Card,
)

data class LocationSummary(
    @Embedded val location: Location,
    val cardCount: Int,
)

const val GENERAL_ID = 1L

const val GENERAL_UID = "general"

/** One change to the collection, from one device. Devices exchange these and replay them. */
@Entity(tableName = "SyncOp", primaryKeys = ["origin", "seq"], indices = [Index("applied")])
data class SyncOp(
    /** The device that made the change. */
    val origin: String,
    /** 1, 2, 3... per origin; gaps never occur in a device's log. */
    val seq: Long,
    /** Hybrid logical clock: milliseconds shifted left 10 bits, plus a counter. Orders changes across devices. */
    val hlc: Long,
    /** The change, as JSON (see [SyncPayload]). */
    val payload: String,
    /** False while the change waits for something that hasn't arrived yet, such as the place it refers to. */
    val applied: Boolean = true,
)

/** A device admitted to the mesh, with the signed records that prove it (see MeshMembers). */
@Entity(tableName = "MeshMember")
data class MeshMember(
    @PrimaryKey val nodeId: String,
    val name: String,
    /** Noise public key, hex. */
    val noiseKey: String,
    /** Ed25519 public key, hex. */
    val signKey: String,
    val admittedBy: String,
    val admittedAt: Long,
    /** Ed25519 signature over the admission record, hex. */
    val signature: String,
    val removedBy: String? = null,
    val removedAt: Long? = null,
    val removalSignature: String? = null,
    /** Addresses this device says it can be reached at, comma separated. */
    val addresses: String = "",
)
