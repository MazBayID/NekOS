package com.nekos.android

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Exercises the real pointer-input detector together with the launcher drag controller.
 *
 * The harness deliberately mirrors the callback boundary used by NekOSRoot:
 * gesture callbacks create/update/finish/cancel one LauncherInteractionController session.
 */
class LauncherItemGestureIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressOnEmptyHomeSurfaceOpensLauncherSettings() {
        var settingsOpened = false

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .testTag("empty-home-settings-surface")
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { settingsOpened = true },
                        onLongClickLabel = "Open launcher settings"
                    )
            )
        }

        composeRule.onNodeWithTag("empty-home-settings-surface").performTouchInput {
            longClick()
        }

        assertTrue(settingsOpened)
    }

    @Test
    fun quickReleaseIsDeliveredAsTap() {
        val harness = GestureHarnessState()

        composeRule.setContent {
            GestureHarness(source = LauncherDragSource.APP_DRAWER, state = harness)
        }

        composeRule.onNodeWithTag(GESTURE_ITEM_TAG).performTouchInput {
            down(center)
            up()
        }

        assertEquals(1, harness.tapCount)
        assertTrue(harness.events.isEmpty())
        assertNull(harness.controller.activeDrag)
    }

    @Test
    fun stationaryLongPressPreviewsThenEndsWithoutStartingDrag() {
        val harness = GestureHarnessState()

        composeRule.setContent {
            GestureHarness(source = LauncherDragSource.FAVORITE, state = harness)
        }

        composeRule.onNodeWithTag(GESTURE_ITEM_TAG).performTouchInput {
            longClick()
        }

        assertEquals(listOf("preview", "preview-end"), harness.events)
        assertNull(harness.controller.activeDrag)
        assertNull(harness.finishedDrag)
    }

    @Test
    fun longPressMoveAndReleaseCompletesOneDragSession() {
        val harness = GestureHarnessState()

        composeRule.setContent {
            GestureHarness(source = LauncherDragSource.APP_DRAWER, state = harness)
        }

        composeRule.onNodeWithTag(GESTURE_ITEM_TAG).performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(90f, 0f))
            up()
        }

        val finished = harness.finishedDrag
        assertEquals("com.example.gesture", finished?.packageName)
        assertEquals(LauncherDragSource.APP_DRAWER, finished?.source)
        assertTrue(finished?.hasMoved == true)
        assertEquals(4, finished?.targetIndex)
        assertTrue(finished?.supportsCancelDropTarget == true)
        assertNull(harness.controller.activeDrag)
        assertEquals(
            listOf("preview", "preview-cancel", "drag-start", "drag", "drag-end"),
            harness.events
        )
    }

    @Test
    fun systemCancellationClearsPreviewOrActiveDrag() {
        val harness = GestureHarnessState()

        composeRule.setContent {
            GestureHarness(source = LauncherDragSource.FAVORITE, state = harness)
        }

        composeRule.onNodeWithTag(GESTURE_ITEM_TAG).performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(90f, 0f))
            cancel()
        }

        assertNull(harness.controller.activeDrag)
        assertNull(harness.finishedDrag)
        assertTrue(harness.events.contains("drag-cancel"))
        assertFalse(harness.events.contains("drag-end"))
    }

    @Test
    fun secondPointerCancelsActiveLongPressDrag() {
        val harness = GestureHarnessState()

        composeRule.setContent {
            GestureHarness(source = LauncherDragSource.FAVORITE, state = harness)
        }

        composeRule.onNodeWithTag(GESTURE_ITEM_TAG).performTouchInput {
            down(0, center)
            advanceEventTime(600)
            moveBy(Offset(90f, 0f))
            down(1, Offset(180f, 60f))
            up(pointerId = 1)
            up(pointerId = 0)
        }

        assertNull(harness.controller.activeDrag)
        assertNull(harness.finishedDrag)
        assertTrue(harness.events.contains("drag-cancel"))
        assertFalse(harness.events.contains("drag-end"))
    }

    @Test
    fun tagHandleTapOpensOptions() {
        val harness = TagHandleHarnessState()

        composeRule.setContent { TagHandleGestureHarness(harness) }

        composeRule.onNodeWithTag(TAG_HANDLE_TAG).performTouchInput {
            down(center)
            up()
        }

        assertEquals(1, harness.optionsOpened)
        assertEquals(0, harness.dragStarted)
    }

    @Test
    fun stationaryTagHandleLongPressDoesNotOpenOptionsOrStartDrag() {
        val harness = TagHandleHarnessState()

        composeRule.setContent { TagHandleGestureHarness(harness) }

        composeRule.onNodeWithTag(TAG_HANDLE_TAG).performTouchInput {
            longClick()
        }

        assertEquals(0, harness.optionsOpened)
        assertEquals(0, harness.dragStarted)
        assertEquals(0, harness.dragFinished)
    }

    @Test
    fun tagHandleLongPressAndMoveStartsReorderingWithoutOpeningOptions() {
        val harness = TagHandleHarnessState()

        composeRule.setContent { TagHandleGestureHarness(harness) }

        composeRule.onNodeWithTag(TAG_HANDLE_TAG).performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(90f, 0f))
            up()
        }

        assertEquals(0, harness.optionsOpened)
        assertEquals(1, harness.dragStarted)
        assertEquals(1, harness.dragFinished)
    }

    private companion object {
        const val GESTURE_ITEM_TAG = "launcher-gesture-test-item"
        const val TAG_HANDLE_TAG = "tag-handle-gesture-test-item"
    }
}

private class GestureHarnessState {
    val controller = LauncherInteractionController()
    val events = mutableListOf<String>()
    var tapCount = 0
    var finishedDrag: LauncherDragSession? = null
}

@Composable
private fun GestureHarness(
    source: LauncherDragSource,
    state: GestureHarnessState
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .testTag("launcher-gesture-test-item")
            .pointerInput(source) {
                detectLauncherItemGesture(
                    toRootPosition = { it },
                    onTap = { state.tapCount += 1 },
                    onLongPress = { state.events += "preview" },
                    onLongPressEnd = { state.events += "preview-end" },
                    onLongPressCancel = { state.events += "preview-cancel" },
                    onDragStart = { position ->
                        state.events += "drag-start"
                        state.controller.startDrag(
                            packageName = "com.example.gesture",
                            source = source,
                            position = position
                        )
                    },
                    onDrag = { position ->
                        state.events += "drag"
                        state.controller.updateDrag(
                            position = position,
                            targetIndex = 4
                        )
                    },
                    onDragEnd = {
                        state.events += "drag-end"
                        state.finishedDrag = state.controller.finishDrag()
                    },
                    onDragCancel = {
                        state.events += "drag-cancel"
                        state.controller.cancelDrag()
                    }
                )
            }
    )
}

private class TagHandleHarnessState {
    var optionsOpened = 0
    var dragStarted = 0
    var dragFinished = 0
}

@Composable
private fun TagHandleGestureHarness(state: TagHandleHarnessState) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .testTag("tag-handle-gesture-test-item")
            .pointerInput(Unit) {
                detectLauncherItemGesture(
                    toRootPosition = { it },
                    onTap = { state.optionsOpened++ },
                    onLongPress = {},
                    onLongPressEnd = {},
                    onLongPressCancel = {},
                    onDragStart = { state.dragStarted++ },
                    onDrag = {},
                    onDragEnd = { state.dragFinished++ },
                    onDragCancel = {}
                )
            }
    )
}
