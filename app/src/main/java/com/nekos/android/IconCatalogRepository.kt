package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * Reads the tag-icon catalog bundled under assets/ikon.
 * The JSON index is the source of truth; images are decoded only when requested.
 */
class IconCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private var cachedIcons: List<CatalogIcon>? = null

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
                require(file.startsWith("icons/") && !file.contains("..")) {
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

    fun loadBitmap(icon: CatalogIcon): Bitmap? {
        require(icon.file.startsWith("icons/") && !icon.file.contains("..")) {
            "Invalid catalog asset path: ${icon.file}"
        }
        return appContext.assets.open("ikon/${icon.file}").use {
            BitmapFactory.decodeStream(it)
        }
    }
}
