package com.nekos.android

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInteractionControllerTest {
    @Test
    fun onlyAppDrawerDragsSupportTheCancelDropTarget() {
        val controller = LauncherInteractionController()

        val drawerDrag = controller.startDrag(
            appId = "com.example.drawer",
            source = LauncherDragSource.APP_DRAWER,
            position = Offset.Zero
        )
        assertTrue(drawerDrag.supportsCancelDropTarget)

        val favoriteDrag = controller.startDrag(
            appId = "com.example.favorite",
            source = LauncherDragSource.FAVORITE,
            position = Offset.Zero
        )
        assertFalse(favoriteDrag.supportsCancelDropTarget)
    }

    @Test
    fun startDragCreatesOneUnmovedSession() {
        val controller = LauncherInteractionController()
        val position = Offset(12f, 24f)

        val session = controller.startDrag(
            appId = "com.example.app",
            source = LauncherDragSource.APP_DRAWER,
            position = position
        )

        assertEquals("com.example.app", session.appId)
        assertEquals(LauncherDragSource.APP_DRAWER, session.source)
        assertEquals(position, session.position)
        assertFalse(session.hasMoved)
        assertNull(session.targetIndex)
        assertFalse(session.cancelTarget)
        assertEquals(session, controller.activeDrag)
    }

    @Test
    fun updateDragTracksPositionAndDropTarget() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            appId = "com.example.app",
            source = LauncherDragSource.APP_DRAWER,
            position = Offset.Zero
        )

        val updated = controller.updateDrag(
            position = Offset(80f, 120f),
            targetIndex = 7,
            cancelTarget = true
        )

        assertTrue(updated!!.hasMoved)
        assertEquals(Offset(80f, 120f), updated.position)
        assertEquals(7, updated.targetIndex)
        assertTrue(updated.cancelTarget)
        assertEquals(updated, controller.activeDrag)
    }

    @Test
    fun finishDragReturnsFinalSessionAndClearsController() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            appId = "com.example.app",
            source = LauncherDragSource.APP_DRAWER,
            position = Offset.Zero
        )
        controller.updateDrag(Offset(10f, 10f), 3, cancelTarget = true)

        val finished = controller.finishDrag()

        assertEquals(3, finished!!.targetIndex)
        assertTrue(finished.cancelTarget)
        assertNull(controller.activeDrag)
        assertNull(controller.finishDrag())
    }

    @Test
    fun cancelDragClearsSessionWithoutReturningDropTarget() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            appId = "com.example.app",
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

    @Test
    fun drawerOpensOnlyAfterCompletedUpwardSwipeBeyondThreshold() {
        assertTrue(shouldOpenDrawerAfterDrag(totalDrag = -80f, threshold = 64f))
        assertFalse(shouldOpenDrawerAfterDrag(totalDrag = -40f, threshold = 64f))
        assertFalse(shouldOpenDrawerAfterDrag(totalDrag = 80f, threshold = 64f))
    }

    @Test
    fun quickUpwardFlingOpensDrawerBeforeDistanceThreshold() {
        assertTrue(
            shouldOpenDrawerAfterDrag(
                totalDrag = -24f,
                threshold = 64f,
                velocityY = -1100f
            )
        )
    }

    @Test
    fun quickDownwardFlingDoesNotOpenDrawer() {
        assertFalse(
            shouldOpenDrawerAfterDrag(
                totalDrag = -24f,
                threshold = 64f,
                velocityY = 1100f
            )
        )
    }

    @Test
    fun slowShortSwipeDoesNotOpenDrawer() {
        assertFalse(
            shouldOpenDrawerAfterDrag(
                totalDrag = -24f,
                threshold = 64f,
                velocityY = -300f
            )
        )
    }

    @Test
    fun fastReverseFlingOverridesTheAccumulatedSwipeDistance() {
        assertFalse(
            shouldOpenDrawerAfterDrag(
                totalDrag = -100f,
                threshold = 64f,
                velocityY = 1200f
            )
        )
        assertTrue(
            shouldOpenDrawerAfterDrag(
                totalDrag = 100f,
                threshold = 64f,
                velocityY = -1200f
            )
        )
    }

    @Test
    fun drawerCloseUsesReleaseDirectionBeforeDistance() {
        assertFalse(
            shouldCloseDrawerAfterDrag(
                totalDrag = 100f,
                threshold = 72f,
                velocityY = -1200f
            )
        )
        assertTrue(
            shouldCloseDrawerAfterDrag(
                totalDrag = 20f,
                threshold = 72f,
                velocityY = 1200f
            )
        )
    }

    @Test
    fun cancelledDrawerCloseNeverClosesDrawer() {
        assertFalse(
            shouldCloseDrawerAfterDrag(
                totalDrag = 120f,
                threshold = 72f,
                velocityY = 0f,
                cancelled = true
            )
        )
    }

    @Test
    fun cancelledDrawerSwipeNeverOpensDrawer() {
        assertFalse(
            shouldOpenDrawerAfterDrag(
                totalDrag = -120f,
                threshold = 64f,
                cancelled = true
            )
        )
    }

    @Test
    fun favoriteDragCannotActivateCancelDropTarget() {
        val controller = LauncherInteractionController()
        controller.startDrag(
            appId = "com.example.favorite",
            source = LauncherDragSource.FAVORITE,
            position = Offset.Zero
        )

        val updated = controller.updateDrag(
            position = Offset(20f, 30f),
            targetIndex = null,
            cancelTarget = true
        )

        assertFalse(updated!!.supportsCancelDropTarget)
        assertFalse(updated.cancelTarget)
    }

}
