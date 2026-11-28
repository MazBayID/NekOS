package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

internal data class AppIconPackEntry(
    val iconPath: String?,
    val glyph: String
)

internal data class AppIconKeywordRule(
    val pattern: Regex,
    val glyph: String
)

internal data class ResolvedAppIcon(
    val bitmap: Bitmap,
    val isGlyph: Boolean
)

/**
 * Reads the bundled icon_pack-v2 manifest and resolves package names to either
 * a full app artwork file or a catalog glyph. This repository is deliberately
 * independent from SkinAssetResolver and IconCatalogRepository.
 */
internal class AppIconPackRepository(context: Context) {
    private val assets: AssetManager = context.applicationContext.assets
    private val entries by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadEntries() }
    private val defaultGlyph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadDefaultGlyph() }
    private val keywordRules by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadKeywordRules() }
    private val reportedPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun loadIcon(packageName: String, label: String): ResolvedAppIcon? {
        val entry = entries[packageName]
        entry?.iconPath?.let { path ->
            decode(path)?.let {
                reportOnce(packageName, label, "manifest-full-icon", path)
                return ResolvedAppIcon(it, isGlyph = false)
            }
        }

        if (entry != null) {
            decode(glyphAssetPath(entry.glyph))?.let {
                reportOnce(packageName, label, "manifest-glyph", entry.glyph)
                return ResolvedAppIcon(it, isGlyph = true)
            }

        }

        val keywordGlyph = resolveKeywordGlyph(packageName, label)
        if (keywordGlyph != null) {
            decode(glyphAssetPath(keywordGlyph))?.let {
                reportOnce(packageName, label, "keyword-fallback", keywordGlyph)
                return ResolvedAppIcon(it, isGlyph = true)
            }

        }

        val fallback = defaultGlyph
        decode(glyphAssetPath(fallback))?.let {
            reportOnce(packageName, label, "default-fallback", fallback)
            return ResolvedAppIcon(it, isGlyph = true)
        }
        reportOnce(packageName, label, "icon-resolution-failed", fallback)
        return null
    }

    private fun reportOnce(packageName: String, label: String, reason: String, detail: String) {
        if (!reportedPackages.add(packageName)) return
        val summary = diagnosticSummary()
        val level = if (reason == "default-fallback" || reason == "icon-resolution-failed") Log.WARN else Log.INFO
        if (level == Log.WARN) {
            Log.w(TAG, "Icon resolution: package=$packageName label=$label source=$reason detail=$detail; $summary")
        } else {
            Log.i(TAG, "Icon resolution: package=$packageName label=$label source=$reason detail=$detail; $summary")
        }
    }

    internal fun diagnosticSummary(): String =
        "manifestEntries=${entries.size}, keywordRules=${keywordRules.size}, defaultGlyph=$defaultGlyph"

    private fun resolveKeywordGlyph(packageName: String, label: String): String? {
        val haystack = "$packageName $label"
        return keywordRules.firstOrNull { it.pattern.containsMatchIn(haystack) }?.glyph
    }

    private fun loadEntries(): Map<String, AppIconPackEntry> = runCatching {
        val root = assets.open("app_icon_map.json").bufferedReader().use { JSONObject(it.readText()) }
        val schemaVersion = root.optInt("schema_version", 1)
        require(schemaVersion in 1..2) { "Unsupported NekOS icon map schema: $schemaVersion" }
        val apps = root.getJSONArray("apps")
        buildMap {
            for (index in 0 until apps.length()) {
                val item = apps.optJSONObject(index) ?: continue
                val packageName = item.optString("package").takeIf(::isSafePackageName) ?: continue
                val glyph = item.optString("glyph").takeIf(::isSafeGlyphName) ?: continue
                val iconPath = if (item.isNull("icon")) null else {
                    item.optString("icon").takeIf(::isSafeFullIconPath)
                }
                putIfAbsent(packageName, AppIconPackEntry(iconPath, glyph))
            }
        }
    }.onFailure { Log.e(TAG, "Failed to load app_icon_map.json; icon mappings disabled", it) }
        .getOrDefault(emptyMap())

    private fun loadDefaultGlyph(): String = runCatching {
        val root = assets.open("app_icon_map.json").bufferedReader().use { JSONObject(it.readText()) }
        root.optString("default_glyph", "paw").takeIf(::isSafeGlyphName) ?: "paw"
    }.onFailure { Log.e(TAG, "Failed to read default glyph; using paw", it) }
        .getOrDefault("paw")

    private fun loadKeywordRules(): List<AppIconKeywordRule> = runCatching {
        val root = assets.open("tools/keyword_rules.json").bufferedReader().use { JSONObject(it.readText()) }
        val rules = root.getJSONArray("rules")
        buildList {
            for (index in 0 until rules.length()) {
                val item = rules.optJSONArray(index) ?: continue
                if (item.length() < 2) continue
                val source = item.optString(0)
                val glyph = item.optString(1).takeIf(::isSafeGlyphName) ?: continue
                val pattern = runCatching { Regex(source, RegexOption.IGNORE_CASE) }.getOrNull() ?: continue
                add(AppIconKeywordRule(pattern, glyph))
            }
        }
    }.onFailure { Log.e(TAG, "Failed to load keyword_rules.json; keyword fallback disabled", it) }
        .getOrDefault(emptyList())

    private fun decode(path: String): Bitmap? {
        if (!isSafeIconAssetPath(path)) return null
        return runCatching {
            assets.open(path).use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
    }

    private fun glyphAssetPath(glyph: String): String =
        "icons/apps/icon_$glyph.webp"

    private companion object {
        const val TAG = "NekOSAppIconResolver"
    }

    private fun isSafePackageName(value: String): Boolean =
        value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+"))

    private fun isSafeGlyphName(value: String): Boolean =
        value.matches(Regex("[a-z0-9_]+"))

    private fun isSafeFullIconPath(value: String): Boolean =
        value.startsWith("icons/apps_full/") &&
            value.endsWith(".webp") &&
            !value.contains("..") &&
            !value.startsWith("/")

    private fun isSafeIconAssetPath(value: String): Boolean =
        (value.startsWith("icons/apps/") || value.startsWith("icons/apps_full/")) &&
            value.endsWith(".webp") &&
            !value.contains("..") &&
            !value.startsWith("/")
}
