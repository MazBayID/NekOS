package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val WarmBackground = Color(0xFFF4F1EA)
private val Ink = Color(0xFF34332F)
private val Muted = Color(0xFF8B8982)
private val TagSurface = Color(0xFFE7E1D6)

@Composable
fun NekOSRoot() {
    var drawerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val apps by remember { mutableStateOf(loadLaunchableApps(context)) }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var editingFavoriteSlot by remember { mutableStateOf<Int?>(null) }

    fun updateFavorites(updated: List<String?>) {
        favoritePackages = updated
        saveFavoritePackages(context, updated)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(WarmBackground)
            .pointerInput(drawerOpen) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (!drawerOpen && totalDrag < -70f) drawerOpen = true
                        if (drawerOpen && totalDrag > 70f) drawerOpen = false
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
    ) {
        if (drawerOpen) {
            AppDrawer(
                apps = apps,
                onAppClick = { app ->
                    launchApp(context, app.packageName)
                    drawerOpen = false
                },
            )
        } else {
            HomeScreen(
                apps = apps,
                favoritePackages = favoritePackages,
                onFavoriteClick = { launchApp(context, it) },
                onFavoriteLongPress = { editingFavoriteSlot = it }
            )
        }

        editingFavoriteSlot?.let { slot ->
            FavoritePickerDialog(
                slotIndex = slot,
                apps = apps,
                currentPackage = favoritePackages.getOrNull(slot),
                favoritePackages = favoritePackages,
                onSelect = { packageName ->
                    val updated = favoritePackages.toMutableList()
                    while (updated.size < 15) updated.add(null)
                    updated[slot] = packageName
                    updateFavorites(updated)
                    editingFavoriteSlot = null
                },
                onRemove = {
                    val updated = favoritePackages.toMutableList()
                    while (updated.size < 15) updated.add(null)
                    updated[slot] = null
                    updateFavorites(updated)
                    editingFavoriteSlot = null
                },
                onDismiss = { editingFavoriteSlot = null }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<InstalledApp>,
    favoritePackages: List<String?>,
    onFavoriteClick: (String) -> Unit,
    onFavoriteLongPress: (Int) -> Unit
) {
    val appByPackage = remember(apps) { apps.associateBy { it.packageName } }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("NekOS", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TagPill("Your tags") }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Swipe up to open apps", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFD5CFC4)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) { column ->
                        val index = row * 5 + column
                        val app = favoritePackages.getOrNull(index)?.let { appByPackage[it] }
                        FavoriteSlot(
                            app = app,
                            onClick = { app?.let { onFavoriteClick(it.packageName) } },
                            onLongClick = { onFavoriteLongPress(index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSlot(
    app: InstalledApp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = app?.let { remember(it.packageName) { it.icon.toBitmap(96, 96).asImageBitmap() } }
    Column(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(19.dp))
            .background(Color(0xFFECE8E0))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app != null && bitmap != null) {
            androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = app.label, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(3.dp))
            Text(app.label, color = Ink, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
        } else {
            Text("+", color = Muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun FavoritePickerDialog(
    slotIndex: Int,
    apps: List<InstalledApp>,
    currentPackage: String?,
    favoritePackages: List<String?>,
    onSelect: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val availableApps = apps.filter { app ->
        app.packageName == currentPackage || app.packageName !in favoritePackages.filterNotNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Favorite slot \${slotIndex + 1}") },
        text = {
            Column {
                if (currentPackage != null) {
                    TextButton(onClick = onRemove) { Text("Remove from favorites") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(availableApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(app.packageName) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = remember(app.packageName) { app.icon.toBitmap(72, 72).asImageBitmap() }
                            androidx.compose.foundation.Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = Ink)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TagPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TagSurface)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FavoritesPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xFFECE8E0))
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 22.dp)) {
        Text(
            "Applications",
            color = Ink,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No apps found", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppGridItem(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: InstalledApp, onClick: () -> Unit) {
    val bitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            app.label,
            color = Ink,
            fontSize = 10.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            modifier = Modifier.heightIn(min = 24.dp)
        )
    }
}

