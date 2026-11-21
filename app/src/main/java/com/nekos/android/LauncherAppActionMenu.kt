package com.nekos.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
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

@Composable
internal fun LauncherAppActionMenuContent(
    app: InstalledApp,
    isFavorite: Boolean,
    isInteractive: Boolean,
    onAppInfo: () -> Unit,
    onFavorite: () -> Unit,
    onUninstall: () -> Unit
) {
    Text(
        app.label,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        color = Ink,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
    PopupAction("App info", Icons.Filled.Info, enabled = isInteractive, onClick = onAppInfo)
    PopupAction(
        if (isFavorite) "Remove from favorite" else "Add to favorite",
        if (isFavorite) Icons.Filled.Check else Icons.Filled.Star,
        enabled = isInteractive,
        onClick = onFavorite
    )
    PopupAction("Uninstall", Icons.Filled.Delete, enabled = isInteractive, onClick = onUninstall)
    if (!isInteractive) {
        Text(
            "Release to choose",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = Muted,
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
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Ink else Muted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            color = if (enabled) Ink else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
