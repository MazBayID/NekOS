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
    hasPendingPayment: Boolean = false,
    currentProvider: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val paypalAvailable = priceUsd > 0.0
    val initialProvider = when {
        currentProvider.equals("paypal", true) -> "midtrans"
        currentProvider.equals("midtrans", true) && paypalAvailable -> "paypal"
        paypalAvailable -> "paypal"
        else -> "midtrans"
    }
    var selectedProvider by remember(skinName, currentProvider, priceUsd) {
        mutableStateOf(initialProvider)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (hasPendingPayment) "GANTI METODE PEMBAYARAN" else "PILIH PEMBAYARAN",
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skinName, fontSize = 13.sp)
                if (hasPendingPayment) {
                    Text(
                        "NekOS akan memeriksa transaksi sebelumnya terlebih dahulu. Pembayaran baru hanya dibuat jika transaksi lama sudah tidak aktif.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == "midtrans",
                        onClick = { selectedProvider = "midtrans" },
                        enabled = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Midtrans", fontSize = 14.sp)
                        Text(
                            "Rp${String.format("%,d", priceIdr)}",
                            fontSize = 11.sp
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
                        enabled = paypalAvailable
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("PayPal", fontSize = 14.sp, color = if (paypalAvailable) Color.Unspecified else Color.Gray)
                        Text(
                            if (paypalAvailable) String.format(java.util.Locale.US, "US$%.2f", priceUsd)
                            else "Belum tersedia untuk skin ini",
                            fontSize = 11.sp,
                            color = if (paypalAvailable) Color.Unspecified else Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedProvider) }) {
                Text("LANJUTKAN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL") }
        }
    )
}
