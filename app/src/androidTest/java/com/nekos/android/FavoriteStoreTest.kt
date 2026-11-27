package com.nekos.android

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.Preferences
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class FavoriteStoreTest {
    private val preferenceNamespace = "FavoriteStoreTest_${UUID.randomUUID()}"
    private val context: Context by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File =
                File(target.filesDir, preferenceNamespace).apply { mkdirs() }

            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("$preferenceNamespace-$name", mode)
        }
    }

    @Before
    fun clearIsolatedPreferences() {
        favoritesPreferences().edit().clear().commit()
    }

    @After
    fun clearIsolatedPreferencesAfterTest() {
        favoritesPreferences().edit().clear().commit()
    }

    @Test
    fun emptyStorageReturnsFifteenEmptySlots() = runBlocking {
        assertEquals(List(15) { null }, loadFavoritePackages(context))
    }

    @Test
    fun savedPackagesAreRestoredAndPaddedToFifteenSlots() = runBlocking {
        val packages = listOf("com.example.alpha", null, "com.example.beta")

        assertTrue(saveFavoritePackages(context, packages))

        assertEquals(
            packages + List(12) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun valuesBeyondFifteenSlotsAreDiscarded() = runBlocking {
        val packages = (1..18).map { "com.example.app$it" }

        assertTrue(saveFavoritePackages(context, packages))

        assertEquals(packages.take(15), loadFavoritePackages(context).filterNotNull())
        assertEquals(15, loadFavoritePackages(context).size)
    }

    @Test
    fun blankEntriesBecomeEmptySlots() = runBlocking {
        favoritesPreferences().edit()
            .putString("favorite_packages", "com.example.alpha||   |com.example.beta")
            .commit()

        assertEquals(
            listOf("com.example.alpha", null, null, "com.example.beta") +
                List(11) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun duplicatePackageNamesKeepOnlyTheirFirstSlot() = runBlocking {
        val packages = listOf(
            "com.example.alpha",
            "com.example.beta",
            "com.example.alpha",
            "com.example.gamma",
            "com.example.beta"
        )

        assertTrue(saveFavoritePackages(context, packages))

        assertEquals(
            listOf("com.example.alpha", "com.example.beta", null, "com.example.gamma", null) +
                List(10) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun duplicateValuesAlreadyInStorageAreNormalizedOnLoad() = runBlocking {
        favoritesPreferences().edit()
            .putString(
                "favorite_packages",
                "com.example.alpha|com.example.alpha|com.example.beta"
            )
            .commit()

        assertEquals(
            listOf("com.example.alpha", null, "com.example.beta") +
                List(12) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun wrongPreferenceValueTypeIsUnreadableAndCannotBeOverwritten() = runBlocking {
        favoritesPreferences().edit().putInt("favorite_packages", 42).commit()

        val loadResult = loadFavoritePackagesResult(context)

        assertEquals(List(15) { null }, loadResult.packages)
        assertFalse(loadResult.isValid)
        assertFalse(saveFavoritePackages(context, listOf("com.example.new")))
        val storedValue = LauncherCollectionsDataStore.get(context)
            .favorites.data.first().valueFor("favorite_packages")
        assertEquals(42, storedValue)
    }

    @Test
    fun missingStorageIsAValidEmptyFavoriteCollection() = runBlocking {
        val loadResult = loadFavoritePackagesResult(context)

        assertEquals(List(15) { null }, loadResult.packages)
        assertTrue(loadResult.isValid)
    }

    private fun favoritesPreferences() =
        context.getSharedPreferences("nekos_favorites", Context.MODE_PRIVATE)

    private fun Preferences.valueFor(name: String): Any? =
        asMap().entries.firstOrNull { it.key.name == name }?.value
}
