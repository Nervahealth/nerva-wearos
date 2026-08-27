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
    fun `disconnect reset discards old lineage before resubscription`() {
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
    fun `cardiac pressure evicts lower priority data but never cardiac`() {
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

    private fun queue(
        elapsed: () -> Long,
        maxDepth: Int = 32,
        ppgSoftLimit: Int = 16,
        timeoutMs: Long = 3_000,
        trigger: (GattNotification) -> GattNotificationTrigger,
    ): GattNotificationQueue {
        return GattNotificationQueue(
            maxDepth = maxDepth,
            ppgSoftLimit = ppgSoftLimit,
            timeoutMs = timeoutMs,
            nowElapsedMs = elapsed,
            nowWallMs = { 1_000L + elapsed() },
            trigger = trigger,
        )
    }
}
