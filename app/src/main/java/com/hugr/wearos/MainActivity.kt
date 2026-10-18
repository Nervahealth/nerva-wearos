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
import android.widget.Button
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
    private lateinit var primaryText: TextView
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
    private var freshRequested = false
    private var freshUi = true
    private var readinessRefreshActive = false
    private var freshReadPending = false
    private var freshTerminalFailure = false
    private var freshAdmissionScheduled = false
    private var freshReadinessResolved = false
    private var freshReceiptDisplayed = false
    private var freshReceiptOnlyRequested = false
    private var retainedDeliveryRequested = false
    private var freshStartRecheckPending = false
    private var freshObservedReceipt: FreshRunObservedReceipt? = null
    private var freshObservedActiveRuntime: FreshRunUiIdentity? = null
    private var freshObservedReceiptOnly = false
    private var freshAdmissionIdentity: FreshRunUiIdentity? = null
    private val freshActionButtons = mutableListOf<Button>()
    private lateinit var freshStartButton: Button
    private lateinit var freshReceiptOnlyButton: Button
    private lateinit var retainedDeliveryButton: Button
    private fun freshActionBlocked(): Boolean {
        // Volatile process-local guards are not filesystem reads. Retain them
        // alongside the worker snapshot so an intervening service blocks a tap.
        return !freshReadinessResolved || freshTerminalFailure || freshReadPending || freshRequested || freshReceiptOnlyRequested ||
            FreshRunRuntime.session != null || FreshRunRuntime.receiptOnly ||
            freshObservedActiveRuntime != null || freshObservedReceiptOnly
    }
    private fun freshStartBlocked(): Boolean = freshActionBlocked() ||
        freshStartRecheckPending || !FreshRunReadinessPresenter.startPotentiallyPermitted(freshReadinessInput())
    private fun freshReceiptOnlyBlocked(): Boolean = freshActionBlocked() || freshReceiptOnlyRequested || freshObservedReceipt == null
    private fun retainedDeliveryBlocked(): Boolean = freshActionBlocked() || retainedDeliveryRequested || freshStartRecheckPending
    private fun latchFreshFailure(message: String) {
        if (freshTerminalFailure) return
        freshTerminalFailure = true
        freshActionButtons.forEach { it.isEnabled = false }
        statusText.text = "HUGR\n$message · STOP · no retry"
        primaryText.text = "The screen stopped locally. No new completion, sensor, timer, or hardware claim is made."
    }
    private fun freshReadinessInput() = FreshRunReadinessInput(
        receiptReadResolved = freshReadinessResolved,
        selectedReceipt = freshObservedReceipt,
        activeRuntime = freshObservedActiveRuntime,
        receiptOnly = freshObservedReceiptOnly,
        startRequestedOnThisScreen = freshRequested,
        admittedOnThisScreen = freshAdmissionIdentity,
    )
    private fun renderFreshReadiness() {
        val presentation = FreshRunReadinessPresenter.present(freshReadinessInput())
        setTextIfChanged(statusText, "HUGR\n${presentation.heading}")
        setTextIfChanged(primaryText, presentation.primary)
        val receipt = freshObservedReceipt
        val detail = if (receipt == null) {
            "No selected fresh receipt. Read status is current only for this screen refresh; no source root, sensor, or hardware state is inferred."
        } else {
            "Selected fresh receipt\nSource ${receipt.identity.sourceId}\nRun ${receipt.identity.runId}\nReceipt revision ${receipt.revision}; phase ${receipt.phase.name}; failure ${receipt.failure}\nRequested 5 minutes; supported/started ${receipt.supportedMask}/${receipt.startedMask}\nManifests ${receipt.manifestCount}; exact accepted ACKs ${receipt.acknowledgementCount}\nReceipt/admission state does not prove sensors, timer execution, or hardware activity. Phone durable report confirmation is separate. PPG live-only; no canonical PPG in this receipt."
        }
        setTextIfChanged(evidenceText, detail)
        if (!freshTerminalFailure) {
            freshStartButton.isEnabled = !freshStartBlocked()
            freshReceiptOnlyButton.isEnabled = !freshReceiptOnlyBlocked()
            retainedDeliveryButton.isEnabled = !retainedDeliveryBlocked()
        }
    }
    private fun setTextIfChanged(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }
    private fun FreshRunReceipt.observed() = FreshRunObservedReceipt(
        identity = FreshRunUiIdentity(sourceId, runId),
        revision = revision,
        phase = phase,
        failure = failure,
        supportedMask = supportedMask,
        startedMask = startedMask,
        manifestCount = manifests.size,
        acknowledgementCount = acks.size,
    )
    private data class FreshRuntimeRead(
        val receipt: FreshRunReceipt?,
        val activeRuntime: FreshRunUiIdentity?,
        val receiptOnly: Boolean,
    )
    private fun beginFreshStartRecheck() {
        if (freshStartBlocked()) return
        // A prior receipt can appear after the last passive poll and before a
        // tap. Re-read before permission/scope/admission work so UI eligibility
        // cannot bypass FreshRunRuntime.admit's unresolved-receipt guard.
        freshStartRecheckPending = true
        freshActionButtons.forEach { it.isEnabled = false }
        setTextIfChanged(statusText, "HUGR\nFRESH RECEIPT RECHECKING · START LOCKED")
        setTextIfChanged(primaryText, "Rechecking the selected receipt before a new Start. No fresh run is admitted.")
        renderEvidence()
    }
    private fun completeFreshStartRecheck() {
        if (!freshStartRecheckPending) return
        freshStartRecheckPending = false
        if (!FreshRunReadinessPresenter.startPotentiallyPermitted(freshReadinessInput())) {
            renderFreshReadiness()
            return
        }
        freshRequested = true
        renderFreshReadiness()
        WatchDiagnosticRuntime.begin(this, null, null)
        beginFreshOrdinaryScopeAfterRetainedDeliveryProbe()
    }
    private val readinessRefresh = object : Runnable {
        override fun run() {
            if (!readinessRefreshActive || isFinishing || isDestroyed) return
            if (freshUi || finalizedDeliveryRecoveryOnly) renderEvidence()
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
        WatchDiagnosticRuntime.begin(this, null, null)
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
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "HUGR\nLocal startup check pending"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        content.addView(statusText)
        primaryText = TextView(this).apply {
            text = "Reading the current fresh receipt off the UI thread. Start remains locked until that read resolves."
            textSize = 9f
            setTextColor(Color.LTGRAY)
            setPadding(0, 4, 0, 6)
        }
        content.addView(primaryText)
        evidenceText = TextView(this).apply {
            textSize = 8f
            setTextColor(Color.GREEN)
            setPadding(0, 6, 0, 8)
        }
        // Exact source/run/revision details belong above the actions so a compact
        // round display exposes the decision evidence before any control.
        content.addView(evidenceText)
        freshStartButton = Button(this).apply {
            text = "Start fresh 5 minutes"
            textSize = 10f
            minimumHeight = 0
            setPadding(8, 4, 8, 4)
            isEnabled = false
            freshActionButtons += this
            setOnClickListener {
                beginFreshStartRecheck()
            }
        }
        content.addView(freshStartButton)
        freshReceiptOnlyButton = Button(this).apply {
            text = "Serve current fresh receipt only"
            textSize = 10f
            minimumHeight = 0
            setPadding(8, 4, 8, 4)
            isEnabled = false
            freshActionButtons += this
            setOnClickListener {
                if (freshReceiptOnlyBlocked()) return@setOnClickListener
                freshReceiptOnlyRequested = true
                freshActionButtons.forEach { it.isEnabled = false }
                try {
                    startForegroundService(Intent(this@MainActivity, BleGattService::class.java).apply {
                        action = FreshRunRuntime.ACTION_SERVE_FRESH_RECEIPT_ONLY
                    })
                    statusText.text = "HUGR\nReceipt-only service requested · no sensing"
                    primaryText.text = "Requested explicitly from the selected saved receipt. It is not a fresh Start or sensor/hardware claim."
                    startReadinessRefresh()
                } catch (failure: Exception) {
                    WatchDiagnosticRuntime.capture(this@MainActivity, DiagnosticStage.SERVICE_START, failure = failure)
                    latchFreshFailure("Receipt-only start failed")
                }
            }
        }
        content.addView(freshReceiptOnlyButton)
        retainedDeliveryButton = Button(this).apply {
            text = "Explicit retained v2 delivery"
            textSize = 10f
            minimumHeight = 0
            setPadding(8, 4, 8, 4)
            isEnabled = false
            freshActionButtons += this
            setOnClickListener {
                if (retainedDeliveryBlocked()) return@setOnClickListener
                retainedDeliveryRequested = true
                freshActionButtons.forEach { it.isEnabled = false }
                freshUi = false
                Thread({ probeRetainedFinalizedDeliveryInBackground() }, "HUGR-ExplicitRetainedProbe").start()
            }
        }
        content.addView(retainedDeliveryButton)

        // Legacy contract anchor was `layout.addView(Switch...)`; this same
        // explicit test-only control is now part of the complete scroll content.
        content.addView(Switch(this).apply {
            text = "TEST ONLY: keep screen awake"
            textSize = 9f
            setTextColor(Color.YELLOW)
            minimumHeight = 0
            setPadding(0, 4, 0, 4)
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        // The root ScrollView owns heading, compact identity, controls, switch, and
        // complete evidence. A round display must never squeeze evidence to zero height.
        setContentView(scrollView)
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
        // Returning from clock/ambient is not a new launch or a new readiness decision.
        // Never offer START before the serialized receipt read resolves.
        statusText.text = "HUGR\nFRESH RECEIPT CHECKING · START LOCKED"
        primaryText.text = "Opening is passive. Reading current fresh receipt off the UI thread; no fresh run is admitted."
        evidenceText.text = "Existing source roots are not opened."
        startReadinessRefresh()
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
            WatchDiagnosticRuntime.capture(this, DiagnosticStage.STARTUP_SELECTION, failure = failure)
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
            // This is presentation containment only. No receipt is created,
            // reclassified, cleaned up, served, or otherwise mutated here.
            latchFreshFailure("Fresh scope preparation failed · no sensing")
        }
    }

    private fun renderFirstFrame(presentation: FirstFramePresentation) {
        if (freshTerminalFailure) return
        firstFramePresentation = presentation
        if (freshRequested && freshReadinessResolved) {
            renderFreshReadiness()
            return
        }
        statusText.text = "HUGR\n${presentation.headline}"
        primaryText.text = "Startup presentation only; no fresh run is admitted."
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
        latchFreshFailure("Fresh permission denied")
    }

    private fun startAllServices() {
        if (!startupRecoveryGate.permitsServiceStart()) {
            recordNormalStartupMarker(NormalStartupStage.SERVICE_START_BLOCKED)
            renderFirstFrame(firstFrameCoordinator.recoveryFailed())
            return
        }
        if (!freshRequested) return
        if (freshTerminalFailure || freshAdmissionScheduled) return
        freshAdmissionScheduled = true
        renderFreshReadiness()
        Thread({
            try {
                if (isFinishing || isDestroyed) return@Thread
                val admitted = FreshRunRuntime.admit(applicationContext)
                WatchDiagnosticRuntime.bindAttempt(applicationContext, admitted.runId, admitted.journal.watchBootSessionId)
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        FreshRunRuntime.end()
                        return@runOnUiThread
                    }
                    freshAdmissionIdentity = FreshRunUiIdentity(
                        admitted.journal.watchBootSessionId,
                        admitted.runId,
                    )
                    freshObservedActiveRuntime = freshAdmissionIdentity
                    renderFreshReadiness()
                    try {
                        startForegroundService(Intent(this, BleGattService::class.java).apply {
                            action = FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES
                        })
                        // Receipt observation on the next serialized refresh, not this
                        // request, is what may make the exact NEW RUN ADMITTED claim.
                        startReadinessRefresh()
                    } catch (failure: Exception) {
                        WatchDiagnosticRuntime.capture(this, DiagnosticStage.SERVICE_START, failure = failure)
                        runCatching { FreshRunRuntime.session?.fail("SERVICE_START_FAILED") }
                        FreshRunRuntime.end()
                        latchFreshFailure("Fresh service start failed")
                    }
                }
            } catch (failure: Exception) {
                WatchDiagnosticRuntime.capture(this, DiagnosticStage.STARTUP_SELECTION, failure = failure)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) latchFreshFailure("Fresh admission failed · no sensing")
                }
            }
        }, "HUGR-ExplicitFreshAdmission").start()
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
            WatchDiagnosticRuntime.capture(this, DiagnosticStage.SERVICE_START, failure = failure)
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
        if (freshUi || finalizedDeliveryRecoveryOnly) startReadinessRefresh()
        if (freshUi || permissionDecisionReached || finalizedDeliveryRecoveryOnly) renderEvidence() else renderFirstFrame(firstFramePresentation)
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
        if (freshUi) return
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
        if (freshUi) {
            if (freshTerminalFailure || freshReadPending) return
            freshReadPending = true
            // Block actions logically on every read, but do not blink already-ready
            // controls disabled/enabled on each passive one-second poll.
            if (!freshReadinessResolved) freshActionButtons.forEach { it.isEnabled = false }
            Thread({
                val result = runCatching {
                    // Snapshot all process-local values under the same runtime monitor as
                    // current(). This is a read only UI projection; it does not open a
                    // source journal or alter receipt/lifecycle state.
                    synchronized(FreshRunRuntime) {
                        val activeSession = FreshRunRuntime.session
                        val active = activeSession?.let {
                            FreshRunUiIdentity(it.journal.watchBootSessionId, it.runId)
                        }
                        FreshRuntimeRead(
                            receipt = FreshRunRuntime.current(applicationContext),
                            activeRuntime = active,
                            receiptOnly = FreshRunRuntime.receiptOnly,
                        )
                    }
                }
                result.exceptionOrNull()?.let { failure ->
                    WatchDiagnosticRuntime.capture(this, DiagnosticStage.STARTUP_SELECTION, failure = failure)
                    FreshRunRuntime.session?.quiesce()
                }
                runOnUiThread {
                    freshReadPending = false
                    if (isFinishing || isDestroyed || freshTerminalFailure) return@runOnUiThread
                    freshReadinessResolved = result.isSuccess
                    if (result.isFailure) {
                        latchFreshFailure("Fresh receipt unavailable / interrupted IO · no completion")
                    } else {
                        val observed = requireNotNull(result.getOrNull())
                        freshObservedReceipt = observed.receipt?.observed()
                        freshObservedActiveRuntime = observed.activeRuntime
                        freshObservedReceiptOnly = observed.receiptOnly
                        freshReceiptDisplayed = freshObservedReceipt != null
                        // Equal text never resets layout or manual scroll position.
                        renderFreshReadiness()
                        completeFreshStartRecheck()
                    }
                }
            }, "HUGR-FreshReceiptRead").start()
            return
        }
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
                FinalizedRecoveryDisplay.NOT_CONFIRMED -> if (
                    recoverySnapshot?.reason == FinalizedRecoveryStopReason.DEADLINE
                ) "HUGR\nRECOVERY DEADLINE EXPIRED · do not connect"
                else "HUGR\nRecovery readiness NOT CONFIRMED · do not connect"
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
        // Keep manual scroll position stable; saved-report viewer never auto-scrolls.
        if (WatchDiagnosticRuntime.lastSaveFailed) statusText.append("\nDIAGNOSTIC SAVE FAILED")
    }

    private fun Long?.orZero(): Long = this ?: 0L
    private fun flag(value: Boolean): Char = if (value) 'Y' else '-'
}
