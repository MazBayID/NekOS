package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TagColors = listOf(
    Color(0xFFE7E1D6), Color(0xFFD8E6D7), Color(0xFFDCE4F3),
    Color(0xFFF2DCD6), Color(0xFFE9DDF1), Color(0xFFF1E7C9)
)

private val TagIconFrameColors = listOf(
    "Ocean" to 0xFF114B56L,
    "Blue" to 0xFF2563EBL,
    "Purple" to 0xFF7C3AEDL,
    "Rose" to 0xFFE11D48L,
    "Red" to 0xFFDC2626L,
    "Orange" to 0xFFEA580CL,
    "Amber" to 0xFFD97706L,
    "Green" to 0xFF16A34AL,
    "Mint" to 0xFF0D9488L,
    "Slate" to 0xFF64748BL,
    "Silver" to 0xFFA1A1AAL,
    "Dark" to 0xFF27272AL
)

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
        frameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = {
            DialogTitleWithFrame(launcherUiColorArgb) {
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
                        items(assignedApps, key = { it.id }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF7F5F0))
                                    .clickable { onLaunch(app) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TagAppIcon(app = app, iconBitmapCache = iconBitmapCache)
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
    movePageLabel: String?,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onMovePage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    NekOSAlertDialog(
        frameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { DialogTitleWithFrame(launcherUiColorArgb) { Text(tag.name) } },
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

@Composable
fun TagManagementDialog(
    tag: NekTag?,
    apps: List<InstalledApp>,
    iconBitmapCache: AppIconBitmapCache,
    iconCatalogRepository: IconCatalogRepository,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onSave: (NekTag) -> Unit,
    onDelete: (NekTag) -> Unit,
    onDismiss: () -> Unit
) {
    val catalog = remember(iconCatalogRepository) {
        runCatching { iconCatalogRepository.loadCatalog() }.getOrDefault(emptyList())
    }
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var selectedIcon by remember(tag?.id) { mutableStateOf(tag?.iconId) }
    var selectedColor by remember(tag?.id) { mutableLongStateOf(tag?.colorArgb ?: 0xFFE7E1D6) }
    var selectedIconFrameColor by remember(tag?.id) {
        mutableLongStateOf(tag?.iconFrameColorArgb ?: DEFAULT_TAG_ICON_FRAME_COLOR)
    }
    var selectedPackages by remember(tag?.id) { mutableStateOf(tag?.appPackages?.toSet().orEmpty()) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showIconColorPicker by remember { mutableStateOf(false) }
    var appSearch by remember(tag?.id) { mutableStateOf("") }
    val selectedIconEntry = remember(catalog, selectedIcon) {
        catalog.firstOrNull { it.id == selectedIcon }
    }
    var selectedIconBitmap by remember(selectedIcon, selectedIconFrameColor) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(selectedIcon, selectedIconEntry, selectedIconFrameColor) {
        selectedIconBitmap = withContext(Dispatchers.IO) {
            selectedIconEntry?.let {
                runCatching {
                    iconCatalogRepository.loadBitmap(it, selectedIconFrameColor)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val filteredApps = remember(apps, appSearch) {
        if (appSearch.isBlank()) apps
        else apps.filter { it.label.contains(appSearch.trim(), ignoreCase = true) ||
            it.packageName.contains(appSearch.trim(), ignoreCase = true) }
    }

    NekOSAlertDialog(
        frameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { DialogTitleWithFrame(launcherUiColorArgb) { Text(if (tag == null) "Create tag" else "Edit tag") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Tag name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF34332F),
                        unfocusedTextColor = Color(0xFF34332F),
                        focusedContainerColor = Color(0xFFF4F1EA),
                        unfocusedContainerColor = Color(0xFFF4F1EA),
                        focusedIndicatorColor = Color(0xFF746657),
                        unfocusedIndicatorColor = Color(0xFF8B8982).copy(alpha = 0.28f),
                        focusedLabelColor = Color(0xFF746657),
                        unfocusedLabelColor = Color(0xFF8B8982),
                        cursorColor = Color(0xFF746657)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagColors.forEach { color ->
                        val colorValue = color.toArgb().toLong() and 0xFFFFFFFFL
                        val selected = selectedColor == colorValue
                        Box(
                            Modifier.size(48.dp)
                                .clip(CircleShape)
                                .clickable { selectedColor = colorValue },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color(0xFF746657) else Color(0xFF34332F).copy(alpha = 0.16f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Text("✓", color = Color(0xFF34332F))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFFF0EEE8))
                            .clickable { showIconPicker = true }
                            .semantics {
                                contentDescription = "Choose tag icon"
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedIconBitmap != null) {
                            Image(selectedIconBitmap!!, contentDescription = selectedIconEntry?.name, modifier = Modifier.fillMaxSize().padding(5.dp))
                        } else {
                            Text("＋", color = Color(0xFF746657))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(selectedIconEntry?.name ?: "No icon selected", color = Color(0xFF34332F), style = MaterialTheme.typography.bodyMedium)
                        Text("Tag icon", color = Color(0xFF8B8982), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { showIconColorPicker = true }) { Text("Icon Color") }
                    TextButton(onClick = { selectedIcon = null }) { Text("Clear") }
                }
                Text("Apps (${selectedPackages.size} selected)", style = MaterialTheme.typography.labelMedium)
                TextField(
                    value = appSearch,
                    onValueChange = { appSearch = it },
                    placeholder = { Text("Search apps or package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF34332F),
                        unfocusedTextColor = Color(0xFF34332F),
                        focusedContainerColor = Color(0xFFF4F1EA),
                        unfocusedContainerColor = Color(0xFFF4F1EA),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF746657)
                    )
                )
                if (filteredApps.isEmpty()) {
                    Text("No matching apps", color = Color(0xFF8B8982), modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(filteredApps, key = { it.id }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedPackages = if (app.id in selectedPackages) selectedPackages - app.id
                                    else selectedPackages + app.id
                                }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.id in selectedPackages,
                                    onCheckedChange = { checked ->
                                        selectedPackages = if (checked) selectedPackages + app.id
                                        else selectedPackages - app.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF746657),
                                        uncheckedColor = Color(0xFF8B8982),
                                        checkmarkColor = Color.White
                                    )
                                )
                                TagAppIcon(app, iconBitmapCache)
                                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                    Text(app.label, color = Color(0xFF34332F), maxLines = 1)
                                    Text(
                                        if (app.isInCurrentProfile) "Personal profile" else "Work profile",
                                        color = Color(0xFF8B8982),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
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
                            appPackages = selectedPackages.toList(),
                            iconFrameColorArgb = selectedIconFrameColor
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
        NekOSAlertDialog(
            frameColorArgb = launcherUiColorArgb,
            onDismissRequest = { showIconPicker = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = launcherSurface,
            titleContentColor = Color(0xFF34332F),
            textContentColor = Color(0xFF34332F),
            title = { DialogTitleWithFrame(launcherUiColorArgb) { Text("Choose tag icon") } },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(300.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(catalog, key = { it.id }) { icon ->
                        var bitmap by remember(icon.id, selectedIconFrameColor) {
                            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
                        }
                        LaunchedEffect(icon.id, selectedIconFrameColor) {
                            bitmap = withContext(Dispatchers.IO) {
                                runCatching {
                                    iconCatalogRepository.loadPickerBitmap(icon, selectedIconFrameColor)?.asImageBitmap()
                                }.getOrNull()
                            }
                        }
                        Box(
                            Modifier.aspectRatio(1f).padding(2.dp)
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
    if (showIconColorPicker) {
        NekOSAlertDialog(
            frameColorArgb = launcherUiColorArgb,
            onDismissRequest = { showIconColorPicker = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = launcherSurface,
            titleContentColor = Color(0xFF34332F),
            textContentColor = Color(0xFF34332F),
            title = { DialogTitleWithFrame(launcherUiColorArgb) { Text("Icon Color") } },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 300.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TagIconFrameColors) { (name, colorValue) ->
                        val selected = selectedIconFrameColor == colorValue
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF4F1EA))
                                .clickable {
                                    selectedIconFrameColor = colorValue
                                    showIconColorPicker = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                Modifier.size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue.toInt()))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color(0xFF34332F) else Color(0xFF34332F).copy(alpha = 0.18f),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                name,
                                color = Color(0xFF34332F),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconColorPicker = false }) { Text("Done") }
            }
        )
    }

}
