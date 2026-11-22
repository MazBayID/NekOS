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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Two-level launcher icon cache.
 *
 * The bounded memory cache is retained across ordinary launcher transitions and
 * released when Android reports substantial background memory pressure.
 * The bounded disk cache lives in filesDir and is removed automatically on uninstall.
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
    private class KeyLockEntry {
        val mutex = Mutex()
        var users: Int = 0
    }

    private val keyLocks = ConcurrentHashMap<String, KeyLockEntry>()

    @Volatile
    private var memoryLoadsEnabled = true

    /** Read by Compose items so they can drop their own bitmap references on memory trim. */
    var generation by mutableIntStateOf(0)
        private set

    operator fun get(cacheKey: String): ImageBitmap? = memoryCache.get(cacheKey)

    /**
     * Size the RAM cache for the current catalog, while keeping it within a
     * conservative fraction of this device's application memory class.
     * Icons are decoded at 96x96 ARGB_8888 (about 36 KiB each).
     */
    fun configureForAppCount(appCount: Int) {
        val activityManager = appContext.getSystemService(android.app.ActivityManager::class.java)
        val deviceBudgetKb = (activityManager.memoryClass * 1024 / 8)
            .coerceIn(4 * 1024, 24 * 1024)
        val estimatedCatalogKb = (appCount.coerceAtLeast(1) * 36L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        memoryCache.resize(estimatedCatalogKb.coerceIn(4 * 1024, deviceBudgetKb))
    }

    suspend fun getOrLoad(app: InstalledApp): ImageBitmap? {
        memoryCache.get(app.iconCacheKey)?.let { return it }
        if (!memoryLoadsEnabled) return null

        val cacheKey = app.iconCacheKey
        // Count both the active loader and any waiters before entering the mutex.
        // The entry is removed only after the last caller has left, so a new caller
        // cannot create a second mutex while another request is still waiting.
        val lockEntry = keyLocks.compute(cacheKey) { _, current ->
            (current ?: KeyLockEntry()).also { it.users++ }
        } ?: error("Unable to acquire icon cache key lock")

        return try {
            lockEntry.mutex.withLock {
                memoryCache.get(cacheKey)?.let { return@withLock it }
                if (!memoryLoadsEnabled) return@withLock null

                loadSemaphore.withPermit {
                    memoryCache.get(cacheKey)?.let { return@withPermit it }
                    if (!memoryLoadsEnabled) return@withPermit null

                    withContext(Dispatchers.IO) {
                        val diskBitmap = readDiskBitmap(cacheKey)
                        val bitmap = diskBitmap ?: createAppIcon(app).also {
                            writeDiskBitmap(cacheKey, it)
                        }
                        bitmap.asImageBitmap()
                    }.also { bitmap ->
                        if (bitmap != null && memoryLoadsEnabled) {
                            memoryCache.put(cacheKey, bitmap)
                        }
                    }
                }
            }
        } finally {
            keyLocks.computeIfPresent(cacheKey) { _, current ->
                if (current === lockEntry) {
                    current.users--
                    if (current.users == 0) null else current
                } else {
                    current
                }
            }
        }
    }

    /**
     * Warm the complete app catalog in the background using two workers.
     * Per-key locking prevents duplicate decoding when UI items request an
     * icon while the preload is still running.
     */
    suspend fun preload(apps: List<InstalledApp>) = coroutineScope {
        if (apps.isEmpty() || !memoryLoadsEnabled) return@coroutineScope

        val nextIndex = AtomicInteger(0)
        repeat(2) {
            launch {
                while (memoryLoadsEnabled) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= apps.size) break
                    getOrLoad(apps[index])
                    yield()
                }
            }
        }
        if (memoryLoadsEnabled) generation++
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
