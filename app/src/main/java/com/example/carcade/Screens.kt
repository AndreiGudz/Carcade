package com.example.carcade

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri

@Composable
fun MqttFeedScreen(viewModel: MqttViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // При возвращении на экран (onResume) пытаемся переподключиться
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.startMqttIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "MQTT лента (последние 10 сообщений)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Сообщений пока нет")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages) { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.body,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (msg.lat != 0.0 && msg.lng != 0.0) {
                                val yandexUrl = "http://maps.yandex.ru/?text=${msg.time}&sll=${msg.lng},${msg.lat}&sspn=0.032932,0.018581&ol=geo&oll=${msg.lng},${msg.lat}&ll=${msg.lng},${msg.lat}&spn=0.067205,0.021163&z=15&l=map"
                                val googleUrl = "https://www.google.com/maps?ll=${msg.lat},${msg.lng}"
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, yandexUrl.toUri())
                                    context.startActivity(intent)
                                }) {
                                    Text("Открыть ссылку")
                                }
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, googleUrl.toUri())
                                    context.startActivity(intent)
                                }) {
                                    Text("Открыть ссылку")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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