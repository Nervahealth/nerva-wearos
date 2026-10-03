package com.hugr.wearos

import android.content.Context
import android.content.SharedPreferences

/**
 * Small durable metadata for one ordinary startup attempt.
 *
 * This store is separate from the retained source journal and frozen evidence.
 * It never opens, enumerates, hashes, writes, exports, acknowledges, or deletes
 * source-segment files. It is diagnostic metadata only.
 */
internal enum class NormalStartupStage {
    UI_REACHED,
    FIRST_DRAW_OBSERVED,
    FRESH_SCOPE_DEFERRED,
    FRESH_SCOPE_PREPARING,
    FRESH_SCOPE_READY,
    FRESH_SCOPE_FAILED,
    RECOVERY_DEFERRED,
    RECOVERY_IN_PROGRESS,
        JOURNAL_READY,
        JOURNAL_FAILED,
        BASELINE_ENTER,
        BASELINE_READY,
        BASELINE_FAILED,
        RECOVERY_READY_HOLD,
        PERMISSION_PATH_ENTER,
    PERMISSION_DENIED,
    SERVICE_START_REQUESTED,
    SERVICE_START_BLOCKED,
    HEALTH_ON_START_ENTER,
    HEALTH_FOREGROUND_ENTER,
    HEALTH_FOREGROUND_STARTED,
    BOUNDED_RUN_STARTED,
    BOUNDED_RUN_SAMPLE_SEEN,
    BOUNDED_RUN_STOP_REQUESTED,
    BOUNDED_RUN_FINALIZED,
    BOUNDED_RUN_FINALIZATION_FAILED,
    BOUNDED_RUN_DELIVERY_ACKNOWLEDGED,
    BOUNDED_RUN_GATT_STOP_REQUESTED,
}

internal data class NormalStartupMarker(
    val runId: String,
    val stage: NormalStartupStage,
    val recordedAtEpochMillis: Long,
)

internal class NormalStartupMarkerStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun record(runId: String, stage: NormalStartupStage) {
        preferences.edit()
            .putString(KEY_RUN_ID, runId)
            .putString(KEY_STAGE, stage.name)
            .putLong(KEY_RECORDED_AT_EPOCH_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun recordCurrent(stage: NormalStartupStage) {
        val runId = preferences.getString(KEY_RUN_ID, null) ?: return
        record(runId, stage)
    }

    fun read(): NormalStartupMarker? {
        val runId = preferences.getString(KEY_RUN_ID, null) ?: return null
        val stageName = preferences.getString(KEY_STAGE, null) ?: return null
        val stage = runCatching { NormalStartupStage.valueOf(stageName) }.getOrNull() ?: return null
        return NormalStartupMarker(
            runId = runId,
            stage = stage,
            recordedAtEpochMillis = preferences.getLong(KEY_RECORDED_AT_EPOCH_MILLIS, 0L),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "hugr_normal_startup_markers_v1"
        const val KEY_RUN_ID = "run_id"
        const val KEY_STAGE = "stage"
        const val KEY_RECORDED_AT_EPOCH_MILLIS = "recorded_at_epoch_millis"
    }
}
