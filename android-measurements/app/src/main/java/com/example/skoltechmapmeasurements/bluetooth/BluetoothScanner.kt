package com.example.skoltechmapmeasurements.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.skoltechmapmeasurements.model.BluetoothDevice as AppBluetoothDevice
import com.example.skoltechmapmeasurements.util.PermissionUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BluetoothScanner(private val context: Context) {

    private val TAG = "BluetoothScanner"
    
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Scan for both classic and BLE devices
    @SuppressLint("MissingPermission")
    fun getBluetoothDevices(): Flow<List<AppBluetoothDevice>> = callbackFlow {
        if (!PermissionUtil.hasBluetoothPermissions(context)) {
            Log.e(TAG, "Bluetooth permissions not granted")
            trySend(emptyList())
            return@callbackFlow
        }
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            trySend(emptyList())
            return@callbackFlow
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            trySend(emptyList())
            return@callbackFlow
        }

        val discoveredDevices = mutableMapOf<String, AppBluetoothDevice>()
        
        // BLE Scan callback
        val bleScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address
                val name = device.name ?: "Unknown Device"
                val rssi = result.rssi
                val isConnected = device.bondState == BluetoothDevice.BOND_BONDED
                
                val bleDevice = AppBluetoothDevice(
                    name = name,
                    address = address,
                    rssi = rssi,
                    isConnected = isConnected
                )
                
                discoveredDevices[address] = bleDevice
                trySend(discoveredDevices.values.toList())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
            }
        }
        
        // Classic Bluetooth receiver
        val classicScanReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        } ?: return
                        
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        val address = device.address
                        val name = device.name ?: "Unknown Device"
                        val isConnected = device.bondState == BluetoothDevice.BOND_BONDED
                        
                        val classicDevice = AppBluetoothDevice(
                            name = name,
                            address = address,
                            rssi = rssi,
                            isConnected = isConnected
                        )
                        
                        discoveredDevices[address] = classicDevice
                        trySend(discoveredDevices.values.toList())
                    }
                }
            }
        }
        
        // For classic Bluetooth scanning
        val classicFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(classicScanReceiver, classicFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(classicScanReceiver, classicFilter)
        }
        
        // Start classic Bluetooth discovery
        try {
            bluetoothAdapter.startDiscovery()
            Log.d(TAG, "Started classic Bluetooth discovery")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting classic Bluetooth discovery", e)
        }
        
        // Start BLE scanning
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner != null) {
            try {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                
                bluetoothLeScanner.startScan(null, scanSettings, bleScanCallback)
                Log.d(TAG, "Started BLE scanning")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting BLE scanning", e)
            }
        } else {
            Log.e(TAG, "BLE Scanner not available")
        }
        
        // Periodically restart scanning to ensure fresh results
        val scanRestartRunnable = object : Runnable {
            @SuppressLint("MissingPermission")
            override fun run() {
                try {
                    // Restart classic discovery
                    if (bluetoothAdapter.isDiscovering) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                    bluetoothAdapter.startDiscovery()
                    
                    // Restart BLE scanning
                    val bleScanner = bluetoothAdapter.bluetoothLeScanner
                    if (bleScanner != null) {
                        bleScanner.stopScan(bleScanCallback)
                        val scanSettings = ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build()
                        bleScanner.startScan(null, scanSettings, bleScanCallback)
                    }
                    
                    Log.d(TAG, "Restarted Bluetooth scanning")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restarting Bluetooth scanning", e)
                }
                
                // Schedule next restart
                mainHandler.postDelayed(this, SCAN_RESTART_INTERVAL)
            }
        }
        
        // Start the periodic restart
        mainHandler.postDelayed(scanRestartRunnable, SCAN_RESTART_INTERVAL)

        awaitClose {
            // Clean up everything
            try {
                context.unregisterReceiver(classicScanReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering classic receiver", e)
            }
            
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling discovery", e)
            }
            
            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
            
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
    
    companion object {
        private const val SCAN_RESTART_INTERVAL = 6_000L // ms
    }
}