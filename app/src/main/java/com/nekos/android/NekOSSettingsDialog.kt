package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsInk = Color(0xFF34332F)
private val SettingsMuted = Color(0xFF706B63)
private val SettingsAccent = Color(0xFF746657)

internal fun nekosBackgroundColor(preset: String): Color = when (preset) {
    "sage" -> Color(0xFFE8EEE5)
    "mist" -> Color(0xFFE8EDF2)
    else -> Color(0xFFF4F1EA)
}

@Composable
internal fun NekOSSettingsDialog(
    backgroundPreset: String,
    showDock: Boolean,
    hasWallpaper: Boolean,
    onBackgroundPresetChange: (String) -> Unit,
    onShowDockChange: (Boolean) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                .clickable { onBackgroundPresetChange(key) }
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

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsSectionTitle("HOME LAYOUT")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Show Home Dock",
                                color = SettingsInk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Hide the Dock without removing its apps.",
                                color = SettingsMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Checkbox(checked = showDock, onCheckedChange = onShowDockChange)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SettingsAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFFFEFCF8),
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
