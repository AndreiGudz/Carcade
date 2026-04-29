package com.example.carcade

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsDataStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_settings", Context.MODE_PRIVATE)

    var deviceFilter: String
        get() = prefs.getString("device_filter", "") ?: ""
        set(value) = prefs.edit { putString("device_filter", value) }

    var notifyIntervalMinutes: Int
        get() = prefs.getInt("notify_interval_minutes", 5)
        set(value) = prefs.edit { putInt("notify_interval_minutes", value) }
}