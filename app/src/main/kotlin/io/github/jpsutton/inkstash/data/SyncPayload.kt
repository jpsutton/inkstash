package io.github.jpsutton.inkstash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What one [SyncOp] changes. Place and quantity changes are kept so that, whatever order they arrive in,
 * every device ends up with the same collection:
 * - quantity changes are additions (`+2`, `-1`), which commute;
 * - a place is created once, renamed by the newest rename, and deleted for good.
 * A place is named by its `uid`, a card by its Lorcast id.
 */
@Serializable
sealed interface SyncPayload {
    @Serializable @SerialName("loc.create")
    data class LocCreate(val uid: String, val name: String, val kind: LocationKind, val createdAt: Long) : SyncPayload

    @Serializable @SerialName("loc.rename")
    data class LocRename(val uid: String, val name: String) : SyncPayload

    /** The place is gone. Copies a device still has in it (from changes made at the same time) go to General. */
    @Serializable @SerialName("loc.delete")
    data class LocDelete(val uid: String) : SyncPayload

    /** `delta` copies of `card` in place `loc`; `src` is the place a temp set borrowed them from, or "". */
    @Serializable @SerialName("qty")
    data class Qty(val card: String, val loc: String, val foil: Boolean, val src: String, val delta: Int) : SyncPayload

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }
        fun encode(p: SyncPayload): String = json.encodeToString(serializer(), p)
        fun decode(s: String): SyncPayload = json.decodeFromString(serializer(), s)
    }
}
