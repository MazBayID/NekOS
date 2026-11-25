package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkinVersionTest {
    @Test
    fun detectsOnlyNewerOfficialVersions() {
        assertTrue(OfficialSkinDownloader.isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(OfficialSkinDownloader.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(OfficialSkinDownloader.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(OfficialSkinDownloader.isNewerVersion("1.0.0", "1.1.0"))
    }

    @Test
    fun comparesPrereleaseVersionsCorrectly() {
        assertTrue(OfficialSkinDownloader.isNewerVersion("1.0.0", "1.0.0-rc.1"))
        assertTrue(OfficialSkinDownloader.isNewerVersion("1.0.0-rc.2", "1.0.0-rc.1"))
        assertFalse(OfficialSkinDownloader.isNewerVersion("1.0.0-rc.1", "1.0.0"))
    }

    @Test
    fun ignoresBuildMetadataWhenComparingVersions() {
        assertFalse(OfficialSkinDownloader.isNewerVersion("1.0.0+build.2", "1.0.0+build.1"))
    }
}
