package com.hugr.wearos

/** One explicitly launched retained-delivery attempt; no automatic restart or retry. */
internal class FinalizedDeliveryLifetime(
    private val maxDurationMs: Long = MAX_DURATION_MS,
) {
    companion object {
        const val MAX_DURATION_MS = 15 * 60 * 1000L
    }

    private var startedAtElapsedMs: Long? = null
    private var stopped = false

    @Synchronized
    fun start(nowElapsedMs: Long) {
        check(startedAtElapsedMs == null && !stopped) { "Recovery lifetime cannot restart in this service" }
        startedAtElapsedMs = nowElapsedMs
    }

    @Synchronized
    fun permitsDelivery(nowElapsedMs: Long): Boolean {
        val start = startedAtElapsedMs ?: return false
        if (stopped) return false
        if (nowElapsedMs - start >= maxDurationMs || nowElapsedMs < start) {
            stopped = true
            return false
        }
        return true
    }

    @Synchronized
    fun stop() {
        stopped = true
    }
}
