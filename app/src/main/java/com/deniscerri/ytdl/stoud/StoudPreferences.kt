package com.deniscerri.ytdl.stoud

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object StoudPreferences {
    const val KEY_ENABLED = "stoud_enabled"
    const val KEY_SERVER_URL = "stoud_server_url"
    const val KEY_API_TOKEN = "stoud_api_token"
    const val KEY_QUALITY = "stoud_quality"
    const val KEY_CUSTOM_RTMP = "stoud_custom_rtmp"

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun getServerUrl(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return (prefs.getString(KEY_SERVER_URL, "") ?: "").trim()
    }

    fun setServerUrl(context: Context, url: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putString(KEY_SERVER_URL, url.trim()) }
    }

    fun getApiToken(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return (prefs.getString(KEY_API_TOKEN, "") ?: "").trim()
    }

    fun getQuality(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_QUALITY, "copy") ?: "copy"
    }

    fun getCustomRtmp(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return (prefs.getString(KEY_CUSTOM_RTMP, "") ?: "").trim()
    }

    fun isConfigured(context: Context): Boolean {
        return getServerUrl(context).isNotBlank()
    }
}
