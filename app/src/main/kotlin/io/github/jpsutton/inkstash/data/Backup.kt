package io.github.jpsutton.inkstash.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON file format of "Save a backup". Card ids are Lorcast ids, so a backup survives a DB update. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long = 0,
    val locations: List<BackupLocation>,
    val holdings: List<BackupHolding>,
) {
    companion object {
        const val FORMAT = "inkstash-backup"
    }
}

@Serializable
data class BackupLocation(val id: Long, val name: String, val kind: String, val notes: String = "", val createdAt: Long = 0)

@Serializable
data class BackupHolding(val cardId: String, val locationId: Long, val foil: Boolean, val quantity: Int, val sourceLocationId: Long = 0)

object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(data: BackupData, exportedAt: Long): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = exportedAt,
            locations = data.locations.map { BackupLocation(it.id, it.name, it.kind.name, it.notes, it.createdAt) },
            holdings = data.holdings.map { BackupHolding(it.cardId, it.locationId, it.foil, it.quantity, it.sourceLocationId) },
        ),
    )

    /** Throws [IllegalArgumentException] if the text is not an inkstash backup. */
    fun decode(text: String): BackupData {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not an inkstash backup", e)
        }
        require(file.format == BackupFile.FORMAT) { "This is not an inkstash backup" }
        require(file.version <= 1) { "This backup comes from a newer version of inkstash" }
        return BackupData(
            locations = file.locations.map {
                Location(it.id, it.name, LocationKind.valueOf(it.kind), it.notes, it.createdAt)
            },
            holdings = file.holdings.filter { it.quantity > 0 }.map {
                Holding(cardId = it.cardId, locationId = it.locationId, foil = it.foil, quantity = it.quantity, sourceLocationId = it.sourceLocationId)
            },
        )
    }
}
