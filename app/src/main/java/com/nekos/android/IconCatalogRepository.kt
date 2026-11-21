package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
    fun loadBitmap(icon: CatalogIcon): Bitmap? {
        require(isValidCatalogIconPath(icon.file)) {
            "Invalid catalog asset path: ${icon.file}"
        }
        bitmapCache.get(icon.file)?.let { return it }

        val bitmap = appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        bitmapCache.put(icon.file, bitmap)
        return bitmap
    }
}
