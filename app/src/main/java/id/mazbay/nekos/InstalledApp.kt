package com.nekos.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

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
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
