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
    private const val MAX_DOWNLOAD_BYTES = 32L * 1024L * 1024L

    fun downloadAndInstall(context: Context, onProgress: (Int) -> Unit = {}): SkinInstallResult {
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
            FileInputStream(archive).use { SkinInstaller(context).install(it) }
        } catch (error: Exception) {
            SkinInstallResult(false, error = error.message ?: "Unable to download official skin")
        } finally {
            connection?.disconnect()
            archive.delete()
        }
    }
}
