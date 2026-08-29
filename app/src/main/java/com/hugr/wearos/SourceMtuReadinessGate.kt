package com.hugr.wearos

import java.util.UUID

internal data class PreparedSourceResumePlan(
    val watchBootSessionId: UUID,
    val acceptedRecordIndex: Long,
    val replayHighWaterRecordIndex: Long,
    val replayBacklogCount: Long,
) {
    init {
        require(acceptedRecordIndex >= 0L) { "acceptedRecordIndex cannot be negative" }
        require(replayHighWaterRecordIndex >= acceptedRecordIndex) {
            "replayHighWaterRecordIndex cannot precede acceptedRecordIndex"
        }
        require(replayBacklogCount == replayHighWaterRecordIndex - acceptedRecordIndex) {
            "replayBacklogCount must match the frozen replay window"
        }
    }
}

internal data class SourceReleaseDecision(
    val lineageGeneration: Long,
    val preparedResumePlan: PreparedSourceResumePlan,
    val pendingLiveRecordCount: Int,
)

internal enum class ResumePreparationResult {
    PREPARED,
    DUPLICATE,
    CONFLICT,
    STALE_LINEAGE,
}

internal enum class PendingLiveReservationResult {
    ACCEPTED,
    CAPACITY_EXCEEDED,
    STALE_LINEAGE,
}

internal data class SourceMtuReadinessSnapshot(
    val lineageGeneration: Long,
    val connected: Boolean,
    val mtuCallbackReceived: Boolean,
    val negotiatedMtu: Int,
    val attPayloadBytes: Int,
    val sourceCccdEnabled: Boolean,
    val preparedResumePlan: PreparedSourceResumePlan?,
    val released: Boolean,
    val pendingLiveRecordCount: Int,
)

/**
 * Pure, deterministic ownership of the Build 48 source-release predicate.
 *
 * This class never owns canonical records or transport work. It only decides when the active
 * BLE lineage has enough watch-side evidence to permit source-frame construction. The durable
 * SourceJournal remains authoritative across disconnects and process restarts.
 */
internal class SourceMtuReadinessGate(
    private val minimumAttPayloadBytes: Int = SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL,
    private val maxPendingLiveRecords: Int = 1_280,
) {
    private var lineageGeneration = 0L
    private var connected = false
    private var mtuCallbackReceived = false
    private var negotiatedMtu = DEFAULT_GATT_MTU
    private var sourceCccdEnabled = false
    private var preparedResumePlan: PreparedSourceResumePlan? = null
    private var released = false
    private var pendingLiveRecordCount = 0

    init {
        require(minimumAttPayloadBytes > 0) { "minimumAttPayloadBytes must be positive" }
        require(maxPendingLiveRecords > 0) { "maxPendingLiveRecords must be positive" }
    }

    fun onConnected(): Long {
        lineageGeneration += 1L
        connected = true
        resetLineageState()
        return lineageGeneration
    }

    fun onDisconnected(): Long {
        lineageGeneration += 1L
        connected = false
        resetLineageState()
        return lineageGeneration
    }

    fun onMtuChanged(lineage: Long, negotiatedMtu: Int): Boolean {
        if (!isCurrentConnectedLineage(lineage)) return false
        mtuCallbackReceived = true
        this.negotiatedMtu = negotiatedMtu.coerceAtLeast(0)
        return true
    }

    fun onSourceCccdChanged(lineage: Long, enabled: Boolean): Boolean {
        if (!isCurrentConnectedLineage(lineage)) return false
        sourceCccdEnabled = enabled
        return true
    }

    fun prepareResume(lineage: Long, plan: PreparedSourceResumePlan): ResumePreparationResult {
        if (!isCurrentConnectedLineage(lineage)) return ResumePreparationResult.STALE_LINEAGE
        val existing = preparedResumePlan
        if (existing == null) {
            preparedResumePlan = plan
            return ResumePreparationResult.PREPARED
        }
        return if (existing == plan) ResumePreparationResult.DUPLICATE else ResumePreparationResult.CONFLICT
    }

    fun reservePendingLiveRecord(lineage: Long): PendingLiveReservationResult {
        if (!isCurrentConnectedLineage(lineage)) return PendingLiveReservationResult.STALE_LINEAGE
        if (pendingLiveRecordCount >= maxPendingLiveRecords) {
            return PendingLiveReservationResult.CAPACITY_EXCEEDED
        }
        pendingLiveRecordCount += 1
        return PendingLiveReservationResult.ACCEPTED
    }

    fun canConstructSourceFrames(lineage: Long): Boolean =
        isCurrentConnectedLineage(lineage) &&
            mtuCallbackReceived &&
            attPayloadBytes() >= minimumAttPayloadBytes &&
            sourceCccdEnabled &&
            preparedResumePlan != null

    fun takeReleaseIfReady(lineage: Long): SourceReleaseDecision? {
        if (!canConstructSourceFrames(lineage) || released) return null
        val plan = requireNotNull(preparedResumePlan)
        val decision = SourceReleaseDecision(lineage, plan, pendingLiveRecordCount)
        released = true
        pendingLiveRecordCount = 0
        return decision
    }

    fun snapshot(): SourceMtuReadinessSnapshot = SourceMtuReadinessSnapshot(
        lineageGeneration = lineageGeneration,
        connected = connected,
        mtuCallbackReceived = mtuCallbackReceived,
        negotiatedMtu = negotiatedMtu,
        attPayloadBytes = attPayloadBytes(),
        sourceCccdEnabled = sourceCccdEnabled,
        preparedResumePlan = preparedResumePlan,
        released = released,
        pendingLiveRecordCount = pendingLiveRecordCount,
    )

    private fun isCurrentConnectedLineage(lineage: Long): Boolean =
        connected && lineage == lineageGeneration

    private fun attPayloadBytes(): Int = (negotiatedMtu - ATT_OVERHEAD_BYTES).coerceAtLeast(0)

    private fun resetLineageState() {
        mtuCallbackReceived = false
        negotiatedMtu = DEFAULT_GATT_MTU
        sourceCccdEnabled = false
        preparedResumePlan = null
        released = false
        pendingLiveRecordCount = 0
    }

    private companion object {
        const val DEFAULT_GATT_MTU = 23
        const val ATT_OVERHEAD_BYTES = 3
    }
}
