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

    private var lastMessageTime: Long = 0
    private var connectRequested = false

    init {
        startMqttIfNeeded()
    }

    fun startMqttIfNeeded() {
        if (MqttClientManager.isConnected() || connectRequested) return
        connectRequested = true

        // Упрощено для MVP: запускаем подключение в отдельном потоке.
        // В будущем заменить на корутины и Lifecycle-aware управление.
        Thread {
            try {
                MqttClientManager.connect(
                    onMessage = { payload -> handleIncomingMessage(payload) },
                    onError = { error ->
                        error.printStackTrace()
                        connectRequested = false // Разрешаем повторное подключение при ошибке
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                connectRequested = false
            }
        }.start()
    }

    // Ручное переподключение (кнопка в настройках)
    fun reconnect() {
        MqttClientManager.disconnect()
        connectRequested = false
        startMqttIfNeeded()
    }

    private fun handleIncomingMessage(payload: String) {
        val filter = settings.deviceFilter.trim()
        try {
            val json = JSONObject(payload)
            val device = json.optString("device", "")

            // Если фильтр не пуст и устройство не совпадает — пропускаем сообщение
            if (filter.isNotEmpty() && device != filter) return

            val time = if (json.has("time")) json.getString("time") else null
            val lat = if (json.has("lat")) json.getDouble("lat") else 0.0
            val lng = if (json.has("lng")) json.getDouble("lng") else 0.0

            val now = System.currentTimeMillis()
            val messageItem = MqttMessageItem(payload, now, time, lat, lng)

            // Проверяем интервал для уведомлений
            val intervalMinutes = settings.notifyIntervalMinutes
            if (shouldShowNotification(now, intervalMinutes)) {
                // Показываем уведомление с полными данными
                notificationHelper.showNotification(
                    getApplication(),
                    messageItem,
                    if (filter.isNotEmpty()) device else ""
                )
            }
            lastMessageTime = now

            // Добавляем сообщение в список
            _messages.update { list ->
                val updated = list.toMutableList()
                updated.add(0, messageItem)
                if (updated.size > 10) updated.removeAt(updated.lastIndex)
                updated
            }
        } catch (e: Exception) {
            // Невалидный JSON или ошибка парсинга — всё равно покажем сообщение, но без фильтрации
            val now = System.currentTimeMillis()
            val fallbackItem = MqttMessageItem(payload, now)

            _messages.update { list ->
                val updated = list.toMutableList()
                updated.add(0, fallbackItem)
                if (updated.size > 10) updated.removeAt(updated.lastIndex)
                updated
            }

            // Показываем уведомление даже для невалидных сообщений, если интервал соблюдён
            if (shouldShowNotification(now)) {
                notificationHelper.showSimpleNotification(getApplication(), payload)
            }
        }
    }

    /**
     * Проверяет, нужно ли показывать уведомление на основе интервала
     */
    private fun shouldShowNotification(now: Long, intervalMinutes: Int = settings.notifyIntervalMinutes): Boolean {
        if (lastMessageTime == 0L) return true // Первое сообщение всегда показывает уведомление
        if (intervalMinutes <= 0) return false // Уведомления отключены

        val diffMinutes = (now - lastMessageTime) / 60000
        return diffMinutes >= intervalMinutes
    }

    override fun onCleared() {
        super.onCleared()
        MqttClientManager.disconnect()
        connectRequested = false
    }
}