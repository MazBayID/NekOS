package com.nekos.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCollectionMutationGateTest {
    @Test
    fun catalogRefreshWaitsUntilReorderHasCommittedOrRolledBack() = runBlocking {
        val gate = LauncherCollectionMutationGate()
        val reorderOwner = Any()
        val refreshEntered = CompletableDeferred<Unit>()

        assertTrue(gate.tryBeginReorder(reorderOwner))

        val refresh = async {
            gate.withCatalogRefresh {
                refreshEntered.complete(Unit)
                "refreshed"
            }
        }

        yield()
        assertFalse(refreshEntered.isCompleted)

        gate.endReorder(reorderOwner)

        assertEquals("refreshed", withTimeout(2_000) { refresh.await() })
        assertTrue(refreshEntered.isCompleted)
    }

    @Test
    fun reorderCannotStartWhileCatalogRefreshIsReconciling() = runBlocking {
        val gate = LauncherCollectionMutationGate()
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()

        val refresh = async {
            gate.withCatalogRefresh {
                refreshEntered.complete(Unit)
                releaseRefresh.await()
            }
        }

        refreshEntered.await()
        assertFalse(gate.tryBeginReorder(Any()))

        releaseRefresh.complete(Unit)
        withTimeout(2_000) { refresh.await() }

        val reorderOwner = Any()
        assertTrue(gate.tryBeginReorder(reorderOwner))
        gate.endReorder(reorderOwner)
    }
}
