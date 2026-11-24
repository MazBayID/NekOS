package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LauncherAppIdentityTest {
    @Test
    fun samePackageInDifferentProfilesGetsDifferentIdentity() {
        val personalApp = launcherAppId("com.example.notes", 10L)
        val workApp = launcherAppId("com.example.notes", 42L)

        assertNotEquals(personalApp, workApp)
        assertEquals("10::com.example.notes", personalApp)
        assertEquals("42::com.example.notes", workApp)
    }

    @Test
    fun identityIsStableForTheSamePackageAndProfileSerial() {
        assertEquals(
            launcherAppId("com.example.notes", 42L),
            launcherAppId("com.example.notes", 42L)
        )
    }

    @Test
    fun removesPersistedMarkersForDeletedProfilesButKeepsExistingProfiles() {
        val markers = setOf(
            "10::com.example.personal",
            "42::com.example.work",
            "deleted-profile-marker",
            "not-a-serial::com.example.invalid"
        )

        val retained = retainAppIdsForExistingProfiles(markers) { serial ->
            serial == 42L
        }

        assertEquals(setOf("42::com.example.work"), retained)
    }
}
