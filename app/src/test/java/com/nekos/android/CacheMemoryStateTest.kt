package com.nekos.android

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CacheMemoryStateTest {
    @Test
    fun hiddenLauncherReleasesArtworkCaches() {
        assertTrue(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
    }

    @Test
    fun criticalMemoryPressureAlsoTrimsLauncherCaches() {
        assertTrue(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertTrue(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
        assertFalse(shouldTrimLauncherCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }

    @Test
    fun loadStartedBeforeTrimCannotRepopulateCache() {
        val state = CacheMemoryState()
        var stored = false
        val loadEpoch = state.beginLoad()!!

        state.trim { }

        val result = state.storeIfCurrent(loadEpoch) {
            stored = true
        }

        assertNull(result)
        assertFalse(stored)
    }

    @Test
    fun loadStartedBeforeTrimAndResumeCannotRepopulateCache() {
        val state = CacheMemoryState()
        var stored = false
        val loadEpoch = state.beginLoad()!!

        state.trim { }
        state.resume { }

        val result = state.storeIfCurrent(loadEpoch) {
            stored = true
        }

        assertNull(result)
        assertEquals(false, stored)
    }

    @Test
    fun invalidateKeepsLoadingEnabledAndRejectsStaleLoads() {
        val state = CacheMemoryState()
        val oldEpoch = state.beginLoad()!!
        var cached = false

        state.invalidate { cached = false }

        assertTrue(state.isEnabled())
        assertNull(state.storeIfCurrent(oldEpoch) { cached = true })
        assertFalse(cached)
        assertTrue(state.beginLoad() != null)
    }

    @Test
    fun trimWaitsForAnInFlightStoreThenEvictsIt() {
        val state = CacheMemoryState()
        val cached = AtomicBoolean(false)
        val loadEpoch = state.beginLoad()!!
        val storeEntered = CountDownLatch(1)
        val releaseStore = CountDownLatch(1)

        val storeThread = Thread {
            state.storeIfCurrent(loadEpoch) {
                storeEntered.countDown()
                releaseStore.await()
                cached.set(true)
            }
        }
        storeThread.start()

        var trimThread: Thread? = null
        try {
            assertTrue(storeEntered.await(2, TimeUnit.SECONDS))
            trimThread = Thread {
                state.trim { cached.set(false) }
            }
            trimThread.start()
        } finally {
            releaseStore.countDown()
        }

        storeThread.join(2_000)
        trimThread?.join(2_000)
        assertFalse(storeThread.isAlive)
        assertFalse(trimThread?.isAlive ?: false)
        assertFalse(cached.get())
    }

    @Test
    fun currentLoadCanStoreAndTrimClearsItAtomically() {
        val state = CacheMemoryState()
        var cachedValue = 0
        val loadEpoch = state.beginLoad()!!

        val stored = state.storeIfCurrent(loadEpoch) {
            cachedValue = 1
            cachedValue
        }
        assertEquals(1, stored)
        assertEquals(1, cachedValue)

        state.trim { cachedValue = 0 }
        assertEquals(0, cachedValue)
        assertNull(state.beginLoad())
    }
}
