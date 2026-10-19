package com.hugr.wearos

/**
 * Closed, fresh-run-only V2 duration ladder. These values are deliberately not
 * clock calibration claims: elapsed time is used only to bind the Watch's
 * recording and delivery lifetime to its admitted receipt.
 */
internal object WatchFreshRunDurationPolicy {
    const val FIVE_MINUTES_MS = 300_000L
    const val THIRTY_MINUTES_MS = 1_800_000L
    const val TWO_HOURS_MS = 7_200_000L
    const val EIGHT_HOURS_MS = 28_800_000L
    const val DEFAULT_DURATION_MS = THIRTY_MINUTES_MS

    /** Fixed after-recording finalization/delivery grace for every V2 duration. */
    const val DELIVERY_GRACE_MS = 900_000L
    const val MAX_LIFETIME_MS = EIGHT_HOURS_MS + DELIVERY_GRACE_MS // 495 minutes

    /**
     * V2 keeps V1's human-readable m/a rows. The reconciled 2 MiB ceiling is
     * therefore intentional: a complete 2,048-entry five-stream inventory must
     * never be compacted, omitted, or relabelled to manufacture a success.
     */
    const val MAX_RECEIPT_BYTES = 2 * 1024 * 1024
    const val MAX_MANIFESTS = 2_048
    const val MAX_ACKS = 2_048
    const val MAX_MANIFEST_STREAM_RANGES = 10_240
    const val MAX_CANONICAL_RECORDS = 2_000_000L
    const val MAX_ACCEPTED_SNAPSHOT_BYTES = 512L * 1024L * 1024L

    private val admitted = setOf(
        FIVE_MINUTES_MS,
        THIRTY_MINUTES_MS,
        TWO_HOURS_MS,
        EIGHT_HOURS_MS,
    )

    fun accepts(durationMs: Long): Boolean = durationMs in admitted

    fun lifetimeMs(durationMs: Long): Long {
        require(accepts(durationMs)) { "Unadmitted fresh V2 duration" }
        return durationMs + DELIVERY_GRACE_MS
    }

    fun label(durationMs: Long): String = when (durationMs) {
        FIVE_MINUTES_MS -> "5 minutes"
        THIRTY_MINUTES_MS -> "30 minutes"
        TWO_HOURS_MS -> "2 hours"
        EIGHT_HOURS_MS -> "8 hours"
        else -> error("Unadmitted fresh V2 duration")
    }
}
