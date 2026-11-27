package com.nekos.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class TagStoreTest {
    private val preferenceNamespace = "TagStoreTest_${UUID.randomUUID()}"
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
        tagsPreferences().edit().clear().commit()
    }

    @After
    fun clearIsolatedPreferencesAfterTest() {
        tagsPreferences().edit().clear().commit()
    }

    @Test
    fun emptyStorageReturnsNoTags() = runBlocking {
        assertEquals(emptyList<NekTag>(), TagStore.load(context))
    }

    @Test
    fun saveAndLoadPreservesTagFields() = runBlocking {
        val tags = listOf(
            NekTag(
                id = "tag-work",
                name = "Work",
                iconId = 7,
                colorArgb = 0xFF123456,
                appPackages = listOf("com.example.mail", "com.example.calendar")
            ),
            NekTag(id = "tag-play", name = "Play")
        )

        TagStore.save(context, tags)

        assertEquals(tags, TagStore.load(context))
    }

    @Test
    fun sameIconCanKeepDifferentFrameColorsForDifferentTags() = runBlocking {
        val tags = listOf(
            NekTag(
                id = "tag-one",
                name = "One",
                iconId = 7,
                colorArgb = 0xFFE7E1D6,
                iconFrameColorArgb = 0xFF2563EB
            ),
            NekTag(
                id = "tag-two",
                name = "Two",
                iconId = 7,
                colorArgb = 0xFFE7E1D6,
                iconFrameColorArgb = 0xFF7C3AED
            )
        )

        TagStore.save(context, tags)

        assertEquals(tags, TagStore.load(context))
    }

    @Test
    fun legacyTagsDefaultIconFrameColorToOcean() = runBlocking {
        tagsPreferences().edit().putString(
            "tag_collection_v1",
            """[{"id":"legacy","name":"Legacy","iconId":7,"apps":[]}]"""
        ).commit()

        val loaded = TagStore.load(context).single()

        assertEquals(DEFAULT_TAG_ICON_FRAME_COLOR, loaded.iconFrameColorArgb)
    }

    @Test
    fun saveTrimsNamesAndRemovesBlankAndDuplicatePackages() = runBlocking {
        val tag = NekTag(
            id = "tag-normalized",
            name = "  Productivity  ",
            appPackages = listOf(
                "com.example.mail",
                "",
                "com.example.mail",
                "com.example.calendar"
            )
        )

        TagStore.save(context, listOf(tag))

        assertEquals(
            listOf(
                tag.copy(
                    name = "Productivity",
                    appPackages = listOf("com.example.mail", "com.example.calendar")
                )
            ),
            TagStore.load(context)
        )
    }

    @Test
    fun namesAreLimitedToTwentyFourCharacters() = runBlocking {
        val longName = "123456789012345678901234567890"

        TagStore.save(context, listOf(NekTag(id = "tag-long", name = longName)))

        assertEquals("123456789012345678901234", TagStore.load(context).single().name)
    }

    @Test
    fun blankNamesAreNotPersisted() = runBlocking {
        TagStore.save(
            context,
            listOf(
                NekTag(id = "tag-blank", name = "   "),
                NekTag(id = "tag-valid", name = "Valid")
            )
        )

        assertEquals(listOf(NekTag(id = "tag-valid", name = "Valid")), TagStore.load(context))
    }

    @Test
    fun duplicateIdsKeepTheFirstTag() = runBlocking {
        TagStore.save(
            context,
            listOf(
                NekTag(id = "same-id", name = "First"),
                NekTag(id = "same-id", name = "Second"),
                NekTag(id = "other-id", name = "Other")
            )
        )

        assertEquals(
            listOf(
                NekTag(id = "same-id", name = "First"),
                NekTag(id = "other-id", name = "Other")
            ),
            TagStore.load(context)
        )
    }

    @Test
    fun malformedJsonReturnsEmptyList() = runBlocking {
        tagsPreferences().edit().putString("tag_collection_v1", "[not-json").commit()

        assertEquals(emptyList<NekTag>(), TagStore.load(context))
    }

    @Test
    fun malformedJsonIsReportedAsUnreadableAndCannotBeOverwritten() = runBlocking {
        val malformed = "[not-json"
        tagsPreferences().edit().putString("tag_collection_v1", malformed).commit()

        val loadResult = TagStore.loadResult(context)

        assertEquals(emptyList<NekTag>(), loadResult.tags)
        assertEquals(false, loadResult.isValid)
        assertEquals(false, TagStore.save(context, listOf(NekTag(id = "new", name = "New"))))
        assertEquals(malformed, storedTagValue())
    }

    @Test
    fun malformedAppsFieldIsUnreadableAndCannotBeOverwritten() = runBlocking {
        val malformed = """[{"id":"tag-one","name":"Work","apps":"com.example.mail"}]"""
        tagsPreferences().edit().putString("tag_collection_v1", malformed).commit()

        val loadResult = TagStore.loadResult(context)

        assertEquals(emptyList<NekTag>(), loadResult.tags)
        assertEquals(false, loadResult.isValid)
        assertEquals(false, TagStore.save(context, listOf(NekTag(id = "new", name = "New"))))
        assertEquals(malformed, storedTagValue())
    }

    @Test
    fun nonStringAppReferenceIsUnreadableAndCannotBeOverwritten() = runBlocking {
        val malformed = """[{"id":"tag-one","name":"Work","apps":["com.example.mail",42]}]"""
        tagsPreferences().edit().putString("tag_collection_v1", malformed).commit()

        val loadResult = TagStore.loadResult(context)

        assertEquals(emptyList<NekTag>(), loadResult.tags)
        assertEquals(false, loadResult.isValid)
        assertEquals(false, TagStore.save(context, listOf(NekTag(id = "new", name = "New"))))
        assertEquals(malformed, storedTagValue())
    }

    @Test
    fun wrongPreferenceValueTypeIsUnreadableAndCannotBeOverwritten() = runBlocking {
        tagsPreferences().edit().putInt("tag_collection_v1", 42).commit()

        val loadResult = TagStore.loadResult(context)

        assertEquals(emptyList<NekTag>(), loadResult.tags)
        assertEquals(false, loadResult.isValid)
        assertEquals(false, TagStore.save(context, listOf(NekTag(id = "new", name = "New"))))
        assertEquals(42, storedTagValue())
    }

    @Test
    fun missingStorageIsAValidEmptyCollection() = runBlocking {
        val loadResult = TagStore.loadResult(context)

        assertEquals(emptyList<NekTag>(), loadResult.tags)
        assertEquals(true, loadResult.isValid)
    }

    private fun tagsPreferences() =
        context.getSharedPreferences("nekos_tags", Context.MODE_PRIVATE)

    private suspend fun storedTagValue(): Any? =
        LauncherCollectionsDataStore.get(context).tags.data.first()
            .asMap().entries.firstOrNull { it.key.name == "tag_collection_v1" }?.value
}
