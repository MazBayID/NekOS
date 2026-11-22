package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ActionMenuInk = Color(0xFF34332F)
private val ActionMenuMuted = Color(0xFF8B8982)

@Composable
internal fun LauncherAppActionMenuContent(
    app: InstalledApp,
    iconBitmapCache: AppIconBitmapCache,
    isInteractive: Boolean,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFavorite: (() -> Unit)? = null,
    launcherSurface: Color
) {
    var appIcon by remember(app.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.id) {
        appIcon = iconBitmapCache.getOrLoad(app)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (appIcon != null) {
            Image(appIcon!!, contentDescription = null, modifier = Modifier.size(30.dp))
        }
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            color = ActionMenuInk,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
    val optionSurface = lerp(launcherSurface, Color.White, 0.48f)
    PopupAction("App info", Icons.Filled.Info, enabled = isInteractive, surface = optionSurface, onClick = onAppInfo)
    if (onRemoveFavorite != null) {
        PopupAction("Remove from Favorites", Icons.Filled.Delete, enabled = isInteractive, surface = optionSurface, onClick = onRemoveFavorite)
    }
    PopupAction(
        if (app.isInCurrentProfile) "Uninstall" else "Manage app",
        Icons.Filled.Delete,
        enabled = isInteractive,
        surface = optionSurface,
        iconTint = if (app.isInCurrentProfile) Color(0xFFB3261E) else null,
        onClick = onUninstall
    )
    if (!isInteractive) {
        Text(
            "Release to choose",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = ActionMenuMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PopupAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    iconTint: Color? = null,
    surface: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (!enabled) ActionMenuMuted else iconTint ?: ActionMenuInk,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            color = if (enabled) ActionMenuInk else ActionMenuMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
