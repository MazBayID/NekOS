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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tag.name) },
        text = {
            if (assignedApps.isEmpty()) {
                Text("No apps assigned yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(assignedApps, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onLaunch(app) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap(64, 64).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label)
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
                                .clickable { selectedColor = color.toArgb().toLong() }
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color.toArgb().toLong()) {
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
                        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                            initialValue = null, key1 = icon.id
                        ) {
                            value = withContext(Dispatchers.IO) {
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
