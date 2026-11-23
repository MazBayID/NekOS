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
internal fun isValidCachedIconDimensions(
    width: Int,
    height: Int,
    maxDimensionPx: Int = 96
): Boolean =
    maxDimensionPx > 0 &&
        width in 1..maxDimensionPx &&
        height in 1..maxDimensionPx

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
    private val keyLocks = KeyedMutex()

    private val memoryState = CacheMemoryState()

    /** Read by Compose items so they can drop their own bitmap references on memory trim. */
    var generation by mutableIntStateOf(0)
        private set

    operator fun get(cacheKey: String): ImageBitmap? =
        memoryState.ifEnabled { memoryCache.get(cacheKey) }

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

    suspend fun getCached(cacheKey: String): ImageBitmap? {
        memoryState.ifEnabled { memoryCache.get(cacheKey) }?.let { return it }

        return keyLocks.withLock(cacheKey) {
            memoryState.ifEnabled { memoryCache.get(cacheKey) }?.let {
                return@withLock it
            }
            loadSemaphore.withPermit {
                memoryState.ifEnabled { memoryCache.get(cacheKey) }?.let {
                    return@withPermit it
                }
                val loadEpoch = memoryState.beginLoad() ?: return@withPermit null
                val bitmap = withContext(Dispatchers.IO) {
                    readDiskBitmap(cacheKey)?.asImageBitmap()
                } ?: return@withPermit null
                memoryState.storeIfCurrent(loadEpoch) {
                    memoryCache.put(cacheKey, bitmap)
                    bitmap
                }
            }
        }
    }

    suspend fun getOrLoad(app: InstalledApp): ImageBitmap? {
        memoryState.ifEnabled { memoryCache.get(app.iconCacheKey) }?.let { return it }

        val cacheKey = app.iconCacheKey
        return keyLocks.withLock(cacheKey) {
            memoryState.ifEnabled { memoryCache.get(cacheKey) }?.let {
                return@withLock it
            }

            loadSemaphore.withPermit {
                memoryState.ifEnabled { memoryCache.get(cacheKey) }?.let {
                    return@withPermit it
                }
                val loadEpoch = memoryState.beginLoad() ?: return@withPermit null

                val bitmap = withContext(Dispatchers.IO) {
                    val diskBitmap = readDiskBitmap(cacheKey)
                    val decodedBitmap = diskBitmap ?: createAppIcon(app).also {
                        writeDiskBitmap(cacheKey, it)
                    }
                    decodedBitmap.asImageBitmap()
                }

                memoryState.storeIfCurrent(loadEpoch) {
                    memoryCache.put(cacheKey, bitmap)
                    bitmap
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
        if (apps.isEmpty() || !memoryState.isEnabled()) return@coroutineScope

        val nextIndex = AtomicInteger(0)
        repeat(2) {
            launch {
                while (memoryState.isEnabled()) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= apps.size) break
                    getOrLoad(apps[index])
                    yield()
                }
            }
        }
        memoryState.ifEnabled { generation++ }
        Unit
    }

    /** Release rebuildable bitmap allocations while the launcher is not visible. */
    fun trimMemory() {
        memoryState.trim {
            memoryCache.evictAll()
            generation++
        }
    }

    /**
     * Re-enable icon loading and invalidate visible item references whenever NekOS
     * returns to the foreground. This also recovers if Android cleared bitmap
     * references without the cache's enabled flag reflecting that transition.
     */
    fun resumeAfterBackground() {
        memoryState.resume {
            generation++
        }
    }

    private fun createAppIcon(app: InstalledApp): Bitmap =
        try {
            app.launcherActivityInfo.getBadgedIcon(app.iconDensityDpi).toBitmap(96, 96)
        } catch (_: Exception) {
            appContext.packageManager.defaultActivityIcon.toBitmap(96, 96)
        }

    private fun readDiskBitmap(cacheKey: String): Bitmap? {
        val file = diskFile(cacheKey)
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (!isValidCachedIconDimensions(bounds.outWidth, bounds.outHeight)) {
            file.delete()
            return null
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bitmap == null) {
            file.delete()
            return null
        }
        if (!isValidCachedIconDimensions(bitmap.width, bitmap.height)) {
            bitmap.recycle()
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    private fun writeDiskBitmap(cacheKey: String, bitmap: Bitmap) {
        if (!diskDirectory.isDirectory && !diskDirectory.mkdirs() && !diskDirectory.isDirectory) return

        val target = diskFile(cacheKey)
        val temporary = File(diskDirectory, "${target.name}.${System.nanoTime()}.tmp")
        try {
            val compressed = FileOutputStream(temporary).use { output ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                if (success) output.fd.sync()
                success
            }
            if (!compressed) {
                temporary.delete()
                return
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
        val allFiles = diskDirectory.listFiles()
            ?.filter { it.isFile }
            ?: return
        val staleTemporaryCutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        allFiles
            .filter { it.name.endsWith(".tmp") && it.lastModified() < staleTemporaryCutoff }
            .forEach { it.delete() }

        val files = allFiles.filter { it.name.endsWith(".png") }
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


/**
 * Serializes work per cache key without retaining one Mutex for every app ever seen.
 *
 * A caller is counted before it can wait on the mutex and uncounted in finally,
 * so cancellation and exceptions cannot strand an entry or let a second mutex
 * be created while an earlier caller is still queued.
 */
internal class KeyedMutex {
    private class Entry {
        val mutex = Mutex()
        val users = AtomicInteger()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    internal val trackedKeyCount: Int
        get() = entries.size

    internal fun activeCallCount(key: String): Int =
        entries[key]?.users?.get() ?: 0

    suspend fun <T> withLock(key: String, action: suspend () -> T): T {
        val entry = entries.compute(key) { _, current ->
            (current ?: Entry()).also { it.users.incrementAndGet() }
        } ?: error("Unable to acquire keyed mutex")

        return try {
            entry.mutex.withLock { action() }
        } finally {
            entries.computeIfPresent(key) { _, current ->
                if (current === entry && current.users.decrementAndGet() == 0) {
                    null
                } else {
                    current
                }
            }
        }
    }
}
