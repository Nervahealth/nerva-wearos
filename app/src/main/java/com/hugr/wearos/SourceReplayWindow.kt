package com.hugr.wearos

import java.util.UUID

internal object SourceReplayWindow {
    data class FinalizedManifestDeliveryPlan(
        val replayHighWaterRecordIndex: Long,
        val nextManifest: SourceSegmentManifest?,
    )

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
        // The Phone may have durably appended every record in a final segment
        // before it has received that segment's manifest. In that manifest-only
        // case, the endpoint equals its resume index and must still be queued
        // exactly once for hash verification and acknowledgement.
        if (queuedManifestEndIndex != null && queuedManifestEndIndex >= durablePhoneRecordIndex) return null
        val inWindow = manifests.asSequence()
            .filter { includesManifest(activeSession, replayHighWaterRecordIndex, it) }
            .filter { it.lastRecordIndex >= durablePhoneRecordIndex }
            .sortedBy { it.firstRecordIndex }
            .toList()
        // Ordinary replay must prefer records the Phone does not yet have. Only
        // if no later retained manifest exists may it queue the equality-only
        // terminal manifest for hash verification and exact acknowledgement.
        return inWindow.firstOrNull { it.lastRecordIndex > durablePhoneRecordIndex }
            ?: inWindow.firstOrNull { it.lastRecordIndex == durablePhoneRecordIndex }
    }

    /**
     * Finalization happens after a live resume window has deliberately frozen its
     * high-water mark. A newly sealed current-session segment is durable but lies
     * outside that old window. Extend the active window from durable manifests and
     * select the next normal replay manifest; never emit a manifest outside the
     * acknowledgement endpoint machinery.
     */
    fun planFinalizedManifestDelivery(
        activeSession: UUID?,
        durablePhoneRecordIndex: Long,
        replayHighWaterRecordIndex: Long,
        queuedManifestEndIndex: Long?,
        finalizedManifests: List<SourceSegmentManifest>,
    ): FinalizedManifestDeliveryPlan? {
        val session = activeSession ?: return null
        val sessionManifests = finalizedManifests.filter { manifest ->
            manifest.watchBootSessionId == session && manifest.lastRecordIndex >= durablePhoneRecordIndex
        }
        if (sessionManifests.isEmpty()) return null
        val expandedHighWater = maxOf(
            replayHighWaterRecordIndex,
            sessionManifests.maxOf { it.lastRecordIndex },
        )
        return FinalizedManifestDeliveryPlan(
            replayHighWaterRecordIndex = expandedHighWater,
            nextManifest = nextManifestToQueue(
                activeSession = session,
                durablePhoneRecordIndex = durablePhoneRecordIndex,
                replayHighWaterRecordIndex = expandedHighWater,
                queuedManifestEndIndex = queuedManifestEndIndex,
                manifests = sessionManifests,
            ),
        )
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
