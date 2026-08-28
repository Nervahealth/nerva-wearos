package com.hugr.wearos

internal class ReplayStartLineageGuard {
    private var generation = 0L

    fun advanceLineage(): Long {
        generation += 1L
        return generation
    }

    fun capture(): Long = generation

    fun isCurrent(capturedGeneration: Long): Boolean = capturedGeneration == generation
}
