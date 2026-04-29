package com.example.carcade

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: SettingsDataStore, onReconnect: () -> Unit) {
    var deviceFilter by remember { mutableStateOf(settings.deviceFilter) }
    var notifyInterval by remember { mutableStateOf(settings.notifyIntervalMinutes.toString()) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = deviceFilter,
            onValueChange = { deviceFilter = it },
            label = { Text("Device фильтр") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = notifyInterval,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } || newVal.isEmpty()) {
                    notifyInterval = newVal
                }
            },
            label = { Text("Интервал уведомлений (мин)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            settings.deviceFilter = deviceFilter
            settings.notifyIntervalMinutes = notifyInterval.toIntOrNull() ?: 1
        }) {
            Text("Сохранить настройки")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onReconnect) {
            Text("Переподключиться к MQTT")
        }
    }
}

@Composable
fun PlaceholderScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Заглушки будущего функционала", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text("• Bluetooth терминал (подключение к ESP32, отправка команд, приём ответов)")
        Text("• Автозапуск MQTT после перезагрузки телефона и постоянный фоновый сервис с уведомлением о статусе")
        Text("• Сохранение истории сообщений в Room после перезапуска приложения")
        Text("• Кликабельные ссылки внутри карточек (парсинг JSON)")
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Для работы нужны разрешения: уведомления, интернет. Пожалуйста, выдайте их в настройках телефона вручную.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}