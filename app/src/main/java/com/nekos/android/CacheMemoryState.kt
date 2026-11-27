package com.nekos.android

import android.content.ComponentCallbacks2

/**
 * Android 13 and lower may still report this legacy level (15).
 * Android 14+ no longer sends it; keep the numeric value to preserve
 * low-memory handling on older supported devices without using the
 * API 35-deprecated framework constant.
 */
internal const val LEGACY_TRIM_MEMORY_RUNNING_CRITICAL = 15

/**
 * Keep decoded launcher artwork through ordinary visibility changes.
 * Release it when Android places the process in the background under memory pressure.
 */
internal fun shouldTrimLauncherCaches(level: Int): Boolean =
    level == LEGACY_TRIM_MEMORY_RUNNING_CRITICAL ||
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

/**
 * Coordinates cache state transitions with in-flight bitmap loads.
 * A trim or resume invalidates earlier load epochs, preventing stale decodes
 * from repopulating RAM after the cache has been released.
 */
internal class CacheMemoryState {
    private val lock = Any()
    private var enabled = true
    private var epoch = 0L

    fun isEnabled(): Boolean = synchronized(lock) { enabled }

    fun beginLoad(): Long? = synchronized(lock) {
        if (enabled) epoch else null
    }

    fun <T> ifEnabled(action: () -> T): T? = synchronized(lock) {
        if (enabled) action() else null
    }

    fun <T> storeIfCurrent(loadEpoch: Long, action: () -> T): T? =
        synchronized(lock) {
            if (enabled && epoch == loadEpoch) action() else null
        }

    /** Invalidates in-flight loads and cached data without disabling future loads. */
    fun invalidate(action: () -> Unit) {
        synchronized(lock) {
            epoch++
            action()
        }
    }

    fun trim(action: () -> Unit) {
        synchronized(lock) {
            enabled = false
            epoch++
            action()
        }
    }

    fun resume(action: () -> Unit) {
        synchronized(lock) {
            if (!enabled) {
                enabled = true
                epoch++
                action()
            }
        }
    }
}
