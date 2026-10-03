package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class SourceReplayWindowTest {
    private val session = UUID.randomUUID()

    @Test
    fun `exact in-window acknowledgement is accepted`() {
        val acknowledgement = acknowledgement(session, 100L)
        assertEquals(
            session,
            SourceReplayWindow.validateAcknowledgement(session, 50L, 100L, acknowledgement),
        )
    }

    @Test
    fun `acknowledgement above frozen replay high water is rejected before deletion`() {
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(session, 50L, 100L, acknowledgement(session, 101L))
        }
    }

    @Test
    fun `backwards and wrong-session acknowledgements are rejected`() {
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(session, 50L, 100L, acknowledgement(session, 49L))
        }
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(session, 50L, 100L, acknowledgement(UUID.randomUUID(), 100L))
        }
    }

    @Test
    fun `manifest above frozen replay high water is deferred to a later resume`() {
        val withinWindow = manifest(session, firstRecordIndex = 1L, lastRecordIndex = 100L)
        val afterWindow = manifest(session, firstRecordIndex = 101L, lastRecordIndex = 150L)

        assertEquals(true, SourceReplayWindow.includesManifest(session, 100L, withinWindow))
        assertEquals(false, SourceReplayWindow.includesManifest(session, 100L, afterWindow))
        assertEquals(false, SourceReplayWindow.includesManifest(UUID.randomUUID(), 100L, withinWindow))
    }

    @Test
    fun `bounded finalization extends a stale replay window and queues the terminal manifest`() {
        val earlyManifest = manifest(session, firstRecordIndex = 1L, lastRecordIndex = 11L)
        val terminalManifest = manifest(session, firstRecordIndex = 12L, lastRecordIndex = 13L)

        val plan = requireNotNull(
            SourceReplayWindow.planFinalizedManifestDelivery(
                activeSession = session,
                durablePhoneRecordIndex = 11L,
                replayHighWaterRecordIndex = 11L,
                queuedManifestEndIndex = null,
                finalizedManifests = listOf(earlyManifest, terminalManifest),
            ),
        )

        assertEquals(13L, plan.replayHighWaterRecordIndex)
        assertEquals(terminalManifest, plan.nextManifest)
        assertEquals(
            13L,
            SourceReplayWindow.replayReadUpperBound(
                replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex,
                queuedManifestEndIndex = plan.nextManifest?.lastRecordIndex,
            ),
        )
        SourceReplayWindow.validateAcknowledgement(
            activeSession = session,
            durablePhoneRecordIndex = 11L,
            replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex,
            acknowledgement = acknowledgement(session, 13L),
        )
        SourceReplayWindow.validateQueuedManifestAcknowledgement(
            queuedManifestEndIndex = plan.nextManifest?.lastRecordIndex,
            acknowledgement = acknowledgement(session, 13L),
        )
    }

    private fun acknowledgement(watchBootSessionId: UUID, cumulativeRecordIndex: Long) =
        SourceSegmentAcknowledgement(
            watchBootSessionId = watchBootSessionId,
            cumulativeRecordIndex = cumulativeRecordIndex,
            completedSegmentSha256 = "ab".repeat(32),
        )

    private fun manifest(watchBootSessionId: UUID, firstRecordIndex: Long, lastRecordIndex: Long) =
        SourceSegmentManifest(
            segmentId = "test-segment",
            watchBootSessionId = watchBootSessionId,
            firstRecordIndex = firstRecordIndex,
            lastRecordIndex = lastRecordIndex,
            recordCount = lastRecordIndex - firstRecordIndex + 1L,
            byteCount = 100L,
            sha256Hex = "ab".repeat(32),
            streamRanges = emptyMap(),
        )
}
