package com.nekos.android

import android.content.Context

internal object TemporarilyUnavailableAppStore {
    private const val PREFERENCES_NAME = "nekos_temporary_app_availability"
    private const val KEY_APP_IDS = "temporarily_unavailable_app_ids"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_APP_IDS, emptySet())
            ?.toSet()
            .orEmpty()

    fun save(context: Context, appIds: Set<String>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_APP_IDS, appIds.toSet())
            .apply()
    }
}

internal object SuspendedAppStore {
    private const val PREFERENCES_NAME = "nekos_suspended_apps"
    private const val KEY_APP_IDS = "suspended_app_ids"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_APP_IDS, emptySet())
            ?.toSet()
            .orEmpty()

    fun save(context: Context, appIds: Set<String>) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_APP_IDS, appIds.toSet())
            .apply()
    }
}
