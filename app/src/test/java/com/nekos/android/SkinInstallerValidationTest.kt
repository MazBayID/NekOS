package com.nekos.android

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
        assertFalse(SkinInstaller.isAllowedSkinPath("scripts/install.sh"))
        assertFalse(SkinInstaller.isAllowedSkinPath("assets/unknown.bin"))
    }

    @Test
    fun validatesSkinIdentifiers() {
        assertTrue(SkinInstaller.isValidSkinId("minimal_dark-2.0"))
        assertFalse(SkinInstaller.isValidSkinId("../default"))
        assertFalse(SkinInstaller.isValidSkinId("UpperCase"))
        assertFalse(SkinInstaller.isValidSkinId(""))
    }
}
