package com.hugr.wearos

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID

internal object WatchDiagnosticRuntime {
    const val ROOT_NAME = "hugr_operational_diagnostics_v1"
    private val processId = UUID.randomUUID()
    private val tail = java.util.ArrayDeque<DiagnosticEvent>()
    @Volatile private var current: DiagnosticContext? = null
    private var reportId = UUID.randomUUID()
    private var primaryExceptionReport: String? = null
    @Volatile var lastSaveFailed = false
        private set

    fun root(context: Context): File = File(context.filesDir, ROOT_NAME)
    fun store(context: Context) = SavedDiagnosticStore(root(context))

    @Synchronized
    fun begin(context: Context, attempt: UUID?, session: UUID?) {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        current = DiagnosticContext(info?.longVersionCode ?: 0, info?.versionName ?: "unknown", processId, attempt, session)
        reportId = attempt ?: UUID.randomUUID()
        primaryExceptionReport = null
        synchronized(tail) { tail.clear() }
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
