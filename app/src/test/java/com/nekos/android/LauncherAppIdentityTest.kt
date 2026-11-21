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
}
