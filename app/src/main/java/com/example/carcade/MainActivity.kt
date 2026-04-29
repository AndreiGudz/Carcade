package com.example.carcade

import com.example.carcade.ui.theme.CarcadeTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsDataStore(this)

        setContent {
            CarcadeTheme {
                val navController = rememberNavController()
                // ViewModel для переподключения из настроек
                val mqttViewModel: MqttViewModel = viewModel()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Home, contentDescription = "Лента") },
                                label = { Text("MQTT лента") },
                                selected = navController.currentDestination?.route == "mqtt_feed",
                                onClick = {
                                    navController.navigate("mqtt_feed") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
}