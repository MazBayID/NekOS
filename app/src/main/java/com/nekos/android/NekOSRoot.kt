package com.nekos.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun NekOSRoot(refreshToken: Int, resumeToken: Int = 0, homeSignal: Int = 0) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val launchSourceView = LocalView.current
    val iconCatalogRepository = remember(context) { IconCatalogRepository(context) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var tags by remember { mutableStateOf(TagStore.load(context)) }
    var activeTagPage by remember { mutableIntStateOf(0) }
    var isInitialLoadComplete by remember { mutableStateOf(false) }
    var lastAppliedRefreshToken by remember { mutableIntStateOf(-1) }

    val latestFavoritePackages by rememberUpdatedState(favoritePackages)
    val latestTags by rememberUpdatedState(tags)

    // Discover launchable apps while the brand splash is visible instead of
    // waiting for an artificial timer before starting initialization.
    LaunchedEffect(refreshToken, resumeToken) {
        val refreshedCatalog = withContext(Dispatchers.IO) {
            loadLaunchableApps(context)
        }
        val refreshedApps = refreshedCatalog.apps
        // A resume-only refresh that found the same apps keeps the current list, so decoded
        // icons are reused and Favorite icons do not re-animate every time Home is shown.
        if (refreshToken != lastAppliedRefreshToken || !hasSameAppCatalog(apps, refreshedApps)) {
            apps = refreshedApps
        }
        lastAppliedRefreshToken = refreshToken
        val currentProfileSerialNumber = context
            .getSystemService(UserManager::class.java)
            .getSerialNumberForUser(Process.myUserHandle())
        // Legacy data stored only package names because earlier versions queried
        // the current profile only. Upgrade those references before profile-aware cleanup.
        val currentProfileAppIds = refreshedApps
            .filter { it.profileSerialNumber == currentProfileSerialNumber }
            .associate { it.packageName to it.id }
        val migratedReferences = migrateLegacyLauncherReferences(
            favorites = latestFavoritePackages,
            tags = latestTags,
            currentProfileAppIds = currentProfileAppIds
        )
        val installedAppIds = refreshedApps.mapTo(mutableSetOf<String>()) { it.id }
        // Read the latest user-edited collections after the background query so
        // an in-flight refresh cannot overwrite a Favorite or Tag edit.
        val cleanedReferences = cleanupLauncherReferences(
            favorites = migratedReferences.favorites,
            tags = migratedReferences.tags,
            installedAppIds = installedAppIds,
            unavailableProfileSerialNumbers = refreshedCatalog.unavailableProfileSerialNumbers
        )
        if (cleanedReferences.favorites != latestFavoritePackages) {
            favoritePackages = cleanedReferences.favorites
            saveFavoritePackages(context, cleanedReferences.favorites)
        }
        if (cleanedReferences.tags != latestTags) {
            tags = cleanedReferences.tags
            TagStore.save(context, cleanedReferences.tags)
        }
        isInitialLoadComplete = true
    }

    if (!isInitialLoadComplete) {
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

    val iconBitmapCache = remember(apps) { AppIconBitmapCache() }
    val interactionController = remember { LauncherInteractionController() }
    var favoriteDrag by remember { mutableStateOf<LauncherDragSession?>(null) }
    val favoriteSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var cancelDropBounds by remember { mutableStateOf<Rect?>(null) }
    var tagEditorTarget by remember { mutableStateOf<NekTag?>(null) }
    var viewingTag by remember { mutableStateOf<NekTag?>(null) }
    var actionMenuTag by remember { mutableStateOf<NekTag?>(null) }
    var tagPendingDelete by remember { mutableStateOf<NekTag?>(null) }
    val tagPillBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedTagId by remember { mutableStateOf<String?>(null) }
    var draggedTagPreview by remember { mutableStateOf<NekTag?>(null) }
    var draggedTagPosition by remember { mutableStateOf(Offset.Zero) }
    var tagDropTargetId by remember { mutableStateOf<String?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var actionMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var actionMenuPreviewApp by remember { mutableStateOf<InstalledApp?>(null) }
    var actionMenuPreviewPosition by remember { mutableStateOf(Offset.Zero) }
    var actionMenuPopupSize by remember { mutableStateOf(IntSize.Zero) }
    val preferences = remember(context) {
        context.getSharedPreferences("nekos_preferences", Context.MODE_PRIVATE)
    }
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    }
    var showFirstLaunchTip by remember {
        mutableStateOf(!preferences.getBoolean("has_seen_drawer_tip", false))
    }
    var showSettings by remember { mutableStateOf(false) }
    var backgroundPreset by remember {
        mutableStateOf(preferences.getString("background_preset", "warm") ?: "warm")
    }
    var wallpaperPath by remember {
        mutableStateOf(preferences.getString("home_wallpaper_path", null))
    }
    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    // The wallpaper is always saved to the same file, so the path alone cannot signal a new
    // image. The revision makes choosing a second wallpaper reload it immediately.
    var wallpaperRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(wallpaperPath, wallpaperRevision) {
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
                    wallpaperRevision++
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

    fun addFavorite(appId: String) {
        when (val result = addFavoriteToSlots(favoritePackages, appId)) {
            is FavoriteSlotOperation.Updated -> {
                updateFavorites(result.packages)
            }
            FavoriteSlotOperation.Unchanged -> Unit
            FavoriteSlotOperation.Full -> Toast.makeText(
                context,
                "Favorite slots are full",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun placeFavoriteFromDrawer(appId: String, targetIndex: Int) {
        when (val result = placePackageInFavoriteSlot(favoritePackages, appId, targetIndex)) {
            is FavoriteSlotOperation.Updated -> {
                updateFavorites(result.packages)
            }
            FavoriteSlotOperation.Unchanged -> Unit
            FavoriteSlotOperation.Full -> Toast.makeText(
                context,
                "Favorite slots are full",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(WarmBackground)) {
        val drawerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val homeSwipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        val drawerCloseThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
        val backgroundBlurRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
        var drawerProgress by remember { mutableFloatStateOf(1f) }
        var drawerTargetProgress by remember { mutableFloatStateOf(1f) }
        var isDrawerDragging by remember { mutableStateOf(false) }
        // Pointer movement updates one shared progress value directly. A spring
        // is used only to settle after release, never once per pointer event.
        LaunchedEffect(drawerTargetProgress, isDrawerDragging) {
            if (!isDrawerDragging) {
                animate(
                    initialValue = drawerProgress,
                    targetValue = drawerTargetProgress,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) { value, _ -> drawerProgress = value }
            }
        }
        fun closeDrawer() {
            focusManager.clearFocus()
            isDrawerDragging = false
            drawerTargetProgress = 1f
        }

        // Pressing Home while NekOS is open returns to a clean Home screen. An in-progress
        // tag edit is left alone so the user does not lose what they typed.
        LaunchedEffect(homeSignal) {
            if (homeSignal > 0) {
                closeDrawer()
                showSettings = false
                actionMenuApp = null
                actionMenuPreviewApp = null
                viewingTag = null
                actionMenuTag = null
                tagPendingDelete = null
            }
        }

        BackHandler(enabled = showSettings || actionMenuApp != null || drawerProgress < 1f) {
            when {
                showSettings -> showSettings = false
                actionMenuApp != null -> actionMenuApp = null
                else -> closeDrawer()
            }
        }

        HomeScreen(
            apps = apps,
            backgroundColor = homeBackground,
            wallpaper = wallpaperBitmap,
            onOpenSettings = { showSettings = true },
            favoritePackages = favoritePackages,
            tags = tags,
            activeTagPage = activeTagPage,
            onTagPageChange = { activeTagPage = it },
            iconCatalogRepository = iconCatalogRepository,
            iconBitmapCache = iconBitmapCache,
            homeSwipeThresholdPx = homeSwipeThresholdPx,
            backgroundBlurRadiusPx = if (favoriteDrag?.hasMoved == true) {
                0f
            } else {
                backgroundBlurRadiusPx * (1f - drawerProgress)
            },
            onDrawerDrag = { totalDrag ->
                isDrawerDragging = true
                drawerProgress = (1f + totalDrag / drawerHeightPx).coerceIn(0f, 1f)
            },
            onDrawerDragEnd = { totalDrag, velocityY, cancelled ->
                isDrawerDragging = false
                drawerTargetProgress = if (
                    shouldOpenDrawerAfterDrag(totalDrag, homeSwipeThresholdPx, cancelled, velocityY)
                ) 0f else 1f
            },
            onFavoriteClick = { app, bounds ->
                launchApp(context, app, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
            },
            onFavoriteLongPress = { index, position ->
                favoritePackages.getOrNull(index)?.let { packageName ->
                    apps.firstOrNull { it.id == packageName }?.let { app ->
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
                    app.id,
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
                    drag.targetIndex?.let { placeFavoriteFromDrawer(drag.appId, it) }
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
            onTagOptionsClick = { tag -> actionMenuTag = tag },
            onTagBoundsChanged = { tagId, bounds -> tagPillBounds[tagId] = bounds },
            onTagBoundsRemoved = { tagId -> tagPillBounds.remove(tagId) },
            draggedTagId = draggedTagId,
            tagDropTargetId = tagDropTargetId,
            onTagDragStart = { tag, position ->
                draggedTagId = tag.id
                draggedTagPreview = tag
                draggedTagPosition = position
                tagDropTargetId = tag.id
            },
            onTagDrag = { position ->
                draggedTagPosition = position
                tagDropTargetId = tagPillBounds.entries
                    .firstOrNull { (_, bounds) -> bounds.contains(position) }
                    ?.key
            },
            onTagDragEnd = {
                val sourceId = draggedTagId
                val targetIndex = tags.indexOfFirst { it.id == tagDropTargetId }
                if (sourceId != null && targetIndex >= 0) {
                    saveTags(moveTagToIndex(tags, sourceId, targetIndex))
                }
                draggedTagId = null
                draggedTagPreview = null
                tagDropTargetId = null
            },
            onTagDragCancel = {
                draggedTagId = null
                draggedTagPreview = null
                tagDropTargetId = null
            },
            onAddTag = {
                if (tags.size >= 10) {
                    Toast.makeText(context, "Maximum of 10 tags", Toast.LENGTH_SHORT).show()
                } else {
                    tagEditorTarget = null
                    showTagEditor = true
                }
            }
        )

        draggedTagPreview?.let { tag ->
            tagPillBounds[tag.id]?.let { bounds ->
                val previewWidth = with(LocalDensity.current) { bounds.width.toDp() }
                val previewHeight = with(LocalDensity.current) { bounds.height.toDp() }
                TagPill(
                    tag = tag,
                    iconCatalogRepository = iconCatalogRepository,
                    isDragging = true,
                    isDropTarget = false,
                    isDragPreview = true,
                    onBoundsChanged = {},
                    onBoundsRemoved = {},
                    onClick = {},
                    onHandleClick = {},
                    onDragStart = {},
                    onDrag = {},
                    onDragEnd = {},
                    onDragCancel = {},
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (draggedTagPosition.x - bounds.width / 2f).roundToInt(),
                                (draggedTagPosition.y - bounds.height / 2f).roundToInt()
                            )
                        }
                        .width(previewWidth)
                        .height(previewHeight)
                        .zIndex(11f)
                )
            }
        }

        if (drawerProgress < 1f) {
            // A subtle warm translucent veil keeps the blurred home screen legible
            // and provides a graceful dimmed fallback on Android versions below 12.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        WarmBackground.copy(
                            alpha = 0.28f * (1f - drawerProgress)
                        )
                    )
            )

            if (drawerProgress <= 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.40f)
                        .align(Alignment.TopCenter)
                        .clickable {
                            closeDrawer()
                        }
                )
            }

            // The panel keeps its 60% height but rises above the keyboard while searching,
            // so results stay visible while typing.
            val fullScreenHeight = maxHeight
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().imePadding()
            ) {
            AppDrawer(
                apps = apps,
                iconBitmapCache = iconBitmapCache,
                closeThresholdPx = drawerCloseThresholdPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minOf(fullScreenHeight * 0.60f, maxHeight))
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Hide the entire drawer during a drag so no grid rows remain
                        // visible over the Home Favorite area.
                        val isDraggingApp = favoriteDrag?.hasMoved == true
                        translationY = if (isDraggingApp) size.height * 0.12f else drawerProgress * size.height
                        alpha = if (isDraggingApp) 0f else 1f
                        scaleX = if (isDraggingApp) 0.985f else 1f
                        scaleY = if (isDraggingApp) 0.985f else 1f
                    },
                onAppClick = { app, bounds ->
                    launchApp(context, app, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
                    closeDrawer()
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
                        app.id,
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
                        drag.targetIndex?.let { placeFavoriteFromDrawer(drag.appId, it) }
                    }
                    favoriteDrag = null
                    cancelDropBounds = null
                },
                onAppDragCancel = {
                    interactionController.cancelDrag()
                    favoriteDrag = null
                    cancelDropBounds = null
                },
                onCloseDrawer = { totalDrag, velocityY ->
                    if ((totalDrag == 0f && velocityY == 0f) || shouldCloseDrawerAfterDrag(totalDrag, drawerCloseThresholdPx, velocityY)) closeDrawer()
                }
            )
            }
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
        AnimatedVisibility(
            visible = popupApp != null,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.96f, animationSpec = tween(140)),
            exit = fadeOut(tween(100))
        ) {
            popupApp?.let { app ->
            val isFavorite = app.id in favoritePackages.filterNotNull()
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
                    isInteractive = isInteractive,
                    onAppInfo = {
                        actionMenuApp = null
                        openAppInfo(context, app)
                    },
                    onFavorite = {
                        if (isFavorite) {
                            updateFavorites(favoritePackages.map { if (it == app.id) null else it })
                        } else {
                            addFavorite(app.id)
                        }
                        actionMenuApp = null
                    },
                    onUninstall = {
                        actionMenuApp = null
                        requestAppUninstall(context, app)
                    }
                )
            }
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
            val draggedApp = apps.firstOrNull { it.id == drag.appId }
            if (draggedApp != null) {
                val cachedBitmap = iconBitmapCache[draggedApp.id]
                var bitmap by remember(draggedApp.id) { mutableStateOf(cachedBitmap) }
                LaunchedEffect(draggedApp, cachedBitmap) {
                    bitmap = cachedBitmap
                    if (cachedBitmap == null) {
                        val loadedBitmap = withContext(Dispatchers.Default) {
                            draggedApp.icon.toBitmap(96, 96).asImageBitmap()
                        }
                        iconBitmapCache.put(draggedApp.id, loadedBitmap)
                        bitmap = loadedBitmap
                    }
                }
                val iconOffset = with(LocalDensity.current) { 28.dp.toPx() }
                bitmap?.let { loadedBitmap -> Image(
                    bitmap = loadedBitmap,
                    contentDescription = draggedApp.label,
                    modifier = Modifier
                        .offset { IntOffset((drag.position.x - iconOffset).roundToInt(), (drag.position.y - iconOffset).roundToInt()) }
                        .size(56.dp)
                        .zIndex(10f)
                ) }
            }
        }

        if (showSettings) {
            NekOSSettingsDialog(
                backgroundPreset = backgroundPreset,
                hasWallpaper = wallpaperPath != null,
                appVersion = appVersion,
                onBackgroundPresetChange = { preset ->
                    backgroundPreset = preset
                    preferences.edit().putString("background_preset", preset).apply()
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
                onShowDrawerHint = {
                    preferences.edit().putBoolean("has_seen_drawer_tip", false).apply()
                    showSettings = false
                    showFirstLaunchTip = true
                },
                onOpenRepository = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MazBayID/NekOS"))
                        )
                    }.onFailure {
                        Toast.makeText(context, "Unable to open GitHub", Toast.LENGTH_SHORT).show()
                    }
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
                    launchApp(context, app, launchSourceView)
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
            val tagIndex = tags.indexOfFirst { it.id == tag.id }
            val currentPage = tagIndex.coerceAtLeast(0) / 5
            val pageCount = ((tags.size + 4) / 5).coerceAtLeast(1)
            val targetPage = when {
                pageCount < 2 -> null
                currentPage == 0 -> 1
                else -> currentPage - 1
            }
            TagActionsDialog(
                tag = tag,
                movePageLabel = targetPage?.let { "Move to page ${it + 1}" },
                onMovePage = {
                    targetPage?.let { page ->
                        saveTags(moveTagToPage(tags, tag.id, page))
                        activeTagPage = page
                    }
                    actionMenuTag = null
                },
                onEdit = {
                    actionMenuTag = null
                    tagEditorTarget = tag
                    showTagEditor = true
                },
                onDelete = {
                    actionMenuTag = null
                    tagPendingDelete = tag
                },
                onDismiss = { actionMenuTag = null }
            )
        }

        if (showTagEditor) {
            TagManagementDialog(
                tag = tagEditorTarget,
                apps = apps,
                iconCatalogRepository = iconCatalogRepository,
                onSave = { saved ->
                    val duplicateName = tags.any { it.id != saved.id && it.name.equals(saved.name, ignoreCase = true) }
                    if (duplicateName) {
                        Toast.makeText(context, "Tag name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        if (tagEditorTarget == null && tags.size >= 10) {
                            Toast.makeText(context, "Maximum of 10 tags", Toast.LENGTH_SHORT).show()
                        } else {
                            if (tagEditorTarget == null) activeTagPage = tags.size / 5
                            saveTags(if (tagEditorTarget == null) tags + saved else tags.map { if (it.id == saved.id) saved else it })
                            showTagEditor = false
                            tagEditorTarget = null
                        }
                    }
                },
                onDelete = { deleted -> tagPendingDelete = deleted },
                onDismiss = { showTagEditor = false; tagEditorTarget = null }
            )
        }

        tagPendingDelete?.let { tag ->
            AlertDialog(
                onDismissRequest = { tagPendingDelete = null },
                shape = RoundedCornerShape(28.dp),
                containerColor = Color(0xFFFEFCF8),
                titleContentColor = Ink,
                textContentColor = Ink,
                title = { Text("Delete “${tag.name}”?") },
                text = { Text("Only the tag is removed. Your apps are not affected.") },
                confirmButton = {
                    TextButton(onClick = {
                        saveTags(tags.filterNot { it.id == tag.id })
                        tagPendingDelete = null
                        showTagEditor = false
                        tagEditorTarget = null
                    }) { Text("Delete", color = Color(0xFFB3261E)) }
                },
                dismissButton = {
                    TextButton(onClick = { tagPendingDelete = null }) { Text("Cancel") }
                }
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
                    Text("Swipe up from Home to open your apps. Swipe down on the handle, or tap above the panel, to return Home.")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    apps: List<InstalledApp>,
    backgroundColor: Color,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    onOpenSettings: () -> Unit,
    favoritePackages: List<String?>,
    tags: List<NekTag>,
    activeTagPage: Int,
    onTagPageChange: (Int) -> Unit,
    iconCatalogRepository: IconCatalogRepository,
    iconBitmapCache: AppIconBitmapCache,
    homeSwipeThresholdPx: Float,
    backgroundBlurRadiusPx: Float,
    onDrawerDrag: (Float) -> Unit,
    onDrawerDragEnd: (totalDrag: Float, velocityY: Float, cancelled: Boolean) -> Unit,
    onFavoriteClick: (InstalledApp, Rect) -> Unit,
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
    onTagOptionsClick: (NekTag) -> Unit,
    onTagBoundsChanged: (String, Rect) -> Unit,
    onTagBoundsRemoved: (String) -> Unit,
    draggedTagId: String?,
    tagDropTargetId: String?,
    onTagDragStart: (NekTag, Offset) -> Unit,
    onTagDrag: (Offset) -> Unit,
    onTagDragEnd: () -> Unit,
    onTagDragCancel: () -> Unit,
    onAddTag: () -> Unit
) {
    val appById = remember(apps) { apps.associateBy { it.id } }
    val currentOnDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val currentOnDrawerDragEnd by rememberUpdatedState(onDrawerDragEnd)
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
            // A restrained warm scrim keeps dark launcher labels readable over
            // bright or highly detailed wallpapers without hiding the image.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            backgroundColor.copy(alpha = 0.24f),
                            backgroundColor.copy(alpha = 0.06f),
                            backgroundColor.copy(alpha = 0.22f)
                        )
                    )
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Keep launcher controls sharp while the wallpaper behind them blurs.
                .pointerInput(homeSwipeThresholdPx) {
                    var totalDrag = 0f
                    var drawerDragStarted = false
                    var gestureEligible = false
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            totalDrag = 0f
                            drawerDragStarted = false
                            // The swipe can start in the main Home canvas or the
                            // Favorite band; the scrolling Tag rail stays free.
                            gestureEligible = isHomeDrawerGestureEligible(
                                positionX = position.x,
                                positionY = position.y,
                                containerWidth = size.width.toFloat(),
                                containerHeight = size.height.toFloat(),
                                favoriteBandPx = 244.dp.toPx()
                            )
                            velocityTracker.resetTracking()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (gestureEligible) {
                                totalDrag += dragAmount
                                if (totalDrag < 0f) {
                                    drawerDragStarted = true
                                    currentOnDrawerDrag(totalDrag)
                                    change.consume()
                                }
                            }
                        },
                        onDragEnd = {
                            if (drawerDragStarted) {
                                currentOnDrawerDragEnd(totalDrag, velocityTracker.calculateVelocity().y, false)
                            }
                            totalDrag = 0f
                            drawerDragStarted = false
                            gestureEligible = false
                        },
                        onDragCancel = {
                            if (drawerDragStarted) currentOnDrawerDragEnd(totalDrag, 0f, true)
                            totalDrag = 0f
                            drawerDragStarted = false
                            velocityTracker.resetTracking()
                            gestureEligible = false
                        }
                    )
                }
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                // Give the tag rail enough room for readable labels while keeping
                // the logo area useful on compact phone screens.
                val tagRailWidth = (maxWidth - 16.dp) * 0.38f
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Image(
                                painter = painterResource(R.drawable.text_logo_sec),
                                contentDescription = "NekOS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.offset(x = (-12).dp).height(72.dp).weight(1f)
                            )
                            Box(
                                modifier = Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TagSurface.copy(alpha = 0.72f))
                                    .clickable(onClick = onOpenSettings)
                                    .semantics { contentDescription = "Open launcher settings"; role = Role.Button },
                                contentAlignment = Alignment.Center
                            ) { Text("⚙︎", color = Ink, fontSize = 19.sp) }
                        }
                    }
                    val tagPageCount = ((tags.size + 4) / 5).coerceAtLeast(1)
                    val safeTagPage = activeTagPage.coerceIn(0, tagPageCount - 1)
                    val visibleTags = tags.drop(safeTagPage * 5).take(5)
                    LaunchedEffect(tagPageCount, activeTagPage) {
                        if (activeTagPage != safeTagPage) onTagPageChange(safeTagPage)
                    }
                    Box(
                        modifier = Modifier.width(tagRailWidth).fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(
                                horizontal = 9.dp,
                                vertical = 12.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.nekos_ui_tag_frame),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = maxHeight * 0.5f - 40.dp)
                                        .requiredWidth(tagRailWidth * 1.1f)
                                        .fillMaxHeight(0.65f)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                        .pointerInput(tagPageCount, safeTagPage, draggedTagId) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onHorizontalDrag = { change, dragAmount ->
                                                    if (draggedTagId == null) {
                                                        totalDrag += dragAmount
                                                        change.consume()
                                                    }
                                                },
                                                onDragEnd = {
                                                    when {
                                                        totalDrag <= -48.dp.toPx() && safeTagPage < tagPageCount - 1 ->
                                                            onTagPageChange(safeTagPage + 1)
                                                        totalDrag >= 48.dp.toPx() && safeTagPage > 0 ->
                                                            onTagPageChange(safeTagPage - 1)
                                                    }
                                                    totalDrag = 0f
                                                },
                                                onDragCancel = { totalDrag = 0f }
                                            )
                                        },
                                    reverseLayout = true,
                                    userScrollEnabled = false,
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
                                ) {
                                    items(visibleTags, key = { it.id }) { tag ->
                                        TagPill(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = 420f
                                                ),
                                                fadeOutSpec = null
                                            ),
                                            tag = tag,
                                            iconCatalogRepository = iconCatalogRepository,
                                            isDragging = draggedTagId == tag.id,
                                            isDropTarget = tagDropTargetId == tag.id && draggedTagId != tag.id,
                                            onBoundsChanged = { bounds -> onTagBoundsChanged(tag.id, bounds) },
                                            onBoundsRemoved = { onTagBoundsRemoved(tag.id) },
                                            onClick = { onTagClick(tag) },
                                            onHandleClick = { onTagOptionsClick(tag) },
                                            onDragStart = { position -> onTagDragStart(tag, position) },
                                            onDrag = onTagDrag,
                                            onDragEnd = onTagDragEnd,
                                            onDragCancel = onTagDragCancel
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(28.dp)
                            ) {
                                if (tagPageCount > 1) {
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(tagPageCount) { page ->
                                            Box(
                                                modifier = Modifier.size(18.dp)
                                                    .clickable { onTagPageChange(page) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(if (page == safeTagPage) 7.dp else 5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (page == safeTagPage) Ink
                                                            else Muted.copy(alpha = 0.45f)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .offset(x = tagRailWidth * 0.05f - 7.dp)
                                        .size(28.dp)
                                        .clickable(onClick = onAddTag)
                                        .semantics {
                                            contentDescription = "Add tag"
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.nekos_ui_add_tag),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(5) { column ->
                            val index = row * 5 + column
                            val app = favoritePackages.getOrNull(index)?.let { appById[it] }
                            FavoriteSlot(
                                app = app,
                                iconBitmapCache = iconBitmapCache,
                                onBoundsChanged = { bounds -> onFavoriteSlotBounds(index, bounds) },
                                isDropTarget = favoriteDropTarget == index,
                                onClick = { bounds -> app?.let { onFavoriteClick(it, bounds) } },
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
    iconBitmapCache: AppIconBitmapCache,
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
    val cachedBitmap = app?.let { iconBitmapCache[it.id] }
    var bitmap by remember(app?.id) { mutableStateOf(cachedBitmap) }
    LaunchedEffect(app, cachedBitmap) {
        bitmap = cachedBitmap
        val currentApp = app ?: return@LaunchedEffect
        if (cachedBitmap == null) {
            val loadedBitmap = withContext(Dispatchers.Default) {
                currentApp.icon.toBitmap(96, 96).asImageBitmap()
            }
            iconBitmapCache.put(currentApp.id, loadedBitmap)
            bitmap = loadedBitmap
        }
    }
    val slotBackground by animateColorAsState(
        targetValue = if (isDropTarget) Color(0xFFDCD3C4) else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f),
        label = "favorite-drop-target"
    )
    var slotCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSlotCoordinates by rememberUpdatedState(slotCoordinates)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnLongPressCancel by rememberUpdatedState(onLongPressCancel)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(slotBackground)
            .onGloballyPositioned {
                slotCoordinates = it
                onBoundsChanged(it.boundsInRoot())
            }
            .pointerInput(app?.id) {
                if (app != null) {
                    detectLauncherItemGesture(
                        toRootPosition = { local -> currentSlotCoordinates?.localToRoot(local) ?: local },
                        onTap = { currentOnClick(currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero) },
                        onLongPress = currentOnLongPress,
                        onLongPressEnd = currentOnLongPressEnd,
                        onLongPressCancel = currentOnLongPressCancel,
                        onDragStart = currentOnDragStart,
                        onDrag = currentOnDrag,
                        onDragEnd = currentOnDragEnd,
                        onDragCancel = currentOnDragCancel
                    )
                }
            }
            .padding(5.dp)
            .then(
                if (app != null) {
                    Modifier.launcherItemSemantics(
                        appLabel = app.label,
                        onActivate = {
                            onClick(currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero)
                        },
                        onShowActions = {
                            onLongPress(currentSlotCoordinates?.boundsInRoot()?.center ?: Offset.Zero)
                            onLongPressEnd()
                        }
                    )
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconBitmap = bitmap
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (app == null) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(TagSurface.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Muted.copy(alpha = 0.72f), fontSize = 17.sp) }
            }
            this@Column.AnimatedVisibility(
                visible = app != null && iconBitmap != null,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.90f,
                    animationSpec = tween(180)
                )
            ) {
                if (app != null && iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.label,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(
    tag: NekTag,
    iconCatalogRepository: IconCatalogRepository,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onBoundsRemoved: (String) -> Unit,
    onClick: () -> Unit,
    onHandleClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isDragPreview: Boolean = false
) {
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.045f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tag-drag-scale"
    )
    val dragAlpha by animateFloatAsState(
        targetValue = when {
            isDragging && isDragPreview -> 0.94f
            isDragging -> 0f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tag-drag-alpha"
    )
    val dragElevation by animateDpAsState(
        targetValue = when {
            isDragging -> 10.dp
            isDropTarget -> 2.dp
            else -> 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tag-drag-elevation"
    )
    val pillColor by animateColorAsState(
        targetValue = if (isDropTarget) Color(0xFFD5C8B5) else Color(tag.colorArgb.toInt()),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tag-drop-color"
    )
    var tagIcon by remember(tag.iconId) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentHandleCoordinates by rememberUpdatedState(handleCoordinates)
    val currentOnHandleClick by rememberUpdatedState(onHandleClick)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    DisposableEffect(tag.id) {
        onDispose { onBoundsRemoved(tag.id) }
    }
    LaunchedEffect(tag.iconId) {
        tagIcon = withContext(Dispatchers.IO) {
            runCatching {
                tag.iconId?.let { id ->
                    iconCatalogRepository.loadCatalog().firstOrNull { it.id == id }?.let(iconCatalogRepository::loadBitmap)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                alpha = dragAlpha
                scaleX = dragScale
                scaleY = dragScale
            }
            .shadow(dragElevation, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(pillColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 10.dp, top = 11.dp, bottom = 11.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tagIcon != null) Image(tagIcon!!, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                tag.name,
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp)
            )
        }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 40.dp, minHeight = 44.dp)
                .onGloballyPositioned { handleCoordinates = it }
                .pointerInput(tag.id) {
                    detectLauncherItemGesture(
                        toRootPosition = { local ->
                            currentHandleCoordinates?.localToRoot(local) ?: local
                        },
                        onTap = currentOnHandleClick,
                        onLongPress = {},
                        onLongPressEnd = {},
                        onLongPressCancel = {},
                        onDragStart = currentOnDragStart,
                        onDrag = currentOnDrag,
                        onDragEnd = currentOnDragEnd,
                        onDragCancel = currentOnDragCancel
                    )
                }
                .semantics {
                    contentDescription = "Options for ${tag.name}; hold and drag to reorder"
                    role = Role.Button
                    onClick(label = "Open ${tag.name} options") {
                        onHandleClick()
                        true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("⠿", color = Ink.copy(alpha = 0.72f), fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    apps: List<InstalledApp>,
    iconBitmapCache: AppIconBitmapCache,
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
    onCloseDrawer: (totalDrag: Float, velocityY: Float) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentOnCloseDrawer by rememberUpdatedState(onCloseDrawer)
    val filteredApps = remember(apps, searchQuery) {
        apps.filter { app ->
            matchesAppDrawerSearch(
                label = app.label,
                query = searchQuery
            )
        }
    }
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    // Dragging the list hides the keyboard so results can be browsed. Programmatic scrolls
    // (such as jumping back to the top when the query changes) do not reach this connection.
    val dismissKeyboardOnScroll = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(searchQuery) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(WarmBackground.copy(alpha = 0.98f))
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable { currentOnCloseDrawer(0f, 0f) }
                .pointerInput(closeThresholdPx) {
                    var totalDrag = 0f
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            velocityTracker.resetTracking()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            totalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            currentOnCloseDrawer(totalDrag, velocityTracker.calculateVelocity().y)
                            totalDrag = 0f
                        },
                        onDragCancel = {
                            totalDrag = 0f
                            velocityTracker.resetTracking()
                        }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Applications",
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (searchQuery.isBlank()) "${apps.size} installed" else "${filteredApps.size} results",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps", color = Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Clear", color = Muted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No apps match your search", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                // Adaptive sizing avoids cramped labels on narrow phones and
                // uses the available width more naturally on larger displays.
                columns = GridCells.Adaptive(minSize = 68.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(dismissKeyboardOnScroll)
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    val cachedBitmap = iconBitmapCache[app.id]
                    var bitmap by remember(app.id) {
                        mutableStateOf(cachedBitmap)
                    }
                    LaunchedEffect(app, cachedBitmap) {
                        bitmap = cachedBitmap
                        if (cachedBitmap == null) {
                            val loadedBitmap = withContext(Dispatchers.Default) {
                                app.icon.toBitmap(96, 96).asImageBitmap()
                            }
                            iconBitmapCache.put(app.id, loadedBitmap)
                            bitmap = loadedBitmap
                        }
                    }
                    AppGridItem(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = 380f
                            ),
                            fadeOutSpec = null
                        ),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    modifier: Modifier = Modifier,
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
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnLongPressCancel by rememberUpdatedState(onLongPressCancel)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 7.dp)
            .onGloballyPositioned { itemCoordinates = it }
            .pointerInput(app.id) {
                detectLauncherItemGesture(
                    toRootPosition = { local -> currentItemCoordinates?.localToRoot(local) ?: local },
                    onTap = { currentOnClick(currentItemCoordinates?.boundsInRoot() ?: Rect.Zero) },
                    onLongPress = currentOnLongPress,
                    onLongPressEnd = currentOnLongPressEnd,
                    onLongPressCancel = currentOnLongPressCancel,
                    onDragStart = currentOnDragStart,
                    onDrag = currentOnDrag,
                    onDragEnd = currentOnDragEnd,
                    onDragCancel = currentOnDragCancel
                )
            }
            .launcherItemSemantics(
                appLabel = app.label,
                onActivate = {
                    onClick(currentItemCoordinates?.boundsInRoot() ?: Rect.Zero)
                },
                onShowActions = {
                    onLongPress(currentItemCoordinates?.boundsInRoot()?.center ?: Offset.Zero)
                    onLongPressEnd()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            this@Column.AnimatedVisibility(
                visible = bitmap != null,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.90f,
                    animationSpec = tween(180)
                )
            ) {
                bitmap?.let { loadedBitmap ->
                    Image(
                        bitmap = loadedBitmap,
                        contentDescription = app.label,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
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
