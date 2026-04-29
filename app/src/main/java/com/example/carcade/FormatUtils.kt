package com.example.carcade

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Вспомогательные функции для форматирования
 */
object FormatUtils {
    @SuppressLint("ConstantLocale")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        return "${"%.6f".format(lat)}, ${"%.6f".format(lng)}"
    }
}