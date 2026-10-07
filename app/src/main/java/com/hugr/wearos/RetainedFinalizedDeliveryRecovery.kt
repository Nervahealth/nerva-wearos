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
        // A recorded capacity/integrity anomaly (including a misnamed or
        // corrupted finalized segment) cannot be promoted to a GATT offer.
        // This recovery-only candidate's no-target branch never starts sensing.
        if (!journal.preflight().eligible) return null
        // Filesystem mtimes do not establish the user's intended source session.
        // With more than one unacknowledged session, require an explicit future
        // selection decision rather than silently choosing one or crossing over.
        if (journal.finalizedManifests().map { it.watchBootSessionId }.distinct().size > 1) return null
        val sourceSessionId = journal.oldestFinalizedSessionId() ?: return null
        val terminalManifest = journal.finalizedManifests(sourceSessionId)
            .maxByOrNull { manifest -> manifest.lastRecordIndex }
            ?: return null
        return RetainedFinalizedDeliveryTarget(sourceSessionId, terminalManifest)
    }
}
