package com.glyph.tracker.util

import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

object AppIconCache {
    // Cache up to 150 app icons in memory as Compose ImageBitmap
    private val cache = LruCache<String, ImageBitmap>(150)

    fun get(packageName: String): ImageBitmap? {
        return cache.get(packageName)
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    fun getOrCreate(packageName: String, drawable: Drawable?): ImageBitmap? {
        if (packageName.isEmpty()) return null
        cache.get(packageName)?.let { return it }
        if (drawable == null) return null

        return try {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 144) else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 144) else 96
            val bitmap = drawable.toBitmap(width = width, height = height).asImageBitmap()
            cache.put(packageName, bitmap)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    fun prewarm(packageName: String, drawable: Drawable?) {
        if (packageName.isNotEmpty() && drawable != null && cache.get(packageName) == null) {
            try {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 144) else 96
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 144) else 96
                val bitmap = drawable.toBitmap(width = width, height = height).asImageBitmap()
                cache.put(packageName, bitmap)
            } catch (_: Throwable) {
                // Ignore failure for prewarming
            }
        }
    }
}
