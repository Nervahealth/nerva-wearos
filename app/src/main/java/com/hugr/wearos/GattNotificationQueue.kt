package com.hugr.wearos

import java.util.ArrayDeque
import java.util.UUID

internal enum class GattNotificationStream {
    CARDIAC,
    DEVICE_HEALTH,
    HAPTIC_RECEIPT,
    EDA,
    SKIN_TEMP,
    ACCEL,
    PPG,
}

internal enum class GattNotificationTrigger {
    TRIGGERED,
    IMMEDIATE_FAILURE,
    NOT_SUBSCRIBED,
    NO_CONNECTION,
}

internal enum class GattEnqueueResult {
    QUEUED,
    COALESCED,
    DROPPED_LOW_PRIORITY,
    CRITICAL_OVERFLOW,
}

internal data class GattNotification(
    val stream: GattNotificationStream,
    val characteristicUuid: UUID,
    val payload: ByteArray,
    val sourceSequence: Long,
    val sourceTimestampMs: Long,
    val enqueuedElapsedMs: Long,
    val enqueuedWallMs: Long,
)

internal data class GattNotificationQueueSnapshot(
    val queueDepth: Int,
    val oldestAgeMs: Long,
    val completedCount: Long,
    val failedCount: Long,
    val timeoutCount: Long,
    val droppedPpgCount: Long,
    val coalescedAccelCount: Long,
    val coalescedEdaCount: Long,
    val criticalOverflowCount: Long,
    val notSubscribedCount: Long,
    val noConnectionCount: Long,
    val resetCount: Long,
)

/**
 * Pure, completion-driven GATT notification queue.
 *
 * Android permits only one outstanding server notification. The queue copies
 * every payload at enqueue time, starts one send, and advances only after the
 * service reports onNotificationSent, an immediate trigger failure, or timeout.
 */
internal class GattNotificationQueue(
    private val maxDepth: Int = 256,
    private val ppgSoftLimit: Int = 96,
    private val timeoutMs: Long = 3_000L,
    private val nowElapsedMs: () -> Long,
    private val nowWallMs: () -> Long,
    private val trigger: (GattNotification) -> GattNotificationTrigger,
    private val onCriticalFault: (String) -> Unit = {},
) {
    init {
        require(maxDepth >= 2) { "maxDepth must be at least 2" }
        require(ppgSoftLimit in 1..maxDepth) { "ppgSoftLimit must be within queue bounds" }
        require(timeoutMs > 0) { "timeoutMs must be positive" }
    }

    private val pending = ArrayDeque<GattNotification>()
    private var inFlight: GattNotification? = null
    private var inFlightStartedElapsedMs: Long = 0L

    private var completedCount = 0L
    private var failedCount = 0L
    private var timeoutCount = 0L
    private var droppedPpgCount = 0L
    private var coalescedAccelCount = 0L
    private var coalescedEdaCount = 0L
    private var criticalOverflowCount = 0L
    private var notSubscribedCount = 0L
    private var noConnectionCount = 0L
    private var resetCount = 0L

    @Synchronized
    fun enqueue(
        stream: GattNotificationStream,
        characteristicUuid: UUID,
        payload: ByteArray,
        sourceSequence: Long,
        sourceTimestampMs: Long,
    ): GattEnqueueResult {
        val item = GattNotification(
            stream = stream,
            characteristicUuid = characteristicUuid,
            payload = payload.copyOf(),
            sourceSequence = sourceSequence,
            sourceTimestampMs = sourceTimestampMs,
            enqueuedElapsedMs = nowElapsedMs(),
            enqueuedWallMs = nowWallMs(),
        )

        if (stream == GattNotificationStream.PPG && depthLocked() >= ppgSoftLimit) {
            droppedPpgCount += 1
            return GattEnqueueResult.DROPPED_LOW_PRIORITY
        }

        if (stream == GattNotificationStream.ACCEL && replaceNewestPendingLocked(stream, item)) {
            coalescedAccelCount += 1
            return GattEnqueueResult.COALESCED
        }

        if (depthLocked() >= maxDepth) {
            when (stream) {
                GattNotificationStream.CARDIAC,
                GattNotificationStream.DEVICE_HEALTH,
                GattNotificationStream.HAPTIC_RECEIPT,
                -> {
                    if (!evictLowestPriorityPendingLocked()) {
                        criticalOverflowCount += 1
                        onCriticalFault("notification_queue_overflow:${stream.name}")
                        return GattEnqueueResult.CRITICAL_OVERFLOW
                    }
                }

                GattNotificationStream.EDA -> {
                    if (replaceNewestPendingLocked(stream, item)) {
                        coalescedEdaCount += 1
                        return GattEnqueueResult.COALESCED
                    }
                    failedCount += 1
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.ACCEL -> {
                    coalescedAccelCount += 1
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.PPG -> {
                    droppedPpgCount += 1
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.SKIN_TEMP -> {
                    failedCount += 1
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }
            }
        }

        pending.addLast(item)
        pumpLocked()
        return GattEnqueueResult.QUEUED
    }

    @Synchronized
    fun onNotificationSent(success: Boolean) {
        if (inFlight == null) return
        if (success) completedCount += 1 else failedCount += 1
        inFlight = null
        inFlightStartedElapsedMs = 0L
        pumpLocked()
    }

    @Synchronized
    fun checkTimeout(): Boolean {
        if (inFlight == null) return false
        if (nowElapsedMs() - inFlightStartedElapsedMs < timeoutMs) return false
        timeoutCount += 1
        failedCount += 1
        inFlight = null
        inFlightStartedElapsedMs = 0L
        // Do not trigger a later packet after timeout: Android may still deliver
        // a late callback for the timed-out send. The service must abort/reset the
        // GATT lineage before any new notification can be attributed safely.
        pending.clear()
        return true
    }

    @Synchronized
    fun reset() {
        pending.clear()
        inFlight = null
        inFlightStartedElapsedMs = 0L
        resetCount += 1
    }

    @Synchronized
    fun snapshot(): GattNotificationQueueSnapshot {
        val now = nowElapsedMs()
        val oldestEnqueued = listOfNotNull(inFlight?.enqueuedElapsedMs, pending.peekFirst()?.enqueuedElapsedMs).minOrNull()
        return GattNotificationQueueSnapshot(
            queueDepth = depthLocked(),
            oldestAgeMs = oldestEnqueued?.let { (now - it).coerceAtLeast(0L) } ?: 0L,
            completedCount = completedCount,
            failedCount = failedCount,
            timeoutCount = timeoutCount,
            droppedPpgCount = droppedPpgCount,
            coalescedAccelCount = coalescedAccelCount,
            coalescedEdaCount = coalescedEdaCount,
            criticalOverflowCount = criticalOverflowCount,
            notSubscribedCount = notSubscribedCount,
            noConnectionCount = noConnectionCount,
            resetCount = resetCount,
        )
    }

    private fun pumpLocked() {
        if (inFlight != null) return
        while (pending.isNotEmpty()) {
            val next = removeHighestPriorityPendingLocked()
            when (trigger(next)) {
                GattNotificationTrigger.TRIGGERED -> {
                    inFlight = next
                    inFlightStartedElapsedMs = nowElapsedMs()
                    return
                }

                GattNotificationTrigger.NOT_SUBSCRIBED -> {
                    notSubscribedCount += 1
                    failedCount += 1
                }

                GattNotificationTrigger.NO_CONNECTION -> {
                    noConnectionCount += 1
                    failedCount += 1
                }

                GattNotificationTrigger.IMMEDIATE_FAILURE -> failedCount += 1
            }
        }
    }

    private fun removeHighestPriorityPendingLocked(): GattNotification {
        var bestPriority = Int.MAX_VALUE
        pending.forEach { item ->
            bestPriority = minOf(bestPriority, streamPriority(item.stream))
        }
        val retained = ArrayDeque<GattNotification>()
        var selected: GattNotification? = null
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (selected == null && streamPriority(item.stream) == bestPriority) {
                selected = item
            } else {
                retained.addLast(item)
            }
        }
        pending.addAll(retained)
        return requireNotNull(selected)
    }

    private fun streamPriority(stream: GattNotificationStream): Int = when (stream) {
        GattNotificationStream.CARDIAC -> 0
        GattNotificationStream.DEVICE_HEALTH,
        GattNotificationStream.HAPTIC_RECEIPT,
        -> 1
        GattNotificationStream.EDA,
        GattNotificationStream.SKIN_TEMP,
        -> 2
        GattNotificationStream.ACCEL -> 3
        GattNotificationStream.PPG -> 4
    }

    private fun depthLocked(): Int = pending.size + if (inFlight == null) 0 else 1

    private fun replaceNewestPendingLocked(
        stream: GattNotificationStream,
        replacement: GattNotification,
    ): Boolean {
        val retained = ArrayDeque<GattNotification>()
        var replaced = false
        while (pending.isNotEmpty()) {
            val item = pending.removeLast()
            if (!replaced && item.stream == stream) {
                retained.addFirst(replacement)
                replaced = true
            } else {
                retained.addFirst(item)
            }
        }
        pending.addAll(retained)
        return replaced
    }

    private fun evictLowestPriorityPendingLocked(): Boolean {
        val priorities = listOf(
            GattNotificationStream.PPG,
            GattNotificationStream.ACCEL,
            GattNotificationStream.EDA,
            GattNotificationStream.SKIN_TEMP,
        )
        for (stream in priorities) {
            val retained = ArrayDeque<GattNotification>()
            var removed = false
            while (pending.isNotEmpty()) {
                val item = pending.removeFirst()
                if (!removed && item.stream == stream) {
                    removed = true
                    when (stream) {
                        GattNotificationStream.PPG -> droppedPpgCount += 1
                        GattNotificationStream.ACCEL -> coalescedAccelCount += 1
                        GattNotificationStream.EDA -> coalescedEdaCount += 1
                        else -> failedCount += 1
                    }
                } else {
                    retained.addLast(item)
                }
            }
            pending.addAll(retained)
            if (removed) return true
        }
        return false
    }
}
