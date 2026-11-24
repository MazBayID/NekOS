package com.nekos.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAGS_PREFS = "nekos_tags"
private const val TAGS_KEY = "tag_collection_v1"
internal const val DEFAULT_TAG_ICON_FRAME_COLOR: Long = 0xFF114B56L

data class NekTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconId: Int? = null,
    val colorArgb: Long = 0xFFE7E1D6,
    val appPackages: List<String> = emptyList(),
    val iconFrameColorArgb: Long = DEFAULT_TAG_ICON_FRAME_COLOR
) {
    fun normalized(): NekTag = copy(
        name = name.trim().take(24),
        appPackages = appPackages.filter(String::isNotBlank).distinct()
    )
}

internal data class TagLoadResult(
    val tags: List<NekTag>,
    val isValid: Boolean
)

/** Small local persistence boundary for user-created tag collections. */
object TagStore {
    fun load(context: Context): List<NekTag> = loadResult(context).tags

    /**
     * Keeps malformed or unreadable persisted data distinguishable from a genuinely
     * empty collection, so callers can avoid overwriting data that could not be read.
     */
    internal fun loadResult(context: Context): TagLoadResult {
        val preferences = runCatching {
            context.getSharedPreferences(TAGS_PREFS, Context.MODE_PRIVATE)
        }.getOrNull() ?: return TagLoadResult(emptyList(), false)

        if (!preferences.contains(TAGS_KEY)) {
            return TagLoadResult(emptyList(), true)
        }

        val raw = try {
            preferences.getString(TAGS_KEY, null)
        } catch (_: ClassCastException) {
            return TagLoadResult(emptyList(), false)
        } ?: return TagLoadResult(emptyList(), false)

        return runCatching {
            val array = JSONArray(raw)
            val parsed = buildList {
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
                            }.distinct(),
                            iconFrameColorArgb = item.optLong(
                                "iconFrameColorArgb",
                                DEFAULT_TAG_ICON_FRAME_COLOR
                            )
                        )
                    )
                }
            }.filter { it.name.isNotBlank() }.distinctBy { it.id }
            TagLoadResult(parsed, true)
        }.getOrElse {
            TagLoadResult(emptyList(), false)
        }
    }

    /**
     * Refuses to replace persisted content when the current value cannot be read.
     * Returns false so the UI can keep the in-memory state unchanged and notify the user.
     */
    fun save(context: Context, tags: List<NekTag>): Boolean {
        if (!loadResult(context).isValid) return false

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
                    .put("iconFrameColorArgb", normalized.iconFrameColorArgb)
                    .put("apps", JSONArray(normalized.appPackages))
            )
        }
        return runCatching {
            context.getSharedPreferences(TAGS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(TAGS_KEY, array.toString())
                .apply()
            true
        }.getOrDefault(false)
    }
}

/**
 * Moves a tag by one position while preserving every other tag and its data.
 * Returns the original list when the tag is missing or already at the boundary.
 */
internal fun moveTag(tags: List<NekTag>, tagId: String, direction: Int): List<NekTag> {
    if (direction != -1 && direction != 1) return tags
    val fromIndex = tags.indexOfFirst { it.id == tagId }
    val toIndex = fromIndex + direction
    if (fromIndex < 0 || toIndex !in tags.indices) return tags

    return tags.toMutableList().also { reordered ->
        val moved = reordered[fromIndex]
        reordered[fromIndex] = reordered[toIndex]
        reordered[toIndex] = moved
    }
}


/** Moves a tag directly to a target index while preserving all other tag data. */
internal fun moveTagToIndex(tags: List<NekTag>, tagId: String, targetIndex: Int): List<NekTag> {
    val sourceIndex = tags.indexOfFirst { it.id == tagId }
    if (sourceIndex < 0 || targetIndex !in tags.indices || sourceIndex == targetIndex) return tags
    return tags.toMutableList().also { reordered ->
        val moved = reordered.removeAt(sourceIndex)
        reordered.add(targetIndex, moved)
    }
}


/** Moves a tag to the adjacent page while preserving the order of all other tags. */
internal fun moveTagToPage(
    tags: List<NekTag>,
    tagId: String,
    targetPage: Int,
    pageSize: Int = 5
): List<NekTag> {
    if (pageSize <= 0) return tags
    val sourceIndex = tags.indexOfFirst { it.id == tagId }
    if (sourceIndex < 0) return tags
    val targetPageIndex = targetPage.coerceIn(0, (tags.lastIndex / pageSize).coerceAtLeast(0))
    if (sourceIndex / pageSize == targetPageIndex) return tags
    return tags.toMutableList().also { reordered ->
        val moved = reordered.removeAt(sourceIndex)
        val insertionIndex = if (targetPageIndex > sourceIndex / pageSize) {
            ((targetPageIndex + 1) * pageSize - 1).coerceAtMost(reordered.size)
        } else {
            (targetPageIndex * pageSize).coerceAtMost(reordered.size)
        }
        reordered.add(insertionIndex, moved)
    }
}


/** Converts an item index within a paged tag list to its index in the full list. */
internal fun tagPageItemIndex(pageIndex: Int, itemIndex: Int, pageSize: Int = 5): Int {
    require(pageIndex >= 0) { "pageIndex must not be negative" }
    require(itemIndex >= 0) { "itemIndex must not be negative" }
    require(pageSize > 0) { "pageSize must be positive" }
    return pageIndex * pageSize + itemIndex
}
