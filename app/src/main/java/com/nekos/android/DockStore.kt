package com.nekos.android

import android.content.Context

private const val DOCK_PREFERENCES = "nekos_dock"
private const val DOCK_PACKAGES_KEY = "dock_packages"

fun loadDockPackages(context: Context): List<String?> {
    val preferences = context.getSharedPreferences(DOCK_PREFERENCES, Context.MODE_PRIVATE)
    val saved = try {
        preferences.getString(DOCK_PACKAGES_KEY, null)
    } catch (_: ClassCastException) {
        null
    } ?: return normalizeDockPackages(emptyList())

    return normalizeDockPackages(saved.split("|").map { it.ifBlank { null } })
}

fun saveDockPackages(context: Context, packages: List<String?>) {
    context.getSharedPreferences(DOCK_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(DOCK_PACKAGES_KEY, normalizeDockPackages(packages).joinToString("|") { it.orEmpty() })
        .apply()
}
