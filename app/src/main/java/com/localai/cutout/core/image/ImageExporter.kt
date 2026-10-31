package com.localai.cutout.core.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageExporter {
    fun save(context: Context, bitmap: Bitmap, namePrefix: String): android.net.Uri {
        val safePrefix = namePrefix
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "dapple-image" }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safePrefix-$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dapple")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create image")
        try { context.contentResolver.openOutputStream(uri)?.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("PNG encoding failed") } ?: error("Could not open output") }
        catch (e: Throwable) { context.contentResolver.delete(uri, null, null); throw e }
        return uri
    }
}
