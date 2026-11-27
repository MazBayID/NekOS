package com.nekos.android

import android.content.ComponentCallbacks2

/**
 * Release decoded launcher artwork as soon as the launcher is hidden.
 * Artwork is loaded again from its existing sources when the launcher resumes.
 */
internal fun shouldTrimLauncherCaches(level: Int): Boolean =
    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN

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
