package com.nekos.android

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    /** Bumped when Android reports a package or profile change; always reloads the catalog. */
    private var appRefreshToken by mutableIntStateOf(0)
    /** Bumped on resume to refresh only the default-launcher role state. */
    private var resumeRefreshToken by mutableIntStateOf(0)
    /** Bumped when the Home button is pressed while NekOS is already open. */
    private var homeSignal by mutableIntStateOf(0)
    private var paypalResultToken by mutableIntStateOf(0)
    private var paypalResultMessage by mutableStateOf<String?>(null)
    private val paypalCheckout by lazy { NekOSPayPalCheckout(this) }
    /** Bumped when the launcher stops being visible so transient drawer UI can reset. */
    private var backgroundSignal by mutableIntStateOf(0)
    /** Package IDs whose launcher activities are temporarily unavailable. */
    private var temporarilyUnavailableAppIds by mutableStateOf<Set<String>>(emptySet())
    /** Suspended packages remain assigned even if the current catalog omits them. */
    private var suspendedAppIds by mutableStateOf<Set<String>>(emptySet())
    private var isForcedRefreshPending = false
    private var stoppedAtElapsedMillis = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshInstalledAppsRunnable = Runnable {
        isForcedRefreshPending = false
        appRefreshToken++
    }
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            updateTemporarilyUnavailableApps(listOf(packageName), user, unavailable = false)
            updateSuspendedApps(listOf(packageName), user, suspended = false)
            refreshInstalledApps()
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            updateTemporarilyUnavailableApps(listOf(packageName), user, unavailable = false)
            updateSuspendedApps(listOf(packageName), user, suspended = false)
            refreshInstalledApps()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshPackageLauncherAvailability(packageName, user)
            refreshInstalledApps()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            updateTemporarilyUnavailableApps(packageNames.toList(), user, unavailable = false)
            refreshInstalledApps()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            updateTemporarilyUnavailableApps(packageNames.toList(), user, unavailable = true)
            refreshInstalledApps()
        }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
            updateSuspendedApps(packageNames.toList(), user, suspended = true)
            refreshInstalledApps()
        }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
            updateSuspendedApps(packageNames.toList(), user, suspended = false)
            refreshInstalledApps()
        }
    }
    private var isPackageCallbackRegistered = false
    private var isProfileReceiverRegistered = false
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val action = intent?.action
            if (
                action == Intent.ACTION_MANAGED_PROFILE_REMOVED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    action == Intent.ACTION_PROFILE_REMOVED)
            ) {
                intent?.let(::profileUserFromIntent)?.let(::clearTemporarilyUnavailableAppsForProfile)
            }
            refreshInstalledApps()
        }
    }

    private fun updateTemporarilyUnavailableApps(
        packageNames: List<String>,
        user: UserHandle,
        unavailable: Boolean
    ) {
        val profileSerial = getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        if (profileSerial < 0L || packageNames.isEmpty()) return
        val appIds = packageNames.mapTo(mutableSetOf()) { launcherAppId(it, profileSerial) }
        val updatedAppIds = if (unavailable) {
            temporarilyUnavailableAppIds + appIds
        } else {
            temporarilyUnavailableAppIds - appIds
        }
        persistTemporarilyUnavailableAppIds(updatedAppIds)
    }

    private fun clearTemporarilyUnavailableAppsForProfile(user: UserHandle) {
        val userManager = getSystemService(UserManager::class.java)
        val removedProfileSerial = userManager.getSerialNumberForUser(user)
        // The removed UserHandle may already be invalid when its broadcast is delivered.
        // Reconcile all persisted markers by serial so stale entries are still discarded.
        val profileStillExists: (Long) -> Boolean = { serial ->
            serial != removedProfileSerial && userManager.getUserForSerialNumber(serial) != null
        }
        persistTemporarilyUnavailableAppIds(
            retainAppIdsForExistingProfiles(temporarilyUnavailableAppIds, profileStillExists)
        )
        persistSuspendedAppIds(
            retainAppIdsForExistingProfiles(suspendedAppIds, profileStillExists)
        )
    }

    private fun refreshPackageLauncherAvailability(packageName: String, user: UserHandle) {
        val hasLaunchableActivities = try {
            launcherApps.getActivityList(packageName, user).isNotEmpty()
        } catch (_: SecurityException) {
            return
        } catch (_: IllegalStateException) {
            return
        }
        val profileSerial = getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        if (profileSerial < 0L) return
        val appId = launcherAppId(packageName, profileSerial)
        lifecycleScope.launch {
            if (!hasLaunchableActivities && appId !in temporarilyUnavailableAppIds &&
                !hasSavedLauncherReference(appId)
            ) {
                return@launch
            }
            updateTemporarilyUnavailableApps(
                listOf(packageName),
                user,
                unavailable = !hasLaunchableActivities
            )
        }
    }

    private suspend fun hasSavedLauncherReference(appId: String): Boolean {
        if (appId in loadFavoritePackagesResult(this).packages) return true
        return TagStore.loadResult(this).tags.any { appId in it.appPackages }
    }

    private fun updateSuspendedApps(
        packageNames: List<String>,
        user: UserHandle,
        suspended: Boolean
    ) {
        val profileSerial = getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        if (profileSerial < 0L || packageNames.isEmpty()) return
        val appIds = packageNames.mapTo(mutableSetOf()) { launcherAppId(it, profileSerial) }
        val updatedAppIds = if (suspended) suspendedAppIds + appIds else suspendedAppIds - appIds
        persistSuspendedAppIds(updatedAppIds)
    }

    private fun persistSuspendedAppIds(appIds: Set<String>) {
        if (appIds == suspendedAppIds) return
        suspendedAppIds = appIds
        SuspendedAppStore.save(this, appIds)
    }

    private fun persistTemporarilyUnavailableAppIds(appIds: Set<String>) {
        if (appIds == temporarilyUnavailableAppIds) return
        temporarilyUnavailableAppIds = appIds
        TemporarilyUnavailableAppStore.save(this, appIds)
    }

    @Suppress("DEPRECATION")
    private fun profileUserFromIntent(intent: Intent): UserHandle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_USER) as? UserHandle
        }

    private fun refreshInstalledApps() {
        // Package updates can emit several callbacks in quick succession.
        // Coalesce them into one app-catalog refresh.
        isForcedRefreshPending = true
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        mainHandler.postDelayed(refreshInstalledAppsRunnable, 250L)
    }

    /**
     * Keep package/profile observers registered while the launcher Activity is stopped.
     * Package changes can happen while another app is in the foreground; unregistering
     * in onStop would leave a gap that a short return to Home cannot detect.
     */
    private fun registerAppChangeObservers() {
        if (!isPackageCallbackRegistered) {
            launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
            isPackageCallbackRegistered = true
        }
        if (!isProfileReceiverRegistered) {
            val profileFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    addAction(Intent.ACTION_PROFILE_ADDED)
                    addAction(Intent.ACTION_PROFILE_REMOVED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    addAction(Intent.ACTION_PROFILE_AVAILABLE)
                    addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                }
            }
            ContextCompat.registerReceiver(
                this,
                profileReceiver,
                profileFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isProfileReceiverRegistered = true
        }
    }

    private fun unregisterAppChangeObservers() {
        if (isPackageCallbackRegistered) {
            launcherApps.unregisterCallback(packageCallback)
            isPackageCallbackRegistered = false
        }
        if (isProfileReceiverRegistered) {
            unregisterReceiver(profileReceiver)
            isProfileReceiverRegistered = false
        }
    }

    override fun onStop() {
        Log.i(LIFECYCLE_TAG, "onStop pid=${Process.myPid()}")
        stoppedAtElapsedMillis = SystemClock.elapsedRealtime()
        backgroundSignal++
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        handlePayPalReturn(intent)
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        // Refresh the catalog only after a pending package event or a longer absence.
        // Ordinary returns only refresh the lightweight default-launcher role state.
        val stoppedAt = stoppedAtElapsedMillis
        stoppedAtElapsedMillis = 0L
        val wasAwayForLong = stoppedAt != 0L &&
            SystemClock.elapsedRealtime() - stoppedAt > LONG_ABSENCE_MILLIS
        if (isForcedRefreshPending || wasAwayForLong) {
            isForcedRefreshPending = false
            appRefreshToken++
        } else {
            resumeRefreshToken++
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePayPalReturn(intent)
        // Pressing Home while NekOS is open returns to a clean Home screen.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A restored launcher task should return directly to Home instead of replaying
        // the branded startup screen after Android recreates the Activity.
        val isRestoredActivity = savedInstanceState != null
        installSplashScreen()
        super.onCreate(savedInstanceState)
        paypalCheckout.restore(
            savedInstanceState?.getString(PAYPAL_STATE_KEY)
                ?: getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).getString(PAYPAL_STATE_KEY, null)
        )
        handlePayPalReturn(intent)
        logProcessDiagnostics(savedInstanceState != null)
        temporarilyUnavailableAppIds = TemporarilyUnavailableAppStore.load(this)
        suspendedAppIds = SuspendedAppStore.load(this)
        registerAppChangeObservers()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MaterialTheme(typography = NekOSTypography) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NekOSRoot(
                        refreshToken = appRefreshToken,
                        resumeToken = resumeRefreshToken,
                        paypalResultToken = paypalResultToken,
                        paypalResultMessage = paypalResultMessage,
                        homeSignal = homeSignal,
                        backgroundSignal = backgroundSignal,
                        temporarilyUnavailableAppIds = temporarilyUnavailableAppIds + suspendedAppIds,
                        skipStartupSplash = isRestoredActivity
                    )
                }
            }
        }
    }

    fun startPayPalCheckout(providerOrderId: String, fallbackCheckoutUrl: String) {
        paypalCheckout.start(this, providerOrderId) { result ->
            getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).edit()
                .putString(PAYPAL_STATE_KEY, paypalCheckout.instanceState)
                .apply()
            when (result) {
                is com.paypal.android.paypalpayments.PayPalPresentAuthChallengeResult.Success -> Unit
                is com.paypal.android.paypalpayments.PayPalPresentAuthChallengeResult.Failure -> {
                    val fallback = android.net.Uri.parse(fallbackCheckoutUrl)
                    if (fallback.scheme == "https" && fallback.host?.endsWith(".paypal.com") == true) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, fallback)) }
                    }
                    paypalResultMessage = result.error.errorDescription.ifBlank {
                        "PayPal checkout could not be started."
                    }
                    paypalResultToken++
                }
            }
        }
    }

    private fun handlePayPalReturn(sourceIntent: Intent?) {
        val data = sourceIntent?.data ?: return
        val isPayPalReturn = data.scheme == "nekos" && (
            data.host in setOf("paypal-finish", "paypal-cancel") ||
                (data.host == "x-callback-url" &&
                    data.path.orEmpty().startsWith("/paypal-sdk/paypal-checkout"))
            )
        if (!isPayPalReturn) return
        val result = runCatching { paypalCheckout.finishStart(sourceIntent) }.getOrNull() ?: return
        when (result) {
            is com.paypal.android.paypalpayments.PayPalFinishStartResult.Success -> {
                paypalResultMessage = "approved"
                getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).edit().remove(PAYPAL_STATE_KEY).apply()
                paypalResultToken++
                resumeRefreshToken++
            }
            is com.paypal.android.paypalpayments.PayPalFinishStartResult.Canceled -> {
                paypalResultMessage = "cancelled"
                getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).edit().remove(PAYPAL_STATE_KEY).apply()
                paypalResultToken++
                resumeRefreshToken++
            }
            is com.paypal.android.paypalpayments.PayPalFinishStartResult.Failure -> {
                paypalResultMessage = result.error.errorDescription
                getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).edit().remove(PAYPAL_STATE_KEY).apply()
                paypalResultToken++
                resumeRefreshToken++
            }
            com.paypal.android.paypalpayments.PayPalFinishStartResult.NoResult -> Unit
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = paypalCheckout.instanceState
        outState.putString(PAYPAL_STATE_KEY, state)
        getSharedPreferences(PAYPAL_PREFS, MODE_PRIVATE).edit()
            .putString(PAYPAL_STATE_KEY, state)
            .apply()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Log.i(
            LIFECYCLE_TAG,
            "onDestroy pid=${Process.myPid()} finishing=$isFinishing changingConfigurations=$isChangingConfigurations"
        )
        mainHandler.removeCallbacks(refreshInstalledAppsRunnable)
        unregisterAppChangeObservers()
        super.onDestroy()
    }

    private fun logProcessDiagnostics(isRestoredActivity: Boolean) {
        Log.i(LIFECYCLE_TAG, "onCreate pid=${Process.myPid()} restored=$isRestoredActivity")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val activityManager = getSystemService(ActivityManager::class.java)
            runCatching {
                activityManager.getHistoricalProcessExitReasons(packageName, 0, 5)
            }.onSuccess { exits ->
                exits.forEach { exit ->
                    Log.i(
                        LIFECYCLE_TAG,
                        "previousExit pid=${exit.pid} reason=${exit.reason} " +
                            "importance=${exit.importance} timestamp=${exit.timestamp} " +
                            "pssKb=${exit.pss} description=${exit.description}"
                    )
                }
            }.onFailure { error ->
                Log.w(LIFECYCLE_TAG, "Unable to read process exit history", error)
            }
        }
    }

    private companion object {
        const val LONG_ABSENCE_MILLIS = 30_000L
        const val LIFECYCLE_TAG = "NekOSLifecycle"
        const val PAYPAL_PREFS = "nekos_paypal_sdk"
        const val PAYPAL_STATE_KEY = "paypal_instance_state"
    }
}
