package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SourcePayloadCodecTest {
    @Test
    fun `accelerometer payload preserves converted values and batch flags`() {
        val bytes = SourcePayloadCodec.accel(1_000, -500, 250, "FLUSH", 25, screenOn = false)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(SourcePayloadCodec.VERSION, buffer.get().toInt())
        assertEquals(0x06, buffer.get().toInt() and 0xFF)
        assertEquals(25, buffer.short.toInt())
        assertEquals(9.81f, buffer.float, 0.0001f)
        assertEquals(-4.905f, buffer.float, 0.0001f)
        assertEquals(2.4525f, buffer.float, 0.0001f)
    }

    @Test
    fun `cardiac payload preserves list provenance and contract anomaly`() {
        val bytes = SourcePayloadCodec.cardiac(
            kind = 2,
            value = 812,
            status = 0,
            callbackId = 9,
            pointIndex = 1,
            pointCount = 3,
            listIndex = 2,
            listCount = 4,
            contractAnomaly = true,
            deliveryMode = "REALTIME",
            batchSize = 3,
            screenOn = true,
        )
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(SourcePayloadCodec.VERSION, buffer.get().toInt())
        assertEquals(2, buffer.get().toInt())
        val flags = buffer.get().toInt() and 0xFF
        assertTrue((flags and 0x01) != 0)
        assertTrue((flags and 0x04) != 0)
        assertTrue((flags and 0x08) != 0)
        assertEquals(3, buffer.short.toInt())
        assertEquals(812, buffer.int)
        assertEquals(0, buffer.int)
        assertEquals(9, buffer.int)
        assertEquals(1, buffer.short.toInt())
        assertEquals(3, buffer.short.toInt())
        assertEquals(2, buffer.short.toInt())
        assertEquals(4, buffer.short.toInt())
    }

    @Test
    fun `device health payload preserves explicit data loss stream sequence bounds and reason`() {
        val bytes = SourcePayloadCodec.deviceHealth(
            batteryPercent = 72,
            flags = 0x0F,
            activeSensorMask = 0x19,
            sdkStatus = 1,
            buildVersionCode = 45,
            totalSourceRecords = 123,
            flushCount = 4,
            transportCompletedCount = 100,
            transportFailedCount = 2,
            transportTimeoutCount = 1,
            transportCoalescedAccelCount = 3,
            negotiatedMtu = 512,
            replayBacklogCount = 20,
            dataLoss = true,
            dataLossStreamCode = SourceStreamCode.ACCEL.wireCode,
            dataLossFirstSequence = 77,
            dataLossLastSequence = 79,
            dataLossReasonCode = 3,
        )
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(48)

        assertEquals(58, bytes.size)
        assertEquals(SourceStreamCode.ACCEL.wireCode, buffer.get().toInt() and 0xFF)
        assertEquals(3, buffer.get().toInt() and 0xFF)
        assertEquals(77L, buffer.int.toLong() and 0xFFFF_FFFFL)
        assertEquals(79L, buffer.int.toLong() and 0xFFFF_FFFFL)
    }
}
