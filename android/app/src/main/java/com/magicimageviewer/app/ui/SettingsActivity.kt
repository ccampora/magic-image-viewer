package com.magicimageviewer.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.magicimageviewer.app.R
import com.magicimageviewer.app.data.PrefsRepository
import com.magicimageviewer.app.network.DiscoveredServer
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
        private lateinit var prefs: PrefsRepository

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            discovery = PcDiscovery(requireContext())
            prefs = PrefsRepository(requireContext())

            updateChoosePcSummary()

            findPreference<Preference>("choose_pc")?.setOnPreferenceClickListener {
                showPickerDialog()
                true
            }

            findPreference<EditTextPreference>("pc_host")?.setOnPreferenceChangeListener { _, _ ->
                view?.post { updateChoosePcSummary() }
                true
            }
        }

        private fun updateChoosePcSummary() {
            val choosePc = findPreference<Preference>("choose_pc") ?: return
            val host = prefs.pcHost
            choosePc.summary = if (host.isNullOrBlank()) {
                getString(R.string.pref_choose_pc_summary_none)
            } else {
                getString(R.string.pref_choose_pc_summary_set, host)
            }
        }

        private fun showPickerDialog() {
            val context = requireContext()
            val servers = mutableListOf<DiscoveredServer>()

            val adapter = object : ArrayAdapter<DiscoveredServer>(
                context, android.R.layout.simple_list_item_2, android.R.id.text1, servers
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = super.getView(position, convertView, parent)
                    val server = servers[position]
                    row.findViewById<TextView>(android.R.id.text1).text = server.name
                    row.findViewById<TextView>(android.R.id.text2).text =
                        "${server.host}:${server.port}"
                    return row
                }
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle(R.string.dialog_choose_pc_title)
                .setAdapter(adapter) { _, position ->
                    val server = servers[position]
                    prefs.pcHost = "${server.host}:${server.port}"
                    findPreference<EditTextPreference>("pc_host")?.text =
                        "${server.host}:${server.port}"
                    updateChoosePcSummary()
                }
                .setNeutralButton(R.string.action_enter_manually, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { discovery.stop() }
                .create()

            dialog.show()

            discovery.start { server ->
                activity?.runOnUiThread {
                    val exists = servers.any { it.host == server.host && it.port == server.port }
                    if (!exists) {
                        servers.add(server)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onDestroy() {
            if (::discovery.isInitialized) discovery.stop()
            super.onDestroy()
        }
    }
}
