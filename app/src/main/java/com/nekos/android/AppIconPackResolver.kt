package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import org.json.JSONObject
import java.util.Locale

internal data class ResolvedAppIcon(
    val glyph: String,
    val brand: String? = null
)

/**
 * Resolves the bundled app icon pack independently from the skin/tag icon catalog.
 * Exact package mappings are authoritative; label matching is only used for
 * packages that are absent from app_icon_map.json.
 */
internal class AppIconPackResolver(context: Context) {
    private val assets = context.applicationContext.assets
    private val packageEntries: Map<String, ResolvedAppIcon>
    private val aliases: Map<String, String>
    private val slugs: Set<String>
    private val prefixRules: List<Pair<String, String>>
    private val keywordRules: List<Pair<Regex, String>>
    private val defaultGlyph: String

    init {
        val map = JSONObject(assets.open("app_icon_map.json").bufferedReader().use { it.readText() })
        val rules = JSONObject(assets.open("app_icon_rules.json").bufferedReader().use { it.readText() })
        val brandIndex = JSONObject(assets.open("brand_index.json").bufferedReader().use { it.readText() })

        packageEntries = buildMap {
            val apps = map.getJSONArray("apps")
            for (index in 0 until apps.length()) {
                val item = apps.getJSONObject(index)
                val packageName = item.getString("package")
                val glyph = item.getString("glyph")
                val brand = item.optString("brand").takeIf { it.isNotBlank() && it != "null" }
                put(packageName, ResolvedAppIcon(glyph, brand))
            }
        }

        aliases = brandIndex.getJSONObject("aliases").let { json ->
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.getString(key))
                }
            }
        }
        slugs = brandIndex.getJSONArray("slugs").let { json ->
            buildSet {
                for (index in 0 until json.length()) {
                    add(json.getString(index))
                }
            }
        }

        defaultGlyph = rules.optString("default", map.optString("default_glyph", "paw"))
        prefixRules = buildList {
            val rows = rules.getJSONArray("prefix_rules")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONArray(index)
                add(row.getString(0) to row.getString(1))
            }
        }.sortedByDescending { it.first.length }

        keywordRules = buildList {
            val rows = rules.getJSONArray("keyword_rules")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONArray(index)
                runCatching {
                    add(Regex(row.getString(0), RegexOption.IGNORE_CASE) to row.getString(1))
                }
            }
        }
    }

    fun resolve(packageName: String, label: String): ResolvedAppIcon {
        packageEntries[packageName]?.let { return it }

        val normalizedLabel = normalizeBrandLabel(label)
        val labelBrand = aliases[normalizedLabel]
            ?: normalizedLabel.takeIf { it in slugs }

        val glyph = prefixRules.firstOrNull { packageName.startsWith(it.first) }?.second
            ?: keywordRules.firstOrNull { it.first.containsMatchIn("$packageName $label") }?.second
            ?: defaultGlyph

        return ResolvedAppIcon(glyph, labelBrand)
    }

    fun loadBitmap(resolved: ResolvedAppIcon): Bitmap? {
        val source = resolved.brand?.let { loadAssetBitmap("icons/brands/$it.webp") }
            ?: loadAssetBitmap("icons/apps/icon_${resolved.glyph}.webp")
            ?: return null

        // App pack images are white alpha masks. Tint them for legibility on NekOS'
        // transparent launcher surface while retaining the source antialiasing.
        val tinted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(ICON_MASK_TINT, PorterDuff.Mode.SRC_IN)
        }
        Canvas(tinted).drawBitmap(source, 0f, 0f, paint)
        if (source !== tinted && !source.isRecycled) source.recycle()
        return if (tinted.width == 96 && tinted.height == 96) tinted
        else Bitmap.createScaledBitmap(tinted, 96, 96, true).also { tinted.recycle() }
    }

    private fun loadAssetBitmap(path: String): Bitmap? =
        runCatching { assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun normalizeBrandLabel(label: String): String =
        label.lowercase(Locale.ROOT)
            .replace("+", "plus")
            .replace("&", "and")
            .filter { it in 'a'..'z' || it in '0'..'9' }

    private companion object {
        const val ICON_MASK_TINT = 0xFF263238.toInt()
    }
}
