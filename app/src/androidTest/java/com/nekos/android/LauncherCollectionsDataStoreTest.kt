package com.nekos.android

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.Preferences
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class LauncherCollectionsDataStoreTest {
    private val namespace = "LauncherCollectionsDataStoreTest_${UUID.randomUUID()}"
    private val context: Context by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("$namespace-$name", mode)

            override fun getFilesDir(): File =
                File(target.filesDir, namespace).apply { mkdirs() }
        }
    }
    private val stores by lazy {
        LauncherCollectionsDataStore.createForTest(context, namespace)
    }

    @Before
    fun clearLegacyPreferences() {
        listOf(
            "nekos_favorites",
            "nekos_tags",
            "nekos_favorite_snapshots"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun migratesFavoriteStringWithoutChangingItsLegacyFormat() = runBlocking {
        context.getSharedPreferences("nekos_favorites", Context.MODE_PRIVATE)
            .edit()
            .putString("favorite_packages", "com.example.alpha||com.example.beta")
            .commit()

        val migrated = stores.favorites.data.first().valueFor("favorite_packages")

        assertEquals("com.example.alpha||com.example.beta", migrated)
    }

    @Test
    fun preservesWrongTypedFavoriteValueForValidation() = runBlocking {
        context.getSharedPreferences("nekos_favorites", Context.MODE_PRIVATE)
            .edit()
            .putInt("favorite_packages", 42)
            .commit()

        val migrated = stores.favorites.data.first().valueFor("favorite_packages")

        assertEquals(42, migrated)
        assertTrue(migrated is Int)
    }

    @Test
    fun migratesMalformedTagJsonVerbatimForExistingValidation() = runBlocking {
        val malformed = "[not-json"
        context.getSharedPreferences("nekos_tags", Context.MODE_PRIVATE)
            .edit()
            .putString("tag_collection_v1", malformed)
            .commit()

        val migrated = stores.tags.data.first().valueFor("tag_collection_v1")

        assertEquals(malformed, migrated)
    }

    @Test
    fun migratesFavoriteSnapshotsVerbatim() = runBlocking {
        val snapshots = """[{"appId":"com.example.alpha","label":"Alpha","iconCacheKey":"alpha"}]"""
        context.getSharedPreferences("nekos_favorite_snapshots", Context.MODE_PRIVATE)
            .edit()
            .putString("favorite_app_snapshots_v1", snapshots)
            .commit()

        val migrated = stores.favoriteSnapshots.data.first()
            .valueFor("favorite_app_snapshots_v1")

        assertEquals(snapshots, migrated)
    }

    @Test
    fun migrationIsStableAcrossRepeatedReads() = runBlocking {
        val legacy = "com.example.alpha|com.example.beta"
        context.getSharedPreferences("nekos_favorites", Context.MODE_PRIVATE)
            .edit()
            .putString("favorite_packages", legacy)
            .commit()

        val first = stores.favorites.data.first().valueFor("favorite_packages")
        val second = stores.favorites.data.first().valueFor("favorite_packages")

        assertEquals(legacy, first)
        assertEquals(first, second)
    }


    @Test
    fun malformedFavoriteSnapshotIsUnreadableAndCannotBeOverwritten() = runBlocking {
        val malformed = "[not-json"
        context.getSharedPreferences("nekos_favorite_snapshots", Context.MODE_PRIVATE)
            .edit().putString("favorite_app_snapshots_v1", malformed).commit()

        assertFalse(FavoriteAppSnapshotStore.loadResult(context).isValid)
        assertFalse(
            FavoriteAppSnapshotStore.save(
                context,
                listOf(FavoriteAppSnapshot("com.example.app", "Example", "example"))
            )
        )
        val stored = LauncherCollectionsDataStore.get(context).favoriteSnapshots.data.first()
            .valueFor("favorite_app_snapshots_v1")
        assertEquals(malformed, stored)
    }

    @Test
    fun wrongTypedFavoriteSnapshotIsUnreadableAndCannotBeOverwritten() = runBlocking {
        context.getSharedPreferences("nekos_favorite_snapshots", Context.MODE_PRIVATE)
            .edit().putInt("favorite_app_snapshots_v1", 42).commit()

        assertFalse(FavoriteAppSnapshotStore.loadResult(context).isValid)
        assertFalse(FavoriteAppSnapshotStore.save(context, emptyList()))
        val stored = LauncherCollectionsDataStore.get(context).favoriteSnapshots.data.first()
            .valueFor("favorite_app_snapshots_v1")
        assertEquals(42, stored)
    }

    @Test
    fun wrongTypedTagMigrationMarkerIsUnreadableAndPreserved() = runBlocking {
        val tags = """[{\"id\":\"tag-one\",\"name\":\"Work\",\"apps\":[]}]"""
        context.getSharedPreferences("nekos_tags", Context.MODE_PRIVATE)
            .edit()
            .putString("tag_collection_v1", tags)
            .putInt(TAG_ICON_MIGRATION_KEY, 1)
            .commit()

        assertFalse(TagStore.loadResult(context).isValid)
        assertFalse(TagStore.save(context, listOf(NekTag(id = "new", name = "New"))))
        val stored = LauncherCollectionsDataStore.get(context).tags.data.first()
        assertEquals(tags, stored.valueFor("tag_collection_v1"))
        assertEquals(1, stored.asMap().entries.first { it.key.name == TAG_ICON_MIGRATION_KEY }.value)
    }

    private fun Preferences.valueFor(name: String): Any? =
        asMap().entries.firstOrNull { it.key.name == name }?.value
}
