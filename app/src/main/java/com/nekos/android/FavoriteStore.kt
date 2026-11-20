package com.nekos.android

import android.content.Context

private const val FAVORITES_PREFS = "nekos_favorites"
private const val FAVORITES_KEY = "favorite_packages"
private const val FAVORITE_SLOT_COUNT = 15

fun loadFavoritePackages(context: Context): List<String?> {
    val saved = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        .getString(FAVORITES_KEY, null)
        ?: return List(FAVORITE_SLOT_COUNT) { null }

    val packages = saved.split("|").map { it.ifBlank { null } }.take(FAVORITE_SLOT_COUNT)
    return packages + List(FAVORITE_SLOT_COUNT - packages.size) { null }
}

fun saveFavoritePackages(context: Context, packages: List<String?>) {
    val normalized = packages.take(FAVORITE_SLOT_COUNT).map { it.orEmpty() }
    val complete = normalized + List(FAVORITE_SLOT_COUNT - normalized.size) { "" }
    context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(FAVORITES_KEY, complete.joinToString("|"))
        .apply()
}
