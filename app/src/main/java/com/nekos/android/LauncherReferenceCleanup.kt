package com.nekos.android

data class LauncherReferenceCleanupResult(
    val favorites: List<String?>,
    val tags: List<NekTag>
)

/** Removes references to apps that are no longer installed without changing slot/tag structure. */
fun cleanupLauncherReferences(
    favorites: List<String?>,
    tags: List<NekTag>,
    installedPackages: Set<String>
): LauncherReferenceCleanupResult {
    val cleanedFavorites = favorites.map { packageName ->
        packageName?.takeIf(installedPackages::contains)
    }
    val cleanedTags = tags.map { tag ->
        tag.copy(appPackages = tag.appPackages.filter(installedPackages::contains))
    }
    return LauncherReferenceCleanupResult(cleanedFavorites, cleanedTags)
}