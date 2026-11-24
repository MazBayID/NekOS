package com.nekos.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Debug-only diagnostics for intermittent Favorite input issues.
 *
 * No gesture behavior is changed by this logger. Release builds and non-debuggable
 * installs produce no output.
 */
internal object FavoriteGestureDiagnostics {
    private const val TAG = "NekOS-Favorite"

    fun log(context: Context, message: String) {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.d(TAG, message)
        }
    }
}
