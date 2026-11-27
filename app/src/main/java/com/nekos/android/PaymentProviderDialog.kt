package com.nekos.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PaymentProviderDialog(
    skinName: String,
    onDismiss: () -> Unit,
    onConfirm: (provider: String, email: String) -> Unit
) {
    var provider by remember { mutableStateOf("midtrans") }
    var email by remember { mutableStateOf("") }
    val validEmail = remember(email) {
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PILIH PEMBAYARAN", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skinName, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { provider = "midtrans" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = provider == "midtrans", onClick = { provider = "midtrans" })
                    Text("Midtrans", fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { provider = "duitku" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = provider == "duitku", onClick = { provider = "duitku" })
                    Text("Duitku", fontSize = 14.sp)
                }
                if (provider == "duitku") {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Email pembayaran") },
                        placeholder = { Text("nama@email.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Text(
                        "Email hanya dikirim ke Duitku untuk membuat invoice. NekOS tidak menyimpannya.",
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = provider != "duitku" || validEmail,
                onClick = { onConfirm(provider, if (provider == "duitku") email.trim() else "") }
            ) {
                Text("LANJUTKAN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL") }
        }
    )
}
