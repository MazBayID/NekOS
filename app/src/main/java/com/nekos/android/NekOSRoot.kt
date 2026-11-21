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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
    val interactionController = remember { LauncherInteractionController() }
    var favoriteDrag by remember { mutableStateOf<LauncherDragSession?>(null) }
    val favoriteSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var tags by remember { mutableStateOf(TagStore.load(context)) }
    var tagEditorTarget by remember { mutableStateOf<NekTag?>(null) }
    var viewingTag by remember { mutableStateOf<NekTag?>(null) }
    var actionMenuTag by remember { mutableStateOf<NekTag?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var actionMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var actionMenuPreviewApp by remember { mutableStateOf<InstalledApp?>(null) }
    var actionMenuPreviewPosition by remember { mutableStateOf(Offset.Zero) }
    var actionMenuPopupSize by remember { mutableStateOf(IntSize.Zero) }
    val preferences = remember(context) {
        context.getSharedPreferences("nekos_preferences", Context.MODE_PRIVATE)
    }
    var showFirstLaunchTip by remember {
        mutableStateOf(!preferences.getBoolean("has_seen_drawer_tip", false))
    }

    fun clearActionMenuPreview() {
        actionMenuPreviewApp = null
    }

    fun openActionMenuAfterRelease() {
        actionMenuPreviewApp?.let { actionMenuApp = it }
        actionMenuPreviewApp = null
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
        val updated = favoritePackages.toMutableList()
        while (updated.size < 15) updated.add(null)
        val sourceIndex = updated.indexOf(packageName)
        if (sourceIndex == targetIndex) return

        val displaced = updated[targetIndex]
        if (sourceIndex >= 0) {
            // Reordering an existing Favorite swaps slots without losing either app.
            updated[targetIndex] = packageName
            updated[sourceIndex] = displaced
        } else {
            if (displaced != null) {
                val emptyIndex = updated.indexOfFirst { it == null }
                if (emptyIndex < 0) {
                    Toast.makeText(context, "Favorite slots are full", Toast.LENGTH_SHORT).show()
                    return
                }
                updated[emptyIndex] = displaced
            }
            updated[targetIndex] = packageName
        }
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
            onFavoriteLongPress = { index, position ->
                favoritePackages.getOrNull(index)?.let { packageName ->
                    apps.firstOrNull { it.packageName == packageName }?.let { app ->
                        actionMenuPreviewApp = app
                        actionMenuPreviewPosition = position
                    }
                }
            },
            onFavoriteLongPressEnd = ::openActionMenuAfterRelease,
            onFavoriteLongPressCancel = ::clearActionMenuPreview,
            onFavoriteSlotBounds = { index, bounds -> favoriteSlotBounds[index] = bounds },
            favoriteDropTarget = favoriteDrag?.takeIf { it.hasMoved }?.targetIndex,
            onFavoriteDragStart = { app, position ->
                clearActionMenuPreview()
                favoriteDrag = interactionController.startDrag(
                    app.packageName,
                    LauncherDragSource.FAVORITE,
                    position
                )
            },
            onFavoriteDrag = { position ->
                val target = favoriteSlotBounds.entries.firstOrNull { it.value.contains(position) }?.key
                favoriteDrag = interactionController.updateDrag(position, target)
            },
            onFavoriteDragEnd = {
                val drag = interactionController.finishDrag()
                if (drag?.hasMoved == true) {
                    drag.targetIndex?.let { placeFavoriteFromDrawer(drag.packageName, it) }
                }
                favoriteDrag = null
            },
            onFavoriteDragCancel = {
                interactionController.cancelDrag()
                favoriteDrag = null
            },
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
                        // During a drag, lift the drawer to expose the Home Favorite area
                        // underneath while keeping the dragged icon in the root overlay.
                        translationY = drawerOffset.value * size.height -
                            if (favoriteDrag?.hasMoved == true) size.height * 0.58f else 0f
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
                onAppLongPress = { app, position ->
                    actionMenuPreviewApp = app
                    actionMenuPreviewPosition = position
                },
                onAppLongPressEnd = ::openActionMenuAfterRelease,
                onAppLongPressCancel = ::clearActionMenuPreview,
                onAppDragStart = { app, position ->
                    clearActionMenuPreview()
                    favoriteDrag = interactionController.startDrag(
                        app.packageName,
                        LauncherDragSource.APP_DRAWER,
                        position
                    )
                },
                onAppDrag = { position ->
                    val target = favoriteSlotBounds.entries.firstOrNull { it.value.contains(position) }?.key
                    favoriteDrag = interactionController.updateDrag(position, target)
                },
                onAppDragEnd = {
                    val drag = interactionController.finishDrag()
                    if (drag?.hasMoved == true) {
                        drag.targetIndex?.let { placeFavoriteFromDrawer(drag.packageName, it) }
                    }
                    favoriteDrag = null
                },
                onAppDragCancel = {
                    interactionController.cancelDrag()
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

        val popupApp = actionMenuPreviewApp ?: actionMenuApp
        if (actionMenuApp != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(19f)
                    .pointerInput(actionMenuApp) {
                        detectTapGestures { actionMenuApp = null }
                    }
            )
        }
        popupApp?.let { app ->
            val isFavorite = app.packageName in favoritePackages.filterNotNull()
            val isInteractive = actionMenuApp != null
            Column(
                modifier = Modifier
                    .onSizeChanged { actionMenuPopupSize = it }
                    .offset {
                        calculateLauncherPopupOffset(
                            anchorPosition = actionMenuPreviewPosition,
                            popupSize = actionMenuPopupSize,
                            containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                        )
                    }
                    .zIndex(20f)
                    .widthIn(max = minOf(210.dp, maxWidth))
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFEFCF8))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    app.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                PopupAction("App info", enabled = isInteractive) {
                    actionMenuApp = null
                    openAppInfo(context, app.packageName)
                }
                PopupAction(
                    if (isFavorite) "Remove from favorite" else "Add to favorite",
                    enabled = isInteractive
                ) {
                    if (isFavorite) {
                        updateFavorites(favoritePackages.map { if (it == app.packageName) null else it })
                    } else {
                        addFavorite(app.packageName)
                    }
                    actionMenuApp = null
                }
                PopupAction("Uninstall", enabled = isInteractive) {
                    actionMenuApp = null
                    requestAppUninstall(context, app.packageName)
                }
                if (!isInteractive) {
                    Text(
                        "Release to choose",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        favoriteDrag?.takeIf { it.hasMoved }?.let { drag ->
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
    onFavoriteLongPress: (Int, Offset) -> Unit,
    onFavoriteLongPressEnd: () -> Unit,
    onFavoriteLongPressCancel: () -> Unit,
    onFavoriteSlotBounds: (Int, Rect) -> Unit,
    favoriteDropTarget: Int?,
    onFavoriteDragStart: (InstalledApp, Offset) -> Unit,
    onFavoriteDrag: (Offset) -> Unit,
    onFavoriteDragEnd: () -> Unit,
    onFavoriteDragCancel: () -> Unit,
    onTagClick: (NekTag) -> Unit,
    onTagLongPress: (NekTag) -> Unit,
    onAddTag: () -> Unit
) {
    val appByPackage = remember(apps) { apps.associateBy { it.packageName } }
    Box(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.offset(x = 5.dp).height(60.dp)
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
                                onBoundsChanged = { bounds -> onFavoriteSlotBounds(index, bounds) },
                                isDropTarget = favoriteDropTarget == index,
                                onClick = { app?.let { onFavoriteClick(it.packageName) } },
                                onLongPress = { position -> onFavoriteLongPress(index, position) },
                                onLongPressEnd = onFavoriteLongPressEnd,
                                onLongPressCancel = onFavoriteLongPressCancel,
                                onDragStart = { position -> app?.let { onFavoriteDragStart(it, position) } },
                                onDrag = onFavoriteDrag,
                                onDragEnd = onFavoriteDragEnd,
                                onDragCancel = onFavoriteDragCancel,
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
    onBoundsChanged: (Rect) -> Unit,
    isDropTarget: Boolean,
    onClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = if (app == null) null else remember(app.packageName) {
        app.icon.toBitmap(96, 96).asImageBitmap()
    }
    var slotCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSlotCoordinates by rememberUpdatedState(slotCoordinates)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDropTarget) Color(0xFFDCD3C4) else Color.Transparent)
            .onGloballyPositioned {
                slotCoordinates = it
                onBoundsChanged(it.boundsInRoot())
            }
            .pointerInput(app?.packageName) {
                if (app != null) {
                    detectLauncherItemGesture(
                        toRootPosition = { local -> currentSlotCoordinates?.localToRoot(local) ?: local },
                        onTap = onClick,
                        onLongPress = onLongPress,
                        onLongPressEnd = onLongPressEnd,
                        onLongPressCancel = onLongPressCancel,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
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
private fun PopupAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        color = if (enabled) Ink else Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(tag: NekTag, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    var tagIcon by remember(tag.iconId) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(tag.iconId) {
        tagIcon = withContext(Dispatchers.IO) {
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tagIcon != null) Image(tagIcon!!, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(tag.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddTagPill(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(TagSurface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("+", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppDrawer(
    apps: List<InstalledApp>,
    iconBitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap>,
    closeThresholdPx: Float,
    modifier: Modifier = Modifier,
    onAppClick: (InstalledApp) -> Unit,
    onAppLongPress: (InstalledApp, Offset) -> Unit,
    onAppLongPressEnd: () -> Unit,
    onAppLongPressCancel: () -> Unit,
    onAppDragStart: (InstalledApp, Offset) -> Unit,
    onAppDrag: (Offset) -> Unit,
    onAppDragEnd: () -> Unit,
    onAppDragCancel: () -> Unit,
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
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val cachedBitmap = iconBitmapCache[app.packageName]
                    var bitmap by remember(app.packageName) {
                        mutableStateOf(cachedBitmap)
                    }
                    LaunchedEffect(app.packageName, cachedBitmap) {
                        if (cachedBitmap != null) {
                            bitmap = cachedBitmap
                        } else {
                            val loadedBitmap = withContext(Dispatchers.Default) {
                                app.icon.toBitmap(96, 96).asImageBitmap()
                            }
                            iconBitmapCache[app.packageName] = loadedBitmap
                            bitmap = loadedBitmap
                        }
                    }
                    AppGridItem(
                        app = app,
                        bitmap = bitmap,
                        onClick = { onAppClick(app) },
                        onLongPress = { position -> onAppLongPress(app, position) },
                        onLongPressEnd = onAppLongPressEnd,
                        onLongPressCancel = onAppLongPressCancel,
                        onDragStart = { position -> onAppDragStart(app, position) },
                        onDrag = onAppDrag,
                        onDragEnd = onAppDragEnd,
                        onDragCancel = onAppDragCancel
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
    onLongPress: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentItemCoordinates by rememberUpdatedState(itemCoordinates)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp)
            .onGloballyPositioned { itemCoordinates = it }
            .pointerInput(app.packageName) {
                detectLauncherItemGesture(
                    toRootPosition = { local -> currentItemCoordinates?.localToRoot(local) ?: local },
                    onTap = onClick,
                    onLongPress = onLongPress,
                    onLongPressEnd = onLongPressEnd,
                    onLongPressCancel = onLongPressCancel,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            },
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
