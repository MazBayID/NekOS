package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogAssetPathTest {
    @Test
    fun acceptsIconFileInCatalogDirectory() {
        assertTrue(isValidCatalogIconPath("icons/icon_001.png"))
    }

    @Test
    fun acceptsNestedIconPath() {
        assertTrue(isValidCatalogIconPath("icons/outlined/icon.png"))
    }

    @Test
    fun rejectsPathOutsideCatalogDirectory() {
        assertFalse(isValidCatalogIconPath("wallpapers/icon.png"))
    }

    @Test
    fun rejectsParentDirectoryTraversal() {
        assertFalse(isValidCatalogIconPath("icons/../private/icon.png"))
    }

    @Test
    fun rejectsCurrentDirectorySegments() {
        assertFalse(isValidCatalogIconPath("icons/./icon.png"))
    }

    @Test
    fun rejectsEmptyPathSegments() {
        assertFalse(isValidCatalogIconPath("icons//icon.png"))
    }

    @Test
    fun rejectsTrailingDirectorySeparator() {
        assertFalse(isValidCatalogIconPath("icons/"))
    }

    @Test
    fun rejectsBackslashSeparators() {
        assertFalse(isValidCatalogIconPath("icons\\icon.png"))
    }

    @Test
    fun rejectsNullCharacter() {
        assertFalse(isValidCatalogIconPath("icons/icon\u0000.png"))
    }
}
