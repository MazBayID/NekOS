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
import androidx.compose.foundation.shape.GenericShape
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
internal fun FavoriteSlot(
    slotIndex: Int,
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
    val diagnosticContext = LocalContext.current
    val cacheGeneration = iconBitmapCache.generation
    val iconCacheKey = app?.iconCacheKey ?: snapshot?.iconCacheKey

    LaunchedEffect(slotIndex, app?.id, snapshot?.appId) {
        if (app == null && snapshot != null) {
            FavoriteGestureDiagnostics.log(
                diagnosticContext,
                "slot-state slot=$slotIndex app=missing snapshotAppId=${snapshot.appId}; input disabled"
            )
        } else {
            FavoriteGestureDiagnostics.log(
                diagnosticContext,
                "slot-state slot=$slotIndex appId=${app?.id} snapshotAppId=${snapshot?.appId} inputEnabled=${app != null}"
            )
        }
    }
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
                    FavoriteGestureDiagnostics.log(
                        diagnosticContext,
                        "slot-tap slot=$slotIndex appId=${app?.id} enabled=${app != null}"
                    )
                    if (app != null) {
                        currentOnClick(currentSlotCoordinates?.boundsInRoot() ?: Rect.Zero)
                    }
                },
                onLongPress = { position ->
                    FavoriteGestureDiagnostics.log(
                        diagnosticContext,
                        "slot-long-press slot=$slotIndex appId=${app?.id}"
                    )
                    currentOnLongPress(position)
                },
                onLongPressEnd = {
                    FavoriteGestureDiagnostics.log(diagnosticContext, "slot-long-press-end slot=$slotIndex")
                    currentOnLongPressEnd()
                },
                onLongPressCancel = {
                    FavoriteGestureDiagnostics.log(diagnosticContext, "slot-long-press-cancel slot=$slotIndex")
                    currentOnLongPressCancel()
                },
                onReorderStart = {
                    FavoriteGestureDiagnostics.log(diagnosticContext, "slot-reorder-start slot=$slotIndex appId=${app?.id}")
                    currentOnReorderStart()
                },
                onReorderEnd = { cancelled ->
                    FavoriteGestureDiagnostics.log(diagnosticContext, "slot-reorder-end slot=$slotIndex cancelled=$cancelled")
                    currentOnReorderEnd(cancelled)
                },
                onTrace = { event ->
                    FavoriteGestureDiagnostics.log(
                        diagnosticContext,
                        "slot-gesture slot=$slotIndex appId=${app?.id} $event"
                    )
                }
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

private val HorizontalTagPillShape = GenericShape { size, _ ->
    val quarterHeight = size.height * 0.25f
    moveTo(0f, quarterHeight)
    cubicTo(
        size.width * 0.25f, 0f,
        size.width * 0.75f, 0f,
        size.width, quarterHeight
    )
    lineTo(size.width, size.height - quarterHeight)
    cubicTo(
        size.width * 0.75f, size.height,
        size.width * 0.25f, size.height,
        0f, size.height - quarterHeight
    )
    close()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TagPill(
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
    val activeSkinGeneration = SkinRuntimeState.generation
    var tagIcon by remember(tag.iconId, tag.iconFrameColorArgb, iconCacheGeneration, activeSkinGeneration) {
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
    LaunchedEffect(tag.iconId, tag.iconFrameColorArgb, iconCacheGeneration, activeSkinGeneration) {
        val refreshedTagIcon = withContext(Dispatchers.IO) {
            runCatching {
                tag.iconId?.let { id ->
                    val catalog = iconCatalogRepository.loadCatalog()
                    val icon = catalog.firstOrNull { it.id == id }
                        ?: catalog.firstOrNull { it.id == 90 } // canonical star fallback
                    icon?.let { iconCatalogRepository.loadBitmap(it, tag.iconFrameColorArgb) }
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (activeSkinGeneration == SkinRuntimeState.generation) tagIcon = refreshedTagIcon
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
            .shadow(dragElevation, HorizontalTagPillShape)
            .clip(HorizontalTagPillShape)
            .background(Color(tag.colorArgb.toInt()))
            .border(
                width = 1.dp,
                color = Ink.copy(alpha = 0.18f),
                shape = HorizontalTagPillShape
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
            if (tagIcon != null) Image(tagIcon!!, contentDescription = null, modifier = Modifier.size(28.dp))
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

@Composable
internal fun TagFrame(
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = remember(context) { SkinAssetResolver(context) }
    val skinGeneration = SkinRuntimeState.generation
    var frameBitmap by remember(resolver, skinGeneration) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(resolver, skinGeneration) {
        frameBitmap = withContext(Dispatchers.IO) {
            runCatching {
                resolver.loadBitmap(
                    "components/tag_frame/frame_spritesheet.webp",
                    expectedWidth = 512,
                    expectedHeight = 240
                )?.asImageBitmap()
            }.getOrNull()
        }
    }

    val bitmap = frameBitmap
    if (bitmap != null) Canvas(modifier = modifier) {
        val widthPx = size.width
        val scale = widthPx / 512f
        val topHeight = 144f * scale
        val bottomHeight = 80f * scale
        val middleHeight = (size.height - topHeight - bottomHeight).coerceAtLeast(0f)
        val dstWidth = widthPx.toInt().coerceAtLeast(1)
        val topDstHeight = topHeight.toInt().coerceAtLeast(1)
        val middleDstHeight = middleHeight.toInt()
        val bottomDstHeight = bottomHeight.toInt().coerceAtLeast(1)
        val tint = androidx.compose.ui.graphics.ColorFilter.tint(
            Color(colorArgb.toInt()),
            androidx.compose.ui.graphics.BlendMode.SrcIn
        )

        drawImage(bitmap, IntOffset(0, 0), IntSize(512, 144), IntOffset(0, 0),
            IntSize(dstWidth, topDstHeight), colorFilter = tint)
        if (middleDstHeight > 0) {
            drawImage(bitmap, IntOffset(0, 144), IntSize(512, 16),
                IntOffset(0, topDstHeight), IntSize(dstWidth, middleDstHeight),
                colorFilter = tint)
        }
        drawImage(bitmap, IntOffset(0, 160), IntSize(512, 80),
            IntOffset(0, topDstHeight + middleDstHeight),
            IntSize(dstWidth, bottomDstHeight), colorFilter = tint)
    }
}
