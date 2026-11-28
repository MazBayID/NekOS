package com.nekos.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MIDTRANS_FINISH_HOST = "mazbayid.github.io"
private const val MIDTRANS_FINISH_PATH = "/skin-payment-return/"

internal fun isTrustedStoreCheckout(value: String, provider: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    val host = uri.host?.lowercase().orEmpty()
    val allowedHosts = when (provider.lowercase()) {
        "midtrans" -> setOf("app.midtrans.com", "app.sandbox.midtrans.com")
        "paypal" -> setOf("www.paypal.com", "www.sandbox.paypal.com")
        else -> emptySet()
    }
    uri.scheme?.lowercase() == "https" && host in allowedHosts &&
        uri.userInfo == null && (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

internal fun launchPayPalCheckout(context: Context, checkoutUrl: String): Boolean {
    if (!isTrustedStoreCheckout(checkoutUrl, "paypal")) return false
    val uri = Uri.parse(checkoutUrl)
    return runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
        true
    }.getOrElse {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrDefault(false)
    }
}

private fun isPaymentAppLink(uri: Uri): Boolean {
    val host = uri.host?.lowercase().orEmpty()
    val scheme = uri.scheme?.lowercase().orEmpty()
    return (scheme != "http" && scheme != "https") ||
        host in setOf(
            "gojek.link",
            "gopay.co.id",
            "gopayapp.page.link",
            "gojek.page.link",
            "wsa.wallet.airpay.co.id",
            "tmrwbyuobid.page.link"
        )
}

@Composable
internal fun StoreCheckoutDialog(
    checkoutUrl: String,
    provider: String,
    onDismiss: () -> Unit,
    onPaymentFinished: () -> Unit,
    onCancelPurchase: () -> Unit
) {
    var paymentWebView by remember { mutableStateOf<WebView?>(null) }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var paypalLaunchFailed by remember(checkoutUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    val isPaypal = provider.equals("paypal", ignoreCase = true)
    val trustedUrl = remember(checkoutUrl, provider) { isTrustedStoreCheckout(checkoutUrl, provider) }

    LaunchedEffect(checkoutUrl, provider, trustedUrl) {
        if (isPaypal && trustedUrl) {
            paypalLaunchFailed = !launchPayPalCheckout(context, checkoutUrl)
        }
    }

    BackHandler {
        val webView = paymentWebView
        if (webView?.canGoBack() == true) webView.goBack() else onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            paymentWebView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            paymentWebView = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
                .fillMaxHeight(0.88f),
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
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "PEMBAYARAN ${provider.uppercase()}",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text("TUTUP", color = Color.White, fontSize = 11.sp)
                    }
                }

                if (!trustedUrl) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Alamat pembayaran tidak dikenali. Demi keamanan, halaman tidak dibuka.",
                            color = Color(0xFF34332F),
                            fontSize = 14.sp
                        )
                        TextButton(onClick = onDismiss) { Text("Tutup") }
                    }
                } else if (isPaypal) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (paypalLaunchFailed) {
                                "NekOS tidak dapat membuka checkout PayPal. Pastikan browser tersedia, lalu coba lagi."
                            } else {
                                "Checkout PayPal dibuka di browser. Selesaikan atau batalkan pembayaran di sana, lalu kembali ke NekOS."
                            },
                            color = Color(0xFF34332F),
                            fontSize = 14.sp
                        )
                        if (paypalLaunchFailed) {
                            TextButton(onClick = {
                                paypalLaunchFailed = !launchPayPalCheckout(context, checkoutUrl)
                            }) { Text("COBA BUKA PAYPAL LAGI") }
                        } else {
                            TextButton(onClick = onPaymentFinished) {
                                Text("SUDAH KEMBALI — PERIKSA STATUS")
                            }
                        }
                        TextButton(onClick = { showCancelConfirmation = true }) {
                            Text("BATALKAN PEMBELIAN", color = Color(0xFF9B3D35), fontSize = 11.sp)
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { context ->
                            WebView(context).apply {
                                paymentWebView = this
                                settings.javaScriptEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.setSupportMultipleWindows(false)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val uri = request.url
                                        if (uri.host == MIDTRANS_FINISH_HOST &&
                                            uri.path?.startsWith(MIDTRANS_FINISH_PATH) == true
                                        ) {
                                            onPaymentFinished()
                                            return true
                                        }
                                        if (isPaymentAppLink(uri)) {
                                            return try {
                                                if (uri.scheme == "intent") {
                                                    val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                                    context.startActivity(intent)
                                                } else {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                                }
                                                true
                                            } catch (_: Exception) {
                                                true
                                            }
                                        }
                                        return false
                                    }
                                }
                                loadUrl(checkoutUrl)
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Batalkan pembelian?") },
            text = {
                Text(
                    "NekOS akan meminta penyedia pembayaran membatalkan transaksi. Jika pembayaran ternyata sudah berhasil, pembelian tidak akan dibatalkan dan skin tetap dipasang. Jika penyedia belum mengonfirmasi pembayaran, periksa kembali statusnya sebelum mencoba membeli lagi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmation = false
                    onCancelPurchase()
                }) { Text("YA, BATALKAN") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) { Text("KEMBALI") }
            }
        )
    }
}
