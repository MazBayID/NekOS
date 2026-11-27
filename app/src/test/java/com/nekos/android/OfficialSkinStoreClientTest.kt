package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkinStoreClientTest {
    @Test
    fun resolvesInstalledSkinByPackageKeyOrCatalogId() {
        val packageSkin = InstalledSkin(
            id = "package-skin-key",
            name = "Purchased skin",
            version = "1.0.0",
            author = "",
            description = "",
            isBuiltIn = false
        )
        val catalogSkin = OfficialStoreSkin(
            id = "catalog-row-id",
            skinKey = "package-skin-key",
            name = "Purchased skin",
            author = "",
            description = "",
            price = 5000L,
            currency = "IDR",
            previewUrl = ""
        )

        assertEquals(
            packageSkin,
            findInstalledStoreSkin(listOf(packageSkin), catalogSkin)
        )
    }

    @Test
    fun returnsNullWhenStoreSkinIsNotInstalled() {
        val otherSkin = InstalledSkin(
            id = "different-skin",
            name = "Other skin",
            version = "1.0.0",
            author = "",
            description = "",
            isBuiltIn = false
        )
        val catalogSkin = OfficialStoreSkin(
            id = "catalog-row-id",
            skinKey = "package-skin-key",
            name = "Purchased skin",
            author = "",
            description = "",
            price = 5000L,
            currency = "IDR",
            previewUrl = ""
        )

        assertNull(findInstalledStoreSkin(listOf(otherSkin), catalogSkin))
    }

    @Test
    fun acceptsOnlyHttpsSupabaseSignedStorageUrls() {
        assertTrue(
            isTrustedSkinDownloadUrl(
                "https://vizmufbfaghqucsoukne.supabase.co/storage/v1/object/sign/skin-packages/example.zip?token=abc"
            )
        )
    }

    @Test
    fun rejectsUntrustedDownloadSchemesHostsAndPaths() {
        assertFalse(
            isTrustedSkinDownloadUrl(
                "http://vizmufbfaghqucsoukne.supabase.co/storage/v1/object/sign/skin-packages/example.zip?token=abc"
            )
        )
        assertFalse(
            isTrustedSkinDownloadUrl(
                "https://evil.example/storage/v1/object/sign/skin-packages/example.zip?token=abc"
            )
        )
        assertFalse(
            isTrustedSkinDownloadUrl(
                "https://vizmufbfaghqucsoukne.supabase.co/functions/v1/skin-store?token=abc"
            )
        )
        assertFalse(
            isTrustedSkinDownloadUrl(
                "https://user@vizmufbfaghqucsoukne.supabase.co/storage/v1/object/sign/skin-packages/example.zip?token=abc"
            )
        )
        assertFalse(
            isTrustedSkinDownloadUrl(
                "https://vizmufbfaghqucsoukne.supabase.co:8443/storage/v1/object/sign/skin-packages/example.zip?token=abc"
            )
        )
        assertFalse(
            isTrustedSkinDownloadUrl(
                "https://vizmufbfaghqucsoukne.supabase.co/storage/v1/object/sign/skin-packages/example.zip"
            )
        )
    }
}
