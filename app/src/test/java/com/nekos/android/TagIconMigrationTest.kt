package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TagIconMigrationTest {
    @Test
    fun mapsMergedAndRemovedLegacyIconsToCanonicalIds() {
        val entries = listOf(
            LegacyTagIconEntry("icon_001.webp", "icon_cloud.webp"),
            LegacyTagIconEntry("icon_002.webp", "icon_cloud.webp"),
            LegacyTagIconEntry("icon_003.webp", null),
            LegacyTagIconEntry("icon_004.webp", "icon_star.webp")
        )
        val map = buildLegacyTagIconIdMap(entries)
        assertEquals(1, map[1])
        assertEquals(1, map[2])
        assertEquals(4, map[3])
        assertEquals(4, map[4])
    }
}
