package io.github.jpsutton.inkstash.mesh

import io.github.jpsutton.inkstash.data.SyncOp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One change in the operation log, on the wire. */
@Serializable
data class WireOp(val origin: String, val seq: Long, val hlc: Long, val payload: String) {
    fun toOp() = SyncOp(origin, seq, hlc, payload)

    companion object {
        fun of(op: SyncOp) = WireOp(op.origin, op.seq, op.hlc, op.payload)
    }
}

/** Messages inside the encrypted channel, one JSON object each. */
@Serializable
sealed interface MeshMessage {
    /**
     * First message each way. [joining] devices have no mesh yet ([meshId] null) and ask to be admitted;
     * [invite] is the one-time token from a QR code.
     */
    @Serializable @SerialName("hello")
    data class Hello(
        val meshId: String?,
        val nodeId: String,
        val name: String,
        val signKey: String,
        val port: Int,
        val addresses: List<String> = emptyList(),
        val members: List<MemberRecord> = emptyList(),
        val joining: Boolean = false,
        val invite: String? = null,
    ) : MeshMessage

    /** The member that was asked is waiting for a person to approve. [code] is what both screens show. */
    @Serializable @SerialName("pending")
    data class JoinPending(val code: String) : MeshMessage

    /** You are in. [members] holds every record, yours included, signed by whoever approved you. */
    @Serializable @SerialName("admitted")
    data class Admitted(val meshId: String, val members: List<MemberRecord>) : MeshMessage

    @Serializable @SerialName("declined")
    data object Declined : MeshMessage

    @Serializable @SerialName("refused")
    data class Refused(val reason: String, val members: List<MemberRecord> = emptyList()) : MeshMessage

    /** "This is what I have": highest operation per device. The other side sends what is missing. */
    @Serializable @SerialName("vector")
    data class Vector(val v: Map<String, Long>) : MeshMessage

    @Serializable @SerialName("ops")
    data class Ops(val ops: List<WireOp>) : MeshMessage

    @Serializable @SerialName("members")
    data class Members(val records: List<MemberRecord>) : MeshMessage

    @Serializable @SerialName("bye")
    data object Bye : MeshMessage

    companion object {
        private val json = Json { classDiscriminator = "t"; ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(m: MeshMessage): ByteArray = json.encodeToString(serializer(), m).toByteArray()
        fun decode(b: ByteArray): MeshMessage = json.decodeFromString(serializer(), String(b))
    }
}

/**
 * What a QR code carries so a new device can join without typing: where to connect, the passphrase, a
 * fingerprint of the inviting device's key (the new device checks it, so it can't be tricked into talking to
 * someone else) and a one-time token that makes the inviting device approve the join by itself. It expires.
 * Anyone who sees the code while it is valid can join, so show it only to the device you mean to add.
 */
@Serializable
data class MeshInvite(
    val meshId: String,
    val addresses: List<String>,
    val passphrase: String,
    val fingerprint: String,
    val token: String,
    val expiresAt: Long,
) {
    fun encode(): String = PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    companion object {
        const val PREFIX = "inkstash-join:"

        /** Null if [text] isn't an invite. */
        fun decode(text: String): MeshInvite? = try {
            if (!text.startsWith(PREFIX)) null
            else Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), String(java.util.Base64.getUrlDecoder().decode(text.removePrefix(PREFIX))))
        } catch (e: Exception) {
            null
        }
    }
}
