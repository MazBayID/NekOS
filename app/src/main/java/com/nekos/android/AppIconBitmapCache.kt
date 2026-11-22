package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Two-level launcher icon cache.
 *
 * The bounded memory cache is discarded when NekOS leaves the foreground.
 * The bounded disk cache lives in filesDir, so Android does not evict it under
 * ordinary cache pressure and it is removed automatically when the app is uninstalled.
 */
class AppIconBitmapCache(
    context: Context,
    maxSizeKb: Int = 4 * 1024,
    private val maxDiskSizeBytes: Long = 32L * 1024L * 1024L
) {
    private val appContext = context.applicationContext
    private val diskDirectory = File(appContext.filesDir, "icon_cache")
    private val memoryCache = object : LruCache<String, ImageBitmap>(maxSizeKb) {
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int =
            (bitmap.width * bitmap.height * 4 / 1024).coerceAtLeast(1)
    }
    private val loadSemaphore = Semaphore(2)

    @Volatile
    private var memoryLoadsEnabled = true

    /** Read by Compose items so they can drop their own bitmap references on memory trim. */
    var generation by mutableIntStateOf(0)
        private set

    operator fun get(cacheKey: String): ImageBitmap? = memoryCache.get(cacheKey)

    suspend fun getOrLoad(app: InstalledApp): ImageBitmap? {
        memoryCache.get(app.iconCacheKey)?.let { return it }
        if (!memoryLoadsEnabled) return null

        return loadSemaphore.withPermit {
            memoryCache.get(app.iconCacheKey)?.let { return@withPermit it }
            if (!memoryLoadsEnabled) return@withPermit null

            withContext(Dispatchers.IO) {
                val diskBitmap = readDiskBitmap(app.iconCacheKey)
                val bitmap = diskBitmap ?: createAppIcon(app).also {
                    writeDiskBitmap(app.iconCacheKey, it)
                }
                bitmap.asImageBitmap()
            }.also { bitmap ->
                if (bitmap != null && memoryLoadsEnabled) {
                    memoryCache.put(app.iconCacheKey, bitmap)
                }
            }
        }
    }

    /** Release rebuildable bitmap allocations while the launcher is not visible. */
    fun trimMemory() {
        memoryLoadsEnabled = false
        memoryCache.evictAll()
        generation++
    }

    /**
     * Re-enable icon loading and invalidate visible item references whenever NekOS
     * returns to the foreground. This also recovers if Android cleared bitmap
     * references without the cache's enabled flag reflecting that transition.
     */
    fun resumeAfterBackground() {
        memoryLoadsEnabled = true
        generation++
    }

    private fun createAppIcon(app: InstalledApp): Bitmap =
        runCatching {
            app.launcherActivityInfo.getBadgedIcon(app.iconDensityDpi).toBitmap(96, 96)
        }.getOrElse {
            appContext.packageManager.defaultActivityIcon.toBitmap(96, 96)
        }

    private fun readDiskBitmap(cacheKey: String): Bitmap? {
        val file = diskFile(cacheKey)
        if (!file.isFile) return null

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    private fun writeDiskBitmap(cacheKey: String, bitmap: Bitmap) {
        if (!diskDirectory.exists() && !diskDirectory.mkdirs()) return

        val target = diskFile(cacheKey)
        val temporary = File(diskDirectory, "${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    return
                }
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            pruneDiskCache()
        } catch (_: Exception) {
            temporary.delete()
        }
    }

    private fun pruneDiskCache() {
        val files = diskDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".png") }
            ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxDiskSizeBytes) return

        for (file in files.sortedBy { it.lastModified() }) {
            if (totalBytes <= maxDiskSizeBytes) break
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
    }

    private fun diskFile(cacheKey: String): File =
        File(diskDirectory, "${cacheFileName(cacheKey)}.png")

    private fun cacheFileName(cacheKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
