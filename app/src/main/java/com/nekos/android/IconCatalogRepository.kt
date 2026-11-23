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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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

/** Rejects incomplete or malformed entries before they enter the icon picker. */
internal fun isValidCatalogIconEntry(
    id: Int,
    file: String,
    name: String,
    row: Int,
    column: Int
): Boolean =
    id > 0 &&
        isValidCatalogIconPath(file) &&
        name.isNotBlank() &&
        row > 0 &&
        column > 0

/**
 * Reads the tag-icon catalog bundled under assets/ikon.
 * The JSON index is the source of truth; images are decoded only when requested.
 */
class IconCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val displayDiskDirectory = File(appContext.filesDir, "tag_icon_cache")
    private var cachedIcons: List<CatalogIcon>? = null
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int =
            (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }
    // Picker thumbnails use a separate cache so full-size tag icons cannot evict them.
    // 8 MB can retain the 100 catalog icons at the picker's 96 px target size.
    private val pickerBitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
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
                val id = item.getInt("id")
                val name = item.getString("name")
                val row = item.optInt("row")
                val column = item.optInt("column")
                require(isValidCatalogIconEntry(id, file, name, row, column)) {
                    "Invalid catalog icon entry: id=$id, file=$file, name=$name, row=$row, column=$column"
                }
                add(
                    CatalogIcon(
                        id = id,
                        file = file,
                        name = name,
                        visual = item.optString("visual"),
                        description = item.optString("description"),
                        row = row,
                        column = column
                    )
                )
            }
        }

        require(icons.map { it.id }.distinct().size == icons.size) {
            "Catalog icon IDs must be unique"
        }
        require(icons.map { it.file }.distinct().size == icons.size) {
            "Catalog icon asset paths must be unique"
        }
        cachedIcons = icons
        return icons
    }

    @Synchronized
    fun trimMemory() {
        memoryState.trim {
            bitmapCache.evictAll()
            pickerBitmapCache.evictAll()
            generation++
        }
    }

    fun resumeAfterBackground() {
        memoryState.resume {
            generation++
        }
    }

    /**
     * Loads a reduced-size bitmap for the icon picker. Icons render at roughly
     * 28 dp, so a 96 px thumbnail is sufficient for high-density displays.
     * The original asset is decoded near the target size, then scaled exactly.
     */
    fun loadPickerBitmap(
        icon: CatalogIcon,
        frameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR,
        maxDimensionPx: Int = 96
    ): Bitmap? {
        require(isValidCatalogIconPath(icon.file)) {
            "Invalid catalog asset path: ${icon.file}"
        }
        val normalizedColor = frameColorArgb and 0xFFFFFFFFL
        val targetSize = maxDimensionPx.coerceAtLeast(1)
        val cacheKey = "${icon.file}:${normalizedColor.toString(16)}:picker:$targetSize"
        memoryState.ifEnabled { pickerBitmapCache.get(cacheKey) }?.let { return it }
        val loadEpoch = memoryState.beginLoad() ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= targetSize) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null
        val scale = minOf(
            1f,
            targetSize.toFloat() / maxOf(source.width, source.height)
        )
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val thumbnail = if (
            source.width == targetWidth && source.height == targetHeight
        ) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
                .also { source.recycle() }
        }
        val bitmap = recolorIconFrame(thumbnail, normalizedColor)

        return memoryState.storeIfCurrent(loadEpoch) {
            pickerBitmapCache.put(cacheKey, bitmap)
            bitmap
        }
    }

    /**
     * Loads a display-sized Tag icon. Home pills render at 20 dp and the editor
     * preview is only slightly larger, so 128 px preserves sharpness on dense
     * screens without retaining the original 512 px asset in memory.
     */
    @Synchronized
    fun loadBitmap(
        icon: CatalogIcon,
        frameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR,
        maxDimensionPx: Int = 128
    ): Bitmap? {
        require(isValidCatalogIconPath(icon.file)) {
            "Invalid catalog asset path: ${icon.file}"
        }
        val normalizedColor = frameColorArgb and 0xFFFFFFFFL
        val targetSize = maxDimensionPx.coerceAtLeast(1)
        val cacheKey = "${icon.file}:${normalizedColor.toString(16)}:display:$targetSize"
        memoryState.ifEnabled { bitmapCache.get(cacheKey) }?.let { return it }
        val loadEpoch = memoryState.beginLoad() ?: return null
        readDisplayDiskBitmap(cacheKey)?.let { cachedBitmap ->
            return memoryState.storeIfCurrent(loadEpoch) {
                bitmapCache.put(cacheKey, cachedBitmap)
                cachedBitmap
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= targetSize) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null
        val scale = minOf(
            1f,
            targetSize.toFloat() / maxOf(source.width, source.height)
        )
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val displayBitmap = if (
            source.width == targetWidth && source.height == targetHeight
        ) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
                .also { source.recycle() }
        }
        val bitmap = recolorIconFrame(displayBitmap, normalizedColor)
        writeDisplayDiskBitmap(cacheKey, bitmap)

        return memoryState.storeIfCurrent(loadEpoch) {
            bitmapCache.put(cacheKey, bitmap)
            bitmap
        }
    }

    private fun displayDiskFile(cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { byte -> "%02x".format(byte) }
        return File(displayDiskDirectory, "$name.png")
    }

    private fun readDisplayDiskBitmap(cacheKey: String): Bitmap? {
        val file = displayDiskFile(cacheKey)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    private fun writeDisplayDiskBitmap(cacheKey: String, bitmap: Bitmap) {
        if (!displayDiskDirectory.isDirectory &&
            !displayDiskDirectory.mkdirs() &&
            !displayDiskDirectory.isDirectory
        ) return

        val target = displayDiskFile(cacheKey)
        val temporary = File(displayDiskDirectory, "${target.name}.${System.nanoTime()}.tmp")
        try {
            val success = FileOutputStream(temporary).use { output ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                if (compressed) output.fd.sync()
                compressed
            }
            if (success) {
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            } else {
                temporary.delete()
            }
        } catch (_: Exception) {
            temporary.delete()
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
