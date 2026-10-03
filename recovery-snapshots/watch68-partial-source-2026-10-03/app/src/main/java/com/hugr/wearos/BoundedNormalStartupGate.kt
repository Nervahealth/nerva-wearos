package com.hugr.wearos

/**
 * Pure gate for the normal Watch startup path.
 *
 * The first frame may truthfully state that a fresh scope is deferred. The
 * legacy retained journal and the frozen completed-package root are never
 * consulted by this gate. Only a newly created, eligible ordinary scope can
 * advance to permission assessment and service start. This gate owns no file,
 * service, BLE, sensor, export, acknowledgement, or evidence-root operation.
 */
internal class BoundedNormalStartupGate {
    enum class Phase {
        NOT_STARTED,
        FRESH_SCOPE_DEFERRED,
        FRESH_SCOPE_PREPARING,
        FRESH_SCOPE_READY,
        FAILED,
    }

    private var phase = Phase.NOT_STARTED

    @Synchronized
    fun deferFreshScope(): Phase {
        check(phase == Phase.NOT_STARTED) { "Normal fresh scope is already scheduled" }
        phase = Phase.FRESH_SCOPE_DEFERRED
        return phase
    }

    @Synchronized
    fun beginFreshScope(): Phase {
        check(phase == Phase.FRESH_SCOPE_DEFERRED) { "Fresh scope must be deferred before it begins" }
        phase = Phase.FRESH_SCOPE_PREPARING
        return phase
    }

    @Synchronized
    fun markFreshScopeReady(): Phase {
        check(phase == Phase.FRESH_SCOPE_PREPARING) { "Only active fresh scope may become ready" }
        phase = Phase.FRESH_SCOPE_READY
        return phase
    }

    @Synchronized
    fun markFailed(): Phase {
        check(phase == Phase.FRESH_SCOPE_PREPARING) { "Only active fresh scope may fail" }
        phase = Phase.FAILED
        return phase
    }

    @Synchronized
    fun permitsPermissionPath(): Boolean = phase == Phase.FRESH_SCOPE_READY

    @Synchronized
    fun permitsServiceStart(): Boolean = phase == Phase.FRESH_SCOPE_READY

    @Synchronized
    fun snapshot(): Phase = phase
}
