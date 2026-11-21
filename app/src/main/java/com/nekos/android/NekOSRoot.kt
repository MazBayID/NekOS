package com.nekos.android

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalView
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
    val launchSourceView = LocalView.current
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    val iconBitmapCache = remember(apps) {
        mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var dockPackages by remember { mutableStateOf(loadDockPackages(context)) }
    val interactionController = remember { LauncherInteractionController() }
    var favoriteDrag by remember { mutableStateOf<LauncherDragSession?>(null) }
    val favoriteSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var cancelDropBounds by remember { mutableStateOf<Rect?>(null) }
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
    var showSettings by remember { mutableStateOf(false) }
    var backgroundPreset by remember {
        mutableStateOf(preferences.getString("background_preset", "warm") ?: "warm")
    }
    var showDock by remember { mutableStateOf(preferences.getBoolean("show_dock", true)) }
    var wallpaperPath by remember {
        mutableStateOf(preferences.getString("home_wallpaper_path", null))
    }
    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(wallpaperPath) {
        wallpaperBitmap = withContext(Dispatchers.IO) {
            wallpaperPath?.let { path -> loadHomeWallpaper(context, path) }
        }
    }
    val wallpaperPickerScope = rememberCoroutineScope()
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            wallpaperPickerScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    saveHomeWallpaper(context, uri)
                }
                if (savedPath != null) {
                    wallpaperPath = savedPath
                    preferences.edit().putString("home_wallpaper_path", savedPath).apply()
                } else {
                    Toast.makeText(context, "Unable to use this image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val homeBackground = nekosBackgroundColor(backgroundPreset)

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

    fun updateDock(updated: List<String?>) {
        dockPackages = updated
        saveDockPackages(context, updated)
    }

    fun toggleDock(packageName: String) {
        if (packageName in dockPackages.filterNotNull()) {
            updateDock(removeDockPackage(dockPackages, packageName))
            return
        }
        when (val result = addDockPackage(dockPackages, packageName)) {
            is DockSlotOperation.Updated -> {
                updateDock(result.packages)
                if (packageName in favoritePackages.filterNotNull()) {
                    updateFavorites(favoritePackages.map { if (it == packageName) null else it })
                }
            }
            DockSlotOperation.Unchanged -> Unit
            DockSlotOperation.Full -> Toast.makeText(
                context,
                "Dock is full",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun addFavorite(packageName: String) {
        when (val result = addFavoriteToSlots(favoritePackages, packageName)) {
            is FavoriteSlotOperation.Updated -> {
                updateFavorites(result.packages)
                if (packageName in dockPackages.filterNotNull()) {
                    updateDock(removeDockPackage(dockPackages, packageName))
                }
            }
            FavoriteSlotOperation.Unchanged -> Unit
            FavoriteSlotOperation.Full -> Toast.makeText(
                context,
                "Favorite slots are full",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun placeFavoriteFromDrawer(packageName: String, targetIndex: Int) {
        when (val result = placePackageInFavoriteSlot(favoritePackages, packageName, targetIndex)) {
            is FavoriteSlotOperation.Updated -> {
                updateFavorites(result.packages)
                if (packageName in dockPackages.filterNotNull()) {
                    updateDock(removeDockPackage(dockPackages, packageName))
                }
            }
            FavoriteSlotOperation.Unchanged -> Unit
            FavoriteSlotOperation.Full -> Toast.makeText(
                context,
                "Favorite slots are full",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(refreshToken) {
        // PackageManager queries and icon loading can be expensive on devices
        // with many installed apps, so keep discovery off the main thread.
        val refreshedApps = withContext(Dispatchers.IO) {
            loadLaunchableApps(context)
        }
        apps = refreshedApps
        val installedPackages = refreshedApps.mapTo(mutableSetOf<String>()) { it.packageName }
        val cleanedReferences = cleanupLauncherReferences(
            favorites = favoritePackages,
            tags = tags,
            installedPackages = installedPackages
        )
        if (cleanedReferences.favorites != favoritePackages) {
            favoritePackages = cleanedReferences.favorites
            saveFavoritePackages(context, cleanedReferences.favorites)
        }
        if (cleanedReferences.tags != tags) saveTags(cleanedReferences.tags)
        val cleanedDock = normalizeDockPackages(
            dockPackages.map { packageName -> packageName?.takeIf { it in installedPackages } }
        )
        if (cleanedDock != dockPackages) {
            dockPackages = cleanedDock
            saveDockPackages(context, cleanedDock)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(WarmBackground)) {
        val drawerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val homeSwipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val drawerCloseThresholdPx = drawerHeightPx * 0.18f
        val backgroundBlurRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
        val drawerOffset = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()

        BackHandler(enabled = showSettings || actionMenuApp != null || drawerOffset.value < 1f) {
            when {
                showSettings -> showSettings = false
                actionMenuApp != null -> actionMenuApp = null
                else -> scope.launch {
                    drawerOffset.animateTo(
                        1f,
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = 420f
                        )
                    )
                }
            }
        }

        HomeScreen(
            apps = apps,
            backgroundColor = homeBackground,
            wallpaper = wallpaperBitmap,
            showDock = showDock,
            onOpenSettings = { showSettings = true },
            favoritePackages = favoritePackages,
            dockPackages = dockPackages,
            tags = tags,
            homeSwipeThresholdPx = homeSwipeThresholdPx,
            backgroundBlurRadiusPx = if (favoriteDrag?.hasMoved == true) {
                0f
            } else {
                backgroundBlurRadiusPx * (1f - drawerOffset.value)
            },
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
            onFavoriteClick = { packageName, bounds ->
                launchApp(context, packageName, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
            },
            onDockClick = { packageName, bounds ->
                launchApp(context, packageName, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
            },
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
                cancelDropBounds = null
                favoriteDrag = interactionController.startDrag(
                    app.packageName,
                    LauncherDragSource.FAVORITE,
                    position
                )
            },
            onFavoriteDrag = { position ->
                val target = favoriteSlotBounds.entries.firstOrNull { it.value.contains(position) }?.key
                favoriteDrag = interactionController.updateDrag(
                    position, target,
                    cancelTarget = favoriteDrag?.supportsCancelDropTarget == true && cancelDropBounds?.contains(position) == true
                )
            },
            onFavoriteDragEnd = {
                val drag = interactionController.finishDrag()
                if (drag?.hasMoved == true && !drag.cancelTarget) {
                    drag.targetIndex?.let { placeFavoriteFromDrawer(drag.packageName, it) }
                }
                favoriteDrag = null
                cancelDropBounds = null
            },
            onFavoriteDragCancel = {
                interactionController.cancelDrag()
                favoriteDrag = null
                cancelDropBounds = null
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
                tags = tags,
                iconBitmapCache = iconBitmapCache,
                closeThresholdPx = drawerCloseThresholdPx,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Hide the entire drawer during a drag so no grid rows remain
                        // visible over the Home Favorite area.
                        translationY = if (favoriteDrag?.hasMoved == true) {
                            -size.height
                        } else {
                            drawerOffset.value * size.height
                        }
                    },
                onAppClick = { app, bounds ->
                    launchApp(context, app.packageName, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
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
                    cancelDropBounds = null
                    favoriteDrag = interactionController.startDrag(
                        app.packageName,
                        LauncherDragSource.APP_DRAWER,
                        position
                    )
                },
                onAppDrag = { position ->
                    val target = favoriteSlotBounds.entries.firstOrNull { it.value.contains(position) }?.key
                    favoriteDrag = interactionController.updateDrag(
                        position, target,
                        cancelTarget = favoriteDrag?.supportsCancelDropTarget == true && cancelDropBounds?.contains(position) == true
                    )
                },
                onAppDragEnd = {
                    val drag = interactionController.finishDrag()
                    if (drag?.hasMoved == true && !drag.cancelTarget) {
                        drag.targetIndex?.let { placeFavoriteFromDrawer(drag.packageName, it) }
                    }
                    favoriteDrag = null
                    cancelDropBounds = null
                },
                onAppDragCancel = {
                    interactionController.cancelDrag()
                    favoriteDrag = null
                    cancelDropBounds = null
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
            val isDocked = app.packageName in dockPackages.filterNotNull()
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
                LauncherAppActionMenuContent(
                    app = app,
                    isFavorite = isFavorite,
                    isDocked = isDocked,
                    isInteractive = isInteractive,
                    onAppInfo = {
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
                    onDock = {
                        toggleDock(app.packageName)
                        actionMenuApp = null
                    },
                    onUninstall = {
                        actionMenuApp = null
                        requestAppUninstall(context, app.packageName)
                    }
                )
            }
        }

        favoriteDrag?.takeIf { it.hasMoved && it.supportsCancelDropTarget }?.let { drag ->
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .onGloballyPositioned { cancelDropBounds = it.boundsInRoot() }
                    .zIndex(12f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(50))
                    .background(if (drag.cancelTarget) Color(0xFFB3261E) else Color(0xFF6D625C)),
                    contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 36.sp, lineHeight = 36.sp)
                }
                Text("Cancel", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

        if (showSettings) {
            NekOSSettingsDialog(
                backgroundPreset = backgroundPreset,
                showDock = showDock,
                hasWallpaper = wallpaperPath != null,
                onBackgroundPresetChange = { preset ->
                    backgroundPreset = preset
                    preferences.edit().putString("background_preset", preset).apply()
                },
                onShowDockChange = { visible ->
                    showDock = visible
                    preferences.edit().putBoolean("show_dock", visible).apply()
                },
                onChooseWallpaper = {
                    wallpaperPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearWallpaper = {
                    clearHomeWallpaper(context)
                    wallpaperPath = null
                    preferences.edit().remove("home_wallpaper_path").apply()
                },
                onDismiss = { showSettings = false }
            )
        }

        viewingTag?.let { tag ->
            TagAppsDialog(
                tag = tag,
                apps = apps,
                onLaunch = { app ->
                    viewingTag = null
                    launchApp(context, app.packageName, launchSourceView)
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
    backgroundColor: Color,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    showDock: Boolean,
    onOpenSettings: () -> Unit,
    favoritePackages: List<String?>,
    dockPackages: List<String?>,
    tags: List<NekTag>,
    homeSwipeThresholdPx: Float,
    backgroundBlurRadiusPx: Float,
    onDrawerDrag: (Float) -> Unit,
    onDrawerDragEnd: (Float) -> Unit,
    onFavoriteClick: (String, Rect) -> Unit,
    onDockClick: (String, Rect) -> Unit,
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
    Box(Modifier.fillMaxSize().background(backgroundColor)) {
        wallpaper?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
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
            )
        }
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
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.text_logo_sec),
                        contentDescription = "NekOS",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.offset(x = (-12).dp).height(72.dp).weight(1f, fill = false)
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings", color = Ink, fontSize = 13.sp)
                    }
                }
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
                                onClick = { bounds -> app?.let { onFavoriteClick(it.packageName, bounds) } },
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
                if (showDock && dockPackages.any { it != null }) {
                    DockBar(
                        dockPackages = dockPackages,
                        appByPackage = appByPackage,
                        onAppClick = onDockClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBar(
    dockPackages: List<String?>,
    appByPackage: Map<String, InstalledApp>,
    onAppClick: (String, Rect) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TagSurface.copy(alpha = 0.86f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val app = dockPackages.getOrNull(index)?.let { appByPackage[it] }
            var slotCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val currentSlotCoordinates by rememberUpdatedState(slotCoordinates)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .onGloballyPositioned { slotCoordinates = it }
                    .clickable(enabled = app != null) {
                        app?.let {
                            onAppClick(it.packageName, currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    val bitmap = remember(app.packageName) {
                        app.icon.toBitmap(96, 96).asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(13.dp))
                    )
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
    onClick: (Rect) -> Unit,
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
            .background(if (isDropTarget) Color(0xFFDCD3C4) else Color.White.copy(alpha = 0.24f))
            .onGloballyPositioned {
                slotCoordinates = it
                onBoundsChanged(it.boundsInRoot())
            }
            .pointerInput(app?.packageName) {
                if (app != null) {
                    detectLauncherItemGesture(
                        toRootPosition = { local -> currentSlotCoordinates?.localToRoot(local) ?: local },
                        onTap = { onClick(currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero) },
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
                modifier = Modifier.size(50.dp)
            )
        }
    }
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
        if (tagIcon != null) Image(tagIcon!!, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(tag.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
    tags: List<NekTag>,
    iconBitmapCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap>,
    closeThresholdPx: Float,
    modifier: Modifier = Modifier,
    onAppClick: (InstalledApp, Rect) -> Unit,
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
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    val currentOnCloseDrawer by rememberUpdatedState(onCloseDrawer)
    val selectedTag = tags.firstOrNull { it.id == selectedTagId }
    LaunchedEffect(tags, selectedTagId) {
        if (selectedTagId != null && selectedTag == null) selectedTagId = null
    }
    val filteredApps = remember(apps, searchQuery, selectedTag?.appPackages) {
        val tagPackages = selectedTag?.appPackages?.toSet()
        apps.filter { app ->
            matchesAppDrawerFilter(
                packageName = app.packageName,
                label = app.label,
                query = searchQuery,
                tagPackages = tagPackages
            )
        }.sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = modifier
            .background(WarmBackground.copy(alpha = 0.98f))
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
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps", color = Muted) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedContainerColor = TagSurface.copy(alpha = 0.78f),
                unfocusedContainerColor = TagSurface.copy(alpha = 0.58f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Ink,
                focusedPlaceholderColor = Muted,
                unfocusedPlaceholderColor = Muted
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerTagFilterChip(
                    label = "All",
                    color = TagSurface,
                    selected = selectedTagId == null,
                    onClick = { selectedTagId = null }
                )
                tags.forEach { tag ->
                    DrawerTagFilterChip(
                        label = tag.name,
                        color = Color(tag.colorArgb),
                        selected = selectedTagId == tag.id,
                        onClick = { selectedTagId = tag.id }
                    )
                }
            }
        }
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
                        onClick = { bounds -> onAppClick(app, bounds) },
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

@Composable
private fun DrawerTagFilterChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Ink else color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: InstalledApp,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: (Rect) -> Unit,
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
            .padding(vertical = 7.dp)
            .onGloballyPositioned { itemCoordinates = it }
            .pointerInput(app.packageName) {
                detectLauncherItemGesture(
                    toRootPosition = { local -> currentItemCoordinates?.localToRoot(local) ?: local },
                    onTap = { onClick(currentItemCoordinates?.boundsInRoot() ?: Rect.Zero) },
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
                modifier = Modifier.size(52.dp)
            )
        } else {
            Spacer(Modifier.size(56.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            app.label,
            color = Ink,
            fontSize = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.heightIn(min = 28.dp)
        )
    }
}