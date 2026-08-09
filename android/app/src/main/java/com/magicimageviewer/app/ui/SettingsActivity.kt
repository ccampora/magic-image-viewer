package com.magicimageviewer.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.magicimageviewer.app.R
import com.magicimageviewer.app.network.PcDiscovery

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var discovery: PcDiscovery

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            discovery = PcDiscovery(requireContext())

            findPreference<Preference>("rediscover")?.setOnPreferenceClickListener {
                it.summary = "Searching..."
                discovery.start { host, port ->
                    activity?.runOnUiThread {
                        findPreference<androidx.preference.EditTextPreference>("pc_host")?.text =
                            "$host:$port"
                        it.summary = "Found $host:$port"
                    }
                }
                true
            }
        }

        override fun onDestroy() {
            if (::discovery.isInitialized) discovery.stop()
            super.onDestroy()
        }
    }
}
