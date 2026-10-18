package com.hugr.wearos

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshRunReadinessPresentationTest {
    private fun identity() = FreshRunUiIdentity(UUID.randomUUID(), UUID.randomUUID())
    private fun receipt(identity: FreshRunUiIdentity, phase: FreshRunPhase) = FreshRunObservedReceipt(
        identity = identity,
        revision = 3,
        phase = phase,
        failure = if (phase == FreshRunPhase.FAILED) "PROCESS_INTERRUPTED" else "NONE",
        supportedMask = 16,
        startedMask = 16,
        manifestCount = 0,
        acknowledgementCount = 0,
    )
    private fun input(
        resolved: Boolean = true,
        receipt: FreshRunObservedReceipt? = null,
        active: FreshRunUiIdentity? = null,
        receiptOnly: Boolean = false,
        requested: Boolean = false,
        admitted: FreshRunUiIdentity? = null,
    ) = FreshRunReadinessInput(resolved, receipt, active, receiptOnly, requested, admitted)

    @Test fun `only absent FAILED and COMPLETED receipt states are potentially fresh Start eligible`() {
        assertTrue(FreshRunReadinessPresenter.present(input()).startPotentiallyPermitted)
        listOf(FreshRunPhase.FAILED, FreshRunPhase.COMPLETED).forEach { phase ->
            assertTrue(FreshRunReadinessPresenter.present(input(receipt = receipt(identity(), phase))).startPotentiallyPermitted)
        }
        listOf(FreshRunPhase.STARTED, FreshRunPhase.FINALIZED).forEach { phase ->
            val presentation = FreshRunReadinessPresenter.present(input(receipt = receipt(identity(), phase)))
            assertFalse(presentation.startPotentiallyPermitted)
            assertTrue(presentation.heading.contains("PREVIOUS RUN UNRESOLVED"))
        }
    }

    @Test fun `read pending active runtime and receipt only never offer a fresh Start`() {
        assertFalse(FreshRunReadinessPresenter.present(input(resolved = false)).startPotentiallyPermitted)
        val current = identity()
        val active = FreshRunReadinessPresenter.present(input(active = current))
        assertFalse(active.startPotentiallyPermitted)
        assertTrue(active.heading.contains("CURRENT APP RUN (memory verified)"))
        assertFalse(FreshRunReadinessPresenter.present(input(receiptOnly = true)).startPotentiallyPermitted)
    }

    @Test fun `requested Start never lets an old STARTED receipt stand in for a new admission`() {
        val old = identity()
        val pending = FreshRunReadinessPresenter.present(input(receipt = receipt(old, FreshRunPhase.STARTED), requested = true))
        assertTrue(pending.heading.contains("NEW RUN ADMISSION PENDING"))
        assertFalse(pending.heading.contains("NEW RUN ADMITTED"))
        assertFalse(pending.startPotentiallyPermitted)
    }

    @Test fun `NEW RUN ADMITTED requires both returned source and run identities to match observed receipt`() {
        val admitted = identity()
        val sameRunDifferentSource = FreshRunUiIdentity(UUID.randomUUID(), admitted.runId)
        val mismatch = FreshRunReadinessPresenter.present(
            input(receipt = receipt(sameRunDifferentSource, FreshRunPhase.STARTED), requested = true, admitted = admitted),
        )
        assertTrue(mismatch.heading.contains("NEW RUN ADMISSION IN PROCESS"))
        assertFalse(mismatch.heading.contains("NEW RUN ADMITTED"))

        val matched = FreshRunReadinessPresenter.present(
            input(receipt = receipt(admitted, FreshRunPhase.STARTED), requested = true, admitted = admitted),
        )
        assertTrue(matched.heading.contains("NEW RUN ADMITTED"))
        assertFalse(matched.startPotentiallyPermitted)
    }

    @Test fun `saved STARTED after recreation remains unresolved rather than newly admitted on this screen`() {
        val saved = receipt(identity(), FreshRunPhase.STARTED)
        val presentation = FreshRunReadinessPresenter.present(input(receipt = saved))
        assertTrue(presentation.heading.contains("PREVIOUS RUN UNRESOLVED"))
        assertFalse(presentation.heading.contains("NEW RUN ADMITTED"))
    }

    @Test fun `matched admission failure is prominently STOP not new-admission readiness`() {
        val current = identity()
        val presentation = FreshRunReadinessPresenter.present(input(receipt = receipt(current, FreshRunPhase.FAILED), requested = true, admitted = current))
        assertTrue(presentation.heading.contains("FAILED"))
        assertTrue(presentation.heading.contains("STOP"))
        assertTrue(presentation.primary.contains("PROCESS_INTERRUPTED"))
        assertFalse(presentation.heading.contains("NEW RUN ADMITTED"))
        assertFalse(presentation.startPotentiallyPermitted)
    }

    @Test fun `matched finalization and app completion explicitly keep Phone custody separate`() {
        val current = identity()
        val finalized = FreshRunReadinessPresenter.present(input(receipt = receipt(current, FreshRunPhase.FINALIZED), requested = true, admitted = current))
        assertTrue(finalized.heading.contains("FINALIZED"))
        assertTrue(finalized.heading.contains("DELIVERY SEPARATE"))
        val completed = FreshRunReadinessPresenter.present(input(receipt = receipt(current, FreshRunPhase.COMPLETED), requested = true, admitted = current))
        assertTrue(completed.heading.contains("PHONE CHECK SEPARATE"))
        assertFalse(completed.startPotentiallyPermitted)
    }
}
