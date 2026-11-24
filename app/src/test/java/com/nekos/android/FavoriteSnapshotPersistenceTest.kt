package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSnapshotPersistenceTest {
    @Test
    fun allowsSnapshotUpdateWhenFavoriteListDidNotChange() {
        assertTrue(shouldPersistFavoriteSnapshots(
            favoritesChanged = false,
            favoriteSaveSucceeded = false
        ))
    }

    @Test
    fun blocksSnapshotUpdateWhenChangedFavoritesCouldNotBeSaved() {
        assertFalse(shouldPersistFavoriteSnapshots(
            favoritesChanged = true,
            favoriteSaveSucceeded = false
        ))
    }

    @Test
    fun allowsSnapshotUpdateAfterChangedFavoritesWereSaved() {
        assertTrue(shouldPersistFavoriteSnapshots(
            favoritesChanged = true,
            favoriteSaveSucceeded = true
        ))
    }
}
