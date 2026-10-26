package io.github.jpsutton.inkstash.ui

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aomedia.avif.android.AvifDecoder
import java.nio.ByteBuffer

/**
 * Decodes AVIF with libavif. The Lorcast CDN serves card art only as AVIF, which Android decodes
 * itself from API 31 on; this keeps the art working on older tablets (API 26-30) too.
 */
class AvifCoilDecoder(private val source: ImageSource) : Decoder {
    override suspend fun decode(): DecodeResult = withContext(Dispatchers.Default) {
        val bytes = source.source().readByteArray()
        // libavif reads the buffer through JNI, so it must be a direct one.
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
        buffer.rewind()
        val info = AvifDecoder.Info()
        check(AvifDecoder.getInfo(buffer, bytes.size, info)) { "Not a readable AVIF image" }
        val bitmap = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888)
        check(AvifDecoder.decode(buffer, bytes.size, bitmap)) { "AVIF decode failed" }
        DecodeResult(image = bitmap.asImage(), isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val peek = result.source.source().peek()
            peek.request(HEADER_BYTES.toLong()) // may stop short on a tiny file
            val sniffed = peek.readByteArray(minOf(HEADER_BYTES.toLong(), peek.buffer.size))
            return if (looksLikeAvif(sniffed) || result.mimeType == "image/avif") AvifCoilDecoder(result.source) else null
        }
    }

    companion object {
        private const val HEADER_BYTES = 32

        /** An ISO base media file (bytes 4-7 `ftyp`) whose major brand is `avif` or `avis`. */
        fun looksLikeAvif(head: ByteArray): Boolean {
            if (head.size < 12) return false
            fun at(i: Int) = String(head, i, 4, Charsets.ISO_8859_1)
            return at(4) == "ftyp" && at(8).let { it == "avif" || it == "avis" }
        }
    }
}
