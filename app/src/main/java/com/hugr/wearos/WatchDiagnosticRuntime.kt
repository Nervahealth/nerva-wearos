package com.hugr.wearos

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal object WatchDiagnosticRuntime {
    const val ROOT_NAME = "hugr_operational_diagnostics_v1"
    private val processId = UUID.randomUUID()
    private val tail = java.util.ArrayDeque<DiagnosticEvent>()
    @Volatile private var current: DiagnosticContext? = null
    private var reportId = UUID.randomUUID()
    private var primaryExceptionReport: String? = null
    private val incidentWindow = WatchIncidentDiagnosticWindow(processId)
    private val incidentWorker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HUGR-AttIncidentCheckpoint").apply { isDaemon = true }
    }
    private var incidentCheckpointQueued = false
    private var incidentCheckpointRevision = -1L
    @Volatile var lastSaveFailed = false
        private set

    fun root(context: Context): File = File(context.filesDir, ROOT_NAME)
    fun store(context: Context) = SavedDiagnosticStore(root(context))

    /** Watch-local process/connection epoch start; not a cross-device correlation key. */
    fun onIncidentConnectionOpened() = incidentWindow.onConnected()

    /** Records local disconnect lifecycle and queues only a bounded worker checkpoint. */
    fun onIncidentConnectionClosed(context: Context) {
        incidentWindow.onDisconnected()
        scheduleIncidentCheckpoint(context.applicationContext)
    }

    /** Records a local cancellation lifecycle marker only. */
    fun onIncidentCancellationRequested(context: Context) {
        incidentWindow.onCancellationRequested()
        scheduleIncidentCheckpoint(context.applicationContext)
    }

    fun enterIncidentOperation(
        kind: IncidentOperationKind,
        access: IncidentAccess,
        nativeRequestId: Int,
        characteristicInstanceId: Int?,
        r1PageIndex: Long?,
        attOffset: Int,
    ): IncidentOperationToken? = incidentWindow.enter(
        kind, access, nativeRequestId, characteristicInstanceId, r1PageIndex, attOffset,
    )

    fun preResponseIncidentOperation(token: IncidentOperationToken?, status: Int, responseLength: Int) {
        incidentWindow.preResponse(token, status, responseLength)
    }

    /**
     * This method must be called only after the original platform response attempt returns. It
     * stores the in-memory outcome first and queues at most one bounded worker checkpoint. Timely
     * TRUE is retained as Watch-local platform evidence in the open rolling window; it is not
     * delivery, Phone acceptance, or cross-device correlation evidence.
     */
    fun settleIncidentOperationAfterResponse(
        context: Context,
        token: IncidentOperationToken?,
        outcome: IncidentResponseOutcome,
        status: Int,
        responseLength: Int,
        responseNanos: Long,
    ) {
        incidentWindow.settle(token, outcome, status, responseLength, responseNanos)
        scheduleIncidentCheckpoint(context.applicationContext)
    }

    /** Test/readback helper; returns payload-free in-memory state only. */
    fun incidentSnapshotForTest(): IncidentWindowSnapshot = incidentWindow.snapshot()

    @Synchronized
    private fun scheduleIncidentCheckpoint(context: Context) {
        val snapshot = incidentWindow.snapshot()
        if (snapshot.rows.isEmpty() || incidentCheckpointQueued || snapshot.operationRevision == incidentCheckpointRevision) return
        incidentCheckpointQueued = true
        val worker = Runnable {
            var checkpoint: IncidentWindowSnapshot? = null
            var result = IncidentPublicationResult.WRITE_FAILED
            try {
                checkpoint = incidentWindow.snapshot()
                result = store(context).saveIncident(
                    IncidentDiagnosticFormat.render(requireNotNull(checkpoint)),
                    requireNotNull(checkpoint).diagnosticEpoch,
                )
            } catch (_: Throwable) {
                // A worker failure is a bounded explicit loss. It is not requeued automatically;
                // another real post-response/disconnect event may coalesce a later checkpoint.
                result = IncidentPublicationResult.WRITE_FAILED
            }
            try {
                incidentWindow.notePublication(result)
            } catch (_: Throwable) {
                // Publication-state failure cannot strand the queue or manufacture an ACK.
                result = IncidentPublicationResult.WRITE_FAILED
                runCatching { incidentWindow.notePublication(result) }
            } finally {
                val latest = incidentWindow.snapshot()
                synchronized(this) {
                    incidentCheckpointRevision = checkpoint?.operationRevision ?: latest.operationRevision
                    incidentCheckpointQueued = false
                    // A late settlement can arrive while the atomic write executes. It is
                    // checkpointed in a later worker job, never synchronously in the ATT callback.
                    // The first successful checkpoint also writes a same-file acknowledgement
                    // state once; failure and capacity exhaustion deliberately do not self-retry.
                    if ((result == IncidentPublicationResult.ACKNOWLEDGED_ATOMIC ||
                            result == IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC) &&
                        latest.operationRevision != incidentCheckpointRevision
                    ) {
                        scheduleIncidentCheckpoint(context)
                    }
                }
            }
            when (result) {
                IncidentPublicationResult.ACKNOWLEDGED_ATOMIC,
                IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC -> {
                    lastSaveFailed = false
                    Log.w("HUGR-Diagnostic", "Saved payload-free ATT incident checkpoint")
                }
                IncidentPublicationResult.CAPACITY_EXHAUSTED,
                IncidentPublicationResult.WRITE_FAILED -> {
                    lastSaveFailed = true
                    Log.e("HUGR-Diagnostic", "Payload-free ATT incident checkpoint was not published: $result")
                }
            }
        }
        try {
            incidentWorker.execute(worker)
        } catch (_: Throwable) {
            // Executor rejection is also a bounded loss, not a queued task and not a retry loop.
            incidentWindow.notePublication(IncidentPublicationResult.WRITE_FAILED)
            incidentCheckpointRevision = snapshot.operationRevision
            incidentCheckpointQueued = false
            lastSaveFailed = true
            Log.e("HUGR-Diagnostic", "Payload-free ATT incident checkpoint worker was not queued")
        }
    }

    @Synchronized
    fun begin(context: Context, attempt: UUID?, session: UUID?) {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        current = DiagnosticContext(info?.longVersionCode ?: 0, info?.versionName ?: "unknown", processId, attempt, session)
        reportId = attempt ?: UUID.randomUUID()
        primaryExceptionReport = null
        // A fresh local diagnostic epoch is permitted only before the first incident freezes.
        // beginDiagnosticEpoch deliberately refuses to replace a frozen incident in memory.
        incidentWindow.beginDiagnosticEpoch()
        synchronized(tail) { tail.clear() }
    }
    /** No journal I/O; attach only identifiers already obtained from current admission. */
    @Synchronized
    fun bindAttempt(context: Context, attempt: UUID?, session: UUID?) {
        if (current == null) begin(context, attempt, session)
        else current = current?.copy(attemptId = attempt, sourceSessionId = session)
    }
    @Synchronized
    fun update(attempt: UUID?, session: UUID?, lineage: Long, cursor: Long?, retained: Long?, historical: Long?, mtu: Int, queued: Long?) {
        current = current?.copy(attemptId = attempt, sourceSessionId = session, lineage = lineage,
            phoneCursor = cursor, retainedEndpoint = retained, historicalBound = historical, mtu = mtu,
            pendingManifestEndpoint = queued)
    }
    fun event(event: CausalFlightEvent) {
        synchronized(tail) {
            tail.addLast(DiagnosticEvent(event.eventSequence, event.wallTimeMs, event.elapsedRealtimeMs,
                event.bleLineage, event.code.name, event.reasonCode))
            while (tail.size > 16) tail.removeFirst()
        }
    }
    /** Synchronous bounded persistence BEFORE abort/stop; failure never replaces the original outcome. */
    @Synchronized
    fun capture(context: Context, stage: DiagnosticStage, outcome: DiagnosticOutcome = DiagnosticOutcome.EXCEPTION, failure: Throwable? = null) {
        try {
            if (current == null) begin(context, null, null)
            val text = SavedDiagnosticFormat.render(requireNotNull(current), stage, outcome, failure,
                System.currentTimeMillis(), SystemClock.elapsedRealtime(), synchronized(tail) { tail.toList() })
            if (failure != null && primaryExceptionReport == null) primaryExceptionReport = text
            val preserved = primaryExceptionReport?.let {
                SavedDiagnosticFormat.withTerminal(it, stage, outcome, System.currentTimeMillis())
            } ?: text
            store(context).save(preserved, reportId)
            lastSaveFailed = false
            Log.i("HUGR-Diagnostic", "Saved operational diagnostic stage=${stage.name} outcome=${outcome.name}")
        } catch (_: Throwable) {
            lastSaveFailed = true
            Log.e("HUGR-Diagnostic", "Operational diagnostic persistence failed; original failure handling continues")
        }
    }

    /**
     * Invoked only after sendResponse returned false, could not be attempted because the server
     * was null, or returned after the ten-second Phone operation limit. The caller posts this method
     * after the ATT response attempt; this method does not
     * change response, ACK, source, retry, or transport behavior.
     */
    fun captureAttResponseOutcome(context: Context, observation: AttResponseOperationDiagnostic) {
        require(observation.responseOutcome != AttResponseOutcome.TRUE ||
            observation.responseElapsedMs - observation.callbackEntryElapsedMs >= 10_000L)
        try {
            store(context).save(SavedDiagnosticFormat.renderAttResponseOperation(observation))
            lastSaveFailed = false
            Log.w("HUGR-Diagnostic", "Saved payload-free ATT response outcome=${observation.responseOutcome.name}")
        } catch (_: Throwable) {
            lastSaveFailed = true
            Log.e("HUGR-Diagnostic", "ATT response diagnostic persistence failed after response attempt")
        }
    }
}

/** Best effort for Java/Kotlin uncaught exceptions; never suppresses Android crash termination. */
class HugrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            WatchDiagnosticRuntime.capture(this, DiagnosticStage.UNCAUGHT_EXCEPTION, failure = failure)
            if (previous != null) previous.uncaughtException(thread, failure)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}
