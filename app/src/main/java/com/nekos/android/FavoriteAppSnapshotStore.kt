package com.nekos.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal persisted presentation data for Home favorites.
 *
 * LauncherActivityInfo is process-bound, so only stable display metadata and the
 * icon-cache key are stored. Live InstalledApp objects still come from LauncherApps.
 */
internal data class FavoriteAppSnapshot(
    val appId: String,
    val label: String,
    val iconCacheKey: String
)

/**
 * Snapshot metadata may only follow the persisted Favorite list. If Favorite
 * references changed but their save failed, keep the previous snapshots intact.
 */
internal fun shouldPersistFavoriteSnapshots(
    favoritesChanged: Boolean,
    favoriteSaveSucceeded: Boolean
): Boolean = !favoritesChanged || favoriteSaveSucceeded

internal object FavoriteAppSnapshotStore {
    private const val PREFS_NAME = "nekos_favorite_snapshots"
    private const val KEY_SNAPSHOTS = "favorite_app_snapshots_v1"

    fun load(context: Context): Map<String, FavoriteAppSnapshot> {
        val raw = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOTS, null)
        }.getOrNull() ?: return emptyMap()

        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val appId = item.optString("appId")
                    val label = item.optString("label")
                    val iconCacheKey = item.optString("iconCacheKey")
                    if (appId.isNotBlank() && label.isNotBlank() && iconCacheKey.isNotBlank()) {
                        put(appId, FavoriteAppSnapshot(appId, label, iconCacheKey))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(context: Context, snapshots: Collection<FavoriteAppSnapshot>) {
        val array = JSONArray()
        snapshots.distinctBy { it.appId }.forEach { snapshot ->
            if (snapshot.appId.isBlank() || snapshot.label.isBlank() || snapshot.iconCacheKey.isBlank()) {
                return@forEach
            }
            array.put(
                JSONObject()
                    .put("appId", snapshot.appId)
                    .put("label", snapshot.label)
                    .put("iconCacheKey", snapshot.iconCacheKey)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOTS, array.toString())
            .apply()
    }
}
