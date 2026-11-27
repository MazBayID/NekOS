package com.nekos.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File


/** Restores or removes backup directories left by an interrupted skin update. */
internal fun recoverInterruptedSkinUpdates(skinsDirectory: File): Boolean =
    synchronized(SkinDirectoryLock.monitor) {
        val root = skinsDirectory.canonicalFile
        val backups = root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(".skin-update-") }
            .orEmpty()
        var recovered = false
        for (backup in backups) {
            val suffix = backup.name.removePrefix(".skin-update-")
            if (suffix.length <= 37) continue
            val skinId = suffix.dropLast(37)
            if (!SkinInstaller.isLegacySafeSkinId(skinId)) continue
            val destination = File(root, skinId).canonicalFile
            if (destination.parentFile != root) continue

            if (isValidInstalledSkinDirectory(destination, skinId)) {
                // The replacement was fully installed; its backup is now obsolete.
                backup.deleteRecursively()
            } else if (isValidInstalledSkinDirectory(backup, skinId)) {
                // A destination without a valid manifest may be a partial replacement.
                // Restore only a backup that is itself a valid installed skin.
                if (destination.exists() && !destination.deleteRecursively()) continue
                if (backup.renameTo(destination)) recovered = true
            }
        }
        recovered
    }

internal fun isValidInstalledSkinDirectory(directory: File, expectedId: String): Boolean =
    runCatching {
        if (!directory.isDirectory) return false
        val manifest = File(directory, "skin.json")
        if (!manifest.isFile) return false
        val json = JSONObject(manifest.readText(Charsets.UTF_8))
        if (json.optInt("schema_version", -1) != 1) return false
        val skin = json.optJSONObject("skin") ?: return false
        val id = skin.optString("id")
        id == expectedId &&
            SkinInstaller.isValidSkinId(id) &&
            id != "default" &&
            skin.optString("name").isNotBlank() &&
            skin.optString("version").isNotBlank()
    }.getOrDefault(false)

internal data class InstalledSkin(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isBuiltIn: Boolean
)

/**
 * Owns the installed-skin directory and the persisted active skin selection.
 * The built-in default skin is always available and cannot be removed.
 */
internal class SkinRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val skinsDirectory = File(appContext.filesDir, "skins")

    init {
        recoverInterruptedUpdates()
    }

    /**
     * A process can be killed after the old skin directory was renamed to its
     * backup but before the staged replacement was moved into place. Restore
     * that backup when the destination is missing; otherwise the replacement
     * completed and the leftover backup is safe to remove.
     */
    @Synchronized
    private fun recoverInterruptedUpdates() {
        if (recoverInterruptedSkinUpdates(skinsDirectory)) SkinRuntimeState.invalidate()
    }

    @Synchronized
    fun listInstalledSkins(): List<InstalledSkin> = synchronized(SkinDirectoryLock.monitor) {
        val result = mutableListOf<InstalledSkin>()
        readBundledDefault()?.let(result::add)
        skinsDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.mapNotNull(::readInstalledSkin)
            ?.let(result::addAll)
        result
    }

    @Synchronized
    fun activeSkinId(): String = synchronized(SkinDirectoryLock.monitor) {
        val requested = preferences.getString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID) ?: DEFAULT_SKIN_ID
        if (requested == DEFAULT_SKIN_ID ||
            (SkinInstaller.isValidSkinId(requested) && findInstalledSkinDirectory(requested) != null)
        ) {
            return@synchronized requested
        }
        // A skin may have been removed or damaged while NekOS was not running.
        // Persist the safe fallback and notify every skin-dependent Compose
        // renderer so no artwork from the previous generation remains visible.
        if (preferences.edit().putString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID).commit()) {
            SkinRuntimeState.invalidate()
        }
        DEFAULT_SKIN_ID
    }

    @Synchronized
    fun setActiveSkin(id: String): Boolean = synchronized(SkinDirectoryLock.monitor) {
        if (id != DEFAULT_SKIN_ID && !SkinInstaller.isValidSkinId(id)) return@synchronized false
        val exists = id == DEFAULT_SKIN_ID || findInstalledSkinDirectory(id) != null
        if (!exists) return@synchronized false
        val saved = preferences.edit().putString(ACTIVE_SKIN_KEY, id).commit()
        if (saved) SkinRuntimeState.invalidate()
        saved
    }

    @Synchronized
    fun deleteSkin(id: String): Boolean = synchronized(SkinDirectoryLock.monitor) {
        if (id == DEFAULT_SKIN_ID || !SkinInstaller.isValidSkinId(id)) return@synchronized false
        val target = findInstalledSkinDirectory(id) ?: return@synchronized false
        if (target.parentFile != skinsDirectory.canonicalFile || !target.isDirectory) return@synchronized false
        if (activeSkinId() == id && !setActiveSkin(DEFAULT_SKIN_ID)) return@synchronized false
        target.deleteRecursively()
    }

    private fun findInstalledSkinDirectory(id: String): File? {
        if (!SkinInstaller.isValidSkinId(id)) return null
        val hashed = File(skinsDirectory, SkinInstaller.skinDirectoryName(id))
        if (readInstalledSkin(hashed)?.id == id) return hashed.canonicalFile
        if (SkinInstaller.isLegacySafeSkinId(id)) {
            val legacy = File(skinsDirectory, id)
            if (readInstalledSkin(legacy)?.id == id) return legacy.canonicalFile
        }
        return skinsDirectory.listFiles()
            ?.firstOrNull { it.isDirectory && readInstalledSkin(it)?.id == id }
            ?.canonicalFile
    }

    private fun readBundledDefault(): InstalledSkin? = runCatching {
        val json = appContext.assets.open("skins/default/skin.json").bufferedReader().use { it.readText() }
        parseSummary(JSONObject(json), true)
    }.getOrNull()

    private fun readInstalledSkin(directory: File): InstalledSkin? = runCatching {
        if (!directory.isDirectory) return null
        val manifest = File(directory, "skin.json")
        if (!manifest.isFile) return null
        val summary = parseSummary(JSONObject(manifest.readText(Charsets.UTF_8)), false) ?: return null
        val expectedFolder = SkinInstaller.skinDirectoryName(summary.id)
        val isLegacyFolder = SkinInstaller.isLegacySafeSkinId(summary.id) && directory.name == summary.id
        if (directory.name != expectedFolder && !isLegacyFolder) return null
        summary
    }.getOrNull()

    private fun parseSummary(json: JSONObject, builtIn: Boolean): InstalledSkin? {
        if (json.optInt("schema_version", -1) != 1) return null
        val skin = json.optJSONObject("skin") ?: return null
        val id = skin.optString("id")
        val name = skin.optString("name")
        val version = skin.optString("version")
        if (id.isBlank() || name.isBlank() || version.isBlank()) return null
        if (!builtIn && (!SkinInstaller.isValidSkinId(id) || id == DEFAULT_SKIN_ID)) return null
        return InstalledSkin(
            id = id,
            name = name,
            version = version,
            author = skin.optString("author"),
            description = skin.optString("description"),
            isBuiltIn = builtIn
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "nekos_skin_preferences"
        private const val ACTIVE_SKIN_KEY = "active_skin_id"
        private const val DEFAULT_SKIN_ID = "default"

        internal fun activeSkinDirectory(context: Context): File? =
            synchronized(SkinDirectoryLock.monitor) {
                val preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME, Context.MODE_PRIVATE
                )
                val id = preferences.getString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID) ?: DEFAULT_SKIN_ID
                if (id == DEFAULT_SKIN_ID || !SkinInstaller.isValidSkinId(id)) return@synchronized null
                val root = File(context.applicationContext.filesDir, "skins").canonicalFile
                val hashedName = SkinInstaller.skinDirectoryName(id)
                val directory = root.listFiles()
                    ?.firstOrNull { candidate ->
                        candidate.isDirectory &&
                            (candidate.name == hashedName ||
                                (SkinInstaller.isLegacySafeSkinId(id) && candidate.name == id)) &&
                            isValidInstalledSkinDirectory(candidate, id)
                    }
                    ?.canonicalFile
                    ?: return@synchronized null
                if (directory.parentFile != root) return@synchronized null
                directory
            }
    }
}


/** Compose-observable generation used to refresh cached skin artwork after selection changes. */
internal object SkinRuntimeState {
    var generation by mutableIntStateOf(0)
        private set

    fun invalidate() {
        generation++
    }
}
