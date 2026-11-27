package com.nekos.android

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes catalog reconciliation with index-based Favorite and Tag reorder gestures.
 *
 * Reorders acquire the gate synchronously when the gesture starts and release it
 * only after the durable write or rollback. Catalog reconciliation waits for the
 * same gate, so it cannot invalidate a gesture's index positions or rollback state.
 */
internal class LauncherCollectionMutationGate {
    private val mutex = Mutex()

    fun tryBeginReorder(owner: Any): Boolean = mutex.tryLock(owner)

    fun endReorder(owner: Any) {
        mutex.unlock(owner)
    }

    suspend fun <T> withCatalogRefresh(block: suspend () -> T): T =
        mutex.withLock { block() }
}
