package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconPackRepositoryTest {
    private val rules = listOf(
        AppIconKeywordRule(Regex("\\b9bit\\b", RegexOption.IGNORE_CASE), "globe"),
        AppIconKeywordRule(Regex("terminal|termux|shell", RegexOption.IGNORE_CASE), "terminal")
    )

    @Test
    fun explicitExactMappingWinsOverInstalledLabelRules() {
        val entry = AppIconPackEntry(iconPath = null, glyph = "terminal", source = "exact")

        assertEquals(
            "terminal",
            resolveAppIconGlyph(entry, "com.zeroday.cyberprotocol", "9BIT", rules, "paw")
        )
    }

    @Test
    fun importedRuleMappingKeepsItsCatalogGlyph() {
        val entry = AppIconPackEntry(iconPath = null, glyph = "bank", source = "rule")

        assertEquals(
            "bank",
            resolveAppIconGlyph(entry, "org.example.bank", "9BIT", rules, "paw")
        )
    }

    @Test
    fun defaultCatalogEntryRetriesRulesAgainstInstalledLabel() {
        val entry = AppIconPackEntry(iconPath = null, glyph = "paw", source = "default")

        assertEquals(
            "globe",
            resolveAppIconGlyph(entry, "org.chromium.webapk.opaque", "9BIT", rules, "paw")
        )
    }

    @Test
    fun defaultCatalogEntryUsesDefaultWhenNoRuleMatches() {
        val entry = AppIconPackEntry(iconPath = null, glyph = "paw", source = "default")

        assertEquals(
            "paw",
            resolveAppIconGlyph(entry, "org.example.unknown", "Unknown App", rules, "paw")
        )
    }

    @Test
    fun curatedEntryWithoutSourceKeepsItsExplicitGlyph() {
        val entry = AppIconPackEntry(iconPath = null, glyph = "music_note")

        assertEquals(
            "music_note",
            resolveAppIconGlyph(entry, "org.example.player", "9BIT", rules, "paw")
        )
    }

    @Test
    fun unlistedPackageStillUsesRuntimeKeywordRules() {
        assertEquals(
            "terminal",
            resolveAppIconGlyph(null, "com.example.shell", "Utility", rules, "paw")
        )
    }
}
