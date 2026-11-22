package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheMemoryStateTest {
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
        assertEquals(false, stored)
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

        assertEquals(null, result)
        assertEquals(false, stored)
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
