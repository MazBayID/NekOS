package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun OfficialStoreActionButton(
    label: String,
    enabled: Boolean,
    isInstall: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "storeButtonPress"
    )
    val shape = RoundedCornerShape(50)
    val colors = when {
        !enabled -> listOf(Color(0xFFE1DED7), Color(0xFFD5D0C7))
        isInstall -> listOf(Color(0xFFB8F36B), Color(0xFF79D95B))
        else -> listOf(Color(0xFFFFA36F), Color(0xFFFF765F))
    }
    val contentColor = if (enabled && isInstall) Color(0xFF172314) else Color(0xFF2A211D)

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(if (enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) contentColor else SettingsMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.25.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun OfficialStorePreview(url: String) {
    val ocean = Color(0xFF114B56)
    val imageState = remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        imageState.value = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val image = imageState.value
    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = "Skin preview",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(width = 68.dp, height = 90.dp).clip(RoundedCornerShape(9.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(width = 68.dp, height = 90.dp)
                .clip(RoundedCornerShape(9.dp)).background(ocean),
            contentAlignment = Alignment.Center
        ) {
            Text("NEKOS", color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

internal val SettingsInk = Color(0xFF34332F)
internal val SettingsMuted = Color(0xFF5E574E)
internal val SettingsAccent = Color(0xFF746657)
internal val SettingsBorder = SettingsInk.copy(alpha = 0.20f)
internal val SettingsCardShape = RoundedCornerShape(16.dp)

internal fun normalizeNekOSBackgroundPreset(preset: String?): String = when (preset) {
    "warm", "sage", "mist" -> preset
    else -> "warm"
}

internal fun nekosBackgroundColor(preset: String): Color = when (normalizeNekOSBackgroundPreset(preset)) {
    "sage" -> Color(0xFFE8EEE5)
    "mist" -> Color(0xFFE8EDF2)
    else -> Color(0xFFF4F1EA)
}

internal data class LauncherThemeOption(val key: String, val label: String, val color: Color)

internal val LauncherThemeOptions = listOf(
    LauncherThemeOption("porcelain", "Porcelain", Color(0xFFFEFCF8)),
    LauncherThemeOption("sand", "Sand", Color(0xFFF5EBDD)),
    LauncherThemeOption("sage", "Sage", Color(0xFFE8EEE5)),
    LauncherThemeOption("mist", "Mist", Color(0xFFE8EDF2)),
    LauncherThemeOption("lavender", "Lavender", Color(0xFFEFEAF5)),
    LauncherThemeOption("blush", "Blush", Color(0xFFC69D98)),
    LauncherThemeOption("mint", "Mint", Color(0xFFA2C1B1)),
    LauncherThemeOption("sky", "Sky", Color(0xFF9FBBD0))
)

internal fun normalizeLauncherTheme(key: String?): String =
    LauncherThemeOptions.firstOrNull { it.key == key }?.key ?: "porcelain"

internal fun launcherThemeColor(key: String): Color =
    LauncherThemeOptions.firstOrNull { it.key == normalizeLauncherTheme(key) }?.color
        ?: Color(0xFFFEFCF8)

private enum class SettingsPage { MAIN, WALLPAPER, LAUNCHER_THEME, SKINS, OFFICIAL_SKIN_STORE }

@Composable
internal fun NekOSSettingsDialog(
    backgroundPreset: String,
    launcherThemeKey: String,
    launcherUiColor: Long,
    onLauncherThemeChange: (String) -> Unit,
    onLauncherUiColorChange: (Long) -> Unit,
    hasWallpaper: Boolean,
    selectedBuiltInWallpaper: String?,
    appVersion: String,
    isDefaultLauncher: Boolean,
    onSetAsHome: () -> Unit,
    onBackgroundPresetChange: (String) -> Unit,
    onBuiltInWallpaperChange: (String) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onShowDrawerHint: () -> Unit,
    onOpenRepository: () -> Unit,
    installedSkins: List<InstalledSkin>,
    activeSkinId: String,
    officialStoreSkins: List<OfficialStoreSkin>,
    isOfficialStoreLoading: Boolean,
    storeBusySkinId: String?,
    pendingStoreSkinIds: Set<String>,
    onRefreshOfficialStore: () -> Unit,
    onStoreAction: (OfficialStoreSkin, Boolean) -> Unit,
    onImportSkin: () -> Unit,
    onSelectSkin: (String) -> Unit,
    onDeleteSkin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    LaunchedEffect(page) { if (page == SettingsPage.OFFICIAL_SKIN_STORE) onRefreshOfficialStore() }
    val surfaceColor = launcherThemeColor(launcherThemeKey)
    val optionSurface = lerp(surfaceColor, Color.White, 0.48f)
    val motifBrush = rememberDialogMotifBrush()
    val motifTint = ColorFilter.tint(darkenMotifColor(surfaceColor), BlendMode.SrcIn)

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = if (page == SettingsPage.OFFICIAL_SKIN_STORE) {
            SettingsPage.SKINS
        } else {
            SettingsPage.MAIN
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            },
            contentAlignment = Alignment.Center
        ) {
            val availableDialogHeight = maxHeight
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .heightIn(max = availableDialogHeight * 0.78f - 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (page != SettingsPage.OFFICIAL_SKIN_STORE) {
                                    motifBrush?.let {
                                        drawRect(brush = it, alpha = 0.18f, colorFilter = motifTint)
                                    }
                                }
                            }
                            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (page != SettingsPage.MAIN) {
                                Text(
                                    "‹",
                                    color = SettingsAccent,
                                    fontSize = 30.sp,
                                    modifier = Modifier.clickable {
                                        page = if (page == SettingsPage.OFFICIAL_SKIN_STORE) {
                                            SettingsPage.SKINS
                                        } else {
                                            SettingsPage.MAIN
                                        }
                                    }
                                )
                            }
                            Text(
                                when (page) {
                                    SettingsPage.MAIN -> "Settings"
                                    SettingsPage.WALLPAPER -> "Wallpaper"
                                    SettingsPage.LAUNCHER_THEME -> "Launcher theme"
                                    SettingsPage.SKINS -> "Skins"
                                    SettingsPage.OFFICIAL_SKIN_STORE -> "Official Skin Store"
                                },
                                color = SettingsInk,
                                fontSize = if (page == SettingsPage.OFFICIAL_SKIN_STORE) 17.sp else 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (page != SettingsPage.OFFICIAL_SKIN_STORE) {
                            SettingsBrushSeparator()
                        }
                        Column(
                modifier = Modifier
                    .heightIn(max = availableDialogHeight * 0.58f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (page) {
                    SettingsPage.MAIN -> {
                        SettingsSectionTitle("APPEARANCE")
                        SettingsListRow(
                            title = "Wallpaper",
                            onClick = { page = SettingsPage.WALLPAPER }
                        )
                        SettingsListRow(
                            title = "Launcher theme",
                            onClick = { page = SettingsPage.LAUNCHER_THEME }
                        )
                        SettingsListRow(
                            title = "Skins",
                            trailing = "Manage",
                            onClick = { page = SettingsPage.SKINS }
                        )

                        SettingsSectionTitle("BEHAVIOR")
                        SettingsListRow(
                            title = "Default launcher",
                            trailing = if (isDefaultLauncher) "Active" else "Choose",
                            onClick = onSetAsHome
                        )
                        SettingsListRow(
                            title = "Gesture hints",
                            onClick = onShowDrawerHint
                        )

                        SettingsSectionTitle("ABOUT")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsCardShape)
                                .background(SettingsInk.copy(alpha = 0.5f))
                                .border(1.dp, SettingsBorder, SettingsCardShape)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.tagline_a),
                                contentDescription = "NekOS",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.size(width = 76.dp, height = 42.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("NekOS", color = SettingsInk, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Version $appVersion", color = SettingsMuted, fontSize = 11.sp)
                            }
                            TextButton(onClick = onOpenRepository) {
                                Text("GitHub", color = SettingsAccent, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    SettingsPage.WALLPAPER -> {
                        SettingsSectionTitle("BACKGROUND COLOR")
                        listOf(
                            "warm" to "Warm paper",
                            "sage" to "Soft sage",
                            "mist" to "Cool mist"
                        ).forEach { (key, label) ->
                            val selected = selectedBuiltInWallpaper == null && !hasWallpaper && backgroundPreset == key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(SettingsCardShape)
                                    .background(nekosBackgroundColor(key).copy(alpha = 0.5f))
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) SettingsAccent else SettingsBorder,
                                        shape = SettingsCardShape
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onBackgroundPresetChange(key) }
                                    )
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, color = SettingsInk, fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Text("✓", color = SettingsAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        SettingsSectionTitle("BUILT-IN WALLPAPERS")
                        BuiltInWallpaperOptions.forEach { option ->
                            val selected = selectedBuiltInWallpaper == option.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SettingsCardShape)
                                    .background(optionSurface.copy(alpha = 0.5f))
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) SettingsAccent else SettingsBorder,
                                        shape = SettingsCardShape
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onBuiltInWallpaperChange(option.key) }
                                    )
                                    .padding(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BuiltInWallpaperPreview(
                                    resourceId = option.resourceId,
                                    label = option.label
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        option.label,
                                        color = SettingsInk,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                                if (selected) {
                                    Text("✓", color = SettingsAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        SettingsSectionTitle("YOUR PHOTO")
                        SettingsActionCard(
                            title = if (hasWallpaper) "Change wallpaper" else "Choose from gallery",
                            action = "Choose",
                            surface = optionSurface,
                            onClick = onChooseWallpaper
                        )
                        if (hasWallpaper) {
                            SettingsActionCard(
                                title = "Remove photo",
                                action = "Remove",
                                surface = optionSurface,
                                onClick = onClearWallpaper
                            )
                        }
                    }

                    SettingsPage.LAUNCHER_THEME -> {
                        SettingsSectionTitle("COLOR PALETTE")
                        LauncherThemeOptions.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                pair.forEach { option ->
                                    val selected = launcherThemeKey == option.key
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(SettingsCardShape)
                                            .background(option.color.copy(alpha = 0.5f))
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) SettingsAccent else SettingsBorder,
                                                shape = SettingsCardShape
                                            )
                                            .selectable(
                                                selected = selected,
                                                role = Role.RadioButton,
                                                onClick = { onLauncherThemeChange(option.key) }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(option.label, color = SettingsInk, fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        if (selected) Text("✓", color = SettingsAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        SettingsSectionTitle("SHARED UI COLOR")
                        LauncherUiColorOptions.chunked(3).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { option ->
                                    val selected = launcherUiColor == option.argb
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(SettingsCardShape)
                                            .background(Color(0xFFF4F1EA).copy(alpha = 0.5f))
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) SettingsAccent else SettingsBorder,
                                                shape = SettingsCardShape
                                            )
                                            .selectable(
                                                selected = selected,
                                                role = Role.RadioButton,
                                                onClick = { onLauncherUiColorChange(option.argb) }
                                            )
                                            .padding(horizontal = 5.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(17.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(option.argb))
                                                .border(1.dp, SettingsInk.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        )
                                        Text(
                                            option.name,
                                            color = SettingsInk,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (pair.size < 3) repeat(3 - pair.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    SettingsPage.SKINS -> {
                        SettingsSectionTitle("INSTALLED SKINS")
                        installedSkins.forEach { skin ->
                            val selected = skin.id == activeSkinId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SettingsCardShape)
                                    .background(optionSurface.copy(alpha = 0.5f))
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) SettingsAccent else SettingsBorder,
                                        shape = SettingsCardShape
                                    )
                                    .clickable { onSelectSkin(skin.id) }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        skin.name,
                                        color = SettingsInk,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        "${skin.author.ifBlank { "NekOS" }} · v${skin.version}",
                                        color = SettingsMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                if (selected) {
                                    Text("Active", color = SettingsAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (!skin.isBuiltIn) {
                                    Text(
                                        "Remove",
                                        color = SettingsAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onDeleteSkin(skin.id) }
                                            .padding(horizontal = 7.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        SettingsSectionTitle("OFFICIAL STORE")
                        SettingsActionCard(
                            title = "Browse official skins",
                            action = "Explore",
                            surface = optionSurface,
                            onClick = { page = SettingsPage.OFFICIAL_SKIN_STORE }
                        )
                        SettingsActionCard(
                            title = "Import skin ZIP",
                            action = "Import",
                            surface = optionSurface,
                            onClick = onImportSkin
                        )
                        Text(
                            "Skin files change appearance only. Your apps, tags, gestures, and launcher settings remain unchanged.",
                            color = SettingsMuted,
                            fontSize = 11.sp
                        )
                    }

                    SettingsPage.OFFICIAL_SKIN_STORE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "OFFICIAL SKINS",
                                color = SettingsMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.9.sp
                            )
                            TextButton(
                                onClick = onRefreshOfficialStore,
                                enabled = !isOfficialStoreLoading
                            ) {
                                Text(
                                    if (isOfficialStoreLoading) "LOADING…" else "REFRESH",
                                    color = SettingsAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isOfficialStoreLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = SettingsAccent,
                                trackColor = SettingsBorder
                            )
                        }

                        if (officialStoreSkins.isEmpty() && !isOfficialStoreLoading) {
                            Text(
                                "No official skins available yet.",
                                color = SettingsMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        officialStoreSkins.forEach { skin ->
                            val installed = installedSkins.any { it.id == skin.skinKey || it.id == skin.id }
                            val pending = skin.id in pendingStoreSkinIds
                            val actionEnabled = storeBusySkinId != skin.id && !installed
                            val actionLabel = when {
                                installed -> "INSTALLED"
                                storeBusySkinId == skin.id -> "PLEASE WAIT…"
                                pending -> "CHECK & INSTALL"
                                skin.price <= 0L -> "INSTALL"
                                else -> "BUY"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(optionSurface.copy(alpha = 0.5f))
                                    .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
                                    .padding(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                OfficialStorePreview(skin.previewUrl)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        skin.name,
                                        color = SettingsInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "BY ${skin.author.ifBlank { "NEKOS COMMUNITY" }.uppercase()}",
                                        color = SettingsMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.35.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (skin.description.isNotBlank()) {
                                        Text(
                                            skin.description,
                                            color = SettingsMuted,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            if (skin.price <= 0L) "FREE" else "Rp ${"%,d".format(skin.price)}",
                                            color = if (skin.price <= 0L) Color(0xFF2E7D32) else Color(0xFF171717),
                                            fontSize = 11.sp,
                                            fontWeight = if (skin.price <= 0L) FontWeight.SemiBold else FontWeight.Bold,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                        OfficialStoreActionButton(
                                            label = actionLabel,
                                            enabled = actionEnabled,
                                            isInstall = skin.price <= 0L || pending,
                                            onClick = { onStoreAction(skin, pending) }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Paid skins unlock after payment confirmation.",
                            color = SettingsMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = when (page) {
                                    SettingsPage.MAIN -> onDismiss
                                    SettingsPage.OFFICIAL_SKIN_STORE -> ({ page = SettingsPage.SKINS })
                                    else -> ({ page = SettingsPage.MAIN })
                                }
                            ) {
                                Text(
                                    if (page == SettingsPage.MAIN) "Done" else "Back",
                                    color = SettingsAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                TopFrame(
                    colorArgb = launcherUiColor,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-6).dp)
                )
            }
        }
    }
}
