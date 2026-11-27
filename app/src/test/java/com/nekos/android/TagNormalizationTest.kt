package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TagNormalizationTest {

    @Test
    fun derivesALighterTagColorFromIconColor() {
        assertEquals(0xFFBED0F9L, tagColorFromIconColor(0xFF2563EBL))
    }

    @Test
    fun preservesIconColorAlphaWhenDerivingTagColor() {
        assertEquals(0x80BED0F9L, tagColorFromIconColor(0x802563EBL))
    }

    @Test
    fun trimsTagNameAndLimitsItToTwentyFourCharacters() {
        val tag = NekTag(id = "tools", name = "  A very long collection name  ")

        val normalized = tag.normalized()

        assertEquals("A very long collection n", normalized.name)
        assertEquals(24, normalized.name.length)
    }

    @Test
    fun removesBlankPackagesAndDuplicatePackageNames() {
        val tag = NekTag(
            id = "apps",
            name = "Apps",
            appPackages = listOf("com.example.one", "", " ", "com.example.two", "com.example.one")
        )

        assertEquals(
            listOf("com.example.one", "com.example.two"),
            tag.normalized().appPackages
        )
    }

    @Test
    fun preservesTagIdentityAndAppearanceMetadata() {
        val tag = NekTag(
            id = "creative",
            name = " Creative ",
            iconId = 12,
            colorArgb = 0xFF123456,
            appPackages = listOf("com.example.editor"),
            iconFrameColorArgb = 0xFF2563EB
        )

        assertEquals(
            NekTag(
                id = "creative",
                name = "Creative",
                iconId = 12,
                colorArgb = 0xFFBED0F9,
                appPackages = listOf("com.example.editor"),
                iconFrameColorArgb = 0xFF2563EB
            ),
            tag.normalized()
        )
    }

    @Test
    fun normalizationIsIdempotent() {
        val tag = NekTag(
            id = "mixed",
            name = "  Mixed collection  ",
            appPackages = listOf("a", "b", "a", "")
        )

        val once = tag.normalized()

        assertEquals(once, once.normalized())
    }

    @Test
    fun returnsEquivalentValueWhenAlreadyNormalized() {
        val tag = NekTag(
            id = "ready",
            name = "Ready",
            iconId = 3,
            colorArgb = tagColorFromIconColor(DEFAULT_TAG_ICON_FRAME_COLOR),
            appPackages = listOf("a", "b")
        )

        assertEquals(tag, tag.normalized())
    }
}
