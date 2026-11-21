package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerFilterTest {
    @Test
    fun showsAllAppsWhenNoTagIsSelected() {
        assertTrue(matchesAppDrawerFilter("pkg.one", "One App", "", null))
    }

    @Test
    fun filtersAppsBySelectedTagPackages() {
        val tagPackages = setOf("pkg.one", "pkg.three")
        assertTrue(matchesAppDrawerFilter("pkg.one", "One App", "", tagPackages))
        assertFalse(matchesAppDrawerFilter("pkg.two", "Two App", "", tagPackages))
    }

    @Test
    fun combinesTagAndPrefixSearchFilters() {
        val tagPackages = setOf("pkg.one", "pkg.two")
        assertTrue(matchesAppDrawerFilter("pkg.one", "  Notes", "no", tagPackages))
        assertFalse(matchesAppDrawerFilter("pkg.three", "Notes", "no", tagPackages))
        assertFalse(matchesAppDrawerFilter("pkg.two", "Browser", "no", tagPackages))
    }

    @Test
    fun searchIsCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        assertTrue(matchesAppDrawerFilter("pkg.one", "  Notes", " no ", null))
        assertFalse(matchesAppDrawerFilter("pkg.one", "Notes", "tes", null))
    }
}
