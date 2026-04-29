package com.example.carcade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "mqtt_alerts"

    init {
        val channel = NotificationChannel(
            channelId,
            "MQTT Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Уведомления о новых MQTT сообщениях"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MQTT уведомление")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Упрощено для MVP: уведомление показывается только когда приложение на экране.
        // В будущем заменить на Foreground Service для фонового приёма.
        notificationManager.notify(1, notification)
    }
}