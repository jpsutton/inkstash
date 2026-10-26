package io.github.jpsutton.inkstash.mesh

import io.github.jpsutton.inkstash.data.MeshMember
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** A member of the mesh as sent between devices. See [MeshRecords] for what is signed. */
@Serializable
data class MemberRecord(
    val nodeId: String,
    val name: String,
    val noiseKey: String,
    val signKey: String,
    val admittedBy: String,
    val admittedAt: Long,
    val signature: String,
    val removedBy: String? = null,
    val removedAt: Long? = null,
    val removalSignature: String? = null,
    val addresses: List<String> = emptyList(),
) {
    val removed: Boolean get() = removedBy != null

    fun toEntity() = MeshMember(
        nodeId, name, noiseKey, signKey, admittedBy, admittedAt, signature, removedBy, removedAt, removalSignature,
        addresses.joinToString(","),
    )

    companion object {
        fun of(m: MeshMember) = MemberRecord(
            m.nodeId, m.name, m.noiseKey, m.signKey, m.admittedBy, m.admittedAt, m.signature, m.removedBy, m.removedAt,
            m.removalSignature, m.addresses.split(',').filter { it.isNotBlank() },
        )
    }
}

/**
 * Who is in the mesh, provable by signatures:
 * - The founder signs their own record; the mesh's id is the founder's node id.
 * - Every other member's record is signed by the member who approved them.
 * - A removal (or a member leaving: they remove themselves) is signed by the remover.
 * A device checks every record against the members it already trusts, so knowing the passphrase is not enough
 * to be a member, and a member can't be given other keys by someone else.
 */
object MeshRecords {
    fun admissionBytes(meshId: String, r: MemberRecord): ByteArray =
        listOf("inkstash-admit-v1", meshId, r.nodeId, r.name, r.noiseKey, r.signKey, r.admittedBy, r.admittedAt.toString())
            .joinToString("\n").toByteArray()

    fun removalBytes(meshId: String, nodeId: String, removedBy: String, removedAt: Long): ByteArray =
        listOf("inkstash-remove-v1", meshId, nodeId, removedBy, removedAt.toString()).joinToString("\n").toByteArray()

    /** The founder's own record: starts the mesh. */
    fun founder(identity: MeshIdentity, now: Long): MemberRecord = admit(identity, identity.nodeId, identity.deviceName, identity.noiseKey.publicKey, identity.signPublic, now)

    /** Signs the admission of a device, as [approver]. */
    fun admit(approver: MeshIdentity, nodeId: String, name: String, noiseKey: ByteArray, signKey: ByteArray, now: Long): MemberRecord {
        val meshId = approver.meshId ?: approver.nodeId // the founder signs before it has a meshId stored
        val unsigned = MemberRecord(nodeId, name, noiseKey.toHex(), signKey.toHex(), approver.nodeId, now, "")
        return unsigned.copy(signature = approver.sign(admissionBytes(meshId, unsigned)).toHex())
    }

    fun remove(remover: MeshIdentity, meshId: String, target: MemberRecord, now: Long): MemberRecord =
        target.copy(
            removedBy = remover.nodeId, removedAt = now,
            removalSignature = remover.sign(removalBytes(meshId, target.nodeId, remover.nodeId, now)).toHex(),
        )

    private fun verifyAdmission(meshId: String, r: MemberRecord, trusted: Map<String, MemberRecord>): Boolean {
        val signer: ByteArray = if (r.admittedBy == r.nodeId) {
            // Only the founder signs their own record.
            if (r.nodeId != meshId) return false
            r.signKey.fromHex()
        } else {
            (trusted[r.admittedBy] ?: return false).signKey.fromHex()
        }
        return MeshIdentity.verify(signer, admissionBytes(meshId, r), r.signature.fromHex())
    }

    private fun verifyRemoval(meshId: String, r: MemberRecord, trusted: Map<String, MemberRecord>): Boolean {
        val by = r.removedBy ?: return false
        val at = r.removedAt ?: return false
        val sig = r.removalSignature ?: return false
        val remover = if (by == r.nodeId) r else trusted[by] ?: return false
        return MeshIdentity.verify(remover.signKey.fromHex(), removalBytes(meshId, r.nodeId, by, at), sig.fromHex())
    }

    /**
     * Merges [incoming] records into [known], keeping only what verifies. Records can arrive in any order:
     * one whose approver isn't known yet is retried after the others. Returns the new set.
     */
    fun accept(meshId: String, incoming: List<MemberRecord>, known: Map<String, MemberRecord>): Map<String, MemberRecord> {
        val out = known.toMutableMap()
        val queue = incoming.toMutableList()
        do {
            var progressed = false
            val it = queue.iterator()
            while (it.hasNext()) {
                val r = it.next()
                val have = out[r.nodeId]
                if (have == null) {
                    if (verifyAdmission(meshId, r, out)) {
                        out[r.nodeId] = r.copy(removedBy = null, removedAt = null, removalSignature = null)
                        progressed = true
                        // Fall through to the removal check on the next pass.
                    } else {
                        continue
                    }
                }
                var cur = out[r.nodeId]!!
                // A member who left or was removed can be admitted again: a newer, valid admission with the same keys.
                if (cur.removed && !r.removed && r.admittedAt > (cur.removedAt ?: 0) && cur.noiseKey == r.noiseKey &&
                    cur.signKey == r.signKey && verifyAdmission(meshId, r, out)
                ) {
                    out[r.nodeId] = r.copy(removedBy = null, removedAt = null, removalSignature = null)
                    cur = out[r.nodeId]!!
                    progressed = true
                }
                // The keys and admission can't change for a known id: ignore records that disagree.
                if (cur.signature != r.signature || cur.noiseKey != r.noiseKey || cur.signKey != r.signKey) {
                    it.remove()
                    continue
                }
                // A removal older than the member's latest admission has been undone by it.
                if (!cur.removed && r.removed && (r.removedAt ?: 0) > cur.admittedAt && verifyRemoval(meshId, r, out)) {
                    out[r.nodeId] = cur.copy(removedBy = r.removedBy, removedAt = r.removedAt, removalSignature = r.removalSignature)
                    progressed = true
                }
                if (r.addresses.isNotEmpty() && r.addresses != cur.addresses) out[r.nodeId] = out[r.nodeId]!!.copy(addresses = r.addresses)
                it.remove()
            }
        } while (progressed && queue.isNotEmpty())
        return out
    }

    /** The six digits two people compare on both screens: the same on both devices of one connection, whoever is in the middle changes it. */
    fun approvalCode(handshakeHash: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest("inkstash-code".toByteArray() + handshakeHash)
        val n = ((d[0].toLong() and 0xff) shl 24 or ((d[1].toLong() and 0xff) shl 16) or ((d[2].toLong() and 0xff) shl 8) or (d[3].toLong() and 0xff)) % 1_000_000
        return "%06d".format(n)
    }

    /** Short fingerprint of a Noise public key, for pinning in an invite. */
    fun fingerprint(noisePublicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(noisePublicKey).toHex()
}
