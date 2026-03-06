package com.chaitalkstech.teslaheadsupdisplay

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), OnMapsSdkInitializedCallback, SensorEventListener {

    private val TAG = "HUD_DEBUG"
    private val speedMph = mutableStateOf(0)
    private val currentLimit = mutableStateOf("--")
    private val roadName = mutableStateOf("")
    private val bearing = mutableStateOf(0f)
    private val batteryPct = mutableStateOf(0)
    private val isCharging = mutableStateOf(false)
    private val tempCelsius = mutableStateOf(0f)
    private val autoDarkMode = mutableStateOf(true)
    private var isFromCache by mutableStateOf(false)
    private var lastLocation by mutableStateOf<Location?>(null)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            Log.d(TAG, "Location Permission granted")
            startLocationUpdates()
        }
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            Log.d(TAG, "Bluetooth Permission granted")
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryPct.value = (level * 100 / scale.toFloat()).toInt()
                
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL
                
                val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                tempCelsius.value = temp / 10f
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        setupLocationCallback()
        checkAndRequestPermissions()

        setContent {
            HudScreen(
                currentSpeedMph = speedMph.value,
                speedLimit = currentLimit.value,
                currentRoadName = roadName.value,
                heading = bearing.value,
                batteryLevel = batteryPct.value,
                isBatteryCharging = isCharging.value,
                thermalTemp = tempCelsius.value,
                isAutoDarkMode = autoDarkMode.value,
                isFromCache = isFromCache,
                onRefresh = { refreshRoadData() } 
            )
        }
    }

    private fun refreshRoadData() {
        lastLocation?.let { 
            CoroutineScope(Dispatchers.Main).launch {
                val info = SpeedLimitRepo.getRoadData(this@MainActivity, it.latitude, it.longitude, speedMph.value, forceRefresh = true)
                info.speedLimit?.let { currentLimit.value = it }
                info.roadName?.let { roadName.value = it }
                isFromCache = info.isFromCache
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
        sensorManager.unregisterListener(this)
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
        Log.d(TAG, "Maps SDK Initialized: $renderer")
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastLocation = location
                speedMph.value = (location.speed * 2.23694).toInt()
                if (location.hasBearing()) {
                    bearing.value = location.bearing
                }

                CoroutineScope(Dispatchers.Main).launch {
                    val info = SpeedLimitRepo.getRoadData(this@MainActivity, location.latitude, location.longitude, speedMph.value)
                    info.speedLimit?.let { currentLimit.value = it }
                    info.roadName?.let { roadName.value = it }
                    isFromCache = info.isFromCache
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(250)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            if (autoDarkMode.value && lux > 20) { 
                autoDarkMode.value = false
            } else if (!autoDarkMode.value && lux < 10) {
                autoDarkMode.value = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
