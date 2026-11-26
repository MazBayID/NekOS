package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun TagAppIcon(app: InstalledApp, iconBitmapCache: AppIconBitmapCache) {
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
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp))
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(assignedApps, key = { it.id }) { app ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onLaunch(app) }
                                    .padding(horizontal = 3.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                TagAppIcon(app, iconBitmapCache)
                                Text(
                                    app.label,
                                    color = Color(0xFF34332F),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
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
        shape = RoundedCornerShape(14.dp),
        dialogWidth = 280.dp,
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = {
            Text(
                tag.name,
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(onClick = onEdit)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit tag",
                        tint = Color(0xFF746657),
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Edit", color = Color(0xFF34332F), style = MaterialTheme.typography.labelSmall)
                }
                if (movePageLabel != null) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .clickable(onClick = onMovePage)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = movePageLabel,
                            tint = Color(0xFF746657),
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Move", color = Color(0xFF34332F), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Delete tag",
                        tint = Color(0xFFB3261E),
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Delete", color = Color(0xFFB3261E), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}
