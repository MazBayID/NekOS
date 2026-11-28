package com.nekos.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun isTrustedStoreCheckout(value: String, provider: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    val host = uri.host?.lowercase().orEmpty()
    val paypalHosts = setOf("www.paypal.com", "www.sandbox.paypal.com")
    val midtransHosts = setOf(
        "app.midtrans.com",
        "app.sandbox.midtrans.com",
        "simulator.sandbox.midtrans.com",
        "gopay.co.id",
        "gojek.link",
        "link.gojek.com"
    )
    val allowed = if (provider.equals("paypal", true)) {
        host in paypalHosts
    } else {
        host in midtransHosts || host.endsWith(".gopay.co.id") || host.endsWith(".gojek.link")
    }
    uri.scheme?.lowercase() == "https" && allowed &&
        uri.userInfo == null && (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

private fun isGoPayCheckout(value: String): Boolean = runCatching {
    val host = Uri.parse(value).host?.lowercase().orEmpty()
    host == "simulator.sandbox.midtrans.com" ||
        host == "gopay.co.id" || host.endsWith(".gopay.co.id") ||
        host == "gojek.link" || host.endsWith(".gojek.link") ||
        host == "link.gojek.com"
}.getOrDefault(false)

@Composable
internal fun StoreCheckoutDialog(
    checkoutUrl: String,
    provider: String,
    onDismiss: () -> Unit,
    onPaymentFinished: () -> Unit,
    onCancelPurchase: () -> Unit
) {
    val context = LocalContext.current
    val isPaypal = provider.equals("paypal", ignoreCase = true)
    val isGoPay = !isPaypal && isGoPayCheckout(checkoutUrl)
    val trustedUrl = remember(checkoutUrl, provider) {
        isTrustedStoreCheckout(checkoutUrl, provider)
    }
    var showCancelConfirmation by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .fillMaxHeight(0.48f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF7F8FA),
            shadowElevation = 20.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(Color(0xFF071A33))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isPaypal) "PEMBAYARAN PAYPAL"
                        else if (isGoPay) "PEMBAYARAN GOPAY"
                        else "PEMBAYARAN MIDTRANS",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text("TUTUP", color = Color.White, fontSize = 11.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            !trustedUrl -> "Alamat pembayaran tidak dikenali. Demi keamanan, tautan tidak akan dibuka."
                            isPaypal -> "Lanjutkan pembayaran PayPal. Setelah selesai, kembali ke NekOS agar status pembayaran diperiksa otomatis."
                            isGoPay -> "NekOS akan membuka aplikasi GoPay. Setelah pembayaran selesai, kamu akan diarahkan kembali ke NekOS dan statusnya diverifikasi otomatis."
                            else -> "Lanjutkan pembayaran Midtrans melalui browser. Setelah selesai, kembali ke NekOS agar status pembayaran diperiksa otomatis."
                        },
                        color = Color(0xFF34332F),
                        fontSize = 14.sp
                    )
                    if (trustedUrl) {
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        "Tidak ada aplikasi yang dapat membuka tautan pembayaran.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                when {
                                    isPaypal -> "BUKA PAYPAL"
                                    isGoPay -> "BUKA GOPAY"
                                    else -> "BUKA PEMBAYARAN"
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showCancelConfirmation = true }) {
                        Text(
                            "BATALKAN PEMBELIAN",
                            color = Color(0xFF9B3D35),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Batalkan pembelian?") },
            text = {
                Text(
                    if (isPaypal) {
                        "NekOS akan memeriksa status PayPal saat kamu kembali. Menutup halaman tidak membatalkan pesanan PayPal yang sudah dibuat; jika pembayaran disetujui, skin tetap akan diberikan."
                    } else {
                        "NekOS akan memeriksa status transaksi Midtrans terlebih dahulu. Jika pembayaran sudah berhasil, skin tetap akan diberikan. Jika belum dibayar, transaksi akan diminta untuk dibatalkan."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmation = false
                    onCancelPurchase()
                }) { Text("YA, BATALKAN") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("KEMBALI")
                }
            }
        )
    }
}