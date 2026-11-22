package com.nekos.android

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.content.pm.LauncherActivityInfo
import android.view.View
import android.widget.Toast
import java.io.File

data class LauncherAppScanResult(
    val apps: List<InstalledApp>,
    /** Profiles temporarily stopped or locked; keep their saved launcher references intact. */
    val unavailableProfileSerialNumbers: Set<Long>
)

/** A launchable package scoped to the Android profile where it is installed. */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val launcherActivityInfo: LauncherActivityInfo,
    val iconDensityDpi: Int,
    val iconVersion: Long = 0L,
    val user: UserHandle,
    val componentName: ComponentName,
    val profileSerialNumber: Long
) {
    /** Stable across process restarts and distinct for identical packages in separate profiles. */
    val id: String
        get() = launcherAppId(packageName, profileSerialNumber)

    /** Changes when the installed package is updated, invalidating its previous disk icon. */
    val iconCacheKey: String
        get() = "$id::$iconVersion::$iconDensityDpi::${componentName.flattenToString()}"

    val isInCurrentProfile: Boolean
        get() = user == Process.myUserHandle()
}

internal const val LAUNCHER_APP_ID_SEPARATOR = "::"

internal fun launcherAppId(packageName: String, profileSerialNumber: Long): String =
    "$profileSerialNumber$LAUNCHER_APP_ID_SEPARATOR$packageName"

fun loadLaunchableApps(context: Context): LauncherAppScanResult {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    val densityDpi = context.resources.displayMetrics.densityDpi
    val currentUser = Process.myUserHandle()

    val unavailableProfileSerialNumbers = mutableSetOf<Long>()
    val apps = launcherApps.profiles
        .flatMap { profile ->
            val profileSerial = userManager.getSerialNumberForUser(profile)
            if (profileSerial < 0L) return@flatMap emptyList()
            if (
                !userManager.isUserRunning(profile) ||
                !userManager.isUserUnlocked(profile) ||
                userManager.isQuietModeEnabled(profile)
            ) {
                unavailableProfileSerialNumbers += profileSerial
                return@flatMap emptyList()
            }

            val activities = try {
                launcherApps.getActivityList(null, profile)
            } catch (_: SecurityException) {
                unavailableProfileSerialNumbers += profileSerial
                return@flatMap emptyList()
            } catch (_: IllegalStateException) {
                unavailableProfileSerialNumbers += profileSerial
                return@flatMap emptyList()
            }

            activities
                // Keep NekOS out of its own drawer, but do not hide a separate
                // installation of the same package in another Android profile.
                .filterNot { activity ->
                    profile == currentUser &&
                        activity.applicationInfo.packageName == context.packageName
                }
                .distinctBy { it.applicationInfo.packageName }
                .map { activity ->
                    InstalledApp(
                        label = activity.label?.toString().orEmpty()
                            .ifBlank { activity.applicationInfo.packageName },
                        packageName = activity.applicationInfo.packageName,
                        launcherActivityInfo = activity,
                        iconDensityDpi = densityDpi,
                        iconVersion = File(activity.applicationInfo.sourceDir).lastModified(),
                        user = profile,
                        componentName = activity.componentName,
                        profileSerialNumber = profileSerial
                    )
                }
        }
        .distinctBy { it.id }
        .sortedWith(compareBy(appLabelComparator()) { it.label })

    return LauncherAppScanResult(
        apps = apps,
        unavailableProfileSerialNumbers = unavailableProfileSerialNumbers
    )
}

fun launchApp(
    context: Context,
    app: InstalledApp,
    sourceView: View,
    sourceX: Int? = null,
    sourceY: Int? = null,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null
) {
    try {
        val width = (sourceWidth ?: 1).coerceAtLeast(1)
        val height = (sourceHeight ?: 1).coerceAtLeast(1)
        val launchOptions = ActivityOptions.makeScaleUpAnimation(
            sourceView,
            sourceX ?: (sourceView.width - width) / 2,
            sourceY ?: (sourceView.height - height) / 2,
            width,
            height
        )
        context.getSystemService(LauncherApps::class.java).startMainActivity(
            app.componentName,
            app.user,
            null,
            launchOptions.toBundle()
        )
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "This app is no longer available to launch",
            Toast.LENGTH_SHORT
        ).show()
    } catch (error: SecurityException) {
        Toast.makeText(
            context,
            "NekOS is not allowed to open this app",
            Toast.LENGTH_SHORT
        ).show()
    } catch (error: IllegalStateException) {
        Toast.makeText(
            context,
            "This app's profile is currently unavailable",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun openAppInfo(context: Context, app: InstalledApp) {
    try {
        context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(
            app.componentName,
            app.user,
            null,
            null
        )
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
    } catch (error: IllegalStateException) {
        Toast.makeText(
            context,
            "This app's profile is currently unavailable",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun requestAppUninstall(context: Context, app: InstalledApp) {
    if (app.user != Process.myUserHandle()) {
        // The uninstall confirmation intent is scoped to the calling profile.
        // Open the profile-aware app details page instead for managed-profile apps.
        openAppInfo(context, app)
        return
    }

    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:${app.packageName}")
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

/**
 * True when two catalogs list the same launchable entries in the same order.
 * Used to keep the current list, and its decoded icon cache, after a refresh that
 * found nothing new.
 */
internal fun hasSameAppCatalog(current: List<InstalledApp>, refreshed: List<InstalledApp>): Boolean =
    current.size == refreshed.size && current.indices.all { index ->
        val old = current[index]
        val new = refreshed[index]
        old.id == new.id &&
            old.label == new.label &&
            old.componentName == new.componentName &&
            old.iconVersion == new.iconVersion
    }
