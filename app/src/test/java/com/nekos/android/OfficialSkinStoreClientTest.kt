package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkinStoreClientTest {
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
