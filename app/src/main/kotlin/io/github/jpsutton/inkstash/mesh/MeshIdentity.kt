package io.github.jpsutton.inkstash.mesh

import android.content.SharedPreferences
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.fromHex(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * This device's identity and mesh settings, kept in app-private storage: its id, a Noise key (who it is on
 * the wire), an Ed25519 key (it signs admission records), its name, and, once in a mesh, the mesh's id and
 * passphrase. The passphrase must stay readable because every new connection and every new member needs it.
 */
class MeshIdentity(private val prefs: SharedPreferences, val nodeId: String, defaultName: String) {
    val noiseKey: NoiseKey
    private val signPrivate: Ed25519PrivateKeyParameters
    val signPublic: ByteArray

    init {
        val nPriv = prefs.getString(K_NOISE_PRIV, null)
        val nPub = prefs.getString(K_NOISE_PUB, null)
        noiseKey = if (nPriv != null && nPub != null) NoiseKey(nPriv.fromHex(), nPub.fromHex())
        else NoiseKey.generate().also { prefs.edit().putString(K_NOISE_PRIV, it.privateKey.toHex()).putString(K_NOISE_PUB, it.publicKey.toHex()).apply() }

        val seed = prefs.getString(K_SIGN_SEED, null)?.fromHex()
            ?: ByteArray(32).also { SecureRandom().nextBytes(it); prefs.edit().putString(K_SIGN_SEED, it.toHex()).apply() }
        signPrivate = Ed25519PrivateKeyParameters(seed, 0)
        signPublic = signPrivate.generatePublicKey().encoded

        if (prefs.getString(K_NAME, null) == null) prefs.edit().putString(K_NAME, defaultName).apply()
    }

    var deviceName: String
        get() = prefs.getString(K_NAME, "My device")!!
        set(v) = prefs.edit().putString(K_NAME, v.trim().ifEmpty { "My device" }).apply()

    /** The mesh this device belongs to (the founder's node id), or null. */
    var meshId: String?
        get() = prefs.getString(K_MESH, null)
        private set(v) = prefs.edit().putString(K_MESH, v).apply()

    var passphrase: String?
        get() = prefs.getString(K_PASS, null)
        private set(v) = prefs.edit().putString(K_PASS, v).apply()

    var listenPort: Int
        get() = prefs.getInt(K_PORT, DEFAULT_PORT)
        set(v) = prefs.edit().putInt(K_PORT, v).apply()

    /** Addresses typed by hand (`host`, `host:port`), tried for every member that has no better one. */
    var extraAddresses: List<String>
        get() = prefs.getString(K_ADDRS, "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString(K_ADDRS, v.joinToString("\n")).apply()

    val inMesh: Boolean get() = meshId != null && passphrase != null

    fun joinMesh(meshId: String, passphrase: String) {
        prefs.edit().putString(K_MESH, meshId).putString(K_PASS, passphrase).apply()
    }

    /** Forgets the mesh and its passphrase. Nothing else changes. */
    fun leaveMesh() {
        prefs.edit().remove(K_MESH).remove(K_PASS).apply()
    }

    fun sign(message: ByteArray): ByteArray =
        Ed25519Signer().apply { init(true, signPrivate); update(message, 0, message.size) }.generateSignature()

    companion object {
        const val DEFAULT_PORT = 41890
        private const val K_NOISE_PRIV = "mesh_noise_priv"
        private const val K_NOISE_PUB = "mesh_noise_pub"
        private const val K_SIGN_SEED = "mesh_sign_seed"
        private const val K_NAME = "mesh_name"
        private const val K_MESH = "mesh_id"
        private const val K_PASS = "mesh_pass"
        private const val K_PORT = "mesh_port"
        private const val K_ADDRS = "mesh_addrs"

        fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = try {
            Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(publicKey, 0)); update(message, 0, message.size) }.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}
