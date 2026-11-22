package com.nekos.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeyedMutexTest {
    @Test
    fun sameKeySerializesConcurrentCallersAndReleasesEntry() = runBlocking {
        val keyedMutex = KeyedMutex()
        val activeCalls = AtomicInteger()
        val maximumConcurrentCalls = AtomicInteger()

        coroutineScope {
            repeat(64) {
                launch(Dispatchers.Default) {
                    keyedMutex.withLock("same-icon") {
                        val active = activeCalls.incrementAndGet()
                        maximumConcurrentCalls.updateAndGet { previous ->
                            maxOf(previous, active)
                        }
                        yield()
                        activeCalls.decrementAndGet()
                    }
                }
            }
        }

        assertEquals(1, maximumConcurrentCalls.get())
        assertEquals(0, keyedMutex.trackedKeyCount)
    }

    @Test
    fun differentKeysCanRunAtTheSameTime() = runBlocking {
        val keyedMutex = KeyedMutex()
        val enteredCount = AtomicInteger()
        val bothEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        suspend fun holdKey(key: String) {
            keyedMutex.withLock(key) {
                if (enteredCount.incrementAndGet() == 2) {
                    bothEntered.complete(Unit)
                }
                release.await()
            }
        }

        val first = async(Dispatchers.Default) { holdKey("icon-one") }
        val second = async(Dispatchers.Default) { holdKey("icon-two") }

        try {
            withTimeout(2_000) { bothEntered.await() }
        } finally {
            release.complete(Unit)
        }

        first.await()
        second.await()
        assertEquals(0, keyedMutex.trackedKeyCount)
    }

    @Test
    fun cancellingAWaitingCallerDoesNotLeakTheKeyEntry() = runBlocking {
        val keyedMutex = KeyedMutex()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            keyedMutex.withLock("shared-icon") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val waiting = launch(Dispatchers.Default) {
            keyedMutex.withLock("shared-icon") {
                throw AssertionError("A cancelled waiter must not enter the critical section")
            }
        }

        withTimeout(2_000) {
            while (keyedMutex.activeCallCount("shared-icon") != 2) {
                yield()
            }
        }

        waiting.cancelAndJoin()
        assertEquals(1, keyedMutex.activeCallCount("shared-icon"))

        releaseFirst.complete(Unit)
        first.await()
        assertEquals(0, keyedMutex.trackedKeyCount)
    }

    @Test
    fun thrownActionStillReleasesTheKeyEntry() = runBlocking {
        val keyedMutex = KeyedMutex()

        try {
            keyedMutex.withLock("failing-icon") {
                throw IllegalStateException("expected test failure")
            }
        } catch (_: IllegalStateException) {
            // Expected: the lock registry must still be cleaned up in finally.
        }

        assertEquals(0, keyedMutex.trackedKeyCount)
    }
}
