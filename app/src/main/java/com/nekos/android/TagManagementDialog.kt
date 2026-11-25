package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val activeSkinGeneration = SkinRuntimeState.generation
    val catalog = remember(iconCatalogRepository, activeSkinGeneration) {
        runCatching { iconCatalogRepository.loadCatalog() }.getOrDefault(emptyList())
    }
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var selectedIcon by remember(tag?.id) { mutableStateOf(tag?.iconId) }
    var selectedColor by remember(tag?.id) { mutableLongStateOf(tag?.colorArgb ?: 0xFFE7E1D6) }
    var selectedIconFrameColor by remember(tag?.id) {
        mutableLongStateOf(tag?.iconFrameColorArgb ?: DEFAULT_TAG_ICON_FRAME_COLOR)
    }
    var selectedPackages by remember(tag?.id, tag?.appPackages) { mutableStateOf(tag?.appPackages?.toSet().orEmpty()) }
    var showIconColorPicker by remember { mutableStateOf(false) }
    var showTagColorPicker by remember { mutableStateOf(false) }
    var appSearch by remember(tag?.id) { mutableStateOf("") }
    val iconCarouselState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedIcon, catalog) {
        val index = catalog.indexOfFirst { it.id == selectedIcon }
        if (index >= 0) iconCarouselState.scrollToItem(index)
    }
    val availableAppIds = remember(apps) { apps.mapTo(mutableSetOf()) { it.id } }
    val unavailableSelectedCount = selectedPackages.count { it !in availableAppIds }
    val filteredApps = remember(apps, appSearch) {
        if (appSearch.isBlank()) apps
        else apps.filter { it.label.contains(appSearch.trim(), ignoreCase = true) ||
            it.packageName.contains(appSearch.trim(), ignoreCase = true) }
    }

    NekOSAlertDialog(
        topFrameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { Text(if (tag == null) "Create tag" else "Edit tag") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Tag Name", color = Color(0xFF34332F), style = MaterialTheme.typography.labelMedium)
                        TextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF34332F),
                                unfocusedTextColor = Color(0xFF34332F),
                                focusedContainerColor = Color(0xFFF4F1EA),
                                unfocusedContainerColor = Color(0xFFF4F1EA),
                                focusedIndicatorColor = Color(0xFF746657),
                                unfocusedIndicatorColor = Color(0xFF8B8982).copy(alpha = 0.28f),
                                cursorColor = Color(0xFF746657)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Tag Color", color = Color(0xFF34332F), style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF4F1EA))
                                .clickable { showTagColorPicker = true }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(selectedColor.toInt()))
                                    .border(1.dp, Color(0xFF34332F).copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            )
                            Text("Choose", color = Color(0xFF34332F), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tag icon", color = Color(0xFF34332F), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { showIconColorPicker = true }) { Text("Icon color") }
                        TextButton(onClick = { selectedIcon = null }) { Text("Clear") }
                    }
                    LazyRow(
                        state = iconCarouselState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(catalog, key = { it.id }) { icon ->
                            var bitmap by remember(icon.id, selectedIconFrameColor, iconCatalogRepository.generation, activeSkinGeneration) {
                                mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
                            }
                            LaunchedEffect(icon.id, selectedIconFrameColor, iconCatalogRepository.generation, activeSkinGeneration) {
                                bitmap = withContext(Dispatchers.IO) {
                                    runCatching {
                                        iconCatalogRepository.loadPickerBitmap(icon, selectedIconFrameColor)?.asImageBitmap()
                                    }.getOrNull()
                                }
                            }
                            val selected = selectedIcon == icon.id
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFFE7E1D6) else Color(0xFFF4F1EA))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color(0xFF746657) else Color(0xFF34332F).copy(alpha = 0.10f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedIcon = icon.id },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) Image(bitmap!!, contentDescription = icon.name,
                                    modifier = Modifier.fillMaxSize().padding(6.dp))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("App assignment · ${selectedPackages.size} selected", color = Color(0xFF34332F), style = MaterialTheme.typography.labelMedium)
                    if (unavailableSelectedCount > 0) {
                        Text(
                            "$unavailableSelectedCount saved assignment(s) are not currently visible. NekOS keeps them until their Android profile becomes available again.",
                            color = Color(0xFF8B8982),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        placeholder = { Text("Search apps or package") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
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
                        Text(
                            "No matching apps",
                            color = Color(0xFF8B8982),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 120.dp)) {
                            items(filteredApps, key = { it.id }) { app ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedPackages = if (app.id in selectedPackages) selectedPackages - app.id
                                        else selectedPackages + app.id
                                    }.padding(vertical = 4.dp),
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
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
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

    if (showTagColorPicker) {
        TagColorPickerDialog(
            selectedColor = selectedColor,
            launcherSurface = launcherSurface,
            launcherUiColorArgb = launcherUiColorArgb,
            onColorSelected = { selectedColor = it },
            onDismiss = { showTagColorPicker = false }
        )
    }

    if (showIconColorPicker) {
        TagIconFrameColorPickerDialog(
            selectedColor = selectedIconFrameColor,
            launcherSurface = launcherSurface,
            launcherUiColorArgb = launcherUiColorArgb,
            onColorSelected = { selectedIconFrameColor = it },
            onDismiss = { showIconColorPicker = false }
        )
    }
}
