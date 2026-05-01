package com.achlvv.application1

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatDelegate
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.lifecycle.ViewModelProvider
import android.bluetooth.BluetoothGattDescriptor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class MainActivity : AppCompatActivity() {
    // Create a launcher to handle the human clicking "Allow" or "Deny"
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()

    ) { permissions ->
        // Check if the user granted everything we asked for
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth Permissions Granted! 🔎", Toast.LENGTH_SHORT).show()
            startBleScan()
        } else {
            Toast.makeText(this, "Bluetooth is required to connect to the sensor.", Toast.LENGTH_LONG).show()
        }
    }
    // Get the Android Bluetooth radio tools
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var bluetoothGatt: BluetoothGatt? = null
    // 2. Create the radar listener that checks every device it finds
    private val sharedViewModel by lazy { ViewModelProvider(this)[SharedViewModel::class.java] }
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // Grab the name of the device it just found
            val deviceName = result.device.name ?: result.scanRecord?.deviceName

            if (deviceName == "SpeedoData AQI") {
                Log.d("BLE_SCAN", "FOUND IT! MAC Address: ${result.device.address}")
                Toast.makeText(this@MainActivity, "Found AQI device", Toast.LENGTH_SHORT).show()

                // Found Arduino, so stop scanning to save phone battery!
                bleScanner.stopScan(this)

                bluetoothGatt = result.device.connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }
    // The Walkie-Talkie Operator that talks to the Arduino
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Successfully connected to AQI device")
                sharedViewModel.isConnected.postValue(true) // Tell UI we are online!

                // Ask the Arduino to show us its folders
                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from AQI device")
                sharedViewModel.isConnected.postValue(false) // Tell UI we dropped!


                gatt.close()
                bluetoothGatt = null
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection lost. Reconnecting...", Toast.LENGTH_SHORT).show()
                    startBleScan()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Found the AQI device's data folders! 📂")
                val service = gatt.getService(UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214"))

                // SUBSCRIBE TO THE AQI FOLDER
                val aqiChar = service?.getCharacteristic(UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214"))
                if (aqiChar != null) {
                    gatt.setCharacteristicNotification(aqiChar, true)
                    // This specific UUID is the universal Bluetooth code for "Turn on Notifications"
                    val descriptor = aqiChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }


                // START THE DAISY CHAIN TO GET THE INITIAL DATA!
                val statusChar = service?.getCharacteristic(UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214"))
                if (statusChar != null) {
                    gatt.readCharacteristic(statusChar)
                }
            }
        }
        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid.toString().uppercase() == "19B10002-E8F2-537E-4F6C-D104768A1214") {
                Log.d("BLE", "Device's timer finished! Fetching fresh data...")

                // Restart the Daisy Chain to grab all the new numbers
                val service = gatt.getService(UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214"))
                val statusChar = service?.getCharacteristic(UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214"))
                if (statusChar != null) {
                    gatt.readCharacteristic(statusChar)
                }
            }
        }

        @SuppressLint("MissingPermission", "Deprecated")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                val uuid = characteristic.uuid.toString().uppercase()
                val rawBytes = characteristic.value
                val service = characteristic.service // Gives us access to the other folders

                when (uuid) {
                    "19B10001-E8F2-537E-4F6C-D104768A1214" -> {
                        val sensorStatus = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).int
                        Log.d("BLE_DATA", "1. Status: $sensorStatus (0=Warming Up, 1=Ready)")
                        sharedViewModel.status.postValue(sensorStatus)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10002-E8F2-537E-4F6C-D104768A1214" -> {
                        val aqi = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).int
                        Log.d("BLE_DATA", "2. Overall AQI: $aqi")
                        sharedViewModel.overallAqi.postValue(aqi)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10003-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10003-E8F2-537E-4F6C-D104768A1214" -> {
                        val pm25 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).float
                        Log.d("BLE_DATA", "3. PM 2.5: $pm25 µg/m³")
                        sharedViewModel.pm25.postValue(pm25)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10004-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10004-E8F2-537E-4F6C-D104768A1214" -> {
                        val pm10 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).float
                        Log.d("BLE_DATA", "4. PM 10: $pm10 µg/m³")
                        sharedViewModel.pm10.postValue(pm10)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10005-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10005-E8F2-537E-4F6C-D104768A1214" -> {
                        val co2 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).int
                        Log.d("BLE_DATA", "5. CO2: $co2 ppm")
                        sharedViewModel.co2.postValue(co2)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10006-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10006-E8F2-537E-4F6C-D104768A1214" -> {
                        val so2 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).float
                        Log.d("BLE_DATA", "6. SO2: $so2 µg/m³")
                        sharedViewModel.so2.postValue(so2)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10007-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10007-E8F2-537E-4F6C-D104768A1214" -> {
                        val no2 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).float
                        Log.d("BLE_DATA", "7. NO2: $no2 µg/m³")
                        sharedViewModel.no2.postValue(no2)
                        gatt.readCharacteristic(service.getCharacteristic(UUID.fromString("19B10008-E8F2-537E-4F6C-D104768A1214")))
                    }
                    "19B10008-E8F2-537E-4F6C-D104768A1214" -> {
                        val o3 = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).float
                        sharedViewModel.o3.postValue(o3)


                        val currentSnapshot = SensorReading(
                            timestamp = System.currentTimeMillis(),
                            aqi = sharedViewModel.overallAqi.value?.toFloat() ?: 0f,
                            no2 = sharedViewModel.no2.value ?: 0f,
                            o3 = o3,
                            so2 = sharedViewModel.so2.value ?: 0f,
                            pm25 = sharedViewModel.pm25.value ?: 0f,
                            pm10 = sharedViewModel.pm10.value ?: 0f,
                            co2 = sharedViewModel.co2.value?.toFloat() ?: 0f
                        )

                        // Add it to the history book
                        sharedViewModel.readingHistory.add(currentSnapshot)
                        val jsonToSave = Gson().toJson(sharedViewModel.readingHistory)
                        // Save itin the background
                        val editor = getSharedPreferences("AirQualityApp", Context.MODE_PRIVATE).edit()
                        editor.putString("sensor_history", jsonToSave)
                        editor.apply()
                    }
                }
            }
        }
    }

    // The function to physically turn on the radar
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please turn on your phone's Bluetooth!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Scanning for Air Quality Sensor...", Toast.LENGTH_SHORT).show()
        bleScanner.startScan(scanCallback)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Figure out which permissions this specific phone needs
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // For older Android versions
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissionLauncher.launch(requiredPermissions)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Handle navigation clicks
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    replaceFragment(DashboardFragment())
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        // Check if user has saved data
        if (savedInstanceState == null) {
            val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val savedAge = sharedPreferences.getString("USER_AGE", null)

            if (savedAge == null) {
                // First time user: Hide the nav bar and show Welcome Screen
                bottomNav.visibility = View.GONE
                replaceFragment(WelcomeFragment())
            } else {
                // Returning user: Show nav bar and go straight to Dashboard
                bottomNav.visibility = View.VISIBLE
                replaceFragment(DashboardFragment())
            }
        }
        // --- DATA PERSISTENCE: LOAD HISTORY ON BOOT ---
        val prefs = getSharedPreferences("AirQualityApp", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("sensor_history", null)

        if (savedJson != null) {
            // Translate the JSON text back into a Kotlin List!
            val type = object : TypeToken<MutableList<SensorReading>>() {}.type
            val savedHistory: MutableList<SensorReading> = Gson().fromJson(savedJson, type)

            // SELF-CLEANING: Delete data older than 7 days to save phone storage
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            savedHistory.removeAll { it.timestamp < oneWeekAgo }

            // Load it into the active bulletin board
            sharedViewModel.readingHistory.clear()
            sharedViewModel.readingHistory.addAll(savedHistory)

            Log.d("HISTORY", "Loaded ${savedHistory.size} historical points from storage!")
        }
    }

    // A function to swap fragments
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    fun showBottomNav() {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).visibility = View.VISIBLE
    }
}