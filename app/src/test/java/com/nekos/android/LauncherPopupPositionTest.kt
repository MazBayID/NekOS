package com.nekos.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPopupPositionTest {
    @Test
    fun popupStaysBesideAnchorWhenThereIsRoom() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset(112, 212),
            calculateLauncherPopupOffset(
                anchorPosition = Offset(100f, 200f),
                popupSize = IntSize(180, 120),
                containerSize = IntSize(400, 800)
            )
        )
    }

    @Test
    fun popupMovesUpAndLeftNearBottomRightCorner() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset(200, 680),
            calculateLauncherPopupOffset(
                anchorPosition = Offset(390f, 790f),
                popupSize = IntSize(200, 120),
                containerSize = IntSize(400, 800)
            )
        )
    }

    @Test
    fun popupOffsetClampsToOriginWhenPopupIsLargerThanContainer() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset.Zero,
            calculateLauncherPopupOffset(
                anchorPosition = Offset(300f, 500f),
                popupSize = IntSize(500, 900),
                containerSize = IntSize(400, 800)
            )
        )
    }
}
