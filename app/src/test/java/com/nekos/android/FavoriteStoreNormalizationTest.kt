package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteStoreNormalizationTest {
    @Test
    fun padsFavoriteListToFifteenSlots() {
        assertEquals(
            listOf("one", "two") + List(13) { null },
            normalizeFavoritePackages(listOf("one", "two"))
        )
    }

    @Test
    fun removesDuplicatePackagesAndKeepsFirstOccurrence() {
        assertEquals(
            listOf("one", "two", null) + List(12) { null },
            normalizeFavoritePackages(listOf("one", "two", "one", null))
        )
    }

    @Test
    fun convertsBlankEntriesToEmptySlots() {
        assertEquals(
            listOf("one", null, null, "two") + List(11) { null },
            normalizeFavoritePackages(listOf("one", "", "  ", "two"))
        )
    }

    @Test
    fun truncatesEntriesBeyondFifteenSlots() {
        val packages = (1..17).map { "app$it" }

        val normalized = normalizeFavoritePackages(packages)

        assertEquals(15, normalized.size)
        assertEquals("app1", normalized.first())
        assertEquals("app15", normalized.last())
    }

    @Test
    fun returnsExactlyFifteenEmptySlotsForEmptyInput() {
        assertEquals(List(15) { null }, normalizeFavoritePackages(emptyList()))
    }

    @Test
    fun preservesPackageOrderWhileRemovingDuplicates() {
        assertEquals(
            listOf("third", "first", null, "second") + List(11) { null },
            normalizeFavoritePackages(listOf("third", "first", "third", "second"))
        )
    }
}
