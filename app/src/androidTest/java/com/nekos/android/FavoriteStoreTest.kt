package com.nekos.android

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FavoriteStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPreferences() {
        favoritesPreferences().edit().clear().commit()
    }

    @After
    fun clearPreferencesAfterTest() {
        favoritesPreferences().edit().clear().commit()
    }

    @Test
    fun emptyStorageReturnsFifteenEmptySlots() {
        assertEquals(List(15) { null }, loadFavoritePackages(context))
    }

    @Test
    fun savedPackagesAreRestoredAndPaddedToFifteenSlots() {
        val packages = listOf("com.example.alpha", null, "com.example.beta")

        saveFavoritePackages(context, packages)

        assertEquals(
            packages + List(12) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun valuesBeyondFifteenSlotsAreDiscarded() {
        val packages = (1..18).map { "com.example.app$it" }

        saveFavoritePackages(context, packages)

        assertEquals(packages.take(15), loadFavoritePackages(context).filterNotNull())
        assertEquals(15, loadFavoritePackages(context).size)
    }

    @Test
    fun blankEntriesBecomeEmptySlots() {
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
    fun duplicatePackageNamesKeepOnlyTheirFirstSlot() {
        val packages = listOf(
            "com.example.alpha",
            "com.example.beta",
            "com.example.alpha",
            "com.example.gamma",
            "com.example.beta"
        )

        saveFavoritePackages(context, packages)

        assertEquals(
            listOf("com.example.alpha", "com.example.beta", null, "com.example.gamma", null) +
                List(10) { null },
            loadFavoritePackages(context)
        )
    }

    @Test
    fun duplicateValuesAlreadyInStorageAreNormalizedOnLoad() {
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
    fun wrongPreferenceValueTypeDoesNotCrashOrReturnInvalidSlots() {
        favoritesPreferences().edit().putInt("favorite_packages", 42).commit()

        assertEquals(List(15) { null }, loadFavoritePackages(context))
    }

    private fun favoritesPreferences() =
        context.getSharedPreferences("nekos_favorites", Context.MODE_PRIVATE)
}
