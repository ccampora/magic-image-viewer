package com.magicimageviewer.app.data

import android.content.Context
import androidx.preference.PreferenceManager

/** Thin wrapper around the default SharedPreferences used across the app. */
class PrefsRepository(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    var pcHost: String?
        get() = prefs.getString(KEY_PC_HOST, null)
        set(value) = prefs.edit().putString(KEY_PC_HOST, value).apply()

    // When enabled, browsing (swipe up/down) while sync is active re-sends the
    // newly shown photo automatically, instead of requiring a swipe right each time.
    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    companion object {
        const val KEY_PC_HOST = "pc_host"
        const val KEY_AUTO_SYNC = "auto_sync"
    }
}
