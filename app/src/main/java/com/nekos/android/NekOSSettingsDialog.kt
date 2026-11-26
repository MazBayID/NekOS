package com.nekos.android

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import java.net.URL
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun OfficialStorePreview(url: String) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        val loadedImage = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
        value = loadedImage
    }
    if (image != null) {
        Image(bitmap = image!!, contentDescription = "Skin preview",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(width = 82.dp, height = 96.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(modifier = Modifier.size(width = 82.dp, height = 96.dp).clip(RoundedCornerShape(10.dp))
            .background(SettingsBorder), contentAlignment = Alignment.Center) {
            Text("NEKOS", color = SettingsMuted, fontSize = 9.sp)
        }
    }
}

internal val SettingsInk = Color(0xFF34332F)
internal val SettingsMuted = Color(0xFF706B63)
internal val SettingsAccent = Color(0xFF746657)
internal val SettingsBorder = SettingsInk.copy(alpha = 0.13f)
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
    LauncherThemeOption("blush", "Blush", Color(0xFFF5E8E6)),
    LauncherThemeOption("mint", "Mint", Color(0xFFE6F1EC)),
    LauncherThemeOption("sky", "Sky", Color(0xFFE5F0F7))
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
    val motifTint = ColorFilter.tint(Color(launcherUiColor.toInt()), BlendMode.SrcIn)

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
                            .drawBehind {
                                motifBrush?.let {
                                    drawRect(brush = it, alpha = 0.18f, colorFilter = motifTint)
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
                                    fontSize = 29.sp,
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
        Column(
                modifier = Modifier
                    .heightIn(max = availableDialogHeight * 0.58f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (page) {
                    SettingsPage.MAIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.nekos_ui_wallpaper,
                                iconColorArgb = launcherUiColor,
                                surface = optionSurface,
                                title = "Wallpaper",
                                onClick = { page = SettingsPage.WALLPAPER }
                            )
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.nekos_ui_palette,
                                iconColorArgb = launcherUiColor,
                                surface = optionSurface,
                                title = "Launcher theme",
                                onClick = { page = SettingsPage.LAUNCHER_THEME }
                            )
                        }

                        SettingsSectionTitle("PERSONALIZATION")
                        SettingsActionCard(
                            title = "Skins",
                            action = "Manage",
                            surface = optionSurface,
                            onClick = { page = SettingsPage.SKINS }
                        )

                        SettingsSectionTitle("HOME BEHAVIOR")
                        SettingsActionCard(
                            title = "Default launcher",
                            action = if (isDefaultLauncher) "Active" else "Choose",
                            surface = optionSurface,
                            onClick = onSetAsHome
                        )
                        SettingsActionCard(
                            title = "Gesture hints",
                            action = "View",
                            surface = optionSurface,
                            onClick = onShowDrawerHint
                        )

                        SettingsSectionTitle("ABOUT")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsCardShape)
                                .background(SettingsInk.copy(alpha = 0.025f))
                                .border(1.dp, SettingsBorder, SettingsCardShape)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.tagline_a),
                                contentDescription = "NekOS",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 188.dp)
                            )
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
                                    .background(optionSurface)
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

                    SettingsPage.SKINS -> {
                        SettingsSectionTitle("INSTALLED SKINS")
                        installedSkins.forEach { skin ->
                            val selected = skin.id == activeSkinId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SettingsCardShape)
                                    .background(optionSurface)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) SettingsAccent else SettingsBorder,
                                        shape = SettingsCardShape
                                    )
                                    .clickable { onSelectSkin(skin.id) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        skin.name,
                                        color = SettingsInk,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        "${skin.author.ifBlank { "NekOS" }} · v${skin.version}",
                                        color = SettingsMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                if (selected) {
                                    Text("Active", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (!skin.isBuiltIn) {
                                    TextButton(onClick = { onDeleteSkin(skin.id) }) {
                                        Text("Remove", color = SettingsAccent, fontSize = 11.sp)
                                    }
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
                            fontSize = 10.sp
                        )
                    }

                    SettingsPage.OFFICIAL_SKIN_STORE -> {
                        SettingsSectionTitle("OFFICIAL COLLECTION")
                        Text("Browse, buy, and install skins directly on this device. Purchases are locked to this device.",
                            color = SettingsMuted, fontSize = 11.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onRefreshOfficialStore, enabled = !isOfficialStoreLoading) {
                                Text(if (isOfficialStoreLoading) "LOADING…" else "REFRESH", color = SettingsAccent, fontSize = 10.sp)
                            }
                        }
                        if (officialStoreSkins.isEmpty() && !isOfficialStoreLoading) {
                            Text("No skins are published yet.", color = SettingsMuted, fontSize = 12.sp)
                        }
                        officialStoreSkins.forEach { skin ->
                            val installed = installedSkins.any { it.id == skin.skinKey || it.id == skin.id }
                            val pending = skin.id in pendingStoreSkinIds
                            Row(modifier = Modifier.fillMaxWidth().clip(SettingsCardShape)
                                .background(optionSurface).border(1.dp, SettingsBorder, SettingsCardShape).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OfficialStorePreview(skin.previewUrl)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(skin.name, color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(skin.description.ifBlank { "By ${skin.author}" }, color = SettingsMuted, fontSize = 10.sp)
                                    Text(if (skin.price <= 0L) "FREE" else "Rp ${"%,d".format(skin.price)}",
                                        color = Color(0xFF39734B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { onStoreAction(skin, pending) },
                                        enabled = storeBusySkinId != skin.id && !installed) {
                                        Text(when {
                                            installed -> "INSTALLED"
                                            storeBusySkinId == skin.id -> "PLEASE WAIT…"
                                            pending -> "CHECK PAYMENT & INSTALL"
                                            skin.price <= 0L -> "INSTALL FREE"
                                            else -> "BUY NOW"
                                        }, color = SettingsAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text("Paid packages are delivered from private storage only after payment is confirmed. Temporary ZIP files are removed after installation.",
                            color = SettingsMuted, fontSize = 10.sp)
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
