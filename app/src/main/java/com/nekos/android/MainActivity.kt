package com.nekos.android

import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private var appRefreshToken by mutableIntStateOf(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshInstalledAppsRunnable = Runnable { appRefreshToken++ }
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshInstalledApps()
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshInstalledApps()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshInstalledApps()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            refreshInstalledApps()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            refreshInstalledApps()
        }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
            refreshInstalledApps()
        }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
            refreshInstalledApps()
        }
    }
    private var isPackageCallbackRegistered = false

    private fun refreshInstalledApps() {
        // Package updates can emit several callbacks in quick succession.
        // Coalesce them into one app-catalog refresh.
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        mainHandler.postDelayed(refreshInstalledAppsRunnable, 250L)
    }

    override fun onStart() {
        super.onStart()
        if (!isPackageCallbackRegistered) {
            launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
            isPackageCallbackRegistered = true
        }
    }

    override fun onStop() {
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        if (isPackageCallbackRegistered) {
            launcherApps.unregisterCallback(packageCallback)
            isPackageCallbackRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        appRefreshToken++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NekOSRoot(refreshToken = appRefreshToken)
                }
            }
        }
    }
}
