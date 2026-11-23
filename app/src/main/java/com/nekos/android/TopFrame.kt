package com.nekos.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
internal fun TopFrame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val images by produceState<TopFrameImages?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val sheet = context.assets.open(
                    "topframe_spritesheet@4x.png"
                ).use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: error("Unable to decode topframe spritesheet")

                try {
                    fun crop(x: Int, width: Int): ImageBitmap =
                        Bitmap.createBitmap(sheet, x * 4, 0, width * 4, 64 * 4)
                            .asImageBitmap()

                    TopFrameImages(
                        leftCap = crop(0, 32),
                        barTile = crop(34, 4),
                        head = crop(40, 60),
                        rightCap = crop(102, 32)
                    )
                } finally {
                    sheet.recycle()
                }
            }.getOrNull()
        }
    }

    val frame = images ?: return
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp)
    ) {
        Image(
            bitmap = frame.leftCap,
            contentDescription = null,
            modifier = Modifier.width(32.dp).height(64.dp),
            contentScale = ContentScale.FillBounds
        )
        Image(
            bitmap = frame.barTile,
            contentDescription = null,
            modifier = Modifier.weight(1f).height(64.dp),
            contentScale = ContentScale.FillBounds
        )
        Image(
            bitmap = frame.head,
            contentDescription = null,
            modifier = Modifier.width(60.dp).height(64.dp),
            contentScale = ContentScale.FillBounds
        )
        Image(
            bitmap = frame.barTile,
            contentDescription = null,
            modifier = Modifier.weight(1f).height(64.dp),
            contentScale = ContentScale.FillBounds
        )
        Image(
            bitmap = frame.rightCap,
            contentDescription = null,
            modifier = Modifier.width(32.dp).height(64.dp),
            contentScale = ContentScale.FillBounds
        )
    }
}
