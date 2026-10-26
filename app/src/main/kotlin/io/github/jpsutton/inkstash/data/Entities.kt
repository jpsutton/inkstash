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

@Entity(tableName = "Location")
data class Location(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: LocationKind,
    val notes: String = "",
    val createdAt: Long = 0,
)

/**
 * `copies` of one card (one foil state) in one location. `sourceLocationId` is only set (non-zero) in
 * TEMP_SET rows: the place the copies were borrowed from. 0 stands for "none" (not NULL, so the
 * unique index below also catches duplicates).
 */
@Entity(
    tableName = "Holding",
    foreignKeys = [
        ForeignKey(Card::class, ["id"], ["cardId"]),
        ForeignKey(Location::class, ["id"], ["locationId"], onDelete = ForeignKey.CASCADE),
    ],
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
