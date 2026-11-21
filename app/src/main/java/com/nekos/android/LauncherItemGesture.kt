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
 * The same detector is shared by Favorite and App Drawer cells.
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
                // Compose marks synthetic pointer-cancel changes as consumed. Do not
                // mistake system cancellation for a real finger release.
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
            // Also clear transient UI/drag state if this pointerInput coroutine is
            // cancelled because its node leaves composition or its key changes.
            if (!completed) {
                if (dragging) onDragCancel() else onLongPressCancel()
            }
        }
    }
}
