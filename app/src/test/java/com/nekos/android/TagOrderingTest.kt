package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TagOrderingTest {
    private val first = NekTag(id = "first", name = "First")
    private val second = NekTag(id = "second", name = "Second")
    private val third = NekTag(id = "third", name = "Third")
    private val tags = listOf(first, second, third)

    @Test
    fun movesTagUpByOnePosition() {
        assertEquals(listOf(second, first, third), moveTag(tags, "second", -1))
    }

    @Test
    fun movesTagDownByOnePosition() {
        assertEquals(listOf(first, third, second), moveTag(tags, "second", 1))
    }

    @Test
    fun keepsOrderWhenTagIsAtBoundary() {
        assertSame(tags, moveTag(tags, "first", -1))
        assertSame(tags, moveTag(tags, "third", 1))
    }

    @Test
    fun keepsOrderWhenTagDoesNotExistOrDirectionIsInvalid() {
        assertSame(tags, moveTag(tags, "missing", 1))
        assertSame(tags, moveTag(tags, "second", 0))
    }
}
