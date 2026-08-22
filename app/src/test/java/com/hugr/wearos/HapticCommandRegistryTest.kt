package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticCommandRegistryTest {
    @Test
    fun duplicateSequenceReturnsOriginalResult() {
        val registry = HapticCommandRegistry(4)
        val original = HapticCommandRegistry.Result(true, 0, 3, 1)
        registry[42L] = original

        assertEquals(original, registry.get(42L))
        assertNull(registry.get(43L))
    }

    @Test
    fun registryEvictsOldestCommandAtCapacity() {
        val registry = HapticCommandRegistry(2)
        registry[1L] = HapticCommandRegistry.Result(true, 0, 1, 1)
        registry[2L] = HapticCommandRegistry.Result(true, 0, 1, 1)
        registry[3L] = HapticCommandRegistry.Result(true, 0, 1, 1)

        assertEquals(2, registry.size())
        assertNull(registry.get(1L))
    }
}
