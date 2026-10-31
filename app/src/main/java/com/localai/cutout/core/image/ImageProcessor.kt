package com.localai.cutout.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageProcessor {
    private fun objectRemovalPixelLimit(): Long = (Runtime.getRuntime().maxMemory() / 96L).coerceAtMost(3_000_000L).coerceAtLeast(500_000L)
    private fun upscalerPixelLimit(): Long = (Runtime.getRuntime().maxMemory() / 256L).coerceAtMost(1_200_000L).coerceAtLeast(250_000L)

    /** Returns a bounded editing copy, or the original bitmap if it already fits. */
    fun fitForObjectRemoval(bitmap: Bitmap): Bitmap {
        return fitToPixelLimit(bitmap, objectRemovalPixelLimit())
    }

    fun fitForBackgroundRemoval(bitmap: Bitmap): Bitmap {
        return fitToPixelLimit(bitmap, objectRemovalPixelLimit())
    }

    fun fitForUpscaling(bitmap: Bitmap): Bitmap {
        return fitToPixelLimit(bitmap, upscalerPixelLimit())
    }

    private fun fitToPixelLimit(bitmap: Bitmap, maxPixels: Long): Bitmap {
        val pixels = bitmap.width.toLong() * bitmap.height
        if (pixels <= maxPixels) return bitmap
        val scale = kotlin.math.sqrt(maxPixels.toDouble() / pixels)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Decodes an editing copy sized to a conservative per-device memory budget. */
    fun decodeForObjectRemoval(context: Context, uri: Uri): Bitmap = decodeBounded(context, uri, objectRemovalPixelLimit())

    fun decodeForBackgroundRemoval(context: Context, uri: Uri): Bitmap = decodeBounded(context, uri, objectRemovalPixelLimit())

    fun decodeForUpscaling(context: Context, uri: Uri): Bitmap = decodeBounded(context, uri, upscalerPixelLimit())

    private fun decodeBounded(context: Context, uri: Uri, maxPixels: Long): Bitmap {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            android.graphics.BitmapFactory.decodeStream(input ?: error("Cannot open image"), null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use { ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swapsDimensions = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val orientedWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
        val orientedHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
        val originalPixels = orientedWidth.toLong() * orientedHeight
        var sampleSize = 1
        while ((bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight.toLong() / sampleSize) > maxPixels) {
            sampleSize *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            android.graphics.BitmapFactory.decodeStream(input ?: error("Cannot open image"), null, options)
                ?: error("Unsupported image")
        }
        val oriented = orient(decoded, orientation)
        val resized = fitToPixelLimit(oriented, maxPixels)
        if (resized !== oriented) oriented.recycle()
        // Keep this useful for callers diagnosing a resize without retaining the original bitmap.
        if (originalPixels > maxPixels) android.util.Log.i("DappleObjectRemover", "Prepared ${resized.width}x${resized.height} editing copy from ${orientedWidth}x${orientedHeight}")
        return resized
    }

    fun decode(context: Context, uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactoryCompat.decode(it ?: error("Cannot open image")) }
        val orientation = runCatching { context.contentResolver.openInputStream(uri).use { ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(270f) }
            }
        }
        return if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(270f) }
                else -> return bitmap
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}

private object BitmapFactoryCompat {
    fun decode(input: java.io.InputStream): Bitmap {
        val decoded = android.graphics.BitmapFactory.decodeStream(input) ?: error("Unsupported image")
        if (decoded.config == Bitmap.Config.ARGB_8888 && !decoded.isRecycled) return decoded
        val converted = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: error("Could not decode image pixels")
        decoded.recycle()
        return converted
    }
}
