package com.hugr.wearos

import java.util.UUID

/**
 * Selects a retained, unacknowledged finalized session from the fresh v2
 * ordinary journal. This intentionally does not consult ordinary-startup
 * markers: those are mutable UI/lifecycle metadata rather than source custody.
 */
internal data class RetainedFinalizedDeliveryTarget(
    val sourceSessionId: UUID,
    val terminalManifest: SourceSegmentManifest,
)

internal object RetainedFinalizedDeliveryRecovery {
    fun select(journal: SourceJournal): RetainedFinalizedDeliveryTarget? {
        val sourceSessionId = journal.oldestFinalizedSessionId() ?: return null
        val terminalManifest = journal.finalizedManifests(sourceSessionId)
            .maxByOrNull { manifest -> manifest.lastRecordIndex }
            ?: return null
        return RetainedFinalizedDeliveryTarget(sourceSessionId, terminalManifest)
    }
}
