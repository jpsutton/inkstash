package io.github.jpsutton.inkstash.mesh

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SPAKE2 (RFC 9382) over P-256 with SHA-256: two parties that share a passphrase derive the same
 * secret without sending it, and a recorded exchange gives an attacker nothing to test guesses
 * against offline. Only the A and B sides of the RFC's asymmetric form are implemented: the device
 * that dials is A, the one that listens is B. Checked against the RFC's test vectors (Appendix B).
 *
 * The result is only a shared secret. Whether both sides really derived the same one is shown by
 * the Noise handshake that uses it as its pre-shared key (see [MeshHandshake]).
 */
class Spake2 internal constructor(
    val role: Role,
    private val w: BigInteger,
    private val idA: ByteArray,
    private val idB: ByteArray,
    private val secret: BigInteger,
) {
    enum class Role { A, B }

    class Result(
        /** SHA-256 of the transcript: 32 bytes, used as the key material (Ke || Ka in the RFC). */
        val key: ByteArray,
        /** The RFC's TT, for tests. */
        val transcript: ByteArray,
    )

    /** What this side sends to the other: 65 bytes, an uncompressed P-256 point. */
    val message: ByteArray

    private val ownBlind: ECPoint = if (role == Role.A) M else N
    private val peerBlind: ECPoint = if (role == Role.A) N else M

    init {
        message = params.g.multiply(secret).add(ownBlind.multiply(w)).normalize().getEncoded(false)
    }

    /** Combines the peer's message with our secret. Throws [IllegalArgumentException] for a bad point. */
    fun finish(peerMessage: ByteArray): Result {
        val peer = decode(peerMessage)
        // K = h * x * (peer - w * blind); h = 1 on P-256.
        val k = peer.subtract(peerBlind.multiply(w)).multiply(secret).normalize()
        require(!k.isInfinity) { "SPAKE2 produced the point at infinity" }
        val pA = if (role == Role.A) message else peerMessage
        val pB = if (role == Role.A) peerMessage else message
        val tt = lv(idA) + lv(idB) + lv(pA) + lv(pB) + lv(k.getEncoded(false)) + lv(scalarBytes(w))
        return Result(MessageDigest.getInstance("SHA-256").digest(tt), tt)
    }

    companion object {
        private val params: X9ECParameters = CustomNamedCurves.getByName("secp256r1")
        private val order: BigInteger = params.n

        // RFC 9382 section 6, P-256 (SEC1 compressed).
        private val M: ECPoint = params.curve.decodePoint(hex("02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f"))
        private val N: ECPoint = params.curve.decodePoint(hex("03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49"))

        private val IDENTITY_A = "inkstash mesh dialer".toByteArray()
        private val IDENTITY_B = "inkstash mesh listener".toByteArray()
        private val SALT = "inkstash mesh spake2 v1".toByteArray()

        /** Starts one side with the passphrase and a fresh random secret. */
        fun start(role: Role, passphrase: String, random: SecureRandom = SecureRandom()): Spake2 =
            Spake2(role, w(passphrase), IDENTITY_A, IDENTITY_B, randomScalar(random))

        /** The RFC's `w = MHF(pw) mod p`, with scrypt (N=2^14, r=8, p=1) and 64 bytes of output so the bias is negligible. */
        fun w(passphrase: String): BigInteger =
            BigInteger(1, SCrypt.generate(passphrase.toByteArray(), SALT, 1 shl 14, 8, 1, 64)).mod(order)

        private fun randomScalar(random: SecureRandom): BigInteger {
            while (true) {
                val x = BigInteger(256, random)
                if (x.signum() > 0 && x < order) return x
            }
        }

        /** Throws [IllegalArgumentException] unless [bytes] is an uncompressed P-256 point other than infinity. */
        fun checkPoint(bytes: ByteArray) {
            decode(bytes)
        }

        private fun decode(bytes: ByteArray): ECPoint {
            require(bytes.size == 65 && bytes[0] == 4.toByte()) { "Not an uncompressed P-256 point" }
            val p = try {
                params.curve.decodePoint(bytes)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Not a point on P-256", e)
            }
            require(!p.isInfinity) { "Point at infinity" }
            return p
        }

        /** Eight-byte little-endian length, then the bytes (RFC 9382 section 3.3). */
        private fun lv(b: ByteArray): ByteArray {
            val len = ByteArray(8) { i -> ((b.size.toLong() shr (8 * i)) and 0xff).toByte() }
            return len + b
        }

        private fun scalarBytes(v: BigInteger): ByteArray {
            val raw = v.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
            return ByteArray(32 - raw.size) + raw
        }

        internal fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
