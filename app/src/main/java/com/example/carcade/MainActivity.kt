package com.example.carcade

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.carcade.ui.theme.CarcadeTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        val settings = SettingsDataStore(this)

        // Проверяем и логируем Intent
        handleIncomingIntent(intent)

        setContent {
            CarcadeTheme {
                val navController = rememberNavController()
                val mqttViewModel: MqttViewModel = viewModel()

                // Обрабатываем навигацию при переходе из уведомления
                LaunchedEffect(Unit) {
                    intent?.let { incomingIntent ->
                        val openMqttFeed = incomingIntent.getBooleanExtra("open_mqtt_feed", false)
                        val highlightMessageId = incomingIntent.getStringExtra("highlight_message_id")

                        if (openMqttFeed) {
                            Log.d(TAG, "Переход из уведомления, highlightMessageId=$highlightMessageId")

                            // Переходим на экран MQTT ленты
                            navController.navigate("mqtt_feed") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }

                            // Очищаем extras, чтобы не обрабатывать повторно
                            incomingIntent.removeExtra("open_mqtt_feed")
                            incomingIntent.removeExtra("highlight_message_id")
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Home, contentDescription = "Лента") },
                                label = { Text("MQTT лента") },
                                selected = navController.currentDestination?.route == "mqtt_feed",
                                onClick = {
                                    navController.navigate("mqtt_feed") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Настройки") },
                                label = { Text("Настройки") },
                                selected = navController.currentDestination?.route == "settings",
                                onClick = {
                                    navController.navigate("settings") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Star, contentDescription = "Заглушки") },
                                label = { Text("Заглушки") },
                                selected = navController.currentDestination?.route == "placeholder",
                                onClick = {
                                    navController.navigate("placeholder") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "mqtt_feed",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("mqtt_feed") {
                            MqttFeedScreen(viewModel = mqttViewModel)
                        }
                        composable("settings") {
                            SettingsScreen(
                                settings = settings,
                                onReconnect = { mqttViewModel.reconnect() }
                            )
                        }
                        composable("placeholder") {
                            PlaceholderScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestPermissions()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.let {
            val openMqttFeed = it.getBooleanExtra("open_mqtt_feed", false)
            val highlightMessageId = it.getStringExtra("highlight_message_id")
            Log.d(TAG, "handleIncomingIntent: openMqttFeed=$openMqttFeed, highlightMessageId=$highlightMessageId")
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }
}