package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSlotOperationsTest {
    @Test
    fun emptyDockHasFiveSlots() {
        assertEquals(List(5) { null }, normalizeDockPackages(emptyList()))
    }

    @Test
    fun addUsesFirstEmptySlotAndPadsToFive() {
        val result = addDockPackage(listOf("com.example.first", null), "com.example.second")
        assertEquals(
            DockSlotOperation.Updated(listOf("com.example.first", "com.example.second", null, null, null)),
            result
        )
    }

    @Test
    fun duplicateOrBlankPackageIsUnchanged() {
        val current = listOf("com.example.first", null, null, null, null)
        assertEquals(DockSlotOperation.Unchanged, addDockPackage(current, "com.example.first"))
        assertEquals(DockSlotOperation.Unchanged, addDockPackage(current, "  "))
    }

    @Test
    fun fullDockRejectsAdditionalPackage() {
        val current = (1..5).map { "com.example.app$it" }
        assertEquals(DockSlotOperation.Full, addDockPackage(current, "com.example.extra"))
    }

    @Test
    fun removeKeepsOtherSlotsInPlace() {
        assertEquals(
            listOf("com.example.first", null, "com.example.third", null, null),
            removeDockPackage(
                listOf("com.example.first", "com.example.second", "com.example.third"),
                "com.example.second"
            )
        )
    }

    @Test
    fun normalizationRemovesDuplicatesAndInvalidEntries() {
        assertEquals(
            listOf("com.example.first", null, null, "com.example.second", null),
            normalizeDockPackages(listOf("com.example.first", "com.example.first", " ", "com.example.second"))
        )
        assertTrue(normalizeDockPackages((1..8).map { "com.example.app$it" }).size == 5)
    }
}
