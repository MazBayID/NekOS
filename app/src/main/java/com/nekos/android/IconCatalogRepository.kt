package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

data class CatalogIcon(
    val id: Int,
    val file: String,
    val name: String,
    val visual: String,
    val description: String,
    val row: Int,
    val column: Int
)

internal fun isValidCachedTagIconDimensions(width: Int, height: Int, maxDimensionPx: Int = 128): Boolean =
    maxDimensionPx > 0 && width in 1..maxDimensionPx && height in 1..maxDimensionPx

internal fun isValidCatalogIconPath(path: String): Boolean {
    if (!path.startsWith("icons/tag/") || '\\' in path || '\u0000' in path) return false
    val parts = path.split('/')
    return parts.size == 3 && parts[0] == "icons" && parts[1] == "tag" &&
        parts[2].isNotBlank() && parts[2] != "." && parts[2] != ".."
}

internal fun isValidCatalogIconEntry(id: Int, file: String, name: String, row: Int, column: Int): Boolean =
    id > 0 && isValidCatalogIconPath(file) && name.isNotBlank() && row > 0 && column > 0

/** Loads semantic skin glyphs and composites them over the default skin badge. */
class IconCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val skinAssets = SkinAssetResolver(appContext)
    private var cachedIcons: List<CatalogIcon>? = null
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val memoryState = CacheMemoryState()
    private val pickerLoadSemaphore = Semaphore(2)

    var generation by mutableIntStateOf(0)
        private set

    @Synchronized
    fun loadCatalog(): List<CatalogIcon> {
        cachedIcons?.let { return it }
        val json = appContext.assets.open("skins/default/icons/tag/icons_index.json")
            .bufferedReader().use { it.readText() }
        val items = JSONObject(json).getJSONArray("items")
        val icons = buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val filename = item.getString("file")
                val legacyFile = item.getString("legacy_file")
                val id = legacyFile.substringAfter("icon_").substringBefore(".").toIntOrNull()
                    ?: error("Invalid legacy icon reference: $legacyFile")
                val file = "icons/tag/$filename"
                val row = index / 6 + 1
                val column = index % 6 + 1
                require(isValidCatalogIconEntry(id, file, item.getString("name"), row, column))
                add(CatalogIcon(id, file, item.getString("name"), item.optString("visual"),
                    item.optString("description"), row, column))
            }
        }
        require(icons.map { it.id }.distinct().size == icons.size) { "Canonical icon IDs must be unique" }
        require(icons.map { it.file }.distinct().size == icons.size) { "Skin icon paths must be unique" }
        cachedIcons = icons
        return icons
    }

    @Synchronized
    fun trimMemory() {
        memoryState.trim {
            bitmapCache.evictAll()
            skinAssets.clearCache()
            generation++
        }
    }

    fun resumeAfterBackground() {
        memoryState.resume { generation++ }
    }

    suspend fun loadPickerBitmap(
        icon: CatalogIcon,
        frameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR,
        maxDimensionPx: Int = 96
    ): Bitmap? = pickerLoadSemaphore.withPermit { loadBitmap(icon, frameColorArgb, maxDimensionPx) }

    @Synchronized
    fun loadBitmap(
        icon: CatalogIcon,
        frameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR,
        maxDimensionPx: Int = 128
    ): Bitmap? {
        require(isValidCatalogIconPath(icon.file)) { "Invalid catalog asset path: \${icon.file}" }
        val target = maxDimensionPx.coerceAtLeast(1)
        val color = frameColorArgb and 0xFFFFFFFFL
        val key = "\${icon.file}:\${color.toString(16)}:$target"
        memoryState.ifEnabled { bitmapCache.get(key) }?.let { return it }
        val epoch = memoryState.beginLoad() ?: return null

        val badge = skinAssets.loadBitmap("components/tag_badge/badge_cat.webp", 128, 128) ?: return null
        val glyph = skinAssets.loadBitmap("icons/tag/\${icon.file.substringAfterLast('/')}", 96, 96) ?: return null
        val composed = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color.toInt(), PorterDuff.Mode.SRC_IN)
        }
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(onBadgeColor(color.toInt()), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(badge, null, Rect(0, 0, 128, 128), badgePaint)
        canvas.drawBitmap(glyph, null, Rect(16, 22, 112, 118), glyphPaint)
        val result = if (target == 128) composed else
            Bitmap.createScaledBitmap(composed, target, target, true).also { composed.recycle() }

        return memoryState.storeIfCurrent(epoch) {
            bitmapCache.put(key, result)
            result
        }
    }

    private fun onBadgeColor(background: Int): Int {
        val light = AndroidColor.rgb(0xF5, 0xF2, 0xED)
        val dark = AndroidColor.rgb(0x1B, 0x1B, 0x1F)
        return if (contrastRatio(light, background) >= 3f) light else dark
    }

    private fun contrastRatio(first: Int, second: Int): Float {
        val a = AndroidColor.luminance(first)
        val b = AndroidColor.luminance(second)
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
