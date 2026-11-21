package com.nekos.android

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInteractionControllerTest {
    @Test
    fun startDragCreatesOneUnmovedSession() {
        val controller = LauncherInteractionController()
        val position = Offset(12f, 24f)

        val session = controller.startDrag(
            packageName = "com.example.app",
            source = LauncherDragSource.APP_DRAWER,
            position = position
        )

        assertEquals("com.example.app", session.packageName)
        assertEquals(LauncherDragSource.APP_DRAWER, session.source)
        assertEquals(position, session.position)
        assertFalse(session.hasMoved)
        assertNull(session.targetIndex)
        assertEquals(session, controller.activeDrag)
    }

    @Test
    fun updateDragTracksPositionAndDropTarget() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            packageName = "com.example.app",
            source = LauncherDragSource.FAVORITE,
            position = Offset.Zero
        )

        val updated = controller.updateDrag(
            position = Offset(80f, 120f),
            targetIndex = 7
        )

        assertTrue(updated!!.hasMoved)
        assertEquals(Offset(80f, 120f), updated.position)
        assertEquals(7, updated.targetIndex)
        assertEquals(updated, controller.activeDrag)
    }

    @Test
    fun finishDragReturnsFinalSessionAndClearsController() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            packageName = "com.example.app",
            source = LauncherDragSource.APP_DRAWER,
            position = Offset.Zero
        )
        controller.updateDrag(Offset(10f, 10f), 3)

        val finished = controller.finishDrag()

        assertEquals(3, finished!!.targetIndex)
        assertNull(controller.activeDrag)
        assertNull(controller.finishDrag())
    }

    @Test
    fun cancelDragClearsSessionWithoutReturningDropTarget() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            packageName = "com.example.app",
            source = LauncherDragSource.FAVORITE,
            position = Offset.Zero
        )
        controller.updateDrag(Offset(10f, 10f), 2)

        controller.cancelDrag()

        assertNull(controller.activeDrag)
        assertNull(controller.finishDrag())
    }

    @Test
    fun updateWithoutActiveDragDoesNothing() {
        val controller = LauncherInteractionController()

        assertNull(controller.updateDrag(Offset(1f, 1f), 0))
        assertNull(controller.activeDrag)
    }
}
