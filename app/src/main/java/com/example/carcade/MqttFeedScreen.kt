package com.example.carcade

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    val messageCount by viewModel.messageCount.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messageToDelete by remember { mutableStateOf<String?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Проверяем переход из уведомления
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.intent?.let { intent ->
            val messageId = intent.getStringExtra("highlight_message_id")
            if (messageId != null) {
                Log.d("MqttFeedScreen", "Переход к сообщению: $messageId")
                highlightedMessageId = messageId

                // Ищем индекс сообщения в списке
                val index = messages.indexOfFirst { it.id == messageId }
                if (index >= 0) {
                    // Прокручиваем к сообщению
                    listState.animateScrollToItem(index)
                }

                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Переход к сообщению из уведомления",
                        duration = SnackbarDuration.Short
                    )
                }

                // Очищаем extra после обработки
                intent.removeExtra("highlight_message_id")
                intent.removeExtra("open_mqtt_feed")

                // Сбрасываем подсветку через 3 секунды
                kotlinx.coroutines.delay(3000)
                highlightedMessageId = null
            }
        }
    }

    // Автоматическое переподключение ViewModel к сервису при возвращении на экран
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MqttForegroundService.requestStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // Диалог подтверждения удаления
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
            // Верхняя панель со статистикой
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MQTT лента ESP",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (messageCount > 0) {
                        Text(
                            text = "Всего сообщений: $messageCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.clearAllMessages()
                        viewModel.resetMessageCount()
                    }) {
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

            StatusBar(connectionState, messageCount)

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ожидание сообщений...")
                        if (messageCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Получено сообщений: $messageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { msg ->
                        val isHighlighted = highlightedMessageId == msg.id

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    messageToDelete = msg.id
                                    false
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
                            // Анимация подсветки
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isHighlighted) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                },
                                animationSpec = tween(500),
                                label = "highlight_bg"
                            )

                            val scale by animateFloatAsState(
                                targetValue = if (isHighlighted) 1.02f else 1f,
                                animationSpec = tween(300),
                                label = "highlight_scale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(backgroundColor)
                            ) {
                                Box(
                                    modifier = Modifier.scale(scale)
                                ) {
                                    MessageCard(msg, context)
                                }
                            }
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

// Остальные функции (MessageCard, StatusBar) остаются без изменений
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
fun StatusBar(state: ConnectionState, messageCount: Int = 0) {
    val (text, color) = when (state) {
        ConnectionState.Connected -> "Подключено • Сообщений: $messageCount" to MaterialTheme.colorScheme.primary
        ConnectionState.Connecting -> "Подключение..." to MaterialTheme.colorScheme.secondary
        is ConnectionState.Error -> "Ошибка: ${state.message}" to MaterialTheme.colorScheme.error
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
            when (state) {
                ConnectionState.Connecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                ConnectionState.Connected -> {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(Modifier.width(8.dp))
                }
                else -> {}
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}