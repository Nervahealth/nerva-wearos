package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the future first-frame split specified in
 * HUGR_NSTAIR_R0_2_Watch52w_First_Frame_Separation_Design_and_RED_Test_Decision_2026_09_16.
 *
 * This test intentionally names the missing pure coordinator/presentation model. It must not
 * exercise Android Context, disk, journal recovery, permissions, services, BLE, sensors or export.
 */
class FirstFrameStartupContractTest {

    @Test
    fun `FFR1 local starting is renderable before any startup operation`() {
        val presentation = FirstFrameStartupCoordinator().firstFrame()

        assertEquals(FirstFramePresentation.localStarting(), presentation)
        assertTrue(presentation.operationTrace.isEmpty())
    }

    @Test
    fun `FFR2 starting states expose only unknown or not-started facts`() {
        val presentation = FirstFrameStartupCoordinator().firstFrame()

        assertEquals(FirstFramePresentation.localStarting(), presentation)
        assertFalse(presentation.assertsRecorderIntegrity)
        assertFalse(presentation.assertsSensorState)
        assertFalse(presentation.assertsBleState)
        assertFalse(presentation.assertsConnectionState)
        assertFalse(presentation.assertsFreshness)
        assertFalse(presentation.assertsPermissionOutcome)
        assertFalse(presentation.assertsServiceState)
        assertFalse(presentation.assertsExportState)
    }

    @Test
    fun `FFR2a deferred recovery keeps the first surface responsive and services unasserted`() {
        val presentation = FirstFrameStartupCoordinator().recoveryDeferred()

        assertEquals(FirstFramePresentation.recoveryDeferred(), presentation)
        assertFalse(presentation.assertsRecorderIntegrity)
        assertFalse(presentation.assertsSensorState)
        assertFalse(presentation.assertsBleState)
        assertFalse(presentation.assertsServiceState)
    }

    @Test
    fun `FFR2b denied permission reports an ordinary-service stop without fabricating evidence state`() {
        val presentation = FirstFrameStartupCoordinator().permissionUnavailable()

        assertEquals(FirstFramePresentation.permissionUnavailable(), presentation)
        assertFalse(presentation.assertsRecorderIntegrity)
        assertFalse(presentation.assertsSensorState)
        assertFalse(presentation.assertsBleState)
        assertFalse(presentation.assertsServiceState)
    }

    @Test
    fun `FFR3 recorder success with pending baseline is local evidence ready only`() {
        val presentation = FirstFrameStartupCoordinator().onRecorderReady()

        assertEquals(FirstFramePresentation.localEvidenceReady(), presentation)
        assertNull(presentation.baselineText)
        assertFalse(presentation.assertsBleState)
        assertFalse(presentation.assertsConnectionState)
        assertFalse(presentation.assertsServiceState)
    }

    @Test
    fun `FFR4 baseline success keeps existing metadata-only formatter semantics`() {
        val baseline = Build46SourceBaseline(
            retainedSessionCount = 2,
            latestRecordIndex = 99L,
            deliveryCounts = SourceDeliveryState.entries.associateWith { (it.ordinal + 1).toLong() },
        )

        val presentation = FirstFrameStartupCoordinator().onBaselineReady(baseline)

        assertEquals(FirstFramePresentation.baselineSummaryReady(Build46BaselineFormatter.format(baseline)), presentation)
        assertTrue(presentation.baselineText!!.contains("BUF=1"))
        assertTrue(presentation.baselineText!!.contains("DROP=6"))
        assertFalse(presentation.baselineText!!.contains("FORBIDDEN-PHYSIOLOGY-SENTINEL"))
    }

    @Test
    fun `FFR4a completed recovery enters an honest readiness hold without ordinary-service assertions`() {
        val baseline = Build46SourceBaseline(
            retainedSessionCount = 2,
            latestRecordIndex = 99L,
            deliveryCounts = SourceDeliveryState.entries.associateWith { (it.ordinal + 1).toLong() },
        )

        val presentation = FirstFrameStartupCoordinator().recoveryReadyHold(baseline)

        assertEquals(
            FirstFramePresentation.recoveryReadyHold(Build46BaselineFormatter.format(baseline)),
            presentation,
        )
        assertEquals(FirstFramePresentation.State.RECOVERY_READY_HOLD, presentation.state)
        assertTrue(presentation.detail.contains("services remain stopped"))
        assertFalse(presentation.assertsPermissionOutcome)
        assertFalse(presentation.assertsServiceState)
        assertFalse(presentation.assertsSensorState)
        assertFalse(presentation.assertsBleState)
    }

    @Test
    fun `FFR5 baseline failure is visible without fabricated zero counts or service advance`() {
        val presentation = FirstFrameStartupCoordinator().onBaselineFailure(IllegalStateException("fixture"))

        assertEquals(FirstFramePresentation.baselineSummaryUnavailable(), presentation)
        assertNull(presentation.baselineText)
        assertFalse(presentation.fabricatesEmptyBaseline)
        assertFalse(presentation.operationTrace.contains("PERMISSION_REQUEST"))
        assertFalse(presentation.operationTrace.contains("SERVICE_START"))
    }

    @Test
    fun `FFR6 initialization exception is rendered as initialization unavailable with no downstream action`() {
        val presentation = FirstFrameStartupCoordinator().onInitializationFailure(IllegalStateException("fixture"))

        assertEquals(
            FirstFramePresentation.localInitializationUnavailable(CausalRecorderFailureClass.INITIALIZATION),
            presentation,
        )
        assertEquals(CausalRecorderFailureClass.INITIALIZATION, presentation.failureClass)
        assertFalse(presentation.operationTrace.contains("BASELINE_CAPTURE"))
        assertFalse(presentation.operationTrace.contains("PERMISSION_REQUEST"))
        assertFalse(presentation.operationTrace.contains("SERVICE_START"))
    }

    @Test
    fun `FFR7 startup order renders then prepares a fresh scope before permission assessment`() {
        val trace = FirstFrameStartupCoordinator().successfulStartupTrace()

        assertEquals(
            listOf("FIRST_FRAME", "FRESH_SCOPE_DEFERRED", "FRESH_SCOPE_ATTEMPT", "FRESH_SCOPE_READY", "PERMISSION_DECISION"),
            trace,
        )
        assertTrue(trace.contains("PERMISSION_DECISION"))
        assertFalse(trace.contains("SERVICE_START"))
        assertFalse(trace.contains("BLE_START"))
        assertFalse(trace.contains("EXPORT"))
    }

    @Test
    fun `FFR7a fresh-scope presentation does not claim legacy recovery or service activity`() {
        val preparing = FirstFrameStartupCoordinator().freshScopePreparing()
        val ready = FirstFrameStartupCoordinator().freshScopeReady()

        assertEquals(FirstFramePresentation.State.FRESH_SCOPE_PREPARING, preparing.state)
        assertEquals(FirstFramePresentation.State.FRESH_SCOPE_READY, ready.state)
        assertFalse(preparing.assertsServiceState)
        assertFalse(ready.assertsServiceState)
        assertFalse(ready.assertsBleState)
        assertFalse(ready.assertsSensorState)
    }

    @Test
    fun `FFR8 existing baseline semantics remain stable`() {
        val baseline = Build46SourceBaseline(
            retainedSessionCount = 1,
            latestRecordIndex = 7L,
            deliveryCounts = SourceDeliveryState.entries.associateWith { 0L },
        )

        val formatted = Build46BaselineFormatter.format(baseline)

        assertTrue(formatted.contains("B46 sessions=1 latest=7"))
        assertTrue(formatted.contains("BUF=0"))
        assertTrue(formatted.contains("DROP=0"))
    }
}
