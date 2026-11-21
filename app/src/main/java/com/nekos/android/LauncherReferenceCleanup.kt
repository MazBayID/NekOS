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

/**
 * Upgrades pre-profile package-only references to the current profile's stable app IDs.
 * Older NekOS versions only queried the current profile, so assigning legacy values to
 * another profile would silently change which app a Favorite or Tag opens.
 */
internal fun migrateLegacyLauncherReferences(
    favorites: List<String?>,
    tags: List<NekTag>,
    apps: List<InstalledApp>,
    currentProfileSerialNumber: Long
): LauncherReferenceCleanupResult {
    val currentProfileIds = apps
        .filter { it.profileSerialNumber == currentProfileSerialNumber }
        .associate { it.packageName to it.id }

    fun migrate(appReference: String): String =
        if ('|' in appReference) appReference else currentProfileIds[appReference] ?: appReference

    return LauncherReferenceCleanupResult(
        favorites = favorites.map { it?.let(::migrate) },
        tags = tags.map { tag -> tag.copy(appPackages = tag.appPackages.map(::migrate)) }
    )
}
