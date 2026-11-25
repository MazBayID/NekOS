package com.nekos.android

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val TAG_ICON_MIGRATION_KEY = "tag_icon_ids_migrated_v1"

internal fun buildLegacyTagIconIdMap(jsonText: String): Map<Int, Int> {
    val rows = JSONObject(jsonText).getJSONArray("tag")
    val canonicalByFile = LinkedHashMap<String, Int>()
    for (index in 0 until rows.length()) {
        val row = rows.getJSONObject(index)
        val newFile = row.optString("new_file").takeIf { it.isNotBlank() && it != "null" } ?: continue
        val oldId = legacyId(row.getString("old_file")) ?: continue
        canonicalByFile.putIfAbsent(newFile, oldId)
    }
    val fallbackId = canonicalByFile["icon_star.webp"] ?: return emptyMap()
    return buildMap {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val oldId = legacyId(row.getString("old_file")) ?: continue
            val newFile = row.optString("new_file").takeIf { it.isNotBlank() && it != "null" }
            put(oldId, newFile?.let(canonicalByFile::get) ?: fallbackId)
        }
    }
}

private fun legacyId(filename: String): Int? =
    filename.substringAfter("icon_").substringBefore(".").toIntOrNull()

internal fun migrateLegacyTagIconIds(
    context: Context,
    preferences: SharedPreferences,
    tags: JSONArray
) {
    if (preferences.getBoolean(TAG_ICON_MIGRATION_KEY, false)) return
    val idMap = runCatching {
        val json = context.applicationContext.assets.open("skins/legacy_icon_map.json")
            .bufferedReader().use { it.readText() }
        buildLegacyTagIconIdMap(json)
    }.getOrDefault(emptyMap())
    if (idMap.isEmpty()) return

    for (index in 0 until tags.length()) {
        val item = tags.optJSONObject(index) ?: continue
        if (item.isNull("iconId")) continue
        val oldId = item.optInt("iconId")
        item.put("iconId", idMap[oldId] ?: oldId)
    }
    preferences.edit()
        .putString("tag_collection_v1", tags.toString())
        .putBoolean(TAG_ICON_MIGRATION_KEY, true)
        .apply()
}
