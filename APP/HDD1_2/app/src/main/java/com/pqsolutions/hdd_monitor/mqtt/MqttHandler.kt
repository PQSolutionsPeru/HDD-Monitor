package com.pqsolutions.hdd_monitor.mqtt

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "MqttHandler"

data class Esp32Device(
    val id: String,
    val name: String,
    val status: String
)

class MqttHandler {
    private var mqttClient: MqttAsyncClient? = null
    private val broker = "tcp://node02.myqtthub.com:1883"
    private val clientId = "AndroidApp-${UUID.randomUUID()}"
    private val username = "ESP32-1"
    private val password = "esp32"

    private val _availableDevices = MutableStateFlow<List<Esp32Device>>(emptyList())
    val availableDevices: StateFlow<List<Esp32Device>> = _availableDevices

    val messageChannel = Channel<Pair<String, String>>(Channel.BUFFERED)

    fun connect(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        try {
            val persistence = MemoryPersistence()
            mqttClient = MqttAsyncClient(broker, clientId, persistence)

            val mqttConnectOptions = MqttConnectOptions().apply {
                userName = username
                password = this@MqttHandler.password.toCharArray()
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e(TAG, "Conexión MQTT perdida", cause)
                    onFailure("Conexión perdida: ${cause?.message}")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    try {
                        val payload = String(message.payload)
                        Log.d(TAG, "Mensaje recibido en $topic: $payload")

                        when {
                            topic == "esp32/available" -> handleAvailableDevice(payload)
                            else -> messageChannel.trySend(topic to payload)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando mensaje", e)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(TAG, "Mensaje entregado")
                }
            })

            mqttClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Conectado a MQTT")
                    subscribeToTopics()
                    onSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Error conectando a MQTT", exception)
                    onFailure("Error de conexión: ${exception?.message}")
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando cliente MQTT", e)
            onFailure("Error: ${e.message}")
        }
    }

    private fun handleAvailableDevice(payload: String) {
        try {
            val json = JSONObject(payload)
            val device = Esp32Device(
                id = json.getString("esp32_id"),
                name = json.getString("name"),
                status = json.getString("status")
            )

            val currentList = _availableDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == device.id }

            if (device.status == "configured") {
                if (existingIndex != -1) {
                    currentList.removeAt(existingIndex)
                    _availableDevices.value = currentList
                }
            } else {
                if (existingIndex != -1) {
                    currentList[existingIndex] = device
                } else {
                    currentList.add(device)
                }
                _availableDevices.value = currentList
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando dispositivo disponible", e)
        }
    }

    private fun subscribeToTopics() {
        subscribe("esp32/available") // Tópico para dispositivos disponibles
    }

    fun subscribe(topic: String, qos: Int = 1) {
        mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG, "Suscrito a $topic")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "Error suscribiendo a $topic", exception)
            }
        })
    }

    fun publish(topic: String, message: String, qos: Int = 1, retained: Boolean = false) {
        try {
            val mqttMessage = MqttMessage().apply {
                payload = message.toByteArray()
                this.qos = qos
                isRetained = retained
            }

            mqttClient?.publish(topic, mqttMessage, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Mensaje publicado en $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Error publicando en $topic", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando mensaje", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
            _availableDevices.value = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando MQTT", e)
        }
    }
}