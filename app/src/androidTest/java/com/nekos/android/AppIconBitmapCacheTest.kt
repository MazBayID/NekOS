package com.nekos.android

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconBitmapCacheTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun concurrentRequestsForTheSameAppReuseOneImageBitmap() = runBlocking {
        val app = createTestApp()
        val cache = AppIconBitmapCache(context)

        val results = coroutineScope {
            List(32) {
                async(Dispatchers.Default) {
                    cache.getOrLoad(app)
                }
            }.awaitAll()
        }

        val first = results.first()
        assertNotNull(first)
        assertTrue(results.all { it === first })
        assertSame(first, cache[app.iconCacheKey])
    }

    @Test
    fun trimmingDisablesLoadsUntilTheCacheResumes() = runBlocking {
        val app = createTestApp()
        val cache = AppIconBitmapCache(context)

        cache.trimMemory()
        org.junit.Assert.assertNull(cache.getOrLoad(app))

        cache.resumeAfterBackground()
        assertNotNull(cache.getOrLoad(app))
    }

    private fun createTestApp(): InstalledApp {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()
        val activity = launcherApps.getActivityList(context.packageName, user)
            .firstOrNull()
            ?: error("NekOS launcher activity was not found in the test profile")
        val userManager = context.getSystemService(UserManager::class.java)

        return InstalledApp(
            label = activity.label?.toString().orEmpty().ifBlank { context.packageName },
            packageName = context.packageName,
            launcherActivityInfo = activity,
            iconDensityDpi = context.resources.displayMetrics.densityDpi,
            user = user,
            componentName = activity.componentName,
            profileSerialNumber = userManager.getSerialNumberForUser(user)
        )
    }
}
