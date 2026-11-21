package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class FavoriteSlotOperationsTest {
    @Test
    fun addsFavoriteToFirstEmptySlot() {
        val current = listOf<String?>(null, "com.example.second", null)

        val result = addFavoriteToSlots(current, "com.example.first")

        assertEquals(
            FavoriteSlotOperation.Updated(
                listOf("com.example.first", "com.example.second", null)
            ),
            result
        )
        assertEquals(listOf(null, "com.example.second", null), current)
    }

    @Test
    fun doesNotAddFavoriteTwice() {
        val current = listOf<String?>("com.example.app", null)

        assertSame(
            FavoriteSlotOperation.Unchanged,
            addFavoriteToSlots(current, "com.example.app")
        )
    }

    @Test
    fun reportsFullWhenAddingToFullSlots() {
        val current = (0 until 15).map { "com.example.app$it" }

        assertSame(
            FavoriteSlotOperation.Full,
            addFavoriteToSlots(current, "com.example.new")
        )
    }

    @Test
    fun swapsExistingFavoriteWithOccupiedTarget() {
        val current = mutableListOf<String?>("com.example.first", "com.example.second", null)

        val result = placePackageInFavoriteSlot(current, "com.example.second", 0)

        assertEquals(
            FavoriteSlotOperation.Updated(
                listOf("com.example.second", "com.example.first") + List(13) { null }
            ),
            result
        )
    }

    @Test
    fun placesNewFavoriteInEmptyTarget() {
        val result = placePackageInFavoriteSlot(emptyList(), "com.example.new", 4)

        val expected = MutableList<String?>(15) { null }.apply {
            this[4] = "com.example.new"
        }
        assertEquals(FavoriteSlotOperation.Updated(expected), result)
    }

    @Test
    fun movesDisplacedFavoriteToFirstAvailableEmptySlot() {
        val current = MutableList<String?>(15) { null }.apply {
            this[0] = "com.example.first"
            this[3] = "com.example.displaced"
        }

        val result = placePackageInFavoriteSlot(current, "com.example.new", 3)

        val expected = current.toMutableList().apply {
            this[0] = "com.example.displaced"
            this[3] = "com.example.new"
        }
        assertEquals(FavoriteSlotOperation.Updated(expected), result)
    }

    @Test
    fun reportsFullWithoutChangingSlotsWhenNoDisplacementSpaceExists() {
        val current = (0 until 15).map { "com.example.app$it" }

        assertSame(
            FavoriteSlotOperation.Full,
            placePackageInFavoriteSlot(current, "com.example.new", 4)
        )
    }

    @Test
    fun keepsSlotsUnchangedWhenFavoriteIsDroppedOnItsCurrentPosition() {
        val current = listOf<String?>("com.example.first", "com.example.second")

        assertSame(
            FavoriteSlotOperation.Unchanged,
            placePackageInFavoriteSlot(current, "com.example.second", 1)
        )
    }

    @Test
    fun rejectsTargetOutsideFavoriteGrid() {
        assertThrows(IllegalArgumentException::class.java) {
            placePackageInFavoriteSlot(emptyList(), "com.example.app", 15)
        }
    }
}
