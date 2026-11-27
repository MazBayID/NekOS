package com.nekos.android

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal data class OfficialStoreSkin(
    val id: String, val skinKey: String, val name: String, val author: String,
    val description: String, val price: Long, val currency: String, val previewUrl: String
)
internal data class StoreActionResult(
    val success: Boolean, val message: String, val checkoutUrl: String? = null,
    val orderId: String? = null, val installedSkinId: String? = null,
    val paymentFailed: Boolean = false
)

internal fun isTrustedSkinDownloadUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" &&
        uri.host == "vizmufbfaghqucsoukne.supabase.co" &&
        (uri.port == -1 || uri.port == 443) &&
        uri.userInfo == null &&
        uri.path.startsWith("/storage/v1/object/sign/") &&
        !uri.rawQuery.isNullOrBlank()
}.getOrDefault(false)

internal object OfficialSkinStoreClient {
    private const val API = "https://vizmufbfaghqucsoukne.supabase.co/functions/v1/skin-store"
    private const val MAX_PACKAGE_BYTES = 32L * 1024L * 1024L
    private const val PREFERENCES = "nekos_skin_store"
    private const val PENDING_PREFIX = "pending_order_"

    private fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.lowercase()
            ?: throw IllegalStateException("Device identity is unavailable")

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = (URL("$API$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrElse {
                throw IllegalStateException("Store server returned an invalid response ($code)")
            }
            if (code !in 200..299) {
                throw IllegalStateException(json.optString("details").ifBlank {
                    when (json.optString("error")) {
                        "payment_provider_not_configured" -> "Payment is not configured yet."
                        "purchase_required" -> "This skin has not been purchased on this device."
                        "already_owned" -> "This device already owns this skin."
                        "midtrans_expire_permission_required" -> "Agar transaksi otomatis ditutup setelah 15 menit, aktifkan izin 'Expire transaction: Write' pada Access Key Midtrans."
                        "midtrans_expire_failed" -> "Midtrans belum berhasil menutup transaksi. Silakan periksa kembali status pembayaran."
                        "midtrans_status_check_failed" -> "NekOS gagal memeriksa status pembayaran ke Midtrans. Coba lagi beberapa saat."
                        else -> json.optString("error").ifBlank { "Store request failed ($code)" }
                    }
                })
            }
            return json
        } finally { connection.disconnect() }
    }

    fun pendingSkinIds(context: Context): Set<String> = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .all.keys.filter { it.startsWith(PENDING_PREFIX) }.map { it.removePrefix(PENDING_PREFIX) }.toSet()

    fun loadCatalog(): List<OfficialStoreSkin> {
        val array = request("").optJSONArray("skins") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(OfficialStoreSkin(
                    id=item.optString("id"), skinKey=item.optString("skin_key"),
                    name=item.optString("name"), author=item.optString("author","Community"),
                    description=item.optString("description"), price=item.optDouble("price_amount",0.0).toLong(),
                    currency=item.optString("currency","IDR"), previewUrl=item.optString("preview_url")
                ))
            }
        }
    }

    fun buy(context: Context, skin: OfficialStoreSkin): StoreActionResult {
        val result = request("/purchase","POST",JSONObject()
            .put("android_id",androidId(context)).put("app_version",appVersion(context)).put("skin_id",skin.id))
        if (result.optBoolean("free")) return downloadAndInstall(context,skin.id)
        val orderId=result.optString("order_id")
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit()
            .putString(PENDING_PREFIX+skin.id,orderId).apply()
        return StoreActionResult(true,"Complete payment in the opened payment page, then return to NekOS.",
            checkoutUrl=result.optString("checkout_url"),orderId=orderId)
    }

    @Synchronized
    fun checkPaymentAndInstall(context: Context, skin: OfficialStoreSkin): StoreActionResult {
        val prefs=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
        val orderId=prefs.getString(PENDING_PREFIX+skin.id,null)
            ?: return StoreActionResult(false,"No pending payment found for this skin.")
        val result=request("/status","POST",JSONObject().put("android_id",androidId(context))
            .put("app_version",appVersion(context)).put("order_id",orderId))
        if (!result.optBoolean("owned")) {
            val status=result.optString("status").lowercase()
            val failureMessage = when (status) {
                "expire", "expired", "failed" -> "Pembayaran gagal atau kedaluwarsa. Midtrans belum mengonfirmasi pembayaran, jadi skin belum bisa dipasang. Kamu bisa mencoba membeli skin ini lagi."
                "cancel", "cancelled" -> "Pembayaran dibatalkan. Tidak ada skin yang dipasang dan kamu bisa mencoba membeli lagi."
                "deny", "denied" -> "Pembayaran ditolak oleh penyedia pembayaran. Silakan coba metode pembayaran lain."
                else -> null
            }
            if (failureMessage != null) {
                // Commit synchronously so concurrent checks and Store refreshes
                // cannot resurrect this terminal order from stale preferences.
                prefs.edit().remove(PENDING_PREFIX + skin.id).commit()
                return StoreActionResult(false, failureMessage, orderId = orderId, paymentFailed = true)
            }
            return StoreActionResult(false,if(status=="pending")
                "Pembayaran belum dikonfirmasi. Silakan periksa kembali beberapa saat lagi."
                else "Status pembayaran: $status")
        }
        val installed=downloadAndInstall(context,skin.id)
        if(installed.success)prefs.edit().remove(PENDING_PREFIX+skin.id).apply()
        return installed
    }

    fun downloadAndInstall(context: Context, skinId: String): StoreActionResult {
        val grant=request("/download","POST",JSONObject().put("android_id",androidId(context))
            .put("app_version",appVersion(context)).put("skin_id",skinId))
        val downloadUrl=grant.optString("download_url")
        val expectedHash=grant.optString("package_sha256")
        val expectedSize=grant.optLong("package_size_bytes")
        if(!isTrustedSkinDownloadUrl(downloadUrl)||!expectedHash.matches(Regex("^[0-9a-f]{64}$"))||
            expectedSize !in 1..MAX_PACKAGE_BYTES)return StoreActionResult(false,"Invalid package delivery response.")
        val archive=File(context.cacheDir,"nekos-skin-download-${UUID.randomUUID()}.zip")
        var connection:HttpURLConnection?=null
        return try {
            connection=(URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout=15_000;readTimeout=45_000;instanceFollowRedirects=false
            }
            if(connection.responseCode!=HttpURLConnection.HTTP_OK)
                throw IllegalStateException("Skin download failed (HTTP ${connection.responseCode}).")
            connection.inputStream.use{input->archive.outputStream().use{output->
                val buffer=ByteArray(DEFAULT_BUFFER_SIZE);var total=0L
                while(true){val count=input.read(buffer);if(count<0)break
                    total+=count
                    if(total>MAX_PACKAGE_BYTES||total>expectedSize)throw IllegalStateException("Skin archive exceeds expected size.")
                    output.write(buffer,0,count)
                }
                if(total!=expectedSize)throw IllegalStateException("Skin archive size mismatch.")
            }}
            val messageDigest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    messageDigest.update(buffer, 0, count)
                }
            }
            val digest = messageDigest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if(!digest.equals(expectedHash,true))throw IllegalStateException("Skin package integrity verification failed.")
            val installed=archive.inputStream().use{SkinInstaller(context).install(it)}
            if(!installed.success)throw IllegalStateException(installed.error?:"Skin installation failed.")
            StoreActionResult(true,"Skin installed successfully.",installedSkinId=installed.skinId)
        }catch(error:Exception){StoreActionResult(false,error.message?:"Unable to install skin.")}
        finally{connection?.disconnect();archive.delete()}
    }

    private fun appVersion(context:Context):String =
        runCatching{context.packageManager.getPackageInfo(context.packageName,0).versionName?:""}.getOrDefault("")
}