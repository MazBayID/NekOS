package com.nekos.android

import android.content.Context

private const val FAVORITES_PREFS = "nekos_favorites"
private const val FAVORITES_KEY = "favorite_packages"
private const val FAVORITE_SLOT_COUNT = 15

internal data class FavoriteLoadResult(
    val packages: List<String?>,
    val isValid: Boolean
)

fun loadFavoritePackages(context: Context): List<String?> =
    loadFavoritePackagesResult(context).packages

/**
 * Distinguishes a genuinely missing Favorite setting from data that could not
 * be read, so a later save cannot silently replace an unreadable value.
 */
internal fun loadFavoritePackagesResult(context: Context): FavoriteLoadResult {
    val preferences = runCatching {
        context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    }.getOrNull() ?: return FavoriteLoadResult(emptyFavoriteSlots(), false)

    val hasStoredValue = runCatching {
        preferences.contains(FAVORITES_KEY)
    }.getOrElse {
        return FavoriteLoadResult(emptyFavoriteSlots(), false)
    }
    if (!hasStoredValue) {
        return FavoriteLoadResult(emptyFavoriteSlots(), true)
    }

    val saved = runCatching {
        preferences.getString(FAVORITES_KEY, null)
    }.getOrNull() ?: return FavoriteLoadResult(emptyFavoriteSlots(), false)

    val packages = saved.split("|").map { it.ifBlank { null } }
    return FavoriteLoadResult(normalizeFavoritePackages(packages), true)
}

fun saveFavoritePackages(context: Context, packages: List<String?>): Boolean {
    if (!loadFavoritePackagesResult(context).isValid) return false

    val normalized = normalizeFavoritePackages(packages)
    return runCatching {
        context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(FAVORITES_KEY, normalized.joinToString("|") { it.orEmpty() })
            .apply()
        true
    }.getOrDefault(false)
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
