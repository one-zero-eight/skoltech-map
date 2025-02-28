package com.example.skoltechmapmeasurements.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.example.skoltechmapmeasurements.model.AnyLocation
import com.example.skoltechmapmeasurements.util.PermissionUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationManager(private val context: Context) {

    private val TAG = "LocationManager"
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private val androidLocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val locationUpdateInterval = 5000L // 5 seconds
    private val locationFastestUpdateInterval = 2000L // 2 seconds

    // Create a flow for fused location updates
    @SuppressLint("MissingPermission")
    fun getFusedLocationUpdates(): Flow<AnyLocation> = callbackFlow {
        if (!PermissionUtil.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted")
            close()
            return@callbackFlow
        }
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, locationUpdateInterval)
            .setMinUpdateIntervalMillis(locationFastestUpdateInterval)
            .build()
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val anyLocation = location.toAnyLocation("fused")
                    trySend(anyLocation)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting fused location updates", e)
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Create a flow for GPS location updates
    @SuppressLint("MissingPermission")
    fun getGpsLocationUpdates(): Flow<AnyLocation> = callbackFlow {
        if (!PermissionUtil.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted")
            close()
            return@callbackFlow
        }
        
        if (!androidLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS provider is not enabled")
            close()
            return@callbackFlow
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val anyLocation = location.toAnyLocation(LocationManager.GPS_PROVIDER)
                trySend(anyLocation)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            androidLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                locationUpdateInterval,
                0f,
                locationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting GPS location updates", e)
            close(e)
        }

        awaitClose {
            androidLocationManager.removeUpdates(locationListener)
        }
    }

    // Create a flow for network location updates
    @SuppressLint("MissingPermission")
    fun getNetworkLocationUpdates(): Flow<AnyLocation> = callbackFlow {
        if (!PermissionUtil.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted")
            close()
            return@callbackFlow
        }
        
        if (!androidLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.e(TAG, "Network provider is not enabled")
            close()
            return@callbackFlow
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val anyLocation = location.toAnyLocation(LocationManager.NETWORK_PROVIDER)
                trySend(anyLocation)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            androidLocationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                locationUpdateInterval,
                0f,
                locationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting network location updates", e)
            close(e)
        }

        awaitClose {
            androidLocationManager.removeUpdates(locationListener)
        }
    }

    // Extension function to convert Location to AnyLocation
    private fun Location.toAnyLocation(provider: String): AnyLocation {
        return AnyLocation(
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = if (hasAltitude()) altitude else null,
            speed = if (hasSpeed()) speed else null,
            bearing = if (hasBearing()) bearing else null
        )
    }
}