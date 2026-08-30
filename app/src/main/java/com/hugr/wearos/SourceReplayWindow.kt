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

    fun nextManifestToQueue(
        activeSession: UUID?,
        durablePhoneRecordIndex: Long,
        replayHighWaterRecordIndex: Long,
        queuedManifestEndIndex: Long?,
        manifests: List<SourceSegmentManifest>,
    ): SourceSegmentManifest? {
        if (queuedManifestEndIndex != null && queuedManifestEndIndex > durablePhoneRecordIndex) return null
        return manifests.asSequence()
            .filter { includesManifest(activeSession, replayHighWaterRecordIndex, it) }
            .filter { it.lastRecordIndex > durablePhoneRecordIndex }
            .sortedBy { it.firstRecordIndex }
            .firstOrNull()
    }

    fun replayReadUpperBound(
        replayHighWaterRecordIndex: Long,
        queuedManifestEndIndex: Long?,
    ): Long? = queuedManifestEndIndex?.coerceAtMost(replayHighWaterRecordIndex)

    fun validateQueuedManifestAcknowledgement(
        queuedManifestEndIndex: Long?,
        acknowledgement: SourceSegmentAcknowledgement,
    ) {
        if (queuedManifestEndIndex == null) {
            throw SourceJournalCorruptionException("Acknowledgement arrived without a queued replay manifest")
        }
        if (acknowledgement.cumulativeRecordIndex != queuedManifestEndIndex) {
            throw SourceJournalCorruptionException("Acknowledgement did not match the queued replay manifest endpoint")
        }
    }
}
