package com.nekos.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
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
    val gestureDiagnosticContext = LocalContext.current
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
    var tagsVisible by remember { mutableStateOf(true) }
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
                            FavoriteGestureDiagnostics.log(
                                gestureDiagnosticContext,
                                "home-pointer-start position=$position"
                            )
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
                                    if (!drawerDragStarted) {
                                        FavoriteGestureDiagnostics.log(
                                            gestureDiagnosticContext,
                                            "home-swipe-captured totalDrag=$totalDrag startEligible=$gestureEligible"
                                        )
                                    }
                                    drawerDragStarted = true
                                    currentOnDrawerDrag(totalDrag)
                                    change.consume()
                                }
                            }
                        },
                        onDragEnd = {
                            if (drawerDragStarted) {
                                FavoriteGestureDiagnostics.log(
                                    gestureDiagnosticContext,
                                    "home-swipe-end totalDrag=$totalDrag"
                                )
                                currentOnDrawerDragEnd(totalDrag, velocityTracker.calculateVelocity().y, false)
                            }
                            totalDrag = 0f
                            drawerDragStarted = false
                            gestureEligible = false
                        },
                        onDragCancel = {
                            if (drawerDragStarted) {
                                FavoriteGestureDiagnostics.log(
                                    gestureDiagnosticContext,
                                    "home-swipe-cancel totalDrag=$totalDrag"
                                )
                                currentOnDrawerDragEnd(totalDrag, 0f, true)
                            }
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
                    Spacer(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .pointerInput(tagsVisible) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        totalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val threshold = 56.dp.toPx()
                                        if (totalDrag >= threshold && tagsVisible) {
                                            tagsVisible = false
                                        } else if (totalDrag <= -threshold && !tagsVisible) {
                                            tagsVisible = true
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            }
                    )
                    val tagPageCount = ((tags.size + 4) / 5).coerceAtLeast(1)
                    val safeTagPage = activeTagPage.coerceIn(0, tagPageCount - 1)
                    val visibleTags = tags.drop(safeTagPage * 5).take(5)
                    LaunchedEffect(tagPageCount, activeTagPage) {
                        if (activeTagPage != safeTagPage) onTagPageChange(safeTagPage)
                    }
                    AnimatedVisibility(
                        visible = tagsVisible,
                        enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f),
                        exit = androidx.compose.animation.fadeOut(animationSpec = tween(180)) +
                            androidx.compose.animation.scaleOut(targetScale = 0.96f)
                    ) {
                    Box(
                        modifier = Modifier.width(tagRailWidth).fillMaxHeight()
                            .offset(y = (-100).dp)
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
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(122.dp)
                ) {
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
                        .offset(y = (-50).dp)
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
                                slotIndex = index,
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
