package io.github.jpsutton.inkstash.mesh

import com.southernstorm.noise.protocol.CipherState
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.BadPaddingException

/** A device's long-term Noise key (X25519). The public half identifies the device to the mesh. */
class NoiseKey(val privateKey: ByteArray, val publicKey: ByteArray) {
    companion object {
        fun generate(): NoiseKey {
            val dh = Noise.createDH("25519")
            dh.generateKeyPair()
            val priv = ByteArray(dh.privateKeyLength).also { dh.getPrivateKey(it, 0) }
            val pub = ByteArray(dh.publicKeyLength).also { dh.getPublicKey(it, 0) }
            dh.destroy()
            return NoiseKey(priv, pub)
        }
    }
}

/** Length-prefixed frames (4 bytes, big endian) over a stream. */
class FrameStream(input: InputStream, output: OutputStream) {
    private val input = DataInputStream(input.buffered())
    private val output = DataOutputStream(output.buffered())

    @Synchronized
    fun write(bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    fun read(maxSize: Int = MAX_FRAME): ByteArray {
        val size = try {
            input.readInt()
        } catch (e: EOFException) {
            throw IOException("Connection closed", e)
        }
        if (size < 0 || size > maxSize) throw IOException("Frame of $size bytes is too big")
        return ByteArray(size).also { input.readFully(it) }
    }

    companion object {
        const val MAX_FRAME = 70_000
    }
}

/** The passphrase is wrong, on this side or the other. */
class WrongPassphraseException : IOException("The passphrase doesn't match this mesh's")

/** An established, encrypted session. Messages of any size; each direction is ordered. */
class SecureChannel internal constructor(
    private val frames: FrameStream,
    private val sender: CipherState,
    private val receiver: CipherState,
    /** The other device's Noise public key, proven by the handshake. */
    val remoteKey: ByteArray,
    /** Identical on both sides; the six-digit approval code is derived from it. */
    val handshakeHash: ByteArray,
) {
    @Synchronized
    fun send(message: ByteArray) {
        var offset = 0
        do {
            val n = minOf(CHUNK, message.size - offset)
            val last = offset + n >= message.size
            val plain = ByteArray(n + 1)
            plain[0] = if (last) 1 else 0
            System.arraycopy(message, offset, plain, 1, n)
            val out = ByteArray(plain.size + sender.macLength)
            val len = sender.encryptWithAd(null, plain, 0, out, 0, plain.size)
            frames.write(out.copyOf(len))
            offset += n
        } while (offset < message.size)
    }

    /** Blocks until a whole message arrives. Throws [IOException] if the connection ends or a frame fails to decrypt. */
    fun receive(maxSize: Int = MAX_MESSAGE): ByteArray {
        val parts = java.io.ByteArrayOutputStream()
        while (true) {
            val frame = frames.read()
            val plain = ByteArray(frame.size)
            val len = try {
                receiver.decryptWithAd(null, frame, 0, plain, 0, frame.size)
            } catch (e: BadPaddingException) {
                throw IOException("A message failed to decrypt", e)
            }
            if (len < 1) throw IOException("Empty frame")
            parts.write(plain, 1, len - 1)
            if (parts.size() > maxSize) throw IOException("Message is too big")
            if (plain[0] == 1.toByte()) return parts.toByteArray()
        }
    }

    companion object {
        private const val CHUNK = 60_000
        const val MAX_MESSAGE = 64 * 1024 * 1024
    }
}

/**
 * SPAKE2 on the passphrase gives a key, then a Noise `XX` handshake in PSK mode (the library calls it `NoisePSK_XX`, the key mixed in from the first message, like `psk0`) uses it as the pre-shared key and
 * binds both SPAKE2 messages into its prologue. So:
 * - the passphrase never crosses the wire, and a recording can't be tested against guesses offline;
 * - a wrong passphrase fails on the first Noise message, before either side shows its static key;
 * - both sides learn each other's static key, and everything after is encrypted with forward secrecy.
 */
object MeshHandshake {
    private const val VERSION: Byte = 1
    private const val PROTOCOL = "NoisePSK_XX_25519_ChaChaPoly_SHA256"
    private const val PROLOGUE = "inkstash-mesh-v1"

    /** One plaintext byte the listener sends instead of its second Noise message when the first doesn't verify. */
    private val WRONG_PASSPHRASE = byteArrayOf(0xFF.toByte())

    /** The device that opens the connection. */
    fun dial(frames: FrameStream, passphrase: String, key: NoiseKey): SecureChannel {
        val spake = Spake2.start(Spake2.Role.A, passphrase)
        frames.write(byteArrayOf(VERSION) + spake.message)
        val pB = peerSpake(frames.read(200))
        val psk = spake.finish(pB).key
        val hs = noise(HandshakeState.INITIATOR, key, psk, spake.message, pB)
        return run(hs, frames, initiator = true)
    }

    /** The device that listens. */
    fun accept(frames: FrameStream, passphrase: String, key: NoiseKey): SecureChannel {
        val spake = Spake2.start(Spake2.Role.B, passphrase)
        val pA = peerSpake(frames.read(200))
        frames.write(byteArrayOf(VERSION) + spake.message)
        val psk = spake.finish(pA).key
        val hs = noise(HandshakeState.RESPONDER, key, psk, pA, spake.message)
        return run(hs, frames, initiator = false)
    }

    private fun peerSpake(frame: ByteArray): ByteArray {
        if (frame.size != 66 || frame[0] != VERSION) throw IOException("Not an inkstash mesh connection")
        return try {
            frame.copyOfRange(1, 66).also { Spake2.checkPoint(it) }
        } catch (e: IllegalArgumentException) {
            throw IOException("Bad key exchange message", e)
        }
    }

    private fun noise(role: Int, key: NoiseKey, psk: ByteArray, pA: ByteArray, pB: ByteArray): HandshakeState {
        val hs = HandshakeState(PROTOCOL, role)
        val prologue = PROLOGUE.toByteArray() + pA + pB
        hs.setPrologue(prologue, 0, prologue.size)
        hs.setPreSharedKey(psk, 0, psk.size)
        hs.localKeyPair.setPrivateKey(key.privateKey, 0)
        return hs
    }

    private fun run(hs: HandshakeState, frames: FrameStream, initiator: Boolean): SecureChannel {
        val buffer = ByteArray(FrameStream.MAX_FRAME)
        val payload = ByteArray(FrameStream.MAX_FRAME)
        hs.start()
        while (true) {
            when (hs.action) {
                HandshakeState.WRITE_MESSAGE -> {
                    val n = hs.writeMessage(buffer, 0, null, 0, 0)
                    frames.write(buffer.copyOf(n))
                }
                HandshakeState.READ_MESSAGE -> {
                    val frame = frames.read()
                    if (initiator && frame.contentEquals(WRONG_PASSPHRASE)) throw WrongPassphraseException()
                    try {
                        hs.readMessage(frame, 0, frame.size, payload, 0)
                    } catch (e: BadPaddingException) {
                        // A responder only fails to decrypt here when the key from the passphrase differs.
                        if (!initiator) {
                            runCatching { frames.write(WRONG_PASSPHRASE) }
                            throw WrongPassphraseException()
                        }
                        throw IOException("Handshake failed", e)
                    }
                }
                HandshakeState.SPLIT -> {
                    val pair = hs.split()
                    val remote = ByteArray(hs.remotePublicKey.publicKeyLength).also { hs.remotePublicKey.getPublicKey(it, 0) }
                    val hash = hs.handshakeHash
                    hs.destroy()
                    return SecureChannel(frames, pair.sender, pair.receiver, remote, hash)
                }
                else -> throw IOException("Handshake failed")
            }
        }
    }
}
