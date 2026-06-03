package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Loads gallery / camera images at a safe size to avoid OOM on high-resolution phone photos.
 */
object BitmapLoader {

    const val MAX_DIMENSION = 2048

    fun loadScaledFromUri(context: Context, uri: Uri, maxDimension: Int = MAX_DIMENSION): Bitmap {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            decodeWithImageDecoder(context, uri, maxDimension)
        } else {
            decodeWithBitmapFactory(context, uri, maxDimension)
        }
        return ensureSoftwareBitmap(decoded, maxDimension)
    }

    /** [TakePicturePreview] and some decoders may return hardware bitmaps that crash when copied/compressed. */
    fun ensureSoftwareBitmap(source: Bitmap, maxDimension: Int = MAX_DIMENSION): Bitmap {
        val scaled = scaleDownIfNeeded(source, maxDimension)
        return if (scaled.config == Bitmap.Config.HARDWARE) {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) {
                if (scaled !== source) scaled.recycle()
                copy
            } else {
                scaled
            }
        } else {
            scaled
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (max(w, h) <= maxDimension) return source
        val scale = maxDimension.toFloat() / max(w, h)
        val nw = max(1, (w * scale).roundToInt())
        val nh = max(1, (h * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, nw, nh, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    @androidx.annotation.RequiresApi(28)
    private fun decodeWithImageDecoder(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val largest = max(w, h)
            if (largest > maxDimension) {
                val scale = largest.toFloat() / maxDimension
                decoder.setTargetSize(
                    max(1, (w / scale).roundToInt()),
                    max(1, (h / scale).roundToInt())
                )
            }
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: throw IllegalArgumentException("Could not decode image")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        if (height > maxDimension || width > maxDimension) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
