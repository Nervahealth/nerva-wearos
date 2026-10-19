package com.hugr.wearos

import org.junit.Assert.*
import org.junit.Test

class WatchFreshRunDurationPolicyTest {
    private fun rejects(block: () -> Unit) {
        try { block(); fail("expected closed-policy rejection") }
        catch (_: IllegalArgumentException) {}
        catch (_: IllegalStateException) {}
    }
    @Test fun `new duration policy is a closed four-rung ladder with fixed grace`() {
        val rungs=listOf(300_000L,1_800_000L,7_200_000L,28_800_000L)
        assertEquals(rungs,listOf(WatchFreshRunDurationPolicy.FIVE_MINUTES_MS,WatchFreshRunDurationPolicy.THIRTY_MINUTES_MS,WatchFreshRunDurationPolicy.TWO_HOURS_MS,WatchFreshRunDurationPolicy.EIGHT_HOURS_MS))
        rungs.forEach { duration ->
            assertTrue(WatchFreshRunDurationPolicy.accepts(duration))
            assertEquals(duration+900_000L,WatchFreshRunDurationPolicy.lifetimeMs(duration))
            assertTrue(WatchFreshRunDurationPolicy.label(duration).isNotBlank())
        }
        assertEquals(1_800_000L,WatchFreshRunDurationPolicy.DEFAULT_DURATION_MS)
        assertEquals(2*1024*1024,WatchFreshRunDurationPolicy.MAX_RECEIPT_BYTES)
        assertEquals(2_048,WatchFreshRunDurationPolicy.MAX_MANIFESTS)
        assertEquals(2_048,WatchFreshRunDurationPolicy.MAX_ACKS)
        assertFalse(WatchFreshRunDurationPolicy.accepts(300_001L))
        assertFalse(WatchFreshRunDurationPolicy.accepts(0L))
        rejects { WatchFreshRunDurationPolicy.lifetimeMs(300_001L) }
        rejects { WatchFreshRunDurationPolicy.label(0L) }
    }
}
