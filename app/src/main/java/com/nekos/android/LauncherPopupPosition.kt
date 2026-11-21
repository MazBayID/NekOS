package com.nekos.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Places the app-action popup beside the press point while keeping its measured
 * bounds inside the launcher root. All values are in pixels.
 */
internal fun calculateLauncherPopupOffset(
    anchorPosition: Offset,
    popupSize: IntSize,
    containerSize: IntSize,
    gapPx: Float = 12f
): IntOffset {
    val maxX = (containerSize.width - popupSize.width).coerceAtLeast(0)
    val maxY = (containerSize.height - popupSize.height).coerceAtLeast(0)

    val desiredX = (anchorPosition.x + gapPx).roundToInt()
    val desiredY = (anchorPosition.y + gapPx).roundToInt()

    return IntOffset(
        x = desiredX.coerceIn(0, maxX),
        y = desiredY.coerceIn(0, maxY)
    )
}
