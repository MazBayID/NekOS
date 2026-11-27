package com.nekos.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val TAG_ICON_MIGRATION_KEY = "tag_icon_ids_migrated_v1"

internal data class LegacyTagIconEntry(val oldFile: String, val newFile: String?)

internal fun buildLegacyTagIconIdMap(
    entries: List<LegacyTagIconEntry>,
    fallbackFile: String = "icon_star.webp"
): Map<Int, Int> {
    val canonicalByFile = LinkedHashMap<String, Int>()
    for (entry in entries) {
        val newFile = entry.newFile?.takeIf { it.isNotBlank() } ?: continue
        val oldId = legacyId(entry.oldFile) ?: continue
        canonicalByFile.putIfAbsent(newFile, oldId)
    }
    val fallbackId = canonicalByFile[fallbackFile] ?: return emptyMap()
    return buildMap {
        for (entry in entries) {
            val oldId = legacyId(entry.oldFile) ?: continue
            put(oldId, entry.newFile?.let(canonicalByFile::get) ?: fallbackId)
        }
    }
}

internal fun buildLegacyTagIconIdMap(jsonText: String): Map<Int, Int> {
    val rows = JSONObject(jsonText).getJSONArray("tag")
    val entries = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        LegacyTagIconEntry(
            oldFile = row.getString("old_file"),
            newFile = row.optString("new_file").takeIf { it.isNotBlank() && it != "null" }
        )
    }
    return buildLegacyTagIconIdMap(entries)
}

private fun legacyId(filename: String): Int? =
    filename.substringAfter("icon_").substringBefore(".").toIntOrNull()

internal fun legacyTagIconIdMap(context: Context): Map<Int, Int> =
    runCatching {
        val json = context.applicationContext.assets.open("skins/legacy_icon_map.json")
            .bufferedReader().use { it.readText() }
        buildLegacyTagIconIdMap(json)
    }.getOrDefault(emptyMap())

internal fun migrateTagIconIds(
    tags: JSONArray,
    idMap: Map<Int, Int>
): JSONArray {
    for (index in 0 until tags.length()) {
        val item = tags.optJSONObject(index) ?: continue
        if (item.isNull("iconId")) continue
        val oldId = item.optInt("iconId")
        item.put("iconId", idMap[oldId] ?: oldId)
    }
    return tags
}
