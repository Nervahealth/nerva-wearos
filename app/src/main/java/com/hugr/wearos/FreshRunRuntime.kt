package com.hugr.wearos

import android.content.Context
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.UUID

internal object FreshRunRuntime {
    const val DIRECTORY = "fresh_ordinary_runs_v3"
    const val ACTION_START_FRESH_FIVE_MINUTES = "com.hugr.wearos.START_FRESH_FIVE_MINUTES"
    const val ACTION_SERVE_FRESH_RECEIPT_ONLY = "com.hugr.wearos.SERVE_FRESH_RECEIPT_ONLY"
    @Volatile var session: FreshRunSession? = null
        private set
    @Volatile var receiptOnly = false
        private set
    private fun store(context: Context) = FreshRunReceiptStore(File(context.noBackupFilesDir, DIRECTORY), syncDirectory = { dir ->
        val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
        try { check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
    })
    @Synchronized fun admit(context: Context): FreshRunSession {
        check(session == null && !receiptOnly) { "Fresh service already active" }
        val store = store(context)
        val previous = store.current()
        check(previous == null || previous.phase == FreshRunPhase.COMPLETED || previous.phase == FreshRunPhase.FAILED) { "Previous fresh run unresolved" }
        val run = UUID.randomUUID()
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        check(boot >= 0) { "Boot clock provenance unavailable" }
        val journal = SourceJournal(store.sourceRoot(run), boot, { StatFs(context.noBackupFilesDir.absolutePath).availableBytes }, { System.currentTimeMillis() })
        try {
            check(journal.preflight().eligible && journal.latestRecordIndex() == 0L)
            // DEVICE_HEALTH is the one canonical stream known before Samsung SDK discovery.
            // Its status records can precede tracker callbacks, so declare it truthfully here.
            val initial = FreshRunReceipt(
                journal.watchBootSessionId,
                run,
                startWallMs = System.currentTimeMillis(),
                startElapsedMs = SystemClock.elapsedRealtime(),
                bootCount = boot,
                supportedMask = 16,
                startedMask = 16,
            )
            // sourceRoot may have created run directory before receipt admission.
            store.createInExistingNewRun(initial)
            return FreshRunSession(journal, store, initial, { SystemClock.elapsedRealtime() }, { System.currentTimeMillis() }).also { session = it }
        } catch (e: Exception) { runCatching { journal.close() }; throw e }
    }
    @Synchronized fun openReceiptOnly(context: Context): FreshRunReceipt {
        check(session == null && !receiptOnly) { "Fresh service already active" }
        val store = store(context); var receipt = requireNotNull(store.current()) { "No current fresh receipt" }
        if (receipt.phase == FreshRunPhase.STARTED || receipt.phase == FreshRunPhase.FINALIZED) {
            receipt = receipt.copy(revision = receipt.revision + 1, phase = FreshRunPhase.FAILED, failure = "PROCESS_INTERRUPTED")
            store.publish(receipt)
        }
        receiptOnly = true
        return receipt
    }
    fun current(context: Context): FreshRunReceipt? = session?.snapshot() ?: store(context).current()
    @Synchronized fun end() {
        val active = session
        active?.quiesce()
        if (active != null) runCatching { active.fail("SERVICE_INTERRUPTED") }
        // Closing a nonfinalized journal seals bytes, but never changes a FAILED receipt to success.
        active?.let { runCatching { it.journal.close() } }
        session = null; receiptOnly = false
    }
}
