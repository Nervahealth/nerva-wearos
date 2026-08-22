package com.hugr.wearos

/**
 * Bounded in-memory command registry used to suppress BLE retry duplicates.
 * A duplicate returns the original Android-request result; it never requests a
 * second notification vibration for the same command sequence in one process.
 */
class HapticCommandRegistry(private val capacity: Int = 128) {
    data class Result(
        val accepted: Boolean,
        val detailCode: Int,
        val patternId: Int,
        val policyVersion: Int
    )

    private val values = object : LinkedHashMap<Long, Result>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Result>?): Boolean = size > capacity
    }

    @Synchronized operator fun get(commandSequence: Long): Result? = values[commandSequence]

    @Synchronized operator fun set(commandSequence: Long, result: Result) {
        values[commandSequence] = result
    }

    @Synchronized fun size(): Int = values.size
}
