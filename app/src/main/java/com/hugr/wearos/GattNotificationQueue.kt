package com.hugr.wearos

import java.util.ArrayDeque
import java.util.EnumMap
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

internal enum class GattNotificationOrigin {
    LIVE,
    REPLAY,
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
    val origin: GattNotificationOrigin = GattNotificationOrigin.LIVE,
    val recordCount: Int = 1,
    val lossless: Boolean = false,
)

internal data class GattStreamQueueSnapshot(
    val enqueuedCount: Long,
    val completedCount: Long,
    val coalescedCount: Long,
    val droppedCount: Long,
    val oldestAgeMs: Long,
    val maximumServiceGapMs: Long,
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
    val pendingReplayFrames: Int,
    val consecutiveHighPriorityLiveSends: Int,
    val consecutiveLiveSendsSinceReplay: Int,
    val streams: Map<GattNotificationStream, GattStreamQueueSnapshot>,
)

/**
 * Completion-driven GATT notification queue with bounded Build 45 fairness.
 *
 * Android permits only one outstanding server notification. Every payload is
 * copied at enqueue time and the queue advances only after onNotificationSent,
 * an immediate trigger failure, or a transport timeout.
 *
 * Typed cardiac retains normal priority. Under sustained cardiac pressure,
 * required live context older than [contextPromotionAgeMs] is selected after at
 * most [maxConsecutiveHighPriorityLive] high-priority live selections. While a
 * replay backlog exists, a replay frame receives an opportunity after at most
 * [maxLiveBeforeReplay] live selections, provided no required live context is
 * overdue and the queue is below [replayHighWaterMark].
 */
internal class GattNotificationQueue(
    private val maxDepth: Int = 256,
    private val ppgSoftLimit: Int = 96,
    private val timeoutMs: Long = 3_000L,
    private val contextPromotionAgeMs: Long = 500L,
    private val maxConsecutiveHighPriorityLive: Int = 8,
    private val maxLiveBeforeReplay: Int = 4,
    private val replayHighWaterMark: Int = 192,
    private val nowElapsedMs: () -> Long,
    private val nowWallMs: () -> Long,
    private val trigger: (GattNotification) -> GattNotificationTrigger,
    private val onCriticalFault: (String) -> Unit = {},
    private val onTriggered: (GattNotification) -> Unit = {},
    private val onCompleted: (GattNotification) -> Unit = {},
    private val onFailed: (GattNotification) -> Unit = {},
    private val onTriggerResult: (GattNotification, GattNotificationTrigger) -> Unit = { _, _ -> },
    private val onTimedOut: (GattNotification) -> Unit = {},
) {
    init {
        require(maxDepth >= 2) { "maxDepth must be at least 2" }
        require(ppgSoftLimit in 1..maxDepth) { "ppgSoftLimit must be within queue bounds" }
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        require(contextPromotionAgeMs >= 0) { "contextPromotionAgeMs cannot be negative" }
        require(maxConsecutiveHighPriorityLive > 0) { "high-priority fairness bound must be positive" }
        require(maxLiveBeforeReplay > 0) { "live/replay fairness bound must be positive" }
        require(replayHighWaterMark in 1..maxDepth) { "replay high-water mark must be within queue bounds" }
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
    private var consecutiveHighPriorityLiveSends = 0
    private var consecutiveLiveSendsSinceReplay = 0

    private val enqueuedByStream = longMap()
    private val completedByStream = longMap()
    private val coalescedByStream = longMap()
    private val droppedByStream = longMap()
    private val lastCompletedElapsedByStream = EnumMap<GattNotificationStream, Long>(GattNotificationStream::class.java)
    private val maximumServiceGapByStream = longMap()

    @Synchronized
    fun enqueue(
        stream: GattNotificationStream,
        characteristicUuid: UUID,
        payload: ByteArray,
        sourceSequence: Long,
        sourceTimestampMs: Long,
        origin: GattNotificationOrigin = GattNotificationOrigin.LIVE,
        recordCount: Int = 1,
        lossless: Boolean = false,
    ): GattEnqueueResult {
        require(recordCount > 0) { "recordCount must be positive" }
        increment(enqueuedByStream, stream)
        val item = GattNotification(
            stream = stream,
            characteristicUuid = characteristicUuid,
            payload = payload.copyOf(),
            sourceSequence = sourceSequence,
            sourceTimestampMs = sourceTimestampMs,
            enqueuedElapsedMs = nowElapsedMs(),
            enqueuedWallMs = nowWallMs(),
            origin = origin,
            recordCount = recordCount,
            lossless = lossless,
        )

        if (origin == GattNotificationOrigin.LIVE && stream == GattNotificationStream.PPG && depthLocked() >= ppgSoftLimit) {
            droppedPpgCount += 1
            increment(droppedByStream, stream)
            return GattEnqueueResult.DROPPED_LOW_PRIORITY
        }

        if (!lossless && origin == GattNotificationOrigin.LIVE && stream == GattNotificationStream.ACCEL && replaceNewestPendingLocked(stream, origin, item)) {
            coalescedAccelCount += 1
            increment(coalescedByStream, stream)
            return GattEnqueueResult.COALESCED
        }

        if (depthLocked() >= maxDepth) {
            if (lossless) {
                criticalOverflowCount += 1
                increment(droppedByStream, stream)
                onCriticalFault("lossless_notification_queue_overflow:${stream.name}:${origin.name}")
                return GattEnqueueResult.CRITICAL_OVERFLOW
            }
            when (stream) {
                GattNotificationStream.CARDIAC,
                GattNotificationStream.DEVICE_HEALTH,
                GattNotificationStream.HAPTIC_RECEIPT,
                -> {
                    if (!evictLowestPriorityPendingLocked()) {
                        criticalOverflowCount += 1
                        increment(droppedByStream, stream)
                        onCriticalFault("notification_queue_overflow:${stream.name}:${origin.name}")
                        return GattEnqueueResult.CRITICAL_OVERFLOW
                    }
                }

                GattNotificationStream.EDA -> {
                    if (origin == GattNotificationOrigin.LIVE && replaceNewestPendingLocked(stream, origin, item)) {
                        coalescedEdaCount += 1
                        increment(coalescedByStream, stream)
                        return GattEnqueueResult.COALESCED
                    }
                    failedCount += 1
                    increment(droppedByStream, stream)
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.ACCEL -> {
                    if (origin == GattNotificationOrigin.LIVE) coalescedAccelCount += 1
                    increment(droppedByStream, stream)
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.PPG -> {
                    droppedPpgCount += 1
                    increment(droppedByStream, stream)
                    return GattEnqueueResult.DROPPED_LOW_PRIORITY
                }

                GattNotificationStream.SKIN_TEMP -> {
                    failedCount += 1
                    increment(droppedByStream, stream)
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
        val completed = inFlight ?: return
        if (success) {
            completedCount += 1
            increment(completedByStream, completed.stream)
            if (completed.origin == GattNotificationOrigin.LIVE) {
                val now = nowElapsedMs()
                val prior = lastCompletedElapsedByStream.put(completed.stream, now)
                val queueDelay = (now - completed.enqueuedElapsedMs).coerceAtLeast(0L)
                val completionGap = prior?.let { (now - it).coerceAtLeast(0L) } ?: 0L
                maximumServiceGapByStream[completed.stream] = maxOf(
                    maximumServiceGapByStream.getValue(completed.stream),
                    queueDelay,
                    completionGap,
                )
            }
        } else {
            failedCount += 1
        }
        inFlight = null
        inFlightStartedElapsedMs = 0L
        if (success) onCompleted(completed) else onFailed(completed)
        pumpLocked()
    }

    @Synchronized
    fun checkTimeout(): Boolean {
        val timedOut = inFlight ?: return false
        if (nowElapsedMs() - inFlightStartedElapsedMs < timeoutMs) return false
        runCatching { onTimedOut(timedOut) }
        timeoutCount += 1
        failedCount += 1
        inFlight = null
        inFlightStartedElapsedMs = 0L
        pending.clear()
        consecutiveHighPriorityLiveSends = 0
        consecutiveLiveSendsSinceReplay = 0
        return true
    }

    @Synchronized
    fun reset() {
        pending.clear()
        inFlight = null
        inFlightStartedElapsedMs = 0L
        consecutiveHighPriorityLiveSends = 0
        consecutiveLiveSendsSinceReplay = 0
        resetCount += 1
    }

    @Synchronized
    fun snapshot(): GattNotificationQueueSnapshot {
        val now = nowElapsedMs()
        val all = buildList {
            inFlight?.let(::add)
            addAll(pending)
        }
        val streamSnapshots = GattNotificationStream.entries.associateWith { stream ->
            val oldest = all.asSequence()
                .filter { it.stream == stream }
                .map { it.enqueuedElapsedMs }
                .minOrNull()
            GattStreamQueueSnapshot(
                enqueuedCount = enqueuedByStream.getValue(stream),
                completedCount = completedByStream.getValue(stream),
                coalescedCount = coalescedByStream.getValue(stream),
                droppedCount = droppedByStream.getValue(stream),
                oldestAgeMs = oldest?.let { (now - it).coerceAtLeast(0L) } ?: 0L,
                maximumServiceGapMs = maximumServiceGapByStream.getValue(stream),
            )
        }
        return GattNotificationQueueSnapshot(
            queueDepth = depthLocked(),
            oldestAgeMs = all.minOfOrNull { it.enqueuedElapsedMs }?.let { (now - it).coerceAtLeast(0L) } ?: 0L,
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
            pendingReplayFrames = all.count { it.origin == GattNotificationOrigin.REPLAY },
            consecutiveHighPriorityLiveSends = consecutiveHighPriorityLiveSends,
            consecutiveLiveSendsSinceReplay = consecutiveLiveSendsSinceReplay,
            streams = streamSnapshots,
        )
    }

    private fun pumpLocked() {
        if (inFlight != null) return
        while (pending.isNotEmpty()) {
            val next = removeNextPendingLocked()
            updateFairnessCounters(next)
            val result = trigger(next)
            runCatching { onTriggerResult(next, result) }
            when (result) {
                GattNotificationTrigger.TRIGGERED -> {
                    inFlight = next
                    inFlightStartedElapsedMs = nowElapsedMs()
                    onTriggered(next)
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

    private fun removeNextPendingLocked(): GattNotification {
        val now = nowElapsedMs()
        val overdueRequiredLive = pending.filter {
            it.origin == GattNotificationOrigin.LIVE &&
                isRequiredContext(it.stream) &&
                now - it.enqueuedElapsedMs >= contextPromotionAgeMs
        }

        if (overdueRequiredLive.isNotEmpty() && consecutiveHighPriorityLiveSends >= maxConsecutiveHighPriorityLive) {
            val selected = overdueRequiredLive.minBy { it.enqueuedElapsedMs }
            return removeExactLocked(selected)
        }

        val replayAllowed = overdueRequiredLive.isEmpty() &&
            consecutiveLiveSendsSinceReplay >= maxLiveBeforeReplay &&
            depthLocked() < replayHighWaterMark
        if (replayAllowed) {
            removeFirstMatchingLocked { it.origin == GattNotificationOrigin.REPLAY }?.let { return it }
        }

        val bestLivePriority = pending.asSequence()
            .filter { it.origin == GattNotificationOrigin.LIVE }
            .map { streamPriority(it.stream) }
            .minOrNull()
        if (bestLivePriority != null) {
            return requireNotNull(removeFirstMatchingLocked {
                it.origin == GattNotificationOrigin.LIVE && streamPriority(it.stream) == bestLivePriority
            })
        }

        return pending.removeFirst()
    }

    private fun updateFairnessCounters(selected: GattNotification) {
        if (selected.origin == GattNotificationOrigin.REPLAY) {
            consecutiveLiveSendsSinceReplay = 0
            return
        }
        consecutiveLiveSendsSinceReplay += 1
        if (isHighPriority(selected.stream)) {
            consecutiveHighPriorityLiveSends += 1
        } else if (isRequiredContext(selected.stream)) {
            consecutiveHighPriorityLiveSends = 0
        }
    }

    private fun streamPriority(stream: GattNotificationStream): Int = when (stream) {
        GattNotificationStream.CARDIAC -> 0
        GattNotificationStream.HAPTIC_RECEIPT -> 1
        GattNotificationStream.DEVICE_HEALTH -> 2
        GattNotificationStream.EDA,
        GattNotificationStream.SKIN_TEMP,
        -> 3
        GattNotificationStream.ACCEL -> 4
        GattNotificationStream.PPG -> 5
    }

    private fun isHighPriority(stream: GattNotificationStream): Boolean =
        stream == GattNotificationStream.CARDIAC || stream == GattNotificationStream.HAPTIC_RECEIPT

    private fun isRequiredContext(stream: GattNotificationStream): Boolean = when (stream) {
        GattNotificationStream.DEVICE_HEALTH,
        GattNotificationStream.EDA,
        GattNotificationStream.SKIN_TEMP,
        GattNotificationStream.ACCEL,
        -> true
        else -> false
    }

    private fun depthLocked(): Int = pending.size + if (inFlight == null) 0 else 1

    private fun replaceNewestPendingLocked(
        stream: GattNotificationStream,
        origin: GattNotificationOrigin,
        replacement: GattNotification,
    ): Boolean {
        val retained = ArrayDeque<GattNotification>()
        var replaced = false
        while (pending.isNotEmpty()) {
            val item = pending.removeLast()
            if (!replaced && item.stream == stream && item.origin == origin) {
                retained.addFirst(replacement)
                replaced = true
            } else {
                retained.addFirst(item)
            }
        }
        pending.addAll(retained)
        return replaced
    }

    private fun removeExactLocked(target: GattNotification): GattNotification {
        return requireNotNull(removeFirstMatchingLocked { it === target })
    }

    private fun removeFirstMatchingLocked(predicate: (GattNotification) -> Boolean): GattNotification? {
        val retained = ArrayDeque<GattNotification>()
        var selected: GattNotification? = null
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (selected == null && predicate(item)) selected = item else retained.addLast(item)
        }
        pending.addAll(retained)
        return selected
    }

    private fun evictLowestPriorityPendingLocked(): Boolean {
        val candidates = listOf(
            GattNotificationStream.PPG,
            GattNotificationStream.ACCEL,
            GattNotificationStream.EDA,
            GattNotificationStream.SKIN_TEMP,
        )
        for (stream in candidates) {
            val removed = removeFirstMatchingLocked {
                !it.lossless && it.origin == GattNotificationOrigin.LIVE && it.stream == stream
            } ?: continue
            when (removed.stream) {
                GattNotificationStream.PPG -> droppedPpgCount += 1
                GattNotificationStream.ACCEL -> coalescedAccelCount += 1
                GattNotificationStream.EDA -> coalescedEdaCount += 1
                else -> failedCount += 1
            }
            increment(droppedByStream, removed.stream)
            return true
        }
        return false
    }

    private fun longMap(): EnumMap<GattNotificationStream, Long> =
        EnumMap<GattNotificationStream, Long>(GattNotificationStream::class.java).apply {
            GattNotificationStream.entries.forEach { put(it, 0L) }
        }

    private fun increment(map: EnumMap<GattNotificationStream, Long>, stream: GattNotificationStream) {
        map[stream] = map.getValue(stream) + 1L
    }
}
