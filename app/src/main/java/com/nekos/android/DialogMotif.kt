package com.nekos.android

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberDialogMotifBrush(): Brush? {
    val context = LocalContext.current
    val tilePx = with(LocalDensity.current) { 320.dp.roundToPx().coerceAtLeast(64) }
    val resolver = remember(context) { SkinAssetResolver(context) }
    var brush by remember(resolver, tilePx) { mutableStateOf<Brush?>(null) }

    LaunchedEffect(resolver, tilePx) {
        brush = withContext(Dispatchers.IO) {
            runCatching {
                val source = resolver.loadBitmap(
                    "motifs/motif_paw@512.png",
                    expectedWidth = 512,
                    expectedHeight = 512
                ) ?: return@runCatching null
                val scaled = Bitmap.createScaledBitmap(
                    source, tilePx, tilePx, true
                ).asImageBitmap()
                ShaderBrush(ImageShader(scaled, TileMode.Repeated, TileMode.Repeated))
            }.getOrNull()
        }
    }
    return brush
}
