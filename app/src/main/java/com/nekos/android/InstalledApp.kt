package com.nekos.android

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.graphics.drawable.Drawable
import android.widget.Toast
import android.view.View
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Keeps decoded launcher thumbnails within a predictable memory budget.
 * ImageBitmap dimensions are used to estimate the backing ARGB_8888 allocation.
 */
class AppIconBitmapCache(maxSizeKb: Int = 8 * 1024) {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb) {
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int =
            (bitmap.width * bitmap.height * 4 / 1024).coerceAtLeast(1)
    }

    operator fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val icon = try {
                info.loadIcon(pm)
            } catch (_: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
            InstalledApp(
                label = label,
                packageName = packageName,
                icon = icon
            )
        }
        .sortedWith(compareBy(appLabelComparator()) { it.label })
}

fun launchApp(
    context: Context,
    packageName: String,
    sourceView: View,
    sourceX: Int? = null,
    sourceY: Int? = null,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null
) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(
                context,
                "This app is no longer available to launch",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val width = (sourceWidth ?: 1).coerceAtLeast(1)
        val height = (sourceHeight ?: 1).coerceAtLeast(1)
        val launchOptions = ActivityOptions.makeScaleUpAnimation(
            sourceView,
            sourceX ?: (sourceView.width - width) / 2,
            sourceY ?: (sourceView.height - height) / 2,
            width,
            height
        )
        context.startActivity(intent, launchOptions.toBundle())
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Android could not open this app",
            Toast.LENGTH_SHORT
        ).show()
    } catch (error: SecurityException) {
        Toast.makeText(
            context,
            "NekOS is not allowed to open this app",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Android could not open this app's settings",
            Toast.LENGTH_SHORT
        ).show()
    } catch (error: SecurityException) {
        Toast.makeText(
            context,
            "NekOS is not allowed to open this app's settings",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun requestAppUninstall(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:$packageName")
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Android does not provide an uninstall screen for this app",
            Toast.LENGTH_LONG
        ).show()
    } catch (error: SecurityException) {
        Toast.makeText(
            context,
            "NekOS is not allowed to request this uninstall",
            Toast.LENGTH_LONG
        ).show()
    }
}
