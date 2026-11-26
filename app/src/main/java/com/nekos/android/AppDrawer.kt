package com.nekos.android

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawer(
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
                if (available.y < 0f) {
                    pendingDrawerCloseDrag[0] = 0f
                    if (shouldInvalidateDrawerCloseGesture(
                            availableY = available.y,
                            isUserInput = source == NestedScrollSource.UserInput
                        )
                    ) {
                        drawerGestureStartedAtTop[0] = false
                    }
                }
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

    val drawerMotifBrush = rememberDialogMotifBrush()
    val drawerMotifTint = remember(launcherSurface) {
        ColorFilter.tint(darkenMotifColor(launcherSurface), BlendMode.SrcIn)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(launcherSurface)
            .drawBehind {
                drawerMotifBrush?.let { brush ->
                    drawRect(
                        brush = brush,
                        alpha = 0.18f,
                        colorFilter = drawerMotifTint
                    )
                }
            }
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
