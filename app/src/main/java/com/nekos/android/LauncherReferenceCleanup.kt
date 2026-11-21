package com.nekos.android

data class LauncherReferenceCleanupResult(
    val favorites: List<String?>,
    val tags: List<NekTag>
)

/** Removes references to apps that are no longer installed without changing slot/tag structure. */
fun cleanupLauncherReferences(
    favorites: List<String?>,
    tags: List<NekTag>,
    installedAppIds: Set<String>,
    unavailableProfileSerialNumbers: Set<Long> = emptySet()
): LauncherReferenceCleanupResult {
    fun isInstalledOrTemporarilyUnavailable(appId: String): Boolean {
        if (appId in installedAppIds) return true
        val separator = appId.indexOf(LAUNCHER_APP_ID_SEPARATOR)
        if (separator <= 0) return false
        val profileSerial = appId.substring(0, separator).toLongOrNull() ?: return false
        return profileSerial in unavailableProfileSerialNumbers
    }

    val cleanedFavorites = favorites.map { appId ->
        appId?.takeIf(::isInstalledOrTemporarilyUnavailable)
    }
    val cleanedTags = tags.map { tag ->
        tag.copy(appPackages = tag.appPackages.filter(::isInstalledOrTemporarilyUnavailable))
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
        if (LAUNCHER_APP_ID_SEPARATOR in appReference) appReference else currentProfileAppIds[appReference] ?: appReference

    return LauncherReferenceCleanupResult(
        favorites = favorites.map { it?.let(::migrate) },
        tags = tags.map { tag -> tag.copy(appPackages = tag.appPackages.map(::migrate)) }
    )
}
