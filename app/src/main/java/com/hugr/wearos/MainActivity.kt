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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * HUGR Labs — Build 28d (Background-Capable)
 *
 * Based on Samsung's official tutorial (April 2026):
 * - Foreground service with foregroundServiceType="health"
 * - PARTIAL_WAKE_LOCK for CPU active when screen off
 * - BODY_SENSORS_BACKGROUND permission for background sensor access
 * - BLE GATT service for phone communication
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val BACKGROUND_PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private var accelCount = 0

    private val foregroundPermissions = mutableListOf(
        Manifest.permission.BODY_SENSORS,
        "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA",
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACTIVITY_RECOGNITION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR BUILD 40w\nBackground-capable"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
        }
        layout.addView(statusText)

        // Haptic Lab button
        val hapticLabBtn = android.widget.Button(this).apply {
            text = "HAPTIC LAB"
            textSize = 10f
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.parseColor("#00FF88"))
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, HapticLabActivity::class.java))
            }
        }
        layout.addView(hapticLabBtn)

        // Haptic Lab 2 button (System Path Investigation)
        val hapticLab2Btn = android.widget.Button(this).apply {
            text = "HAPTIC LAB 2 (System)"
            textSize = 10f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#FF6600"))
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, HapticLab2Activity::class.java))
            }
        }
        layout.addView(hapticLab2Btn)

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
            appendLog("Build: 40w (Haptic Lab)")
        requestForegroundPermissions()
    }

    // ─── Permission Flow (Samsung pattern: foreground first, then background) ──

    private fun requestForegroundPermissions() {
        val missing = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            appendLog("Foreground permissions OK")
            checkBackgroundPermission()
        } else {
            appendLog("Requesting ${missing.size} permissions...")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkBackgroundPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BODY_SENSORS_BACKGROUND
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                appendLog("Requesting BODY_SENSORS_BACKGROUND...")
                Toast.makeText(this, "Please allow 'All the time' sensor access", Toast.LENGTH_LONG).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
                    BACKGROUND_PERMISSION_REQUEST_CODE
                )
                return false
            }
            appendLog("Background sensor permission OK")
        }
        startAllServices()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val denied = permissions.zip(grantResults.toTypedArray())
                    .filter { it.second != PackageManager.PERMISSION_GRANTED }
                if (denied.isEmpty()) {
                    appendLog("All foreground permissions granted!")
                } else {
                    appendLog("DENIED: ${denied.map { it.first.substringAfterLast('.') }}")
                }
                checkBackgroundPermission()
            }
            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    appendLog("Background sensor permission GRANTED!")
                } else {
                    appendLog("Background permission DENIED (may stop when screen off)")
                }
                // Start services regardless — will work while screen on at minimum
                startAllServices()
            }
        }
    }

    // ─── Start Services ────────────────────────────────────────────────────────

    private fun startAllServices() {
        // 1. Start BLE GATT service
        appendLog("Starting BLE GATT service...")
        startService(Intent(this, BleGattService::class.java))
        appendLog("BLE GATT started")

        // 2. Start Health Sensor service as FOREGROUND (Samsung-documented pattern)
        appendLog("Starting Health Sensor service (foreground)...")
        val sensorIntent = Intent(this, HealthSensorService::class.java).apply {
            action = HealthSensorService.ACTION_START_TRACKING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sensorIntent)
        } else {
            startService(sensorIntent)
        }
        appendLog("Health Sensor service started")

        statusText.text = "HUGR BUILD 40w\nServices active"
        appendLog("=== SERVICES LAUNCHED ===")
        appendLog("Screen can turn off — data continues")
    }

    // ─── Receive status updates from HealthSensorService ───────────────────────

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            runOnUiThread { appendLog(status) }
        }
    }

    private val edaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val conductance = intent?.getFloatExtra("conductance", 0f) ?: return
            runOnUiThread { appendLog("EDA: ${String.format("%.4f", conductance)} µS") }
        }
    }

    private val ibiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val hr = intent?.getIntExtra("heartRate", 0) ?: return
            val ibi = intent.getIntArrayExtra("ibiValues") ?: intArrayOf()
            runOnUiThread { appendLog("HR: $hr bpm | IBI: ${ibi.joinToString(",")}") }
        }
    }

    private val accelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            accelCount++
            if (accelCount % 25 == 0) {
                val x = intent?.getIntExtra("x", 0) ?: return
                val y = intent.getIntExtra("y", 0)
                val z = intent.getIntExtra("z", 0)
                runOnUiThread { appendLog("Accel: x=$x y=$y z=$z") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(statusReceiver, IntentFilter(HealthSensorService.ACTION_STATUS_UPDATE), RECEIVER_EXPORTED)
        registerReceiver(edaReceiver, IntentFilter("com.hugr.wearos.EDA_DATA"), RECEIVER_EXPORTED)
        registerReceiver(ibiReceiver, IntentFilter("com.hugr.wearos.IBI_DATA"), RECEIVER_EXPORTED)
        registerReceiver(accelReceiver, IntentFilter("com.hugr.wearos.ACCEL_DATA"), RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(edaReceiver)
            unregisterReceiver(ibiReceiver)
            unregisterReceiver(accelReceiver)
        } catch (e: Exception) { /* ignore */ }
        // NOTE: Services keep running — only UI updates stop when screen off
    }

    // NOTE: Do NOT stop services in onDestroy — they must survive Activity lifecycle
    // User can stop via a button or the service times out after 10 hours

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logLine = "[$time] $message\n"
        logText.append(logLine)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
