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
import org.json.JSONObject
import java.io.File

internal data class DialogMotifAsset(
    val relativePath: String,
    val size: Int
)

/**
 * Resolves the active skin's declared motif. The bundled paw motif remains the
 * fallback for the built-in skin and for custom skins without a usable motif.
 */
internal fun resolveDialogMotifAsset(skinDirectory: File?): DialogMotifAsset {
    val fallback = DialogMotifAsset("motifs/motif_paw@512.png", 512)
    if (skinDirectory == null) return fallback

    return runCatching {
        val root = skinDirectory.canonicalFile
        val manifestFile = File(root, "skin.json")
        if (!manifestFile.isFile) return fallback

        val motifs = JSONObject(manifestFile.readText(Charsets.UTF_8))
            .optJSONArray("motifs")
        val motif = motifs?.optJSONObject(0) ?: return fallback

        fun existingMotif(key: String, isSmall: Boolean): String? {
            if (!motif.has(key) || motif.isNull(key)) return null
            val path = motif.optString(key)
            if (!MOTIF_PATH_PATTERN.matches(path)) return null
            if (path.endsWith("@512.png") != isSmall) return null
            val candidate = File(root, path).canonicalFile
            if (!candidate.path.startsWith(root.path + File.separator) || !candidate.isFile) {
                return null
            }
            return path
        }

        existingMotif("file_small", true)?.let { return DialogMotifAsset(it, 512) }
        existingMotif("file", false)?.let { return DialogMotifAsset(it, 1024) }
        fallback
    }.getOrDefault(fallback)
}

private val MOTIF_PATH_PATTERN =
    Regex("^motifs/motif_[a-z0-9]+(?:_[a-z0-9]+)*(?:@512)?\\.png$")

@Composable
internal fun rememberDialogMotifBrush(): Brush? {
    val context = LocalContext.current
    val tilePx = with(LocalDensity.current) { 320.dp.roundToPx().coerceAtLeast(64) }
    val resolver = remember(context) { SkinAssetResolver(context) }
    val skinGeneration = SkinRuntimeState.generation
    var brush by remember(resolver, tilePx, skinGeneration) { mutableStateOf<Brush?>(null) }

    LaunchedEffect(resolver, tilePx, skinGeneration) {
        brush = withContext(Dispatchers.IO) {
            runCatching {
                val skinDirectory = SkinRepository.activeSkinDirectory(context)
                val motif = resolveDialogMotifAsset(skinDirectory)
                val source = resolver.loadBitmap(
                    motif.relativePath,
                    expectedWidth = motif.size,
                    expectedHeight = motif.size
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
