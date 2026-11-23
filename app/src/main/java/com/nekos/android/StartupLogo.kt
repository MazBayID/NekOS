package com.nekos.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STARTUP_LOGO_MAX_DIMENSION_PX = 1280

@Composable
internal fun StartupLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var logo by remember(context) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(context) {
        logo = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.nekos_text_logo,
                    bounds
                )
                val sourceMaxDimension = maxOf(bounds.outWidth, bounds.outHeight)
                if (sourceMaxDimension <= 0) {
                    null
                } else {
                    var sampleSize = 1
                    while (sourceMaxDimension / sampleSize > STARTUP_LOGO_MAX_DIMENSION_PX) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.nekos_text_logo,
                        options
                    )?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    logo?.let {
        Image(
            bitmap = it,
            contentDescription = "NekOS",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}
