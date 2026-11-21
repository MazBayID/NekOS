package com.nekos.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherItemGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun setGestureContent() {
        events.clear()
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .testTag(GESTURE_TARGET)
                    .pointerInput(Unit) {
                        detectLauncherItemGesture(
                            toRootPosition = { it },
                            onTap = { events += "tap" },
                            onLongPress = { events += "preview" },
                            onLongPressEnd = { events += "longPressEnd" },
                            onLongPressCancel = { events += "longPressCancel" },
                            onDragStart = { events += "dragStart" },
                            onDrag = { events += "drag" },
                            onDragEnd = { events += "dragEnd" },
                            onDragCancel = { events += "dragCancel" }
                        )
                    }
            )
        }
    }

    @Test
    fun quickReleaseRemainsATap() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(listOf("tap"), events)
        }
    }

    @Test
    fun cancellationBeforeLongPressDoesNotFireTap() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(center)
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun stationaryLongPressPreviewsBeforeReleaseAndActivatesAfterRelease() {
        setGestureContent()
        val target = composeRule.onNodeWithTag(GESTURE_TARGET)

        target.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("preview"), events)
        }

        target.performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(listOf("preview", "longPressEnd"), events)
        }
    }

    @Test
    fun movingPastTouchSlopCancelsPreviewAndUsesDragPath() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            moveBy(Offset(100f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf("preview", "longPressCancel", "dragStart", "drag", "dragEnd"),
                events
            )
        }
    }

    @Test
    fun pointerCancellationAfterPreviewDismissesItWithoutOpeningMenu() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("preview", "longPressCancel"), events)
        }
    }

    @Test
    fun multiplePointersBeforeLongPressDoNotShowPreviewOrTap() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(pointerId = 0, position = Offset(40f, 40f))
            down(pointerId = 1, position = Offset(80f, 80f))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            up(pointerId = 1)
            up(pointerId = 0)
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun secondPointerCancelsThePendingMenuPreview() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(pointerId = 0, position = Offset(40f, 40f))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            down(pointerId = 1, position = Offset(80f, 80f))
            up(pointerId = 1)
            up(pointerId = 0)
        }

        composeRule.runOnIdle {
            assertEquals(listOf("preview", "longPressCancel"), events)
        }
    }

    @Test
    fun pointerCancellationDuringDragCancelsDrag() {
        setGestureContent()

        composeRule.onNodeWithTag(GESTURE_TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            moveBy(Offset(100f, 0f))
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf("preview", "longPressCancel", "dragStart", "drag", "dragCancel"),
                events
            )
        }
    }

    private companion object {
        const val GESTURE_TARGET = "gesture-target"
    }
}
