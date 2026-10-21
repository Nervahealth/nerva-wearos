package com.hugr.wearos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CardiacInputShapeTest {
    @Test
    fun `nulls at unequal positions retain exact positional slots`() {
        val shape = CardiacInputShape.capture(
            valuesGetter = { listOf(700, null, 720) },
            statusesGetter = { listOf(null, 1, 2) },
        )

        assertEquals(CardiacInputFieldState.LIST, shape.valuesField.state)
        assertEquals(CardiacInputFieldState.LIST, shape.statusesField.state)
        assertEquals(3, shape.capturedSlotCount)
        assertTrue(shape.contractAnomaly)
        assertEquals(
            listOf(
                CardiacInputSlotKind.INT32 to CardiacInputSlotKind.NULL,
                CardiacInputSlotKind.NULL to CardiacInputSlotKind.INT32,
                CardiacInputSlotKind.INT32 to CardiacInputSlotKind.INT32,
            ),
            shape.slots.map { it.valueKind to it.statusKind },
        )
        assertEquals(0, shape.slots[0].statusBits)
        assertEquals(0, shape.slots[1].valueBits)
    }

    @Test
    fun `field states and access failures remain explicit without stringification or conversion`() {
        var valuesGetterCalls = 0
        var statusesGetterCalls = 0
        val onceOnly = CardiacInputShape.capture(
            {
                valuesGetterCalls += 1
                listOf(700)
            },
            {
                statusesGetterCalls += 1
                listOf(0)
            },
        )
        assertEquals(1, valuesGetterCalls)
        assertEquals(1, statusesGetterCalls)
        assertFalse(onceOnly.contractAnomaly)

        val toStringBomb = object {
            override fun toString(): String = throw AssertionError("must not stringify raw IBI input")
        }
        val throwingSize = object : AbstractList<Any?>() {
            override val size: Int get() = throw IllegalStateException("size")
            override fun get(index: Int): Any? = null
        }
        val throwingElement = object : AbstractList<Any?>() {
            override val size: Int get() = 2
            override fun get(index: Int): Any? = if (index == 0) Int.MIN_VALUE else throw IllegalStateException("element")
        }

        val nullShape = CardiacInputShape.capture({ null }, { null })
        assertFalse(nullShape.contractAnomaly)
        assertEquals(0, nullShape.capturedSlotCount)
        assertEquals(CardiacInputFieldState.NULL_OR_ABSENT, nullShape.valuesField.state)

        val nonList = CardiacInputShape.capture({ toStringBomb }, { listOf(0) })
        assertTrue(nonList.contractAnomaly)
        assertEquals(CardiacInputFieldState.NON_LIST, nonList.valuesField.state)
        assertEquals(CardiacInputSlotKind.ABSENT, nonList.slots.single().valueKind)

        val unknownMember = CardiacInputShape.capture({ listOf(toStringBomb) }, { listOf(0) })
        assertTrue(unknownMember.contractAnomaly)
        assertEquals(CardiacInputSlotKind.OTHER_TYPE, unknownMember.slots.single().valueKind)
        assertEquals(0, unknownMember.slots.single().valueBits)

        val getterError = CardiacInputShape.capture({ throw IllegalArgumentException("getter") }, { emptyList<Any?>() })
        assertTrue(getterError.contractAnomaly)
        assertEquals(CardiacInputFieldState.ACCESS_ERROR, getterError.valuesField.state)
        val getterErrorPayload = SourcePayloadCodec.cardiacInputShape(
            getterError.chunks(9, 0, 1, 9).single(),
            "REALTIME",
            1,
            true,
        )
        val decodedGetterError = SourcePayloadCodec.decodeCardiacInputShape(getterErrorPayload)
        assertEquals(CardiacInputFieldState.ACCESS_ERROR, decodedGetterError.valuesFieldState)
        assertEquals(0, decodedGetterError.capturedSlotCount)
        assertTrue(decodedGetterError.shapeAnomaly)
        CardiacInputShapeCodec.validateCompleteGroup(listOf(decodedGetterError))

        val sizeError = CardiacInputShape.capture({ throwingSize }, { emptyList<Any?>() })
        assertEquals(CardiacInputFieldState.ACCESS_ERROR, sizeError.valuesField.state)

        val elementError = CardiacInputShape.capture({ throwingElement }, { listOf(0, 1) })
        assertEquals(CardiacInputSlotKind.INT32, elementError.slots[0].valueKind)
        assertEquals(Int.MIN_VALUE, elementError.slots[0].valueBits)
        assertEquals(CardiacInputSlotKind.ACCESS_ERROR, elementError.slots[1].valueKind)
        assertEquals(0, elementError.slots[1].valueBits)
    }

    @Test
    fun `only exact Int values are native integer cells`() {
        val shape = CardiacInputShape.capture(
            { listOf(Int.MIN_VALUE, Int.MAX_VALUE, 0, -1, 7L, 2.5, true, "8") },
            { listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE, 1L, 3.0, false, Any()) },
        )

        assertEquals(
            listOf(
                CardiacInputSlotKind.INT32,
                CardiacInputSlotKind.INT32,
                CardiacInputSlotKind.INT32,
                CardiacInputSlotKind.INT32,
                CardiacInputSlotKind.NON_INT_NUMBER,
                CardiacInputSlotKind.NON_INT_NUMBER,
                CardiacInputSlotKind.OTHER_TYPE,
                CardiacInputSlotKind.OTHER_TYPE,
            ),
            shape.slots.map { it.valueKind },
        )
        assertEquals(listOf(Int.MIN_VALUE, Int.MAX_VALUE, 0, -1), shape.slots.take(4).map { it.valueBits })
        assertTrue(shape.slots.drop(4).all { it.valueBits == 0 && it.statusBits == 0 })
        assertTrue(shape.contractAnomaly)
    }

    @Test
    fun `v2 codec is exact positional round trip with 45 byte header and zero fillers`() {
        val shape = CardiacInputShape.capture(
            { listOf(Int.MIN_VALUE, null, 3L, true, Int.MAX_VALUE) },
            { listOf(-1, 0, 2.0, false, Int.MIN_VALUE) },
        )
        val chunk = shape.chunks(callbackId = 7, pointIndex = 1, pointCount = 3, anchorRecordIndex = 41).single()
        val payload = SourcePayloadCodec.cardiacInputShape(chunk, "FLUSH", batchSize = 3, screenOn = false)
        val decoded = SourcePayloadCodec.decodeCardiacInputShape(payload)

        assertEquals(45 + 5 * 10, payload.size)
        assertEquals(2, payload[0].toInt() and 0xFF)
        assertEquals(3, payload[1].toInt() and 0xFF)
        assertEquals(0x0E, payload[2].toInt() and 0xFF)
        assertEquals(3, decoded.batchSize)
        assertEquals(7, decoded.callbackId)
        assertEquals(1, decoded.pointIndex)
        assertEquals(3, decoded.pointCount)
        assertEquals(41L, decoded.anchorRecordIndex)
        assertEquals(5, decoded.capturedSlotCount)
        assertEquals(0, decoded.chunkStartIndex)
        assertEquals(shape.slots, decoded.slots)
        CardiacInputShapeCodec.validateCompleteGroup(listOf(decoded))
    }

    @Test
    fun `batched slots chunk contiguously with shared flags including anomaly beyond first chunk`() {
        val values = MutableList<Any?>(45) { it }
        values[44] = null
        val shape = CardiacInputShape.capture({ values }, { (0 until 45).toList() })
        val chunks = shape.chunks(callbackId = 12, pointIndex = 0, pointCount = 45, anchorRecordIndex = 1)
        val decoded = chunks.map { chunk ->
            SourcePayloadCodec.decodeCardiacInputShape(
                SourcePayloadCodec.cardiacInputShape(chunk, "REALTIME", batchSize = 45, screenOn = true),
            )
        }

        assertEquals(listOf(0, 20, 40), decoded.map { it.chunkStartIndex })
        assertEquals(listOf(20, 20, 5), decoded.map { it.chunkSlotCount })
        assertTrue(decoded.all { it.shapeAnomaly && it.flags == decoded.first().flags })
        assertEquals(CardiacInputSlotKind.NULL, decoded.last().slots.last().valueKind)
        CardiacInputShapeCodec.validateCompleteGroup(decoded)
    }

    @Test
    fun `batch shape boundaries preserve all positions at five twenty twenty-one forty and forty-one slots`() {
        mapOf(
            5 to listOf(5),
            20 to listOf(20),
            21 to listOf(20, 1),
            40 to listOf(20, 20),
            41 to listOf(20, 20, 1),
        ).forEach { (size, expectedChunkSizes) ->
            val shape = CardiacInputShape.capture(
                { List(size) { it } },
                { List(size) { -it } },
            )
            val chunks = shape.chunks(3, 0, 1, 1)
            assertFalse("clean $size-slot shape must not be anomalous", shape.contractAnomaly)
            assertEquals(expectedChunkSizes, chunks.map { it.chunkSlotCount })
            assertEquals((0 until size).toList(), chunks.flatMap { chunk ->
                (chunk.chunkStartIndex until chunk.chunkStartIndex + chunk.chunkSlotCount).toList()
            })
        }
    }

    @Test
    fun `empty and NULL fields each emit exactly one zero slot chunk`() {
        val empty = CardiacInputShape.capture({ emptyList<Any?>() }, { emptyList<Any?>() })
        val nulls = CardiacInputShape.capture({ null }, { null })

        listOf(empty, nulls).forEachIndexed { index, shape ->
            assertFalse(shape.contractAnomaly)
            val chunks = shape.chunks(1, index, 2, index.toLong() + 1L)
            assertEquals(1, chunks.size)
            val decoded = SourcePayloadCodec.decodeCardiacInputShape(
                SourcePayloadCodec.cardiacInputShape(chunks.single(), "REALTIME", 2, true),
            )
            assertEquals(0, decoded.chunkStartIndex)
            assertEquals(0, decoded.chunkSlotCount)
            assertEquals(45, SourcePayloadCodec.cardiacInputShape(chunks.single(), "REALTIME", 2, true).size)
            CardiacInputShapeCodec.validateCompleteGroup(listOf(decoded))
        }
        assertEquals(CardiacInputFieldState.LIST, empty.valuesField.state)
        assertEquals(CardiacInputFieldState.NULL_OR_ABSENT, nulls.valuesField.state)
    }

    @Test
    fun `max and oversize shapes retain original lengths and only oversize sets truncation`() {
        val atMaximum = CardiacInputShape.capture({ List(4096) { it } }, { List(4096) { -it } })
        val oversize = CardiacInputShape.capture({ List(4097) { it } }, { List(4097) { -it } })

        assertEquals(4096, atMaximum.capturedSlotCount)
        assertFalse(atMaximum.truncated)
        assertFalse(atMaximum.contractAnomaly)
        assertEquals(205, atMaximum.chunks(1, 0, 1, 1).size)
        assertEquals(16, atMaximum.chunks(1, 0, 1, 1).last().chunkSlotCount)

        assertEquals(4097, oversize.originalValuesLength)
        assertEquals(4097, oversize.originalStatusesLength)
        assertEquals(4096, oversize.capturedSlotCount)
        assertTrue(oversize.truncated)
        assertTrue(oversize.contractAnomaly)
        val payload = SourcePayloadCodec.cardiacInputShape(oversize.chunks(1, 0, 1, 1).first(), "REALTIME", 1, true)
        assertEquals(245, payload.size)
        val decoded = SourcePayloadCodec.decodeCardiacInputShape(payload)
        assertTrue(decoded.truncated)
        assertEquals(4097, decoded.originalValuesLength)
        assertEquals(4096, decoded.capturedSlotCount)
    }

    @Test
    fun `v1 cardiac encoder retains exact historical clean bytes while malformed legacy projection is bounded and anomalous`() {
        val historical = SourcePayloadCodec.cardiac(2, 812, 0, 9, 1, 3, 2, 4, true, "REALTIME", 3, true)
        assertEquals(
            "01020d03002c03000000000000090000000100030002000400",
            historical.hex(),
        )

        val clean = SamsungCardiacPoint(1000L, 80, 1, listOf(750, 760), listOf(0, 1))
        val expectedCleanV1 = SourcePayloadCodec.cardiac(2, 760, 1, 17, 0, 1, 1, 2, false, "REALTIME", 1, true)
        val cleanIbi = flattenCardiacBatch(17, listOf(clean)).last()
        assertArrayEquals(expectedCleanV1, SourcePayloadCodec.cardiac(
            cleanIbi.kind.wireCode, cleanIbi.value, cleanIbi.status, cleanIbi.callbackId,
            cleanIbi.pointIndex, cleanIbi.pointCount, cleanIbi.listIndex, cleanIbi.listCount,
            cleanIbi.contractAnomaly, "REALTIME", 1, true,
        ))

        val malformedShape = CardiacInputShape.capture({ listOf(750, 760) }, { listOf(0) })
        val projected = flattenCardiacBatch(18, listOf(
            SamsungCardiacPoint(1000L, 80, 1, emptyList(), emptyList(), malformedShape),
        ))
        assertEquals(3, projected.size)
        assertTrue(projected.all { it.contractAnomaly })
        assertEquals(2, projected.first().listCount)
        assertEquals(0, projected.last().status)
    }

    @Test
    fun `v2 source records round trip through canonical journal and ATT364 with real v1 anchor index`() {
        val directory = createTempDir(prefix = "cardiac-input-shape-")
        val journal = SourceJournal(
            rootDir = directory,
            bootCount = 1,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 1L,
        )
        try {
            val shape = CardiacInputShape.capture(
                { List(20) { if (it == 19) null else it } },
                { List(20) { it } },
            )
            val anchor = journal.append(
                SourceStreamCode.CARDIAC,
                55_000L,
                SourcePayloadCodec.cardiac(1, 72, 0, 21, 0, 1, -1, shape.capturedSlotCount, shape.contractAnomaly, "REALTIME", 1, true),
            )
            val v2 = shape.chunks(21, 0, 1, anchor.recordIndex).map { chunk ->
                journal.append(
                    SourceStreamCode.CARDIAC,
                    anchor.sourceTimestampMs,
                    SourcePayloadCodec.cardiacInputShape(chunk, "REALTIME", 1, true),
                )
            }
            val records = listOf(anchor) + v2
            val frames = SourceReplayProtocol.buildDataFrames(records, replay = true, maximumAttPayloadBytes = 364)
            val decodedRecords = frames.flatMap { SourceReplayProtocol.decodeDataFrame(it).records }
            val decodedV2 = decodedRecords.drop(1).map { SourcePayloadCodec.decodeCardiacInputShape(it.payload) }

            assertEquals(1L, anchor.recordIndex)
            assertTrue(v2.all { it.sourceTimestampMs == anchor.sourceTimestampMs })
            assertTrue(frames.all { it.size <= 364 })
            assertEquals(329, frames.last().size)
            assertEquals(329, SourceReplayProtocol.buildDataFrames(listOf(v2.single()), replay = true, maximumAttPayloadBytes = 364).single().size)
            records.zip(decodedRecords).forEach { (expected, actual) -> assertArrayEquals(expected.canonicalBytes(), actual.canonicalBytes()) }
            assertTrue(decodedV2.all { it.anchorRecordIndex == anchor.recordIndex })
            CardiacInputShapeCodec.validateCompleteGroup(decodedV2)
        } finally {
            journal.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `decoder rejects nonzero filler malformed headers and incomplete group`() {
        val shape = CardiacInputShape.capture({ listOf(null, 2) }, { listOf(0, 1) })
        val payload = SourcePayloadCodec.cardiacInputShape(shape.chunks(1, 0, 1, 1).single(), "REALTIME", 1, true)
        val fillerOffset = 45 + 1 + 1
        payload[fillerOffset] = 1
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(payload) }

        val tooLarge = CardiacInputShape.capture({ List(21) { it } }, { List(21) { it } })
        val first = SourcePayloadCodec.decodeCardiacInputShape(
            SourcePayloadCodec.cardiacInputShape(tooLarge.chunks(1, 0, 1, 1).first(), "REALTIME", 1, true),
        )
        assertThrows(IllegalArgumentException::class.java) { CardiacInputShapeCodec.validateCompleteGroup(listOf(first)) }
    }

    @Test
    fun `asymmetric getter error preserves access markers and fatal errors are not disguised`() {
        val shape = CardiacInputShape.capture({ throw IllegalArgumentException("getter") }, { listOf(0, -1) })
        val decoded = SourcePayloadCodec.decodeCardiacInputShape(
            SourcePayloadCodec.cardiacInputShape(shape.chunks(1, 0, 1, 1).single(), "REALTIME", 1, true),
        )
        assertEquals(CardiacInputFieldState.ACCESS_ERROR, decoded.valuesFieldState)
        assertEquals(-1, decoded.originalValuesLength)
        assertEquals(2, decoded.originalStatusesLength)
        assertTrue(decoded.slots.all { it.valueKind == CardiacInputSlotKind.ACCESS_ERROR && it.valueBits == 0 })
        assertEquals(listOf(0, -1), decoded.slots.map { it.statusBits })
        CardiacInputShapeCodec.validateCompleteGroup(listOf(decoded))
        assertThrows(AssertionError::class.java) {
            CardiacInputShape.capture({ throw AssertionError("fatal error must propagate") }, { emptyList<Int>() })
        }
    }

    @Test
    fun `nonempty shape rejects fabricated trailing zero length chunk`() {
        val shape = CardiacInputShape.capture({ List(20) { it } }, { List(20) { 0 } })
        val payload = SourcePayloadCodec.cardiacInputShape(shape.chunks(1, 0, 1, 1).single(), "REALTIME", 1, true).copyOf(45)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putInt(39, 20).putShort(43, 0)
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(payload) }
        assertThrows(IllegalArgumentException::class.java) { CardiacInputShapeChunk(shape, 1, 0, 1, 1, 20, 0) }
    }

    @Test
    fun `batch delivery flag must match declared batch size`() {
        val chunk = CardiacInputShape.capture({ listOf(700) }, { listOf(0) }).chunks(1, 0, 1, 1).single()
        val single = SourcePayloadCodec.cardiacInputShape(chunk, "REALTIME", 1, true)
        single[2] = (single[2].toInt() or 4).toByte()
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(single) }
        val batch = SourcePayloadCodec.cardiacInputShape(chunk, "FLUSH", 2, false)
        batch[2] = (batch[2].toInt() and 4.inv()).toByte()
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(batch) }
    }

    @Test
    fun `original large point coordinates survive with explicit legacy anchor saturation marker`() {
        val shape = CardiacInputShape.capture({ listOf(700) }, { listOf(0) })
        val payload = SourcePayloadCodec.cardiacInputShape(shape.chunks(1, 70_000, 70_001, 1).single(), "REALTIME", 1, true)
        val decoded = SourcePayloadCodec.decodeCardiacInputShape(payload)
        assertEquals(70_000, decoded.pointIndex)
        assertEquals(70_001, decoded.pointCount)
        assertTrue(decoded.legacyPointMetadataSaturated)
        payload[2] = (payload[2].toInt() and 0x20.inv()).toByte()
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(payload) }
        val normal = SourcePayloadCodec.cardiacInputShape(shape.chunks(1, 0, 1, 1).single(), "REALTIME", 1, true)
        normal[2] = (normal[2].toInt() or 0x20).toByte()
        assertThrows(IllegalArgumentException::class.java) { SourcePayloadCodec.decodeCardiacInputShape(normal) }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
