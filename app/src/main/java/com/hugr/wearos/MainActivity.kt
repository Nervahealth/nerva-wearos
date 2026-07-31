package com.hugr.wearos

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView

class MainActivity : ComponentActivity() {
    private val TAG = "HUGR-WearOS"
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        logText = findViewById(R.id.log_text)

        Log.d(TAG, "MainActivity created")
        statusText.text = "HUGR WearOS App\nInitializing sensors..."

        // Start health sensor service
        startHealthSensorService()

        // Start BLE GATT server
        startBleGattServer()

        updateLog("App started. Sensors initializing...")
    }

    private fun startHealthSensorService() {
        Log.d(TAG, "Starting HealthSensorService")
        val intent = android.content.Intent(this, HealthSensorService::class.java)
        startService(intent)
    }

    private fun startBleGattServer() {
        Log.d(TAG, "Starting BLE GATT server")
        val intent = android.content.Intent(this, BleGattService::class.java)
        startService(intent)
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(System.currentTimeMillis())
            logText.append("[$timestamp] $message\n")
            Log.d(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
    }
}
