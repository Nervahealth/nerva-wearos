package com.hugr.wearos

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/**
 * HUGR Labs — Build 48w MTU-readiness recovery with causal flight recorder.
 *
 * The activity shows fixed-field watch-local diagnostic evidence only. Sensor
 * values and source payloads never enter this UI.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val BACKGROUND_PERMISSION_REQUEST_CODE = 101
    }

    private val causalComponentInstanceId = UUID.randomUUID()
    private lateinit var statusText: TextView
    private lateinit var evidenceText: TextView
    private lateinit var scrollView: ScrollView
    private var receiverRegistered = false
    private var build46Baseline: Build46SourceBaseline? = null

    private val foregroundPermissions = mutableListOf(
        Manifest.permission.BODY_SENSORS,
        "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA",
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createEvidenceLayout()

        WatchCausalRuntime.recorder(this)
        recordCausal(CausalEventCode.ACTIVITY_CREATED)
        captureBuild46Baseline()
        recordPermissionSnapshot()
        renderEvidence()
        requestForegroundPermissions()
    }

    private fun createEvidenceLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR BUILD 51w\n0.51.0-resume-scaling-candidate"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        layout.addView(statusText)

        layout.addView(Switch(this).apply {
            text = "TEST ONLY: keep screen awake"
            textSize = 9f
            setTextColor(Color.YELLOW)
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })

        evidenceText = TextView(this).apply {
            textSize = 8f
            setTextColor(Color.GREEN)
            setPadding(0, 8, 0, 0)
        }
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                evidenceText,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        layout.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(layout)
    }

    private fun captureBuild46Baseline() {
        runCatching {
            Build46BaselineStore(File(filesDir, "build47_causal_flight_recorder"))
                .captureOnce { Build46SourceBaseline.from(WatchSourceRuntime.journal(this)) }
        }.onSuccess { baseline ->
            build46Baseline = baseline
            val recorder = WatchCausalRuntime.recorder(this)
            if (recorder.events().none { it.code == CausalEventCode.B46_BASELINE_SUMMARY }) {
                recordCausal(
                    CausalEventCode.B46_BASELINE_SUMMARY,
                    arg0 = baseline.retainedSessionCount.toLong(),
                    arg1 = baseline.latestRecordIndex,
                )
                SourceDeliveryState.entries.forEach { state ->
                    recordCausal(
                        CausalEventCode.B46_BASELINE_SUMMARY,
                        arg0 = baseline.deliveryCounts[state] ?: 0L,
                        reasonCode = state.ordinal + 1,
                    )
                }
            }
        }.onFailure {
            build46Baseline = null
            recordCausal(CausalEventCode.RECORDER_DEGRADED, reasonCode = 1)
        }
    }

    private fun requestForegroundPermissions() {
        val missing = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            checkBackgroundPermission()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkBackgroundPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS_BACKGROUND,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(this, "Please allow 'All the time' sensor access", Toast.LENGTH_LONG).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
                    BACKGROUND_PERMISSION_REQUEST_CODE,
                )
                return false
            }
        }
        startAllServices()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        recordPermissionSnapshot()
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> checkBackgroundPermission()
            BACKGROUND_PERMISSION_REQUEST_CODE -> startAllServices()
        }
        renderEvidence()
    }

    private fun startAllServices() {
        recordCausal(CausalEventCode.SERVICES_START_REQUESTED)
        startService(Intent(this, BleGattService::class.java))
        val sensorIntent = Intent(this, HealthSensorService::class.java).apply {
            action = HealthSensorService.ACTION_START_TRACKING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sensorIntent)
        } else {
            startService(sensorIntent)
        }
        statusText.text = "HUGR BUILD 51w\nServices active · bounded resume replay"
        renderEvidence()
    }

    private val causalUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread { renderEvidence() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            registerReceiver(
                causalUpdateReceiver,
                IntentFilter(WatchCausalRuntime.ACTION_CAUSAL_EVENT_UPDATE),
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        renderEvidence()
    }

    override fun onPause() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(causalUpdateReceiver) }
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun recordPermissionSnapshot() {
        val permissions = foregroundPermissions + listOf(Manifest.permission.BODY_SENSORS_BACKGROUND)
        val mask = permissions.withIndex().fold(0L) { value, indexed ->
            if (ContextCompat.checkSelfPermission(this, indexed.value) == PackageManager.PERMISSION_GRANTED) {
                value or (1L shl indexed.index)
            } else {
                value
            }
        }
        recordCausal(CausalEventCode.PERMISSION_SNAPSHOT, arg0 = mask, arg1 = permissions.size.toLong())
    }

    private fun recordCausal(
        code: CausalEventCode,
        arg0: Long = 0L,
        arg1: Long = 0L,
        reasonCode: Int = CausalReasonCode.NONE.code,
    ) {
        WatchCausalRuntime.record(
            this,
            code,
            CausalComponentCode.ACTIVITY,
            causalComponentInstanceId,
            arg0 = arg0,
            arg1 = arg1,
            reasonCode = reasonCode,
        )
    }

    private fun renderEvidence() {
        if (!::evidenceText.isInitialized) return
        val recorder = WatchCausalRuntime.recorder(this)
        val events = recorder.events()
        val baseline = build46Baseline
        val latest = { code: CausalEventCode -> events.lastOrNull { it.code == code } }
        val started = events.filter { it.code == CausalEventCode.TRACKER_STARTED }.mapNotNull { it.stream }.toSet()
        val callbacks = events.filter { it.code == CausalEventCode.FIRST_CALLBACK }.mapNotNull { it.stream }.toSet()
        val appends = events.filter { it.code == CausalEventCode.FIRST_APPEND }.mapNotNull { it.stream }.toSet()
        val integrity = recorder.integrity().name
        val failureClass = WatchCausalRuntime.failureClass()
        val bleState = when {
            latest(CausalEventCode.GATT_DISCONNECTED)?.eventSequence.orZero() > latest(CausalEventCode.GATT_CONNECTED)?.eventSequence.orZero() -> "DISCONNECTED"
            latest(CausalEventCode.GATT_CONNECTED) != null -> "CONNECTED"
            else -> "WAITING"
        }
        val baselineText = if (baseline == null) {
            "B46 BASELINE UNAVAILABLE"
        } else {
            Build46BaselineFormatter.format(baseline)
        }
        val acquisition = CausalStreamCode.entries.joinToString(" ") { stream ->
            "${stream.name.take(3)}:${flag(started.contains(stream))}${flag(callbacks.contains(stream))}${flag(appends.contains(stream))}"
        }
        val mtu = latest(CausalEventCode.MTU_CHANGED)?.arg0 ?: 0L
        val cccd = when {
            latest(CausalEventCode.SOURCE_CCCD_DISABLED)?.eventSequence.orZero() > latest(CausalEventCode.SOURCE_CCCD_ENABLED)?.eventSequence.orZero() -> "OFF"
            latest(CausalEventCode.SOURCE_CCCD_ENABLED) != null -> "ON"
            else -> "-"
        }
        val resume = latest(CausalEventCode.RESUME_APPLIED)
        val abort = latest(CausalEventCode.ABORT_REQUESTED)

        evidenceText.text = buildString {
            append("RECORDER $integrity")
            if (failureClass != CausalRecorderFailureClass.NONE) append(" ${failureClass.name}")
            append('\n')
            append(baselineText).append('\n')
            append("ACQ T/C/A $acquisition\n")
            append("BLE $bleState L${events.maxOfOrNull { it.bleLineage } ?: 0L} MTU=$mtu CCCD=$cccd\n")
            append("RESUME=${resume?.recordIndexStart ?: 0L}->${resume?.recordIndexEnd ?: 0L} ")
            append("ABORT=${abort?.reasonCode ?: 0}\n")
            append("--- LAST 20 ---\n")
            CausalFlightFormatter.format(events, 20).forEach { append(it).append('\n') }
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun Long?.orZero(): Long = this ?: 0L
    private fun flag(value: Boolean): Char = if (value) 'Y' else '-'
}
