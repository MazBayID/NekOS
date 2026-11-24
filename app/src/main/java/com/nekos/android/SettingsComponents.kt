package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BuiltInWallpaperPreview(
    resourceId: Int,
    label: String
) {
    val context = LocalContext.current
    val previewState = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(resourceId) {
        val loadedPreview = withContext(Dispatchers.IO) {
            loadBuiltInHomeWallpaper(context, resourceId, maxDimension = 480)
        }
        previewState.value = loadedPreview
    }
    val preview = previewState.value

    if (preview != null) {
        Image(
            bitmap = preview,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 60.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SettingsInk.copy(alpha = 0.08f))
        )
    }
}

@Composable
internal fun SettingsNavigationCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    iconColorArgb: Long,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(SettingsCardShape)
            .background(SettingsInk.copy(alpha = 0.025f))
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ThemedUiArtworkImage(
            resourceId = iconRes,
            colorArgb = iconColorArgb,
            modifier = Modifier.size(23.dp)
        )
        Text(title, color = SettingsInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
internal fun SettingsActionCard(
    title: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(SettingsInk.copy(alpha = 0.025f))
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(action, color = SettingsAccent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
internal fun SettingsSectionTitle(label: String) {
    Text(
        label,
        color = SettingsMuted,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 3.dp, bottom = 0.dp)
    )
}
