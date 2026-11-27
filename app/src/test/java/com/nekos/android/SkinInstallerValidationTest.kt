package com.nekos.android

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinInstallerValidationTest {
    @Test
    fun acceptsSafeRelativeSkinPaths() {
        assertTrue(SkinInstaller.isSafeEntryPath("icons/tag/icon_brain.webp"))
        assertTrue(SkinInstaller.isSafeEntryPath("skin.json"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertFalse(SkinInstaller.isSafeEntryPath("../skin.json"))
        assertFalse(SkinInstaller.isSafeEntryPath("icons/../../skin.json"))
        assertFalse(SkinInstaller.isSafeEntryPath("/skin.json"))
        assertFalse(SkinInstaller.isSafeEntryPath("C:/skin.json"))
        assertFalse(SkinInstaller.isSafeEntryPath("icons\\\\tag\\\\icon.webp"))
    }

    @Test
    fun acceptsOnlyKnownSkinAssetLocations() {
        assertTrue(SkinInstaller.isAllowedSkinPath("icons/ui/icon_palette.webp"))
        assertTrue(SkinInstaller.isAllowedSkinPath("components/top_frame/topframe_spritesheet@4x.png"))
        assertTrue(SkinInstaller.isAllowedSkinPath("motifs/motif_sakura@512.png"))
        assertTrue(SkinInstaller.isAllowedSkinPath("LICENSE.txt"))
        assertTrue(SkinInstaller.isAllowedSkinPath("README.md"))
        assertFalse(SkinInstaller.isAllowedSkinPath("docs/README.md"))
        assertFalse(SkinInstaller.isAllowedSkinPath("scripts/install.sh"))
        assertFalse(SkinInstaller.isAllowedSkinPath("assets/unknown.bin"))
    }

    @Test
    fun acceptsArbitrarySkinIdentifiersWithoutUsingThemAsFilePaths() {
        assertTrue(SkinInstaller.isValidSkinId("minimal_dark-2.0"))
        assertTrue(SkinInstaller.isValidSkinId("UpperCase / 日本語"))
        assertTrue(SkinInstaller.isValidSkinId("../custom skin"))
        assertFalse(SkinInstaller.isValidSkinId(""))
        assertFalse(SkinInstaller.isValidSkinId("   "))
        assertFalse(SkinInstaller.isValidSkinId("default"))
        assertTrue(SkinInstaller.skinDirectoryName("../custom skin").matches(Regex("^[0-9a-f-]{36}$")))
    }
    @Test
    fun rejectsArchivesWithTooManyEntriesBeforeEnumeratingThem() {
        val archive = File.createTempFile("nekos-skin-entry-limit", ".zip")
        try {
            ZipOutputStream(FileOutputStream(archive)).use { output ->
                repeat(401) { index ->
                    output.putNextEntry(ZipEntry("README$index.md"))
                    output.closeEntry()
                }
            }

            ZipFile(archive).use { zip ->
                assertFalse(isSkinArchiveEntryCountAllowed(zip, 400))
                assertTrue(isSkinArchiveEntryCountAllowed(zip, 401))
            }
        } finally {
            archive.delete()
        }
    }

}
