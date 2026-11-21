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
 * A quick release is a tap. A stationary long-press release opens the item menu.
 * Movement after long-press crosses touch slop before a drag starts. The same
 * detector is shared by Favorite cells and App Drawer cells to avoid competing
 * clickable and pointerInput recognizers on the same item.
 */
suspend fun PointerInputScope.detectLauncherItemGesture(
    rootOrigin: () -> Offset,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
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

        var accumulated = Offset.Zero
        var dragging = false
        var completed = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) break

            if (event.changes.any { it.id != down.id && it.pressed }) {
                if (dragging) onDragCancel()
                completed = true
                break
            }

            if (!change.pressed) {
                if (dragging) onDragEnd() else onLongPress()
                completed = true
                break
            }

            val delta = change.positionChange()
            accumulated += delta

            if (!dragging && accumulated.getDistance() > viewConfiguration.touchSlop) {
                dragging = true
                val rootPosition = rootOrigin() + change.position
                onDragStart(rootPosition)
                change.consume()
                onDrag(rootPosition)
            } else if (dragging) {
                change.consume()
                onDrag(rootOrigin() + change.position)
            }
        }

        if (dragging && !completed) onDragCancel()
    }
}
