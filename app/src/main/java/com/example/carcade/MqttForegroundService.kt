package com.example.carcade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MqttForegroundService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settings: SettingsDataStore
    private var lastMessageTime: Long = 0
    private var isServiceStarted = false
    private var currentConnectionState: ConnectionState = ConnectionState.Disconnected
    private var messageCount = 0

    companion object {
        const val TAG = "MqttForegroundService"
        const val CHANNEL_ID = "mqtt_foreground_service"
        const val NOTIFICATION_ID = 777

        // Actions для управления сервисом из UI
        const val ACTION_START = "com.example.carcade.action.START"
        const val ACTION_STOP = "com.example.carcade.action.STOP"
        const val ACTION_RECONNECT = "com.example.carcade.action.RECONNECT"
        const val ACTION_REQUEST_STATUS = "com.example.carcade.action.REQUEST_STATUS"

        // Broadcast для обновления UI
        const val BROADCAST_STATUS = "com.example.carcade.MQTT_STATUS"
        const val BROADCAST_MESSAGE = "com.example.carcade.MQTT_MESSAGE"
        const val EXTRA_CONNECTION_STATE = "connection_state"
        const val EXTRA_MESSAGE_BODY = "message_body"
        const val EXTRA_MESSAGE_TIME = "message_time"
        const val EXTRA_MESSAGE_LAT = "message_lat"
        const val EXTRA_MESSAGE_LNG = "message_lng"
        const val EXTRA_MESSAGE_TIMESTAMP = "message_timestamp"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_MESSAGE_COUNT = "message_count"

        fun startService(context: Context) {
            // Проверяем разрешения перед запуском
            if (!hasRequiredPermissions(context)) {
                Log.w(TAG, "Недостаточно разрешений для запуска сервиса")
                return
            }

            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска сервиса: ${e.message}", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_RECONNECT
            }
            context.startService(intent)
        }

        fun requestStatus(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_REQUEST_STATUS
            }
            context.startService(intent)
        }

        private fun hasRequiredPermissions(context: Context): Boolean {
            val hasInternet = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED

            return hasInternet
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        notificationHelper = NotificationHelper(this)
        settings = SettingsDataStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")

        try {
            when (intent?.action) {
                ACTION_START -> {
                    if (!isServiceStarted) {
                        startForegroundService()
                        startMqttConnection()
                        isServiceStarted = true
                    }
                }
                ACTION_STOP -> {
                    stopMqttConnection()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isServiceStarted = false
                }
                ACTION_RECONNECT -> {
                    updateStatusNotification("Переподключение...")
                    broadcastStatus("connecting")
                    stopMqttConnection()
                    startMqttConnection()
                }
                ACTION_REQUEST_STATUS -> {
                    // Отправляем текущий статус и количество сообщений
                    broadcastCurrentStatus()
                }
                else -> {
                    // Запуск без явного action (например, при перезагрузке)
                    if (!isServiceStarted) {
                        startForegroundService()
                        startMqttConnection()
                        isServiceStarted = true
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}. Проверьте разрешения в манифесте.")
            try {
                if (!isServiceStarted) {
                    startMqttConnection()
                    isServiceStarted = true
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Не удалось запустить MQTT соединение: ${ex.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onStartCommand: ${e.message}", e)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopMqttConnection()
        isServiceStarted = false
    }

    private fun startForegroundService() {
        try {
            val notification = createStatusNotification("Запуск...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "Foreground service запущен успешно")
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка запуска foreground service: ${e.message}")
            throw e
        }
    }

    private fun startMqttConnection() {
        Thread {
            MqttClientManager.connect(
                onMessage = { payload -> handleMessage(payload) },
                onStatus = { state ->
                    currentConnectionState = state
                    when (state) {
                        is ConnectionState.Connected -> {
                            updateStatusNotification("Подключено • Сообщений: $messageCount")
                            broadcastStatus("connected")
                        }
                        is ConnectionState.Connecting -> {
                            updateStatusNotification("Подключение...")
                            broadcastStatus("connecting")
                        }
                        is ConnectionState.Disconnected -> {
                            updateStatusNotification("Отключено • Сообщений: $messageCount")
                            broadcastStatus("disconnected")
                        }
                        is ConnectionState.Error -> {
                            updateStatusNotification("Ошибка: ${state.message}")
                            broadcastStatus("error", state.message)
                        }
                    }
                }
            )
        }.start()
    }

    private fun stopMqttConnection() {
        MqttClientManager.disconnect()
    }

    private fun handleMessage(payload: String) {
        val filter = settings.deviceFilter.trim()

        try {
            // Парсим JSON и проверяем наличие ключа device
            val json = JSONObject(payload)
            val device = json.optString("device", "")

            // Если нет ключа device или он не соответствует фильтру – игнорируем
            if (device.isEmpty()) {
                Log.d(TAG, "Сообщение без ключа device – игнорируем")
                return
            }

            if (filter.isNotEmpty() && device != filter) {
                Log.d(TAG, "Сообщение от $device не соответствует фильтру $filter – игнорируем")
                return
            }

            // Извлекаем опциональные поля
            val time = if (json.has("time")) json.getString("time") else null
            val lat = if (json.has("lat")) json.getDouble("lat") else 0.0
            val lng = if (json.has("lng")) json.getDouble("lng") else 0.0

            val now = System.currentTimeMillis()
            val messageId = (now % 100000).toString()
            messageCount++

            Log.d(TAG, "Обработано сообщение #$messageCount от $device")

            // Проверяем интервал для уведомлений
            val intervalMinutes = settings.notifyIntervalMinutes
            if (shouldShowNotification(now, intervalMinutes)) {
                val messageItem = MqttMessageItem(
                    id = messageId,
                    body = payload,
                    timestamp = now,
                    time = time,
                    lat = lat,
                    lng = lng
                )
                notificationHelper.showNotification(
                    this,
                    messageItem,
                    if (filter.isNotEmpty()) device else "",
                    messageIndex = messageId.toIntOrNull() ?: 1000
                )
            }
            lastMessageTime = now

            // Обновляем уведомление сервиса с количеством сообщений
            updateStatusNotification("Подключено • Сообщений: $messageCount")

            // Отправляем broadcast в UI
            broadcastMessage(messageId, payload, time, lat, lng, now, messageCount)

        } catch (e: Exception) {
            // Невалидный JSON – игнорируем
            Log.d(TAG, "Ошибка парсинга JSON или отсутствует ключ device: ${e.message}")
        }
    }

    private fun shouldShowNotification(now: Long, intervalMinutes: Int): Boolean {
        if (lastMessageTime == 0L) return true
        if (intervalMinutes == 0) return true
        if (intervalMinutes < 0) return false
        val diffMinutes = (now - lastMessageTime) / 60000
        return diffMinutes >= intervalMinutes
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MQTT Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Постоянное уведомление о статусе MQTT"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createStatusNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_mqtt_feed", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MQTT Сервис")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateStatusNotification(status: String) {
        try {
            val notification = createStatusNotification(status)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления уведомления: ${e.message}")
        }
    }

    private fun broadcastStatus(state: String, errorMessage: String? = null) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_CONNECTION_STATE, state)
            if (errorMessage != null) {
                putExtra("error_message", errorMessage)
            }
            putExtra(EXTRA_MESSAGE_COUNT, messageCount)
        }
        sendBroadcast(intent)
    }

    private fun broadcastCurrentStatus() {
        val state = when (currentConnectionState) {
            is ConnectionState.Connected -> "connected"
            is ConnectionState.Connecting -> "connecting"
            is ConnectionState.Disconnected -> "disconnected"
            is ConnectionState.Error -> "error"
        }
        val errorMsg = if (currentConnectionState is ConnectionState.Error) {
            (currentConnectionState as ConnectionState.Error).message
        } else null

        broadcastStatus(state, errorMsg)
    }

    private fun broadcastMessage(
        id: String,
        body: String,
        time: String?,
        lat: Double,
        lng: Double,
        timestamp: Long,
        count: Int = messageCount
    ) {
        val intent = Intent(BROADCAST_MESSAGE).apply {
            putExtra(EXTRA_MESSAGE_ID, id)
            putExtra(EXTRA_MESSAGE_BODY, body)
            putExtra(EXTRA_MESSAGE_TIME, time)
            putExtra(EXTRA_MESSAGE_LAT, lat)
            putExtra(EXTRA_MESSAGE_LNG, lng)
            putExtra(EXTRA_MESSAGE_TIMESTAMP, timestamp)
            putExtra(EXTRA_MESSAGE_COUNT, count)
        }
        sendBroadcast(intent)
    }
}