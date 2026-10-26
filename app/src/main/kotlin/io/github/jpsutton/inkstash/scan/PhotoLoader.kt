package io.github.jpsutton.inkstash.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/** Decodes a photo upright (EXIF rotation applied), down-sampled so its longest side is at most [maxSide]. */
object PhotoLoader {
    const val MAX_SIDE = 4096

    fun load(resolver: ContentResolver, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw java.io.IOException("Couldn't read that photo")
        val orientation = resolver.openInputStream(uri)!!.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }
}
