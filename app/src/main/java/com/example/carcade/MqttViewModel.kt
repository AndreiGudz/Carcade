package com.example.carcade

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class MqttMessageItem(
    val id: String = "",
    val body: String,
    val timestamp: Long,
    val time: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MqttViewModel"
    private val settings = SettingsDataStore(application)

    private val _messages = MutableStateFlow<List<MqttMessageItem>>(emptyList())
    val messages: StateFlow<List<MqttMessageItem>> = _messages

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messageCount = MutableStateFlow(0)
    val messageCount: StateFlow<Int> = _messageCount

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            Log.d(TAG, "Получен broadcast: ${intent.action}")

            when (intent.action) {
                MqttForegroundService.BROADCAST_STATUS -> {
                    val state = intent.getStringExtra(MqttForegroundService.EXTRA_CONNECTION_STATE) ?: return
                    Log.d(TAG, "Статус соединения: $state")

                    _connectionState.value = when (state) {
                        "connected" -> ConnectionState.Connected
                        "connecting" -> ConnectionState.Connecting
                        "disconnected" -> ConnectionState.Disconnected
                        "error" -> {
                            val errorMsg = intent.getStringExtra("error_message") ?: "Неизвестная ошибка"
                            ConnectionState.Error(errorMsg)
                        }
                        else -> ConnectionState.Disconnected
                    }
                }
                MqttForegroundService.BROADCAST_MESSAGE -> {
                    val id = intent.getStringExtra(MqttForegroundService.EXTRA_MESSAGE_ID) ?: ""
                    val body = intent.getStringExtra(MqttForegroundService.EXTRA_MESSAGE_BODY) ?: return
                    val time = intent.getStringExtra(MqttForegroundService.EXTRA_MESSAGE_TIME)
                    val lat = intent.getDoubleExtra(MqttForegroundService.EXTRA_MESSAGE_LAT, 0.0)
                    val lng = intent.getDoubleExtra(MqttForegroundService.EXTRA_MESSAGE_LNG, 0.0)
                    val timestamp = intent.getLongExtra(MqttForegroundService.EXTRA_MESSAGE_TIMESTAMP, System.currentTimeMillis())

                    Log.d(TAG, "Получено сообщение: id=$id, time=$time, lat=$lat, lng=$lng")

                    val item = MqttMessageItem(
                        id = id,
                        body = body,
                        timestamp = timestamp,
                        time = time,
                        lat = lat,
                        lng = lng
                    )

                    _messages.update { list ->
                        val updated = list.toMutableList()
                        updated.add(0, item)
                        if (updated.size > 10) updated.removeAt(updated.lastIndex)
                        updated
                    }

                    _messageCount.update { it + 1 }
                }
            }
        }
    }

    init {
        Log.d(TAG, "ViewModel init, регистрируем receiver")

        val filter = IntentFilter().apply {
            addAction(MqttForegroundService.BROADCAST_STATUS)
            addAction(MqttForegroundService.BROADCAST_MESSAGE)
        }

        // Используем правильный метод регистрации в зависимости от версии Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                messageReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                getApplication<Application>(),
                messageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Запрашиваем текущий статус соединения
        MqttForegroundService.requestStatus(getApplication())

        // Запускаем сервис, если ещё не запущен
        MqttForegroundService.startService(getApplication())
    }

    fun reconnect() {
        MqttForegroundService.reconnect(getApplication())
    }

    fun deleteMessage(messageId: String) {
        _messages.update { list ->
            list.filter { it.id != messageId }
        }
    }

    fun clearAllMessages() {
        _messages.value = emptyList()
    }

    fun resetMessageCount() {
        _messageCount.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared, отписываем receiver")
        try {
            getApplication<Application>().unregisterReceiver(messageReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отписке receiver: ${e.message}")
        }
        // Не останавливаем сервис – он должен работать 24/7
    }
}