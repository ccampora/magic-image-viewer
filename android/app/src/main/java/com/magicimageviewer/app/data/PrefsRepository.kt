package com.magicimageviewer.app.data

import android.content.Context
import androidx.preference.PreferenceManager

/** Thin wrapper around the default SharedPreferences used across the app. */
class PrefsRepository(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    var pcHost: String?
        get() = prefs.getString(KEY_PC_HOST, null)
        set(value) = prefs.edit().putString(KEY_PC_HOST, value).apply()

    companion object {
        const val KEY_PC_HOST = "pc_host"
    }
}
