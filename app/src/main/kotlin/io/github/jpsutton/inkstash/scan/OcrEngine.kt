package io.github.jpsutton.inkstash.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Reads the text lines of a photo. Interface so the pipeline runs headless in tests. */
fun interface TextReader {
    suspend fun read(bitmap: Bitmap): List<OcrLine>
}

/** ML Kit's bundled (no download) Latin text recognizer. */
class OcrEngine : TextReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(bitmap: Bitmap): List<OcrLine> {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom)
        }
    }

    fun close() = recognizer.close()
}
