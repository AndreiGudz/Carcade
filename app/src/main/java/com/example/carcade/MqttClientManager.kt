package com.example.carcade

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * Синглтон для управления подключением к MQTT.
 * Упрощено для MVP: нет реконнекта, фоновой работы и сложной обработки ошибок.
 */
object MqttClientManager {
    private const val TAG = "MqttClientManager"
    private var mqttClient: MqttClient? = null
    private var isConnected = false
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
        if (isConnected) return // уже подключены

        val clientId = MqttClient.generateClientId()
        mqttClient = MqttClient(host, clientId, MemoryPersistence())

        val options = MqttConnectOptions().apply {
            userName = username
            setPassword(password.toCharArray())
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
        }

        try {
            mqttClient?.connect(options)
            isConnected = true
            Log.d(TAG, "Соединение с MQTT установлено")

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    Log.e(TAG, "Соединение потеряно: ${cause?.message}")
                    onError(cause ?: Exception("Connection lost"))
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload)
                        Log.d(TAG, "Сообщение получено: $topic -> $payload")
                        onMessage(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // не используется
                }
            })

            mqttClient?.subscribe(DEFAULT_TOPIC, 1)
            Log.d(TAG, "Подписка на $DEFAULT_TOPIC")
        } catch (e: MqttException) {
            isConnected = false
            Log.e(TAG, "Ошибка подключения: ${e.message}")
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