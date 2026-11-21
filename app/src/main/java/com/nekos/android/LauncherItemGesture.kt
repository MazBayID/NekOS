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
            val pointerStillPressed = currentEvent.changes.any { change ->
                change.id == down.id && change.pressed
            }
            if (!pointerStillPressed) onTap()
            return@awaitEachGesture
        }

        onLongPress(toRootPosition(longPress.position))

        var accumulated = Offset.Zero
        var dragging = false
        var completed = false

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
                if (dragging) onDragEnd() else onLongPressEnd()
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

        if (dragging && !completed) onDragCancel()
        else if (!dragging && !completed) onLongPressCancel()
    }
}
