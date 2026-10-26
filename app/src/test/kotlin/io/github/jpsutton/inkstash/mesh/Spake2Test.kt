package io.github.jpsutton.inkstash.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class Spake2Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun big(s: String) = BigInteger(s, 16)

    private class Vector(
        val a: String, val b: String, val w: String, val x: String, val pA: String, val y: String, val pB: String,
        val tt: String, val hash: String,
    )

    // RFC 9382 Appendix B, P256-SHA256-HKDF-HMAC. (Vectors 1 and 4: identities 'server'/'client' and empty/empty.)
    private val vectors = listOf(
        Vector(
            "server", "client",
            "2ee57912099d31560b3a44b1184b9b4866e904c49d12ac5042c97dca461b1a5f",
            "43dd0fd7215bdcb482879fca3220c6a968e66d70b1356cac18bb26c84a78d729",
            "04a56fa807caaa53a4d28dbb9853b9815c61a411118a6fe516a8798434751470f9010153ac33d0d5f2047ffdb1a3e42c9b4e6be662766e1eeb4116988ede5f912c",
            "dcb60106f276b02606d8ef0a328c02e4b629f84f89786af5befb0bc75b6e66be",
            "0406557e482bd03097ad0cbaa5df82115460d951e3451962f1eaf4367a420676d09857ccbc522686c83d1852abfa8ed6e4a1155cf8f1543ceca528afb591a1e0b7",
            "06000000000000007365727665720600000000000000636c69656e7441000000000000000" +
                "4a56fa807caaa53a4d28dbb9853b9815c61a411118a6fe516a8798434751470f9010153ac33d0d5f2047ffdb1a3e42c9b4e6be662766e1eeb4116988ede5f912c" +
                "41000000000000000406557e482bd03097ad0cbaa5df82115460d951e3451962f1eaf4367a420676d09857ccbc522686c83d1852abfa8ed6e4a1155cf8f1543ceca528afb591a1e0b7" +
                "4100000000000000" +
                "0412af7e89717850671913e6b469ace67bd90a4df8ce45c2af19010175e37eed69f75897996d539356e2fa6a406d528501f907e04d97515fbe83db277b715d3325" +
                "20000000000000002ee57912099d31560b3a44b1184b9b4866e904c49d12ac5042c97dca461b1a5f",
            "0e0672dc86f8e45565d338b0540abe6915bdf72e2b35b5c9e5663168e960a91b",
        ),
        Vector(
            "", "",
            "7bf46c454b4c1b25799527d896508afd5fc62ef4ec59db1efb49113063d70cca",
            "8cef65df64bb2d0f83540c53632de911b5b24b3eab6cc74a97609fd659e95473",
            "04a65b367a3f613cf9f0654b1b28a1e3a8a40387956c8ba6063e8658563890f46ca1ef6a676598889fc28de2950ab8120b79a5ef1ea4c9f44bc98f585634b46d66",
            "d7a66f64074a84652d8d623a92e20c9675c61cb5b4f6a0063e4648a2fdc02d53",
            "04589f13218822710d98d8b2123a079041052d9941b9cf88c6617ddb2fcc0494662eea8ba6b64692dc318250030c6af045cb738bc81ba35b043c3dcb46adf6f58d",
            "0000000000000000" + "0000000000000000" +
                "410000000000000004a65b367a3f613cf9f0654b1b28a1e3a8a40387956c8ba6063e8658563890f46ca1ef6a676598889fc28de2950ab8120b79a5ef1ea4c9f44bc98f585634b46d66" +
                "410000000000000004589f13218822710d98d8b2123a079041052d9941b9cf88c6617ddb2fcc0494662eea8ba6b64692dc318250030c6af045cb738bc81ba35b043c3dcb46adf6f58d" +
                "4100000000000000041a3c03d51b452537ca2a1fea6110353c6d5ed483c4f0f86f4492ca3f378d40a994b4477f93c64d928edbbcd3e85a7c709b7ea73ee97986ce3d1438e135543772" +
                "20000000000000007bf46c454b4c1b25799527d896508afd5fc62ef4ec59db1efb49113063d70cca",
            "fc6374762ba5cf11f4b2caa08b2cd1b9907ae0e26e8d6234318d91583cd74c86",
        ),
    )

    @Test fun matchesTheRfcTestVectorsOnBothSides() {
        for (v in vectors) {
            val a = Spake2(Spake2.Role.A, big(v.w), v.a.toByteArray(), v.b.toByteArray(), big(v.x))
            val b = Spake2(Spake2.Role.B, big(v.w), v.a.toByteArray(), v.b.toByteArray(), big(v.y))
            assertEquals(v.pA, a.message.toHex())
            assertEquals(v.pB, b.message.toHex())
            val ra = a.finish(b.message)
            val rb = b.finish(a.message)
            assertEquals(v.tt, ra.transcript.toHex())
            assertEquals(v.hash, ra.key.toHex())
            assertArrayEquals(ra.key, rb.key)
        }
    }

    @Test fun samePassphraseGivesTheSameKey() {
        val a = Spake2.start(Spake2.Role.A, "correct horse")
        val b = Spake2.start(Spake2.Role.B, "correct horse")
        assertArrayEquals(a.finish(b.message).key, b.finish(a.message).key)
    }

    @Test fun differentPassphrasesGiveDifferentKeys() {
        val a = Spake2.start(Spake2.Role.A, "correct horse")
        val b = Spake2.start(Spake2.Role.B, "battery staple")
        assertFalse(a.finish(b.message).key.contentEquals(b.finish(a.message).key))
    }

    @Test fun eachRunUsesFreshSecrets() {
        assertFalse(Spake2.start(Spake2.Role.A, "x").message.contentEquals(Spake2.start(Spake2.Role.A, "x").message))
    }

    @Test fun badPeerMessagesAreRejected() {
        val a = Spake2.start(Spake2.Role.A, "x")
        assertThrows(IllegalArgumentException::class.java) { a.finish(ByteArray(10)) }
        assertThrows(IllegalArgumentException::class.java) { a.finish(ByteArray(65) { 4 }) } // not on the curve
        assertThrows(IllegalArgumentException::class.java) { a.finish(ByteArray(65)) } // wrong prefix
    }
}
