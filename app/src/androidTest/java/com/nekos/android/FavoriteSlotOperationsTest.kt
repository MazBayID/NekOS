package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteSlotOperationsTest {
    @Test fun addFavoriteUsesFirstEmptySlot() {
        val current = List(15) { if (it == 0 || it == 2) "app.$it" else null }
        assertEquals(FavoriteSlotOperation.Updated(listOf("app.0", "app.new", "app.2") + List(12) { null }), addFavoriteToSlots(current, "app.new"))
    }
    @Test fun addingExistingFavoriteDoesNotChangeSlots() {
        val current = listOf("app.one", null) + List(13) { null }
        assertSame(FavoriteSlotOperation.Unchanged, addFavoriteToSlots(current, "app.one"))
    }
    @Test fun addingToFullSlotsReturnsFull() {
        assertSame(FavoriteSlotOperation.Full, addFavoriteToSlots(List(15) { "app.$it" }, "app.new"))
    }
    @Test fun movingExistingFavoriteSwapsWithTarget() {
        val current = listOf("app.one", null, "app.two") + List(12) { null }
        assertEquals(FavoriteSlotOperation.Updated(listOf("app.two", null, "app.one") + List(12) { null }), placePackageInFavoriteSlot(current, "app.one", 2))
    }
    @Test fun droppingNewAppMovesDisplacedToFirstEmptySlot() {
        val current = listOf("app.one", "app.two", null) + List(12) { null }
        assertEquals(FavoriteSlotOperation.Updated(listOf("app.one", "app.new", "app.two") + List(12) { null }), placePackageInFavoriteSlot(current, "app.new", 1))
    }
    @Test fun droppingNewAppOnOccupiedSlotWhenFullReturnsFull() {
        assertSame(FavoriteSlotOperation.Full, placePackageInFavoriteSlot(List(15) { "app.$it" }, "app.new", 4))
    }
    @Test fun droppingIntoCurrentSlotDoesNotChangeSlots() {
        val current = listOf("app.one", null) + List(13) { null }
        assertSame(FavoriteSlotOperation.Unchanged, placePackageInFavoriteSlot(current, "app.one", 0))
    }
    @Test fun shortFavoriteListIsPaddedToFifteenSlots() {
        val result = placePackageInFavoriteSlot(listOf("app.one"), "app.new", 4) as FavoriteSlotOperation.Updated
        assertEquals(15, result.packages.size)
        assertEquals("app.new", result.packages[4])
    }
}