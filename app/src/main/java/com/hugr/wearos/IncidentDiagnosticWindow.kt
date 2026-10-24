package com.hugr.wearos

import java.util.UUID

/**
 * Payload-free, Watch-local diagnostic evidence. It is deliberately not a transport protocol,
 * shared request identifier, radio delivery assertion, or Phone acceptance signal.
 *
 * Rows live only in memory until a callback has completed its original ATT response attempt.
 * Before a decisive platform anomaly, 56 rows are retained so that eight slots remain available
 * for late settlement or later observations after the frozen failure. Pending rows are never
 * evicted. Capacity loss is reported explicitly rather than silently rotating a frozen incident.
 */
internal enum class IncidentOperationKind {
    R1_WRITE,
    R1_READ,
    C1_WRITE,
    SOURCE_RESUME,
    SOURCE_ACK,
    SOURCE_CONTROL_OTHER,
}

internal enum class IncidentAccess { READ, WRITE }

internal enum class IncidentRowState {
    CALLBACK_ENTERED,
    PRE_RESPONSE,
    SETTLED,
    DISCONNECTED_AWAITING_LATE_SETTLEMENT,
    CANCELLATION_REQUESTED_AWAITING_LATE_SETTLEMENT,
    DISCONNECTED_LATE_SETTLED,
    CANCELLATION_REQUESTED_LATE_SETTLED,
}

/** First incident boundary only; it never changes after it has been recorded. */
internal enum class IncidentFreezeReason {
    FIRST_DECISIVE_CALLBACK_OUTCOME,
    TRACE_WINDOW_OVERFLOW,
    DISCONNECTED_WITH_PENDING_OPERATION,
    DISCONNECTED_AFTER_SETTLED_OPERATION,
    CANCELLATION_REQUESTED_WITH_PENDING_OPERATION,
}

internal enum class IncidentResponseOutcome { PENDING, NOT_REQUESTED, TRUE, FALSE, NULL }

internal enum class IncidentPublicationState {
    NOT_CHECKPOINTED,
    ACKNOWLEDGED_ATOMIC,
    ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC,
    CAPACITY_EXHAUSTED,
    WRITE_FAILED,
}

/** Publication is a local file-store result, not a statement about ATT delivery or Phone receipt. */
internal enum class IncidentPublicationResult {
    ACKNOWLEDGED_ATOMIC,
    ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC,
    CAPACITY_EXHAUSTED,
    WRITE_FAILED,
}

internal data class IncidentOperationToken(
    val diagnosticEpoch: UUID,
    val ordinal: Long,
)

internal data class IncidentOperationRow(
    val ordinal: Long,
    val kind: IncidentOperationKind,
    val access: IncidentAccess,
    val nativeRequestId: Int,
    val connectionEpoch: UUID?,
    val characteristicInstanceId: Int?,
    val r1PageIndex: Long?,
    val attOffset: Int,
    val callbackEntryNanos: Long,
    var preResponseNanos: Long? = null,
    var responseNanos: Long? = null,
    var status: Int? = null,
    var responseLength: Int? = null,
    var responseOutcome: IncidentResponseOutcome = IncidentResponseOutcome.PENDING,
    var state: IncidentRowState = IncidentRowState.CALLBACK_ENTERED,
)

internal data class IncidentWindowSnapshot(
    val processEpoch: UUID,
    val diagnosticEpoch: UUID,
    val connectionEpoch: UUID?,
    val operationRevision: Long,
    val frozen: Boolean,
    val decisiveOrdinal: Long?,
    val freezeReason: IncidentFreezeReason?,
    val freezeConnectionEpoch: UUID?,
    val overflowCount: Long,
    val checkpointLossCount: Long,
    val publicationState: IncidentPublicationState,
    val lastCheckpointResult: IncidentPublicationResult?,
    val rows: List<IncidentOperationRow>,
)

internal class WatchIncidentDiagnosticWindow(
    private val processEpoch: UUID,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    companion object {
        const val MAX_OPERATION_ROWS = 64
        const val PRE_FREEZE_ROWS = 56
        const val POST_FREEZE_RESERVE = MAX_OPERATION_ROWS - PRE_FREEZE_ROWS
    }

    private var diagnosticEpoch = UUID.randomUUID()
    private var connectionEpoch: UUID? = null
    private var nextOrdinal = 0L
    private var operationRevision = 0L
    private var decisiveOrdinal: Long? = null
    private var freezeReason: IncidentFreezeReason? = null
    private var freezeConnectionEpoch: UUID? = null
    private var overflowCount = 0L
    private var checkpointLossCount = 0L
    private var publicationState = IncidentPublicationState.NOT_CHECKPOINTED
    private var lastCheckpointResult: IncidentPublicationResult? = null
    private val rows = ArrayDeque<IncidentOperationRow>()

    @Synchronized
    fun beginDiagnosticEpoch() {
        // A frozen incident is intentionally not replaced in process memory. A later process can
        // attempt a new incident; the durable store independently refuses silent report rotation.
        if (frozen()) return
        diagnosticEpoch = UUID.randomUUID()
        connectionEpoch = null
        nextOrdinal = 0L
        operationRevision = 0L
        overflowCount = 0L
        freezeReason = null
        freezeConnectionEpoch = null
        checkpointLossCount = 0L
        publicationState = IncidentPublicationState.NOT_CHECKPOINTED
        lastCheckpointResult = null
        rows.clear()
    }

    @Synchronized
    fun onConnected() {
        // A frozen incident retains its own freezeConnectionEpoch, while a later real connection
        // still needs a distinct local epoch on any post-freeze row.
        connectionEpoch = UUID.randomUUID()
        operationRevision++
    }

    @Synchronized
    fun onDisconnected() {
        markPendingLifecycle(
            state = IncidentRowState.DISCONNECTED_AWAITING_LATE_SETTLEMENT,
            reason = IncidentFreezeReason.DISCONNECTED_WITH_PENDING_OPERATION,
        )
        // A completed, timely platform response is still local diagnostic evidence. When the
        // connection closes after only settled rows, preserve that bounded window under a distinct
        // frozen context without changing any recorded response outcome.
        if (!frozen() && rows.isNotEmpty()) {
            freeze(IncidentFreezeReason.DISCONNECTED_AFTER_SETTLED_OPERATION)
            operationRevision++
        }
    }

    @Synchronized
    fun onCancellationRequested() {
        markPendingLifecycle(
            state = IncidentRowState.CANCELLATION_REQUESTED_AWAITING_LATE_SETTLEMENT,
            reason = IncidentFreezeReason.CANCELLATION_REQUESTED_WITH_PENDING_OPERATION,
        )
    }

    @Synchronized
    fun enter(
        kind: IncidentOperationKind,
        access: IncidentAccess,
        nativeRequestId: Int,
        characteristicInstanceId: Int?,
        r1PageIndex: Long?,
        attOffset: Int,
    ): IncidentOperationToken? {
        val currentlyFrozen = frozen()
        val limit = if (currentlyFrozen) MAX_OPERATION_ROWS else PRE_FREEZE_ROWS
        if (rows.size >= limit && (currentlyFrozen || !evictOldestSettled())) {
            overflowCount++
            freeze(IncidentFreezeReason.TRACE_WINDOW_OVERFLOW)
            operationRevision++
            return null
        }
        if (rows.size >= limit) {
            // All evictable pre-freeze rows have been removed. A pending row is never displaced.
            overflowCount++
            freeze(IncidentFreezeReason.TRACE_WINDOW_OVERFLOW)
            operationRevision++
            return null
        }
        val ordinal = ++nextOrdinal
        rows.addLast(
            IncidentOperationRow(
                ordinal = ordinal,
                kind = kind,
                access = access,
                nativeRequestId = nativeRequestId,
                connectionEpoch = connectionEpoch,
                characteristicInstanceId = characteristicInstanceId,
                r1PageIndex = r1PageIndex,
                attOffset = attOffset,
                callbackEntryNanos = nowNanos(),
            ),
        )
        operationRevision++
        return IncidentOperationToken(diagnosticEpoch, ordinal)
    }

    @Synchronized
    fun preResponse(token: IncidentOperationToken?, status: Int, responseLength: Int) {
        val row = row(token) ?: return
        if (row.responseOutcome != IncidentResponseOutcome.PENDING) return
        row.preResponseNanos = nowNanos()
        row.status = status
        row.responseLength = responseLength.coerceAtLeast(0)
        if (row.state == IncidentRowState.CALLBACK_ENTERED) row.state = IncidentRowState.PRE_RESPONSE
        operationRevision++
    }

    @Synchronized
    fun settle(
        token: IncidentOperationToken?,
        outcome: IncidentResponseOutcome,
        status: Int,
        responseLength: Int,
        responseNanos: Long = nowNanos(),
    ) {
        val row = row(token) ?: return
        // The first callback outcome is evidence. A duplicate/native-late callback cannot rewrite
        // a completed false, null, timely true, or prior late settlement.
        if (row.responseOutcome != IncidentResponseOutcome.PENDING) return
        val priorState = row.state
        row.status = status
        row.responseLength = responseLength.coerceAtLeast(0)
        row.responseNanos = responseNanos
        row.responseOutcome = outcome
        row.state = when (priorState) {
            IncidentRowState.DISCONNECTED_AWAITING_LATE_SETTLEMENT -> IncidentRowState.DISCONNECTED_LATE_SETTLED
            IncidentRowState.CANCELLATION_REQUESTED_AWAITING_LATE_SETTLEMENT -> IncidentRowState.CANCELLATION_REQUESTED_LATE_SETTLED
            else -> IncidentRowState.SETTLED
        }
        if (decisiveOrdinal == null && isDecisive(row)) {
            decisiveOrdinal = row.ordinal
            freeze(IncidentFreezeReason.FIRST_DECISIVE_CALLBACK_OUTCOME)
        }
        operationRevision++
    }

    @Synchronized
    fun notePublication(result: IncidentPublicationResult): Boolean {
        val previous = publicationState
        val previousResult = lastCheckpointResult
        lastCheckpointResult = result
        val next = when (result) {
            IncidentPublicationResult.ACKNOWLEDGED_ATOMIC -> IncidentPublicationState.ACKNOWLEDGED_ATOMIC
            IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC ->
                IncidentPublicationState.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC
            IncidentPublicationResult.CAPACITY_EXHAUSTED -> IncidentPublicationState.CAPACITY_EXHAUSTED
            IncidentPublicationResult.WRITE_FAILED -> {
                checkpointLossCount++
                IncidentPublicationState.WRITE_FAILED
            }
        }
        publicationState = next
        if (next != previous || result != previousResult) {
            operationRevision++
            return true
        }
        return false
    }

    @Synchronized
    fun snapshot(): IncidentWindowSnapshot = IncidentWindowSnapshot(
        processEpoch = processEpoch,
        diagnosticEpoch = diagnosticEpoch,
        connectionEpoch = connectionEpoch,
        operationRevision = operationRevision,
        frozen = frozen(),
        decisiveOrdinal = decisiveOrdinal,
        freezeReason = freezeReason,
        freezeConnectionEpoch = freezeConnectionEpoch,
        overflowCount = overflowCount,
        checkpointLossCount = checkpointLossCount,
        publicationState = publicationState,
        lastCheckpointResult = lastCheckpointResult,
        rows = rows.map { it.copy() },
    )

    private fun row(token: IncidentOperationToken?): IncidentOperationRow? {
        if (token == null || token.diagnosticEpoch != diagnosticEpoch) return null
        return rows.firstOrNull { it.ordinal == token.ordinal }
    }

    private fun evictOldestSettled(): Boolean {
        val candidate = rows.firstOrNull { it.responseOutcome != IncidentResponseOutcome.PENDING } ?: return false
        return rows.remove(candidate)
    }

    private fun markPendingLifecycle(state: IncidentRowState, reason: IncidentFreezeReason) {
        var changed = false
        rows.filter { it.responseOutcome == IncidentResponseOutcome.PENDING }.forEach { row ->
            if (row.state == IncidentRowState.CALLBACK_ENTERED || row.state == IncidentRowState.PRE_RESPONSE) {
                row.state = state
                changed = true
            }
        }
        if (changed) {
            freeze(reason)
            operationRevision++
        }
    }

    private fun freeze(reason: IncidentFreezeReason) {
        if (freezeReason == null) {
            freezeReason = reason
            freezeConnectionEpoch = connectionEpoch
        }
    }

    private fun isDecisive(row: IncidentOperationRow): Boolean {
        if (row.responseOutcome == IncidentResponseOutcome.FALSE || row.responseOutcome == IncidentResponseOutcome.NULL) return true
        val responseNanos = row.responseNanos ?: return false
        return row.responseOutcome == IncidentResponseOutcome.TRUE &&
            responseNanos - row.callbackEntryNanos >= 10_000_000_000L
    }

    private fun frozen(): Boolean = freezeReason != null
}

/** Renders only fixed vocabulary and scalar metadata; no request/response payload is retained. */
internal object IncidentDiagnosticFormat {
    fun render(snapshot: IncidentWindowSnapshot): String = withinBudget(
        header = buildString {
        append("HUGR SAVED ATT INCIDENT DIAGNOSTIC v1\n")
        append("Watch-local incident window — NOT delivery, radio, Phone acceptance, or cross-device correlation\n")
        append("late TRUE is Android platform response acceptance only; it is not Phone delivery or Phone acceptance\n")
        append("processEpoch=${snapshot.processEpoch} diagnosticEpoch=${snapshot.diagnosticEpoch} latestConnectionEpoch=")
        append(snapshot.connectionEpoch ?: "none")
        append(" freezeConnectionEpoch=")
        append(snapshot.freezeConnectionEpoch ?: "none")
        append("\n")
        append("windowRows=${snapshot.rows.size}/${WatchIncidentDiagnosticWindow.MAX_OPERATION_ROWS} revision=${snapshot.operationRevision} ")
        append("preFreezeRows=${WatchIncidentDiagnosticWindow.PRE_FREEZE_ROWS} ")
        append("postFreezeReserve=${WatchIncidentDiagnosticWindow.POST_FREEZE_RESERVE} ")
        append("frozen=${snapshot.frozen} decisiveOrdinal=${snapshot.decisiveOrdinal ?: "none"} ")
        append("freezeReason=${snapshot.freezeReason?.name ?: "NONE"}\n")
        append("overflow=TRACE_WINDOW_OVERFLOW:${snapshot.overflowCount} ")
        append("checkpointCrashLoss=CHECKPOINT_LOSS_UNCERTAIN:${snapshot.checkpointLossCount} ")
        append("publication=${snapshot.publicationState.name} ")
        append("lastCheckpointResult=${snapshot.lastCheckpointResult?.name ?: "NONE"}\n")
        append("crashBeforeCheckpoint=UNKNOWN_NOT_EVIDENCE_OF_CALLBACK_NON_ARRIVAL\n")
        append("nativeRequestIdScope=WATCH_LOCAL_ONLY_NOT_CROSS_DEVICE_KEY\n")
        append("characteristicHandle=UNAVAILABLE_NOT_INVENTED; characteristicInstanceId is native-local metadata only\n")
        append("CORRELATION_UNRESOLVED: equal ordinals, offsets, request IDs, or clocks are not joins.\n")
        append("Payloads, UUIDs, hashes, source samples, addresses, receipt contents, and exception messages are omitted.\n")
        append("rowCodes kind=R1W|R1R|C1W|SR|SA|SC access=R|W outcome=P|NR|T|F|N lifecycle=E|PR|S|DA|CA|DS|CS\n")
    },
        rows = snapshot.rows.map(::renderRow),
    )

    /**
     * The normal fixed-width scalar vocabulary fits all 64 legal rows within 24 KiB. This fallback
     * is retained for forward safety: it only removes complete trailing rows and reports both the
     * omitted row count and their UTF-8 bytes; it never raw-truncates a report or calls it complete.
     */
    private fun withinBudget(header: String, rows: List<String>): String {
        val complete = renderWithTraceStatus(header, rows, complete = true, omittedRows = 0, omittedBytes = 0)
        if (complete.toByteArray(Charsets.UTF_8).size <= SavedDiagnosticFormat.MAX_REPORT_BYTES) return complete
        for (kept in rows.size - 1 downTo 0) {
            val omitted = rows.drop(kept)
            val candidate = renderWithTraceStatus(
                header = header,
                rows = rows.take(kept),
                complete = false,
                omittedRows = omitted.size,
                omittedBytes = omitted.sumOf { it.toByteArray(Charsets.UTF_8).size },
            )
            if (candidate.toByteArray(Charsets.UTF_8).size <= SavedDiagnosticFormat.MAX_REPORT_BYTES) return candidate
        }
        // Header vocabulary is fixed and deliberately far below the report budget.
        return renderWithTraceStatus(header, emptyList(), complete = false, omittedRows = rows.size,
            omittedBytes = rows.sumOf { it.toByteArray(Charsets.UTF_8).size })
    }

    private fun renderWithTraceStatus(
        header: String,
        rows: List<String>,
        complete: Boolean,
        omittedRows: Int,
        omittedBytes: Int,
    ): String = buildString {
        append(header)
        if (complete) append("traceCompleteness=COMPLETE traceOmittedRows=0 traceByteLoss=0\n")
        else append("traceCompleteness=INCOMPLETE_TRACE_WINDOW_BUDGET traceOmittedRows=$omittedRows traceByteLoss=$omittedBytes\n")
        rows.forEach(::append)
        append("--- END REPORT ---\n")
    }

    private fun renderRow(row: IncidentOperationRow): String {
        val delayMs = row.responseNanos?.let { (it - row.callbackEntryNanos).coerceAtLeast(0L) / 1_000_000L }
        return buildString {
            append("row o=${row.ordinal} k=${kindCode(row.kind)} a=${if (row.access == IncidentAccess.READ) "R" else "W"} ")
            append("rid=${row.nativeRequestId} ce=${row.connectionEpoch ?: "none"} ci=${row.characteristicInstanceId ?: "none"} ")
            append("p=${row.r1PageIndex ?: "none"} off=${row.attOffset} in=${row.callbackEntryNanos} ")
            append("pre=${row.preResponseNanos ?: "none"} res=${row.responseNanos ?: "none"} delayMs=${delayMs ?: "none"} ")
            append("st=${row.status ?: "none"} len=${row.responseLength ?: "none"} ")
            append("out=${outcomeCode(row.responseOutcome)} life=${stateCode(row.state)}\n")
        }
    }

    private fun kindCode(kind: IncidentOperationKind): String = when (kind) {
        IncidentOperationKind.R1_WRITE -> "R1W"
        IncidentOperationKind.R1_READ -> "R1R"
        IncidentOperationKind.C1_WRITE -> "C1W"
        IncidentOperationKind.SOURCE_RESUME -> "SR"
        IncidentOperationKind.SOURCE_ACK -> "SA"
        IncidentOperationKind.SOURCE_CONTROL_OTHER -> "SC"
    }

    private fun outcomeCode(outcome: IncidentResponseOutcome): String = when (outcome) {
        IncidentResponseOutcome.PENDING -> "P"
        IncidentResponseOutcome.NOT_REQUESTED -> "NR"
        IncidentResponseOutcome.TRUE -> "T"
        IncidentResponseOutcome.FALSE -> "F"
        IncidentResponseOutcome.NULL -> "N"
    }

    private fun stateCode(state: IncidentRowState): String = when (state) {
        IncidentRowState.CALLBACK_ENTERED -> "E"
        IncidentRowState.PRE_RESPONSE -> "PR"
        IncidentRowState.SETTLED -> "S"
        IncidentRowState.DISCONNECTED_AWAITING_LATE_SETTLEMENT -> "DA"
        IncidentRowState.CANCELLATION_REQUESTED_AWAITING_LATE_SETTLEMENT -> "CA"
        IncidentRowState.DISCONNECTED_LATE_SETTLED -> "DS"
        IncidentRowState.CANCELLATION_REQUESTED_LATE_SETTLED -> "CS"
    }
}
