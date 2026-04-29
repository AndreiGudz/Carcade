package com.example.carcade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

class NotificationHelper(context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "mqtt_alerts"
    private var notificationId = 1000 // Уникальный ID для каждого уведомления

    init {
        val channel = NotificationChannel(
            channelId,
            "MQTT Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях с устройств"
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Показывает уведомление с информацией о сообщении.
     * @param context Контекст приложения
     * @param messageItem Данные сообщения
     * @param device Фильтр устройства (для заголовка уведомления)
     */
    fun showNotification(context: Context, messageItem: MqttMessageItem, device: String = "") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_mqtt_feed", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Формируем заголовок уведомления
        val title = if (device.isNotEmpty()) {
            "Новое сообщение от $device"
        } else {
            "Новое MQTT сообщение"
        }

        // Формируем текст уведомления с основной информацией
        val contentText = buildString {
            if (messageItem.time != null) {
                append("Время: ${messageItem.time}\n")
            }
            if (messageItem.lat != 0.0 && messageItem.lng != 0.0) {
                append("Координаты: ${"%.6f".format(messageItem.lat)}, ${"%.6f".format(messageItem.lng)}\n")
            }
            append(messageItem.body.take(100)) // Ограничиваем длину
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // Если есть координаты, добавляем кнопку "Открыть на карте"
        if (messageItem.lat != 0.0 && messageItem.lng != 0.0) {
            val mapIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = "http://maps.yandex.ru/?text=${messageItem.time ?: "Location"}&sll=${messageItem.lng},${messageItem.lat}&sspn=0.032932,0.018581&ol=geo&oll=${messageItem.lng},${messageItem.lat}&ll=${messageItem.lng},${messageItem.lat}&spn=0.067205,0.021163&z=15&l=map"
                data = uri.toUri()
            }

            val mapPendingIntent = PendingIntent.getActivity(
                context, 1, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.ic_dialog_map, "На карте", mapPendingIntent)
        }

        // Показываем уведомление с уникальным ID
        notificationManager.notify(notificationId++, builder.build())

        // Сбрасываем ID если достигли большого значения
        if (notificationId > 9999) {
            notificationId = 1000
        }
    }

    /**
     * Показывает простое уведомление с текстом (запасной вариант)
     */
    fun showSimpleNotification(context: Context, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MQTT уведомление")
            .setContentText(text.take(100))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }
}