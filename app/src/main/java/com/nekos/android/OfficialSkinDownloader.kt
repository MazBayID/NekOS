package com.nekos.android

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the official, data-only Inferno Phoenix archive and installs it
 * through the same validator used by user-imported skins.
 */
internal object OfficialSkinDownloader {
    private const val DOWNLOAD_URL = "https://raw.githubusercontent.com/MazBayID/NekOS/main/nekos_skin_kit/official_skin/skin_inferno_phoenix.zip"
    private const val EXPECTED_SHA256 = "c49003eab26be7cea6c0a6cf32c4897b4e79aa89b1a18b3e526a00054da338f1"
    internal const val OFFICIAL_VERSION = "1.0.0"

    internal fun isUpdateAvailable(installedVersion: String?): Boolean =
        installedVersion != null && isNewerVersion(OFFICIAL_VERSION, installedVersion)

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        fun parts(version: String): List<Long>? =
            version.substringBefore("-").substringBefore("+").split(".")
                .map { it.toLongOrNull() ?: return null }
        val candidateParts = parts(candidate) ?: return false
        val currentParts = parts(current) ?: return false
        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val comparison = candidateParts.getOrElse(index) { 0L }
                .compareTo(currentParts.getOrElse(index) { 0L })
            if (comparison != 0) return comparison > 0
        }
        val candidatePre = candidate.substringBefore("+").substringAfter("-", "")
        val currentPre = current.substringBefore("+").substringAfter("-", "")
        if (candidatePre.isEmpty()) return currentPre.isNotEmpty()
        if (currentPre.isEmpty()) return false
        val candidateIds = candidatePre.split(".")
        val currentIds = currentPre.split(".")
        for (index in 0 until minOf(candidateIds.size, currentIds.size)) {
            val left = candidateIds[index]
            val right = currentIds[index]
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison > 0
        }
        return candidateIds.size > currentIds.size
    }
    private const val MAX_DOWNLOAD_BYTES = 32L * 1024L * 1024L

    fun downloadAndInstall(
        context: Context,
        replaceExisting: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): SkinInstallResult {
        val archive = File(context.cacheDir, "official-inferno-phoenix.zip")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return SkinInstallResult(false, error = "Download failed (HTTP ${connection.responseCode})")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_DOWNLOAD_BYTES) {
                return SkinInstallResult(false, error = "Skin archive is larger than 32 MB")
            }
            connection.inputStream.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    onProgress(if (declaredLength > 0L) 0 else -1)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DOWNLOAD_BYTES) {
                            return SkinInstallResult(false, error = "Skin archive is larger than 32 MB")
                        }
                        output.write(buffer, 0, count)
                        if (declaredLength > 0L) {
                            onProgress(((total * 100L) / declaredLength).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            onProgress(100)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (!digest.equals(EXPECTED_SHA256, ignoreCase = true)) {
                return SkinInstallResult(false, error = "Skin download failed integrity verification")
            }
            FileInputStream(archive).use { SkinInstaller(context).install(it, replaceExisting = replaceExisting) }
        } catch (error: Exception) {
            SkinInstallResult(false, error = error.message ?: "Unable to download official skin")
        } finally {
            connection?.disconnect()
            archive.delete()
        }
    }
}
