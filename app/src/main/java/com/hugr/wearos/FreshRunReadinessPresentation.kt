package com.hugr.wearos

import java.util.UUID

/**
 * Pure presentation policy for the explicit fresh-run controls. It deliberately
 * describes receipt and process-local state only; neither is evidence of live
 * sensors, timer execution, or physically validated hardware activity.
 */
internal data class FreshRunUiIdentity(
    val sourceId: UUID,
    val runId: UUID,
)

internal data class FreshRunObservedReceipt(
    val identity: FreshRunUiIdentity,
    val revision: Long,
    val phase: FreshRunPhase,
    val failure: String,
    val supportedMask: Int,
    val startedMask: Int,
    val manifestCount: Int,
    val acknowledgementCount: Int,
)

internal data class FreshRunReadinessInput(
    val receiptReadResolved: Boolean,
    val selectedReceipt: FreshRunObservedReceipt?,
    val activeRuntime: FreshRunUiIdentity?,
    val receiptOnly: Boolean,
    /** A Start click accepted on this Activity, including permission/admission work. */
    val startRequestedOnThisScreen: Boolean,
    /** Identity returned by FreshRunRuntime.admit() on this Activity, if any. */
    val admittedOnThisScreen: FreshRunUiIdentity?,
)

internal data class FreshRunReadinessPresentation(
    val heading: String,
    val primary: String,
    val startPotentiallyPermitted: Boolean,
)

internal object FreshRunReadinessPresenter {
    fun present(input: FreshRunReadinessInput): FreshRunReadinessPresentation {
        if (!input.receiptReadResolved) {
            return FreshRunReadinessPresentation(
                heading = "FRESH RECEIPT CHECKING · START LOCKED",
                primary = "No receipt decision has completed. No fresh run is admitted.",
                startPotentiallyPermitted = false,
            )
        }

        val receipt = input.selectedReceipt
        val matchingNewReceipt = input.admittedOnThisScreen?.let { admitted ->
            receipt?.identity == admitted
        } == true

        if (input.startRequestedOnThisScreen) {
            if (input.admittedOnThisScreen == null) {
                return FreshRunReadinessPresentation(
                    heading = "NEW RUN ADMISSION PENDING",
                    primary = pendingSummary(receipt),
                    startPotentiallyPermitted = false,
                )
            }
            if (matchingNewReceipt) {
                val current = requireNotNull(receipt)
                return FreshRunReadinessPresentation(
                    heading = when (current.phase) {
                        FreshRunPhase.STARTED -> "NEW RUN ADMITTED"
                        FreshRunPhase.FINALIZED -> "CURRENT RUN FINALIZED · DELIVERY SEPARATE"
                        FreshRunPhase.COMPLETED -> "APP ACK COMPLETED · PHONE CHECK SEPARATE"
                        FreshRunPhase.FAILED -> "CURRENT RUN FAILED · STOP"
                    },
                    primary = "Current receipt ${short(current.identity)} · revision ${current.revision} · ${current.phase.name} · ${current.failure}. App evidence only; sensor/timer activity and Phone custody need their own proof.",
                    startPotentiallyPermitted = false,
                )
            }
            return FreshRunReadinessPresentation(
                heading = "NEW RUN ADMISSION IN PROCESS",
                primary = "admit() returned ${short(input.admittedOnThisScreen)}. Waiting for a matching current receipt; any displayed prior receipt is not this new admission.",
                startPotentiallyPermitted = false,
            )
        }

        if (input.activeRuntime != null) {
            return FreshRunReadinessPresentation(
                heading = if (receipt?.identity == input.activeRuntime && receipt.phase == FreshRunPhase.FAILED) {
                    "CURRENT APP RUN FAILED · STOP"
                } else "CURRENT APP RUN (memory verified)",
                primary = "Process-local runtime ${short(input.activeRuntime)} · selected phase ${receipt?.phase?.name ?: "UNKNOWN"} · ${receipt?.failure ?: "UNKNOWN"}. Not newly admitted on this screen; sensors, timer, and hardware activity are not proven.",
                startPotentiallyPermitted = false,
            )
        }

        if (input.receiptOnly) {
            return FreshRunReadinessPresentation(
                heading = "CURRENT RECEIPT-ONLY MODE",
                primary = "Receipt-only mode is active. No fresh Start is available; this is not sensor or hardware proof.",
                startPotentiallyPermitted = false,
            )
        }

        if (receipt?.phase == FreshRunPhase.STARTED || receipt?.phase == FreshRunPhase.FINALIZED) {
            return FreshRunReadinessPresentation(
                heading = "PREVIOUS RUN UNRESOLVED · ${receipt.phase.name}",
                primary = "Selected saved receipt ${short(receipt.identity)} · revision ${receipt.revision}. ${receipt.phase.name} is receipt state only, not proof of sensors, timer, or hardware activity.",
                startPotentiallyPermitted = false,
            )
        }

        return FreshRunReadinessPresentation(
            heading = "READY TO START",
            primary = when (receipt) {
                null -> "No selected fresh receipt and no active app runtime. Potential UI eligibility only; no fresh run is admitted."
                else -> "Prior selected receipt ${short(receipt.identity)} · ${receipt.phase.name} · revision ${receipt.revision}. Potential UI eligibility only; no fresh run is admitted."
            },
            startPotentiallyPermitted = true,
        )
    }

    fun startPotentiallyPermitted(input: FreshRunReadinessInput): Boolean =
        present(input).startPotentiallyPermitted

    private fun pendingSummary(receipt: FreshRunObservedReceipt?): String =
        if (receipt == null) {
            "Start was requested. Permission/scope/admission work is pending; no fresh run is admitted yet."
        } else {
            "Start was requested. Prior selected receipt ${short(receipt.identity)} · ${receipt.phase.name} remains prior state, not a new admission."
        }

    fun short(identity: FreshRunUiIdentity): String =
        "source=${identity.sourceId.toString().take(8)} run=${identity.runId.toString().take(8)}"
}
