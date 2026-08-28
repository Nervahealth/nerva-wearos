package com.hugr.wearos

import java.util.UUID

internal object SourceReplayWindow {
    fun includesManifest(
        activeSession: UUID?,
        replayHighWaterRecordIndex: Long,
        manifest: SourceSegmentManifest,
    ): Boolean = activeSession == manifest.watchBootSessionId
        && manifest.lastRecordIndex <= replayHighWaterRecordIndex

    fun validateAcknowledgement(
        activeSession: UUID?,
        durablePhoneRecordIndex: Long,
        replayHighWaterRecordIndex: Long,
        acknowledgement: SourceSegmentAcknowledgement,
    ): UUID {
        val session = activeSession
            ?: throw SourceJournalCorruptionException("Acknowledgement arrived without an active replay session")
        if (acknowledgement.watchBootSessionId != session) {
            throw SourceJournalCorruptionException("Acknowledgement watch session mismatch")
        }
        if (acknowledgement.cumulativeRecordIndex < durablePhoneRecordIndex) {
            throw SourceJournalCorruptionException("Acknowledgement moved backwards")
        }
        if (acknowledgement.cumulativeRecordIndex > replayHighWaterRecordIndex) {
            throw SourceJournalCorruptionException("Acknowledgement exceeds frozen replay high-water index")
        }
        return session
    }
}
