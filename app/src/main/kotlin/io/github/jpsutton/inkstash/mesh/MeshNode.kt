package io.github.jpsutton.inkstash.mesh

import io.github.jpsutton.inkstash.data.CollectionRepository
import io.github.jpsutton.inkstash.data.InkstashDatabase
import io.github.jpsutton.inkstash.data.SyncStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** A member as the screen shows it. */
data class PeerStatus(val record: MemberRecord, val online: Boolean)

/** A device asking to join, waiting for a person to compare [code] on both screens and approve. */
data class PendingJoin(val nodeId: String, val name: String, val code: String)

sealed interface JoinProgress {
    data object Connecting : JoinProgress
    data class WaitingForApproval(val code: String) : JoinProgress
    data class Failed(val message: String) : JoinProgress
}

data class MeshState(
    val inMesh: Boolean = false,
    val deviceName: String = "",
    val port: Int = MeshIdentity.DEFAULT_PORT,
    val members: List<PeerStatus> = emptyList(),
    val pending: List<PendingJoin> = emptyList(),
    val join: JoinProgress? = null,
    val listenError: String? = null,
    val addresses: List<String> = emptyList(),
    /** Something worth telling the person, such as "Removed from the mesh". */
    val notice: String? = null,
)

/**
 * Links this device with the others in a mesh: listens for them, dials the ones it isn't connected to,
 * admits new ones (a person approves each, or a valid QR invite does), and keeps the collections in sync.
 *
 * Every connection starts with [MeshHandshake] (passphrase, then encryption), then both sides say who they are
 * and check the other against the signed member records ([MeshRecords]). Members then swap what they hold in the
 * operation log: whoever has operations the other lacks sends them. There is no cloud and no relay: devices
 * must be able to reach each other directly (same Wi-Fi, or a VPN).
 */
class MeshNode(
    private val identity: MeshIdentity,
    private val db: InkstashDatabase,
    private val sync: SyncStore,
    private val collection: CollectionRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connectIntervalMs: Long = 3_000,
    private val addressProvider: (port: Int) -> List<String> = ::localAddresses,
) {
    private class Session(val peerId: String, val openedBy: String, val channel: SecureChannel, val socket: Socket) {
        fun send(m: MeshMessage) = channel.send(MeshMessage.encode(m))
        fun close() = runCatching { socket.close() }
    }

    private class Pending(val join: PendingJoin, val decision: CompletableDeferred<Boolean>)

    private val _state = MutableStateFlow(MeshState())
    val state: StateFlow<MeshState> = _state

    private val sessions = ConcurrentHashMap<String, Session>()
    private val dialing = ConcurrentHashMap.newKeySet<String>()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val invites = ConcurrentHashMap<String, Long>()
    private val seenAddress = ConcurrentHashMap<String, String>()
    private val backoff = ConcurrentHashMap<String, Backoff>()
    private val throttle = FailureThrottle(clock)
    private val membersLock = Mutex()
    private val jobs = mutableListOf<Job>()
    private var server: ServerSocket? = null

    @Volatile private var known: Map<String, MemberRecord> = emptyMap()
    @Volatile private var join: JoinProgress? = null
    @Volatile private var listenError: String? = null
    @Volatile private var notice: String? = null

    /** The port actually listening (differs from the setting only when that is 0). */
    @Volatile var boundPort: Int = identity.listenPort
        private set

    // ---- life cycle ----

    /** Starts listening and connecting, if this device is in a mesh. Safe to call again. */
    suspend fun start() {
        if (!identity.inMesh) {
            publish()
            return
        }
        stopNetwork()
        known = db.meshMembers().all().map { MemberRecord.of(it) }.associateBy { it.nodeId }
        synchronized(jobs) {
            jobs += scope.launch(Dispatchers.IO) { listen() }
            jobs += scope.launch(Dispatchers.IO) { connectLoop() }
            jobs += scope.launch { collection.localChanges.collect { broadcastVector() } }
            jobs += scope.launch { while (isActive) { delay(KEEPALIVE_MS); broadcastVector() } }
        }
        publish()
    }

    fun stop() = stopNetwork()

    private fun stopNetwork() {
        synchronized(jobs) { jobs.forEach { it.cancel() }; jobs.clear() }
        runCatching { server?.close() }
        server = null
        sessions.values.forEach { it.close() }
        sessions.clear()
        dialing.clear()
        for (p in pending.values) p.decision.complete(false)
        pending.clear()
    }

    // ---- starting, joining, leaving ----

    /** Starts a new mesh with this device as the founder. */
    suspend fun startMesh(passphrase: String) {
        require(passphrase.length >= MIN_PASSPHRASE) { "Pick a passphrase of at least $MIN_PASSPHRASE characters" }
        check(!identity.inMesh) { "Already in a mesh" }
        sync.baseline()
        identity.joinMesh(identity.nodeId, passphrase)
        val founder = MeshRecords.founder(identity, clock())
        db.meshMembers().upsert(founder.toEntity())
        notice = null
        start()
    }

    /**
     * Asks a device in a mesh to admit this one. With an [invite] (from a QR code) the inviting device approves by
     * itself; without one, a person must approve there after comparing the code shown here. Progress is in [MeshState.join].
     */
    suspend fun join(address: String, passphrase: String, invite: MeshInvite? = null): Boolean {
        check(!identity.inMesh) { "Already in a mesh" }
        setJoin(JoinProgress.Connecting)
        return try {
            withContext(Dispatchers.IO) { doJoin(address, invite?.passphrase ?: passphrase, invite) }
            setJoin(null)
            true
        } catch (e: WrongPassphraseException) {
            setJoin(JoinProgress.Failed("The passphrase doesn't match this mesh's")); false
        } catch (e: IOException) {
            setJoin(JoinProgress.Failed(e.message ?: "Couldn't connect")); false
        }
    }

    private suspend fun doJoin(address: String, passphrase: String, invite: MeshInvite?) {
        val targets = if (invite != null) invite.addresses else listOf(address)
        var last: IOException = IOException("Couldn't connect")
        for (t in targets) {
            val socket = Socket()
            try {
                val (host, port) = parseAddress(t)
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val channel = MeshHandshake.dial(FrameStream(socket.getInputStream(), socket.getOutputStream()), passphrase, identity.noiseKey)
                if (invite != null && MeshRecords.fingerprint(channel.remoteKey) != invite.fingerprint) {
                    throw IOException("This isn't the device that made the code")
                }
                channel.send(
                    MeshMessage.encode(
                        MeshMessage.Hello(
                            meshId = null, nodeId = identity.nodeId, name = identity.deviceName, signKey = identity.signPublic.toHex(),
                            port = boundPort, joining = true, invite = invite?.token,
                        ),
                    ),
                )
                socket.soTimeout = JOIN_WAIT_MS
                while (true) {
                    when (val m = MeshMessage.decode(channel.receive(MAX_JSON))) {
                        is MeshMessage.JoinPending -> setJoin(JoinProgress.WaitingForApproval(m.code))
                        is MeshMessage.Admitted -> {
                            finishJoin(m, passphrase, channel, socket, usedAddress = t)
                            return
                        }
                        is MeshMessage.Declined -> throw IOException("The request was declined")
                        is MeshMessage.Refused -> throw IOException(m.reason)
                        else -> Unit
                    }
                }
            } catch (e: WrongPassphraseException) {
                socket.close()
                throw e
            } catch (e: IOException) {
                socket.close()
                last = e
            }
        }
        throw last
    }

    private suspend fun finishJoin(m: MeshMessage.Admitted, passphrase: String, channel: SecureChannel, socket: Socket, usedAddress: String) {
        val accepted = MeshRecords.accept(m.meshId, m.members, emptyMap())
        val me = accepted[identity.nodeId]
        if (me == null || me.noiseKey != identity.noiseKey.publicKey.toHex() || me.signKey != identity.signPublic.toHex() || me.removed) {
            throw IOException("The mesh sent back a record that isn't ours")
        }
        sync.baseline() // a collection from before the mesh goes into the log first
        identity.joinMesh(m.meshId, passphrase)
        for (r in accepted.values) db.meshMembers().upsert(r.toEntity())
        notice = null
        setJoin(null)
        val approver = accepted[me.admittedBy] ?: throw IOException("Unknown approver")
        // The address that worked is where the approver can be reached again.
        val host = parseAddress(usedAddress).first
        seenAddress[approver.nodeId] = "$host:${parseAddress(usedAddress).second}"
        start()
        scope.launch(Dispatchers.IO) { runSession(channel, socket, approver.nodeId, openedBy = identity.nodeId) }
    }

    /** Takes this device out of the mesh. It tells the members it can reach, then forgets the passphrase and keeps its own copy of everything. */
    suspend fun leave() {
        val meshId = identity.meshId ?: return
        val me = known[identity.nodeId] ?: return
        mergeMembers(listOf(MeshRecords.remove(identity, meshId, me, clock())), from = null)
    }

    /** Removes another device. */
    suspend fun remove(nodeId: String) {
        val meshId = identity.meshId ?: return
        val rec = known[nodeId] ?: return
        if (rec.removed || nodeId == identity.nodeId) return
        mergeMembers(listOf(MeshRecords.remove(identity, meshId, rec, clock())), from = null)
    }

    private suspend fun leaveLocally(why: String?) {
        delay(LEAVE_DELAY_MS) // lets the removal reach the members that are connected
        stopNetwork()
        identity.leaveMesh()
        db.meshMembers().deleteAll()
        known = emptyMap()
        notice = why
        publish()
    }

    // ---- approving ----

    fun approve(nodeId: String) {
        pending[nodeId]?.decision?.complete(true)
    }

    fun decline(nodeId: String) {
        pending[nodeId]?.decision?.complete(false)
    }

    /** A QR invite for a new device to scan. Valid for ten minutes, once. */
    fun createInvite(): MeshInvite? {
        val meshId = identity.meshId ?: return null
        val pass = identity.passphrase ?: return null
        val addresses = ownAddresses()
        if (addresses.isEmpty()) return null
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        val expires = clock() + INVITE_TTL_MS
        invites[token] = expires
        return MeshInvite(meshId, addresses, pass, MeshRecords.fingerprint(identity.noiseKey.publicKey), token, expires)
    }

    fun addAddress(address: String) {
        val a = address.trim()
        if (a.isNotEmpty() && a !in identity.extraAddresses) identity.extraAddresses = identity.extraAddresses + a
        publish()
    }

    fun removeAddress(address: String) {
        identity.extraAddresses = identity.extraAddresses - address
        publish()
    }

    fun rename(name: String) {
        identity.deviceName = name
        publish()
    }

    fun dismissNotice() {
        notice = null
        publish()
    }

    fun ownAddresses(): List<String> = addressProvider(boundPort)

    // ---- inbound ----

    private suspend fun listen() {
        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(identity.listenPort))
            }
        } catch (e: IOException) {
            listenError = "Can't listen on port ${identity.listenPort}: ${e.message}"
            publish()
            return
        }
        server = s
        boundPort = s.localPort
        listenError = null
        publish()
        while (scope.isActive) {
            val c = try { s.accept() } catch (e: IOException) { break }
            scope.launch(Dispatchers.IO) { handleInbound(c) }
        }
    }

    private suspend fun handleInbound(socket: Socket) {
        val host = socket.inetAddress.hostAddress ?: "?"
        try {
            if (!throttle.allow(host)) return
            val pass = identity.passphrase ?: return
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val channel = try {
                MeshHandshake.accept(FrameStream(socket.getInputStream(), socket.getOutputStream()), pass, identity.noiseKey)
            } catch (e: IOException) {
                throttle.failed(host)
                return
            }
            throttle.succeeded(host)
            val hello = MeshMessage.decode(channel.receive(MAX_JSON)) as? MeshMessage.Hello ?: return
            if (hello.joining) handleJoinRequest(channel, socket, hello) else handleMember(channel, socket, hello, host)
        } catch (e: Exception) {
            // A bad peer: drop the connection.
        } finally {
            runCatching { socket.close() } // a session, if there was one, has ended by now
        }
    }

    private suspend fun handleMember(channel: SecureChannel, socket: Socket, hello: MeshMessage.Hello, host: String) {
        val meshId = identity.meshId ?: return
        if (hello.meshId != meshId) {
            channel.send(MeshMessage.encode(MeshMessage.Refused("Different mesh")))
            return
        }
        mergeMembers(hello.members, from = null)
        val rec = known[hello.nodeId]
        if (rec == null || rec.noiseKey != channel.remoteKey.toHex() || rec.signKey != hello.signKey) {
            channel.send(MeshMessage.encode(MeshMessage.Refused("Not a member", membersForWire())))
            return
        }
        if (rec.removed) {
            channel.send(MeshMessage.encode(MeshMessage.Refused("Removed", membersForWire())))
            return
        }
        noteAddresses(rec, hello, host)
        channel.send(MeshMessage.encode(myHello()))
        runSession(channel, socket, rec.nodeId, openedBy = rec.nodeId)
    }

    private suspend fun handleJoinRequest(channel: SecureChannel, socket: Socket, hello: MeshMessage.Hello) {
        val meshId = identity.meshId ?: return
        if (known[hello.nodeId]?.let { !it.removed } == true) {
            channel.send(MeshMessage.encode(MeshMessage.Refused("This device is already in the mesh")))
            return
        }
        val code = MeshRecords.approvalCode(channel.handshakeHash)
        val invited = hello.invite?.let { invites.remove(it) }?.let { it > clock() } == true
        if (!invited) {
            channel.send(MeshMessage.encode(MeshMessage.JoinPending(code)))
            val p = Pending(PendingJoin(hello.nodeId, hello.name, code), CompletableDeferred())
            pending[hello.nodeId] = p
            publish()
            val approved = withTimeoutOrNull(JOIN_WAIT_MS.toLong()) { p.decision.await() } ?: false
            pending.remove(hello.nodeId)
            publish()
            if (!approved) {
                runCatching { channel.send(MeshMessage.encode(MeshMessage.Declined)) }
                return
            }
        }
        val record = MeshRecords.admit(identity, hello.nodeId, hello.name, channel.remoteKey, hello.signKey.fromHex(), clock())
        mergeMembers(listOf(record), from = null)
        val rec = known[hello.nodeId]
        if (rec == null || rec.removed) return
        channel.send(MeshMessage.encode(MeshMessage.Admitted(meshId, membersForWire())))
        socket.soTimeout = SESSION_TIMEOUT_MS
        runSession(channel, socket, rec.nodeId, openedBy = rec.nodeId)
    }

    // ---- outbound ----

    private suspend fun connectLoop() {
        while (scope.isActive) {
            delay(connectIntervalMs)
            connectOnce()
        }
    }

    private fun connectOnce() {
        val me = identity.nodeId
        for (rec in known.values) {
            if (rec.nodeId == me || rec.removed || sessions.containsKey(rec.nodeId)) continue
            if (!dialing.add(rec.nodeId)) continue
            scope.launch(Dispatchers.IO) {
                try {
                    dialMember(rec)
                } finally {
                    dialing.remove(rec.nodeId)
                }
            }
        }
    }

    private suspend fun dialMember(rec: MemberRecord) {
        val candidates = (listOfNotNull(seenAddress[rec.nodeId]) + rec.addresses + identity.extraAddresses).distinct()
        for (addr in candidates) {
            val b = backoff.getOrPut(addr) { Backoff() }
            if (!b.ready(clock())) continue
            try {
                if (connectTo(addr, rec.nodeId)) {
                    b.reset()
                    return
                }
                b.failed(clock())
            } catch (e: IOException) {
                b.failed(clock())
            }
        }
    }

    /** Opens a session with a member at [address]. Returns false if the device there is not [expected]. */
    private suspend fun connectTo(address: String, expected: String): Boolean {
        val pass = identity.passphrase ?: return false
        val meshId = identity.meshId ?: return false
        val socket = Socket()
        var keep = false
        try {
            val (host, port) = parseAddress(address)
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val channel = MeshHandshake.dial(FrameStream(socket.getInputStream(), socket.getOutputStream()), pass, identity.noiseKey)
            channel.send(MeshMessage.encode(myHello()))
            val reply = MeshMessage.decode(channel.receive(MAX_JSON))
            if (reply is MeshMessage.Refused) {
                mergeMembers(reply.members, from = null)
                return false
            }
            val hello = reply as? MeshMessage.Hello ?: return false
            if (hello.meshId != meshId) return false
            mergeMembers(hello.members, from = null)
            val rec = known[hello.nodeId] ?: return false
            // The device must be the member it claims to be: keys pinned in its signed record.
            if (rec.nodeId != expected || rec.removed || rec.noiseKey != channel.remoteKey.toHex() || rec.signKey != hello.signKey) return false
            noteAddresses(rec, hello, host)
            keep = true
            runSession(channel, socket, rec.nodeId, openedBy = identity.nodeId)
            return true
        } finally {
            if (!keep) socket.close()
        }
    }

    // ---- sessions and sync ----

    private suspend fun runSession(channel: SecureChannel, socket: Socket, peerId: String, openedBy: String) {
        val session = Session(peerId, openedBy, channel, socket)
        socket.soTimeout = SESSION_TIMEOUT_MS
        val old = sessions.put(peerId, session)
        if (old != null) {
            // Both ends keep the connection opened by the lower node id.
            val keepNew = openedBy <= old.openedBy
            if (!keepNew) {
                sessions[peerId] = old
                session.close()
                return
            }
            old.close()
        }
        publish()
        try {
            session.send(MeshMessage.Vector(sync.vector()))
            while (scope.isActive) {
                val m = MeshMessage.decode(channel.receive(MAX_JSON))
                if (!handle(session, m)) break
            }
        } catch (e: IOException) {
            // Connection ended.
        } finally {
            if (sessions[peerId] === session) sessions.remove(peerId)
            session.close()
            publish()
        }
    }

    /** Returns false to end the session. */
    private suspend fun handle(session: Session, m: MeshMessage): Boolean {
        when (m) {
            is MeshMessage.Vector -> {
                val ours = sync.vector()
                var theirs = m.v
                while (true) {
                    val batch = sync.missingFor(theirs, BATCH)
                    if (batch.isEmpty()) break
                    session.send(MeshMessage.Ops(batch.map(WireOp::of)))
                    val next = theirs.toMutableMap()
                    for (op in batch) next[op.origin] = maxOf(next[op.origin] ?: 0, op.seq)
                    theirs = next
                }
                if (m.v.any { (o, s) -> s > (ours[o] ?: 0) }) session.send(MeshMessage.Vector(ours))
            }
            is MeshMessage.Ops -> {
                val fresh = sync.receive(m.ops.map(WireOp::toOp))
                if (fresh > 0) {
                    val v = MeshMessage.Vector(sync.vector())
                    for (other in sessions.values) if (other !== session) runCatching { other.send(v) }
                    if (m.ops.size >= BATCH) session.send(v) // more may be waiting
                }
            }
            is MeshMessage.Members -> mergeMembers(m.records, from = session)
            is MeshMessage.Refused -> {
                mergeMembers(m.members, from = null)
                return false
            }
            is MeshMessage.Bye -> return false
            else -> Unit
        }
        return true
    }

    private suspend fun broadcastVector() {
        if (sessions.isEmpty()) return
        val v = MeshMessage.Vector(sync.vector())
        for (s in sessions.values) runCatching { s.send(v) }.onFailure { s.close() }
    }

    // ---- members ----

    private fun myHello() = MeshMessage.Hello(
        meshId = identity.meshId, nodeId = identity.nodeId, name = identity.deviceName, signKey = identity.signPublic.toHex(),
        port = boundPort, addresses = ownAddresses(), members = membersForWire(),
    )

    /** Every member record, with this device's own carrying the addresses it can be reached at. */
    private fun membersForWire(): List<MemberRecord> =
        known.values.map { if (it.nodeId == identity.nodeId) it.copy(addresses = ownAddresses()) else it }

    private suspend fun noteAddresses(rec: MemberRecord, hello: MeshMessage.Hello, host: String) {
        seenAddress[rec.nodeId] = "$host:${hello.port}"
        if (hello.addresses.isNotEmpty() && hello.addresses != rec.addresses) {
            mergeMembers(listOf(rec.copy(addresses = hello.addresses)), from = null)
        }
    }

    /** Verifies and stores member records, then tells the other members if membership changed. */
    private suspend fun mergeMembers(records: List<MemberRecord>, from: Session?) {
        val meshId = identity.meshId ?: return
        membersLock.withLock {
            val before = known
            val after = MeshRecords.accept(meshId, records, before)
            if (after == before) return
            for (r in after.values) if (before[r.nodeId] != r) db.meshMembers().upsert(r.toEntity())
            known = after
            val me = after[identity.nodeId]
            if (me != null && me.removed) {
                val byOther = me.removedBy != identity.nodeId
                // Tell the others (a leave is carried by this), then stop.
                broadcastMembers(after, except = from)
                scope.launch { leaveLocally(if (byOther) "This device was removed from the mesh" else null) }
                return
            }
            for ((id, r) in after) {
                if (r.removed && before[id]?.removed != true) {
                    val s = sessions[id]
                    if (s != null) scope.launch { delay(REMOVE_CLOSE_DELAY_MS); s.close() }
                }
            }
            broadcastMembers(after, except = from) // changes die out: a device that already has them changes nothing
        }
        publish()
    }

    private fun broadcastMembers(all: Map<String, MemberRecord>, except: Session?) {
        val m = MeshMessage.Members(all.values.toList())
        for (s in sessions.values) if (s !== except) runCatching { s.send(m) }
    }

    // ---- state ----

    private fun setJoin(p: JoinProgress?) {
        join = p
        publish()
    }

    private fun publish() {
        val self = identity.nodeId
        _state.value = MeshState(
            inMesh = identity.inMesh,
            deviceName = identity.deviceName,
            port = boundPort,
            members = known.values.filter { it.nodeId != self && !it.removed }.sortedBy { it.name.lowercase() }
                .map { PeerStatus(it, sessions.containsKey(it.nodeId)) },
            pending = pending.values.map { it.join },
            join = join,
            listenError = listenError,
            addresses = if (identity.inMesh) ownAddresses() else emptyList(),
            notice = notice,
        )
    }

    /** Spaces out connection attempts to an address that fails: 3 s, then doubling to a minute. */
    private class Backoff {
        private var next = 0L
        private var wait = 3_000L
        fun ready(now: Long) = now >= next
        fun failed(now: Long) { next = now + wait; wait = minOf(wait * 2, 60_000L) }
        fun reset() { next = 0; wait = 3_000L }
    }

    companion object {
        const val MIN_PASSPHRASE = 8
        private const val BATCH = 500
        private const val MAX_JSON = 8 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        private const val SESSION_TIMEOUT_MS = 70_000
        private const val JOIN_WAIT_MS = 10 * 60 * 1000
        private const val KEEPALIVE_MS = 20_000L
        private const val LEAVE_DELAY_MS = 500L
        private const val REMOVE_CLOSE_DELAY_MS = 2_000L
        private const val INVITE_TTL_MS = 10 * 60 * 1000L

        fun parseAddress(address: String): Pair<String, Int> {
            val a = address.trim().removePrefix("tcp://")
            val i = a.lastIndexOf(':')
            if (i <= 0 || a.indexOf(':') != i) return a to MeshIdentity.DEFAULT_PORT // no port, or an IPv6 literal
            return a.substring(0, i) to (a.substring(i + 1).toIntOrNull() ?: throw IOException("Bad address: $address"))
        }

        /** This device's IPv4 addresses on its networks, with the port: what other devices would dial. */
        fun localAddresses(port: Int): List<String> = try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { "${it.hostAddress}:$port" }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Slows down guessing: after each failed handshake from an address, connections from it are refused for twice as
 * long as the last time (1 s, 2 s, 4 s... up to 5 minutes). A success clears it. Each guess of the passphrase
 * costs a live attempt, which is why a short, memorable passphrase is acceptable.
 */
class FailureThrottle(private val clock: () -> Long) {
    private class Entry(var failures: Int = 0, var until: Long = 0)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun allow(host: String): Boolean = (entries[host]?.until ?: 0) <= clock()

    fun failed(host: String) {
        val e = entries.getOrPut(host) { Entry() }
        e.failures++
        e.until = clock() + minOf(1_000L shl (e.failures - 1).coerceAtMost(20), 5 * 60 * 1000L)
    }

    fun succeeded(host: String) {
        entries.remove(host)
    }
}
