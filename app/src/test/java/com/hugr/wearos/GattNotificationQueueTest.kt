package com.hugr.wearos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattNotificationQueueTest {
    private val characteristic = UUID.fromString("44444444-4444-4444-4444-444444444444")

    @Test
    fun `completion callback observes completed notification removed from in flight state`() {
        var elapsed = 0L
        lateinit var queue: GattNotificationQueue
        var depthSeenByCompletion = -1
        queue = GattNotificationQueue(
            nowElapsedMs = { elapsed },
            nowWallMs = { 1_000L + elapsed },
            trigger = { GattNotificationTrigger.TRIGGERED },
            onCompleted = { depthSeenByCompletion = queue.snapshot().queueDepth },
        )
        queue.enqueue(
            GattNotificationStream.ACCEL,
            characteristic,
            byteArrayOf(1),
            1,
            1,
            origin = GattNotificationOrigin.REPLAY,
        )

        queue.onNotificationSent(success = true)

        assertEquals(0, depthSeenByCompletion)
    }

    @Test
    fun `does not trigger a second notification before completion`() {
        var elapsed = 0L
        val triggered = mutableListOf<Long>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.EDA, characteristic, byteArrayOf(2), 2, 200)

        assertEquals(listOf(1L), triggered)
        queue.onNotificationSent(success = true)
        assertEquals(listOf(1L, 2L), triggered)
        assertEquals(1L, queue.snapshot().completedCount)
    }

    @Test
    fun `copies queued payloads before later mutation`() {
        var elapsed = 0L
        val triggeredPayloads = mutableListOf<ByteArray>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggeredPayloads += item.payload.copyOf()
            GattNotificationTrigger.TRIGGERED
        }
        val first = byteArrayOf(1)
        val second = byteArrayOf(2, 3)

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, first, 1, 100)
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, second, 2, 200)
        second[0] = 99
        queue.onNotificationSent(success = true)

        assertArrayEquals(byteArrayOf(2, 3), triggeredPayloads[1])
    }

    @Test
    fun `timeout records failure and aborts pending lineage`() {
        var elapsed = 0L
        val triggered = mutableListOf<Long>()
        val queue = queue(elapsed = { elapsed }, timeoutMs = 1_000) { item ->
            triggered += item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(2), 2, 200)

        elapsed = 999
        assertFalse(queue.checkTimeout())
        elapsed = 1_001
        assertTrue(queue.checkTimeout())
        assertEquals(listOf(1L), triggered)
        assertEquals(1L, queue.snapshot().timeoutCount)
        assertEquals(1L, queue.snapshot().failedCount)
        assertEquals(0, queue.snapshot().queueDepth)
    }

    @Test
    fun `diagnostic callback observes every trigger result without changing queue counters`() {
        GattNotificationTrigger.entries.forEach { result ->
            var elapsed = 0L
            val observed = mutableListOf<Pair<Long, GattNotificationTrigger>>()
            val queue = GattNotificationQueue(
                nowElapsedMs = { elapsed },
                nowWallMs = { 1_000L + elapsed },
                trigger = { result },
                onTriggerResult = { item, triggerResult -> observed += item.sourceSequence to triggerResult },
            )

            queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 44L, 100L)
            assertEquals(listOf(44L to result), observed)
            when (result) {
                GattNotificationTrigger.TRIGGERED -> {
                    assertEquals(1, queue.snapshot().queueDepth)
                    queue.onNotificationSent(success = true)
                    assertEquals(1L, queue.snapshot().completedCount)
                }
                GattNotificationTrigger.NOT_SUBSCRIBED -> assertEquals(1L, queue.snapshot().notSubscribedCount)
                GattNotificationTrigger.NO_CONNECTION -> assertEquals(1L, queue.snapshot().noConnectionCount)
                GattNotificationTrigger.IMMEDIATE_FAILURE -> assertEquals(1L, queue.snapshot().failedCount)
            }
        }
    }

    @Test
    fun `diagnostic timeout callback receives exact in flight item before existing reset`() {
        var elapsed = 0L
        var timedOut: GattNotification? = null
        val queue = GattNotificationQueue(
            timeoutMs = 1_000L,
            nowElapsedMs = { elapsed },
            nowWallMs = { 1_000L + elapsed },
            trigger = { GattNotificationTrigger.TRIGGERED },
            onTimedOut = { timedOut = it },
        )
        queue.enqueue(
            GattNotificationStream.ACCEL,
            characteristic,
            byteArrayOf(7, 8),
            91L,
            900L,
            origin = GattNotificationOrigin.REPLAY,
            recordCount = 5,
            lossless = true,
        )

        elapsed = 1_001L
        assertTrue(queue.checkTimeout())

        assertEquals(91L, timedOut?.sourceSequence)
        assertEquals(GattNotificationStream.ACCEL, timedOut?.stream)
        assertEquals(GattNotificationOrigin.REPLAY, timedOut?.origin)
        assertEquals(5, timedOut?.recordCount)
        assertEquals(1L, queue.snapshot().timeoutCount)
        assertEquals(0, queue.snapshot().queueDepth)
    }

    @Test
    fun `trigger-result observer exception cannot alter queue delivery semantics`() {
        val triggered = mutableListOf<GattNotification>()
        val queue = GattNotificationQueue(
            nowElapsedMs = { 0L },
            nowWallMs = { 1_000L },
            trigger = { item ->
                triggered += item
                GattNotificationTrigger.TRIGGERED
            },
            onTriggerResult = { _, _ -> error("diagnostic observer failure") },
        )

        assertEquals(
            GattEnqueueResult.QUEUED,
            queue.enqueue(GattNotificationStream.EDA, characteristic, byteArrayOf(1), 1L, 10L),
        )
        assertEquals(1, triggered.size)
        queue.onNotificationSent(success = true)
        assertEquals(1L, queue.snapshot().completedCount)
        assertEquals(0L, queue.snapshot().failedCount)
    }

    @Test
    fun `timeout observer exception cannot alter timeout reset semantics`() {
        var elapsed = 0L
        val queue = GattNotificationQueue(
            timeoutMs = 100L,
            nowElapsedMs = { elapsed },
            nowWallMs = { 1_000L + elapsed },
            trigger = { GattNotificationTrigger.TRIGGERED },
            onTimedOut = { error("diagnostic observer failure") },
        )
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 9L, 90L)
        elapsed = 101L

        assertTrue(queue.checkTimeout())
        assertEquals(1L, queue.snapshot().timeoutCount)
        assertEquals(1L, queue.snapshot().failedCount)
        assertEquals(0, queue.snapshot().queueDepth)
    }

    @Test
    fun `disconnect reset discards old transport lineage before resubscription`() {
        var elapsed = 0L
        val triggered = mutableListOf<Long>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.EDA, characteristic, byteArrayOf(2), 2, 200)

        queue.reset()
        queue.onNotificationSent(success = true)

        assertEquals(listOf(1L), triggered)
        assertEquals(0, queue.snapshot().queueDepth)
        assertEquals(1L, queue.snapshot().resetCount)
    }

    @Test
    fun `cardiac pressure evicts lower priority live data but never cardiac`() {
        var elapsed = 0L
        val triggered = mutableListOf<Pair<GattNotificationStream, Long>>()
        val queue = queue(elapsed = { elapsed }, maxDepth = 3, ppgSoftLimit = 3) { item ->
            triggered += item.stream to item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }
        queue.enqueue(GattNotificationStream.PPG, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.PPG, characteristic, byteArrayOf(2), 2, 200)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 3, 300)

        val result = queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(4), 4, 400)
        assertEquals(GattEnqueueResult.QUEUED, result)
        assertEquals(1L, queue.snapshot().droppedPpgCount)

        queue.onNotificationSent(true)
        queue.onNotificationSent(true)
        queue.onNotificationSent(true)
        assertTrue(triggered.contains(GattNotificationStream.CARDIAC to 4L))
        assertEquals(0L, queue.snapshot().criticalOverflowCount)
    }

    @Test
    fun `pending PPG and accelerometer cannot starve typed cardiac evidence`() {
        var elapsed = 0L
        val triggered = mutableListOf<Pair<GattNotificationStream, Long>>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.stream to item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.PPG, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.PPG, characteristic, byteArrayOf(2), 2, 200)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 3, 300)
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(4), 4, 400)

        queue.onNotificationSent(true)
        assertEquals(
            listOf(
                GattNotificationStream.PPG to 1L,
                GattNotificationStream.CARDIAC to 4L,
            ),
            triggered,
        )
    }

    @Test
    fun `accelerometer coalesces while cardiac preserves source order`() {
        var elapsed = 0L
        val triggered = mutableListOf<Pair<GattNotificationStream, Long>>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.stream to item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 2, 200)
        val result = queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 3, 300)
        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(4), 4, 400)

        assertEquals(GattEnqueueResult.COALESCED, result)
        queue.onNotificationSent(true)
        queue.onNotificationSent(true)
        queue.onNotificationSent(true)
        assertEquals(
            listOf(
                GattNotificationStream.CARDIAC to 1L,
                GattNotificationStream.CARDIAC to 4L,
                GattNotificationStream.ACCEL to 3L,
            ),
            triggered,
        )
        assertEquals(1L, queue.snapshot().coalescedAccelCount)
    }

    @Test
    fun `lossless canonical accelerometer frames are never coalesced`() {
        var elapsed = 0L
        val triggered = mutableListOf<Long>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(1), 1, 100, lossless = true)
        val second = queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 2, 200, lossless = true)
        val third = queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 3, 300, lossless = true)
        queue.onNotificationSent(true)
        queue.onNotificationSent(true)

        assertEquals(GattEnqueueResult.QUEUED, second)
        assertEquals(GattEnqueueResult.QUEUED, third)
        assertEquals(listOf(1L, 2L, 3L), triggered)
        assertEquals(0L, queue.snapshot().coalescedAccelCount)
    }

    @Test
    fun `lossless queue pressure is explicit critical overflow rather than silent replacement`() {
        var elapsed = 0L
        val faults = mutableListOf<String>()
        val queue = GattNotificationQueue(
            maxDepth = 2,
            ppgSoftLimit = 2,
            replayHighWaterMark = 2,
            nowElapsedMs = { elapsed },
            nowWallMs = { 1_000L + elapsed },
            trigger = { GattNotificationTrigger.TRIGGERED },
            onCriticalFault = faults::add,
        )

        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(1), 1, 100, lossless = true)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 2, 200, lossless = true)
        val result = queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 3, 300, lossless = true)

        assertEquals(GattEnqueueResult.CRITICAL_OVERFLOW, result)
        assertEquals(1L, queue.snapshot().criticalOverflowCount)
        assertTrue(faults.single().startsWith("lossless_notification_queue_overflow"))
    }

    @Test
    fun `overdue required context is promoted after at most eight high priority live sends`() {
        var elapsed = 0L
        val triggered = mutableListOf<Pair<GattNotificationStream, Long>>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.stream to item.sourceSequence
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 1, 100)
        for (sequence in 2L..12L) {
            queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(3), sequence, 100 + sequence)
        }
        elapsed = 600L
        repeat(8) { queue.onNotificationSent(true) }

        assertEquals(9, triggered.size)
        assertEquals(8, triggered.take(8).count { it.first == GattNotificationStream.CARDIAC })
        assertEquals(GattNotificationStream.ACCEL to 1L, triggered[8])
    }

    @Test
    fun `replay receives an opportunity after four live sends when required live context is not overdue`() {
        var elapsed = 0L
        val triggered = mutableListOf<GattNotificationOrigin>()
        val queue = queue(elapsed = { elapsed }) { item ->
            triggered += item.origin
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        repeat(6) { index ->
            queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(2), index + 2L, 200 + index.toLong())
        }
        queue.enqueue(
            GattNotificationStream.ACCEL,
            characteristic,
            byteArrayOf(9),
            50,
            50,
            origin = GattNotificationOrigin.REPLAY,
            recordCount = 5,
        )

        repeat(4) { queue.onNotificationSent(true) }
        assertEquals(
            listOf(
                GattNotificationOrigin.LIVE,
                GattNotificationOrigin.LIVE,
                GattNotificationOrigin.LIVE,
                GattNotificationOrigin.LIVE,
                GattNotificationOrigin.REPLAY,
            ),
            triggered,
        )
    }

    @Test
    fun `replay cannot pass overdue required live context`() {
        var elapsed = 0L
        val triggered = mutableListOf<Pair<GattNotificationOrigin, GattNotificationStream>>()
        val queue = queue(elapsed = { elapsed }, maxLiveBeforeReplay = 1) { item ->
            triggered += item.origin to item.stream
            GattNotificationTrigger.TRIGGERED
        }

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 1, 100)
        queue.enqueue(
            GattNotificationStream.ACCEL,
            characteristic,
            byteArrayOf(3),
            2,
            200,
            origin = GattNotificationOrigin.REPLAY,
            recordCount = 5,
        )
        elapsed = 600
        queue.onNotificationSent(true)

        assertEquals(GattNotificationOrigin.LIVE to GattNotificationStream.ACCEL, triggered[1])
    }

    @Test
    fun `per stream evidence records enqueue completion coalescing and maximum service gap`() {
        var elapsed = 0L
        val queue = queue(elapsed = { elapsed }) { GattNotificationTrigger.TRIGGERED }

        queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), 1, 100)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(2), 1, 100)
        queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(3), 2, 200)
        elapsed = 750
        queue.onNotificationSent(true)
        elapsed = 900
        queue.onNotificationSent(true)

        val snapshot = queue.snapshot()
        assertEquals(2L, snapshot.streams.getValue(GattNotificationStream.ACCEL).enqueuedCount)
        assertEquals(1L, snapshot.streams.getValue(GattNotificationStream.ACCEL).coalescedCount)
        assertEquals(1L, snapshot.streams.getValue(GattNotificationStream.ACCEL).completedCount)
        assertTrue(snapshot.streams.getValue(GattNotificationStream.ACCEL).maximumServiceGapMs >= 900L)
    }

    @Test
    fun `batched replay reduces backlog at lower Build 44 completion fixture while live context remains served`() {
        var elapsed = 0L
        var completedReplayRecords = 0
        val completedLiveStreams = mutableListOf<GattNotificationStream>()
        val queue = GattNotificationQueue(
            maxDepth = 512,
            ppgSoftLimit = 480,
            timeoutMs = 3_000,
            contextPromotionAgeMs = 500,
            maxConsecutiveHighPriorityLive = 8,
            maxLiveBeforeReplay = 4,
            replayHighWaterMark = 500,
            nowElapsedMs = { elapsed },
            nowWallMs = { 1_000L + elapsed },
            trigger = { GattNotificationTrigger.TRIGGERED },
            onCompleted = { item ->
                if (item.origin == GattNotificationOrigin.REPLAY) completedReplayRecords += item.recordCount
                else completedLiveStreams += item.stream
            },
        )
        repeat(50) { index ->
            queue.enqueue(
                GattNotificationStream.ACCEL,
                characteristic,
                byteArrayOf(9),
                index + 1L,
                index.toLong(),
                origin = GattNotificationOrigin.REPLAY,
                recordCount = 5,
            )
        }

        repeat(10) { second ->
            elapsed = second * 1_000L
            repeat(3) { beat ->
                queue.enqueue(GattNotificationStream.CARDIAC, characteristic, byteArrayOf(1), second * 3L + beat + 1L, elapsed)
            }
            queue.enqueue(GattNotificationStream.EDA, characteristic, byteArrayOf(2), second + 1L, elapsed)
            queue.enqueue(GattNotificationStream.SKIN_TEMP, characteristic, byteArrayOf(3), second + 1L, elapsed)
            queue.enqueue(GattNotificationStream.ACCEL, characteristic, byteArrayOf(4), second + 1L, elapsed)
            repeat(14) { completion ->
                elapsed = second * 1_000L + completion * 71L
                queue.onNotificationSent(true)
            }
        }

        assertTrue("Replay must complete more than zero records", completedReplayRecords > 0)
        assertTrue("Replay completions must remain within the 250-record backlog", completedReplayRecords in 1..250)
        assertTrue("A 250-record backlog must decrease", 250 - completedReplayRecords < 250)
        assertTrue(completedLiveStreams.contains(GattNotificationStream.CARDIAC))
        assertTrue(completedLiveStreams.contains(GattNotificationStream.EDA))
        assertTrue(completedLiveStreams.contains(GattNotificationStream.ACCEL))
        assertTrue(queue.snapshot().streams.getValue(GattNotificationStream.ACCEL).maximumServiceGapMs <= 2_000L)
    }

    private fun queue(
        elapsed: () -> Long,
        maxDepth: Int = 32,
        ppgSoftLimit: Int = 16,
        timeoutMs: Long = 3_000,
        contextPromotionAgeMs: Long = 500,
        maxConsecutiveHighPriorityLive: Int = 8,
        maxLiveBeforeReplay: Int = 4,
        replayHighWaterMark: Int = maxDepth,
        trigger: (GattNotification) -> GattNotificationTrigger,
    ): GattNotificationQueue {
        return GattNotificationQueue(
            maxDepth = maxDepth,
            ppgSoftLimit = ppgSoftLimit,
            timeoutMs = timeoutMs,
            contextPromotionAgeMs = contextPromotionAgeMs,
            maxConsecutiveHighPriorityLive = maxConsecutiveHighPriorityLive,
            maxLiveBeforeReplay = maxLiveBeforeReplay,
            replayHighWaterMark = replayHighWaterMark,
            nowElapsedMs = elapsed,
            nowWallMs = { 1_000L + elapsed() },
            trigger = trigger,
        )
    }
}
