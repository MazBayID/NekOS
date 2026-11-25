package com.nekos.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TopFrameImages(
    val leftCap: ImageBitmap,
    val barTile: ImageBitmap,
    val head: ImageBitmap,
    val rightCap: ImageBitmap
)

@Composable
internal fun TopFrame(
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = remember(context) { SkinAssetResolver(context) }
    val skinGeneration = SkinRuntimeState.generation
    var images by remember(resolver, skinGeneration) { mutableStateOf<TopFrameImages?>(null) }

    LaunchedEffect(resolver, skinGeneration) {
        val loadedImages = withContext(Dispatchers.IO) {
            runCatching {
                val sheet = resolver.loadBitmap(
                    "components/top_frame/topframe_spritesheet@4x.png",
                    expectedWidth = 536,
                    expectedHeight = 256
                ) ?: error("Unable to decode skin TopFrame spritesheet")
                fun crop(x: Int, width: Int): ImageBitmap =
                    Bitmap.createBitmap(sheet, x * 4, 0, width * 4, 64 * 4).asImageBitmap()
                TopFrameImages(crop(0, 32), crop(34, 4), crop(40, 60), crop(102, 32))
            }.getOrNull()
        }
        images = loadedImages
    }

    val frame = images ?: return
    val tint = ColorFilter.tint(Color(colorArgb.toInt()), BlendMode.SrcIn)
    Row(modifier = modifier.fillMaxWidth().height(64.dp)) {
        Image(frame.leftCap, null, Modifier.width(32.dp).height(64.dp),
            contentScale = ContentScale.FillBounds, colorFilter = tint)
        Image(frame.barTile, null, Modifier.weight(1f).height(64.dp),
            contentScale = ContentScale.FillBounds, colorFilter = tint)
        Image(frame.head, null, Modifier.width(60.dp).height(64.dp),
            contentScale = ContentScale.FillBounds, colorFilter = tint)
        Image(frame.barTile, null, Modifier.weight(1f).height(64.dp),
            contentScale = ContentScale.FillBounds, colorFilter = tint)
        Image(frame.rightCap, null, Modifier.width(32.dp).height(64.dp),
            contentScale = ContentScale.FillBounds, colorFilter = tint)
    }
}
