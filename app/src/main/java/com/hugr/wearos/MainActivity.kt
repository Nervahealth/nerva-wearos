package com.hugr.wearos

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * HUGR Labs — Build 28 (Validation-Ready)
 *
 * This MainActivity:
 * 1. Requests all required permissions
 * 2. Starts HealthSensorService (Samsung SDK → sensor data broadcasts)
 * 3. Starts BleGattService (receives broadcasts → BLE GATT → phone app)
 * 4. Displays live sensor values on screen (diagnostic view)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView

    private val requiredPermissions = mutableListOf(
        Manifest.permission.BODY_SENSORS,
        "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA",
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR BUILD 28\nStarting services..."
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
        }
        layout.addView(statusText)

        logText = TextView(this).apply {
            text = ""
            textSize = 9f
            setTextColor(android.graphics.Color.GREEN)
            setPadding(0, 8, 0, 0)
        }

        scrollView = ScrollView(this)
        scrollView.addView(logText)
        layout.addView(scrollView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(layout)

        appendLog("App started.")
        appendLog("Model: ${Build.MODEL}")
        appendLog("SDK: ${Build.VERSION.SDK_INT}")
        appendLog("Build: 28 (Validation-Ready)")
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            appendLog("All permissions granted!")
            startServices()
        } else {
            appendLog("Requesting ${missingPermissions.size} permissions...")
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }

            if (denied.isEmpty()) {
                appendLog("All permissions granted!")
            } else {
                val deniedNames = denied.map { it.first.substringAfterLast('.') }
                appendLog("DENIED: $deniedNames")
            }
            startServices()
        }
    }

    private fun startServices() {
        appendLog("Starting BLE GATT service...")
        val bleIntent = Intent(this, BleGattService::class.java)
        startService(bleIntent)
        appendLog("BLE GATT service started")

        appendLog("Starting Health Sensor service...")
        val sensorIntent = Intent(this, HealthSensorService::class.java).apply {
            action = HealthSensorService.ACTION_START_TRACKING
        }
        startService(sensorIntent)
        appendLog("Health Sensor service started")

        statusText.text = "HUGR BUILD 28\nServices running"
        appendLog("=== SERVICES ACTIVE ===")
        appendLog("Waiting for sensor data + BLE connection...")
    }

    // ─── Listen for sensor data broadcasts (diagnostic display) ────────────────

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            runOnUiThread { appendLog(status) }
        }
    }

    private val edaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val conductance = intent?.getFloatExtra("conductance", 0f) ?: return
            runOnUiThread { appendLog("EDA: ${conductance} µS") }
        }
    }

    private val ibiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val heartRate = intent?.getIntExtra("heartRate", 0) ?: return
            val ibiValues = intent.getIntArrayExtra("ibiValues") ?: intArrayOf()
            runOnUiThread {
                appendLog("HR: ${heartRate} bpm | IBI: ${ibiValues.joinToString(",")}")
            }
        }
    }

    private val accelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getIntExtra("x", 0) ?: return
            val y = intent.getIntExtra("y", 0)
            val z = intent.getIntExtra("z", 0)
            // Only log every 10th accel reading to avoid flooding
            accelCount++
            if (accelCount % 10 == 0) {
                runOnUiThread { appendLog("Accel: x=$x y=$y z=$z") }
            }
        }
    }
    private var accelCount = 0

    override fun onResume() {
        super.onResume()
        registerReceiver(statusReceiver, IntentFilter(HealthSensorService.ACTION_STATUS_UPDATE), RECEIVER_NOT_EXPORTED)
        registerReceiver(edaReceiver, IntentFilter("com.hugr.wearos.EDA_DATA"), RECEIVER_NOT_EXPORTED)
        registerReceiver(ibiReceiver, IntentFilter("com.hugr.wearos.IBI_DATA"), RECEIVER_NOT_EXPORTED)
        registerReceiver(accelReceiver, IntentFilter("com.hugr.wearos.ACCEL_DATA"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(edaReceiver)
            unregisterReceiver(ibiReceiver)
            unregisterReceiver(accelReceiver)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logLine = "[$time] $message\n"
        logText.append(logLine)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
