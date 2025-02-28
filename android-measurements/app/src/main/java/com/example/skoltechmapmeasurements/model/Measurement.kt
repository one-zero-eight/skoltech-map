package com.example.skoltechmapmeasurements.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Measurement(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("location") val location: AnyLocation?,
    @SerializedName("bluetooth_devices") val bluetoothDevices: List<BluetoothDevice>?,
    @SerializedName("wifi_networks") val wifiNetworks: List<WifiNetwork>?,
    @SerializedName("device_info") val deviceInfo: DeviceInfo?
)

data class AnyLocation(
    @SerializedName("provider") val provider: String?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("altitude") val altitude: Double?,
    @SerializedName("speed") val speed: Float?,
    @SerializedName("bearing") val bearing: Float?
)

data class BluetoothDevice(
    @SerializedName("name") val name: String?,
    @SerializedName("address") val address: String,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("is_connected") val isConnected: Boolean
)

data class WifiNetwork(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("bssid") val bssid: String,
    @SerializedName("signal_strength") val signalStrength: Int,
    @SerializedName("frequency") val frequency: Int
)

data class DeviceInfo(
    @SerializedName("model") val model: String,
)

object SessionManager {
    private var currentSessionId: String? = null

    fun startNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId!!
    }

    fun getCurrentSessionId(): String? {
        return currentSessionId
    }

    fun endSession() {
        currentSessionId = null
    }
}