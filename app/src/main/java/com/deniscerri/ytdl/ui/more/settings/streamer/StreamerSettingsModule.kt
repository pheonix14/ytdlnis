package com.deniscerri.ytdl.ui.more.settings.streamer

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.stoud.StoudClient
import com.deniscerri.ytdl.stoud.StoudDialogHelper
import com.deniscerri.ytdl.stoud.StoudPreferences
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object StreamerSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        when (pref.key) {
            "stoud_enabled" -> {
                (pref as SwitchPreferenceCompat).apply {
                    setOnPreferenceChangeListener { _, _ ->
                        host.refreshUI()
                        true
                    }
                }
            }
            "stoud_server_url" -> {
                (pref as EditTextPreference).apply {
                    val current = text?.trim().orEmpty()
                    summary = if (current.isNotBlank()) current else context.getString(R.string.stoud_server_url_summary)
                    setOnPreferenceChangeListener { _, newValue ->
                        val url = (newValue as? String)?.trim().orEmpty()
                        summary = if (url.isNotBlank()) url else context.getString(R.string.stoud_server_url_summary)
                        host.refreshUI()
                        true
                    }
                }
            }
            "stoud_api_token" -> {
                (pref as EditTextPreference).apply {
                    val current = text?.trim().orEmpty()
                    summary = if (current.isNotBlank()) "••••••••" else context.getString(R.string.stoud_api_token_summary)
                    setOnPreferenceChangeListener { _, newValue ->
                        val token = (newValue as? String)?.trim().orEmpty()
                        summary = if (token.isNotBlank()) "••••••••" else context.getString(R.string.stoud_api_token_summary)
                        host.refreshUI()
                        true
                    }
                }
            }
            "stoud_quality" -> {
                (pref as ListPreference).apply {
                    val s = context.getString(R.string.stoud_quality_summary)
                    summary = if (!value.isNullOrBlank() && entryValues.indexOf(value) >= 0) {
                        "$s\n[${entries[entryValues.indexOf(value)]}]"
                    } else s
                    setOnPreferenceChangeListener { _, newValue ->
                        val idx = entryValues.indexOf(newValue as? String)
                        summary = if (idx >= 0) "$s\n[${entries[idx]}]" else s
                        host.refreshUI()
                        true
                    }
                }
            }
            "stoud_custom_rtmp" -> {
                (pref as EditTextPreference).apply {
                    val current = text?.trim().orEmpty()
                    summary = if (current.isNotBlank()) current else context.getString(R.string.stoud_custom_rtmp_summary)
                    setOnPreferenceChangeListener { _, newValue ->
                        val rtmp = (newValue as? String)?.trim().orEmpty()
                        summary = if (rtmp.isNotBlank()) rtmp else context.getString(R.string.stoud_custom_rtmp_summary)
                        host.refreshUI()
                        true
                    }
                }
            }
            "stoud_test_connection" -> {
                pref.setOnPreferenceClickListener {
                    val serverUrl = StoudPreferences.getServerUrl(context)
                    if (serverUrl.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.stoud_not_configured), Toast.LENGTH_SHORT).show()
                        return@setOnPreferenceClickListener true
                    }

                    Toast.makeText(context, context.getString(R.string.stoud_connecting), Toast.LENGTH_SHORT).show()
                    val client = StoudClient(context)
                    host.hostLifecycleOwner.lifecycleScope.launch {
                        val startTime = System.currentTimeMillis()
                        val result = client.getStatus()
                        val elapsed = System.currentTimeMillis() - startTime
                        withContext(Dispatchers.Main) {
                            result.onSuccess { status ->
                                val mode = if (status.isStreaming) "LIVE (Playing: ${status.currentVideo?.title ?: "Stream"})" else "IDLE"
                                MaterialAlertDialogBuilder(context)
                                    .setTitle("Connection Successful! (${elapsed}ms)")
                                    .setMessage("Server: $serverUrl\nStatus: $mode\nQueued items: ${status.queue.size}\nBreak mode: ${status.breakMode}")
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }.onFailure { err ->
                                MaterialAlertDialogBuilder(context)
                                    .setTitle("Connection Failed")
                                    .setMessage("Could not connect to $serverUrl:\n${err.localizedMessage}")
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                    true
                }
            }
            "stoud_open_monitor" -> {
                pref.setOnPreferenceClickListener {
                    val activity = host.getHostActivity()
                    StoudDialogHelper.openMonitor(activity)
                    true
                }
            }
        }
    }
}
