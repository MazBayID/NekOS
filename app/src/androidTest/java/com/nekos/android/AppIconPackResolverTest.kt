package com.nekos.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun knownAppsUseTheirVerifiedCategoryGlyphs() {
        val resolver = AppIconPackResolver(context)
        assertEquals("calculator", resolver.resolve("com.transsion.calculator", "Kalkulator").glyph)
        assertEquals("calculator", resolver.resolve("com.hihonor.calculator", "Calculator").glyph)
        assertEquals("share", resolver.resolve("com.xiaomi.midrop", "ShareMe").glyph)
        assertEquals("radio", resolver.resolve("com.android.fmradio", "Radio").glyph)
        assertEquals("terminal", resolver.resolve("moe.shizuku.privileged.api", "Shizuku").glyph)
    }

    @Test
    fun categoryKeywordsAreCheckedBeforeVendorPrefixes() {
        val resolver = AppIconPackResolver(context)
        assertEquals("calculator", resolver.resolve("com.transsion.unknowncalculator", "Kalkulator").glyph)
        assertEquals("radio", resolver.resolve("com.android.vendor.fmradio", "Radio").glyph)
    }

    @Test
    fun unknownUnclassifiedPackageUsesNoSyntheticDefaultIcon() {
        val resolver = AppIconPackResolver(context)
        val resolved = resolver.resolve("org.example.unknown", "Unrecognized App")
        assertNull(resolved.glyph)
        assertNull(resolved.brand)
        assertNull(resolver.loadBitmap(resolved))
    }

    @Test
    fun unmappedPackageUsesExactNormalizedBrandLabelAndIndependentGlyphFallback() {
        val resolver = AppIconPackResolver(context)
        val resolved = resolver.resolve("org.example.unknown", "Google Chrome")
        assertEquals("googlechrome", resolved.brand)
        assertEquals("globe", resolved.glyph)
        assertNotNull(resolver.loadBitmap(resolved))
    }

    @Test
    fun prefixRulesSelectTheLongestMatchingPrefixAfterCategoryKeywords() {
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
