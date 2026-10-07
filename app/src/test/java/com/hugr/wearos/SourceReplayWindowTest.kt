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
    fun `bounded finalization extends a stale replay window but verifies older manifest first`() {
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
        assertEquals(earlyManifest, plan.nextManifest)
        assertEquals(
            11L,
            SourceReplayWindow.replayReadUpperBound(
                replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex,
                queuedManifestEndIndex = plan.nextManifest?.lastRecordIndex,
            ),
        )
        SourceReplayWindow.validateAcknowledgement(
            activeSession = session,
            durablePhoneRecordIndex = 11L,
            replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex,
            acknowledgement = acknowledgement(session, 11L),
            queuedManifestEndIndex = 11L,
        )
        SourceReplayWindow.validateQueuedManifestAcknowledgement(
            queuedManifestEndIndex = plan.nextManifest?.lastRecordIndex,
            acknowledgement = acknowledgement(session, 11L),
        )
    }

    @Test
    fun `terminal manifest remains queueable when Phone already has every terminal record`() {
        val terminalManifest = manifest(session, firstRecordIndex = 1L, lastRecordIndex = 13L)

        val plan = requireNotNull(
            SourceReplayWindow.planFinalizedManifestDelivery(
                activeSession = session,
                durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
                replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
                queuedManifestEndIndex = null,
                finalizedManifests = listOf(terminalManifest),
            ),
        )

        assertEquals(terminalManifest.lastRecordIndex, plan.replayHighWaterRecordIndex)
        assertEquals(terminalManifest, plan.nextManifest)
        assertEquals(
            terminalManifest,
            SourceReplayWindow.nextManifestToQueue(
                activeSession = session,
                durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
                replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
                queuedManifestEndIndex = null,
                manifests = listOf(terminalManifest),
            ),
        )
        SourceReplayWindow.validateAcknowledgement(
            activeSession = session,
            durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
            replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
            acknowledgement = acknowledgement(session, terminalManifest.lastRecordIndex),
        )
        SourceReplayWindow.validateQueuedManifestAcknowledgement(
            queuedManifestEndIndex = terminalManifest.lastRecordIndex,
            acknowledgement = acknowledgement(session, terminalManifest.lastRecordIndex),
        )
    }

    @Test
    fun `equal-endpoint terminal manifest is not queued twice and rejects mismatched acknowledgement`() {
        val terminalManifest = manifest(session, firstRecordIndex = 1L, lastRecordIndex = 13L)

        assertEquals(
            terminalManifest,
            SourceReplayWindow.nextManifestToQueue(
                activeSession = session,
                durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
                replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
                queuedManifestEndIndex = null,
                manifests = listOf(terminalManifest),
            ),
        )
        assertEquals(
            null,
            SourceReplayWindow.nextManifestToQueue(
                activeSession = session,
                durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
                replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
                queuedManifestEndIndex = terminalManifest.lastRecordIndex,
                manifests = listOf(terminalManifest),
            ),
        )
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(
                activeSession = session,
                durablePhoneRecordIndex = terminalManifest.lastRecordIndex,
                replayHighWaterRecordIndex = terminalManifest.lastRecordIndex,
                acknowledgement = acknowledgement(UUID.randomUUID(), terminalManifest.lastRecordIndex),
            )
        }
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateQueuedManifestAcknowledgement(
                queuedManifestEndIndex = terminalManifest.lastRecordIndex,
                acknowledgement = acknowledgement(session, terminalManifest.lastRecordIndex - 1L),
            )
        }
    }

    @Test
    fun `all retained segments are verified in order even when Phone already stores later records`() {
        val main = manifest(session, 1L, 8182L)
        val health = manifest(session, 8183L, 8184L)
        var pending = listOf(main, health)
        val durablePhoneIndex = 8184L
        val highWater = health.lastRecordIndex

        val first = requireNotNull(SourceReplayWindow.nextManifestToQueue(
            session, durablePhoneIndex, highWater, null, pending,
        ))
        assertEquals(main, first)
        assertEquals(null, SourceReplayWindow.nextManifestToQueue(
            session, durablePhoneIndex, highWater, first.lastRecordIndex, pending,
        ))
        SourceReplayWindow.validateAcknowledgement(session, durablePhoneIndex, highWater,
            acknowledgement(session, main.lastRecordIndex), first.lastRecordIndex)
        SourceReplayWindow.validateQueuedManifestAcknowledgement(first.lastRecordIndex,
            acknowledgement(session, main.lastRecordIndex))
        pending = pending - first // Only exact journal ACK removes the first segment.
        val second = requireNotNull(SourceReplayWindow.nextManifestToQueue(
            session, durablePhoneIndex, highWater, null, pending,
        ))
        assertEquals(health, second)
        SourceReplayWindow.validateAcknowledgement(session, durablePhoneIndex, highWater,
            acknowledgement(session, health.lastRecordIndex), second.lastRecordIndex)
        pending = pending - second
        assertEquals(true, pending.isEmpty())
        assertEquals(null, SourceReplayWindow.nextManifestToQueue(
            session, durablePhoneIndex, highWater, null, pending,
        ))
    }

    @Test
    fun `backwards acknowledgement is never admitted without exactly queued older unacknowledged manifest`() {
        val old = acknowledgement(session, 8182L)
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(session, 8184L, 8184L, old, null)
        }
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateAcknowledgement(session, 8184L, 8184L, old, 8184L)
        }
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
