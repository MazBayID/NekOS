package com.nekos.android

import androidx.compose.ui.geometry.Offset

enum class LauncherDragSource {
    APP_DRAWER,
    FAVORITE
}

data class LauncherDragSession(
    val appId: String,
    val source: LauncherDragSource,
    val position: Offset,
    val hasMoved: Boolean = false,
    val targetIndex: Int? = null,
    val cancelTarget: Boolean = false
) {
    /** Only App Drawer drags expose the explicit cancel drop target. */
    val supportsCancelDropTarget: Boolean
        get() = source == LauncherDragSource.APP_DRAWER
}

/**
 * Owns the single active launcher drag session.
 *
 * UI composables report pointer positions and resolved drop targets here.
 * This class deliberately contains no Compose UI or animation behavior.
 */
class LauncherInteractionController {
    var activeDrag: LauncherDragSession? = null
        private set

    fun startDrag(
        appId: String,
        source: LauncherDragSource,
        position: Offset
    ): LauncherDragSession {
        return LauncherDragSession(
            appId = appId,
            source = source,
            position = position
        ).also { activeDrag = it }
    }

    fun updateDrag(
        position: Offset,
        targetIndex: Int?,
        cancelTarget: Boolean = false
    ): LauncherDragSession? {
        val current = activeDrag ?: return null
        return current.copy(
            position = position,
            hasMoved = true,
            targetIndex = targetIndex,
            cancelTarget = current.supportsCancelDropTarget && cancelTarget
        ).also { activeDrag = it }
    }

    /**
     * Ends the active gesture and returns its final snapshot exactly once.
     */
    fun finishDrag(): LauncherDragSession? {
        val finished = activeDrag
        activeDrag = null
        return finished
    }

    fun cancelDrag() {
        activeDrag = null
    }
}


/**
 * A cancelled pointer sequence must never commit an open-drawer transition.
 */
internal fun shouldOpenDrawerAfterDrag(
    totalDrag: Float,
    threshold: Float,
    cancelled: Boolean = false,
    velocityY: Float = 0f,
    velocityThreshold: Float = 900f
): Boolean {
    if (cancelled) return false
    return when {
        velocityY <= -velocityThreshold -> true
        velocityY >= velocityThreshold -> false
        else -> totalDrag <= -threshold
    }
}

internal fun shouldCloseDrawerAfterDrag(
    totalDrag: Float,
    threshold: Float,
    velocityY: Float = 0f,
    cancelled: Boolean = false,
    velocityThreshold: Float = 900f
): Boolean {
    if (cancelled) return false
    return when {
        velocityY >= velocityThreshold -> true
        velocityY <= -velocityThreshold -> false
        else -> totalDrag >= threshold
    }
}


/**
 * The Tag rail no longer scrolls vertically, so the Drawer swipe can begin
 * anywhere on Home. Tag-handle long-press drags are handled by the child gesture
 * detector, which consumes the active drag sequence before it reaches this parent.
 */
@Suppress("UNUSED_PARAMETER")
internal fun isHomeDrawerGestureEligible(
    positionX: Float,
    positionY: Float,
    containerWidth: Float,
    containerHeight: Float,
    favoriteBandPx: Float
): Boolean = true
