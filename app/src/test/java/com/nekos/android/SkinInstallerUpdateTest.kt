package com.nekos.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinInstallerIdentityTest {
    @Test
    fun refusesToReplaceAnInstalledSkinWithTheSameIdentity() {
        val root = createTempDir(prefix = "nekos-skin-immutable-test")
        try {
            val installed = File(root, "inferno_phoenix").apply { mkdirs() }
            File(installed, "version.txt").writeText("1.0.0")
            val staging = File(root, "staging").apply { mkdirs() }
            File(staging, "version.txt").writeText("1.1.0")

            val result = finalizeSkinInstallation(staging, installed)

            assertFalse(result.success)
            assertEquals("1.0.0", File(installed, "version.txt").readText())
            assertEquals("1.1.0", File(staging, "version.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }


    @Test
    fun replacesAnInvalidStaleDirectoryWhenReinstallingSkin() {
        val root = createTempDir(prefix = "nekos-skin-stale-directory-test")
        try {
            val stale = File(root, "skin-id").apply { mkdirs() }
            File(stale, "partial.txt").writeText("incomplete")
            val staging = File(root, "staging").apply { mkdirs() }
            File(staging, "skin.json").writeText("""{"schema_version":1}""")

            val result = finalizeSkinInstallation(
                staging,
                stale,
                existingDestinationIsValid = false
            )

            assertTrue(result.success)
            assertTrue(File(stale, "skin.json").isFile)
            assertFalse(File(stale, "partial.txt").exists())
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

            val result = finalizeSkinInstallation(staging, destination)

            assertTrue(result.success)
            assertTrue(File(destination, "skin.json").isFile)
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun restoresBackupWhenProcessStoppedBeforeReplacement() {
        val root = createTempDir(prefix = "nekos-skin-recovery-test")
        try {
            val backup = File(root, ".skin-update-inferno_phoenix-123e4567-e89b-12d3-a456-426614174000")
                .apply { mkdirs() }
            File(backup, "version.txt").writeText("1.0.0")
            File(backup, "skin.json").writeText(
                """{"schema_version":1,"skin":{"id":"inferno_phoenix","name":"Inferno","version":"1.0.0"}}"""
            )

            val recovered = recoverInterruptedSkinUpdates(root)

            assertTrue(recovered)
            assertEquals("1.0.0", File(root, "inferno_phoenix/version.txt").readText())
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removesBackupWhenReplacementAlreadyExists() {
        val root = createTempDir(prefix = "nekos-skin-recovery-complete-test")
        try {
            val installed = File(root, "inferno_phoenix").apply { mkdirs() }
            File(installed, "version.txt").writeText("1.1.0")
            File(installed, "skin.json").writeText(
                """{"schema_version":1,"skin":{"id":"inferno_phoenix","name":"Inferno","version":"1.1.0"}}"""
            )
            val backup = File(root, ".skin-update-inferno_phoenix-123e4567-e89b-12d3-a456-426614174000")
                .apply { mkdirs() }
            File(backup, "version.txt").writeText("1.0.0")

            val recovered = recoverInterruptedSkinUpdates(root)

            assertFalse(recovered)
            assertEquals("1.1.0", File(installed, "version.txt").readText())
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun restoresBackupWhenReplacementDirectoryHasInvalidManifest() {
        val root = createTempDir(prefix = "nekos-skin-recovery-invalid-test")
        try {
            val installed = File(root, "inferno_phoenix").apply { mkdirs() }
            File(installed, "version.txt").writeText("partial")
            File(installed, "skin.json").writeText(
                """{"schema_version":1,"skin":{"id":"wrong_id","name":"Broken","version":"1.1.0"}}"""
            )
            val backup = File(root, ".skin-update-inferno_phoenix-123e4567-e89b-12d3-a456-426614174000")
                .apply { mkdirs() }
            File(backup, "version.txt").writeText("1.0.0")
            File(backup, "skin.json").writeText(
                """{"schema_version":1,"skin":{"id":"inferno_phoenix","name":"Inferno","version":"1.0.0"}}"""
            )

            val recovered = recoverInterruptedSkinUpdates(root)

            assertTrue(recovered)
            assertEquals("1.0.0", File(installed, "version.txt").readText())
            assertFalse(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun keepsInvalidBackupAndPartialDestinationForInspection() {
        val root = createTempDir(prefix = "nekos-skin-recovery-invalid-backup-test")
        try {
            val installed = File(root, "inferno_phoenix").apply { mkdirs() }
            File(installed, "version.txt").writeText("partial")
            val backup = File(root, ".skin-update-inferno_phoenix-123e4567-e89b-12d3-a456-426614174000")
                .apply { mkdirs() }
            File(backup, "version.txt").writeText("corrupt backup")
            File(backup, "skin.json").writeText("{}")

            val recovered = recoverInterruptedSkinUpdates(root)

            assertFalse(recovered)
            assertEquals("partial", File(installed, "version.txt").readText())
            assertTrue(backup.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failsCleanlyWhenNewSkinStagingCannotBeFinalized() {
        val root = createTempDir(prefix = "nekos-skin-finalize-failure-test")
        try {
            val missingStaging = File(root, "missing-staging")
            val destination = File(root, "new_skin")

            val result = finalizeSkinInstallation(missingStaging, destination)

            assertFalse(result.success)
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

}
