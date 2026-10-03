package com.hugr.wearos

/**
 * Small concurrency gate for the temporary fresh-scope ordinary recording.
 *
 * It admits samples only between an explicit bounded start and a controlled
 * stop request. The caller serializes journal append/finalization with the
 * same lifecycle lock, so a late callback cannot append after quiescence.
 */
internal class BoundedOrdinaryRunGate {
    enum class State {
        IDLE,
        ACCEPTING_SAMPLES,
        QUIESCING,
        FINALIZED,
        FINALIZATION_FAILED,
    }

    private var state = State.IDLE

    @Synchronized
    fun begin(): Boolean {
        if (state != State.IDLE) return false
        state = State.ACCEPTING_SAMPLES
        return true
    }

    @Synchronized
    fun acceptsSamples(): Boolean = state == State.ACCEPTING_SAMPLES

    @Synchronized
    fun requestStop(): Boolean {
        if (state != State.ACCEPTING_SAMPLES) return false
        state = State.QUIESCING
        return true
    }

    @Synchronized
    fun markFinalized(): Boolean {
        if (state != State.QUIESCING) return false
        state = State.FINALIZED
        return true
    }

    @Synchronized
    fun markFinalizationFailed(): Boolean {
        if (state != State.QUIESCING) return false
        state = State.FINALIZATION_FAILED
        return true
    }

    @Synchronized
    fun state(): State = state
}
