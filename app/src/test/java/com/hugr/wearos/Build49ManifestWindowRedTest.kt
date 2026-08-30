package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class Build49ManifestWindowRedTest {
    private val session = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `257 retained manifests must preserve safe replay data progress`() {
        val harness = ManifestWindowHarness(session, manifestCount = 257)

        val actual = harness.releaseAndPumpFirstPage()

        assertEquals(
            "Safe progress requires no manifest overflow and replay-data admission before all retained manifests",
            SafeProgressResult(
                manifestCount = 257,
                queueCapacity = 256,
                firstManifestEnqueueFailure = null,
                criticalOverflowCount = 0L,
                pendingReplayFramesBeforeData = 1,
                firstReplayDataPageAdmitted = true,
            ),
            actual,
        )
    }

    @Test
    fun `repeated release callbacks cannot enqueue the current manifest twice`() {
        val harness = ManifestWindowHarness(session, manifestCount = 257)

        harness.enqueueCurrentManifest()
        harness.enqueueCurrentManifest()

        assertEquals(listOf(1L), harness.manifestEnqueueIndexes)
        assertEquals(1, harness.pendingReplayFrames())
    }

    @Test
    fun `exact acknowledgements advance through all 257 manifests without overflow or deadlock`() {
        val harness = ManifestWindowHarness(session, manifestCount = 257)

        harness.enqueueCurrentHealth()
        repeat(257) { zeroBasedIndex ->
            val endpoint = zeroBasedIndex + 1L
            harness.enqueueCurrentManifest()
            harness.enqueueCurrentManifest()
            assertTrue(harness.pumpFirstReplayDataPage())
            harness.completePendingNotifications()
            harness.acknowledge(endpoint)
        }

        assertEquals((1L..257L).toList(), harness.manifestEnqueueIndexes)
        assertEquals((1L..257L).toList(), harness.replayDataIndexes)
        assertEquals(257L, harness.durablePhoneRecordIndex)
        assertEquals(0L, harness.criticalOverflowCount())
        assertEquals(0, harness.pendingReplayFrames())
    }

    @Test
    fun `replay data cannot cross the queued manifest endpoint before exact acknowledgement`() {
        val upperBound = SourceReplayWindow.replayReadUpperBound(
            replayHighWaterRecordIndex = 257L,
            queuedManifestEndIndex = 1L,
        )

        assertEquals(1L, upperBound)
    }

    @Test
    fun `acknowledgement must match the currently queued manifest endpoint`() {
        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceReplayWindow.validateQueuedManifestAcknowledgement(
                queuedManifestEndIndex = 1L,
                acknowledgement = acknowledgement(2L),
            )
        }
    }

    private fun acknowledgement(endpoint: Long) = SourceSegmentAcknowledgement(
        watchBootSessionId = session,
        cumulativeRecordIndex = endpoint,
        completedSegmentSha256 = "ab".repeat(32),
    )

    private class ManifestWindowHarness(
        private val session: UUID,
        manifestCount: Int,
    ) {
        private val sourceCharacteristic = UUID.fromString("0000fef8-0000-1000-8000-00805f9b34fb")
        private val manifests = (1L..manifestCount.toLong()).map { index ->
            SourceSegmentManifest(
                segmentId = "segment-$index",
                watchBootSessionId = session,
                firstRecordIndex = index,
                lastRecordIndex = index,
                recordCount = 1L,
                byteCount = 48L,
                sha256Hex = "ab".repeat(32),
                streamRanges = emptyMap(),
            )
        }
        private val triggered = mutableListOf<Pair<GattNotificationStream, Long>>()
        private val queue = GattNotificationQueue(
            maxDepth = 256,
            ppgSoftLimit = 96,
            replayHighWaterMark = 192,
            nowElapsedMs = { 0L },
            nowWallMs = { 1_000L },
            trigger = { item ->
                triggered += item.stream to item.sourceSequence
                GattNotificationTrigger.TRIGGERED
            },
        )
        val manifestEnqueueIndexes = mutableListOf<Long>()
        val replayDataIndexes = mutableListOf<Long>()
        var durablePhoneRecordIndex = 0L
            private set
        private var queuedManifestEndIndex: Long? = null
        private var lastReplayQueuedRecordIndex = 0L
        private var firstManifestEnqueueFailure: Pair<Int, GattEnqueueResult>? = null

        fun releaseAndPumpFirstPage(): SafeProgressResult {
            enqueueCurrentHealth()
            enqueueCurrentManifest()
            val pendingBeforeData = queue.snapshot().pendingReplayFrames
            val firstReplayDataPageAdmitted = pumpFirstReplayDataPage()
            return SafeProgressResult(
                manifestCount = manifests.size,
                queueCapacity = 256,
                firstManifestEnqueueFailure = firstManifestEnqueueFailure,
                criticalOverflowCount = queue.snapshot().criticalOverflowCount,
                pendingReplayFramesBeforeData = pendingBeforeData,
                firstReplayDataPageAdmitted = firstReplayDataPageAdmitted,
            )
        }

        fun enqueueCurrentHealth() {
            if (triggered.isNotEmpty()) return
            queue.enqueue(
                stream = GattNotificationStream.DEVICE_HEALTH,
                characteristicUuid = sourceCharacteristic,
                payload = byteArrayOf(1),
                sourceSequence = 2_218_711L,
                sourceTimestampMs = 1_000L,
                origin = GattNotificationOrigin.LIVE,
                lossless = true,
            )
        }

        fun enqueueCurrentManifest() {
            val manifest = SourceReplayWindow.nextManifestToQueue(
                activeSession = session,
                durablePhoneRecordIndex = durablePhoneRecordIndex,
                replayHighWaterRecordIndex = manifests.size.toLong(),
                queuedManifestEndIndex = queuedManifestEndIndex,
                manifests = manifests,
            ) ?: return
            val result = queue.enqueue(
                stream = GattNotificationStream.DEVICE_HEALTH,
                characteristicUuid = sourceCharacteristic,
                payload = byteArrayOf(2),
                sourceSequence = manifest.firstRecordIndex,
                sourceTimestampMs = 1_000L + manifest.firstRecordIndex,
                origin = GattNotificationOrigin.REPLAY,
                lossless = true,
            )
            if (result == GattEnqueueResult.QUEUED) {
                queuedManifestEndIndex = manifest.lastRecordIndex
                manifestEnqueueIndexes += manifest.lastRecordIndex
            } else {
                firstManifestEnqueueFailure = manifest.firstRecordIndex.toInt() to result
            }
        }

        fun pumpFirstReplayDataPage(): Boolean {
            if (queue.snapshot().pendingReplayFrames >= 4) return false
            val upperBound = SourceReplayWindow.replayReadUpperBound(
                replayHighWaterRecordIndex = manifests.size.toLong(),
                queuedManifestEndIndex = queuedManifestEndIndex,
            ) ?: return false
            if (lastReplayQueuedRecordIndex >= upperBound) return false
            val nextRecordIndex = lastReplayQueuedRecordIndex + 1L
            val result = queue.enqueue(
                stream = GattNotificationStream.CARDIAC,
                characteristicUuid = sourceCharacteristic,
                payload = byteArrayOf(3),
                sourceSequence = nextRecordIndex,
                sourceTimestampMs = 2_000L + nextRecordIndex,
                origin = GattNotificationOrigin.REPLAY,
                recordCount = 1,
                lossless = true,
            )
            if (result == GattEnqueueResult.QUEUED) {
                lastReplayQueuedRecordIndex = nextRecordIndex
                replayDataIndexes += nextRecordIndex
                return true
            }
            return false
        }

        fun completePendingNotifications() {
            while (queue.snapshot().queueDepth > 0) {
                queue.onNotificationSent(success = true)
            }
        }

        fun acknowledge(endpoint: Long) {
            val acknowledgement = SourceSegmentAcknowledgement(
                watchBootSessionId = session,
                cumulativeRecordIndex = endpoint,
                completedSegmentSha256 = "ab".repeat(32),
            )
            SourceReplayWindow.validateAcknowledgement(
                activeSession = session,
                durablePhoneRecordIndex = durablePhoneRecordIndex,
                replayHighWaterRecordIndex = manifests.size.toLong(),
                acknowledgement = acknowledgement,
            )
            SourceReplayWindow.validateQueuedManifestAcknowledgement(
                queuedManifestEndIndex = queuedManifestEndIndex,
                acknowledgement = acknowledgement,
            )
            durablePhoneRecordIndex = endpoint
            queuedManifestEndIndex = null
            lastReplayQueuedRecordIndex = maxOf(lastReplayQueuedRecordIndex, endpoint)
        }

        fun pendingReplayFrames(): Int = queue.snapshot().pendingReplayFrames

        fun criticalOverflowCount(): Long = queue.snapshot().criticalOverflowCount
    }

    private data class SafeProgressResult(
        val manifestCount: Int,
        val queueCapacity: Int,
        val firstManifestEnqueueFailure: Pair<Int, GattEnqueueResult>?,
        val criticalOverflowCount: Long,
        val pendingReplayFramesBeforeData: Int,
        val firstReplayDataPageAdmitted: Boolean,
    )
}
