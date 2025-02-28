package com.example.skoltechmapmeasurements.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Singleton for event bus to communicate between service and UI
 */
object EventBus {
    private val _locationData = MutableLiveData<LocationUpdate>()
    val locationData: LiveData<LocationUpdate> = _locationData
    
    private val _bluetoothData = MutableLiveData<BluetoothUpdate>()
    val bluetoothData: LiveData<BluetoothUpdate> = _bluetoothData
    
    fun postLocationUpdate(provider: String, latitude: Double, longitude: Double) {
        _locationData.postValue(LocationUpdate(provider, latitude, longitude))
    }
    
    fun postBluetoothUpdate(deviceCount: Int) {
        _bluetoothData.postValue(BluetoothUpdate(deviceCount))
    }
}

/**
 * Data classes for updates
 */
data class LocationUpdate(
    val provider: String,
    val latitude: Double,
    val longitude: Double
)

data class BluetoothUpdate(
    val deviceCount: Int
)