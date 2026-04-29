package com.example.carcade

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttFeedScreen(viewModel: MqttViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Состояние для удаляемого сообщения
    var messageToDelete by remember { mutableStateOf<String?>(null) }

    // Автоматическое переподключение при возвращении на экран
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.startMqttIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Проверяем, нужно ли подсветить сообщение (при переходе из уведомления)
    LaunchedEffect(Unit) {
        val intent = (context as? android.app.Activity)?.intent
        intent?.getStringExtra("highlight_message_id")?.let { messageId ->
            // Можно анимировать прокрутку к сообщению или подсветить его
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Переход к сообщению из уведомления",
                    duration = SnackbarDuration.Short
                )
            }
            intent.removeExtra("highlight_message_id")
        }
    }

    // Показываем Snackbar при ошибке подключения
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Error) {
            val errorMsg = (connectionState as ConnectionState.Error).message
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Ошибка: $errorMsg",
                    actionLabel = "Повторить",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.reconnect()
                }
            }
        }
    }

    // Диалог подтверждения очистки
    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete?.let { viewModel.deleteMessage(it) }
                    messageToDelete = null
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Статусная строка и кнопка очистки
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MQTT лента ESP",
                    style = MaterialTheme.typography.titleMedium
                )
                if (messages.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllMessages() }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Очистить все",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Очистить все")
                    }
                }
            }

            StatusBar(connectionState)

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ожидание сообщений...")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { msg ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    messageToDelete = msg.id
                                    false  // Не удаляем сразу, показываем диалог
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                        else -> Color.Transparent
                                    },
                                    label = "swipe_color"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color.White
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true
                        ) {
                            MessageCard(msg, context)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun MessageCard(msg: MqttMessageItem, context: android.content.Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (msg.time != null) {
                Text(
                    text = "Время: ${msg.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
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
            Text(
                text = msg.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            if (msg.lat != 0.0 && msg.lng != 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val yandexUrl = "http://maps.yandex.ru/?text=${msg.time ?: "Location"}" +
                                "&sll=${msg.lng},${msg.lat}" +
                                "&sspn=0.032932,0.018581" +
                                "&ol=geo&oll=${msg.lng},${msg.lat}" +
                                "&ll=${msg.lng},${msg.lat}" +
                                "&spn=0.067205,0.021163&z=15&l=map"
                        context.startActivity(Intent(Intent.ACTION_VIEW, yandexUrl.toUri()))
                    }) {
                        Icon(Icons.Filled.Place, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Яндекс.Карты")
                    }
                    TextButton(onClick = {
                        val googleUrl = "https://www.google.com/maps?ll=${msg.lat},${msg.lng}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, googleUrl.toUri()))
                    }) {
                        Icon(Icons.Filled.Place, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Google Maps")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBar(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.Connected -> "Подключено" to MaterialTheme.colorScheme.primary
        ConnectionState.Connecting -> "Подключение..." to MaterialTheme.colorScheme.secondary
        is ConnectionState.Error -> "Ошибка" to MaterialTheme.colorScheme.error
        ConnectionState.Disconnected -> "Отключено" to MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == ConnectionState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}