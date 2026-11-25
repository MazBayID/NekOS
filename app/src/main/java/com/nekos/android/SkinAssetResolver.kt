package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Loads skin artwork from the selected skin directory and falls back to the
 * APK-bundled default asset on a per-file basis.
 *
 * Skin installation/selection is intentionally managed by a later layer; this
 * resolver already enforces path containment and safe fallback semantics.
 */
internal class SkinAssetResolver(context: Context) {
    private val appContext = context.applicationContext
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Volatile
    private var activeSkinDirectory: File? = null

    fun clearCache() {
        synchronized(cache) { cache.evictAll() }
    }

    fun setActiveSkinDirectory(directory: File?) {
        activeSkinDirectory = directory?.canonicalFile
        synchronized(cache) { cache.evictAll() }
    }

    @Synchronized
    fun loadBitmap(
        relativePath: String,
        expectedWidth: Int? = null,
        expectedHeight: Int? = null
    ): Bitmap? {
        if (!isSafeRelativePath(relativePath)) return null
        val selectedSkinDirectory = activeSkinDirectory ?: SkinRepository.activeSkinDirectory(appContext)
        val skinKey = selectedSkinDirectory?.absolutePath ?: "default"
        val cacheKey = "${skinKey}:${relativePath}:${expectedWidth ?: 0}x${expectedHeight ?: 0}"
        cache.get(cacheKey)?.let { return it }

        val activeBitmap = selectedSkinDirectory
            ?.let { loadFromDirectory(it, relativePath) }
            ?.takeIf { matchesExpectedSize(it, expectedWidth, expectedHeight) }

        val bitmap = activeBitmap ?: loadFromBundledDefault(relativePath)
            ?.takeIf { matchesExpectedSize(it, expectedWidth, expectedHeight) }
            ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun loadFromDirectory(root: File, relativePath: String): Bitmap? =
        runCatching {
            val canonicalRoot = root.canonicalFile
            val candidate = File(canonicalRoot, relativePath).canonicalFile
            if (!candidate.path.startsWith(canonicalRoot.path + File.separator) ||
                !candidate.isFile
            ) return null
            BitmapFactory.decodeFile(candidate.absolutePath)
        }.getOrNull()

    private fun loadFromBundledDefault(relativePath: String): Bitmap? =
        runCatching {
            appContext.assets.open("skins/default/$relativePath").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()

    private fun matchesExpectedSize(
        bitmap: Bitmap,
        expectedWidth: Int?,
        expectedHeight: Int?
    ): Boolean =
        (expectedWidth == null || bitmap.width == expectedWidth) &&
            (expectedHeight == null || bitmap.height == expectedHeight)

    companion object {
        internal fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank() || path.startsWith("/") || '\\' in path || '\u0000' in path) {
                return false
            }
            return path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
        }
    }
}
