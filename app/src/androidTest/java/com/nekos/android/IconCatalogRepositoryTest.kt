package com.nekos.android

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogRepositoryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledCatalogLoadsEntriesWithUniqueIdsAndSafeAssetPaths() {
        val icons = IconCatalogRepository(context).loadCatalog()

        assertTrue(icons.isNotEmpty())
        assertEquals(icons.size, icons.map { it.id }.distinct().size)
        assertTrue(icons.all { it.file.startsWith("icons/") && !it.file.contains("..") })
        assertTrue(icons.all { it.name.isNotBlank() })
    }

    @Test
    fun catalogResultsAreCached() {
        val repository = IconCatalogRepository(context)

        val first = repository.loadCatalog()
        val second = repository.loadCatalog()

        assertSame(first, second)
    }

    @Test
    fun bundledIconCanBeDecodedIntoBitmap() {
        val repository = IconCatalogRepository(context)
        val firstIcon = repository.loadCatalog().first()

        val bitmap = repository.loadBitmap(firstIcon)
            ?: error("Expected the first bundled icon to decode")

        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun repeatedBitmapLoadsReuseCachedBitmap() {
        val repository = IconCatalogRepository(context)
        val icon = repository.loadCatalog().first()

        val first = repository.loadBitmap(icon)
        val second = repository.loadBitmap(icon)

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun tagIconsAreReleasedOnTrimAndReloadedOnResume() {
        val repository = IconCatalogRepository(context)
        val icon = repository.loadCatalog().first()

        assertNotNull(repository.loadBitmap(icon))
        val generationBeforeTrim = repository.generation

        repository.trimMemory()
        assertTrue(repository.generation > generationBeforeTrim)
        org.junit.Assert.assertNull(repository.loadBitmap(icon))

        repository.resumeAfterBackground()
        assertNotNull(repository.loadBitmap(icon))
    }

    @Test
    fun skinCacheInvalidationAllowsTagIconsToReloadImmediately() {
        val repository = IconCatalogRepository(context)
        val icon = repository.loadCatalog().first()
        assertNotNull(repository.loadBitmap(icon))

        repository.invalidateSkinCache()

        assertNotNull(repository.loadBitmap(icon))
    }

    @Test
    fun bundledCatalogUsesStableLegacyCanonicalIds() {
        val json = context.assets.open("skins/default/icons/tag/icons_index.json")
            .bufferedReader().use { it.readText() }
        val items = org.json.JSONObject(json).getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val legacyId = item.getString("legacy_file")
                .removePrefix("icon_").removeSuffix(".webp").toInt()
            assertEquals(legacyId, item.getInt("id"))
        }
    }

    @Test
    fun catalogAlwaysContainsCanonicalStarFallback() {
        val repository = IconCatalogRepository(context)
        assertTrue(repository.loadCatalog().any { it.id == 90 })
    }

    @Test
    fun cachedTagIconDimensionsMustFitTheRequestedThumbnailSize() {
        assertTrue(isValidCachedTagIconDimensions(128, 128))
        assertTrue(isValidCachedTagIconDimensions(96, 64, 128))
        org.junit.Assert.assertFalse(isValidCachedTagIconDimensions(129, 128))
        org.junit.Assert.assertFalse(isValidCachedTagIconDimensions(128, 129))
        org.junit.Assert.assertFalse(isValidCachedTagIconDimensions(0, 128))
        org.junit.Assert.assertFalse(isValidCachedTagIconDimensions(128, 128, 0))
    }

    @Test
    fun bitmapLoaderRejectsPathTraversal() {
        val repository = IconCatalogRepository(context)
        val unsafeIcon = CatalogIcon(
            id = -1,
            file = "icons/../outside.png",
            name = "unsafe",
            visual = "",
            description = "",
            row = 0,
            column = 0
        )

        val error = runCatching { repository.loadBitmap(unsafeIcon) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun bitmapLoaderRejectsPathsOutsideIconDirectory() {
        val repository = IconCatalogRepository(context)
        val unsafeIcon = CatalogIcon(
            id = -1,
            file = "other/icon.png",
            name = "unsafe",
            visual = "",
            description = "",
            row = 0,
            column = 0
        )

        val error = runCatching { repository.loadBitmap(unsafeIcon) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun skinCatalogCannotRemapCanonicalIdsToDifferentGlyphMeanings() {
        val bundled = CatalogIcon(11, "icons/tag/icon_mail.webp", "mail", "", "", 1, 1)
        val validOverride = CatalogIcon(11, "icons/tag/icon_envelope_alt.webp", "mail", "", "", 1, 1)
        val wrongMeaning = CatalogIcon(11, "icons/tag/icon_camera.webp", "camera", "", "", 1, 1)
        val unknownId = CatalogIcon(999, "icons/tag/icon_custom.webp", "custom", "", "", 1, 1)

        assertTrue(isCanonicalSkinCatalog(listOf(validOverride), listOf(bundled)))
        org.junit.Assert.assertFalse(isCanonicalSkinCatalog(listOf(wrongMeaning), listOf(bundled)))
        org.junit.Assert.assertFalse(isCanonicalSkinCatalog(listOf(unknownId), listOf(bundled)))
    }
}
