package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SettingsInk = Color(0xFF34332F)
private val SettingsMuted = Color(0xFF706B63)
private val SettingsAccent = Color(0xFF746657)
private val SettingsBorder = SettingsInk.copy(alpha = 0.13f)
private val SettingsCardShape = RoundedCornerShape(16.dp)

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
    LauncherThemeOption("blush", "Blush", Color(0xFFF5E8E6)),
    LauncherThemeOption("mint", "Mint", Color(0xFFE6F1EC)),
    LauncherThemeOption("sky", "Sky", Color(0xFFE5F0F7))
)

internal fun normalizeLauncherTheme(key: String?): String =
    LauncherThemeOptions.firstOrNull { it.key == key }?.key ?: "porcelain"

internal fun launcherThemeColor(key: String): Color =
    LauncherThemeOptions.firstOrNull { it.key == normalizeLauncherTheme(key) }?.color
        ?: Color(0xFFFEFCF8)

private enum class SettingsPage { MAIN, WALLPAPER, LAUNCHER_THEME }

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
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    val surfaceColor = launcherThemeColor(launcherThemeKey)

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = SettingsPage.MAIN
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
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
                                    fontSize = 29.sp,
                                    modifier = Modifier.clickable { page = SettingsPage.MAIN }
                                )
                            }
                            Text(
                                when (page) {
                                    SettingsPage.MAIN -> "Settings"
                                    SettingsPage.WALLPAPER -> "Wallpaper"
                                    SettingsPage.LAUNCHER_THEME -> "Launcher theme"
                                },
                                color = SettingsInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
        Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (page) {
                    SettingsPage.MAIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.nekos_ui_wallpaper,
                                iconColorArgb = launcherUiColor,
                                title = "Wallpaper",
                                onClick = { page = SettingsPage.WALLPAPER }
                            )
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.nekos_ui_palette,
                                iconColorArgb = launcherUiColor,
                                title = "Launcher theme",
                                onClick = { page = SettingsPage.LAUNCHER_THEME }
                            )
                        }

                        SettingsSectionTitle("HOME BEHAVIOR")
                        SettingsActionCard(
                            title = "Default launcher",
                            action = if (isDefaultLauncher) "Active" else "Choose",
                            onClick = onSetAsHome
                        )
                        SettingsActionCard(
                            title = "Gesture hints",
                            action = "View",
                            onClick = onShowDrawerHint
                        )

                        SettingsSectionTitle("ABOUT")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsCardShape)
                                .background(SettingsInk.copy(alpha = 0.025f))
                                .border(1.dp, SettingsBorder, SettingsCardShape)
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("NekOS", color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Version $appVersion", color = SettingsMuted, fontSize = 12.sp)
                            TextButton(onClick = onOpenRepository) {
                                Text("GitHub repository", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
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
                                    .background(nekosBackgroundColor(key))
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
                                Text(label, color = SettingsInk, fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Text("✓", color = SettingsAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        SettingsSectionTitle("BUILT-IN WALLPAPERS")
                        BuiltInWallpaperOptions.forEach { option ->
                            val selected = selectedBuiltInWallpaper == option.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SettingsCardShape)
                                    .background(SettingsInk.copy(alpha = 0.025f))
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
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                                if (selected) {
                                    Text("✓", color = SettingsAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        SettingsSectionTitle("YOUR PHOTO")
                        SettingsActionCard(
                            title = if (hasWallpaper) "Change wallpaper" else "Choose from gallery",
                            action = "Choose",
                            onClick = onChooseWallpaper
                        )
                        if (hasWallpaper) {
                            SettingsActionCard(
                                title = "Remove photo",
                                action = "Remove",
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
                                            .background(option.color)
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
                                        Text(option.label, color = SettingsInk, fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        if (selected) Text("✓", color = SettingsAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                            .background(Color(0xFFF4F1EA))
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
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (pair.size < 3) repeat(3 - pair.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = if (page == SettingsPage.MAIN) onDismiss else ({ page = SettingsPage.MAIN })
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
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BuiltInWallpaperPreview(
    resourceId: Int,
    label: String
) {
    val context = LocalContext.current
    val preview = produceState<ImageBitmap?>(initialValue = null, resourceId) {
        value = withContext(Dispatchers.IO) {
            loadBuiltInHomeWallpaper(context, resourceId, maxDimension = 480)
        }
    }.value

    if (preview != null) {
        Image(
            bitmap = preview,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 60.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SettingsInk.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun SettingsNavigationCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    iconColorArgb: Long,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(SettingsCardShape)
            .background(SettingsInk.copy(alpha = 0.025f))
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ThemedUiArtworkImage(
            resourceId = iconRes,
            colorArgb = iconColorArgb,
            modifier = Modifier.size(23.dp)
        )
        Text(title, color = SettingsInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(SettingsInk.copy(alpha = 0.025f))
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(action, color = SettingsAccent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsSectionTitle(label: String) {
    Text(
        label,
        color = SettingsMuted,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 3.dp, bottom = 0.dp)
    )
}
