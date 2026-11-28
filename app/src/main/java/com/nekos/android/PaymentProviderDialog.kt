package com.nekos.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PaymentProviderDialog(
    skinName: String,
    priceIdr: Long,
    priceUsd: Double,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedProvider by remember(skinName) { mutableStateOf("paypal") }
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
                    RadioButton(
                        selected = false,
                        onClick = {},
                        enabled = false
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Midtrans · NON-ACTIVE", fontSize = 14.sp, color = Color.Gray)
                        Text(
                            "Rp${String.format("%,d", priceIdr)}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == "paypal",
                        onClick = { selectedProvider = "paypal" },
                        enabled = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("PayPal", fontSize = 14.sp)
                        Text(
                            String.format(java.util.Locale.US, "US$%.2f", priceUsd),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("paypal") }) {
                Text("LANJUTKAN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL") }
        }
    )
}
