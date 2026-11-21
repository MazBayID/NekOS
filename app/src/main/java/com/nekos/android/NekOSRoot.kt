package com.nekos.android

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap

private val WarmBackground = Color(0xFFF4F1EA)
private val Ink = Color(0xFF34332F)
private val Muted = Color(0xFF8B8982)
private val TagSurface = Color(0xFFE7E1D6)

private data class FavoriteDrag(
    val packageName: String,
    val position: Offset
)

@Composable
fun NekOSRoot(refreshToken: Int) {
    var showBrandSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900L)
        showBrandSplash = false
    }

    if (showBrandSplash) {
        Box(
            modifier = Modifier.fillMaxSize().background(WarmBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.nekos_text_logo),
                contentDescription = "NekOS",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.84f)
            )
        }
        return
    }

    val context = LocalContext.current
    var apps by remember { mutableStateOf(loadLaunchableApps(context)) }
    val iconBitmapCache = remember(apps) {
        mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var favoriteDrag by remember { mutableStateOf<FavoriteDrag?>(null) }
    val favoriteSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var tags by remember { mutableStateOf(TagStore.load(context)) }
    var tagEditorTarget by remember { mutableStateOf<NekTag?>(null) }
    var viewingTag by remember { mutableStateOf<NekTag?>(null) }
    var actionMenuTag by remember { mutableStateOf<NekTag?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var actionMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    val preferences = remember(context) {
        context.getSharedPreferences("nekos_preferences", Context.MODE_PRIVATE)
    }
    var showFirstLaunchTip by remember {
        mutableStateOf(!preferences.getBoolean("has_seen_drawer_tip", false))
    }

    fun saveTags(updated: List<NekTag>) {
        tags = updated.distinctBy { it.id }
        TagStore.save(context, tags)
    }

    fun updateFavorites(updated: List<String?>) {
        favoritePackages = updated
        saveFavoritePackages(context, updated)
    }

    fun addFavorite(packageName: String) {
        if (packageName in favoritePackages) return
        val updated = favoritePackages.toMutableList()
        val emptySlot = updated.indexOfFirst { it == null }
        if (emptySlot < 0) {
            Toast.makeText(context, "Favorite slots are full", Toast.LENGTH_SHORT).show()
            return
        }
        updated[emptySlot] = packageName
        updateFavorites(updated)
    }

    fun placeFavoriteFromDrawer(packageName: String, targetIndex: Int) {
        if (packageName in favoritePackages) return
        val updated = favoritePackages.toMutableList()
        while (updated.size < 15) updated.add(null)
        val displaced = updated[targetIndex]
        if (displaced != null) {
            val emptyIndex = updated.indexOfFirst { it == null }
            if (emptyIndex < 0) {
                Toast.makeText(context, "Favorite slots are full", Toast.LENGTH_SHORT).show()
                return
            }
            updated[emptyIndex] = displaced
        }
        updated[targetIndex] = packageName
        updateFavorites(updated)
    }

    LaunchedEffect(refreshToken) {
        val refreshedApps = loadLaunchableApps(context)
        apps = refreshedApps
        val installedPackages = refreshedApps.mapTo(mutableSetOf<String>()) { it.packageName }
        val cleanedFavorites = favoritePackages.map { packageName ->
            packageName?.takeIf { it in installedPackages }
        }
        if (cleanedFavorites != favoritePackages) {
            favoritePackages = cleanedFavorites
            saveFavoritePackages(context, cleanedFavorites)
        }
        val cleanedTags = tags.map { tag -> tag.copy(appPackages = tag.appPackages.filter { it in installedPackages }) }
        if (cleanedTags != tags) saveTags(cleanedTags)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(WarmBackground)) {
        val drawerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val homeSwipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val drawerCloseThresholdPx = drawerHeightPx * 0.18f
        val backgroundBlurRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
        val drawerOffset = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()

        HomeScreen(
            apps = apps,
            favoritePackages = favoritePackages,
            tags = tags,
            homeSwipeThresholdPx = homeSwipeThresholdPx,
            backgroundBlurRadiusPx = backgroundBlurRadiusPx * (1f - drawerOffset.value),
            onDrawerDrag = { totalDrag ->
                scope.launch {
                    drawerOffset.snapTo(
                        (1f + totalDrag / drawerHeightPx).coerceIn(0f, 1f)
                    )
                }
            },
            onDrawerDragEnd = { totalDrag ->
                scope.launch {
                    drawerOffset.animateTo(
                        if (totalDrag <= -homeSwipeThresholdPx) 0f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = 420f
                        )
                    )
                }
            },
            onFavoriteClick = { launchApp(context, it) },
            onFavoriteLongPress = { index ->
                favoritePackages.getOrNull(index)?.let { packageName ->
                    apps.firstOrNull { it.packageName == packageName }?.let { actionMenuApp = it }
                }
            },
            onFavoriteSlotBounds = { index, bounds -> favoriteSlotBounds[index] = bounds },
            onTagClick = { tag -> viewingTag = tag },
            onTagLongPress = { tag -> actionMenuTag = tag },
            onAddTag = { tagEditorTarget = null; showTagEditor = true }
        )

        if (drawerOffset.value < 1f) {
            // A subtle warm translucent veil keeps the blurred home screen legible
            // and provides a graceful dimmed fallback on Android versions below 12.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        WarmBackground.copy(
                            alpha = 0.28f * (1f - drawerOffset.value)
                        )
                    )
            )

            AppDrawer(
                apps = apps,
                iconBitmapCache = iconBitmapCache,
                closeThresholdPx = drawerCloseThresholdPx,
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
                onAppDragStart = { app, position -> favoriteDrag = FavoriteDrag(app.packageName, position) },
                onAppDrag = { position -> favoriteDrag = favoriteDrag?.copy(position = position) },
                onAppDragEnd = {
                    val drag = favoriteDrag
                    val target = drag?.let { state -> favoriteSlotBounds.entries.firstOrNull { it.value.contains(state.position) }?.key }
                    if (drag != null && target != null) placeFavoriteFromDrawer(drag.packageName, target)
                    favoriteDrag = null
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

        favoriteDrag?.let { drag ->
            val draggedApp = apps.firstOrNull { it.packageName == drag.packageName }
            if (draggedApp != null) {
                val bitmap = remember(draggedApp.packageName) { draggedApp.icon.toBitmap(96, 96).asImageBitmap() }
                val iconOffset = with(LocalDensity.current) { 28.dp.toPx() }
                Image(
                    bitmap = bitmap,
                    contentDescription = draggedApp.label,
                    modifier = Modifier
                        .offset { IntOffset((drag.position.x - iconOffset).roundToInt(), (drag.position.y - iconOffset).roundToInt()) }
                        .size(56.dp)
                        .zIndex(10f)
                )
            }
        }

        viewingTag?.let { tag ->
            TagAppsDialog(
                tag = tag,
                apps = apps,
                onLaunch = { app ->
                    viewingTag = null
                    launchApp(context, app.packageName)
                },
                onEdit = {
                    tagEditorTarget = tag
                    viewingTag = null
                    showTagEditor = true
                },
                onDismiss = { viewingTag = null }
            )
        }

        actionMenuTag?.let { tag ->
            TagActionsDialog(
                tag = tag,
                onEdit = {
                    actionMenuTag = null
                    tagEditorTarget = tag
                    showTagEditor = true
                },
                onDelete = {
                    saveTags(tags.filterNot { it.id == tag.id })
                    actionMenuTag = null
                },
                onDismiss = { actionMenuTag = null }
            )
        }

        if (showTagEditor) {
            TagManagementDialog(
                tag = tagEditorTarget,
                apps = apps,
                onSave = { saved ->
                    val duplicateName = tags.any { it.id != saved.id && it.name.equals(saved.name, ignoreCase = true) }
                    if (duplicateName) {
                        Toast.makeText(context, "Tag name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        saveTags(if (tagEditorTarget == null) tags + saved else tags.map { if (it.id == saved.id) saved else it })
                        showTagEditor = false
                        tagEditorTarget = null
                    }
                },
                onDelete = { deleted ->
                    saveTags(tags.filterNot { it.id == deleted.id })
                    showTagEditor = false
                    tagEditorTarget = null
                },
                onDismiss = { showTagEditor = false; tagEditorTarget = null }
            )
        }

        actionMenuApp?.let { app ->
            val isFavorite = app.packageName in favoritePackages.filterNotNull()
            AppActionsDialog(
                app = app,
                isFavorite = isFavorite,
                onInfo = {
                    actionMenuApp = null
                    openAppInfo(context, app.packageName)
                },
                onFavorite = {
                    if (isFavorite) {
                        updateFavorites(favoritePackages.map { if (it == app.packageName) null else it })
                    } else {
                        addFavorite(app.packageName)
                    }
                    actionMenuApp = null
                },
                onUninstall = {
                    actionMenuApp = null
                    requestAppUninstall(context, app.packageName)
                },
                onDismiss = { actionMenuApp = null }
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
    tags: List<NekTag>,
    homeSwipeThresholdPx: Float,
    backgroundBlurRadiusPx: Float,
    onDrawerDrag: (Float) -> Unit,
    onDrawerDragEnd: (Float) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onFavoriteLongPress: (Int) -> Unit,
    onFavoriteSlotBounds: (Int, Rect) -> Unit,
    onTagClick: (NekTag) -> Unit,
    onTagLongPress: (NekTag) -> Unit,
    onAddTag: () -> Unit
) {
    val appByPackage = remember(apps) { apps.associateBy { it.packageName } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val favoriteCellSizePx = with(density) { ((maxWidth - 48.dp - 40.dp) / 5).toPx() }
        val favoriteCellGapPx = with(density) { 10.dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    renderEffect = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        backgroundBlurRadiusPx > 0.5f
                    ) {
                        BlurEffect(
                            backgroundBlurRadiusPx,
                            backgroundBlurRadiusPx,
                            TileMode.Decal
                        )
                    } else {
                        null
                    }
                }
                .pointerInput(homeSwipeThresholdPx) {
                    var totalDrag = 0f
                    var drawerDragStarted = false
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag < 0f) {
                                drawerDragStarted = true
                                onDrawerDrag(totalDrag)
                            }
                        },
                        onDragEnd = {
                            if (drawerDragStarted) onDrawerDragEnd(totalDrag)
                            totalDrag = 0f
                            drawerDragStarted = false
                        },
                        onDragCancel = {
                            if (drawerDragStarted) onDrawerDragEnd(totalDrag)
                            totalDrag = 0f
                            drawerDragStarted = false
                        }
                    )
                }
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Image(
                    painter = painterResource(R.drawable.text_logo_sec),
                    contentDescription = "NekOS",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.offset(x = (-28).dp).width(170.dp).height(60.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag -> TagPill(tag, onClick = { onTagClick(tag) }, onLongClick = { onTagLongPress(tag) }) }
                    AddTagPill(onClick = onAddTag)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(5) { column ->
                            val index = row * 5 + column
                            val app = favoritePackages.getOrNull(index)?.let { appByPackage[it] }
                            FavoriteSlot(
                                app = app,
                                slotIndex = index,
                                onBoundsChanged = { bounds -> onFavoriteSlotBounds(index, bounds) },
                                onClick = { app?.let { onFavoriteClick(it.packageName) } },
                                onLongPress = { onFavoriteLongPress(index) },
                                onMove = onFavoriteMove,
                                modifier = Modifier.weight(1f)
                            )
                        }
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
    slotIndex: Int,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = if (app == null) null else remember(app.packageName) {
        app.icon.toBitmap(96, 96).asImageBitmap()
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .pointerInput(app?.packageName) {
                if (app != null) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() }
                    )
                }
            }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app != null && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(54.dp)
            )
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: InstalledApp,
    isFavorite: Boolean,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label, color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppActionRow(R.drawable.nekos_ui_info, "App info", onInfo)
                AppActionRow(
                    R.drawable.nekos_ui_favorite,
                    if (isFavorite) "Remove from favorite" else "Add to favorite",
                    onFavorite
                )
                AppActionRow(R.drawable.nekos_ui_trash, "Uninstall", onUninstall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AppActionRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(tag: NekTag, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val tagIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, key1 = tag.iconId
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                tag.iconId?.let { id ->
                    val repository = IconCatalogRepository(context)
                    repository.loadCatalog().firstOrNull { it.id == id }?.let(repository::loadBitmap)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(tag.colorArgb.toInt()))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (tagIcon != null) Image(tagIcon!!, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(tag.name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddTagPill(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(TagSurface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text("+", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppDrawer(
    apps: List<InstalledApp>,
    iconBitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap>,
    closeThresholdPx: Float,
    modifier: Modifier = Modifier,
    onAppClick: (InstalledApp) -> Unit,
    onAppDragStart: (InstalledApp, Offset) -> Unit,
    onAppDrag: (Offset) -> Unit,
    onAppDragEnd: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentOnCloseDrawer by rememberUpdatedState(onCloseDrawer)
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
                .pointerInput(closeThresholdPx) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > closeThresholdPx) currentOnCloseDrawer()
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
            val edgeCloseGesture = Modifier.pointerInput(gridState, closeThresholdPx, touchSlop) {
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

                            if (!closeTriggered && totalMovement.y >= closeThresholdPx) {
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
                        onClick = { onAppClick(app) },
                        onDragStart = { position -> onAppDragStart(app, position) },
                        onDrag = onAppDrag,
                        onDragEnd = onAppDragEnd
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: InstalledApp,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var itemOrigin by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp)
            .onGloballyPositioned { itemOrigin = it.positionInRoot() }
            .pointerInput(app.packageName, itemOrigin) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> onDragStart(itemOrigin + local) },
                    onDrag = { change, amount -> change.consume(); onDrag(itemOrigin + change.position) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
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
