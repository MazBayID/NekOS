package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TagColors = listOf(
    Color(0xFFE7E1D6), Color(0xFFD8E6D7), Color(0xFFDCE4F3),
    Color(0xFFF2DCD6), Color(0xFFE9DDF1), Color(0xFFF1E7C9)
)

@Composable
fun TagAppsDialog(
    tag: NekTag,
    apps: List<InstalledApp>,
    onLaunch: (InstalledApp) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val assignedApps = remember(tag.appPackages, apps) {
        val byPackage = apps.associateBy { it.packageName }
        tag.appPackages.mapNotNull(byPackage::get)
    }
    val tagColor = Color(tag.colorArgb.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(tagColor)
                )
                Text(
                    text = tag.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tagColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("＋", color = Color(0xFF34332F), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "No apps in this tag yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF34332F)
                        )
                        Text(
                            "Edit this tag to add your apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B8982)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(assignedApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF7F5F0))
                                    .clickable { onLaunch(app) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap(72, 72).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                )
                                Text(
                                    text = app.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF34332F),
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tag.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit tag")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete tag", color = Color(0xFFB3261E))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun TagManagementDialog(
    tag: NekTag?,
    apps: List<InstalledApp>,
    onSave: (NekTag) -> Unit,
    onDelete: (NekTag) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val catalog = remember(context) {
        runCatching { IconCatalogRepository(context).loadCatalog() }.getOrDefault(emptyList())
    }
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var selectedIcon by remember(tag?.id) { mutableStateOf(tag?.iconId) }
    var selectedColor by remember(tag?.id) { mutableLongStateOf(tag?.colorArgb ?: 0xFFE7E1D6) }
    var selectedPackages by remember(tag?.id) { mutableStateOf(tag?.appPackages?.toSet().orEmpty()) }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "Create tag" else "Edit tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagColors.forEach { color ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color.toArgb().toLong() and 0xFFFFFFFFL }
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == (color.toArgb().toLong() and 0xFFFFFFFFL)) {
                                Text("✓", color = Color(0xFF34332F))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Icon: ${selectedIcon?.let { " #$it" } ?: "None"}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showIconPicker = true }) { Text("Choose") }
                    TextButton(onClick = { selectedIcon = null }) { Text("Clear") }
                }
                Text("Apps (${selectedPackages.size})", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedPackages = if (app.packageName in selectedPackages) {
                                    selectedPackages - app.packageName
                                } else selectedPackages + app.packageName
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = { checked ->
                                    selectedPackages = if (checked) selectedPackages + app.packageName
                                    else selectedPackages - app.packageName
                                }
                            )
                            Text(app.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onSave(
                        (tag ?: NekTag(name = name.trim())).copy(
                            name = name.trim(),
                            iconId = selectedIcon,
                            colorArgb = selectedColor,
                            appPackages = selectedPackages.toList()
                        ).normalized()
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (tag != null) TextButton(onClick = { onDelete(tag) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showIconPicker) {
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            title = { Text("Choose tag icon") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(300.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(catalog, key = { it.id }) { icon ->
                        var bitmap by remember(icon.id) {
                            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
                        }
                        LaunchedEffect(icon.id) {
                            bitmap = withContext(Dispatchers.IO) {
                                runCatching { IconCatalogRepository(context).loadBitmap(icon)?.asImageBitmap() }
                                    .getOrNull()
                            }
                        }
                        Box(
                            Modifier.aspectRatio(1f).padding(4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF0EEE8))
                                .clickable {
                                    selectedIcon = icon.id
                                    showIconPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) Image(bitmap!!, contentDescription = icon.name, modifier = Modifier.fillMaxSize().padding(5.dp))
                            else Text(icon.id.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIconPicker = false }) { Text("Done") } }
        )
    }
}
