package com.nekos.android

import android.content.ComponentName
import android.content.Intent
import android.os.BadParcelableException
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.UserHandle

/**
 * The public AOSP handshake payload used by gesture navigation when returning Home.
 *
 * This class only reads and validates the request. Resolving an icon and replying with
 * its SurfaceControl are deliberately handled by later launcher stages.
 */
internal data class GestureNavContractData(
    val componentName: ComponentName,
    val user: UserHandle,
    val callback: Message
) {
    companion object {
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"
        private const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"

        fun fromIntent(intent: Intent): GestureNavContractData? {
            // GestureNavContract is supported by Android 11 (API 30) and newer.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

            val extras = try {
                intent.getBundleExtra(EXTRA_GESTURE_CONTRACT)
            } catch (_: BadParcelableException) {
                intent.removeExtra(EXTRA_GESTURE_CONTRACT)
                return null
            } ?: return null

            // Match AOSP: consume the extra once, even if its payload is incomplete.
            intent.removeExtra(EXTRA_GESTURE_CONTRACT)

            return try {
                val componentName: ComponentName?
                val user: UserHandle?
                val callback: Message?

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    componentName = extras.getParcelable(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName::class.java
                    )
                    user = extras.getParcelable(Intent.EXTRA_USER, UserHandle::class.java)
                    callback = extras.getParcelable(
                        EXTRA_REMOTE_CALLBACK,
                        Message::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        componentName = extras.getParcelable(Intent.EXTRA_COMPONENT_NAME)
                        user = extras.getParcelable(Intent.EXTRA_USER)
                        callback = extras.getParcelable(EXTRA_REMOTE_CALLBACK)
                    }
                }

                if (componentName == null || user == null || callback?.replyTo == null) {
                    null
                } else {
                    GestureNavContractData(componentName, user, callback)
                }
            } catch (_: BadParcelableException) {
                null
            } catch (_: ClassCastException) {
                null
            }
        }
    }
}
