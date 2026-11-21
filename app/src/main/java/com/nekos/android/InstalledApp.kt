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

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            InstalledApp(
                label = info.loadLabel(pm).toString(),
                packageName = packageName,
                icon = info.loadIcon(pm)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(context: Context, packageName: String) {
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
        val launchOptions = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.app_open_enter,
            R.anim.app_open_exit
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
