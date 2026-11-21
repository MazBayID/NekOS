package com.nekos.android

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ActionMenuInk = Color(0xFF34332F)
private val ActionMenuMuted = Color(0xFF8B8982)

@Composable
internal fun LauncherAppActionMenuContent(
    app: InstalledApp,
    isFavorite: Boolean,
    isInteractive: Boolean,
    onAppInfo: () -> Unit,
    onFavorite: () -> Unit,
    onUninstall: () -> Unit
) {
    var appIcon by remember(app.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.id) {
        appIcon = withContext(Dispatchers.Default) {
            app.icon.toBitmap(72, 72).asImageBitmap()
        }
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
    PopupAction("App info", Icons.Filled.Info, enabled = isInteractive, onClick = onAppInfo)
    PopupAction(
        if (isFavorite) "Remove from favorite" else "Add to favorite",
        if (isFavorite) Icons.Filled.Check else Icons.Filled.Star,
        enabled = isInteractive,
        onClick = onFavorite
    )
    PopupAction(
        if (app.isInCurrentProfile) "Uninstall" else "Manage app",
        Icons.Filled.Delete,
        enabled = isInteractive,
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) ActionMenuInk else ActionMenuMuted,
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
