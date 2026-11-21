package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerFilterTest {
    @Test
    fun showsAllAppsWhenQueryIsBlank() {
        assertTrue(matchesAppDrawerSearch("One App", ""))
        assertTrue(matchesAppDrawerSearch("One App", "   "))
    }

    @Test
    fun searchesAnywhereInTheAppLabel() {
        assertTrue(matchesAppDrawerSearch("One App", "one"))
        assertTrue(matchesAppDrawerSearch("One App", "app"))
        assertTrue(matchesAppDrawerSearch("One App", "ne Ap"))
        assertFalse(matchesAppDrawerSearch("One App", "missing"))
    }

    @Test
    fun searchIsCaseInsensitiveAndIgnoresSurroundingQueryWhitespace() {
        assertTrue(matchesAppDrawerSearch("Google Play Store", " PLAY "))
        assertTrue(matchesAppDrawerSearch("  Notes", " no "))
        assertFalse(matchesAppDrawerSearch("Notes", "tesx"))
    }
}
