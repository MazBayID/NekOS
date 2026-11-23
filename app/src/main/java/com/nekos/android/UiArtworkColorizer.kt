package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LauncherUiColorOption(val name: String, val argb: Long)

internal const val DEFAULT_LAUNCHER_UI_COLOR = 0xFF114B56L

internal val LauncherUiColorOptions = listOf(
    LauncherUiColorOption("Ocean", 0xFF114B56L),
    LauncherUiColorOption("Blue", 0xFF2563EBL),
    LauncherUiColorOption("Purple", 0xFF7C3AEDL),
    LauncherUiColorOption("Rose", 0xFFE11D48L),
    LauncherUiColorOption("Red", 0xFFDC2626L),
    LauncherUiColorOption("Orange", 0xFFEA580CL),
    LauncherUiColorOption("Amber", 0xFFD97706L),
    LauncherUiColorOption("Green", 0xFF16A34AL),
    LauncherUiColorOption("Mint", 0xFF0D9488L),
    LauncherUiColorOption("Slate", 0xFF64748BL),
    LauncherUiColorOption("Silver", 0xFFA1A1AAL),
    LauncherUiColorOption("Dark", 0xFF27272AL)
)

internal fun normalizeLauncherUiColor(color: Long?): Long =
    LauncherUiColorOptions.firstOrNull { it.argb == color }?.argb
        ?: DEFAULT_LAUNCHER_UI_COLOR

/**
 * Recolors the teal artwork layer while preserving neutral glyphs, transparency,
 * antialiasing, and the original light/shadow variation.
 */
internal object UiArtworkColorizer {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int =
            (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun load(
        context: Context,
        @DrawableRes resourceId: Int,
        targetColorArgb: Long,
        maxDimensionPx: Int = 512
    ): Bitmap? {
        val color = normalizeLauncherUiColor(targetColorArgb)
        val targetSize = maxDimensionPx.coerceAtLeast(1)
        val key = "$resourceId:${color.toString(16)}:$targetSize"
        cache.get(key)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resourceId, bounds)
        val sourceMaxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        if (sourceMaxDimension <= 0) return null

        var sampleSize = 1
        while (sourceMaxDimension / sampleSize > targetSize) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: return null
        val result = recolor(source, color)
        if (result !== source) source.recycle()
        cache.put(key, result)
        return result
    }

    private fun recolor(source: Bitmap, targetColorArgb: Long): Bitmap {
        if (targetColorArgb == DEFAULT_LAUNCHER_UI_COLOR) return source

        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val width = output.width
        val height = output.height
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)

        val sourceHsv = FloatArray(3)
        val targetHsv = FloatArray(3)
        val pixelHsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.rgb(0x11, 0x4B, 0x56), sourceHsv)
        AndroidColor.colorToHSV(targetColorArgb.toInt(), targetHsv)
        val sourceValue = sourceHsv[2].coerceAtLeast(0.01f)
        val sourceSaturation = sourceHsv[1].coerceAtLeast(0.01f)

        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = AndroidColor.alpha(pixel)
            if (alpha == 0) continue

            AndroidColor.colorToHSV(pixel, pixelHsv)
            val rawHueDifference = abs(pixelHsv[0] - sourceHsv[0])
            val hueDifference = minOf(rawHueDifference, 360f - rawHueDifference)
            if (hueDifference <= 28f && pixelHsv[1] >= 0.12f) {
                pixelHsv[0] = targetHsv[0]
                pixelHsv[1] = (
                    pixelHsv[1] * targetHsv[1] / sourceSaturation
                ).coerceIn(0f, 1f)
                pixelHsv[2] = (
                    pixelHsv[2] * targetHsv[2] / sourceValue
                ).coerceIn(0f, 1f)
                pixels[index] = AndroidColor.HSVToColor(alpha, pixelHsv)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}

@Composable
internal fun rememberThemedUiArtwork(
    @DrawableRes resourceId: Int,
    colorArgb: Long,
    maxDimensionPx: Int = 512
): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(resourceId, colorArgb, maxDimensionPx) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(resourceId, colorArgb, maxDimensionPx) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                UiArtworkColorizer.load(context, resourceId, colorArgb, maxDimensionPx)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
internal fun ThemedUiArtworkImage(
    @DrawableRes resourceId: Int,
    colorArgb: Long,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    maxDimensionPx: Int = 512
) {
    val bitmap = rememberThemedUiArtwork(resourceId, colorArgb, maxDimensionPx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier
        )
    }
}


@Composable
internal fun DialogFrameTop(colorArgb: Long, modifier: Modifier = Modifier) {
    val bitmap = rememberThemedUiArtwork(
        R.drawable.dialog_frame_top,
        colorArgb,
        maxDimensionPx = 512
    )
    if (bitmap != null) {
        Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
            val scale = size.height / 64f
            val endWidth = 64f * scale
            val crestWidth = 96f * scale
            val lineBaseWidth = 144f * scale
            val extraLineWidth = ((size.width - endWidth * 2f - crestWidth - lineBaseWidth * 2f) / 2f)
                .coerceAtLeast(0f)
            val leftLineWidth = lineBaseWidth + extraLineWidth
            val rightLineWidth = lineBaseWidth + extraLineWidth
            var destinationX = 0f

            fun drawSlice(sourceX: Int, sourceWidth: Int, destinationWidth: Float) {
                val width = destinationWidth.roundToInt().coerceAtLeast(1)
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(sourceX, 0),
                    srcSize = IntSize(sourceWidth, 64),
                    dstOffset = IntOffset(destinationX.roundToInt(), 0),
                    dstSize = IntSize(width, size.height.roundToInt().coerceAtLeast(1))
                )
                destinationX += destinationWidth
            }

            drawSlice(0, 64, endWidth)
            drawSlice(64, 144, leftLineWidth)
            drawSlice(208, 96, crestWidth)
            drawSlice(304, 144, rightLineWidth)
            drawSlice(448, 64, endWidth)
        }
    }
}

@Composable
internal fun DialogTitleWithFrame(
    colorArgb: Long,
    content: @Composable () -> Unit
) {
    content()
}
