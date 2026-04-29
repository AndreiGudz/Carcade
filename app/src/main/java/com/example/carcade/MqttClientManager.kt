package com.example.carcade

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object MqttClientManager {
    private const val TAG = "MqttClientManager"
    private var mqttClient: MqttClient? = null
    @Volatile
    var isConnected = false
        private set

    const val DEFAULT_HOST = "tcp://srv2.clusterfly.ru:9991"
    const val DEFAULT_USER = "user_bbe4dc60"
    const val DEFAULT_PASS = "uUXbUDySelq-T"
    const val DEFAULT_TOPIC = "user_bbe4dc60/esplog"

    fun connect(
        host: String = DEFAULT_HOST,
        username: String = DEFAULT_USER,
        password: String = DEFAULT_PASS,
        onMessage: (String) -> Unit,
        onStatus: (ConnectionState) -> Unit
    ) {
        if (isConnected) {
            onStatus(ConnectionState.Connected)
            return
        }

        onStatus(ConnectionState.Connecting)
        try {
            val clientId = MqttClient.generateClientId()
            mqttClient = MqttClient(host, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                userName = username
                setPassword(password.toCharArray())
                isCleanSession = false
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true
                maxInflight = 10
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i(TAG, "Подключение завершено, reconnect=$reconnect")
                    try {
                        // QoS 2 – гарантированная доставка без дубликатов
                        mqttClient?.subscribe(DEFAULT_TOPIC, 2)
                        Log.i(TAG, "Подписка на $DEFAULT_TOPIC (QoS 2)")
                    } catch (e: MqttException) {
                        Log.e(TAG, "Ошибка подписки: ${e.message}")
                        onStatus(ConnectionState.Error("Ошибка подписки"))
                        return
                    }
                    isConnected = true
                    onStatus(ConnectionState.Connected)
                }

                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    Log.e(TAG, "Соединение потеряно: ${cause?.message}")
                    onStatus(ConnectionState.Disconnected)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload)
                        Log.d(TAG, "Сообщение получено: $payload")
                        onMessage(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options)
        } catch (e: MqttException) {
            isConnected = false
            Log.e(TAG, "Ошибка подключения: ${e.message}")
            onStatus(ConnectionState.Error(e.message ?: "Неизвестная ошибка"))
        } catch (e: Exception) {
            isConnected = false
            Log.e(TAG, "Неожиданная ошибка: ${e.message}")
            onStatus(ConnectionState.Error(e.message ?: "Ошибка"))
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
}