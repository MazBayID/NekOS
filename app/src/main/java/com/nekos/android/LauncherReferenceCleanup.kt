package com.nekos.android

data class LauncherReferenceCleanupResult(
    val favorites: List<String?>,
    val tags: List<NekTag>
)

/** Removes references to apps that are no longer installed without changing slot/tag structure. */
fun cleanupLauncherReferences(
    favorites: List<String?>,
    tags: List<NekTag>,
    installedAppIds: Set<String>
): LauncherReferenceCleanupResult {
    val cleanedFavorites = favorites.map { packageName ->
        packageName?.takeIf(installedAppIds::contains)
    }
    val cleanedTags = tags.map { tag ->
        tag.copy(appPackages = tag.appPackages.filter(installedAppIds::contains))
    }
    return LauncherReferenceCleanupResult(cleanedFavorites, cleanedTags)
}

/**
 * Upgrades pre-profile package-only references to the current profile's stable app IDs.
 * Older NekOS versions only queried the current profile, so assigning legacy values to
 * another profile would silently change which app a Favorite or Tag opens.
 */
internal fun migrateLegacyLauncherReferences(
    favorites: List<String?>,
    tags: List<NekTag>,
    currentProfileAppIds: Map<String, String>
): LauncherReferenceCleanupResult {
    fun migrate(appReference: String): String =
        if ('|' in appReference) appReference else currentProfileAppIds[appReference] ?: appReference

    return LauncherReferenceCleanupResult(
        favorites = favorites.map { it?.let(::migrate) },
        tags = tags.map { tag -> tag.copy(appPackages = tag.appPackages.map(::migrate)) }
    )
}
