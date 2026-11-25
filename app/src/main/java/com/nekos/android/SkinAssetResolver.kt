package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.util.WeakHashMap

/**
 * Loads skin artwork from the selected skin directory and falls back to the
 * APK-bundled default asset on a per-file basis.
 *
 * The selected skin is read from SkinRepository and missing or invalid files
 * fall back independently to the APK-bundled default skin.
 */
internal class SkinAssetResolver(context: Context) {
    private val appContext = context.applicationContext
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Volatile
    private var activeSkinDirectory: File? = null

    private var cacheEpoch = 0L

    init {
        synchronized(instances) { instances[this] = Unit }
    }

    fun clearCache() {
        synchronized(cache) {
            cacheEpoch++
            cache.evictAll()
        }
    }

    fun setActiveSkinDirectory(directory: File?) {
        activeSkinDirectory = directory?.canonicalFile
        synchronized(cache) {
            cacheEpoch++
            cache.evictAll()
        }
    }

    @Synchronized
    fun loadBitmap(
        relativePath: String,
        expectedWidth: Int? = null,
        expectedHeight: Int? = null
    ): Bitmap? {
        if (!isSafeRelativePath(relativePath)) return null
        val requestedCacheEpoch = synchronized(cache) { cacheEpoch }
        val generationBeforeSkinRead = SkinRuntimeState.generation
        val selectedSkinDirectory = activeSkinDirectory ?: SkinRepository.activeSkinDirectory(appContext)
        val requestedGeneration = SkinRuntimeState.generation
        if (generationBeforeSkinRead != requestedGeneration) return null
        val skinKey = selectedSkinDirectory?.absolutePath ?: "default"
        val cacheKey = "${requestedGeneration}:${skinKey}:${relativePath}:${expectedWidth ?: 0}x${expectedHeight ?: 0}"
        synchronized(cache) {
            cache.get(cacheKey)?.let {
                return if (requestedGeneration == SkinRuntimeState.generation &&
                    requestedCacheEpoch == cacheEpoch
                ) it else null
            }
        }

        val activeBitmap = selectedSkinDirectory
            ?.let { loadFromDirectory(it, relativePath) }
            ?.let { bitmap ->
                if (matchesExpectedSize(bitmap, expectedWidth, expectedHeight)) bitmap
                else bitmap.recycle().let { null }
            }

        val bitmap = activeBitmap ?: loadFromBundledDefault(relativePath)
            ?.let { fallback ->
                if (matchesExpectedSize(fallback, expectedWidth, expectedHeight)) fallback
                else fallback.recycle().let { null }
            }
            ?: return null

        return synchronized(cache) {
            if (requestedGeneration != SkinRuntimeState.generation ||
                requestedCacheEpoch != cacheEpoch
            ) {
                bitmap.recycle()
                null
            } else {
                cache.put(cacheKey, bitmap)
                bitmap
            }
        }
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
        private val instances = WeakHashMap<SkinAssetResolver, Unit>()

        /** Evicts every live resolver cache after Android reports memory pressure. */
        fun trimAllCaches() {
            val resolvers = synchronized(instances) { instances.keys.toList() }
            resolvers.forEach(SkinAssetResolver::clearCache)
        }

        internal fun isSafeRelativePath(path: String): Boolean {
            if (path.isBlank() || path.startsWith("/") || '\\' in path || '\u0000' in path) {
                return false
            }
            return path.split('/').all { it.isNotBlank() && it != "." && it != ".." }
        }
    }
}
