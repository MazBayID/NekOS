package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.LruCache
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

/**
 * Accepts paths contained in the icon catalog's dedicated assets/icons directory.
 *
 * Rejecting empty and dot path segments keeps asset lookup unambiguous and prevents
 * traversal outside the catalog directory.
 */
internal fun isValidCatalogIconPath(path: String): Boolean {
    if (!path.startsWith("icons/") || '\\' in path || '\u0000' in path) {
        return false
    }

    val segments = path.split('/')
    return segments.size >= 2 &&
        segments.first() == "icons" &&
        segments.drop(1).all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".."
        }
}

/**
 * Reads the tag-icon catalog bundled under assets/ikon.
 * The JSON index is the source of truth; images are decoded only when requested.
 */
class IconCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private var cachedIcons: List<CatalogIcon>? = null
    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int =
            (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val memoryState = CacheMemoryState()

    /** Invalidates TagPill bitmap references after a memory trim or resume. */
    var generation by mutableIntStateOf(0)
        private set

    @Synchronized
    fun loadCatalog(): List<CatalogIcon> {
        cachedIcons?.let { return it }

        val jsonText = appContext.assets.open("ikon/icons_index.json")
            .bufferedReader()
            .use { it.readText() }
        val items = JSONObject(jsonText).getJSONArray("items")
        val icons = buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val file = item.getString("file")
                require(isValidCatalogIconPath(file)) {
                    "Invalid catalog asset path: $file"
                }
                add(
                    CatalogIcon(
                        id = item.getInt("id"),
                        file = file,
                        name = item.getString("name"),
                        visual = item.optString("visual"),
                        description = item.optString("description"),
                        row = item.optInt("row"),
                        column = item.optInt("column")
                    )
                )
            }
        }

        require(icons.map { it.id }.distinct().size == icons.size) {
            "Catalog icon IDs must be unique"
        }
        cachedIcons = icons
        return icons
    }

    @Synchronized
    fun trimMemory() {
        memoryState.trim {
            bitmapCache.evictAll()
            generation++
        }
    }

    fun resumeAfterBackground() {
        memoryState.resume {
            generation++
        }
    }

    @Synchronized
    fun loadBitmap(
        icon: CatalogIcon,
        frameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR
    ): Bitmap? {
        require(isValidCatalogIconPath(icon.file)) {
            "Invalid catalog asset path: ${icon.file}"
        }
        val normalizedColor = frameColorArgb and 0xFFFFFFFFL
        val cacheKey = "${icon.file}:${normalizedColor.toString(16)}"
        memoryState.ifEnabled { bitmapCache.get(cacheKey) }?.let { return it }
        val loadEpoch = memoryState.beginLoad() ?: return null

        val source = appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val bitmap = recolorIconFrame(source, normalizedColor)

        return memoryState.storeIfCurrent(loadEpoch) {
            bitmapCache.put(cacheKey, bitmap)
            bitmap
        }
    }

    /**
     * Recolors pixels belonging to the catalog's teal circular frame.
     * Hue matching leaves the white glyph and transparent pixels untouched;
     * relative saturation/value preserve antialiasing and frame shading.
     */
    private fun recolorIconFrame(source: Bitmap, targetColorArgb: Long): Bitmap {
        if (targetColorArgb == DEFAULT_TAG_ICON_FRAME_COLOR) return source

        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val width = output.width
        val height = output.height
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)

        val sourceHsv = FloatArray(3)
        val targetHsv = FloatArray(3)
        val pixelHsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.rgb(0x11, 0x4B, 0x56), sourceHsv)
        AndroidColor.colorToHSV(targetColorArgb.toInt(), targetHsv)
        val sourceValue = sourceHsv[2].coerceAtLeast(0.01f)
        val sourceSaturation = sourceHsv[1].coerceAtLeast(0.01f)

        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = AndroidColor.alpha(pixel)
            if (alpha == 0) continue

            AndroidColor.colorToHSV(pixel, pixelHsv)
            val rawHueDifference = abs(pixelHsv[0] - sourceHsv[0])
            val hueDifference = minOf(rawHueDifference, 360f - rawHueDifference)
            if (hueDifference <= 28f && pixelHsv[1] >= 0.12f) {
                pixelHsv[0] = targetHsv[0]
                pixelHsv[1] = (
                    pixelHsv[1] * targetHsv[1] / sourceSaturation
                ).coerceIn(0f, 1f)
                pixelHsv[2] = (
                    pixelHsv[2] * targetHsv[2] / sourceValue
                ).coerceIn(0f, 1f)
                pixels[index] = AndroidColor.HSVToColor(alpha, pixelHsv)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
