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
        queuedManifestEndIndex: Long? = null,
    ): UUID {
        val session = activeSession
            ?: throw SourceJournalCorruptionException("Acknowledgement arrived without an active replay session")
        if (acknowledgement.watchBootSessionId != session) {
            throw SourceJournalCorruptionException("Acknowledgement watch session mismatch")
        }
        // The Phone's resume index proves durable *records*, not which older
        // segment manifests have been verified and acknowledged. A historical
        // manifest may end before that index. It is admissible only when it is
        // the exact currently queued endpoint, and the journal must still
        // check its full hash/session before removing that segment.
        if (acknowledgement.cumulativeRecordIndex < durablePhoneRecordIndex &&
            queuedManifestEndIndex != acknowledgement.cumulativeRecordIndex
        ) {
            throw SourceJournalCorruptionException("Acknowledgement moved backwards without queued older manifest")
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
        if (queuedManifestEndIndex != null) return null
        val inWindow = manifests.asSequence()
            .filter { includesManifest(activeSession, replayHighWaterRecordIndex, it) }
            .sortedBy { it.firstRecordIndex }
            .toList()
        // The durable record high-water cannot stand in for a manifest-ACK
        // ledger. Queue the oldest unacknowledged segment even when its bytes
        // are already present on the Phone, then advance after its exact ACK.
        return inWindow.firstOrNull()
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
            manifest.watchBootSessionId == session
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
