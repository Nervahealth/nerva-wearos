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
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private var healthTrackingService: HealthTrackingService? = null

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
            text = "HUGR DIAGNOSTIC BUILD\nChecking permissions..."
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
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            appendLog("All permissions granted!")
            startSamsungSDK()
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
            startSamsungSDK()
        }
    }

    private fun startSamsungSDK() {
        appendLog("Connecting to Samsung SDK...")
        statusText.text = "HUGR DIAGNOSTIC\nConnecting to Samsung SDK..."

        try {
            healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
            healthTrackingService?.connectService()
            appendLog("connectService() called OK")
        } catch (e: Exception) {
            appendLog("EXCEPTION: ${e.javaClass.simpleName}")
            appendLog("MSG: ${e.message}")
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            runOnUiThread {
                appendLog("=== SDK CONNECTED ===")
                statusText.text = "HUGR DIAGNOSTIC\nSDK Connected!"
                startTrackers()
            }
        }

        override fun onConnectionEnded() {
            runOnUiThread {
                appendLog("SDK connection ENDED")
            }
        }

        override fun onConnectionFailed(error: HealthTrackerException?) {
            runOnUiThread {
                appendLog("=== SDK FAILED ===")
                appendLog("Error: ${error?.message}")
                appendLog("HasResolution: ${error?.hasResolution()}")
                statusText.text = "HUGR DIAGNOSTIC\nSDK FAILED!"
            }
        }
    }

    private fun startTrackers() {
        val service = healthTrackingService ?: return

        try {
            val types = service.trackingCapability.supportHealthTrackerTypes
            appendLog("Supported types (${types.size}):")
            for (t in types) {
                appendLog("  - ${t.name}")
            }

            if (types.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                val hrTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                hrTracker.setEventListener(object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(data: MutableList<DataPoint>) {
                        val hr = data.firstOrNull()?.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Int
                        runOnUiThread { appendLog("HR: ${hr} bpm") }
                    }
                    override fun onError(error: HealthTracker.TrackerError) {
                        runOnUiThread { appendLog("HR ERROR: ${error.name}") }
                    }
                    override fun onFlushCompleted() {}
                })
                appendLog("HR tracker SET")
            }

            if (types.contains(HealthTrackerType.EDA_CONTINUOUS)) {
                val edaTracker = service.getHealthTracker(HealthTrackerType.EDA_CONTINUOUS)
                edaTracker.setEventListener(object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(data: MutableList<DataPoint>) {
                        val eda = data.firstOrNull()?.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE) as? Float
                        runOnUiThread { appendLog("EDA: ${eda} uS") }
                    }
                    override fun onError(error: HealthTracker.TrackerError) {
                        runOnUiThread { appendLog("EDA ERROR: ${error.name}") }
                    }
                    override fun onFlushCompleted() {}
                })
                appendLog("EDA tracker SET")
            } else {
                appendLog("!! EDA NOT in supported list !!")
            }

        } catch (e: Exception) {
            appendLog("TRACKER ERROR: ${e.javaClass.simpleName}")
            appendLog("MSG: ${e.message}")
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logLine = "[$time] $message\n"
        logText.append(logLine)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
