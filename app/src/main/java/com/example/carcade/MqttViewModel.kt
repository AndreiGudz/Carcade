package com.example.carcade

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class MqttMessageItem(
    val body: String,
    val timestamp: Long,
    val time: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsDataStore(application)
    private val notificationHelper = NotificationHelper(application)

    private val _messages = MutableStateFlow<List<MqttMessageItem>>(emptyList())
    val messages: StateFlow<List<MqttMessageItem>> = _messages

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var lastMessageTime: Long = 0
    private var connectRequested = false

    init {
        startMqttIfNeeded()
    }

    fun startMqttIfNeeded() {
        if (MqttClientManager.isConnected || connectRequested) return
        connectRequested = true

        Thread {
            MqttClientManager.connect(
                onMessage = { payload -> handleIncomingMessage(payload) },
                onStatus = { state ->
                    _connectionState.value = state
                    if (state is ConnectionState.Error || state is ConnectionState.Disconnected) {
                        connectRequested = false
                    }
                }
            )
        }.start()
    }

    fun reconnect() {
        MqttClientManager.disconnect()
        connectRequested = false
        _connectionState.value = ConnectionState.Disconnected
        startMqttIfNeeded()
    }

    private fun handleIncomingMessage(payload: String) {
        val filter = settings.deviceFilter.trim()
        try {
            val json = JSONObject(payload)
            val device = json.optString("device", "")
            if (filter.isNotEmpty() && device != filter) return

            val time = if (json.has("time")) json.getString("time") else null
            val lat = if (json.has("lat")) json.getDouble("lat") else 0.0
            val lng = if (json.has("lng")) json.getDouble("lng") else 0.0

            val now = System.currentTimeMillis()
            val messageItem = MqttMessageItem(payload, now, time, lat, lng)

            val intervalMinutes = settings.notifyIntervalMinutes
            if (shouldShowNotification(now, intervalMinutes)) {
                notificationHelper.showNotification(
                    getApplication(),
                    messageItem,
                    if (filter.isNotEmpty()) device else ""
                )
            }
            lastMessageTime = now

            _messages.update { list ->
                val updated = list.toMutableList()
                updated.add(0, messageItem)
                if (updated.size > 10) updated.removeAt(updated.lastIndex)
                updated
            }
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val fallbackItem = MqttMessageItem(payload, now)
            _messages.update { list ->
                val updated = list.toMutableList()
                updated.add(0, fallbackItem)
                if (updated.size > 10) updated.removeAt(updated.lastIndex)
                updated
            }
            if (shouldShowNotification(now)) {
                notificationHelper.showSimpleNotification(getApplication(), payload)
            }
        }
    }

    private fun shouldShowNotification(now: Long, intervalMinutes: Int = settings.notifyIntervalMinutes): Boolean {
        if (lastMessageTime == 0L) return true
        if (intervalMinutes <= 0) return false
        val diffMinutes = (now - lastMessageTime) / 60000
        return diffMinutes >= intervalMinutes
    }

    override fun onCleared() {
        super.onCleared()
        MqttClientManager.disconnect()
        connectRequested = false
    }
}