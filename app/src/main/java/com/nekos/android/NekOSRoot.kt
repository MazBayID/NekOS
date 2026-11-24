package com.nekos.android

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.app.role.RoleManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

private val WarmBackground = Color(0xFFF4F1EA)
private val Ink = Color(0xFF34332F)
private val Muted = Color(0xFF8B8982)
private val TagSurface = Color(0xFFE7E1D6)

@Composable
internal fun NekOSRoot(
    refreshToken: Int,
    resumeToken: Int = 0,
    homeSignal: Int = 0,
    backgroundSignal: Int = 0,
    skipStartupSplash: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val launchSourceView = LocalView.current
    var isDefaultLauncher by remember { mutableStateOf(isNekOSDefaultHome(context)) }
    LaunchedEffect(resumeToken) {
        isDefaultLauncher = isNekOSDefaultHome(context)
    }
    val iconCatalogRepository = remember(context) { IconCatalogRepository(context) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var favoritePackages by remember { mutableStateOf(loadFavoritePackages(context)) }
    var favoriteAppSnapshots by remember {
        mutableStateOf(FavoriteAppSnapshotStore.load(context))
    }
    var tags by remember { mutableStateOf(TagStore.load(context)) }
    var favoriteOrderSnapshot by remember { mutableStateOf<List<String?>?>(null) }
    var tagOrderSnapshot by remember { mutableStateOf<List<NekTag>?>(null) }
    var activeTagPage by remember { mutableIntStateOf(0) }
    // Restored launcher tasks render Home immediately while the app catalog refreshes.
    var isInitialLoadComplete by remember { mutableStateOf(skipStartupSplash) }
    var lastAppliedRefreshToken by remember { mutableIntStateOf(-1) }
    var catalogLoadFailed by remember { mutableStateOf(false) }

    val latestFavoritePackages by rememberUpdatedState(favoritePackages)
    val latestFavoriteAppSnapshots by rememberUpdatedState(favoriteAppSnapshots)
    val latestTags by rememberUpdatedState(tags)

    // Discover launchable apps while the brand splash is visible instead of
    // waiting for an artificial timer before starting initialization.
    LaunchedEffect(refreshToken, resumeToken) {
        // A normal resume must not rescan a healthy catalog. It only retries a
        // previous failed scan, covering transient package/profile service errors.
        if (refreshToken == lastAppliedRefreshToken && !catalogLoadFailed) {
            return@LaunchedEffect
        }
        val refreshedCatalog = try {
            withContext(Dispatchers.IO) {
                loadLaunchableApps(context)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last known catalog and user collections intact when a
            // transient package/profile query fails. The next resume retries it.
            catalogLoadFailed = true
            Toast.makeText(context, "Unable to load apps. Try again shortly.", Toast.LENGTH_SHORT).show()
            if (!isInitialLoadComplete) isInitialLoadComplete = true
            return@LaunchedEffect
        }
        catalogLoadFailed = false
        val refreshedApps = refreshedCatalog.apps
        // This effect runs only for initial load or a real package/profile refresh.
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
        val activeFavoriteIds = cleanedReferences.favorites.filterNotNull().toSet()
        val retainedSnapshots = latestFavoriteAppSnapshots.filterKeys { it in activeFavoriteIds }
        val refreshedSnapshots = refreshedApps
            .filter { it.id in activeFavoriteIds }
            .associate { app ->
                app.id to FavoriteAppSnapshot(
                    appId = app.id,
                    label = app.label,
                    iconCacheKey = app.iconCacheKey
                )
            }
        favoriteAppSnapshots = retainedSnapshots + refreshedSnapshots
        FavoriteAppSnapshotStore.save(context, favoriteAppSnapshots.values)
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
            StartupLogo(
                modifier = Modifier.fillMaxWidth(0.84f)
            )
        }
        return
    }

    val iconBitmapCache = remember(context.applicationContext) {
        AppIconBitmapCache(context.applicationContext)
    }
    val interactionController = remember { LauncherInteractionController() }
    var favoriteDrag by remember { mutableStateOf<LauncherDragSession?>(null) }
    val favoriteSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var cancelDropBounds by remember { mutableStateOf<Rect?>(null) }
    var tagEditorTarget by remember { mutableStateOf<NekTag?>(null) }
    var viewingTag by remember { mutableStateOf<NekTag?>(null) }
    var actionMenuTag by remember { mutableStateOf<NekTag?>(null) }
    var tagPendingDelete by remember { mutableStateOf<NekTag?>(null) }
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
    var launcherThemeKey by remember {
        mutableStateOf(normalizeLauncherTheme(preferences.getString("launcher_theme", "porcelain")))
    }
    val launcherSurface = launcherThemeColor(launcherThemeKey)
    var launcherUiColorArgb by remember {
        mutableLongStateOf(
            normalizeLauncherUiColor(
                preferences.getLong("launcher_ui_color", DEFAULT_LAUNCHER_UI_COLOR)
            )
        )
    }
    var backgroundPreset by remember {
        mutableStateOf(
            normalizeNekOSBackgroundPreset(preferences.getString("background_preset", "warm"))
        )
    }
    var wallpaperPath by remember {
        mutableStateOf(preferences.getString("home_wallpaper_path", null))
    }
    var builtInWallpaperKey by remember {
        mutableStateOf(normalizeBuiltInWallpaperKey(
            preferences.getString("built_in_wallpaper_key", "nekos_main_wallpaper")
        ))
    }
    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var wallpaperLoadGeneration by remember { mutableIntStateOf(0) }
    // The wallpaper is always saved to the same file, so the path alone cannot signal a new
    // image. The revision makes choosing a second wallpaper reload it immediately.
    var wallpaperRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(wallpaperPath, builtInWallpaperKey, wallpaperRevision, wallpaperLoadGeneration) {
        val loadGeneration = wallpaperLoadGeneration
        val loadedWallpaper = withContext(Dispatchers.IO) {
            when {
                wallpaperPath != null -> loadHomeWallpaper(context, wallpaperPath!!)
                builtInWallpaperKey != "none" -> {
                    val resourceId = BuiltInWallpaperOptions.firstOrNull { it.key == builtInWallpaperKey }?.resourceId
                    resourceId?.let { loadBuiltInHomeWallpaper(context, it) }
                }
                else -> null
            }
        }
        if (loadGeneration == wallpaperLoadGeneration) {
            wallpaperBitmap = loadedWallpaper
        }
    }
    DisposableEffect(context, iconBitmapCache, iconCatalogRepository) {
        val memoryCallbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Keep launcher bitmaps through ordinary background transitions.
                // Release them only when Android reports critical memory pressure.
                if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
                    iconBitmapCache.trimMemory()
                    iconCatalogRepository.trimMemory()
                    wallpaperLoadGeneration++
                    wallpaperBitmap = null
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Suppress("DEPRECATION")
            override fun onLowMemory() {
                iconBitmapCache.trimMemory()
                iconCatalogRepository.trimMemory()
                wallpaperLoadGeneration++
                wallpaperBitmap = null
            }
        }
        context.registerComponentCallbacks(memoryCallbacks)
        onDispose { context.unregisterComponentCallbacks(memoryCallbacks) }
    }
    LaunchedEffect(resumeToken) {
        // Re-enable caches only when a critical memory trim actually disabled them.
        iconBitmapCache.resumeAfterBackground()
        iconCatalogRepository.resumeAfterBackground()
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            iconBitmapCache.configureForAppCount(apps.size)
            iconBitmapCache.preload(apps)
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
        val activeFavoriteIds = updated.filterNotNull().toSet()
        val retainedSnapshots = favoriteAppSnapshots.filterKeys { it in activeFavoriteIds }
        val refreshedSnapshots = apps
            .filter { it.id in activeFavoriteIds }
            .associate { app ->
                app.id to FavoriteAppSnapshot(
                    appId = app.id,
                    label = app.label,
                    iconCacheKey = app.iconCacheKey
                )
            }
        favoriteAppSnapshots = retainedSnapshots + refreshedSnapshots
        FavoriteAppSnapshotStore.save(context, favoriteAppSnapshots.values)
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
        val popupNavigationBarInsetPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
        val homeSwipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        val drawerCloseThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
        val drawerProgress = remember { mutableFloatStateOf(1f) }
        var drawerTargetProgress by remember { mutableFloatStateOf(1f) }
        var drawerVisible by remember { mutableStateOf(false) }
        var drawerFullyOpen by remember { mutableStateOf(false) }
        var isDrawerDragging by remember { mutableStateOf(false) }
        // Keep frame-by-frame progress reads inside graphicsLayer blocks below.
        // This lets Compose update layer properties without recomposing the launcher root.
        LaunchedEffect(drawerTargetProgress, isDrawerDragging) {
            if (!isDrawerDragging) {
                animate(
                    initialValue = drawerProgress.floatValue,
                    targetValue = drawerTargetProgress,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) { value, _ -> drawerProgress.floatValue = value }
                drawerFullyOpen = drawerTargetProgress <= 0f
                if (drawerTargetProgress >= 1f) drawerVisible = false
            }
        }
        fun closeDrawer() {
            focusManager.clearFocus()
            drawerFullyOpen = false
            isDrawerDragging = false
            drawerTargetProgress = 1f
        }

        // Reset transient interactions whenever NekOS is no longer visible.
        // Keep user-facing dialogs intact; only cancel gestures and temporary overlays.
        LaunchedEffect(backgroundSignal) {
            if (backgroundSignal > 0) {
                closeDrawer()
                drawerVisible = false
                drawerProgress.floatValue = 1f
                drawerFullyOpen = false

                interactionController.cancelDrag()
                favoriteDrag = null
                cancelDropBounds = null

                favoriteOrderSnapshot?.let { favoritePackages = it }
                favoriteOrderSnapshot = null
                tagOrderSnapshot?.let { tags = it }
                tagOrderSnapshot = null

                actionMenuApp = null
                clearActionMenuPreview()
            }
        }

        // Pressing Home while the tag editor is open dismisses it just like Back.
        // Otherwise, return NekOS to its clean Home screen.
        LaunchedEffect(homeSignal) {
            if (homeSignal > 0) {
                if (showTagEditor) {
                    showTagEditor = false
                    tagEditorTarget = null
                } else {
                    closeDrawer()
                    showSettings = false
                    actionMenuApp = null
                    actionMenuPreviewApp = null
                    viewingTag = null
                    actionMenuTag = null
                    tagPendingDelete = null
                }
            }
        }

        BackHandler(enabled = showSettings || actionMenuApp != null || drawerVisible) {
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
            launcherUiColorArgb = launcherUiColorArgb,
            onOpenSettings = { showSettings = true },
            favoritePackages = favoritePackages,
            favoriteAppSnapshots = favoriteAppSnapshots,
            tags = tags,
            activeTagPage = activeTagPage,
            onTagPageChange = { activeTagPage = it },
            iconCatalogRepository = iconCatalogRepository,
            iconBitmapCache = iconBitmapCache,
            homeSwipeThresholdPx = homeSwipeThresholdPx,
            backgroundSignal = backgroundSignal,
            drawerProgress = drawerProgress,
            onDrawerDrag = { totalDrag ->
                isDrawerDragging = true
                drawerVisible = true
                drawerFullyOpen = false
                drawerProgress.floatValue = (1f + totalDrag / drawerHeightPx).coerceIn(0f, 1f)
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
                favoritePackages.getOrNull(index)?.let { favoriteId ->
                    val app = apps.firstOrNull { it.id == favoriteId }
                        ?: apps.firstOrNull {
                            it.isInCurrentProfile && it.packageName == favoriteId
                        }
                    app?.let {
                        actionMenuPreviewApp = it
                        actionMenuPreviewPosition = position
                    }
                }
            },
            onFavoriteLongPressEnd = ::openActionMenuAfterRelease,
            onFavoriteLongPressCancel = ::clearActionMenuPreview,
            onFavoriteSlotBounds = { index, bounds -> favoriteSlotBounds[index] = bounds },
            favoriteDropTarget = favoriteDrag?.takeIf { it.hasMoved }?.targetIndex,
            onFavoriteReorder = { fromIndex, toIndex ->
                if (fromIndex != toIndex && fromIndex in 0 until 15 && toIndex in 0 until 15) {
                    val updated = MutableList(15) { favoritePackages.getOrNull(it) }
                    val moved = updated[fromIndex]
                    updated[fromIndex] = updated[toIndex]
                    updated[toIndex] = moved
                    favoritePackages = updated
                }
            },
            onFavoriteReorderStart = {
                favoriteOrderSnapshot = favoritePackages.toList()
            },
            onFavoriteReorderEnd = { cancelled ->
                val snapshot = favoriteOrderSnapshot
                if (cancelled) {
                    if (snapshot != null) favoritePackages = snapshot
                } else {
                    updateFavorites(favoritePackages)
                }
                favoriteOrderSnapshot = null
            },
            onTagClick = { tag -> viewingTag = tag },
            onTagOptionsClick = { tag -> actionMenuTag = tag },
            onTagReorder = { fromIndex, toIndex ->
                val sourceId = tags.getOrNull(fromIndex)?.id
                if (sourceId != null) {
                    tags = moveTagToIndex(tags, sourceId, toIndex)
                }
            },
            onTagReorderStart = {
                tagOrderSnapshot = tags.toList()
            },
            onTagReorderEnd = { cancelled ->
                val snapshot = tagOrderSnapshot
                if (cancelled) {
                    if (snapshot != null) tags = snapshot
                } else {
                    saveTags(tags)
                }
                tagOrderSnapshot = null
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

        if (drawerVisible) {
            // A lightweight dark scrim separates the drawer from Home without
            // rendering a second, blurred copy of the wallpaper.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.32f * (1f - drawerProgress.floatValue)
                    }
                    .background(Color.Black)
                    .clickable {
                        closeDrawer()
                    }
            )

            // The panel uses roughly 75% of the available screen height and
            // still adapts when the keyboard is visible.
            val fullScreenHeight = maxHeight
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().imePadding()
            ) {
            AppDrawer(
                apps = apps,
                launcherSurface = launcherSurface,
                iconBitmapCache = iconBitmapCache,
                closeThresholdPx = drawerCloseThresholdPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minOf(fullScreenHeight * 0.75f, maxHeight))
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Hide the entire drawer during a drag so no grid rows remain
                        // visible over the Home Favorite area.
                        val isDraggingApp = favoriteDrag?.hasMoved == true
                        translationY = if (isDraggingApp) size.height * 0.12f else drawerProgress.floatValue * size.height
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
            modifier = Modifier.zIndex(20f),
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.96f, animationSpec = tween(140)),
            exit = fadeOut(tween(100))
        ) {
            popupApp?.let { app ->
                val isInteractive = actionMenuApp != null
                val popupMaxWidth = maxWidth
                val popupMaxHeight = with(LocalDensity.current) {
                    (constraints.maxHeight - popupNavigationBarInsetPx).coerceAtLeast(0).toDp()
                }
                Box(
                    modifier = Modifier
                        .onSizeChanged { actionMenuPopupSize = it }
                        .offset {
                            calculateLauncherPopupOffset(
                                anchorPosition = actionMenuPreviewPosition,
                                popupSize = actionMenuPopupSize,
                                containerSize = IntSize(
                                    constraints.maxWidth,
                                    (constraints.maxHeight - popupNavigationBarInsetPx).coerceAtLeast(0)
                                )
                            )
                        }
                        .widthIn(max = minOf(210.dp, popupMaxWidth))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                                .padding(top = 32.dp)
                                .widthIn(max = minOf(210.dp, popupMaxWidth))
                            .heightIn(max = popupMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .border(
                                width = 1.dp,
                                color = lerp(launcherSurface, Ink, 0.18f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(launcherSurface)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        LauncherAppActionMenuContent(
                            app = app,
                            iconBitmapCache = iconBitmapCache,
                            isInteractive = isInteractive,
                            onAppInfo = {
                                actionMenuApp = null
                                openAppInfo(context, app)
                            },
                            onUninstall = {
                                actionMenuApp = null
                                requestAppUninstall(context, app)
                            },
                            onRemoveFavorite = if (favoritePackages.any { it == app.id }) {
                                {
                                    updateFavorites(favoritePackages.map { favoriteId ->
                                        if (favoriteId == app.id) null else favoriteId
                                    })
                                    actionMenuApp = null
                                }
                            } else null,
                            launcherSurface = launcherSurface,
                            uiColorArgb = launcherUiColorArgb
                        )
                    }
                    TopFrame(
                        colorArgb = launcherUiColorArgb,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-5).dp)
                    )
                    }
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
                val cacheGeneration = iconBitmapCache.generation
                val cachedBitmap = iconBitmapCache[draggedApp.iconCacheKey]
                var bitmap by remember(draggedApp.iconCacheKey, cacheGeneration) { mutableStateOf(cachedBitmap) }
                LaunchedEffect(draggedApp.iconCacheKey, cacheGeneration) {
                    bitmap = iconBitmapCache[draggedApp.iconCacheKey]
                    if (bitmap == null) {
                        bitmap = iconBitmapCache.getOrLoad(draggedApp)
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
                launcherThemeKey = launcherThemeKey,
                launcherUiColor = launcherUiColorArgb,
                onLauncherThemeChange = { key ->
                    launcherThemeKey = normalizeLauncherTheme(key)
                    preferences.edit().putString("launcher_theme", launcherThemeKey).apply()
                },
                onLauncherUiColorChange = { color ->
                    launcherUiColorArgb = normalizeLauncherUiColor(color)
                    preferences.edit().putLong("launcher_ui_color", launcherUiColorArgb).apply()
                },
                hasWallpaper = wallpaperPath != null,
                selectedBuiltInWallpaper = if (wallpaperPath == null && builtInWallpaperKey != "none") builtInWallpaperKey else null,
                appVersion = appVersion,
                isDefaultLauncher = isDefaultLauncher,
                onSetAsHome = {
                    val homeSettingsOpened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }.isSuccess

                    if (!homeSettingsOpened) {
                        val defaultAppsSettingsOpened = runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }.isSuccess

                        if (!defaultAppsSettingsOpened) {
                            Toast.makeText(
                                context,
                                "Unable to open launcher settings",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onBackgroundPresetChange = { preset ->
                    val normalizedPreset = normalizeNekOSBackgroundPreset(preset)
                    backgroundPreset = normalizedPreset
                    // A color choice becomes the active Home background instead
                    // of leaving a previously selected photo layered above it.
                    if (wallpaperPath != null) {
                        clearHomeWallpaper(context)
                        wallpaperPath = null
                        preferences.edit().remove("home_wallpaper_path").apply()
                    }
                    builtInWallpaperKey = "none"
                    preferences.edit()
                        .putString("built_in_wallpaper_key", "none")
                        .putString("background_preset", normalizedPreset)
                        .apply()
                },
                onBuiltInWallpaperChange = { key ->
                    val normalizedKey = normalizeBuiltInWallpaperKey(key)
                    builtInWallpaperKey = normalizedKey
                    if (wallpaperPath != null) {
                        clearHomeWallpaper(context)
                        wallpaperPath = null
                        preferences.edit().remove("home_wallpaper_path").apply()
                    }
                    preferences.edit().putString("built_in_wallpaper_key", normalizedKey).apply()
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
                iconBitmapCache = iconBitmapCache,
                launcherSurface = launcherSurface,
                launcherUiColorArgb = launcherUiColorArgb,
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
                launcherSurface = launcherSurface,
                launcherUiColorArgb = launcherUiColorArgb,
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
                launcherSurface = launcherSurface,
                apps = apps,
                iconBitmapCache = iconBitmapCache,
                iconCatalogRepository = iconCatalogRepository,
                launcherUiColorArgb = launcherUiColorArgb,
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
            NekOSAlertDialog(
                topFrameColorArgb = launcherUiColorArgb,
                onDismissRequest = { tagPendingDelete = null },
                shape = RoundedCornerShape(18.dp),
                containerColor = launcherSurface,
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
            NekOSAlertDialog(
                topFrameColorArgb = launcherUiColorArgb,
                onDismissRequest = {
                    preferences.edit().putBoolean("has_seen_drawer_tip", true).apply()
                    showFirstLaunchTip = false
                },
                containerColor = launcherSurface,
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
    launcherUiColorArgb: Long,
    onOpenSettings: () -> Unit,
    favoritePackages: List<String?>,
    favoriteAppSnapshots: Map<String, FavoriteAppSnapshot>,
    tags: List<NekTag>,
    activeTagPage: Int,
    onTagPageChange: (Int) -> Unit,
    iconCatalogRepository: IconCatalogRepository,
    iconBitmapCache: AppIconBitmapCache,
    homeSwipeThresholdPx: Float,
    backgroundSignal: Int,
    drawerProgress: State<Float>,
    onDrawerDrag: (Float) -> Unit,
    onDrawerDragEnd: (totalDrag: Float, velocityY: Float, cancelled: Boolean) -> Unit,
    onFavoriteClick: (InstalledApp, Rect) -> Unit,
    onFavoriteLongPress: (Int, Offset) -> Unit,
    onFavoriteLongPressEnd: () -> Unit,
    onFavoriteLongPressCancel: () -> Unit,
    onFavoriteSlotBounds: (Int, Rect) -> Unit,
    favoriteDropTarget: Int?,
    onFavoriteReorder: (Int, Int) -> Unit,
    onFavoriteReorderStart: () -> Unit,
    onFavoriteReorderEnd: (cancelled: Boolean) -> Unit,
    onTagClick: (NekTag) -> Unit,
    onTagOptionsClick: (NekTag) -> Unit,
    onTagReorder: (Int, Int) -> Unit,
    onTagReorderStart: () -> Unit,
    onTagReorderEnd: (cancelled: Boolean) -> Unit,
    onAddTag: () -> Unit
) {
    // Resolve current-profile legacy package-only favorites as well as profile-aware IDs.
    // Older NekOS versions stored package names, while current IDs include the profile serial.
    val appById = remember(apps) {
        buildMap {
            apps.forEach { app ->
                put(app.id, app)
                if (app.isInCurrentProfile) putIfAbsent(app.packageName, app)
            }
        }
    }
    val currentOnDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val currentOnDrawerDragEnd by rememberUpdatedState(onDrawerDragEnd)
    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        wallpaper?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
                // Keep launcher controls sharp above the static wallpaper.
                .pointerInput(homeSwipeThresholdPx, backgroundSignal) {
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
                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                    val tagPageCount = ((tags.size + 4) / 5).coerceAtLeast(1)
                    val safeTagPage = activeTagPage.coerceIn(0, tagPageCount - 1)
                    val visibleTags = tags.drop(safeTagPage * 5).take(5)
                    LaunchedEffect(tagPageCount, activeTagPage) {
                        if (activeTagPage != safeTagPage) onTagPageChange(safeTagPage)
                    }
                    Box(
                        modifier = Modifier.width(tagRailWidth).fillMaxHeight()
                    ) {
                        val tagFrameMiddleHeight by animateDpAsState(
                            targetValue = 20.dp + 58.dp * visibleTags.size,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = 380f
                            ),
                            label = "tag-frame-middle-height"
                        )
                        val tagFrameWidth = tagRailWidth * 1.1f
                        val tagFrameCapsHeight = tagFrameWidth * (224f / 512f)
                        TagFrame(
                            colorArgb = launcherUiColorArgb,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .offset(y = 8.dp)
                                .requiredWidth(tagFrameWidth)
                                .height(tagFrameMiddleHeight + tagFrameCapsHeight)
                        )
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
                                val tagListState = rememberLazyListState()
                                val reorderableTagState = rememberReorderableLazyListState(tagListState) { from, to ->
                                    onTagReorder(
                                        tagPageItemIndex(safeTagPage, from.index),
                                        tagPageItemIndex(safeTagPage, to.index)
                                    )
                                }
                                LazyColumn(
                                    state = tagListState,
                                    modifier = Modifier.fillMaxSize()
                                        .pointerInput(tagPageCount, safeTagPage) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onHorizontalDrag = { change, dragAmount ->
                                                    totalDrag += dragAmount
                                                    change.consume()
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
                                        ReorderableItem(
                                            state = reorderableTagState,
                                            key = tag.id,
                                            animateItemModifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = 420f
                                                ),
                                                fadeOutSpec = null
                                            )
                                        ) { isDragging ->
                                            TagPill(
                                                tag = tag,
                                                iconCatalogRepository = iconCatalogRepository,
                                                isDragging = isDragging,
                                                onClick = { onTagClick(tag) },
                                                onHandleClick = { onTagOptionsClick(tag) },
                                                onReorderStart = onTagReorderStart,
                                                onReorderEnd = onTagReorderEnd,
                                                reorderHandleScope = this
                                            )
                                        }
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
                                    ThemedUiArtworkImage(
                                        resourceId = R.drawable.nekos_ui_add_tag,
                                        colorArgb = launcherUiColorArgb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                         maxDimensionPx = 128
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(122.dp)
                ) {
                    ThemedUiArtworkImage(
                        resourceId = R.drawable.text_logo_sec,
                        colorArgb = launcherUiColorArgb,
                        contentDescription = "NekOS",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(x = (-138).dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = 1.02f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .height(122.dp)
                    )
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .offset(x = 20.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TagSurface.copy(alpha = 0.72f))
                            .clickable(onClick = onOpenSettings)
                            .semantics {
                                contentDescription = "Open launcher settings"
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ThemedUiArtworkImage(
                            resourceId = R.drawable.nekos_ui_launcher_settings,
                            colorArgb = launcherUiColorArgb,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                             maxDimensionPx = 128
                        )
                    }
                }
            }
            BoxWithConstraints {
                val favoriteSlots = List(15) { favoritePackages.getOrNull(it) }
                val favoriteCellSize = (maxWidth - 40.dp) / 5f
                val favoriteGridState = rememberLazyGridState()
                val reorderableFavoriteState = rememberReorderableLazyGridState(favoriteGridState) { from, to ->
                    onFavoriteReorder(from.index, to.index)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = favoriteGridState,
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                        .height(favoriteCellSize * 3 + 20.dp)
                ) {
                    itemsIndexed(
                        items = favoriteSlots,
                        key = { index, appId -> appId ?: "empty-$index" }
                    ) { index, appId ->
                        val app = appId?.let { appById[it] }
                        ReorderableItem(
                            state = reorderableFavoriteState,
                            key = appId ?: "empty-$index",
                            animateItemModifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = 380f
                                ),
                                fadeOutSpec = null
                            )
                        ) {
                            FavoriteSlot(
                                app = app,
                                snapshot = appId?.let { favoriteAppSnapshots[it] },
                                iconBitmapCache = iconBitmapCache,
                                onBoundsChanged = { bounds -> onFavoriteSlotBounds(index, bounds) },
                                isDropTarget = favoriteDropTarget == index,
                                onClick = { bounds -> app?.let { onFavoriteClick(it, bounds) } },
                                onLongPress = { position -> onFavoriteLongPress(index, position) },
                                onLongPressEnd = onFavoriteLongPressEnd,
                                onLongPressCancel = onFavoriteLongPressCancel,
                                onReorderStart = onFavoriteReorderStart,
                                onReorderEnd = onFavoriteReorderEnd,
                                reorderHandleScope = this,
                                modifier = Modifier.fillMaxWidth()
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
    snapshot: FavoriteAppSnapshot?,
    iconBitmapCache: AppIconBitmapCache,
    onBoundsChanged: (Rect) -> Unit,
    isDropTarget: Boolean,
    onClick: (Rect) -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderEnd: (cancelled: Boolean) -> Unit,
    reorderHandleScope: sh.calvin.reorderable.ReorderableCollectionItemScope,
    modifier: Modifier = Modifier
) {
    val cacheGeneration = iconBitmapCache.generation
    val iconCacheKey = app?.iconCacheKey ?: snapshot?.iconCacheKey
    val cachedBitmap = iconCacheKey?.let { iconBitmapCache[it] }
    var bitmap by remember(iconCacheKey, cacheGeneration) { mutableStateOf(cachedBitmap) }
    LaunchedEffect(iconCacheKey, app?.id, cacheGeneration) {
        bitmap = iconCacheKey?.let { iconBitmapCache[it] }
        if (bitmap == null && iconCacheKey != null) {
            bitmap = if (app != null) {
                iconBitmapCache.getOrLoad(app)
            } else {
                iconBitmapCache.getCached(iconCacheKey)
            }
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
    val currentOnReorderStart by rememberUpdatedState(onReorderStart)
    val currentOnReorderEnd by rememberUpdatedState(onReorderEnd)
    val reorderHandle = with(reorderHandleScope) {
        Modifier.draggableHandle(
            enabled = app != null,
            dragGestureDetector = LauncherReorderGestureDetector(
                toRootPosition = { local ->
                    currentSlotCoordinates?.localToRoot(local) ?: local
                },
                onTap = {
                    if (app != null) {
                        currentOnClick(currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero)
                    }
                },
                onLongPress = { position -> currentOnLongPress(position) },
                onLongPressEnd = { currentOnLongPressEnd() },
                onLongPressCancel = { currentOnLongPressCancel() },
                onReorderStart = { currentOnReorderStart() },
                onReorderEnd = { cancelled -> currentOnReorderEnd(cancelled) }
            )
        )
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(slotBackground)
            .onGloballyPositioned {
                slotCoordinates = it
                onBoundsChanged(it.boundsInRoot())
            }
            .then(reorderHandle)
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
            this@Column.AnimatedVisibility(
                visible = iconBitmap != null,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.90f,
                    animationSpec = tween(180)
                )
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app?.label ?: snapshot?.label,
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
    onClick: () -> Unit,
    onHandleClick: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderEnd: (cancelled: Boolean) -> Unit,
    reorderHandleScope: sh.calvin.reorderable.ReorderableCollectionItemScope,
    modifier: Modifier = Modifier
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
        targetValue = if (isDragging) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tag-drag-alpha"
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tag-drag-elevation"
    )
    val iconCacheGeneration = iconCatalogRepository.generation
    var tagIcon by remember(tag.iconId, tag.iconFrameColorArgb) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    val currentOnHandleClick by rememberUpdatedState(onHandleClick)
    val currentOnReorderStart by rememberUpdatedState(onReorderStart)
    val currentOnReorderEnd by rememberUpdatedState(onReorderEnd)
    val reorderHandle = with(reorderHandleScope) {
        Modifier.draggableHandle(
            dragGestureDetector = LauncherReorderGestureDetector(
                onTap = { currentOnHandleClick() },
                onReorderStart = { currentOnReorderStart() },
                onReorderEnd = { cancelled -> currentOnReorderEnd(cancelled) }
            )
        )
    }
    LaunchedEffect(tag.iconId, tag.iconFrameColorArgb, iconCacheGeneration) {
        val refreshedTagIcon = withContext(Dispatchers.IO) {
            runCatching {
                tag.iconId?.let { id ->
                    iconCatalogRepository.loadCatalog().firstOrNull { it.id == id }
                        ?.let { icon -> iconCatalogRepository.loadBitmap(icon, tag.iconFrameColorArgb) }
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (refreshedTagIcon != null) tagIcon = refreshedTagIcon
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                alpha = dragAlpha
                scaleX = dragScale
                scaleY = dragScale
            }
            .shadow(dragElevation, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color(tag.colorArgb.toInt()))
            .border(
                width = 1.dp,
                color = Ink.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            ),
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
                .then(reorderHandle)
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
    launcherSurface: Color,
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
    // Capture the grid position when each touch gesture begins. Reaching the top
    // during the same drag must not turn that drag into a drawer-close gesture.
    val drawerGestureStartedAtTop = remember { booleanArrayOf(false) }
    val pendingDrawerCloseDrag = remember { floatArrayOf(0f) }
    val drawerScrollConnection = remember(focusManager, gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) focusManager.clearFocus()
                if (available.y < 0f) pendingDrawerCloseDrag[0] = 0f
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (
                    drawerGestureStartedAtTop[0] &&
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    !gridState.canScrollBackward
                ) {
                    pendingDrawerCloseDrag[0] += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                val dragDistance = pendingDrawerCloseDrag[0]
                pendingDrawerCloseDrag[0] = 0f
                val canCloseDrawer = drawerGestureStartedAtTop[0]
                drawerGestureStartedAtTop[0] = false
                if (canCloseDrawer && (dragDistance > 0f || available.y > 0f)) {
                    currentOnCloseDrawer(dragDistance, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(searchQuery) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(launcherSurface)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(gridState) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            drawerGestureStartedAtTop[0] = !gridState.canScrollBackward
                        }
                    }
                    .nestedScroll(drawerScrollConnection)
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    val cacheGeneration = iconBitmapCache.generation
                    val cachedBitmap = iconBitmapCache[app.iconCacheKey]
                    var bitmap by remember(app.iconCacheKey, cacheGeneration) {
                        mutableStateOf(cachedBitmap)
                    }
                    LaunchedEffect(app.iconCacheKey, cacheGeneration) {
                        bitmap = iconBitmapCache[app.iconCacheKey]
                        if (bitmap == null) {
                            bitmap = iconBitmapCache.getOrLoad(app)
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
            bitmap?.let { loadedBitmap ->
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(52.dp)
                )
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


private fun isNekOSDefaultHome(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(homeIntent, 0)
    return resolved?.activityInfo?.packageName == context.packageName
}


@Composable
private fun TagFrame(
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    val frameBitmap = rememberThemedUiArtwork(
        R.drawable.tag_frame_spritesheet,
        colorArgb,
        maxDimensionPx = 512
    )
    if (frameBitmap != null) Canvas(modifier = modifier) {
        val widthPx = size.width
        val scale = widthPx / 512f
        val topHeight = 144f * scale
        val bottomHeight = 80f * scale
        val middleHeight = (size.height - topHeight - bottomHeight).coerceAtLeast(0f)
        val dstWidth = widthPx.toInt().coerceAtLeast(1)
        val topDstHeight = topHeight.toInt().coerceAtLeast(1)
        val middleDstHeight = middleHeight.toInt()
        val bottomDstHeight = bottomHeight.toInt().coerceAtLeast(1)

        drawImage(
            image = frameBitmap,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(512, 144),
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(dstWidth, topDstHeight)
        )
        if (middleDstHeight > 0) {
            drawImage(
                image = frameBitmap,
                srcOffset = IntOffset(0, 144),
                srcSize = IntSize(512, 16),
                dstOffset = IntOffset(0, topDstHeight),
                dstSize = IntSize(dstWidth, middleDstHeight)
            )
        }
        drawImage(
            image = frameBitmap,
            srcOffset = IntOffset(0, 160),
            srcSize = IntSize(512, 80),
            dstOffset = IntOffset(0, topDstHeight + middleDstHeight),
            dstSize = IntSize(dstWidth, bottomDstHeight)
        )
    }
}