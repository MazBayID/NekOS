package com.nekos.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.WeakHashMap

/**
 * Owns the DataStore instances used by launcher collections.
 *
 * Each legacy SharedPreferences file is migrated independently so a problem in one
 * collection cannot prevent the other collections from being read.
 */
internal class LauncherCollectionsDataStore private constructor(
    context: Context,
    namespace: String
) {
    val favorites: DataStore<Preferences> = createStore(
        context = context,
        fileName = "nekos_favorites_$namespace",
        legacyPreferencesName = if (namespace.isEmpty()) "nekos_favorites" else "$namespace-nekos_favorites"
    )

    val tags: DataStore<Preferences> = createStore(
        context = context,
        fileName = "nekos_tags_$namespace",
        legacyPreferencesName = if (namespace.isEmpty()) "nekos_tags" else "$namespace-nekos_tags"
    )

    val favoriteSnapshots: DataStore<Preferences> = createStore(
        context = context,
        fileName = "nekos_favorite_snapshots_$namespace",
        legacyPreferencesName = if (namespace.isEmpty()) "nekos_favorite_snapshots" else "$namespace-nekos_favorite_snapshots"
    )

    private fun createStore(
        context: Context,
        fileName: String,
        legacyPreferencesName: String
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(
            SharedPreferencesMigration(context, legacyPreferencesName)
        ),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = {
            File(context.filesDir, "datastore/$fileName.preferences_pb").also {
                it.parentFile?.mkdirs()
            }
        }
    )

    companion object {
        private val instances = WeakHashMap<Context, LauncherCollectionsDataStore>()

        fun get(context: Context): LauncherCollectionsDataStore {
            val appContext = context.applicationContext
            return synchronized(instances) {
                instances.getOrPut(appContext) {
                    LauncherCollectionsDataStore(appContext, namespace = "")
                }
            }
        }

        /** Creates isolated stores for instrumentation tests. */
        internal fun createForTest(
            context: Context,
            namespace: String
        ): LauncherCollectionsDataStore {
            require(namespace.isNotBlank())
            return LauncherCollectionsDataStore(context, namespace)
        }
    }
}
