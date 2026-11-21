package com.nekos.android

internal fun matchesAppDrawerFilter(
    packageName: String,
    label: String,
    query: String,
    tagPackages: Set<String>?
): Boolean {
    val normalizedQuery = query.trim()
    return (tagPackages == null || packageName in tagPackages) &&
        (normalizedQuery.isBlank() ||
            label.trimStart().startsWith(normalizedQuery, ignoreCase = true))
}
