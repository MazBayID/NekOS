package com.nekos.android

import android.content.Context
import org.json.JSONObject
import java.io.File

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

    @Synchronized
    fun listInstalledSkins(): List<InstalledSkin> {
        val result = mutableListOf<InstalledSkin>()
        readBundledDefault()?.let(result::add)
        skinsDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.mapNotNull(::readInstalledSkin)
            ?.let(result::addAll)
        return result
    }

    @Synchronized
    fun activeSkinId(): String {
        val requested = preferences.getString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID) ?: DEFAULT_SKIN_ID
        if (requested == DEFAULT_SKIN_ID ||
            (SkinInstaller.isValidSkinId(requested) &&
                readInstalledSkin(File(skinsDirectory, requested)) != null)
        ) {
            return requested
        }
        preferences.edit().putString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID).commit()
        return DEFAULT_SKIN_ID
    }

    @Synchronized
    fun setActiveSkin(id: String): Boolean {
        if (id != DEFAULT_SKIN_ID && !SkinInstaller.isValidSkinId(id)) return false
        val exists = id == DEFAULT_SKIN_ID || readInstalledSkin(File(skinsDirectory, id)) != null
        if (!exists) return false
        return preferences.edit().putString(ACTIVE_SKIN_KEY, id).commit()
    }

    @Synchronized
    fun deleteSkin(id: String): Boolean {
        if (id == DEFAULT_SKIN_ID || !SkinInstaller.isValidSkinId(id)) return false
        val target = File(skinsDirectory, id).canonicalFile
        if (target.parentFile != skinsDirectory.canonicalFile || !target.isDirectory) return false
        if (activeSkinId() == id && !setActiveSkin(DEFAULT_SKIN_ID)) return false
        return target.deleteRecursively()
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
        if (summary.id != directory.name) return null
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

        internal fun activeSkinDirectory(context: Context): File? {
            val preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE
            )
            val id = preferences.getString(ACTIVE_SKIN_KEY, DEFAULT_SKIN_ID) ?: DEFAULT_SKIN_ID
            if (id == DEFAULT_SKIN_ID || !SkinInstaller.isValidSkinId(id)) return null
            val root = File(context.applicationContext.filesDir, "skins").canonicalFile
            val directory = File(root, id).canonicalFile
            if (directory.parentFile != root || !directory.isDirectory) return null
            val manifest = File(directory, "skin.json")
            return if (manifest.isFile) directory else null
        }
    }
}
