package com.nekos.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PaymentProviderDialog(
    skinName: String,
    onDismiss: () -> Unit,
    onConfirm: (provider: String, email: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PILIH PEMBAYARAN", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skinName, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = true, onClick = null)
                    Text("Midtrans", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("midtrans", "") }) {
                Text("LANJUTKAN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL") }
        }
    )
}
