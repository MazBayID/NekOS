package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TagIconMigrationTest {
    @Test
    fun mapsMergedAndRemovedLegacyIconsToCanonicalIds() {
        val json = """{"tag":[
          {"old_file":"icon_001.webp","new_file":"icon_cloud.webp","status":"kept"},
          {"old_file":"icon_002.webp","new_file":"icon_cloud.webp","status":"merged"},
          {"old_file":"icon_003.webp","new_file":null,"status":"removed"},
          {"old_file":"icon_004.webp","new_file":"icon_star.webp","status":"kept"}
        ]}"""
        val map = buildLegacyTagIconIdMap(json)
        assertEquals(1, map[1])
        assertEquals(1, map[2])
        assertEquals(4, map[3])
        assertEquals(4, map[4])
    }
}
