package com.nekos.android


import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange

/**
 * One pointer-input owner for launcher item tap, long-press, and long-press drag.
 *
 * A quick release is a tap. A stationary long-press immediately previews the item
 * menu while the pointer remains down; release makes the menu actionable.
 * Movement beyond touch slop dismisses the preview and starts the existing drag.
 * Non-reorder drag surfaces such as App Drawer cells use this detector.
 */
suspend fun PointerInputScope.detectLauncherItemGesture(
    toRootPosition: (Offset) -> Offset,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)

        if (longPress == null) {
            val downChange = currentEvent.changes.firstOrNull { it.id == down.id }
            val pointerStillPressed = downChange?.pressed == true
            val gestureWasCancelled = downChange == null ||
                downChange.isConsumed ||
                currentEvent.changes.any { it.id != down.id && it.pressed }

            if (!pointerStillPressed && !gestureWasCancelled) onTap()
            return@awaitEachGesture
        }

        // This detector deliberately treats multi-touch as cancellation. In
        // particular, do not transfer a long-press to a second finger.
        val hasAdditionalPointer = currentEvent.changes.any {
            it.id != down.id && it.pressed
        }
        if (longPress.id != down.id || hasAdditionalPointer) {
            return@awaitEachGesture
        }

        onLongPress(toRootPosition(longPress.position))

        var accumulated = Offset.Zero
        var dragging = false
        var completed = false

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }

                if (change == null) {
                    if (dragging) onDragCancel() else onLongPressCancel()
                    completed = true
                    break
                }

                if (event.changes.any { it.id != down.id && it.pressed }) {
                    if (dragging) onDragCancel() else onLongPressCancel()
                    completed = true
                    break
                }

                if (!change.pressed) {
                    // Compose marks synthetic pointer-cancel changes as consumed.
                    // Do not mistake system cancellation for a normal finger release.
                    if (change.isConsumed) {
                        if (dragging) onDragCancel() else onLongPressCancel()
                    } else {
                        if (dragging) onDragEnd() else onLongPressEnd()
                    }
                    completed = true
                    break
                }

                val delta = change.positionChange()
                accumulated += delta

                if (!dragging && accumulated.getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    onLongPressCancel()
                    val rootPosition = toRootPosition(change.position)
                    onDragStart(rootPosition)
                    change.consume()
                    onDrag(rootPosition)
                } else if (dragging) {
                    change.consume()
                    onDrag(toRootPosition(change.position))
                }
            }
        } finally {
            // Clear transient preview/drag state if this pointer-input coroutine
            // is cancelled because its node leaves composition or its key changes.
            if (!completed) {
                if (dragging) onDragCancel() else onLongPressCancel()
            }
        }
    }
}


/**
 * Routes tap and stationary long-press actions through Reorderable's drag
 * detector, while handing actual long-press-and-move sequences to the
 * library's reorder engine. This keeps launcher-specific actions intact
 * without running a second competing pointer-input detector.
 */
internal class LauncherReorderGestureDetector(
    private val toRootPosition: (Offset) -> Offset = { it },
    private val onTap: () -> Unit = {},
    private val onLongPress: (Offset) -> Unit = {},
    private val onLongPressEnd: () -> Unit = {},
    private val onLongPressCancel: () -> Unit = {},
    private val onReorderStart: () -> Unit = {},
    private val onReorderEnd: (cancelled: Boolean) -> Unit = {},
    private val onTrace: (String) -> Unit = {},
) : sh.calvin.reorderable.DragGestureDetector {
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset) -> Unit
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onTrace("down position=${down.position} consumed=${down.isConsumed}")
            val longPress = awaitLongPressOrCancellation(down.id)

            if (longPress == null) {
                val downChange = currentEvent.changes.firstOrNull { it.id == down.id }
                val pointerReleased = downChange?.pressed == false
                val gestureCancelled = downChange == null ||
                    downChange.isConsumed ||
                    currentEvent.changes.any { it.id != down.id && it.pressed }
                onTrace("press-ended tapCandidate=$pointerReleased cancelled=$gestureCancelled changeConsumed=${downChange?.isConsumed}")
                if (pointerReleased && !gestureCancelled) {
                    onTrace("tap")
                    onTap()
                }
                return@awaitEachGesture
            }

            if (
                longPress.id != down.id ||
                currentEvent.changes.any { it.id != down.id && it.pressed }
            ) {
                onTrace("long-press-rejected pointerMismatch=${longPress.id != down.id} multiTouch=${currentEvent.changes.any { it.id != down.id && it.pressed }}")
                return@awaitEachGesture
            }

            onTrace("long-press position=${toRootPosition(longPress.position)}")
            onLongPress(toRootPosition(longPress.position))

            var accumulated = Offset.Zero
            var dragging = false
            var completed = false

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }

                    if (change == null) {
                        onTrace("pointer-missing dragging=$dragging")
                        if (dragging) {
                            onReorderEnd(true)
                            onDragCancel()
                        } else onLongPressCancel()
                        completed = true
                        break
                    }

                    if (event.changes.any { it.id != down.id && it.pressed }) {
                        onTrace("multi-touch-cancel dragging=$dragging")
                        if (dragging) {
                            onReorderEnd(true)
                            onDragCancel()
                        } else onLongPressCancel()
                        completed = true
                        break
                    }

                    if (!change.pressed) {
                        onTrace("release consumed=${change.isConsumed} dragging=$dragging")
                        if (change.isConsumed) {
                            if (dragging) {
                                onTrace("reorder-cancel")
                                onReorderEnd(true)
                                onDragCancel()
                            } else {
                                onTrace("long-press-cancel")
                                onLongPressCancel()
                            }
                        } else {
                            if (dragging) {
                                onTrace("reorder-end")
                                onReorderEnd(false)
                                onDragEnd()
                            } else {
                                onTrace("long-press-end")
                                onLongPressEnd()
                            }
                        }
                        completed = true
                        break
                    }

                    val delta = change.positionChange()
                    accumulated += delta

                    if (!dragging && accumulated.getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        onTrace("reorder-start delta=${accumulated}")
                        onLongPressCancel()
                        onReorderStart()
                        onDragStart(change.position)
                        change.consume()
                        onDrag(change, accumulated)
                        accumulated = Offset.Zero
                    } else if (dragging) {
                        change.consume()
                        onDrag(change, delta)
                    }
                }
            } finally {
                if (!completed) {
                    onTrace("detector-finally cancelled dragging=$dragging")
                    if (dragging) {
                        onReorderEnd(true)
                        onDragCancel()
                    } else onLongPressCancel()
                }
            }
        }
    }
}
