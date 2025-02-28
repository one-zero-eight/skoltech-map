package com.example.skoltechmapmeasurements.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.skoltechmapmeasurements.model.WifiNetwork
import com.example.skoltechmapmeasurements.util.PermissionUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Timer
import java.util.TimerTask

class WifiScanner(private val context: Context) {

    private val TAG = "WifiScanner"
    
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    @SuppressLint("MissingPermission")
    fun getWifiNetworks(): Flow<List<WifiNetwork>> = callbackFlow {
        if (!PermissionUtil.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted for WiFi scanning")
            trySend(emptyList())
            return@callbackFlow
        }
        
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "WiFi is not enabled")
            trySend(emptyList())
            return@callbackFlow
        }
        
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    scanSuccess()
                } else {
                    scanFailure()
                }
            }
            
            private fun scanSuccess() {
                val results = wifiManager.scanResults
                val wifiNetworks = results.map { it.toWifiNetwork() }
                trySend(wifiNetworks)
            }
            
            private fun scanFailure() {
                // Use the last cached results on failure
                val results = wifiManager.scanResults
                val wifiNetworks = results.map { it.toWifiNetwork() }
                trySend(wifiNetworks)
            }
        }
        
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        
        // Register receiver with explicit export flag for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(wifiScanReceiver, intentFilter)
        }
        
        // Set up a timer to periodically scan for wifi networks
        val scanInterval = 10000L // 10 seconds
        val timer = Timer()
        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                wifiManager.startScan()
            }
        }, 0L, scanInterval)
        
        awaitClose {
            context.unregisterReceiver(wifiScanReceiver)
            timer.cancel()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun ScanResult.toWifiNetwork(): WifiNetwork {
        return WifiNetwork(
            ssid = if (SSID.isNullOrEmpty()) "<Hidden SSID>" else SSID.trim('"'),
            bssid = BSSID,
            signalStrength = level,
            frequency = frequency
        )
    }
}