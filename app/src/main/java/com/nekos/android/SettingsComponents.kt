package com.nekos.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
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
    surface: Color,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(SettingsCardShape)
            .background(surface.copy(alpha = 0.5f))
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
        Text(title, color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}


@Composable
internal fun SettingsListRow(
    title: String,
    iconFileName: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF4F1EA).copy(alpha = 0.5f))
                .border(1.dp, SettingsBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 7.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkinUiIcon(
                fileName = iconFileName,
                tint = SettingsAccent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title,
                color = SettingsInk,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (!trailing.isNullOrBlank()) {
                Text(trailing, color = SettingsMuted, fontSize = 11.sp)
            }
            SkinUiIcon(
                fileName = "icon_chevron_right.webp",
                tint = SettingsMuted,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


@Composable
internal fun SettingsBrushSeparator(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val centerY = size.height / 2f
        val halfThickness = 2.8.dp.toPx() / 2f
        val brushShape = Path().apply {
            moveTo(0f, centerY)
            cubicTo(size.width * 0.18f, centerY - halfThickness * 0.18f,
                size.width * 0.34f, centerY - halfThickness * 0.78f,
                size.width * 0.5f, centerY - halfThickness)
            cubicTo(size.width * 0.66f, centerY - halfThickness * 0.78f,
                size.width * 0.82f, centerY - halfThickness * 0.18f,
                size.width, centerY)
            cubicTo(size.width * 0.82f, centerY + halfThickness * 0.18f,
                size.width * 0.66f, centerY + halfThickness * 0.78f,
                size.width * 0.5f, centerY + halfThickness)
            cubicTo(size.width * 0.34f, centerY + halfThickness * 0.78f,
                size.width * 0.18f, centerY + halfThickness * 0.18f,
                0f, centerY)
            close()
        }
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to SettingsBorder.copy(alpha = 0f),
                0.18f to SettingsBorder.copy(alpha = 0.38f),
                0.5f to SettingsBorder,
                0.82f to SettingsBorder.copy(alpha = 0.38f),
                1f to SettingsBorder.copy(alpha = 0f)
            )
        )
        drawPath(path = brushShape, brush = brush)
    }
}

@Composable
internal fun SettingsActionCard(
    title: String,
    action: String,
    iconFileName: String,
    surface: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(surface)
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkinUiIcon(
            fileName = iconFileName,
            tint = SettingsAccent,
            contentDescription = null,
            modifier = Modifier.size(19.dp)
        )
        Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.weight(1f))
        Text(action, color = SettingsAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
internal fun SettingsSectionTitle(label: String) {
    Text(
        label,
        color = SettingsMuted,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 3.dp, bottom = 0.dp)
    )
}
