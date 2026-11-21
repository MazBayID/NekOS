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
            androidx.compose.ui.unit.IntOffset(178, 658),
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
    @Test
    fun popupFlipsHorizontallyWhenThereIsNoRoomOnTheRight() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset(178, 212),
            calculateLauncherPopupOffset(
                anchorPosition = Offset(390f, 200f),
                popupSize = IntSize(200, 120),
                containerSize = IntSize(400, 800)
            )
        )
    }

    @Test
    fun popupFlipsVerticallyWhenThereIsNoRoomBelow() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset(112, 658),
            calculateLauncherPopupOffset(
                anchorPosition = Offset(100f, 790f),
                popupSize = IntSize(180, 120),
                containerSize = IntSize(400, 800)
            )
        )
    }

    @Test
    fun popupClampsToEdgeWhenThereIsNoRoomOnEitherSide() {
        assertEquals(
            androidx.compose.ui.unit.IntOffset(0, 0),
            calculateLauncherPopupOffset(
                anchorPosition = Offset(5f, 5f),
                popupSize = IntSize(300, 300),
                containerSize = IntSize(200, 200)
            )
        )
    }

}
