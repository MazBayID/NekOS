package com.nekos.android

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val HOME_WALLPAPER_FILE = "home_wallpaper"
private const val HOME_WALLPAPER_TEMP_FILE = "home_wallpaper.tmp"
private const val HOME_WALLPAPER_MAX_DIMENSION = 2048
private const val HOME_WALLPAPER_MAX_FILE_BYTES = 40L * 1024L * 1024L

internal fun isValidHomeWallpaperDimensions(width: Int, height: Int): Boolean =
    width > 0 &&
        height > 0 &&
        width.toLong() * height.toLong() <= 100_000_000L

internal fun saveHomeWallpaper(context: Context, source: Uri): String? = runCatching {
    val target = File(context.filesDir, HOME_WALLPAPER_FILE)
    val temporary = File(context.filesDir, HOME_WALLPAPER_TEMP_FILE)

    context.contentResolver.openInputStream(source)?.use { input ->
        temporary.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            var totalBytes = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                totalBytes += count
                check(totalBytes <= HOME_WALLPAPER_MAX_FILE_BYTES) {
                    "Selected image exceeds the size limit"
                }
                output.write(buffer, 0, count)
            }
        }
    } ?: error("Unable to open selected image")

    check(temporary.length() > 0L) { "Selected image is empty" }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(temporary.absolutePath, bounds)
    check(isValidHomeWallpaperDimensions(bounds.outWidth, bounds.outHeight)) {
        "Selected file is not a supported image or its dimensions are too large"
    }

    Files.move(
        temporary.toPath(),
        target.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    )
    target.absolutePath
}.getOrElse {
    File(context.filesDir, HOME_WALLPAPER_TEMP_FILE).delete()
    null
}

internal fun loadHomeWallpaper(context: Context, path: String): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val file = File(path)
        require(file.parentFile?.canonicalFile == context.filesDir.canonicalFile)
        require(file.name == HOME_WALLPAPER_FILE && file.isFile)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = minOf(
                    1f,
                    HOME_WALLPAPER_MAX_DIMENSION.toFloat() / maxOf(width, height)
                )
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }.asImageBitmap()
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)

            var sampleSize = 1
            while (
                maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) >
                HOME_WALLPAPER_MAX_DIMENSION
            ) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
            )?.asImageBitmap()
        }
    }.getOrNull()

internal fun clearHomeWallpaper(context: Context) {
    File(context.filesDir, HOME_WALLPAPER_FILE).delete()
    File(context.filesDir, HOME_WALLPAPER_TEMP_FILE).delete()
}
