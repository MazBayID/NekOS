package com.nekos.android

internal fun matchesAppDrawerSearch(
    label: String,
    query: String
): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.isBlank() ||
        label.trimStart().startsWith(normalizedQuery, ignoreCase = true)
}
