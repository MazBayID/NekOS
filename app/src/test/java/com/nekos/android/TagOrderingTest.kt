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
    fun convertsPagedTagIndicesToFullListIndices() {
        assertEquals(7, tagPageItemIndex(pageIndex = 1, itemIndex = 2))
        assertEquals(10, tagPageItemIndex(pageIndex = 2, itemIndex = 0))
        assertEquals(14, tagPageItemIndex(pageIndex = 2, itemIndex = 4))
    }

    @Test
    fun rejectsInvalidPageIndexArguments() {
        try {
            tagPageItemIndex(pageIndex = -1, itemIndex = 0)
            throw AssertionError("Expected invalid page index to be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            tagPageItemIndex(pageIndex = 0, itemIndex = 0, pageSize = 0)
            throw AssertionError("Expected invalid page size to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

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
    fun movesTagToDropTargetIndex() {
        assertEquals(listOf(third, first, second), moveTagToIndex(tags, "third", 0))
        assertEquals(listOf(first, third, second), moveTagToIndex(tags, "second", 2))
    }

    @Test
    fun keepsOrderWhenDropTargetIsInvalidOrTagIsMissing() {
        assertSame(tags, moveTagToIndex(tags, "missing", 1))
        assertSame(tags, moveTagToIndex(tags, "second", -1))
        assertSame(tags, moveTagToIndex(tags, "second", tags.size))
    }

    @Test
    fun keepsOrderWhenDroppedOnSamePosition() {
        assertSame(tags, moveTagToIndex(tags, "second", 1))
    }

    @Test
    fun movesTagToNextPageWithoutDroppingOtherTags() {
        val tenTags = (1..10).map { NekTag(id = "tag-$it", name = "Tag $it") }
        val moved = moveTagToPage(tenTags, "tag-2", 1)
        assertEquals("tag-1", moved[0].id)
        assertEquals("tag-6", moved[4].id)
        assertEquals("tag-2", moved[9].id)
        assertEquals(tenTags.map { it.id }.toSet(), moved.map { it.id }.toSet())
    }

    @Test
    fun movesTagToPreviousPageWithoutDroppingOtherTags() {
        val tenTags = (1..10).map { NekTag(id = "tag-$it", name = "Tag $it") }
        val moved = moveTagToPage(tenTags, "tag-8", 0)
        assertEquals("tag-8", moved[0].id)
        assertEquals(tenTags.map { it.id }.toSet(), moved.map { it.id }.toSet())
    }

    @Test
    fun invalidPageMoveLeavesTagsUnchanged() {
        assertSame(tags, moveTagToPage(tags, "missing", 1))
        assertSame(tags, moveTagToPage(tags, "first", 0))
    }

    @Test
    fun keepsOrderWhenTagDoesNotExistOrDirectionIsInvalid() {
        assertSame(tags, moveTag(tags, "missing", 1))
        assertSame(tags, moveTag(tags, "second", 0))
    }
}
