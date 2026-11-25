package com.nekos.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinInstallerUpdateTest {
    @Test
    fun replacesInstalledDirectoryAndRemovesBackup() {
        val root = createTempDir(prefix = "nekos-skin-update-test")
        try {
            val installed = File(root, "inferno_phoenix").apply { mkdirs() }
            File(installed, "version.txt").writeText("1.0.0")
            val staging = File(root, "staging").apply { mkdirs() }
            File(staging, "version.txt").writeText("1.1.0")
            val backup = File(root, "backup")

            val result = finalizeSkinDirectory(staging, installed, backup)

            assertTrue(result.success)
            assertEquals("1.1.0", File(installed, "version.txt").readText())
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installsNewDirectoryWithoutCreatingBackup() {
        val root = createTempDir(prefix = "nekos-skin-install-test")
        try {
            val staging = File(root, "staging").apply { mkdirs() }
            File(staging, "skin.json").writeText("{}")
            val destination = File(root, "new_skin")
            val backup = File(root, "backup")

            val result = finalizeSkinDirectory(staging, destination, backup)

            assertTrue(result.success)
            assertTrue(File(destination, "skin.json").isFile)
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
