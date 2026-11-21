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

    val rightX = (anchorPosition.x + gapPx).roundToInt()
    val leftX = (anchorPosition.x - gapPx - popupSize.width).roundToInt()
    val belowY = (anchorPosition.y + gapPx).roundToInt()
    val aboveY = (anchorPosition.y - gapPx - popupSize.height).roundToInt()

    val desiredX = if (rightX <= maxX) rightX else leftX
    val desiredY = if (belowY <= maxY) belowY else aboveY

    return IntOffset(
        x = desiredX.coerceIn(0, maxX),
        y = desiredY.coerceIn(0, maxY)
    )
}
