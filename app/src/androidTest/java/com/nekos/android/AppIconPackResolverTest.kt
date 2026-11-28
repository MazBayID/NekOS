package com.nekos.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppIconPackResolverTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exactPackageMappingCannotBeOverriddenByAnUnrelatedLabel() {
        val resolver = AppIconPackResolver(context)

        val exactBrand = resolver.resolve("com.alibaba.aliexpresshd", "WhatsApp")
        assertEquals("aliexpress", exactBrand.brand)
        assertEquals("bag", exactBrand.glyph)

        val exactGlyph = resolver.resolve("com.android.deskclock", "Chrome")
        assertEquals("alarm_clock", exactGlyph.glyph)
        assertEquals(null, exactGlyph.brand)
    }

    @Test
    fun unmappedPackageUsesExactNormalizedBrandLabelAndIndependentGlyphFallback() {
        val resolver = AppIconPackResolver(context)

        val resolved = resolver.resolve("org.example.unknown", "Google Chrome")

        assertEquals("googlechrome", resolved.brand)
        assertEquals("paw", resolved.glyph)
        assertNotNull(resolver.loadBitmap(resolved))
    }

    @Test
    fun prefixRulesSelectTheLongestMatchingPrefix() {
        val resolver = AppIconPackResolver(context)

        val resolved = resolver.resolve("com.google.android.apps.unknown", "Unknown App")

        assertEquals("google", resolved.glyph)
        assertEquals(null, resolved.brand)
    }

    @Test
    fun appGlyphAndTagIconAssetsRemainInSeparateNamespaces() {
        context.assets.open("icons/apps/icon_paw.webp").use { assertNotNull(it) }
        context.assets.open("skins/default/icons/tag/icon_star.webp").use { assertNotNull(it) }
    }
}
