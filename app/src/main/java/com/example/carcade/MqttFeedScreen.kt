package com.example.carcade

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

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
            text = "MQTT лента ESP (последние 10 сообщений)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ожидание сообщений...")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages) { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Время сообщения (если есть)
                            if (msg.time != null) {
                                Text(
                                    text = "Время: ${msg.time}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Координаты (если есть)
                            if (msg.lat != 0.0 && msg.lng != 0.0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Place,
                                        contentDescription = "Координаты",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${"%.6f".format(msg.lat)}, ${"%.6f".format(msg.lng)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Тело сообщения
                            Text(
                                text = msg.body,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Кнопки для открытия карт, если есть координаты
                            if (msg.lat != 0.0 && msg.lng != 0.0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Яндекс.Карты
                                    TextButton(onClick = {
                                        val yandexUrl = "http://maps.yandex.ru/?text=${msg.time ?: "Location"}" +
                                                "&sll=${msg.lng},${msg.lat}" +
                                                "&sspn=0.032932,0.018581" +
                                                "&ol=geo&oll=${msg.lng},${msg.lat}" +
                                                "&ll=${msg.lng},${msg.lat}" +
                                                "&spn=0.067205,0.021163&z=15&l=map"
                                        val intent = Intent(Intent.ACTION_VIEW, yandexUrl.toUri())
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Яндекс.Карты")
                                    }

                                    // Google Maps
                                    TextButton(onClick = {
                                        val googleUrl = "https://www.google.com/maps?ll=${msg.lat},${msg.lng}"
                                        val intent = Intent(Intent.ACTION_VIEW, googleUrl.toUri())
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Google Maps")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}