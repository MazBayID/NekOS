package com.nekos.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun nekosBackgroundColor(preset: String): Color = when (preset) {
    "sage" -> Color(0xFFE8EEE5)
    "mist" -> Color(0xFFE8EDF2)
    else -> Color(0xFFF4F1EA)
}

@Composable
internal fun NekOSSettingsDialog(
    backgroundPreset: String,
    showDock: Boolean,
    onBackgroundPresetChange: (String) -> Unit,
    onShowDockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home background", fontWeight = FontWeight.SemiBold)
                    listOf(
                        "warm" to "Warm paper",
                        "sage" to "Soft sage",
                        "mist" to "Cool mist"
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(nekosBackgroundColor(key))
                                .clickable { onBackgroundPresetChange(key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = Color(0xFF34332F))
                            Text(
                                if (backgroundPreset == key) "Selected" else "",
                                color = Color(0xFF34332F),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Home Dock", fontWeight = FontWeight.SemiBold)
                        Text("Show or hide the Dock without removing its apps.")
                    }
                    Checkbox(checked = showDock, onCheckedChange = onShowDockChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
