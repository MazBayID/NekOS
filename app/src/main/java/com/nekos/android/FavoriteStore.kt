package com.nekos.android

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val FAVORITES_KEY = "favorite_packages"
private const val FAVORITE_SLOT_COUNT = 15
private val favoritesKey = stringPreferencesKey(FAVORITES_KEY)

internal data class FavoriteLoadResult(
    val packages: List<String?>,
    val isValid: Boolean
)

suspend fun loadFavoritePackages(context: Context): List<String?> =
    loadFavoritePackagesResult(context).packages

/**
 * Reads the migrated Preferences value without coercing a wrong-typed legacy value.
 * Missing storage is a valid empty collection; unreadable data remains non-writable.
 */
internal suspend fun loadFavoritePackagesResult(context: Context): FavoriteLoadResult {
    return runCatching {
        val preferences = LauncherCollectionsDataStore.get(context).favorites.data.first()
        val stored = preferences.asMap().entries.firstOrNull { it.key.name == FAVORITES_KEY }
            ?: return FavoriteLoadResult(emptyFavoriteSlots(), true)
        val saved = stored.value as? String
            ?: return FavoriteLoadResult(emptyFavoriteSlots(), false)
        val packages = saved.split("|").map { it.ifBlank { null } }
        FavoriteLoadResult(normalizeFavoritePackages(packages), true)
    }.getOrElse {
        if (it is CancellationException) throw it
        FavoriteLoadResult(emptyFavoriteSlots(), false)
    }
}

/**
 * DataStore serializes edits and completes only after the new Preferences value is persisted.
 * The current value is checked inside the transaction to prevent overwriting unreadable data.
 */
suspend fun saveFavoritePackages(context: Context, packages: List<String?>): Boolean {
    val normalized = normalizeFavoritePackages(packages)
    return try {
        LauncherCollectionsDataStore.get(context).favorites.edit { preferences ->
            val existing = preferences.asMap().entries.firstOrNull { it.key.name == FAVORITES_KEY }
            check(existing == null || existing.value is String) {
                "Stored Favorites value has an unexpected type"
            }
            preferences[favoritesKey] = normalized.joinToString("|") { it.orEmpty() }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

internal fun normalizeFavoritePackages(packages: List<String?>): List<String?> {
    val seen = mutableSetOf<String>()
    val normalized = packages.take(FAVORITE_SLOT_COUNT).map { packageName ->
        packageName?.takeIf { it.isNotBlank() && seen.add(it) }
    }

    return normalized + List(FAVORITE_SLOT_COUNT - normalized.size) { null }
}

private fun emptyFavoriteSlots(): List<String?> =
    List(FAVORITE_SLOT_COUNT) { null }
