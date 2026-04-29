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
    private val groupKey = "mqtt_messages_group"
    private var notificationId = 1000

    init {
        // Основной канал уведомлений
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

        // Канал для сводного уведомления
        val summaryChannel = NotificationChannel(
            "mqtt_summary",
            "MQTT Summary",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Сводка уведомлений MQTT"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(summaryChannel)
    }

    fun showNotification(
        context: Context,
        messageItem: MqttMessageItem,
        device: String = "",
        messageIndex: Int = -1
    ) {
        val messageId = if (messageIndex >= 0) messageIndex else notificationId

        // Основной Intent для открытия приложения с переходом на конкретное сообщение
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_mqtt_feed", true)
            putExtra("highlight_message_id", messageId.toString())
            putExtra("message_time", messageItem.time)
            putExtra("message_lat", messageItem.lat)
            putExtra("message_lng", messageItem.lng)
            putExtra("message_body", messageItem.body.take(200))
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            messageId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Формируем заголовок
        val title = if (device.isNotEmpty()) {
            "Сообщение от $device"
        } else {
            "Новое MQTT сообщение"
        }

        // Формируем текст уведомления
        val contentText = buildString {
            if (messageItem.time != null) {
                append("Время: ${messageItem.time}\n")
            }
            if (messageItem.lat != 0.0 && messageItem.lng != 0.0) {
                append("📍 ${"%.6f".format(messageItem.lat)}, ${"%.6f".format(messageItem.lng)}\n")
            }
            append(messageItem.body.take(150))
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
            .setGroup(groupKey)  // Группировка уведомлений
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

        // Если есть координаты – кнопка открытия на карте
        if (messageItem.lat != 0.0 && messageItem.lng != 0.0) {
            // Яндекс.Карты
            val yandexIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = "http://maps.yandex.ru/?text=${messageItem.time ?: "Location"}" +
                        "&sll=${messageItem.lng},${messageItem.lat}" +
                        "&sspn=0.032932,0.018581" +
                        "&ol=geo&oll=${messageItem.lng},${messageItem.lat}" +
                        "&ll=${messageItem.lng},${messageItem.lat}" +
                        "&spn=0.067205,0.021163&z=15&l=map"
                data = uri.toUri()
            }
            val yandexPendingIntent = PendingIntent.getActivity(
                context, messageId + 10000, yandexIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_dialog_map, "Яндекс.Карты", yandexPendingIntent)

            // Google Maps
            val googleIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = "https://www.google.com/maps?ll=${messageItem.lat},${messageItem.lng}"
                data = uri.toUri()
            }
            val googlePendingIntent = PendingIntent.getActivity(
                context, messageId + 20000, googleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_dialog_map, "Google Maps", googlePendingIntent)
        }

        notificationManager.notify(messageId, builder.build())

        // Показываем сводное уведомление для группы
        showSummaryNotification(context, device)
    }

    private fun showSummaryNotification(context: Context, lastDevice: String) {
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_mqtt_feed", true)
        }

        val summaryPendingIntent = PendingIntent.getActivity(
            context, 99999, summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, "mqtt_summary")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новые MQTT сообщения")
            .setContentText("Последнее от: $lastDevice")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(summaryPendingIntent)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(99999, summaryNotification)
    }

    fun showSimpleNotification(context: Context, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_mqtt_feed", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MQTT уведомление")
            .setContentText(text.take(100))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .build()

        notificationManager.notify(notificationId++, notification)
    }
}