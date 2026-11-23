package com.nekos.android

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Message
import android.os.UserHandle
import androidx.core.os.BundleCompat

/**
 * The private handshake used by Android's gesture navigation implementation
 * to animate an app window toward its matching launcher icon.
 *
 * This mirrors the public AOSP Launcher3 contract without depending on
 * Launcher3 implementation classes, which are not part of the SDK.
 */
internal data class GestureNavContractData(
    val componentName: ComponentName,
    val user: UserHandle,
    val remoteCallback: Message
) {
    fun sendEndPosition(
        position: android.graphics.RectF,
        surfaceControl: android.view.SurfaceControl,
        finishCallback: Message
    ) {
        val result = android.os.Bundle().apply {
            putParcelable(EXTRA_ICON_POSITION, position)
            putParcelable(EXTRA_ICON_SURFACE, surfaceControl)
            putParcelable(EXTRA_ON_FINISH_CALLBACK, finishCallback)
        }
        val callback = Message.obtain().apply {
            copyFrom(remoteCallback)
            data = result
        }
        try {
            callback.replyTo?.send(callback)
        } catch (_: android.os.RemoteException) {
            // System UI may have abandoned the gesture; the launcher can continue normally.
        }
    }

    companion object {
        private const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        private const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
        private const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        private const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        private const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"

        fun fromIntent(intent: Intent): GestureNavContractData? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return null
            intent.removeExtra(EXTRA_GESTURE_CONTRACT)
            extras.classLoader = GestureNavContractData::class.java.classLoader

            val component = BundleCompat.getParcelable(
                extras,
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName::class.java
            ) ?: return null
            val user = BundleCompat.getParcelable(
                extras,
                Intent.EXTRA_USER,
                UserHandle::class.java
            ) ?: return null
            val callback = BundleCompat.getParcelable(
                extras,
                EXTRA_REMOTE_CALLBACK,
                Message::class.java
            ) ?: return null
            if (callback.replyTo == null) return null

            return GestureNavContractData(component, user, callback)
        }
    }
}
