package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerFilterTest {
    @Test
    fun showsAllAppsWhenQueryIsBlank() {
        assertTrue(matchesAppDrawerSearch("One App", ""))
    }

    @Test
    fun searchesByLabelPrefixOnly() {
        assertTrue(matchesAppDrawerSearch("One App", "one"))
        assertFalse(matchesAppDrawerSearch("One App", "app"))
    }

    @Test
    fun searchIsCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        assertTrue(matchesAppDrawerSearch("  Notes", " no "))
        assertFalse(matchesAppDrawerSearch("Notes", "tes"))
    }
}
