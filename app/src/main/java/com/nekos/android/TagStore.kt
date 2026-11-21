package com.nekos.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAGS_PREFS = "nekos_tags"
private const val TAGS_KEY = "tag_collection_v1"

data class NekTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconId: Int? = null,
    val colorArgb: Long = 0xFFE7E1D6,
    val appPackages: List<String> = emptyList()
) {
    fun normalized(): NekTag = copy(
        name = name.trim().take(24),
        appPackages = appPackages.distinct()
    )
}

/** Small local persistence boundary for user-created tag collections. */
object TagStore {
    fun load(context: Context): List<NekTag> {
        val raw = context.getSharedPreferences(TAGS_PREFS, Context.MODE_PRIVATE)
            .getString(TAGS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val packages = item.optJSONArray("apps") ?: JSONArray()
                    add(
                        NekTag(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name").trim().take(24),
                            iconId = if (item.isNull("iconId")) null else item.optInt("iconId"),
                            colorArgb = item.optLong("colorArgb", 0xFFE7E1D6),
                            appPackages = buildList {
                                for (j in 0 until packages.length()) {
                                    packages.optString(j).takeIf(String::isNotBlank)?.let(::add)
                                }
                            }.distinct()
                        )
                    )
                }
            }.filter { it.name.isNotBlank() }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, tags: List<NekTag>) {
        val array = JSONArray()
        tags.distinctBy { it.id }.forEach { tag ->
            val normalized = tag.normalized()
            if (normalized.name.isBlank()) return@forEach
            array.put(
                JSONObject()
                    .put("id", normalized.id)
                    .put("name", normalized.name)
                    .put("iconId", normalized.iconId ?: JSONObject.NULL)
                    .put("colorArgb", normalized.colorArgb)
                    .put("apps", JSONArray(normalized.appPackages))
            )
        }
        context.getSharedPreferences(TAGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TAGS_KEY, array.toString())
            .apply()
    }
}
