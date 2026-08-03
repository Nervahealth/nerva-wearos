package com.hugr.wearos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView

    private val requiredPermissions = mutableListOf(
        Manifest.permission.BODY_SENSORS,
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

        // Simple layout created programmatically
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR WearOS App\nChecking permissions..."
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
        }
        layout.addView(statusText)

        logText = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(android.graphics.Color.GREEN)
            setPadding(0, 16, 0, 0)
        }

        scrollView = ScrollView(this)
        scrollView.addView(logText)
        layout.addView(scrollView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(layout)

        appendLog("App started.")
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
                statusText.text = "HUGR WearOS App\nPermissions OK. Starting..."
                startServices()
            } else {
                val deniedNames = denied.map { it.first.substringAfterLast('.') }
                appendLog("Denied: $deniedNames")
                statusText.text = "HUGR WearOS App\nSome permissions denied.\nPlease grant in Settings."
                // Try starting anyway - some sensors might still work
                startServices()
            }
        }
    }

    private fun startServices() {
        statusText.text = "HUGR WearOS App\nStarting sensors..."
        appendLog("Starting HealthSensorService...")

        try {
            val sensorIntent = Intent(this, HealthSensorService::class.java)
            startService(sensorIntent)
            appendLog("HealthSensorService started.")
        } catch (e: Exception) {
            appendLog("ERROR starting sensors: ${e.message}")
        }

        try {
            val bleIntent = Intent(this, BleGattService::class.java)
            startService(bleIntent)
            appendLog("BleGattService started.")
        } catch (e: Exception) {
            appendLog("ERROR starting BLE: ${e.message}")
        }

        try {
            val hapticIntent = Intent(this, HapticService::class.java)
            startService(hapticIntent)
            appendLog("HapticService started.")
        } catch (e: Exception) {
            appendLog("ERROR starting haptics: ${e.message}")
        }

        statusText.text = "HUGR WearOS App\nServices running."
        appendLog("All services initialized.")
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logLine = "[$time] $message\n"
        logText.append(logLine)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
