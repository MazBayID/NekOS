package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogAssetPathTest {
    @Test fun acceptsSkinTagIconPath() {
        assertTrue(isValidCatalogIconPath("icons/tag/icon_mail.webp"))
    }
    @Test fun rejectsPathOutsideSkinTagDirectory() {
        assertFalse(isValidCatalogIconPath("icons/ui/icon_close.webp"))
        assertFalse(isValidCatalogIconPath("wallpapers/icon.png"))
    }
    @Test fun rejectsParentDirectoryTraversal() {
        assertFalse(isValidCatalogIconPath("icons/tag/../private.webp"))
    }
    @Test fun rejectsCurrentDirectorySegments() {
        assertFalse(isValidCatalogIconPath("icons/tag/./icon.webp"))
    }
    @Test fun rejectsEmptySegmentsAndTrailingSeparator() {
        assertFalse(isValidCatalogIconPath("icons//icon.webp"))
        assertFalse(isValidCatalogIconPath("icons/tag/"))
    }
    @Test fun rejectsBackslashAndNullCharacters() {
        assertFalse(isValidCatalogIconPath("icons\\tag\\icon.webp"))
        assertFalse(isValidCatalogIconPath("icons/tag/icon\u0000.webp"))
    }
    @Test fun acceptsCompleteCatalogEntry() {
        assertTrue(isValidCatalogIconEntry(11, "icons/tag/icon_mail.webp", "mail", 1, 1))
    }
    @Test fun rejectsInvalidCatalogEntry() {
        assertFalse(isValidCatalogIconEntry(0, "icons/tag/icon_mail.webp", "mail", 1, 1))
        assertFalse(isValidCatalogIconEntry(11, "icons/tag/icon_mail.webp", " ", 1, 1))
        assertFalse(isValidCatalogIconEntry(11, "icons/tag/icon_mail.webp", "mail", 0, 1))
    }
}
