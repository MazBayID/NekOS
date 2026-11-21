package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteSlotOperationsTest {
    @Test
    fun addFavoriteUsesFirstEmptySlot() {
        val current = List(15) { if (it == 0 || it == 2) "app.$it" else null }

        val result = addFavoriteToSlots(current, "app.new")

        assertEquals(
            FavoriteSlotOperation.Updated(
                listOf("app.0", "app.new", "app.2") + List(12) { null }
            ),
            result
        )
    }

    @Test
    fun addingExistingFavoriteDoesNotChangeSlots() {
        val current = listOf("app.one", null) + List(13) { null }

        assertSame(
            FavoriteSlotOperation.Unchanged,
            addFavoriteToSlots(current, "app.one")
        )
    }

    @Test
    fun addingToFullSlotsReturnsFull() {
        val current = List(15) { "app.$it" }

        assertSame(
            FavoriteSlotOperation.Full,
            addFavoriteToSlots(current, "app.new")
        )
    }

    @Test
    fun movingExistingFavoriteSwapsWithTarget() {
        val current = listOf("app.one", null, "app.two") + List(12) { null }

        val result = placePackageInFavoriteSlot(current, "app.one", 2)

        assertEquals(
            FavoriteSlotOperation.Updated(
                listOf("app.two", null, "app.one") + List(12) { null }
            ),
            result
        )
    }

    @Test
    fun droppingNewAppOnOccupiedSlotMovesDisplacedAppToFirstEmptySlot() {
        val current = listOf("app.one", "app.two", null) + List(12) { null }

        val result = placePackageInFavoriteSlot(current, "app.new", 1)

        assertEquals(
            FavoriteSlotOperation.Updated(
                listOf("app.one", "app.new", "app.two") + List(12) { null }
            ),
            result
        )
    }

    @Test
    fun droppingNewAppOnOccupiedSlotWhenFullReturnsFull() {
        val current = List(15) { "app.$it" }

        assertSame(
            FavoriteSlotOperation.Full,
            placePackageInFavoriteSlot(current, "app.new", 4)
        )
    }

    @Test
    fun droppingFavoriteIntoItsCurrentSlotDoesNotChangeSlots() {
        val current = listOf("app.one", null) + List(13) { null }

        assertSame(
            FavoriteSlotOperation.Unchanged,
            placePackageInFavoriteSlot(current, "app.one", 0)
        )
    }

    @Test
    fun shortFavoriteListIsPaddedToFifteenSlots() {
        val result = placePackageInFavoriteSlot(listOf("app.one"), "app.new", 4)

        assertEquals(15, (result as FavoriteSlotOperation.Updated).packages.size)
        assertEquals("app.new", result.packages[4])
    }
}
