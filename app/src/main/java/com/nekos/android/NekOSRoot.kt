package com.nekos.android

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.core.graphics.drawable.toBitmap

private val WarmBackground = Color(0xFFF4F1EA)
private val Ink = Color(0xFF34332F)
private val Muted = Color(0xFF8B8982)
private val TagSurface = Color(0xFFE7E1D6)

@Composable
fun NekOSRoot() {
    val context = LocalContext.current
    val apps by remember { mutableStateOf(loadLaunchableApps(context)) }
    val iconBitmapCache = remember(apps) {
        mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var editingFavoriteSlot by remember { mutableStateOf<Int?>(null) }
    val preferences = remember(context) {
        context.getSharedPreferences("nekos_preferences", Context.MODE_PRIVATE)
    }
    var showFirstLaunchTip by remember {
        mutableStateOf(!preferences.getBoolean("has_seen_drawer_tip", false))
    }

    fun updateFavorites(updated: List<String?>) {
        favoritePackages = updated
        saveFavoritePackages(context, updated)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(WarmBackground)) {
        val drawerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val drawerOpenThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val drawerOffset = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()

        HomeScreen(
            apps = apps,
            favoritePackages = favoritePackages,
            drawerOpenThresholdPx = drawerOpenThresholdPx,
            onDrawerDrag = { totalDrag ->
                drawerOffset.snapTo(
                    (1f + totalDrag / drawerHeightPx).coerceIn(0f, 1f)
                )
            },
            onDrawerDragEnd = { totalDrag ->
                drawerOffset.animateTo(
                    if (totalDrag <= -drawerOpenThresholdPx) 0f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 420f
                    )
                )
            },
            onFavoriteClick = { launchApp(context, it) },
            onFavoriteLongPress = { editingFavoriteSlot = it }
        )

        if (drawerOffset.value < 1f) {
            AppDrawer(
                apps = apps,
                iconBitmapCache = iconBitmapCache,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = drawerOffset.value * size.height
                    },
                onAppClick = { app ->
                    launchApp(context, app.packageName)
                    scope.launch {
                        drawerOffset.animateTo(
                            1f,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = 420f
                            )
                        )
                    }
                },
                onCloseDrawer = {
                    scope.launch {
                        drawerOffset.animateTo(
                            1f,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = 420f
                            )
                        )
                    }
                }
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

        if (showFirstLaunchTip) {
            AlertDialog(
                onDismissRequest = {
                    preferences.edit().putBoolean("has_seen_drawer_tip", true).apply()
                    showFirstLaunchTip = false
                },
                title = { Text("Welcome to NekOS") },
                text = {
                    Text("Swipe up from the home screen to open your apps. At the top of the app list, swipe down again to return home.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        preferences.edit().putBoolean("has_seen_drawer_tip", true).apply()
                        showFirstLaunchTip = false
                    }) { Text("Got it") }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<InstalledApp>,
    favoritePackages: List<String?>,
    drawerOpenThresholdPx: Float,
    onDrawerDrag: suspend (Float) -> Unit,
    onDrawerDragEnd: suspend (Float) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onFavoriteLongPress: (Int) -> Unit
) {
    val appByPackage = remember(apps) { apps.associateBy { it.packageName } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(drawerOpenThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f
                    var acceptedUpwardDrag = false

                    val dragStart = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (overSlop < 0f) {
                            acceptedUpwardDrag = true
                            totalDrag = overSlop - viewConfiguration.touchSlop
                            change.consume()
                        }
                    }

                    if (dragStart != null && acceptedUpwardDrag) {
                        onDrawerDrag(totalDrag)

                        var change = dragStart
                        while (change != null && change.pressed) {
                            change = awaitVerticalDragOrCancellation(change.id)
                            if (change != null && change.pressed) {
                                totalDrag += change.positionChange().y
                                onDrawerDrag(totalDrag.coerceAtMost(0f))
                                change.consume()
                            }
                        }

                        onDrawerDragEnd(totalDrag)
                    }
                }
            }
            .padding(horizontal = 24.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("NekOS", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TagPill("Your tags") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSlot(
    app: InstalledApp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = if (app == null) null else remember(app.packageName) {
        app.icon.toBitmap(96, 96).asImageBitmap()
    }
    Column(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(19.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app != null && bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(54.dp)
            )
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
        title = { Text("Favorite slot ${slotIndex + 1}") },
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
private fun AppDrawer(
    apps: List<InstalledApp>,
    iconBitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap>,
    modifier: Modifier = Modifier,
    onAppClick: (InstalledApp) -> Unit,
    onCloseDrawer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentOnCloseDrawer by rememberUpdatedState(onCloseDrawer)
    val closeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val filteredApps = remember(apps, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) apps
        else apps.filter { it.label.trimStart().startsWith(query, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > closeThreshold) currentOnCloseDrawer()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.width(38.dp).height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD5CFC4))
            )
        }
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
            val gridState = rememberLazyGridState()
            val touchSlop = LocalViewConfiguration.current.touchSlop
            val edgeCloseGesture = Modifier.pointerInput(gridState, closeThreshold, touchSlop) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )

                    // Lock eligibility at ACTION_DOWN-equivalent time. A gesture that
                    // starts while the grid is scrolled cannot close the drawer later,
                    // even if that same gesture scrolls the grid back to its top.
                    val startedAtTop = !gridState.canScrollBackward
                    var totalMovement = androidx.compose.ui.geometry.Offset.Zero
                    var directionDecided = false
                    var isDownwardCloseGesture = false
                    var closeTriggered = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        totalMovement += change.positionChange()

                        if (!directionDecided &&
                            (abs(totalMovement.x) > touchSlop || abs(totalMovement.y) > touchSlop)
                        ) {
                            directionDecided = true
                            isDownwardCloseGesture =
                                startedAtTop &&
                                totalMovement.y > 0f &&
                                abs(totalMovement.y) > abs(totalMovement.x)
                        }

                        if (isDownwardCloseGesture) {
                            // Consume in the Initial pass so the grid cannot interpret
                            // this edge pull as a list scroll or overscroll gesture.
                            change.consume()

                            if (!closeTriggered && totalMovement.y >= closeThreshold) {
                                closeTriggered = true
                                currentOnCloseDrawer()
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().then(edgeCloseGesture)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val cachedBitmap = iconBitmapCache[app.packageName]
                    val bitmap by produceState(
                        initialValue = cachedBitmap,
                        key1 = app.packageName
                    ) {
                        if (value == null) {
                            val loadedBitmap = withContext(Dispatchers.Default) {
                                app.icon.toBitmap(96, 96).asImageBitmap()
                            }
                            iconBitmapCache[app.packageName] = loadedBitmap
                            value = loadedBitmap
                        }
                    }
                    AppGridItem(
                        app = app,
                        bitmap = bitmap,
                        onClick = { onAppClick(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(
    app: InstalledApp,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(56.dp)
            )
        } else {
            Spacer(Modifier.size(56.dp))
        }
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
