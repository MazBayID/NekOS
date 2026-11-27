package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private val TagColorOptions = listOf(
    "Sand" to 0xFFE7E1D6L,
    "Blue" to 0xFFDCE8F8L,
    "Lavender" to 0xFFE8DFF5L,
    "Rose" to 0xFFF5DDE3L,
    "Peach" to 0xFFF6E1D2L,
    "Amber" to 0xFFF4E8C9L,
    "Mint" to 0xFFD9EEE6L,
    "Sage" to 0xFFE0E9D8L,
    "Slate" to 0xFFE1E5E9L
)

private val TagIconFrameColors = listOf(
    "Ocean" to 0xFF114B56L,
    "Blue" to 0xFF2563EBL,
    "Purple" to 0xFF7C3AEDL,
    "Rose" to 0xFFE11D48L,
    "Red" to 0xFFDC2626L,
    "Orange" to 0xFFEA580CL,
    "Amber" to 0xFFD97706L,
    "Green" to 0xFF16A34AL,
    "Mint" to 0xFF0D9488L,
    "Slate" to 0xFF64748BL,
    "Silver" to 0xFFA1A1AAL,
    "Dark" to 0xFF27272AL
)

@Composable
internal fun TagColorPickerDialog(
    selectedColor: Long,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    NekOSAlertDialog(
        topFrameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { Text("Tag Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 200.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(TagColorOptions) { (name, colorValue) ->
                    val selected = selectedColor == colorValue
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(colorValue.toInt()).copy(alpha = 0.5f))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) SettingsAccent else SettingsBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onColorSelected(colorValue)
                                onDismiss()
                            }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            name,
                            color = SettingsInk,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        if (selected) {
                            SkinUiIcon("icon_check.webp", SettingsAccent, "Selected", Modifier.size(13.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinUiIcon("icon_check.webp", SettingsAccent, null, Modifier.size(15.dp))
                    Text("Done")
                }
            }
        }
    )
}

@Composable
internal fun TagIconFrameColorPickerDialog(
    selectedColor: Long,
    launcherSurface: Color,
    launcherUiColorArgb: Long,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    NekOSAlertDialog(
        topFrameColorArgb = launcherUiColorArgb,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = launcherSurface,
        titleContentColor = Color(0xFF34332F),
        textContentColor = Color(0xFF34332F),
        title = { Text("Icon Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.heightIn(max = 100.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(TagIconFrameColors) { (_, colorValue) ->
                    val selected = selectedColor == colorValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF4F1EA).copy(alpha = 0.5f))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) SettingsAccent else SettingsBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onColorSelected(colorValue)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val swatchColor = Color(colorValue.toInt())
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, Color(0xFF34332F).copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                SkinUiIcon(
                                    "icon_check.webp",
                                    if (swatchColor.luminance() > 0.5f) Color(0xFF34332F) else Color.White,
                                    "Selected",
                                    Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinUiIcon("icon_check.webp", SettingsAccent, null, Modifier.size(15.dp))
                    Text("Done")
                }
            }
        }
    )
}
