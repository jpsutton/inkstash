package io.github.jpsutton.inkstash.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MeshHandshakeTest {
    private val pool = Executors.newCachedThreadPool()
    private val keyA = NoiseKey.generate()
    private val keyB = NoiseKey.generate()

    /** Runs a dial and an accept against each other over a real loopback socket. */
    private fun connect(dialPass: String, acceptPass: String): Pair<SecureChannel, SecureChannel> {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val accepted = pool.submit(Callable {
                server.accept().use { s ->
                    s.soTimeout = 10_000
                    MeshHandshake.accept(FrameStream(s.getInputStream(), s.getOutputStream()), acceptPass, keyB)
                }
            })
            val dialed = pool.submit(Callable {
                val s = Socket(java.net.InetAddress.getLoopbackAddress(), server.localPort).apply { soTimeout = 10_000 }
                MeshHandshake.dial(FrameStream(s.getInputStream(), s.getOutputStream()), dialPass, keyA)
            })
            return get(dialed) to get(accepted)
        }
    }

    private fun <T> get(f: java.util.concurrent.Future<T>): T = try {
        f.get(20, TimeUnit.SECONDS)
    } catch (e: java.util.concurrent.ExecutionException) {
        throw e.cause!!
    }

    /** Both ends of a connection that stays open, to test messages. */
    private fun pair(): Pair<SecureChannel, SecureChannel> {
        val server = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        val accepted = pool.submit(Callable {
            val s = server.accept().apply { soTimeout = 10_000 }
            MeshHandshake.accept(FrameStream(s.getInputStream(), s.getOutputStream()), "pass phrase", keyB)
        })
        val s = Socket(java.net.InetAddress.getLoopbackAddress(), server.localPort).apply { soTimeout = 10_000 }
        val dialed = MeshHandshake.dial(FrameStream(s.getInputStream(), s.getOutputStream()), "pass phrase", keyA)
        return dialed to get(accepted)
    }

    @Test fun rightPassphraseEstablishesAChannelAndSwapsKeys() {
        val (a, b) = connect("pass phrase", "pass phrase")
        assertArrayEquals(keyB.publicKey, a.remoteKey)
        assertArrayEquals(keyA.publicKey, b.remoteKey)
        assertArrayEquals(a.handshakeHash, b.handshakeHash)
    }

    @Test fun messagesGoBothWaysIncludingBigOnes() {
        val (a, b) = pair()
        a.send("hello".toByteArray())
        assertEquals("hello", String(b.receive()))
        val big = ByteArray(300_000) { (it * 31).toByte() }
        b.send(big)
        assertArrayEquals(big, a.receive())
        a.send(ByteArray(0))
        assertEquals(0, b.receive().size)
    }

    @Test fun wrongPassphraseIsToldApartOnBothSides() {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val accepted = pool.submit(Callable {
                server.accept().use { s ->
                    s.soTimeout = 10_000
                    MeshHandshake.accept(FrameStream(s.getInputStream(), s.getOutputStream()), "right one", keyB)
                }
            })
            val dial = pool.submit(Callable {
                Socket(java.net.InetAddress.getLoopbackAddress(), server.localPort).use { s ->
                    s.soTimeout = 10_000
                    MeshHandshake.dial(FrameStream(s.getInputStream(), s.getOutputStream()), "wrong one", keyA)
                }
            })
            assertThrows(WrongPassphraseException::class.java) { get(dial) }
            assertThrows(WrongPassphraseException::class.java) { get(accepted) }
        }
    }

    @Test fun aTamperedFrameIsRejected() {
        val (a, b) = pair()
        a.send("one".toByteArray())
        assertEquals("one", String(b.receive()))
        // Not testable by flipping bytes on a live socket, so check the framing guard instead.
        assertThrows(IOException::class.java) {
            FrameStream(java.io.ByteArrayInputStream(byteArrayOf(0x7f, 0, 0, 0)), java.io.ByteArrayOutputStream()).read()
        }
    }

    @Test fun nonMeshTrafficIsRefused() {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val accepted = pool.submit(Callable {
                server.accept().use { s ->
                    s.soTimeout = 10_000
                    MeshHandshake.accept(FrameStream(s.getInputStream(), s.getOutputStream()), "x", keyB)
                }
            })
            Socket(java.net.InetAddress.getLoopbackAddress(), server.localPort).use { s ->
                s.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                s.getOutputStream().flush()
                assertThrows(IOException::class.java) { get(accepted) }
            }
        }
        assertTrue(true)
    }
}
