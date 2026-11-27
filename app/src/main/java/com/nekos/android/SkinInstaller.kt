package com.nekos.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

internal fun isSkinArchiveEntryCountAllowed(zip: ZipFile, maxEntries: Int): Boolean =
    maxEntries > 0 && zip.size() in 1..maxEntries

internal object SkinDirectoryLock {
    val monitor = Any()
}

internal data class SkinDirectoryFinalizeResult(
    val success: Boolean,
    val error: String? = null
)

/** Installs a skin only when its permanent identity is not already present. */
internal fun finalizeSkinInstallation(
    staging: File,
    destination: File
): SkinDirectoryFinalizeResult {
    if (destination.exists()) {
        return SkinDirectoryFinalizeResult(false, "Skin is already installed")
    }
    return if (staging.renameTo(destination)) {
        SkinDirectoryFinalizeResult(true)
    } else {
        SkinDirectoryFinalizeResult(false, "Unable to finalize skin installation")
    }
}

internal data class SkinInstallResult(
    val success: Boolean,
    val skinId: String? = null,
    val error: String? = null
)

/**
 * Installs data-only NekOS skin archives.
 *
 * Archives are copied to a private temporary file, inspected before extraction,
 * extracted into a staging directory, and moved into the installed-skins folder
 * only after the manifest and every image have passed validation.
 */
internal class SkinInstaller(context: Context) {
    private val appContext = context.applicationContext

    fun install(uri: Uri): SkinInstallResult {
        val input = runCatching { appContext.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return SkinInstallResult(false, error = "Unable to open selected file")
        return input.use { install(it) }
    }

    @Synchronized
    fun install(input: InputStream): SkinInstallResult {
        val workDir = File(appContext.cacheDir, "skin-import-${UUID.randomUUID()}")
        val archive = File(workDir, "skin.zip")
        val staging = File(workDir, "staging")
        return try {
            if (!workDir.mkdirs()) {
                return SkinInstallResult(false, error = "Unable to create temporary directory")
            }
            archive.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_ARCHIVE_BYTES) {
                        return SkinInstallResult(false, error = "Archive is larger than 32 MB")
                    }
                    output.write(buffer, 0, count)
                }
            }

            ZipFile(archive).use { zip ->
                // Check the central-directory entry count before allocating ZipEntry objects.
                if (!isSkinArchiveEntryCountAllowed(zip, MAX_ARCHIVE_ENTRIES)) {
                    return SkinInstallResult(false, error = "Invalid file count in skin archive")
                }
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                if (entries.isEmpty()) {
                    return SkinInstallResult(false, error = "Invalid file count in skin archive")
                }

                val seen = HashSet<String>()
                var declaredTotal = 0L
                for (entry in entries) {
                    val path = entry.name
                    if (!isSafeEntryPath(path) || !isAllowedSkinPath(path)) {
                        return SkinInstallResult(false, error = "Unsupported or unsafe file path: $path")
                    }
                    if (!seen.add(path.lowercase(Locale.ROOT))) {
                        return SkinInstallResult(false, error = "Duplicate file path: $path")
                    }
                    if (entry.size < 0L || entry.size > MAX_FILE_BYTES) {
                        return SkinInstallResult(false, error = "Invalid or oversized file: $path")
                    }
                    declaredTotal += entry.size
                    if (declaredTotal > MAX_TOTAL_BYTES) {
                        return SkinInstallResult(false, error = "Uncompressed skin exceeds 32 MB")
                    }
                    if (entry.size > 1_000_000L && entry.compressedSize > 0L &&
                        entry.size / entry.compressedSize.coerceAtLeast(1L) > MAX_COMPRESSION_RATIO
                    ) {
                        return SkinInstallResult(false, error = "Suspicious compression ratio: $path")
                    }
                }
                if ("skin.json" !in seen) {
                    return SkinInstallResult(false, error = "skin.json must be at the ZIP root")
                }

                if (!staging.mkdirs()) {
                    return SkinInstallResult(false, error = "Unable to prepare installation")
                }
                var extractedTotal = 0L
                for (entry in entries) {
                    val destination = File(staging, entry.name).canonicalFile
                    val rootPath = staging.canonicalPath + File.separator
                    if (!destination.path.startsWith(rootPath)) {
                        return SkinInstallResult(false, error = "Unsafe destination path")
                    }
                    destination.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { source ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var fileBytes = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                extractedTotal += count
                                if (fileBytes > MAX_FILE_BYTES || extractedTotal > MAX_TOTAL_BYTES) {
                                    return SkinInstallResult(false, error = "Extracted skin exceeds size limits")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (destination.length() != entry.size) {
                        return SkinInstallResult(false, error = "Corrupt archive entry: ${entry.name}")
                    }
                }
            }

            val manifest = validateExtractedSkin(staging)
                ?: return SkinInstallResult(false, error = "Invalid skin manifest or assets")
            val skinId = manifest.getJSONObject("skin").getString("id")
            val skinsRoot = File(appContext.filesDir, "skins")
            if (!skinsRoot.exists() && !skinsRoot.mkdirs()) {
                return SkinInstallResult(false, error = "Unable to create skins directory")
            }
            val destination = File(skinsRoot, skinDirectoryName(skinId)).canonicalFile
            if (destination.parentFile != skinsRoot.canonicalFile) {
                return SkinInstallResult(false, error = "Invalid skin installation destination")
            }
            val finalized = synchronized(SkinDirectoryLock.monitor) {
                finalizeSkinInstallation(staging, destination)
            }
            if (!finalized.success) {
                return SkinInstallResult(false, error = finalized.error)
            }
            // A newly installed skin becomes visible to skin-dependent renderers.
            SkinRuntimeState.invalidate()
            SkinInstallResult(true, skinId = skinId)
        } catch (error: Exception) {
            SkinInstallResult(false, error = error.message ?: "Skin installation failed")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun validateExtractedSkin(root: File): JSONObject? = runCatching {
        val manifestFile = File(root, "skin.json")
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.optInt("schema_version", -1) == 1)
        val skin = manifest.getJSONObject("skin")
        val id = skin.getString("id")
        require(isValidSkinId(id) && id != "default")
        require(skin.getString("name").isNotBlank() && skin.getString("name").length <= 80)
        require(VERSION_PATTERN.matches(skin.getString("version")))

        val compatibility = manifest.optJSONObject("compatibility")
        require(compatibility?.optBoolean("contains_executable_code", false) != true)
        require(compatibility?.optBoolean("requires_network", false) != true)

        val assets = manifest.optJSONArray("assets")
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val path = assets.getJSONObject(index).getString("file")
                require(isSafeEntryPath(path) && isAllowedSkinPath(path))
                require(File(root, path).isFile)
            }
        }
        val motifs = manifest.optJSONArray("motifs")
        if (motifs != null) {
            for (index in 0 until motifs.length()) {
                val motif = motifs.getJSONObject(index)
                for (key in listOf("file", "file_small")) {
                    if (!motif.isNull(key) && motif.has(key)) {
                        val path = motif.getString(key)
                        require(isSafeEntryPath(path) && path.startsWith("motifs/"))
                        require(isAllowedSkinPath(path) && File(root, path).isFile)
                    }
                }
            }
        }

        root.walkTopDown().filter { it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
            .forEach { image ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(image.absolutePath, options)
                require(options.outWidth in 1..MAX_IMAGE_SIDE && options.outHeight in 1..MAX_IMAGE_SIDE)
                val relativePath = image.relativeTo(root).invariantSeparatorsPath
                expectedImageSize(relativePath)?.let { (width, height) ->
                    require(options.outWidth == width && options.outHeight == height)
                }
            }
        manifest
    }.getOrNull()

    companion object {
        private const val MAX_ARCHIVE_ENTRIES = 400
        private const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 32L * 1024L * 1024L
        private const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
        private const val MAX_COMPRESSION_RATIO = 100L
        private const val MAX_IMAGE_SIDE = 4096
        private val IMAGE_EXTENSIONS = setOf("png", "webp")
        private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$")

        internal fun expectedImageSize(path: String): Pair<Int, Int>? = when {
            Regex("^icons/(tag|ui)/icon_[a-z0-9]+(?:_[a-z0-9]+)*\\.webp$").matches(path) -> 96 to 96
            path == "components/tag_badge/badge_cat.webp" -> 128 to 128
            path == "components/tag_frame/frame_spritesheet.webp" -> 512 to 240
            path == "components/top_frame/topframe_spritesheet.png" -> 134 to 64
            path == "components/top_frame/topframe_spritesheet@2x.png" -> 268 to 128
            path == "components/top_frame/topframe_spritesheet@4x.png" -> 536 to 256
            path.startsWith("motifs/") && path.endsWith("@512.png") -> 512 to 512
            path.startsWith("motifs/") && path.endsWith(".png") -> 1024 to 1024
            else -> null
        }

        internal fun isValidSkinId(id: String): Boolean =
            id.isNotBlank() && id.length <= 255 && id != "default"

        internal fun skinDirectoryName(id: String): String =
            UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString()

        internal fun isLegacySafeSkinId(id: String): Boolean =
            id.length in 1..64 && Regex("^[a-z0-9][a-z0-9._-]*$").matches(id)

        internal fun isSafeEntryPath(path: String): Boolean {
            if (path.isBlank() || path.startsWith("/") || path.contains(92.toChar()) || '\u0000' in path ||
                Regex("^[A-Za-z]:").containsMatchIn(path)
            ) return false
            val parts = path.split('/')
            return parts.all { it.isNotBlank() && it != "." && it != ".." }
        }

        internal fun isAllowedSkinPath(path: String): Boolean {
            if (path == "skin.json") return true
            if (!path.contains('/') && !path.startsWith(".") &&
                (path.endsWith(".md", ignoreCase = true) || path.endsWith(".txt", ignoreCase = true))
            ) return true
            if (path in setOf(
                    "icons/tag/icons_index.json", "icons/tag/icons_index.md",
                    "icons/ui/ui_index.json", "icons/ui/ui_index.md",
                    "components/tag_badge/badge.json",
                    "components/tag_frame/frame_spritesheet.json",
                    "components/top_frame/topframe_atlas.json",
                    "components/top_frame/topframe_formula.json"
                )
            ) return true
            return when {
                path.startsWith("icons/tag/") || path.startsWith("icons/ui/") ->
                    Regex("^icons/(tag|ui)/icon_[a-z0-9]+(?:_[a-z0-9]+)*\\.webp$").matches(path)
                path.startsWith("components/tag_badge/") ->
                    path == "components/tag_badge/badge_cat.webp"
                path.startsWith("components/tag_frame/") ->
                    path == "components/tag_frame/frame_spritesheet.webp"
                path.startsWith("components/top_frame/") ->
                    Regex("^components/top_frame/topframe_spritesheet(?:@2x|@4x)?\\.png$").matches(path)
                path.startsWith("motifs/") ->
                    Regex("^motifs/motif_[a-z0-9]+(?:_[a-z0-9]+)*(?:@512)?\\.png$").matches(path)
                else -> false
            }
        }
    }
}
