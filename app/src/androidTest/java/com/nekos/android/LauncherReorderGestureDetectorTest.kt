package com.nekos.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LauncherReorderGestureDetectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickReleaseIsDeliveredAsTap() {
        val state = ReorderGestureState()
        composeRule.setContent { ReorderGestureHarness(state) }

        composeRule.onNodeWithTag(TARGET).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("tap"), state.events)
        }
    }

    @Test
    fun stationaryLongPressPreviewsAndEndsWithoutStartingReorder() {
        val state = ReorderGestureState()
        composeRule.setContent { ReorderGestureHarness(state) }

        composeRule.onNodeWithTag(TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf("preview", "preview-end"), state.events)
        }
    }

    @Test
    fun longPressAndMoveDelegatesDragToReorderable() {
        val state = ReorderGestureState()
        composeRule.setContent { ReorderGestureHarness(state) }

        composeRule.onNodeWithTag(TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            moveBy(Offset(100f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf("preview", "preview-cancel", "drag-start", "drag", "drag-end"),
                state.events
            )
        }
    }

    @Test
    fun cancellationDuringReorderCancelsTheDrag() {
        val state = ReorderGestureState()
        composeRule.setContent { ReorderGestureHarness(state) }

        composeRule.onNodeWithTag(TARGET).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50)
            move()
            moveBy(Offset(100f, 0f))
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf("preview", "preview-cancel", "drag-start", "drag", "drag-cancel"),
                state.events
            )
        }
    }

    private companion object {
        const val TARGET = "reorder-gesture-target"
    }
}

private class ReorderGestureState {
    val events = mutableListOf<String>()
}

@Composable
private fun ReorderGestureHarness(state: ReorderGestureState) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .testTag("reorder-gesture-target")
            .pointerInput(Unit) {
                LauncherReorderGestureDetector(
                    toRootPosition = { it + Offset(5f, 10f) },
                    onTap = { state.events += "tap" },
                    onLongPress = { state.events += "preview" },
                    onLongPressEnd = { state.events += "preview-end" },
                    onLongPressCancel = { state.events += "preview-cancel" }
                ).detect(
                    onDragStart = { state.events += "drag-start" },
                    onDragEnd = { state.events += "drag-end" },
                    onDragCancel = { state.events += "drag-cancel" },
                    onDrag = { _, _ -> state.events += "drag" }
                )
            }
    )
}
