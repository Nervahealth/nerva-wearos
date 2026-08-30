package com.hugr.wearos

import java.util.concurrent.atomic.AtomicLong

internal class ReplayStartLineageGuard {
    private val generation = AtomicLong(0L)

    fun advanceLineage(): Long = generation.incrementAndGet()

    fun capture(): Long = generation.get()

    fun isCurrent(capturedGeneration: Long): Boolean = capturedGeneration == generation.get()
}
