package com.hugr.wearos

/**
 * Fixed policy for the first fresh-session ordinary-recording admission.
 * The candidate does not expose an open-ended collection control.
 */
internal object BoundedOrdinaryRunPolicy {
    const val SHORT_ADMISSION_DURATION_MS = 5 * 60 * 1000L

    fun acceptsDuration(durationMs: Long): Boolean =
        durationMs == SHORT_ADMISSION_DURATION_MS
}
