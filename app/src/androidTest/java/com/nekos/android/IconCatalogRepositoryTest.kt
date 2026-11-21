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
}
