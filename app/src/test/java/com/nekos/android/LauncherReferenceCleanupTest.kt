package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherReferenceCleanupTest {
    @Test
    fun removesUninstalledFavoritePackagesWithoutChangingSlotCount() {
        val favorites = listOf("keep", "remove", null, "keep2")
        val result = cleanupLauncherReferences(favorites, emptyList(), setOf("keep", "keep2"))
        assertEquals(listOf("keep", null, null, "keep2"), result.favorites)
        assertEquals(favorites.size, result.favorites.size)
    }

    @Test
    fun removesUninstalledAppsFromTagsAndPreservesTagMetadata() {
        val tag = NekTag(
            id = "tag-id",
            name = "Tools",
            iconId = 7,
            colorArgb = 0xFF112233,
            appPackages = listOf("keep", "remove", "keep")
        )
        val result = cleanupLauncherReferences(emptyList(), listOf(tag), setOf("keep"))
        assertEquals(
            tag.copy(appPackages = listOf("keep", "keep")),
            result.tags.single()
        )
    }

    @Test
    fun retainsTagsThatBecomeEmpty() {
        val tag = NekTag(id = "empty", name = "Empty", appPackages = listOf("gone"))
        val result = cleanupLauncherReferences(emptyList(), listOf(tag), emptySet())
        assertEquals(listOf(tag.copy(appPackages = emptyList())), result.tags)
    }

    @Test
    fun preservesReferencesForTemporarilyUnavailableProfiles() {
        val favorites = listOf("42::com.example.work", "10::com.example.removed")
        val tags = listOf(
            NekTag(
                id = "work",
                name = "Work",
                appPackages = listOf("42::com.example.work", "10::com.example.removed")
            )
        )

        val result = cleanupLauncherReferences(
            favorites = favorites,
            tags = tags,
            installedAppIds = emptySet(),
            unavailableProfileSerialNumbers = setOf(42L)
        )

        assertEquals(listOf("42::com.example.work", null), result.favorites)
        assertEquals(listOf("42::com.example.work"), result.tags.single().appPackages)
    }

    @Test
    fun refreshesTagDialogReferencesUsingStableIdAndLatestData() {
        val oldTag = NekTag(
            id = "tools",
            name = "Tools",
            appPackages = listOf("installed", "uninstalled")
        )
        val refreshedTag = oldTag.copy(appPackages = listOf("installed"))

        assertEquals(refreshedTag, refreshTagReference(oldTag, listOf(refreshedTag)))
        assertEquals(null, refreshTagReference(oldTag, emptyList()))
        assertEquals(null, refreshTagReference(null, listOf(refreshedTag)))
    }

    @Test
    fun removesMissingWorkProfileAppsAfterProfileBecomesAvailableAgain() {
        val appId = "42::com.example.work"
        val tag = NekTag(
            id = "work-tools",
            name = "Work tools",
            appPackages = listOf(appId, "10::com.example.personal")
        )

        val result = cleanupLauncherReferences(
            favorites = listOf(appId),
            tags = listOf(tag),
            installedAppIds = setOf("10::com.example.personal"),
            unavailableProfileSerialNumbers = emptySet()
        )

        assertEquals(listOf(null), result.favorites)
        assertEquals(
            tag.copy(appPackages = listOf("10::com.example.personal")),
            result.tags.single()
        )
    }

    @Test
    fun migratesLegacyFavoritesAndTagsToCurrentProfileIds() {
        val favorites = listOf("com.example.alpha", null, "42::com.example.work")
        val tags = listOf(
            NekTag(
                id = "tools",
                name = "Tools",
                appPackages = listOf("com.example.alpha", "com.example.beta")
            )
        )

        val migrated = migrateLegacyLauncherReferences(
            favorites = favorites,
            tags = tags,
            currentProfileAppIds = mapOf(
                "com.example.alpha" to "10::com.example.alpha",
                "com.example.beta" to "10::com.example.beta"
            )
        )

        assertEquals(
            listOf("10::com.example.alpha", null, "42::com.example.work"),
            migrated.favorites
        )
        assertEquals(
            listOf("10::com.example.alpha", "10::com.example.beta"),
            migrated.tags.single().appPackages
        )
    }

    @Test
    fun returnsUnchangedDataWhenAllAppsAreInstalled() {
        val favorites = listOf("a", null, "b")
        val tags = listOf(NekTag(id = "t", name = "T", appPackages = listOf("a", "b")))
        val result = cleanupLauncherReferences(favorites, tags, setOf("a", "b"))
        assertEquals(favorites, result.favorites)
        assertEquals(tags, result.tags)
    }
}
