package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
private fun TagAppIcon(app: InstalledApp, iconBitmapCache: AppIconBitmapCache) {
    var bitmap by remember(app.id, iconBitmapCache.generation) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(app.id, iconBitmapCache.generation) {
        bitmap = iconBitmapCache.getOrLoad(app)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFE7E1D6))
        )
    }
}

@Composable
fun TagAppsDialog(
    tag: NekTag,
    apps: List<InstalledApp>,
    iconBitmapCache: AppIconBitmapCache,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onLaunch: (InstalledApp) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val assignedApps = remember(tag.appPackages, apps) {
        val byPackage = apps.associateBy { it.id }
        tag.appPackages.mapNotNull(byPackage::get)
    }
    val tagColor = Color(tag.colorArgb.toInt())

    NekOSAlertDialog(
        topFrameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(tagColor))
                    Text(
                        text = tag.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "${assignedApps.size} ${if (assignedApps.size == 1) "app" else "apps"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8B8982)
                )
                if (assignedApps.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFF4F1EA))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            if (tag.appPackages.isEmpty()) "No apps in this tag" else "No apps currently available",
                            color = Color(0xFF34332F)
                        )
                        Text(
                            if (tag.appPackages.isEmpty()) {
                                "Edit this tag to add applications."
                            } else {
                                "Saved app assignments are kept while their Android profile is unavailable."
                            },
                            color = Color(0xFF8B8982),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(assignedApps, key = { it.id }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onLaunch(app) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TagAppIcon(app, iconBitmapCache)
                                Text(
                                    app.label,
                                    color = Color(0xFF34332F),
                                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit tag") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun TagActionsDialog(
    tag: NekTag,
    movePageLabel: String?,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onMovePage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    NekOSAlertDialog(
        topFrameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { Text(tag.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit tag")
                }
                if (movePageLabel != null) {
                    TextButton(onClick = onMovePage, modifier = Modifier.fillMaxWidth()) {
                        Text(movePageLabel)
                    }
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete tag", color = Color(0xFFB3261E))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
