package io.github.jpsutton.inkstash.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshRecordsTest {
    private var n = 0
    private fun identity(name: String): MeshIdentity {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("id${n++}", Context.MODE_PRIVATE)
        return MeshIdentity(prefs, "node-$name", name)
    }

    private fun admit(approver: MeshIdentity, joiner: MeshIdentity, at: Long) =
        MeshRecords.admit(approver, joiner.nodeId, joiner.deviceName, joiner.noiseKey.publicKey, joiner.signPublic, at)

    /** A founder who started a mesh, and the founder's record. */
    private fun founder(): Pair<MeshIdentity, MemberRecord> {
        val a = identity("a")
        a.joinMesh(a.nodeId, "secret phrase")
        return a to MeshRecords.founder(a, 1)
    }

    @Test fun identityIsStable() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("stable", Context.MODE_PRIVATE)
        val one = MeshIdentity(prefs, "node-x", "Tablet")
        val two = MeshIdentity(prefs, "node-x", "Other")
        assertEquals(one.noiseKey.publicKey.toHex(), two.noiseKey.publicKey.toHex())
        assertEquals(one.signPublic.toHex(), two.signPublic.toHex())
        assertEquals("Tablet", two.deviceName)
    }

    @Test fun founderAndAdmittedMembersVerify() {
        val (a, fa) = founder()
        val b = identity("b")
        val c = identity("c")
        val fb = admit(a, b, 2)
        b.joinMesh(a.nodeId, "secret phrase")
        val fc = admit(b, c, 3) // approved by b, not by the founder
        val known = MeshRecords.accept(a.nodeId, listOf(fc, fb, fa), emptyMap()) // any order
        assertEquals(setOf("node-a", "node-b", "node-c"), known.keys)
    }

    @Test fun aForgedAdmissionIsRefused() {
        val (a, fa) = founder()
        val evil = identity("evil")
        val intruder = identity("intruder")
        // Signed by a device that was never admitted.
        val forged = MeshRecords.admit(evil, intruder.nodeId, "intruder", intruder.noiseKey.publicKey, intruder.signPublic, 5)
        val known = MeshRecords.accept(a.nodeId, listOf(fa, forged), emptyMap())
        assertEquals(setOf("node-a"), known.keys)
        // Claiming to be admitted by the founder without the founder's signature.
        val claimed = forged.copy(admittedBy = "node-a")
        assertEquals(setOf("node-a"), MeshRecords.accept(a.nodeId, listOf(fa, claimed), emptyMap()).keys)
    }

    @Test fun onlyTheFounderSignsTheirOwnRecord() {
        val (a, _) = founder()
        val b = identity("b")
        // b signs itself in, but the mesh's id is a's.
        val selfSigned = MeshRecords.admit(b, b.nodeId, "b", b.noiseKey.publicKey, b.signPublic, 2)
        assertTrue(MeshRecords.accept(a.nodeId, listOf(selfSigned), emptyMap()).isEmpty())
    }

    @Test fun aTamperedRecordIsRefused() {
        val (a, fa) = founder()
        val b = identity("b")
        val fb = admit(a, b, 2)
        val swapped = identity("swap")
        val tampered = fb.copy(noiseKey = swapped.noiseKey.publicKey.toHex())
        assertEquals(setOf("node-a"), MeshRecords.accept(a.nodeId, listOf(fa, tampered), emptyMap()).keys)
        val renamed = fb.copy(name = "someone else")
        assertEquals(setOf("node-a"), MeshRecords.accept(a.nodeId, listOf(fa, renamed), emptyMap()).keys)
    }

    @Test fun aKnownMembersKeysCannotBeReplaced() {
        val (a, fa) = founder()
        val b = identity("b")
        val fb = admit(a, b, 2)
        val known = MeshRecords.accept(a.nodeId, listOf(fa, fb), emptyMap())
        val other = identity("b") // same id, new keys (a reinstall, or an impostor)
        val replacement = admit(a, other, 9)
        val after = MeshRecords.accept(a.nodeId, listOf(replacement), known)
        assertEquals(fb.noiseKey, after["node-b"]!!.noiseKey)
        assertNotEquals(replacement.noiseKey, after["node-b"]!!.noiseKey)
    }

    @Test fun removalAndLeavingAreSignedAndVerified() {
        val (a, fa) = founder()
        val b = identity("b")
        val c = identity("c")
        val fb = admit(a, b, 2)
        b.joinMesh(a.nodeId, "secret phrase")
        val fc = admit(a, c, 3)
        var known = MeshRecords.accept(a.nodeId, listOf(fa, fb, fc), emptyMap())

        // A member removes another.
        val removedB = MeshRecords.remove(a, a.nodeId, fb, 10)
        known = MeshRecords.accept(a.nodeId, listOf(removedB), known)
        assertTrue(known["node-b"]!!.removed)
        assertEquals("node-a", known["node-b"]!!.removedBy)

        // A member leaves: removes itself.
        c.joinMesh(a.nodeId, "secret phrase")
        val left = MeshRecords.remove(c, a.nodeId, fc, 11)
        known = MeshRecords.accept(a.nodeId, listOf(left), known)
        assertTrue(known["node-c"]!!.removed)
        assertEquals("node-c", known["node-c"]!!.removedBy)

        // The founder can't be removed by someone who isn't a member.
        val stranger = identity("stranger")
        val bogus = MeshRecords.remove(stranger, a.nodeId, fa, 12)
        assertFalse(MeshRecords.accept(a.nodeId, listOf(bogus), known)["node-a"]!!.removed)
    }

    @Test fun aForgedRemovalIsIgnored() {
        val (a, fa) = founder()
        val b = identity("b")
        val fb = admit(a, b, 2)
        val known = MeshRecords.accept(a.nodeId, listOf(fa, fb), emptyMap())
        val forged = fb.copy(removedBy = "node-a", removedAt = 5, removalSignature = "00".repeat(64))
        assertFalse(MeshRecords.accept(a.nodeId, listOf(forged), known)["node-b"]!!.removed)
    }

    @Test fun approvalCodeIsSixDigitsAndDependsOnTheHandshake() {
        val h1 = ByteArray(32) { it.toByte() }
        val h2 = ByteArray(32) { (it + 1).toByte() }
        val c1 = MeshRecords.approvalCode(h1)
        assertTrue(c1.matches(Regex("\\d{6}")))
        assertEquals(c1, MeshRecords.approvalCode(h1))
        assertNotEquals(c1, MeshRecords.approvalCode(h2))
    }
}
