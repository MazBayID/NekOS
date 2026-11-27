package com.nekos.android

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAGS_KEY = "tag_collection_v1"
internal const val DEFAULT_TAG_ICON_FRAME_COLOR: Long = 0xFF114B56L
private val tagsKey = stringPreferencesKey(TAGS_KEY)
private val tagIconMigrationKey = booleanPreferencesKey(TAG_ICON_MIGRATION_KEY)

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

/** Asynchronous local persistence boundary for user-created tag collections. */
object TagStore {
    suspend fun load(context: Context): List<NekTag> = loadResult(context).tags

    /**
     * Keeps malformed, wrong-typed, or unreadable values non-writable.
     * Legacy icon IDs are migrated transactionally with the migration marker.
     */
    internal suspend fun loadResult(context: Context): TagLoadResult {
        return try {
            val store = LauncherCollectionsDataStore.get(context).tags
            val preferences = store.data.first()
            val storedTags = preferences.asMap().entries.firstOrNull { it.key.name == TAGS_KEY }
            val raw = when {
                storedTags == null -> null
                storedTags.value is String -> storedTags.value as String
                else -> return TagLoadResult(emptyList(), false)
            }
            val migrationEntry = preferences.asMap().entries
                .firstOrNull { it.key.name == TAG_ICON_MIGRATION_KEY }
            val migrationComplete = when {
                migrationEntry == null -> false
                migrationEntry.value is Boolean -> migrationEntry.value as Boolean
                else -> return TagLoadResult(emptyList(), false)
            }
            if (raw == null) return TagLoadResult(emptyList(), true)

            val shouldMigrateIcons = !migrationComplete
            val iconIdMap = if (shouldMigrateIcons) legacyTagIconIdMap(context) else emptyMap()
            val array = JSONArray(raw)
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val rawPackages = item.opt("apps")
                    val packages = when (rawPackages) {
                        null, JSONObject.NULL -> JSONArray()
                        is JSONArray -> rawPackages
                        else -> error("Invalid Tag apps field")
                    }
                    for (j in 0 until packages.length()) {
                        require(packages.opt(j) is String) {
                            "Invalid Tag app reference"
                        }
                    }
                    val rawId = item.opt("id")
                    val rawName = item.opt("name")
                    require(rawId == null || rawId is String) { "Invalid Tag id field" }
                    require(rawName is String) { "Invalid Tag name field" }
                    add(
                        NekTag(
                            id = (rawId as? String).orEmpty().ifBlank { UUID.randomUUID().toString() },
                            name = (rawName as String).trim().take(24),
                            iconId = if (item.isNull("iconId")) null else {
                                val storedId = item.optInt("iconId")
                                iconIdMap[storedId] ?: storedId
                            },
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

            if (shouldMigrateIcons && iconIdMap.isNotEmpty()) {
                val migratedJson = migrateTagIconIds(array, iconIdMap).toString()
                store.edit { current ->
                    val currentRaw = current.asMap().entries
                        .firstOrNull { it.key.name == TAGS_KEY }?.value
                    val currentFlag = current.asMap().entries
                        .firstOrNull { it.key.name == TAG_ICON_MIGRATION_KEY }?.value
                    check(currentRaw == raw && (currentFlag == null || currentFlag == false)) {
                        "Tag data changed during icon migration"
                    }
                    current[tagsKey] = migratedJson
                    current[tagIconMigrationKey] = true
                }
            }
            TagLoadResult(parsed, true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TagLoadResult(emptyList(), false)
        }
    }

    /**
     * Writes tags and the icon-migration marker in one durable DataStore transaction.
     * Refuses to replace malformed or wrong-typed stored values.
     */
    suspend fun save(context: Context, tags: List<NekTag>): Boolean {
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
        val store = LauncherCollectionsDataStore.get(context).tags
        if (!loadResult(context).isValid) return false
        val validatedPreferences = runCatching { store.data.first() }.getOrNull() ?: return false
        val validatedRaw = validatedPreferences.asMap().entries
            .firstOrNull { it.key.name == TAGS_KEY }?.value
        val validatedMigrationMarker = validatedPreferences.asMap().entries
            .firstOrNull { it.key.name == TAG_ICON_MIGRATION_KEY }?.value

        return try {
            store.edit { preferences ->
                val storedTags = preferences.asMap().entries
                    .firstOrNull { it.key.name == TAGS_KEY }
                val migrationEntry = preferences.asMap().entries
                    .firstOrNull { it.key.name == TAG_ICON_MIGRATION_KEY }
                check(storedTags?.value == validatedRaw) {
                    "Tag data changed before the save transaction"
                }
                check(migrationEntry?.value == validatedMigrationMarker) {
                    "Tag migration state changed before the save transaction"
                }
                check(storedTags == null || storedTags.value is String) {
                    "Stored Tags value has an unexpected type"
                }
                check(migrationEntry == null || migrationEntry.value is Boolean) {
                    "Tag icon migration marker has an unexpected type"
                }
                preferences[tagsKey] = array.toString()
                preferences[tagIconMigrationKey] = true
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
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
