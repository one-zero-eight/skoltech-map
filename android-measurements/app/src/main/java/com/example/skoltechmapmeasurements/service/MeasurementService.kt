package com.example.skoltechmapmeasurements.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.skoltechmapmeasurements.MainActivity
import com.example.skoltechmapmeasurements.R
import com.example.skoltechmapmeasurements.bluetooth.BluetoothScanner
import com.example.skoltechmapmeasurements.device.DeviceInfoManager
import com.example.skoltechmapmeasurements.location.LocationManager
import com.example.skoltechmapmeasurements.model.AnyLocation
import com.example.skoltechmapmeasurements.model.BluetoothDevice
import com.example.skoltechmapmeasurements.model.DeviceInfo
import com.example.skoltechmapmeasurements.model.Measurement
import com.example.skoltechmapmeasurements.model.SessionManager
import com.example.skoltechmapmeasurements.model.WifiNetwork
import com.example.skoltechmapmeasurements.network.MeasurementApiService
import com.example.skoltechmapmeasurements.network.RetrofitClient
import com.example.skoltechmapmeasurements.preferences.PreferencesManager
import com.example.skoltechmapmeasurements.wifi.WifiScanner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MeasurementService : Service() {

    private val TAG = "MeasurementService"
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error in service coroutine", throwable)
    })
    
    private val counter = AtomicInteger(0)
    
    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var wifiScanner: WifiScanner
    private lateinit var deviceInfoManager: DeviceInfoManager
    private lateinit var preferencesManager: PreferencesManager
    
    private var apiService: MeasurementApiService? = null
    
    private val fusedLocationFlow = MutableStateFlow<AnyLocation?>(null)
    private val gpsLocationFlow = MutableStateFlow<AnyLocation?>(null)
    private val networkLocationFlow = MutableStateFlow<AnyLocation?>(null)
    private val bluetoothDevicesFlow = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val wifiNetworksFlow = MutableStateFlow<List<WifiNetwork>>(emptyList())
    
    private val _measurementStatusFlow = MutableStateFlow<String>("Initializing...")
    val measurementStatusFlow = _measurementStatusFlow.asStateFlow()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MeasurementService onCreate()")
        
        try {
            locationManager = LocationManager(this)
            bluetoothScanner = BluetoothScanner(this)
            wifiScanner = WifiScanner(this)
            deviceInfoManager = DeviceInfoManager()
            preferencesManager = PreferencesManager(this)
            
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Started foreground service")
            
            scope.launch {
                initializeApiService()
                _measurementStatusFlow.value = "Initialized API service with URL: ${preferencesManager.baseUrlFlow.first()}"
                
                collectLocationData()
                _measurementStatusFlow.value = "Started location data collection"
                
                collectBluetoothData()
                _measurementStatusFlow.value = "Started Bluetooth data collection"
                
                collectWifiData()
                _measurementStatusFlow.value = "Started WiFi data collection"
                
                startMeasurementSending()
                _measurementStatusFlow.value = "Started sending measurements to backend"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in service onCreate", e)
            _measurementStatusFlow.value = "Error starting service: ${e.message}"
        }
    }
    
    private suspend fun initializeApiService() {
        val baseUrl = preferencesManager.baseUrlFlow.first()
        apiService = RetrofitClient.buildApiService(baseUrl)
    }
    
    private fun collectLocationData() {
        // Send an initial location update to let the UI know we're scanning for locations
        scope.launch {
            kotlinx.coroutines.delay(1000) // Wait a bit to make sure the UI is ready
            
            // Send via broadcast intent
            val intent = Intent(ACTION_LOCATION_UPDATE).apply {
                putExtra(EXTRA_PROVIDER, "initializing")
                putExtra(EXTRA_LATITUDE, 0.0)
                putExtra(EXTRA_LONGITUDE, 0.0)
            }
            sendBroadcast(intent)
            
            // Also via EventBus
            com.example.skoltechmapmeasurements.event.EventBus.postLocationUpdate(
                "initializing", 0.0, 0.0
            )
            
            Log.d(TAG, "Sent initial location update broadcast and EventBus post")
        }
        
        // Collect fused location data
        scope.launch {
            try {
                locationManager.getFusedLocationUpdates().collect { location ->
                    fusedLocationFlow.value = location
                    broadcastLocationUpdate("fused", location)
                    Log.d(TAG, "Fused location update: ${location.latitude}, ${location.longitude}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting fused location data", e)
            }
        }
        
        // Collect GPS location data
        scope.launch {
            try {
                locationManager.getGpsLocationUpdates().collect { location ->
                    gpsLocationFlow.value = location
                    broadcastLocationUpdate("GPS", location)
                    Log.d(TAG, "GPS location update: ${location.latitude}, ${location.longitude}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting GPS location data", e)
            }
        }
        
        // Collect network location data
        scope.launch {
            try {
                locationManager.getNetworkLocationUpdates().collect { location ->
                    networkLocationFlow.value = location
                    broadcastLocationUpdate("Network", location)
                    Log.d(TAG, "Network location update: ${location.latitude}, ${location.longitude}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting network location data", e)
            }
        }
    }
    
    private fun collectBluetoothData() {
        scope.launch {
            try {
                bluetoothScanner.getBluetoothDevices().collect { devices ->
                    bluetoothDevicesFlow.value = devices
                    _measurementStatusFlow.value = "Found ${devices.size} Bluetooth devices"
                    Log.d(TAG, "Updated Bluetooth devices: ${devices.size} found")
                    
                    // Broadcast Bluetooth update
                    broadcastBluetoothDevicesUpdate(devices)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting Bluetooth data", e)
                _measurementStatusFlow.value = "Error with Bluetooth: ${e.message}"
            }
        }
        
        // Send an initial "empty" update to let the UI know we're scanning
        scope.launch {
            kotlinx.coroutines.delay(1000) // Wait a bit to make sure the UI is ready
            
            // Send via broadcast intent
            val intent = Intent(ACTION_BLUETOOTH_UPDATE).apply {
                putExtra(EXTRA_BLUETOOTH_COUNT, 0)
            }
            sendBroadcast(intent)
            
            // Also via EventBus
            com.example.skoltechmapmeasurements.event.EventBus.postBluetoothUpdate(0)
            
            Log.d(TAG, "Sent initial Bluetooth update broadcast and EventBus post")
        }
    }
    
    private fun collectWifiData() {
        scope.launch {
            wifiScanner.getWifiNetworks().collect { networks ->
                wifiNetworksFlow.value = networks
                _measurementStatusFlow.value = "Found ${networks.size} WiFi networks"
            }
        }
    }
    
    private fun startMeasurementSending() {
        // Create combined flow
        val deviceInfo = deviceInfoManager.getDeviceInfo()
        
        scope.launch {
            kotlinx.coroutines.delay(5000) // Initial delay to collect some data
            
            while (true) {
                sendMeasurements(deviceInfo)
                kotlinx.coroutines.delay(SEND_INTERVAL)
            }
        }
    }
    
    private suspend fun sendMeasurements(deviceInfo: DeviceInfo) {
        val currentSessionId = SessionManager.getCurrentSessionId() ?: return
        val timestamp = System.currentTimeMillis()
        
        try {
            // Send fused location
            val fusedLocation = fusedLocationFlow.value
            if (fusedLocation != null) {
                sendMeasurement(
                    Measurement(
                        sessionId = currentSessionId,
                        timestamp = timestamp,
                        location = fusedLocation,
                        bluetoothDevices = null,
                        wifiNetworks = null,
                        deviceInfo = deviceInfo
                    )
                )
            }
            
            // Send GPS location
            val gpsLocation = gpsLocationFlow.value
            if (gpsLocation != null) {
                sendMeasurement(
                    Measurement(
                        sessionId = currentSessionId,
                        timestamp = timestamp,
                        location = gpsLocation,
                        bluetoothDevices = null,
                        wifiNetworks = null,
                        deviceInfo = deviceInfo
                    )
                )
            }
            
            // Send network location
            val networkLocation = networkLocationFlow.value
            if (networkLocation != null) {
                sendMeasurement(
                    Measurement(
                        sessionId = currentSessionId,
                        timestamp = timestamp,
                        location = networkLocation,
                        bluetoothDevices = null,
                        wifiNetworks = null,
                        deviceInfo = deviceInfo
                    )
                )
            }
            
            // Send Bluetooth devices
            val bluetoothDevices = bluetoothDevicesFlow.value
            if (bluetoothDevices.isNotEmpty()) {
                Log.d(TAG, "Sending ${bluetoothDevices.size} Bluetooth devices")
                sendMeasurement(
                    Measurement(
                        sessionId = currentSessionId,
                        timestamp = timestamp,
                        location = null,
                        bluetoothDevices = bluetoothDevices,
                        wifiNetworks = null,
                        deviceInfo = deviceInfo
                    )
                )
            } else {
                Log.d(TAG, "No Bluetooth devices to send")
            }
            
            // Send WiFi networks
            val wifiNetworks = wifiNetworksFlow.value
            if (wifiNetworks.isNotEmpty()) {
                sendMeasurement(
                    Measurement(
                        sessionId = currentSessionId,
                        timestamp = timestamp,
                        location = null,
                        bluetoothDevices = null,
                        wifiNetworks = wifiNetworks,
                        deviceInfo = deviceInfo
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending measurements", e)
            _measurementStatusFlow.value = "Error sending measurements: ${e.message}"
        }
    }
    
    private suspend fun sendMeasurement(measurement: Measurement) {
        withContext(Dispatchers.IO) {
            try {
                val count = counter.incrementAndGet()
                _measurementStatusFlow.value = "Sending measurement #$count"
                
                val apiService = apiService ?: return@withContext
                val response = apiService.sendMeasurement(measurement)
                
                if (response.isSuccessful) {
                    _measurementStatusFlow.value = "Measurement #$count sent successfully"
                } else {
                    _measurementStatusFlow.value = "Failed to send measurement: ${response.code()}"
                    Log.e(TAG, "Failed to send measurement: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                _measurementStatusFlow.value = "Error sending measurement: ${e.message}"
                Log.e(TAG, "Error sending measurement", e)
            }
        }
    }
    
    private fun broadcastLocationUpdate(provider: String, location: AnyLocation) {
        try {
            // 1. Use intent broadcast
            val intent = Intent(ACTION_LOCATION_UPDATE).apply {
                putExtra(EXTRA_PROVIDER, provider)
                putExtra(EXTRA_LATITUDE, location.latitude)
                putExtra(EXTRA_LONGITUDE, location.longitude)
            }
            sendBroadcast(intent)
            
            // 2. Also use EventBus (more reliable)
            com.example.skoltechmapmeasurements.event.EventBus.postLocationUpdate(
                provider, 
                location.latitude,
                location.longitude
            )
            
            Log.d(TAG, "Broadcast location update for $provider: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting location update", e)
        }
    }
    
    // Add a broadcast for Bluetooth devices found
    private fun broadcastBluetoothDevicesUpdate(devices: List<BluetoothDevice>) {
        try {
            // 1. Use intent broadcast
            val intent = Intent(ACTION_BLUETOOTH_UPDATE).apply {
                putExtra(EXTRA_BLUETOOTH_COUNT, devices.size)
            }
            sendBroadcast(intent)
            
            // 2. Also use EventBus (more reliable)
            com.example.skoltechmapmeasurements.event.EventBus.postBluetoothUpdate(devices.size)
            
            Log.d(TAG, "Broadcast Bluetooth update: ${devices.size} devices")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting Bluetooth update", e)
        }
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Measurement Service")
            .setContentText("Collecting location and network data")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Measurement Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Measurement Service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: intent=$intent, flags=$flags, startId=$startId")
        
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        
        if (sessionId != null) {
            // If a session ID was provided, use it instead of starting a new one
            SessionManager.endSession() // End any existing session
            SessionManager.startNewSession() // Start a new session with the provided ID
            Log.d(TAG, "Session started with ID: $sessionId")
        } else if (SessionManager.getCurrentSessionId() == null) {
            // If no session is active, start a new one
            val newSessionId = SessionManager.startNewSession()
            Log.d(TAG, "New session started with generated ID: $newSessionId")
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        SessionManager.endSession()
    }
    
    companion object {
        const val CHANNEL_ID = "MeasurementServiceChannel"
        const val NOTIFICATION_ID = 1
        
        const val SEND_INTERVAL = 10000L // 10 seconds
        
        const val EXTRA_SESSION_ID = "session_id"
        
        const val ACTION_LOCATION_UPDATE = "com.example.skoltechmapmeasurements.LOCATION_UPDATE"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        
        const val ACTION_BLUETOOTH_UPDATE = "com.example.skoltechmapmeasurements.BLUETOOTH_UPDATE"
        const val EXTRA_BLUETOOTH_COUNT = "bluetooth_count"
    }
}