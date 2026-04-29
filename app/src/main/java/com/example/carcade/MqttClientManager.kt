package com.example.carcade

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object MqttClientManager {
    private const val TAG = "MqttClientManager"
    private var mqttClient: MqttClient? = null
    @Volatile
    private var isConnected = false

    // Параметры подключения (замените на свои)
    const val DEFAULT_HOST = "tcp://srv2.clusterfly.ru:9991"
    const val DEFAULT_USER = "user_bbe4dc60"
    const val DEFAULT_PASS = "uUXbUDySelq-T"
    const val DEFAULT_TOPIC = "user_bbe4dc60/esplog"

    fun connect(
        host: String = DEFAULT_HOST,
        username: String = DEFAULT_USER,
        password: String = DEFAULT_PASS,
        onMessage: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (isConnected) {
            Log.d(TAG, "Уже подключены к MQTT")
            return
        }

        try {
            val clientId = MqttClient.generateClientId()
            mqttClient = MqttClient(host, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                userName = username
                setPassword(password.toCharArray())
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true // Пытаемся автоматически переподключаться
                maxInflight = 10
            }

            Log.d(TAG, "Подключение к $host...")
            mqttClient?.connect(options)
            isConnected = true
            Log.d(TAG, "Соединение с MQTT установлено")

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "Подключение завершено, reconnect=$reconnect, server=$serverURI")
                    try {
                        mqttClient?.subscribe(DEFAULT_TOPIC, 1)
                        Log.d(TAG, "Подписка на $DEFAULT_TOPIC")
                    } catch (e: MqttException) {
                        Log.e(TAG, "Ошибка подписки: ${e.message}")
                        onError(e)
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    Log.e(TAG, "Соединение потеряно: ${cause?.message}")
                    onError(cause ?: Exception("Connection lost"))
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload)
                        Log.d(TAG, "Получено сообщение от $topic")
                        onMessage(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Не используется, т.к. мы только подписываемся
                }
            })
        } catch (e: MqttException) {
            isConnected = false
            Log.e(TAG, "Ошибка подключения: ${e.message}", e)
            onError(e)
        } catch (e: Exception) {
            isConnected = false
            Log.e(TAG, "Неожиданная ошибка: ${e.message}", e)
            onError(e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отключения: ${e.message}")
        } finally {
            isConnected = false
            mqttClient = null
        }
    }

    fun isConnected(): Boolean = isConnected
}