package io.github.jpsutton.inkstash.mesh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.jpsutton.inkstash.data.Card
import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.GENERAL_ID
import io.github.jpsutton.inkstash.data.InkstashDatabase
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.data.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real nodes over loopback sockets, each with its own database and keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshNodeTest {
    private val pass = "correct horse battery"
    private val nodes = mutableListOf<TestNode>()
    private var n = 0

    private inner class TestNode(val name: String) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db: InkstashDatabase = Room.inMemoryDatabaseBuilder(context, InkstashDatabase::class.java)
            .addCallback(InkstashDatabase.seedCallback).allowMainThreadQueries().build()
        val identity = MeshIdentity(context.getSharedPreferences("mesh${n++}", Context.MODE_PRIVATE), "node-$name", name).also { it.listenPort = 0 }
        val sync = SyncStore(db, identity.nodeId)
        val repo = CollectionRepository(db, sync)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        lateinit var node: MeshNode

        init {
            node = MeshNode(identity, db, sync, repo, scope, connectIntervalMs = 100, addressProvider = { p -> listOf("127.0.0.1:$p") })
            runBlocking {
                db.cards().upsertAll(listOf("a", "b", "c").map {
                    Card(it, "T", "Set", it, it.uppercase(), "v", "Amber", true, 1, "Character", "Rare", null, null, null)
                })
            }
            nodes += this
        }

        val address: String get() = "127.0.0.1:${node.boundPort}"
        val state get() = node.state.value
        fun copies(): Int = runBlocking { db.holdings().totalCopies() }
        fun stop() { node.stop(); scope.cancel(); db.close() }
    }

    @After fun tearDown() = nodes.forEach { runCatching { it.stop() } }

    private fun await(what: String, timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(40)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** Founder with its listener up. */
    private fun founder(name: String = "a"): TestNode {
        val a = TestNode(name)
        runBlocking { a.node.startMesh(pass) }
        await("$name listening") { a.node.boundPort != 0 }
        return a
    }

    /** Joins [joiner] to [host] by typing the address, approving on the host. */
    private fun joinAndApprove(joiner: TestNode, host: TestNode) {
        val result = java.util.concurrent.CompletableFuture<Boolean>()
        Thread { result.complete(runBlocking { joiner.node.join(host.address, pass) }) }.start()
        await("request shows on ${host.name}") { host.state.pending.isNotEmpty() }
        val request = host.state.pending.single()
        // Both screens show the same six digits.
        await("code shows on ${joiner.name}") { joiner.state.join is JoinProgress.WaitingForApproval }
        assertEquals(request.code, (joiner.state.join as JoinProgress.WaitingForApproval).code)
        host.node.approve(request.nodeId)
        assertTrue(result.get(20, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun aDeviceJoinsByTypingAnAddressAndApproval() {
        val a = founder()
        val b = TestNode("b")
        joinAndApprove(b, a)
        assertTrue(b.identity.inMesh)
        await("both see each other online") {
            a.state.members.singleOrNull()?.online == true && b.state.members.singleOrNull()?.online == true
        }
        assertEquals("b", a.state.members.single().record.name)
        assertEquals("a", b.state.members.single().record.name)
        assertTrue(a.state.pending.isEmpty())
    }

    @Test fun collectionsSyncBothWaysAndAnExistingOneIsShared() {
        val a = founder()
        runBlocking { a.repo.add("a", false, 2, GENERAL_ID) }
        val b = TestNode("b")
        // B had cards before it joined: they must join the mesh too.
        runBlocking { b.repo.add("c", true, 3, GENERAL_ID) }
        joinAndApprove(b, a)
        await("A has B's cards and B has A's") { a.copies() == 5 && b.copies() == 5 }

        runBlocking { a.repo.createLocation("Trade", LocationKind.POOL) }
        await("B sees the pool") { runBlocking { b.db.locations().all().any { it.name == "Trade" } } }
        runBlocking { b.repo.add("b", false, 1, b.db.locations().all().first { it.name == "Trade" }.id) }
        await("A gets B's new card") { a.copies() == 6 }
    }

    @Test fun aWrongPassphraseIsRefused() {
        val a = founder()
        val b = TestNode("b")
        assertFalse(runBlocking { b.node.join(a.address, "not the passphrase") })
        val failed = b.state.join as JoinProgress.Failed
        assertTrue(failed.message, failed.message.contains("passphrase"))
        assertFalse(b.identity.inMesh)
        assertTrue(a.state.pending.isEmpty())
    }

    @Test fun declineKeepsTheDeviceOut() {
        val a = founder()
        val b = TestNode("b")
        val result = java.util.concurrent.CompletableFuture<Boolean>()
        Thread { result.complete(runBlocking { b.node.join(a.address, pass) }) }.start()
        await("request") { a.state.pending.isNotEmpty() }
        a.node.decline(a.state.pending.single().nodeId)
        assertFalse(result.get(20, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue((b.state.join as JoinProgress.Failed).message.contains("declined"))
        assertTrue(a.state.members.isEmpty())
    }

    @Test fun aQrInviteJoinsWithoutApproval() {
        val a = founder()
        val b = TestNode("b")
        val invite = MeshInvite.decode(a.node.createInvite()!!.encode())!! // through its text form, as the QR carries it
        assertTrue(runBlocking { b.node.join("", "", invite) })
        assertTrue(a.state.pending.isEmpty())
        await("online") { a.state.members.singleOrNull()?.online == true }
        // One use only: the same code can't add another device by itself. It becomes a request a person must approve.
        val c = TestNode("c")
        val second = java.util.concurrent.CompletableFuture<Boolean>()
        Thread { second.complete(runBlocking { c.node.join("", "", invite) }) }.start()
        await("second use waits for a person") { a.state.pending.isNotEmpty() }
        a.node.decline(a.state.pending.single().nodeId)
        assertFalse(second.get(20, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun anInviteForADifferentDeviceKeyIsRefused() {
        val a = founder()
        val b = TestNode("b")
        val invite = a.node.createInvite()!!.copy(fingerprint = "00".repeat(32))
        assertFalse(runBlocking { b.node.join("", "", invite) })
        assertTrue((b.state.join as JoinProgress.Failed).message.contains("isn't the device"))
        assertFalse(b.identity.inMesh)
    }

    @Test fun everyFailedInviteAddressIsReported() {
        val a = founder()
        val b = TestNode("b")
        val invite = a.node.createInvite()!!.copy(addresses = listOf("127.0.0.1:1", "127.0.0.1:2"))
        assertFalse(runBlocking { b.node.join("", "", invite) })
        val message = (b.state.join as JoinProgress.Failed).message
        assertTrue(message, message.contains("127.0.0.1:1: ") && message.contains("127.0.0.1:2: "))
    }

    @Test fun theCarrierTranslatorAddressIsNotShared() {
        fun clat(ip: String) = MeshNode.isClatAddress(java.net.InetAddress.getByName(ip))
        assertTrue(clat("192.0.0.4"))
        assertTrue(clat("192.0.0.0"))
        assertFalse(clat("192.0.0.8"))
        assertFalse(clat("192.0.1.4"))
        assertFalse(clat("192.168.1.102"))
    }

    @Test fun anExpiredInviteNeedsApprovalInstead() {
        val a = founder()
        val b = TestNode("b")
        val invite = a.node.createInvite()!!.let { it.copy(token = "expired-token") } // a token the host never issued
        val result = java.util.concurrent.CompletableFuture<Boolean>()
        Thread { result.complete(runBlocking { b.node.join("", "", invite) }) }.start()
        await("falls back to a request a person must approve") { a.state.pending.isNotEmpty() }
        a.node.decline(a.state.pending.single().nodeId)
        assertFalse(result.get(20, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun removingADeviceStopsItAndItKeepsItsCopy() {
        val a = founder()
        val b = TestNode("b")
        runBlocking { b.repo.add("a", false, 2, GENERAL_ID) }
        joinAndApprove(b, a)
        await("synced") { a.copies() == 2 }
        runBlocking { a.node.remove("node-b") }
        await("B stops itself") { !b.identity.inMesh }
        assertEquals(2, b.copies())
        assertEquals("This device was removed from the mesh", b.state.notice)
        assertTrue(a.state.members.isEmpty())
        // It can't just reconnect: it no longer has the passphrase and its record says removed.
        runBlocking { a.repo.add("c", false, 1, GENERAL_ID) }
        Thread.sleep(500)
        assertEquals(2, b.copies())
    }

    @Test fun leavingTellsTheOthersAndKeepsTheCollection() {
        val a = founder()
        val b = TestNode("b")
        joinAndApprove(b, a)
        await("online") { a.state.members.singleOrNull()?.online == true }
        runBlocking { b.repo.add("b", false, 4, GENERAL_ID) }
        await("A has it") { a.copies() == 4 }
        runBlocking { b.node.leave() }
        await("B is out") { !b.identity.inMesh }
        await("A drops B") { a.state.members.isEmpty() }
        assertEquals(4, b.copies())
        assertNull(b.state.notice)
    }

    @Test fun aThirdDeviceJoinedThroughOneMemberFindsTheOther() {
        val a = founder()
        val b = TestNode("b")
        joinAndApprove(b, a)
        await("a-b") { a.state.members.singleOrNull()?.online == true }
        val c = TestNode("c")
        joinAndApprove(c, b) // approved by B, a member who is not the founder
        // A learns about C from B and dials it; C learns about A the same way.
        await("A sees C online") { a.state.members.size == 2 && a.state.members.all { it.online } }
        await("C sees both online") { c.state.members.size == 2 && c.state.members.all { it.online } }
        runBlocking { c.repo.add("a", false, 3, GENERAL_ID) }
        await("A gets C's card") { a.copies() == 3 && b.copies() == 3 }
    }

    @Test fun aDeviceThatWasOfflineCatchesUp() {
        val a = founder()
        val b = TestNode("b")
        joinAndApprove(b, a)
        await("online") { a.state.members.singleOrNull()?.online == true }
        b.node.stop()
        await("A sees B offline") { a.state.members.single().online.not() }
        runBlocking { a.repo.add("a", false, 5, GENERAL_ID) }
        runBlocking { b.node.start() }
        await("B catches up") { b.copies() == 5 }
    }

    @Test fun failedAttemptsSlowGuessingDown() {
        var now = 1_000L
        val t = FailureThrottle { now }
        assertTrue(t.allow("1.2.3.4"))
        t.failed("1.2.3.4") // refused for 1 s
        assertFalse(t.allow("1.2.3.4"))
        now += 1_001
        assertTrue(t.allow("1.2.3.4"))
        t.failed("1.2.3.4") // 2 s
        now += 1_500
        assertFalse(t.allow("1.2.3.4"))
        now += 600
        assertTrue(t.allow("1.2.3.4"))
        repeat(20) { t.failed("1.2.3.4") } // never more than 5 minutes
        now += 5 * 60 * 1000 + 1
        assertTrue(t.allow("1.2.3.4"))
        assertTrue(t.allow("5.6.7.8")) // others are not affected
        t.failed("1.2.3.4"); t.succeeded("1.2.3.4")
        assertTrue(t.allow("1.2.3.4"))
    }

    @Test fun addressesParse() {
        assertEquals("10.0.0.5" to 41890, MeshNode.parseAddress("10.0.0.5"))
        assertEquals("10.0.0.5" to 9000, MeshNode.parseAddress("10.0.0.5:9000"))
        assertEquals("tablet.local" to 41890, MeshNode.parseAddress("tcp://tablet.local"))
    }
}
