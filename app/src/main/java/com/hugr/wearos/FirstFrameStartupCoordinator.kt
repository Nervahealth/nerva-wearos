package com.hugr.wearos

/**
 * Pure contract seam for bounded first-frame sequencing.
 *
 * It has no Android, disk, journal, BLE, service, permission, sensor, transport, export, or
 * retained-evidence side effects.
 */
internal class FirstFrameStartupCoordinator {
    fun firstFrame(): FirstFramePresentation = FirstFramePresentation.localStarting()

    fun recoveryDeferred(): FirstFramePresentation = FirstFramePresentation.recoveryDeferred()

    fun recoveryInProgress(): FirstFramePresentation = FirstFramePresentation.recoveryInProgress()

    fun freshScopePreparing(): FirstFramePresentation = FirstFramePresentation.freshScopePreparing()

    fun freshScopeReady(): FirstFramePresentation = FirstFramePresentation.freshScopeReady()

    fun recorderInitializing(): FirstFramePresentation = FirstFramePresentation.recorderInitializing()

    fun onRecorderReady(): FirstFramePresentation = FirstFramePresentation.localEvidenceReady()

    fun onBaselineReady(baseline: Build46SourceBaseline): FirstFramePresentation =
        FirstFramePresentation.baselineSummaryReady(Build46BaselineFormatter.format(baseline))

    fun recoveryReadyHold(baseline: Build46SourceBaseline): FirstFramePresentation =
        FirstFramePresentation.recoveryReadyHold(Build46BaselineFormatter.format(baseline))

    fun onBaselineFailure(@Suppress("UNUSED_PARAMETER") failure: Throwable): FirstFramePresentation =
        FirstFramePresentation.baselineSummaryUnavailable()

    fun onInitializationFailure(@Suppress("UNUSED_PARAMETER") failure: Throwable): FirstFramePresentation =
        FirstFramePresentation.localInitializationUnavailable(CausalRecorderFailureClass.INITIALIZATION)

    fun recoveryFailed(): FirstFramePresentation = FirstFramePresentation.recoveryFailed()

    fun permissionUnavailable(): FirstFramePresentation = FirstFramePresentation.permissionUnavailable()

    fun onRecoveryFailure(@Suppress("UNUSED_PARAMETER") failure: Throwable): FirstFramePresentation =
        recoveryFailed()

    fun successfulStartupTrace(): List<String> = listOf(
        "FIRST_FRAME",
        "FRESH_SCOPE_DEFERRED",
        "FRESH_SCOPE_ATTEMPT",
        "FRESH_SCOPE_READY",
        "PERMISSION_DECISION",
    )
}
