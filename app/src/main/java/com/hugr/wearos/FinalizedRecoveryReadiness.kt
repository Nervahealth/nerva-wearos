package com.hugr.wearos

import java.util.UUID

/** UI-only attempt status. This is NOT a source-session identity or a delivery acknowledgement. */
internal enum class FinalizedRecoveryDisplay { PREPARING, READY, NOT_CONFIRMED, STOPPED }

/** Codes belong only to FINALIZED_RECOVERY_STOP_REQUESTED / BLE_SERVICE_DESTROYED events. */
internal enum class FinalizedRecoveryStopReason(val code: Int) {
    INVALID_SOURCE_ID(101),
    FOREGROUND_PROMOTION_FAILED(102),
    INITIALIZATION_FAILED(103),
    GATT_SERVICE_REGISTER_REJECTED(104),
    GATT_SERVICE_FAILED(105),
    ADVERTISING_FAILED(106),
    GATT_READINESS_FAILED(107),
    PHONE_DISCONNECTED(108),
    DEADLINE(109),
    DEADLINE_BEFORE_CONNECTION(110),
    RESUME_AFTER_DEADLINE_OR_DISCONNECT(111),
    ACK_AFTER_DEADLINE_OR_DISCONNECT(112),
    TRANSPORT_ABORT(113),
    EXACT_ACK_COMPLETED(114),
    UNEXPLAINED_DESTROY(115),
    ATTEMPT_REUSED(116),
    SERVICE_START_REJECTED(117);

    companion object {
        fun fromCode(code: Int): FinalizedRecoveryStopReason? = entries.firstOrNull { it.code == code }
        fun fromStopLabel(label: String): FinalizedRecoveryStopReason =
            entries.firstOrNull { it.name == label && it != UNEXPLAINED_DESTROY && it != EXACT_ACK_COMPLETED }
                ?: TRANSPORT_ABORT
    }
}

internal data class FinalizedRecoverySnapshot(
    val display: FinalizedRecoveryDisplay,
    val reason: FinalizedRecoveryStopReason? = null,
)

/**
 * One explicitly requested attempt, bound to one in-process Service instance.
 * A persisted causal history is never interpreted as a live readiness lease.
 * No source journal, BLE, sensing, egress or frozen-root access occurs here.
 */
internal class FinalizedRecoveryReadinessStore(
    private val leaseMs: Long = 2_500L,
    private val maxDurationMs: Long = FinalizedDeliveryLifetime.MAX_DURATION_MS,
) {
    init { require(leaseMs > 0L && maxDurationMs > 0L) }
    private var attempt: UUID? = null
    private var service: UUID? = null
    private var foreground = false
    private var advertised = false
    private var foregroundStartedAtMs: Long? = null
    private var lastHeartbeatMs: Long? = null
    private var stopped: FinalizedRecoveryStopReason? = null

    @Synchronized fun request(id: UUID) {
        attempt = id
        service = null
        foreground = false
        advertised = false
        foregroundStartedAtMs = null
        lastHeartbeatMs = null
        stopped = null
    }

    @Synchronized fun enter(id: UUID, serviceId: UUID): Boolean {
        if (attempt != id || stopped != null) return false
        if (service != null) {
            stopped = FinalizedRecoveryStopReason.ATTEMPT_REUSED
            return false
        }
        service = serviceId
        foreground = false
        advertised = false
        foregroundStartedAtMs = null
        lastHeartbeatMs = null
        return true
    }

    @Synchronized fun foreground(id: UUID, serviceId: UUID, nowMs: Long): Boolean {
        if (!current(id, serviceId) || foregroundStartedAtMs != null) return false
        foreground = true
        foregroundStartedAtMs = nowMs
        lastHeartbeatMs = nowMs
        return true
    }

    @Synchronized fun advertising(id: UUID, serviceId: UUID, nowMs: Long): Boolean {
        if (!current(id, serviceId) || deadlineExceeded(nowMs)) return false
        advertised = true
        lastHeartbeatMs = nowMs
        return true
    }

    @Synchronized fun heartbeat(id: UUID, serviceId: UUID, nowMs: Long): Boolean {
        if (!current(id, serviceId) || !foreground || deadlineExceeded(nowMs)) return false
        lastHeartbeatMs = nowMs
        return true
    }

    @Synchronized fun stop(id: UUID, serviceId: UUID, reason: FinalizedRecoveryStopReason): Boolean {
        if (!current(id, serviceId)) return false
        stopped = reason
        return true
    }

    @Synchronized fun rejectRequest(id: UUID): Boolean {
        if (attempt != id || service != null || stopped != null) return false
        stopped = FinalizedRecoveryStopReason.SERVICE_START_REJECTED
        return true
    }

    @Synchronized fun destroyed(id: UUID, serviceId: UUID): FinalizedRecoveryStopReason? {
        if (attempt != id || service != serviceId) return null
        if (stopped == null) stopped = FinalizedRecoveryStopReason.UNEXPLAINED_DESTROY
        return stopped
    }

    @Synchronized fun snapshot(id: UUID?, nowMs: Long): FinalizedRecoverySnapshot {
        if (id == null || id != attempt) return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.NOT_CONFIRMED)
        stopped?.let { return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.STOPPED, it) }
        if (service == null) return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.PREPARING)
        if (deadlineExceeded(nowMs)) {
            return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.NOT_CONFIRMED, FinalizedRecoveryStopReason.DEADLINE)
        }
        val heartbeat = lastHeartbeatMs ?: return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.PREPARING)
        if (nowMs < heartbeat || nowMs - heartbeat > leaseMs) {
            return FinalizedRecoverySnapshot(FinalizedRecoveryDisplay.NOT_CONFIRMED)
        }
        return FinalizedRecoverySnapshot(
            if (foreground && advertised) FinalizedRecoveryDisplay.READY else FinalizedRecoveryDisplay.PREPARING,
        )
    }

    private fun deadlineExceeded(nowMs: Long): Boolean {
        val started = foregroundStartedAtMs ?: return false
        return nowMs < started || nowMs - started >= maxDurationMs
    }

    private fun current(id: UUID, serviceId: UUID) = attempt == id && service == serviceId && stopped == null
}

/** Volatile on purpose: process death cannot leave a false positive READY behind. */
internal object FinalizedRecoveryReadiness {
    private val state = FinalizedRecoveryReadinessStore()
    fun request(id: UUID) = state.request(id)
    fun enter(id: UUID, serviceId: UUID) = state.enter(id, serviceId)
    fun foreground(id: UUID, serviceId: UUID, nowMs: Long) = state.foreground(id, serviceId, nowMs)
    fun advertising(id: UUID, serviceId: UUID, nowMs: Long) = state.advertising(id, serviceId, nowMs)
    fun heartbeat(id: UUID, serviceId: UUID, nowMs: Long) = state.heartbeat(id, serviceId, nowMs)
    fun stop(id: UUID, serviceId: UUID, reason: FinalizedRecoveryStopReason) = state.stop(id, serviceId, reason)
    fun rejectRequest(id: UUID) = state.rejectRequest(id)
    fun destroyed(id: UUID, serviceId: UUID) = state.destroyed(id, serviceId)
    fun snapshot(id: UUID?, nowMs: Long) = state.snapshot(id, nowMs)
}
