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
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var startupBreadcrumbRunId = causalComponentInstanceId.toString()
    private lateinit var statusText: TextView
    private lateinit var evidenceText: TextView
    private lateinit var scrollView: ScrollView
    private var receiverRegistered = false
    private var build46Baseline: Build46SourceBaseline? = null
    private val firstFrameCoordinator = FirstFrameStartupCoordinator()
    private var firstFramePresentation = FirstFramePresentation.localStarting()
    private var permissionDecisionReached = false
    private var startupScheduled = false
    private lateinit var startupBreadcrumbStore: StartupBreadcrumbStore
    private lateinit var normalStartupMarkerStore: NormalStartupMarkerStore
    private val startupRecoveryGate = BoundedNormalStartupGate()
    private var finalizedDeliveryRecoveryOnly = false
    private var finalizedDeliverySourceSessionId: String? = null
    private var finalizedRecoveryAttemptId: UUID? = null
    private var readinessRefreshActive = false
    private val readinessRefresh = object : Runnable {
        override fun run() {
            if (!readinessRefreshActive || isFinishing || isDestroyed) return
            if (finalizedDeliveryRecoveryOnly) renderEvidence()
            evidenceText.postDelayed(this, 1_000L)
        }
    }

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
        startupBreadcrumbStore = StartupBreadcrumbStore(this)
        normalStartupMarkerStore = NormalStartupMarkerStore(this)
        intent.getStringExtra(StartupBreadcrumbPlan.EXTRA_DIAGNOSTIC_LAUNCH_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { startupBreadcrumbRunId = it }
        recordStartupBreadcrumb(StartupBreadcrumbStage.ACTIVITY_ONCREATE_ENTER)
        recordNormalStartupMarker(NormalStartupStage.UI_REACHED)
        createEvidenceLayout()
        recordStartupBreadcrumb(StartupBreadcrumbStage.FIRST_FRAME_LAYOUT_ATTACHED)
        renderFirstFrame(firstFrameCoordinator.firstFrame())
        recordStartupBreadcrumb(StartupBreadcrumbStage.FIRST_FRAME_PRESENTATION_SET)
        scheduleStartupAfterFirstFrame()
    }

    private fun createEvidenceLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR\nLocal startup check pending"
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

    private fun scheduleStartupAfterFirstFrame() {
        if (startupScheduled) return
        startupScheduled = true
        lateinit var firstDrawListener: ViewTreeObserver.OnDrawListener
        firstDrawListener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (!startupScheduled) return
                startupScheduled = false
                recordStartupBreadcrumb(StartupBreadcrumbStage.FIRST_DRAW_OBSERVED)
                recordNormalStartupMarker(NormalStartupStage.FIRST_DRAW_OBSERVED)
                evidenceText.post {
                    evidenceText.viewTreeObserver
                        .takeIf { it.isAlive }
                        ?.removeOnDrawListener(firstDrawListener)
                    beginStartupAfterFirstFrame()
                }
            }
        }
        evidenceText.viewTreeObserver.addOnDrawListener(firstDrawListener)
    }

    private fun beginStartupAfterFirstFrame() {
        if (isFinishing || isDestroyed) return
        if (finalizedDeliveryRecoveryOnly) {
            startFinalizedDeliveryRecovery()
            return
        }

        renderFirstFrame(firstFrameCoordinator.freshScopePreparing())
        Thread({ probeRetainedFinalizedDeliveryInBackground() }, "HUGR-RetainedFinalizedDeliveryProbe").start()
    }

    /**
     * Opens only the isolated fresh v2 journal after first draw. Durable finalized
     * source manifests—not the overwriteable startup marker—decide whether this
     * launch is a GATT-only delivery recovery. The ordinary path remains blocked
     * until the probe proves no retained finalized session is available.
     */
    private fun probeRetainedFinalizedDeliveryInBackground() {
        val target = runCatching {
            RetainedFinalizedDeliveryRecovery.select(WatchSourceRuntime.journal(applicationContext))
        }.getOrElse { failure ->
            runOnUiThread { completeFreshOrdinaryScopeFailure(failure) }
            return
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (target != null) {
                finalizedDeliveryRecoveryOnly = true
                finalizedDeliverySourceSessionId = target.sourceSessionId.toString()
                statusText.text = "HUGR\nFinalized delivery recovery · standard GATT only"
                evidenceText.text = "Retained source session ${target.sourceSessionId} is finalized. No sensing or new source records are started; Phone resume must verify manifest equality and acknowledge the exact terminal hash."
                startFinalizedDeliveryRecovery()
                return@runOnUiThread
            }
            // This candidate is a delivery-recovery line, not a fresh-recording
            // admission. If retained v2 evidence is absent, stop before the
            // permission or health-service paths rather than starting sensing.
            recordNormalStartupMarker(NormalStartupStage.RETAINED_DELIVERY_TARGET_UNAVAILABLE)
            statusText.text = "HUGR\nFinalized delivery recovery unavailable"
            evidenceText.text = "No retained unacknowledged finalized v2 session was selected. No sensing, new source records, acknowledgement, deletion, egress, or legacy/frozen-root operation was started."
        }
    }

    private fun beginFreshOrdinaryScopeAfterRetainedDeliveryProbe() {
        startupRecoveryGate.deferFreshScope()
        recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_DEFERRED)
        renderFirstFrame(firstFrameCoordinator.freshScopePreparing())
        Thread({ prepareFreshOrdinaryScopeInBackground() }, "HUGR-FreshOrdinaryScope").start()
    }

    private fun prepareFreshOrdinaryScopeInBackground() {
        startupRecoveryGate.beginFreshScope()
        recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_PREPARING)
        recordStartupBreadcrumb(StartupBreadcrumbStage.RECORDER_INITIALIZATION_ENTER)
        val journal = runCatching { WatchSourceRuntime.journal(applicationContext) }.getOrElse { failure ->
            recordStartupBreadcrumb(StartupBreadcrumbStage.RECORDER_INITIALIZATION_FAILURE)
            recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_FAILED)
            completeFreshOrdinaryScopeFailure(failure)
            return
        }
        if (!journal.preflight().eligible) {
            recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_FAILED)
            completeFreshOrdinaryScopeFailure(
                SourceJournalCorruptionException("Fresh ordinary source journal is not eligible for startup"),
            )
            return
        }
        runCatching { WatchCausalRuntime.recorder(applicationContext) }.getOrElse { failure ->
            recordStartupBreadcrumb(StartupBreadcrumbStage.RECORDER_INITIALIZATION_FAILURE)
            recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_FAILED)
            completeFreshOrdinaryScopeFailure(failure)
            return
        }
        recordCausal(CausalEventCode.ACTIVITY_CREATED)

        startupRecoveryGate.markFreshScopeReady()
        recordNormalStartupMarker(NormalStartupStage.FRESH_SCOPE_READY)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            build46Baseline = null
            renderFirstFrame(firstFrameCoordinator.freshScopeReady())
            requestForegroundPermissions()
        }
    }

    private fun completeFreshOrdinaryScopeFailure(failure: Throwable) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            startupRecoveryGate.markFailed()
            build46Baseline = null
            renderFirstFrame(firstFrameCoordinator.onRecoveryFailure(failure))
        }
    }

    private fun renderFirstFrame(presentation: FirstFramePresentation) {
        firstFramePresentation = presentation
        statusText.text = "HUGR\n${presentation.headline}"
        evidenceText.text = buildString {
            append(presentation.detail)
            presentation.baselineText?.let { append('\n').append(it) }
            presentation.failureClass?.let { append("\nFailure class: ").append(it.name) }
        }
    }

    private fun recordStartupBreadcrumb(stage: StartupBreadcrumbStage) {
        startupBreadcrumbStore.record(stage, startupBreadcrumbRunId)
    }

    private fun recordNormalStartupMarker(stage: NormalStartupStage) {
        normalStartupMarkerStore.record(startupBreadcrumbRunId, stage)
    }

    private fun requestForegroundPermissions() {
        if (!startupRecoveryGate.permitsPermissionPath()) {
            recordNormalStartupMarker(NormalStartupStage.SERVICE_START_BLOCKED)
            renderFirstFrame(firstFrameCoordinator.recoveryFailed())
            return
        }
        permissionDecisionReached = true
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
            PERMISSION_REQUEST_CODE -> {
                if (allPermissionsGranted(grantResults)) checkBackgroundPermission() else onPermissionDenied()
            }
            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                if (allPermissionsGranted(grantResults)) startAllServices() else onPermissionDenied()
            }
        }
        renderEvidence()
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean =
        grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

    private fun onPermissionDenied() {
        permissionDecisionReached = false
        recordNormalStartupMarker(NormalStartupStage.PERMISSION_DENIED)
        renderFirstFrame(firstFrameCoordinator.permissionUnavailable())
    }

    private fun startAllServices() {
        if (!startupRecoveryGate.permitsServiceStart()) {
            recordNormalStartupMarker(NormalStartupStage.SERVICE_START_BLOCKED)
            renderFirstFrame(firstFrameCoordinator.recoveryFailed())
            return
        }
        recordNormalStartupMarker(NormalStartupStage.SERVICE_START_REQUESTED)
        recordCausal(CausalEventCode.SERVICES_START_REQUESTED)
        startService(Intent(this, BleGattService::class.java))
        val sensorIntent = Intent(this, HealthSensorService::class.java).apply {
            action = HealthSensorService.ACTION_START_BOUNDED_ORDINARY_RUN
            putExtra(
                HealthSensorService.EXTRA_BOUNDED_RUN_DURATION_MS,
                BoundedOrdinaryRunPolicy.SHORT_ADMISSION_DURATION_MS,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sensorIntent)
        } else {
            startService(sensorIntent)
        }
        statusText.text = "HUGR\nServices active · bounded resume replay"
        renderEvidence()
    }

    /**
     * Starts only the standard GATT peripheral for a retained, already-finalized
     * fresh run. It deliberately does not request permissions, prepare a new
     * bounded Health service, alter the BOUNDED_RUN_FINALIZED marker, or activate
     * any egress capability. The resumed Phone owns the normal manifest/hash/ack
     * sequence; the GATT service records terminal completion only after that ack.
     */
    private fun startFinalizedDeliveryRecovery() {
        val sourceSessionId = finalizedDeliverySourceSessionId ?: return
        val attemptId = UUID.randomUUID()
        finalizedRecoveryAttemptId = attemptId
        FinalizedRecoveryReadiness.request(attemptId)
        try {
            startForegroundService(
                Intent(this, BleGattService::class.java).apply {
                    putExtra(BleGattService.EXTRA_FINALIZED_DELIVERY_RECOVERY_ONLY, true)
                    putExtra(BleGattService.EXTRA_FINALIZED_DELIVERY_SOURCE_SESSION_ID, sourceSessionId)
                    putExtra(BleGattService.EXTRA_FINALIZED_RECOVERY_ATTEMPT_ID, attemptId.toString())
                },
            )
        } catch (failure: Exception) {
            FinalizedRecoveryReadiness.rejectRequest(attemptId)
            recordCausal(
                CausalEventCode.FINALIZED_RECOVERY_STOP_REQUESTED,
                reasonCode = FinalizedRecoveryStopReason.SERVICE_START_REJECTED.code,
            )
            renderEvidence()
            return
        }
        statusText.text = "HUGR\nRetained delivery PREPARING · do not connect Phone yet"
        startReadinessRefresh()
    }

    private fun startReadinessRefresh() {
        if (readinessRefreshActive) return
        readinessRefreshActive = true
        evidenceText.post(readinessRefresh)
    }

    private val causalUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                if (permissionDecisionReached || finalizedDeliveryRecoveryOnly) renderEvidence()
            }
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
        if (finalizedDeliveryRecoveryOnly) startReadinessRefresh()
        if (permissionDecisionReached || finalizedDeliveryRecoveryOnly) renderEvidence() else renderFirstFrame(firstFramePresentation)
    }

    override fun onPause() {
        readinessRefreshActive = false
        if (::evidenceText.isInitialized) evidenceText.removeCallbacks(readinessRefresh)
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
        if (!permissionDecisionReached && !finalizedDeliveryRecoveryOnly) {
            renderFirstFrame(firstFramePresentation)
            return
        }
        val recorder = WatchCausalRuntime.recorder(this)
        val events = recorder.events()
        val baseline = build46Baseline
        val latest = { code: CausalEventCode -> events.lastOrNull { it.code == code } }
        val started = events.filter { it.code == CausalEventCode.TRACKER_STARTED }.mapNotNull { it.stream }.toSet()
        val callbacks = events.filter { it.code == CausalEventCode.FIRST_CALLBACK }.mapNotNull { it.stream }.toSet()
        val appends = events.filter { it.code == CausalEventCode.FIRST_APPEND }.mapNotNull { it.stream }.toSet()
        val integrity = recorder.integrity().name
        val failureClass = WatchCausalRuntime.failureClass()
        val recoverySnapshot = if (finalizedDeliveryRecoveryOnly) {
            FinalizedRecoveryReadiness.snapshot(finalizedRecoveryAttemptId, SystemClock.elapsedRealtime())
        } else null
        val recoveryDisplay = if (finalizedDeliveryRecoveryOnly &&
            integrity != CausalRecorderIntegrity.OK.name &&
            recoverySnapshot?.display != FinalizedRecoveryDisplay.STOPPED
        ) {
            FinalizedRecoveryDisplay.NOT_CONFIRMED
        } else recoverySnapshot?.display
        if (finalizedDeliveryRecoveryOnly) {
            val reason = recoverySnapshot?.reason?.name ?: "UNKNOWN"
            statusText.text = when (recoveryDisplay) {
                FinalizedRecoveryDisplay.READY -> "HUGR\nCURRENT RECOVERY ADVERTISING READY"
                FinalizedRecoveryDisplay.STOPPED -> if (recoverySnapshot?.reason == FinalizedRecoveryStopReason.EXACT_ACK_COMPLETED) {
                    "HUGR\nExact delivery ACK accepted · service stopped"
                } else "HUGR\nRETAINED DELIVERY STOPPED · $reason"
                FinalizedRecoveryDisplay.PREPARING -> "HUGR\nRetained delivery PREPARING · do not connect"
                else -> "HUGR\nRecovery readiness NOT CONFIRMED · do not connect"
            }
        }
        val bleState = when {
            latest(CausalEventCode.GATT_DISCONNECTED)?.eventSequence.orZero() > latest(CausalEventCode.GATT_CONNECTED)?.eventSequence.orZero() -> "DISCONNECTED"
            latest(CausalEventCode.GATT_CONNECTED) != null -> "CONNECTED"
            else -> "WAITING"
        }
        val gattReadiness = if (finalizedDeliveryRecoveryOnly) {
            when (recoveryDisplay) {
                FinalizedRecoveryDisplay.READY -> "CURRENT ADVERTISING READY"
                FinalizedRecoveryDisplay.STOPPED -> "STOPPED ${recoverySnapshot?.reason?.name ?: "UNKNOWN"}"
                FinalizedRecoveryDisplay.PREPARING -> "PREPARING"
                else -> "NOT CONFIRMED"
            }
        } else when {
            latest(CausalEventCode.GATT_ADVERTISING_FAILED)?.eventSequence.orZero() > latest(CausalEventCode.GATT_ADVERTISING_READY)?.eventSequence.orZero() -> "ADVERTISING FAILED"
            latest(CausalEventCode.GATT_ADVERTISING_READY) != null -> "ADVERTISING READY"
            latest(CausalEventCode.GATT_SERVICE_FAILED)?.eventSequence.orZero() > latest(CausalEventCode.GATT_SERVICE_READY)?.eventSequence.orZero() -> "SERVICE FAILED"
            latest(CausalEventCode.GATT_SERVICE_READY) != null -> "SERVICE READY"
            latest(CausalEventCode.GATT_SERVER_OPENED) != null -> "SERVER OPEN"
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
            if (finalizedDeliveryRecoveryOnly) {
                append("CURRENT RECOVERY $gattReadiness (attempt ${finalizedRecoveryAttemptId?.toString()?.take(8) ?: "none"})\n")
                append("Historical connection, MTU, resume and ACK entries below are NOT current readiness.\n")
            } else {
                append("BLE $bleState · $gattReadiness L${events.maxOfOrNull { it.bleLineage } ?: 0L} MTU=$mtu CCCD=$cccd\n")
                append("RESUME=${resume?.recordIndexStart ?: 0L}->${resume?.recordIndexEnd ?: 0L} ")
                append("ABORT=${abort?.reasonCode ?: 0}\n")
            }
            append(if (finalizedDeliveryRecoveryOnly) "--- HISTORY — NOT LIVE READINESS ---\n" else "--- LAST 20 ---\n")
            CausalFlightFormatter.format(events, 20).forEach { append(it).append('\n') }
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun Long?.orZero(): Long = this ?: 0L
    private fun flag(value: Boolean): Char = if (value) 'Y' else '-'
}
