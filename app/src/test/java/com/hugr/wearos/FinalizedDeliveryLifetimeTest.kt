package com.hugr.wearos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizedDeliveryLifetimeTest {
    @Test fun `deadline stops delivery without implying acknowledgement`() {
        val lifetime = FinalizedDeliveryLifetime(maxDurationMs = 60_000L)
        lifetime.start(12L)
        assertTrue(lifetime.permitsDelivery(60_011L))
        assertFalse(lifetime.permitsDelivery(60_012L))
        assertFalse(lifetime.permitsDelivery(60_013L))
    }

    @Test fun `disconnect ends one attempt and rejects late acknowledgement`() {
        val lifetime = FinalizedDeliveryLifetime()
        lifetime.start(0L)
        assertTrue(lifetime.permitsDelivery(1L))
        lifetime.stop()
        assertFalse(lifetime.permitsDelivery(2L))
    }

    @Test fun `new process has no inherited permission and can start only once`() {
        val original = FinalizedDeliveryLifetime()
        original.start(100L)
        original.stop()
        val restarted = FinalizedDeliveryLifetime()
        assertFalse(restarted.permitsDelivery(101L))
        restarted.start(200L)
        assertTrue(restarted.permitsDelivery(201L))
        assertFalse(runCatching { restarted.start(202L) }.isSuccess)
    }

    @Test fun `clock rollback fails closed`() {
        val lifetime = FinalizedDeliveryLifetime()
        lifetime.start(100L)
        assertFalse(lifetime.permitsDelivery(99L))
    }
}
