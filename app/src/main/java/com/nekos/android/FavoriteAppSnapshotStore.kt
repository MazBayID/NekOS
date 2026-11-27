package com.nekos.android

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
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

internal data class FavoriteAppSnapshotLoadResult(
    val snapshots: Map<String, FavoriteAppSnapshot>,
    val isValid: Boolean
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
    private const val KEY_SNAPSHOTS = "favorite_app_snapshots_v1"
    private val snapshotsKey = stringPreferencesKey(KEY_SNAPSHOTS)

    suspend fun load(context: Context): Map<String, FavoriteAppSnapshot> =
        loadResult(context).snapshots

    internal suspend fun loadResult(context: Context): FavoriteAppSnapshotLoadResult {
        return try {
            val preferences = LauncherCollectionsDataStore.get(context)
                .favoriteSnapshots.data.first()
            val stored = preferences.asMap().entries.firstOrNull { it.key.name == KEY_SNAPSHOTS }
                ?: return FavoriteAppSnapshotLoadResult(emptyMap(), true)
            val raw = stored.value as? String
                ?: return FavoriteAppSnapshotLoadResult(emptyMap(), false)
            val array = JSONArray(raw)
            val snapshots = buildMap {
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
            FavoriteAppSnapshotLoadResult(snapshots, true)
        } catch (_: Exception) {
            FavoriteAppSnapshotLoadResult(emptyMap(), false)
        }
    }

    suspend fun save(
        context: Context,
        snapshots: Collection<FavoriteAppSnapshot>
    ): Boolean {
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
        return runCatching {
            LauncherCollectionsDataStore.get(context).favoriteSnapshots.edit { preferences ->
                val stored = preferences.asMap().entries
                    .firstOrNull { it.key.name == KEY_SNAPSHOTS }
                check(stored == null || stored.value is String) {
                    "Stored Favorite snapshots value has an unexpected type"
                }
                val existing = stored?.value as? String
                if (existing != null) JSONArray(existing)
                preferences[snapshotsKey] = array.toString()
            }
            true
        }.getOrDefault(false)
    }
}
