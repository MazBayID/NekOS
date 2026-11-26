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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
private val WarmBackground = Color(0xFFF4F1EA)
internal val Ink = Color(0xFF34332F)
internal val Muted = Color(0xFF8B8982)
internal val TagSurface = Color(0xFFE7E1D6)

@Composable
internal fun NekOSRoot(
    refreshToken: Int,
    resumeToken: Int = 0,
    homeSignal: Int = 0,
    backgroundSignal: Int = 0,
    temporarilyUnavailableAppIds: Set<String> = emptySet(),
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
    val skinRepository = remember(context) { SkinRepository(context) }
    var installedSkins by remember(skinRepository) {
        mutableStateOf(skinRepository.listInstalledSkins())
    }
    var activeSkinId by remember(skinRepository) {
        mutableStateOf(skinRepository.activeSkinId())
    }
    var officialStoreSkins by remember { mutableStateOf(emptyList<OfficialStoreSkin>()) }
    var isOfficialStoreLoading by remember { mutableStateOf(false) }
    var storeBusySkinId by remember { mutableStateOf<String?>(null) }
    var pendingStoreSkinIds by remember { mutableStateOf(emptySet<String>()) }
    var storePaymentFailure by remember { mutableStateOf<String?>(null) }

    // Reconcile unfinished Store payments whenever NekOS returns to foreground.
    // The checkout browser may be dismissed without a Midtrans webhook (e.g. the
    // buyer never selects a payment method), so the client must query pending
    // orders on resume rather than rely on a manual button.
    LaunchedEffect(resumeToken) {
        val pendingIds = OfficialSkinStoreClient.pendingSkinIds(context)
        if (pendingIds.isEmpty() || storeBusySkinId != null) return@LaunchedEffect

        val catalog = officialStoreSkins.ifEmpty {
            runCatching { withContext(Dispatchers.IO) { OfficialSkinStoreClient.loadCatalog() } }
                .getOrDefault(emptyList())
        }
        if (officialStoreSkins.isEmpty() && catalog.isNotEmpty()) {
            officialStoreSkins = catalog
        }

        val skinsToCheck = pendingIds.map { id ->
            catalog.firstOrNull { it.id == id }
                ?: officialStoreSkins.firstOrNull { it.id == id }
                ?: OfficialStoreSkin(
                    id = id, skinKey = id, name = id, author = "", description = "",
                    price = 0L, currency = "IDR", previewUrl = ""
                )
        }
        val failures = mutableListOf<String>()
        for (skin in skinsToCheck) {
            if (storeBusySkinId != null) break
            storeBusySkinId = skin.id
            val result = withContext(Dispatchers.IO) {
                runCatching { OfficialSkinStoreClient.checkPaymentAndInstall(context, skin) }
                    .getOrElse { StoreActionResult(false, it.message ?: "Unable to check payment status.") }
            }
            storeBusySkinId = null
            if (result.paymentFailed) {
                // Ensure the Store UI immediately reflects that this order is terminal.
                pendingStoreSkinIds = pendingStoreSkinIds - skin.id
                failures += result.message
            } else if (result.success && result.installedSkinId != null) {
                installedSkins = skinRepository.listInstalledSkins()
                iconCatalogRepository.invalidateSkinCache()
            } else if (!result.success && !result.message.contains("belum dikonfirmasi", ignoreCase = true)) {
                // Keep pending orders on transient errors and inform the user.
                failures += result.message
            }
        }
        pendingStoreSkinIds = OfficialSkinStoreClient.pendingSkinIds(context)
        if (failures.isNotEmpty()) {
            storePaymentFailure = failures.distinct().joinToString("\n\n")
        }
    }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    val initialFavoriteLoad = remember(context) { loadFavoritePackagesResult(context) }
    var favoritePackages by remember { mutableStateOf(initialFavoriteLoad.packages) }
    var favoriteStorageWritable by remember { mutableStateOf(initialFavoriteLoad.isValid) }
    var favoriteAppSnapshots by remember {
        mutableStateOf(FavoriteAppSnapshotStore.load(context))
    }
    val initialTagLoad = remember(context) { TagStore.loadResult(context) }
    var tags by remember { mutableStateOf(initialTagLoad.tags) }
    var tagStorageWritable by remember { mutableStateOf(initialTagLoad.isValid) }
    var tagEditorTarget by remember { mutableStateOf<NekTag?>(null) }
    var viewingTag by remember { mutableStateOf<NekTag?>(null) }
    var actionMenuTag by remember { mutableStateOf<NekTag?>(null) }
    var tagPendingDelete by remember { mutableStateOf<NekTag?>(null) }
    var favoriteOrderSnapshot by remember { mutableStateOf<List<String?>?>(null) }
    var tagOrderSnapshot by remember { mutableStateOf<List<NekTag>?>(null) }
    var activeTagPage by remember { mutableIntStateOf(0) }
    // Restored launcher tasks render Home immediately while the app catalog refreshes.
    var isInitialLoadComplete by remember { mutableStateOf(skipStartupSplash) }
    var lastAppliedRefreshToken by remember { mutableIntStateOf(-1) }
    var catalogLoadFailed by remember { mutableStateOf(false) }

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
        // Do not mutate Favorite or Tag collections while a reorder gesture is
        // still using index-based positions and a pre-refresh rollback snapshot.
        // The gesture completion commits or restores its state before cleanup runs.
        snapshotFlow {
            favoriteOrderSnapshot == null && tagOrderSnapshot == null
        }.first { reorderFinished -> reorderFinished }

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
            favorites = favoritePackages,
            tags = tags,
            currentProfileAppIds = currentProfileAppIds
        )
        val installedAppIds = refreshedApps.mapTo(mutableSetOf<String>()) { it.id }
        // Read the latest user-edited collections after the background query so
        // an in-flight refresh cannot overwrite a Favorite or Tag edit.
        val cleanedReferences = cleanupLauncherReferences(
            favorites = migratedReferences.favorites,
            tags = migratedReferences.tags,
            installedAppIds = installedAppIds,
            unavailableProfileSerialNumbers = refreshedCatalog.unavailableProfileSerialNumbers,
            temporarilyUnavailableAppIds = temporarilyUnavailableAppIds
        )
        val favoritesChanged = cleanedReferences.favorites != favoritePackages
        val favoriteSaveSucceeded = when {
            !favoritesChanged -> true
            !favoriteStorageWritable -> false
            saveFavoritePackages(context, cleanedReferences.favorites) -> {
                favoritePackages = cleanedReferences.favorites
                true
            }
            else -> {
                favoriteStorageWritable = false
                Toast.makeText(
                    context,
                    "Favorites could not be saved. Existing data was preserved.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
        if (shouldPersistFavoriteSnapshots(favoritesChanged, favoriteSaveSucceeded)) {
            // Snapshot metadata must describe the persisted Favorite list, never
            // a cleanup result whose Favorite write failed.
            val activeFavoriteIds = favoritePackages.filterNotNull().toSet()
            val retainedSnapshots = favoriteAppSnapshots.filterKeys { it in activeFavoriteIds }
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
        }
        if (tagStorageWritable && cleanedReferences.tags != tags) {
            if (TagStore.save(context, cleanedReferences.tags)) {
                tags = cleanedReferences.tags
                viewingTag = refreshTagReference(viewingTag, tags)
                actionMenuTag = refreshTagReference(actionMenuTag, tags)
                tagEditorTarget = refreshTagReference(tagEditorTarget, tags)
                tagPendingDelete = refreshTagReference(tagPendingDelete, tags)
            } else {
                tagStorageWritable = false
                Toast.makeText(
                    context,
                    "Tags could not be saved. Existing data was preserved.",
                    Toast.LENGTH_LONG
                ).show()
            }
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
    var isWallpaperTrimmed by remember { mutableStateOf(false) }
    // The wallpaper is always saved to the same file, so the path alone cannot signal a new
    // image. The revision makes choosing a second wallpaper reload it immediately.
    var wallpaperRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(wallpaperPath, builtInWallpaperKey, wallpaperRevision, wallpaperLoadGeneration, isWallpaperTrimmed) {
        if (isWallpaperTrimmed) {
            wallpaperBitmap = null
            return@LaunchedEffect
        }
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
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                    iconBitmapCache.trimMemory()
                    iconCatalogRepository.trimMemory()
                    SkinAssetResolver.trimAllCaches()
                    UiArtworkColorizer.trimMemory()
                    isWallpaperTrimmed = true
                    wallpaperLoadGeneration++
                    wallpaperBitmap = null
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Suppress("DEPRECATION")
            override fun onLowMemory() {
                iconBitmapCache.trimMemory()
                iconCatalogRepository.trimMemory()
                SkinAssetResolver.trimAllCaches()
                UiArtworkColorizer.trimMemory()
                isWallpaperTrimmed = true
                wallpaperLoadGeneration++
                wallpaperBitmap = null
            }
        }
        context.registerComponentCallbacks(memoryCallbacks)
        onDispose { context.unregisterComponentCallbacks(memoryCallbacks) }
    }
    LaunchedEffect(resumeToken) {
        isWallpaperTrimmed = false
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

    val skinPickerScope = rememberCoroutineScope()
    val skinPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            skinPickerScope.launch {
                val result = withContext(Dispatchers.IO) {
                    SkinInstaller(context).install(uri)
                }
                if (result.success) {
                    installedSkins = skinRepository.listInstalledSkins()
                    iconCatalogRepository.invalidateSkinCache()
                    Toast.makeText(context, "Skin imported: ${result.skinId}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        result.error ?: "Unable to import skin",
                        Toast.LENGTH_LONG
                    ).show()
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

    fun saveTags(updated: List<NekTag>): Boolean {
        if (!tagStorageWritable) {
            Toast.makeText(
                context,
                "Tags could not be loaded. Existing data was preserved.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        val normalized = updated.distinctBy { it.id }
        if (TagStore.save(context, normalized)) {
            tags = normalized
            return true
        } else {
            tagStorageWritable = false
            Toast.makeText(
                context,
                "Tags could not be saved. Existing data was preserved.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }

    fun updateFavorites(updated: List<String?>): Boolean {
        if (!favoriteStorageWritable) {
            Toast.makeText(
                context,
                "Favorites could not be loaded. Existing data was preserved.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (!saveFavoritePackages(context, updated)) {
            favoriteStorageWritable = false
            Toast.makeText(
                context,
                "Favorites could not be saved. Existing data was preserved.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        favoritePackages = updated
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
        return true
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
            clearActionMenuPreview()
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
                    tagPendingDelete = null
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
                FavoriteGestureDiagnostics.log(context, "click-dispatch appId=${app.id} label=${app.label}")
                launchApp(context, app, launchSourceView, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
            },
            onFavoriteLongPress = { index, position ->
                val favoriteId = favoritePackages.getOrNull(index)
                val app = favoriteId?.let { id ->
                    apps.firstOrNull { it.id == id }
                        ?: apps.firstOrNull {
                            it.isInCurrentProfile && it.packageName == id
                        }
                }
                FavoriteGestureDiagnostics.log(
                    context,
                    "long-press-dispatch slot=$index favoriteId=$favoriteId resolvedAppId=${app?.id} resolved=${app != null}"
                )
                app?.let {
                    actionMenuPreviewApp = it
                    actionMenuPreviewPosition = position
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
                } else if (!updateFavorites(favoritePackages)) {
                    if (snapshot != null) favoritePackages = snapshot
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
                } else if (!saveTags(tags)) {
                    if (snapshot != null) tags = snapshot
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
                motifTintArgb = launcherUiColorArgb,
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
                val appActionMotifBrush = rememberDialogMotifBrush()
                val appActionMotifTint = androidx.compose.ui.graphics.ColorFilter.tint(
                    Color(launcherUiColorArgb.toInt()),
                    androidx.compose.ui.graphics.BlendMode.SrcIn
                )
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
                            .drawBehind {
                                appActionMotifBrush?.let { brush ->
                                    drawRect(brush = brush, alpha = 0.18f, colorFilter = appActionMotifTint)
                                }
                            }
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
                                    if (updateFavorites(favoritePackages.map { favoriteId ->
                                        if (favoriteId == app.id) null else favoriteId
                                    })) {
                                        actionMenuApp = null
                                    }
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
                installedSkins = installedSkins,
                activeSkinId = activeSkinId,
                officialStoreSkins = officialStoreSkins,
                isOfficialStoreLoading = isOfficialStoreLoading,
                storeBusySkinId = storeBusySkinId,
                pendingStoreSkinIds = pendingStoreSkinIds,
                onRefreshOfficialStore = {
                    if (!isOfficialStoreLoading) skinPickerScope.launch {
                        isOfficialStoreLoading = true
                        val result = withContext(Dispatchers.IO) { runCatching { OfficialSkinStoreClient.loadCatalog() } }
                        isOfficialStoreLoading = false
                        result.onSuccess { officialStoreSkins = it }
                            .onFailure { Toast.makeText(context, it.message ?: "Unable to load skin store", Toast.LENGTH_LONG).show() }
                        pendingStoreSkinIds = OfficialSkinStoreClient.pendingSkinIds(context)
                    }
                },
                onStoreAction = { skin, checkPayment ->
                    if (storeBusySkinId == null) skinPickerScope.launch {
                        storeBusySkinId = skin.id
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                if (checkPayment) OfficialSkinStoreClient.checkPaymentAndInstall(context, skin)
                                else OfficialSkinStoreClient.buy(context, skin)
                            }.getOrElse { StoreActionResult(false, it.message ?: "Store action failed.") }
                        }
                        storeBusySkinId = null
                        if (!result.checkoutUrl.isNullOrBlank()) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.checkoutUrl))) }
                                .onFailure { Toast.makeText(context, "Unable to open payment page", Toast.LENGTH_LONG).show() }
                        }
                        if (result.success && result.installedSkinId != null) {
                            installedSkins = skinRepository.listInstalledSkins()
                            iconCatalogRepository.invalidateSkinCache()
                        }
                        if (result.paymentFailed) {
                            // Terminal payment status: remove the pending badge/action immediately.
                            pendingStoreSkinIds = pendingStoreSkinIds - skin.id
                            storePaymentFailure = result.message
                        } else {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                        pendingStoreSkinIds = OfficialSkinStoreClient.pendingSkinIds(context)
                    }
                },
                onImportSkin = {
                    skinPicker.launch(arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    ))
                },
                onSelectSkin = { id ->
                    if (skinRepository.setActiveSkin(id)) {
                        activeSkinId = id
                        iconCatalogRepository.invalidateSkinCache()
                        installedSkins = skinRepository.listInstalledSkins()
                        Toast.makeText(context, "Skin aktif: ${installedSkins.firstOrNull { it.id == id }?.name ?: id}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unable to activate this skin", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteSkin = { id ->
                    if (skinRepository.deleteSkin(id)) {
                        activeSkinId = skinRepository.activeSkinId()
                        installedSkins = skinRepository.listInstalledSkins()
                        iconCatalogRepository.invalidateSkinCache()
                        Toast.makeText(context, "Skin removed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unable to remove this skin", Toast.LENGTH_SHORT).show()
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
                        if (saveTags(moveTagToPage(tags, tag.id, page))) {
                            activeTagPage = page
                            actionMenuTag = null
                        }
                    }
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
                            val updatedTags = if (tagEditorTarget == null) {
                                tags + saved
                            } else {
                                tags.map { if (it.id == saved.id) saved else it }
                            }
                            if (saveTags(updatedTags)) {
                                if (tagEditorTarget == null) activeTagPage = tags.size / 5
                                showTagEditor = false
                                tagEditorTarget = null
                            }
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
                        if (saveTags(tags.filterNot { it.id == tag.id })) {
                            tagPendingDelete = null
                            showTagEditor = false
                            tagEditorTarget = null
                        }
                    }) { Text("Delete", color = Color(0xFFB3261E)) }
                },
                dismissButton = {
                    TextButton(onClick = { tagPendingDelete = null }) { Text("Cancel") }
                }
            )
        }

        storePaymentFailure?.let { message ->
            NekOSAlertDialog(
                topFrameColorArgb = launcherUiColorArgb,
                onDismissRequest = { storePaymentFailure = null },
                containerColor = launcherSurface,
                title = { Text("Pembayaran gagal") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { storePaymentFailure = null }) {
                        Text("Mengerti")
                    }
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


