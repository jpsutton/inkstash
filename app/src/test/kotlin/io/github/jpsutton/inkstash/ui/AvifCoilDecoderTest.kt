package io.github.jpsutton.inkstash.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvifCoilDecoderTest {
    private fun box(brand: String) = byteArrayOf(0, 0, 0, 0x1c) + "ftyp".toByteArray() + brand.toByteArray() + ByteArray(16)

    @Test fun avifBrandsAreRecognized() {
        assertTrue(AvifCoilDecoder.looksLikeAvif(box("avif")))
        assertTrue(AvifCoilDecoder.looksLikeAvif(box("avis")))
    }

    @Test fun otherFilesAreNot() {
        assertFalse(AvifCoilDecoder.looksLikeAvif(box("heic")))
        assertFalse(AvifCoilDecoder.looksLikeAvif(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(20)))
        assertFalse(AvifCoilDecoder.looksLikeAvif(ByteArray(4)))
    }
}
