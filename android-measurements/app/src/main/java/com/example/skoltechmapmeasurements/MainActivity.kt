package com.example.skoltechmapmeasurements

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.skoltechmapmeasurements.model.SessionManager
import com.example.skoltechmapmeasurements.preferences.PreferencesManager
import com.example.skoltechmapmeasurements.service.MeasurementService
import com.example.skoltechmapmeasurements.ui.theme.SkoltechMapMeasurementsTheme
import com.example.skoltechmapmeasurements.util.PermissionUtil
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private lateinit var preferencesManager: PreferencesManager
    private val latestLocationFlow = MutableStateFlow<String>("No location data yet")
    private val bluetoothDevicesFlow = MutableStateFlow<String>("No Bluetooth devices found yet")

    // Receiver for location and Bluetooth updates
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast: ${intent.action}")
            
            when (intent.action) {
                MeasurementService.ACTION_LOCATION_UPDATE -> {
                    val provider = intent.getStringExtra(MeasurementService.EXTRA_PROVIDER) ?: "unknown"
                    val latitude = intent.getDoubleExtra(MeasurementService.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(MeasurementService.EXTRA_LONGITUDE, 0.0)
                    
                    val locationText = "Last $provider location: $latitude, $longitude"
                    Log.d(TAG, "Location update: $locationText")
                    latestLocationFlow.value = locationText
                }
                MeasurementService.ACTION_BLUETOOTH_UPDATE -> {
                    val count = intent.getIntExtra(MeasurementService.EXTRA_BLUETOOTH_COUNT, 0)
                    val btText = "Found $count Bluetooth devices"
                    Log.d(TAG, "Bluetooth update: $btText")
                    bluetoothDevicesFlow.value = btText
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)
        Log.d(TAG, "MainActivity onCreate()")

        try {
            // Create an explicit filter for our broadcasts
            val intentFilter = IntentFilter().apply {
                addAction(MeasurementService.ACTION_LOCATION_UPDATE)
                addAction(MeasurementService.ACTION_BLUETOOTH_UPDATE)
            }
            
            // Log detailed information about the filter
            Log.d(TAG, "Registering broadcast receiver with filter: LOCATION_UPDATE=${MeasurementService.ACTION_LOCATION_UPDATE}, BLUETOOTH_UPDATE=${MeasurementService.ACTION_BLUETOOTH_UPDATE}")
            
            // Register with explicit export flag (required for Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    dataReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    dataReceiver,
                    intentFilter
                )
            }
            
            Log.d(TAG, "Broadcast receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering broadcast receiver", e)
        }

        setContent {
            SkoltechMapMeasurementsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeasurementApp(preferencesManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReceiver)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun MeasurementApp(preferencesManager: PreferencesManager) {
        val context = LocalContext.current
        val lifecycleScope = (context as ComponentActivity).lifecycleScope

        val baseUrl = preferencesManager.baseUrlFlow
            .collectAsStateWithLifecycle(initialValue = PreferencesManager.DEFAULT_BASE_URL)

        var editingBaseUrl by remember { mutableStateOf(baseUrl.value) }
        var isSessionActive by remember { mutableStateOf(SessionManager.getCurrentSessionId() != null) }
        val latestLocation by latestLocationFlow.collectAsStateWithLifecycle()
        val bluetoothDevices by bluetoothDevicesFlow.collectAsStateWithLifecycle()
        var statusText by remember { mutableStateOf("Ready to start") }
        val checkpointId by remember { mutableIntStateOf(SessionManager.currentCheckpointId) }
        val checkpointSent by remember { mutableStateOf(SessionManager.checkpointSent) }

        // Observe EventBus for data updates
        DisposableEffect(Unit) {
            // Observe location updates from EventBus
            val locationObserver = androidx.lifecycle.Observer<com.example.skoltechmapmeasurements.event.LocationUpdate> { update ->
                Log.d(TAG, "EventBus: Location update from ${update.provider}: ${update.latitude}, ${update.longitude}")
                val locationText = "Last ${update.provider} location: ${update.latitude}, ${update.longitude}"
                latestLocationFlow.value = locationText
            }
            
            // Observe Bluetooth updates from EventBus
            val bluetoothObserver = androidx.lifecycle.Observer<com.example.skoltechmapmeasurements.event.BluetoothUpdate> { update ->
                Log.d(TAG, "EventBus: Bluetooth update - ${update.deviceCount} devices")
                val btText = "Found ${update.deviceCount} Bluetooth devices"
                bluetoothDevicesFlow.value = btText
            }
            
            // Start observing
            com.example.skoltechmapmeasurements.event.EventBus.locationData.observe(context, locationObserver)
            com.example.skoltechmapmeasurements.event.EventBus.bluetoothData.observe(context, bluetoothObserver)
            
            onDispose {
                // Stop observing
                com.example.skoltechmapmeasurements.event.EventBus.locationData.removeObserver(locationObserver)
                com.example.skoltechmapmeasurements.event.EventBus.bluetoothData.removeObserver(bluetoothObserver)
            }
        }

        val scrollState = rememberScrollState()

        val permissionsState = rememberMultiplePermissionsState(
            permissions = PermissionUtil.REQUIRED_PERMISSIONS.toList()
        )

        LaunchedEffect(permissionsState.allPermissionsGranted) {
            if (!permissionsState.allPermissionsGranted) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Skoltech Map Measurements",
                style = MaterialTheme.typography.headlineMedium
            )

            PermissionStatusCard(permissionsState.allPermissionsGranted)

            OutlinedTextField(
                value = editingBaseUrl,
                onValueChange = { editingBaseUrl = it },
                label = { Text("Backend API URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    lifecycleScope.launch {
                        preferencesManager.saveBaseUrl(editingBaseUrl)
                    }
                    Toast.makeText(context, "Backend URL saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Backend URL")
            }

            Spacer(modifier = Modifier.height(16.dp))

            SessionControls(
                isSessionActive = isSessionActive,
                baseUrl = baseUrl.value,
                onSessionStart = {
                    // Start a new measurement session
                    val sessionId = SessionManager.startNewSession()
                    val serviceIntent = Intent(context, MeasurementService::class.java).apply {
                        putExtra(MeasurementService.EXTRA_SESSION_ID, sessionId)
                    }
                    context.startService(serviceIntent)
                    isSessionActive = true
                    statusText = "Session started with ID: $sessionId"
                    Log.d(TAG, "Started measurement service with session: $sessionId")
                    
                    // Reset initial values to show we're waiting for updates
                    latestLocationFlow.value = "Waiting for location updates..."
                    bluetoothDevicesFlow.value = "Scanning for Bluetooth devices..."
                    
                    // Post initial values to EventBus
                    com.example.skoltechmapmeasurements.event.EventBus.postLocationUpdate(
                        "initializing", 0.0, 0.0
                    )
                    com.example.skoltechmapmeasurements.event.EventBus.postBluetoothUpdate(0)
                    
                    Toast.makeText(context, "Measurement service started", Toast.LENGTH_SHORT).show()
                },
                onSessionStop = {
                    // Stop the measurement service
                    context.stopService(Intent(context, MeasurementService::class.java))
                    SessionManager.endSession()
                    isSessionActive = false
                    statusText = "Session stopped"
                    Log.d(TAG, "Stopped measurement service")
                }
            )

            Button(
                onClick = {
                    SessionManager.incrementCheckpointId()
                },
                enabled = isSessionActive && checkpointSent,
                modifier = Modifier.weight(1f)
            ) {
                Text("Checkpoint $checkpointId" + if (!checkpointSent) " (sending...)" else "")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = latestLocation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = bluetoothDevices,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (isSessionActive) {
                Text(
                    text = "Measurement in progress...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "The app is collecting location, WiFi, and Bluetooth data",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    fun PermissionStatusCard(allGranted: Boolean) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (allGranted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = if (allGranted) "All permissions granted" else "Missing permissions",
                modifier = Modifier.padding(16.dp),
                color = if (allGranted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    @Composable
    fun SessionControls(
        isSessionActive: Boolean,
        baseUrl: String,
        onSessionStart: () -> Unit,
        onSessionStop: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSessionActive) "Session is active" else "No active session",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onSessionStart,
                    enabled = !isSessionActive && baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Session")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onSessionStop,
                    enabled = isSessionActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Session")
                }
            }
        }
    }
}
