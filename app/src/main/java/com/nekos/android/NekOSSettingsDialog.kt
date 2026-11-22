package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
internal fun NekOSSettingsDialog(
    backgroundPreset: String,
    launcherThemeKey: String,
    onLauncherThemeChange: (String) -> Unit,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Settings",
                color = SettingsInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SettingsSectionTitle("APPEARANCE")
                    Text(
                        "Home background",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
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
                                .clip(RoundedCornerShape(13.dp))
                                .background(nekosBackgroundColor(key))
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) SettingsAccent else SettingsInk.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(13.dp)
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
                            Text(
                                label,
                                color = SettingsInk,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (selected) {
                                Text(
                                    "✓",
                                    color = SettingsAccent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Launcher theme",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "App Drawer and dialog background",
                        color = SettingsMuted,
                        fontSize = 12.sp
                    )
                    LauncherThemeOptions.forEach { option ->
                        val selected = launcherThemeKey == option.key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(option.color)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) SettingsAccent else SettingsInk.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onLauncherThemeChange(option.key) }
                                )
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                option.label,
                                color = SettingsInk,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (selected) Text("✓", color = SettingsAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = SettingsInk.copy(alpha = 0.12f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsSectionTitle("WALLPAPER")
                    Text(
                        "Home wallpaper",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        if (hasWallpaper) {
                            "A custom image is currently applied."
                        } else {
                            "Choose an image from your photo library."
                        },
                        color = SettingsMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onChooseWallpaper) {
                            Text("Choose image", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
                        }
                        if (hasWallpaper) {
                            TextButton(onClick = onClearWallpaper) {
                                Text("Remove", color = SettingsMuted)
                            }
                        }
                    }
                }

                HorizontalDivider(color = SettingsInk.copy(alpha = 0.12f))

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SettingsSectionTitle("HOME BEHAVIOR")
                    Text(
                        "Default launcher",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        if (isDefaultLauncher) {
                            "NekOS is currently your default Home app. You can choose another launcher here."
                        } else {
                            "Choose which app opens when you press the Home button."
                        },
                        color = SettingsMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    TextButton(onClick = onSetAsHome) {
                        Text(
                            "Choose launcher",
                            color = SettingsAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Gesture hints",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Review the gesture that opens the App Drawer.",
                        color = SettingsMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    TextButton(onClick = onShowDrawerHint) {
                        Text("Show App Drawer hint", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = SettingsInk.copy(alpha = 0.12f))

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SettingsSectionTitle("ABOUT")
                    Text(
                        "NekOS",
                        color = SettingsInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text("Version $appVersion", color = SettingsMuted, fontSize = 12.sp)
                    Text(
                        "Licensed under the MIT License.",
                        color = SettingsMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        "Copyright © 2026 MazBayID (Bayu Dirgantara)",
                        color = SettingsMuted,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = onOpenRepository) {
                        Text("GitHub repository", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = launcherThemeColor(launcherThemeKey),
        titleContentColor = SettingsInk,
        textContentColor = SettingsInk
    )
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
