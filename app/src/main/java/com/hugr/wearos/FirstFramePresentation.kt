package com.hugr.wearos

/**
 * Pure, local presentation state for a future truthful ordinary-activity first frame.
 *
 * This type deliberately carries only local startup facts. It makes no assertion about
 * recorder integrity, sensors, BLE, phone connection, freshness, permissions, services,
 * export state, or participant physiology unless an owning source path has established it.
 */
internal data class FirstFramePresentation(
    val state: State,
    val headline: String,
    val detail: String,
    val baselineText: String? = null,
    val failureClass: CausalRecorderFailureClass? = null,
    val operationTrace: List<String> = emptyList(),
    val fabricatesEmptyBaseline: Boolean = false,
    val assertsRecorderIntegrity: Boolean = false,
    val assertsSensorState: Boolean = false,
    val assertsBleState: Boolean = false,
    val assertsConnectionState: Boolean = false,
    val assertsFreshness: Boolean = false,
    val assertsPermissionOutcome: Boolean = false,
    val assertsServiceState: Boolean = false,
    val assertsExportState: Boolean = false,
) {
    enum class State {
        LOCAL_STARTING,
        RECOVERY_DEFERRED,
        RECOVERY_IN_PROGRESS,
        FRESH_SCOPE_PREPARING,
        FRESH_SCOPE_READY,
        RECOVERY_READY_HOLD,
        RECORDER_INITIALIZING,
        LOCAL_EVIDENCE_READY,
        BASELINE_SUMMARY_READY,
        BASELINE_SUMMARY_UNAVAILABLE,
        RECOVERY_FAILED,
        PERMISSION_UNAVAILABLE,
        LOCAL_INITIALIZATION_UNAVAILABLE,
    }

    companion object {
        fun localStarting(): FirstFramePresentation = FirstFramePresentation(
            state = State.LOCAL_STARTING,
            headline = "Local startup check pending",
            detail = "No local startup result is available yet.",
        )

        fun recoveryDeferred(): FirstFramePresentation = FirstFramePresentation(
            state = State.RECOVERY_DEFERRED,
            headline = "Local recovery deferred",
            detail = "Checking retained evidence off the first-frame path. Services remain stopped until readiness is known.",
        )

        fun recoveryInProgress(): FirstFramePresentation = FirstFramePresentation(
            state = State.RECOVERY_IN_PROGRESS,
            headline = "Checking local evidence store",
            detail = "Retained evidence recovery is in progress. Services remain stopped until readiness is known.",
        )

        fun freshScopePreparing(): FirstFramePresentation = FirstFramePresentation(
            state = State.FRESH_SCOPE_PREPARING,
            headline = "Preparing a new ordinary recording scope",
            detail = "The retained legacy journal and frozen package remain outside this startup path.",
        )

        fun freshScopeReady(): FirstFramePresentation = FirstFramePresentation(
            state = State.FRESH_SCOPE_READY,
            headline = "New ordinary recording scope ready",
            detail = "A separate session is ready. Ordinary permissions and services may now be assessed.",
        )

        fun recoveryReadyHold(baselineText: String): FirstFramePresentation = FirstFramePresentation(
            state = State.RECOVERY_READY_HOLD,
            headline = "Local recovery complete — services stopped",
            detail = "Recovery completed. Ordinary permissions and services remain stopped pending a separately authorised next step.",
            baselineText = baselineText,
        )

        fun recorderInitializing(): FirstFramePresentation = FirstFramePresentation(
            state = State.RECORDER_INITIALIZING,
            headline = "Checking local evidence store",
            detail = "Local startup result not yet known.",
        )

        fun localEvidenceReady(): FirstFramePresentation = FirstFramePresentation(
            state = State.LOCAL_EVIDENCE_READY,
            headline = "Local evidence store ready",
            detail = "Baseline summary pending.",
        )

        fun baselineSummaryReady(baselineText: String): FirstFramePresentation = FirstFramePresentation(
            state = State.BASELINE_SUMMARY_READY,
            headline = "Local baseline summary ready",
            detail = "Metadata-only local baseline summary.",
            baselineText = baselineText,
        )

        fun baselineSummaryUnavailable(): FirstFramePresentation = FirstFramePresentation(
            state = State.BASELINE_SUMMARY_UNAVAILABLE,
            headline = "Baseline summary unavailable",
            detail = "Local evidence store status is not changed by this summary failure.",
        )

        fun recoveryFailed(): FirstFramePresentation = FirstFramePresentation(
            state = State.RECOVERY_FAILED,
            headline = "Local recovery unavailable",
            detail = "Ordinary services remain stopped. Retained evidence was not altered by this startup result.",
        )

        fun permissionUnavailable(): FirstFramePresentation = FirstFramePresentation(
            state = State.PERMISSION_UNAVAILABLE,
            headline = "Ordinary permissions unavailable",
            detail = "Ordinary services remain stopped. Retained evidence was not altered by this permission result.",
        )

        fun localInitializationUnavailable(
            failureClass: CausalRecorderFailureClass,
        ): FirstFramePresentation = FirstFramePresentation(
            state = State.LOCAL_INITIALIZATION_UNAVAILABLE,
            headline = "Local startup unavailable",
            detail = "Services not started by this activity.",
            failureClass = failureClass,
        )
    }
}
