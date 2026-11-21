package com.nekos.android

import android.content.Context

private const val FAVORITES_PREFS = "nekos_favorites"
private const val FAVORITES_KEY = "favorite_packages"
private const val FAVORITE_SLOT_COUNT = 15

fun loadFavoritePackages(context: Context): List<String?> {
    val preferences = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
    val saved = try {
        preferences.getString(FAVORITES_KEY, null)
    } catch (_: ClassCastException) {
        null
    } ?: return emptyFavoriteSlots()

    val packages = saved.split("|").map { it.ifBlank { null } }
    return normalizeFavoritePackages(packages)
}

fun saveFavoritePackages(context: Context, packages: List<String?>) {
    val normalized = normalizeFavoritePackages(packages)
    context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(FAVORITES_KEY, normalized.joinToString("|") { it.orEmpty() })
        .apply()
}

internal fun normalizeFavoritePackages(packages: List<String?>): List<String?> {
    val seen = mutableSetOf<String>()
    val normalized = mutableListOf<String?>()

    for (packageName in packages) {
        if (normalized.size == FAVORITE_SLOT_COUNT) break

        if (packageName.isNullOrBlank()) {
            normalized.add(null)
        } else if (seen.add(packageName)) {
            normalized.add(packageName)
        }
    }

    return normalized + List(FAVORITE_SLOT_COUNT - normalized.size) { null }
}

private fun emptyFavoriteSlots(): List<String?> =
    List(FAVORITE_SLOT_COUNT) { null }
