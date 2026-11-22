package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onLauncherThemeChange: (String) -> Unit,
    launcherUiColor: Long,
    onLauncherUiColorChange: (Long) -> Unit,
    hasWallpaper: Boolean,
    appVersion: String,
    isDefaultLauncher: Boolean,
    onSetAsHome: () -> Unit,
    onBackgroundPresetChange: (String) -> Unit,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                when (page) {
                    SettingsPage.MAIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                symbol = "▧",
                                title = "Wallpaper",
                                description = if (hasWallpaper) "Custom image" else "Color or photo",
                                onClick = { page = SettingsPage.WALLPAPER }
                            )
                            SettingsNavigationCard(
                                modifier = Modifier.weight(1f),
                                symbol = "◒",
                                title = "Launcher theme",
                                description = LauncherThemeOptions.first { it.key == launcherThemeKey }.label,
                                onClick = { page = SettingsPage.LAUNCHER_THEME }
                            )
                        }

                        SettingsSectionTitle("HOME BEHAVIOR")
                        SettingsActionCard(
                            title = "Default launcher",
                            description = if (isDefaultLauncher) {
                                "NekOS is your default Home app. You can choose another launcher."
                            } else {
                                "Choose which app opens when you press the Home button."
                            },
                            action = "Choose",
                            onClick = onSetAsHome
                        )
                        SettingsActionCard(
                            title = "Gesture hints",
                            description = "Review the gesture that opens the App Drawer.",
                            action = "View",
                            onClick = onShowDrawerHint
                        )

                        SettingsSectionTitle("ABOUT")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsCardShape)
                                .border(1.dp, SettingsBorder, SettingsCardShape)
                                .padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("NekOS", color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Version $appVersion", color = SettingsMuted, fontSize = 12.sp)
                            Text("Licensed under the MIT License.", color = SettingsMuted, fontSize = 12.sp)
                            Text("Copyright © 2026 MazBayID (Bayu Dirgantara)", color = SettingsMuted, fontSize = 12.sp)
                            TextButton(onClick = onOpenRepository) {
                                Text("GitHub repository", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    SettingsPage.WALLPAPER -> {
                        Text(
                            "Choose a soft color or use a photo from your gallery.",
                            color = SettingsMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        SettingsSectionTitle("BACKGROUND COLOR")
                        listOf(
                            "warm" to "Warm paper",
                            "sage" to "Soft sage",
                            "mist" to "Cool mist"
                        ).forEach { (key, label) ->
                            val selected = backgroundPreset == key
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
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, color = SettingsInk, fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Text("✓", color = SettingsAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        SettingsSectionTitle("YOUR PHOTO")
                        SettingsActionCard(
                            title = if (hasWallpaper) "Change wallpaper" else "Choose from gallery",
                            description = if (hasWallpaper) "Select another image for your Home screen." else "Use one of your photos as the Home background.",
                            action = "Choose",
                            onClick = onChooseWallpaper
                        )
                        if (hasWallpaper) {
                            SettingsActionCard(
                                title = "Remove photo",
                                description = "Return to the selected background color.",
                                action = "Remove",
                                onClick = onClearWallpaper
                            )
                        }
                    }

                    SettingsPage.LAUNCHER_THEME -> {
                        Text(
                            "Choose the launcher surface palette and the shared color for UI icons, the tag frame, and the NekOS logo.",
                            color = SettingsMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
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
                                            .padding(horizontal = 11.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(option.label, color = SettingsInk, fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        if (selected) Text("✓", color = SettingsAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        SettingsSectionTitle("SHARED UI COLOR")
                        Text(
                            "One color is applied to every UI icon, the tag frame, and text_logo_sec. Tag icons keep their own color setting.",
                            color = SettingsMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
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
                                            .padding(horizontal = 7.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(20.dp)
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
        },
        confirmButton = {
            TextButton(onClick = if (page == SettingsPage.MAIN) onDismiss else ({ page = SettingsPage.MAIN })) {
                Text(if (page == SettingsPage.MAIN) "Done" else "Back", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = surfaceColor,
        titleContentColor = SettingsInk,
        textContentColor = SettingsInk,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun SettingsNavigationCard(
    modifier: Modifier = Modifier,
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 104.dp)
            .clip(SettingsCardShape)
            .background(SettingsInk.copy(alpha = 0.025f))
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(symbol, color = SettingsAccent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(title, color = SettingsInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = SettingsMuted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .border(1.dp, SettingsBorder, SettingsCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = SettingsMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
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
        fontWeight = FontWeight.Bold
    )
}
