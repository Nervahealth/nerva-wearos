package com.hugr.wearos

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bounded, positional evidence about the two raw IBI fields supplied for one
 * cardiac point. This is evidence about input shape, not an IBI measurement.
 *
 * The snapshot retains at most [MAX_CAPTURED_SLOTS] positions. The original
 * list lengths remain explicit so a complete captured prefix cannot be
 * mistaken for a complete raw input when truncation occurred.
 */
enum class CardiacInputFieldState(val wireCode: Int) {
    NULL_OR_ABSENT(0),
    LIST(1),
    NON_LIST(2),
    ACCESS_ERROR(3);

    companion object {
        fun fromWireCode(code: Int): CardiacInputFieldState = entries.firstOrNull { it.wireCode == code }
            ?: throw IllegalArgumentException("Unknown cardiac input field state $code")
    }
}

enum class CardiacInputSlotKind(val wireCode: Int) {
    ABSENT(0),
    NULL(1),
    INT32(2),
    NON_INT_NUMBER(3),
    OTHER_TYPE(4),
    ACCESS_ERROR(5);

    companion object {
        fun fromWireCode(code: Int): CardiacInputSlotKind = entries.firstOrNull { it.wireCode == code }
            ?: throw IllegalArgumentException("Unknown cardiac input slot kind $code")
    }
}

/** A captured raw list member. [intBits] is meaningful only for [INT32]. */
data class CardiacInputElement(
    val kind: CardiacInputSlotKind,
    val intBits: Int = 0,
) {
    init {
        require(kind == CardiacInputSlotKind.INT32 || intBits == 0) {
            "Only an INT32 input element may carry integer bits"
        }
    }
}

/**
 * Immutable bounded raw-field snapshot. A LIST field has its exact original
 * length and a prefix of at most 4,096 captured members; all other states have
 * no declared length and no members.
 */
data class CardiacInputRawFieldSnapshot(
    val state: CardiacInputFieldState,
    val originalLength: Int,
    val capturedElements: List<CardiacInputElement>,
) {
    init {
        if (state == CardiacInputFieldState.LIST) {
            require(originalLength >= 0) { "A LIST field requires a non-negative original length" }
            require(capturedElements.size == minOf(originalLength, CardiacInputShape.MAX_CAPTURED_SLOTS)) {
                "Captured list prefix does not match the bounded original length"
            }
        } else {
            require(originalLength == -1) { "Only LIST fields may declare a length" }
            require(capturedElements.isEmpty()) { "Only LIST fields may capture members" }
        }
    }
}

/** One original positional pair; non-INT32 cells are deliberately zero fillers. */
data class CardiacInputSlot(
    val valueKind: CardiacInputSlotKind,
    val statusKind: CardiacInputSlotKind,
    val valueBits: Int,
    val statusBits: Int,
) {
    init {
        require(valueKind == CardiacInputSlotKind.INT32 || valueBits == 0) {
            "Non-INT32 value slots must use zero filler"
        }
        require(statusKind == CardiacInputSlotKind.INT32 || statusBits == 0) {
            "Non-INT32 status slots must use zero filler"
        }
    }

    /** Legacy V1 carries this only with the enclosing anomaly bit when it is filler. */
    fun legacyValue(): Int = if (valueKind == CardiacInputSlotKind.INT32) valueBits else 0
    fun legacyStatus(): Int = if (statusKind == CardiacInputSlotKind.INT32) statusBits else 0
}

/**
 * A full bounded snapshot for one raw IBI field pair. Construction is limited
 * to [capture], which reads each supplied field getter exactly once before any
 * list normalization, and [fromCleanIntLists], for existing synthetic tests.
 */
data class CardiacInputShape private constructor(
    val valuesField: CardiacInputRawFieldSnapshot,
    val statusesField: CardiacInputRawFieldSnapshot,
    val slots: List<CardiacInputSlot>,
    val originalMaximumLength: Int,
    val truncated: Boolean,
    val contractAnomaly: Boolean,
) {
    val originalValuesLength: Int get() = valuesField.originalLength
    val originalStatusesLength: Int get() = statusesField.originalLength
    val capturedSlotCount: Int get() = slots.size

    init {
        require(originalMaximumLength >= 0) { "Original maximum length cannot be negative" }
        require(slots.size == minOf(originalMaximumLength, MAX_CAPTURED_SLOTS)) {
            "Captured slot count must be the bounded original maximum"
        }
        require(truncated == (originalMaximumLength > MAX_CAPTURED_SLOTS)) {
            "Truncation must exactly represent the original maximum length"
        }
        require(contractAnomaly == anomalyFor(valuesField, statusesField, slots, truncated)) {
            "Cardiac input-shape anomaly does not match its bounded snapshot"
        }
    }

    internal fun chunks(
        callbackId: Int,
        pointIndex: Int,
        pointCount: Int,
        anchorRecordIndex: Long,
    ): List<CardiacInputShapeChunk> {
        require(callbackId >= 0) { "callbackId must be non-negative" }
        require(pointIndex >= 0) { "pointIndex must be non-negative" }
        require(pointCount > 0 && pointIndex < pointCount) { "point identity is invalid" }
        require(anchorRecordIndex > 0) { "anchorRecordIndex must be positive" }

        val count = maxOf(1, (capturedSlotCount + MAX_SLOTS_PER_CHUNK - 1) / MAX_SLOTS_PER_CHUNK)
        return (0 until count).map { chunkNumber ->
            val start = chunkNumber * MAX_SLOTS_PER_CHUNK
            val slotCount = minOf(MAX_SLOTS_PER_CHUNK, capturedSlotCount - start)
            CardiacInputShapeChunk(this, callbackId, pointIndex, pointCount, anchorRecordIndex, start, slotCount)
        }
    }

    companion object {
        const val MAX_CAPTURED_SLOTS = 4096
        const val MAX_SLOTS_PER_CHUNK = 20

        /**
         * Reads both raw fields exactly once. Any getter, size, or element
         * access failure is represented as evidence; it is not stringified,
         * reflected on, converted, or retried.
         */
        fun capture(
            valuesGetter: () -> Any?,
            statusesGetter: () -> Any?,
        ): CardiacInputShape {
            val rawValues = readRawField(valuesGetter)
            val rawStatuses = readRawField(statusesGetter)
            return fromSnapshots(snapshot(rawValues), snapshot(rawStatuses))
        }

        /** Existing synthetic SamsungCardiacPoint constructors are clean LIST inputs. */
        fun fromCleanIntLists(values: List<Int>, statuses: List<Int>): CardiacInputShape = capture(
            valuesGetter = { values },
            statusesGetter = { statuses },
        )

        internal fun anomalyFor(
            valuesField: CardiacInputRawFieldSnapshot,
            statusesField: CardiacInputRawFieldSnapshot,
            slots: List<CardiacInputSlot>,
            truncated: Boolean,
        ): Boolean = anomalyForMetadata(
            valuesField.state,
            valuesField.originalLength,
            statusesField.state,
            statusesField.originalLength,
            slots,
            truncated,
        )

        internal fun anomalyForMetadata(
            valuesState: CardiacInputFieldState,
            valuesLength: Int,
            statusesState: CardiacInputFieldState,
            statusesLength: Int,
            slots: List<CardiacInputSlot>,
            truncated: Boolean,
        ): Boolean =
            valuesState != statusesState ||
                valuesState == CardiacInputFieldState.NON_LIST ||
                valuesState == CardiacInputFieldState.ACCESS_ERROR ||
                statusesState == CardiacInputFieldState.NON_LIST ||
                statusesState == CardiacInputFieldState.ACCESS_ERROR ||
                (valuesState == CardiacInputFieldState.LIST &&
                    statusesState == CardiacInputFieldState.LIST &&
                    valuesLength != statusesLength) ||
                truncated ||
                slots.any { slot ->
                    slot.valueKind in ANOMALOUS_SLOT_KINDS || slot.statusKind in ANOMALOUS_SLOT_KINDS
                }

        private val ANOMALOUS_SLOT_KINDS = setOf(
            CardiacInputSlotKind.NULL,
            CardiacInputSlotKind.NON_INT_NUMBER,
            CardiacInputSlotKind.OTHER_TYPE,
            CardiacInputSlotKind.ACCESS_ERROR,
        )

        private data class RawFieldRead(val value: Any?, val getterFailed: Boolean)

        private fun readRawField(getter: () -> Any?): RawFieldRead = try {
            RawFieldRead(getter(), getterFailed = false)
        } catch (_: Exception) {
            RawFieldRead(null, getterFailed = true)
        }

        private fun snapshot(read: RawFieldRead): CardiacInputRawFieldSnapshot {
            if (read.getterFailed) return CardiacInputRawFieldSnapshot(
                CardiacInputFieldState.ACCESS_ERROR,
                -1,
                emptyList(),
            )
            val raw = read.value ?: return CardiacInputRawFieldSnapshot(
                CardiacInputFieldState.NULL_OR_ABSENT,
                -1,
                emptyList(),
            )
            if (raw !is List<*>) return CardiacInputRawFieldSnapshot(
                CardiacInputFieldState.NON_LIST,
                -1,
                emptyList(),
            )
            val length = try {
                raw.size
            } catch (_: Exception) {
                return CardiacInputRawFieldSnapshot(CardiacInputFieldState.ACCESS_ERROR, -1, emptyList())
            }
            if (length < 0) return CardiacInputRawFieldSnapshot(
                CardiacInputFieldState.ACCESS_ERROR,
                -1,
                emptyList(),
            )
            val elements = ArrayList<CardiacInputElement>(minOf(length, MAX_CAPTURED_SLOTS))
            repeat(minOf(length, MAX_CAPTURED_SLOTS)) { index ->
                elements += try {
                    classify(raw[index])
                } catch (_: Exception) {
                    CardiacInputElement(CardiacInputSlotKind.ACCESS_ERROR)
                }
            }
            return CardiacInputRawFieldSnapshot(
                CardiacInputFieldState.LIST,
                length,
                elements.toList(),
            )
        }

        private fun classify(raw: Any?): CardiacInputElement = when (raw) {
            null -> CardiacInputElement(CardiacInputSlotKind.NULL)
            is Int -> CardiacInputElement(CardiacInputSlotKind.INT32, raw)
            is Number -> CardiacInputElement(CardiacInputSlotKind.NON_INT_NUMBER)
            else -> CardiacInputElement(CardiacInputSlotKind.OTHER_TYPE)
        }

        private fun fromSnapshots(
            valuesField: CardiacInputRawFieldSnapshot,
            statusesField: CardiacInputRawFieldSnapshot,
        ): CardiacInputShape {
            val maximum = maxOf(valuesField.originalLength.coerceAtLeast(0), statusesField.originalLength.coerceAtLeast(0))
            val capturedCount = minOf(maximum, MAX_CAPTURED_SLOTS)
            val slots = (0 until capturedCount).map { index ->
                val value = elementAt(valuesField, index)
                val status = elementAt(statusesField, index)
                CardiacInputSlot(
                    valueKind = value.kind,
                    statusKind = status.kind,
                    valueBits = if (value.kind == CardiacInputSlotKind.INT32) value.intBits else 0,
                    statusBits = if (status.kind == CardiacInputSlotKind.INT32) status.intBits else 0,
                )
            }
            val truncated = maximum > MAX_CAPTURED_SLOTS
            return CardiacInputShape(
                valuesField = valuesField,
                statusesField = statusesField,
                slots = slots,
                originalMaximumLength = maximum,
                truncated = truncated,
                contractAnomaly = anomalyFor(valuesField, statusesField, slots, truncated),
            )
        }

        private fun elementAt(field: CardiacInputRawFieldSnapshot, index: Int): CardiacInputElement = when (field.state) {
            CardiacInputFieldState.NULL_OR_ABSENT,
            CardiacInputFieldState.NON_LIST -> CardiacInputElement(CardiacInputSlotKind.ABSENT)
            CardiacInputFieldState.ACCESS_ERROR -> CardiacInputElement(CardiacInputSlotKind.ACCESS_ERROR)
            CardiacInputFieldState.LIST -> if (index >= field.originalLength) {
                CardiacInputElement(CardiacInputSlotKind.ABSENT)
            } else {
                field.capturedElements[index]
            }
        }
    }
}

/** A deterministic, complete chunk of a [CardiacInputShape]. */
internal data class CardiacInputShapeChunk(
    val shape: CardiacInputShape,
    val callbackId: Int,
    val pointIndex: Int,
    val pointCount: Int,
    val anchorRecordIndex: Long,
    val chunkStartIndex: Int,
    val chunkSlotCount: Int,
) {
    init {
        require(callbackId >= 0) { "callbackId must be non-negative" }
        require(pointIndex >= 0 && pointCount > 0 && pointIndex < pointCount) { "point identity is invalid" }
        require(anchorRecordIndex > 0) { "anchorRecordIndex must be positive" }
        require(chunkStartIndex >= 0 && chunkStartIndex % CardiacInputShape.MAX_SLOTS_PER_CHUNK == 0) {
            "chunk start must be a non-negative multiple of ${CardiacInputShape.MAX_SLOTS_PER_CHUNK}"
        }
        require(chunkSlotCount in 0..CardiacInputShape.MAX_SLOTS_PER_CHUNK) { "chunk slot count is invalid" }
        require(chunkStartIndex <= shape.capturedSlotCount) { "chunk start exceeds captured slots" }
        require(shape.capturedSlotCount == 0 || chunkStartIndex < shape.capturedSlotCount) { "Nonempty shape cannot have a trailing empty chunk" }
        require(chunkSlotCount == minOf(CardiacInputShape.MAX_SLOTS_PER_CHUNK, shape.capturedSlotCount - chunkStartIndex)) {
            "chunk must contain the complete bounded range at its declared start"
        }
    }

    val slots: List<CardiacInputSlot> get() = shape.slots.subList(chunkStartIndex, chunkStartIndex + chunkSlotCount)
}

/** Strict decoded V2 input-shape chunk used by source tests and independent readers. */
internal data class DecodedCardiacInputShapeChunk(
    val flags: Int,
    val batchSize: Int,
    val callbackId: Int,
    val pointIndex: Int,
    val pointCount: Int,
    val anchorRecordIndex: Long,
    val valuesFieldState: CardiacInputFieldState,
    val statusesFieldState: CardiacInputFieldState,
    val originalValuesLength: Int,
    val originalStatusesLength: Int,
    val capturedSlotCount: Int,
    val chunkStartIndex: Int,
    val slots: List<CardiacInputSlot>,
) {
    val chunkSlotCount: Int get() = slots.size
    val shapeAnomaly: Boolean get() = (flags and CardiacInputShapeCodec.FLAG_SHAPE_ANOMALY) != 0
    val truncated: Boolean get() = (flags and CardiacInputShapeCodec.FLAG_ORIGINAL_SLOTS_TRUNCATED) != 0
    val legacyPointMetadataSaturated: Boolean get() = (flags and CardiacInputShapeCodec.FLAG_LEGACY_POINT_METADATA_SATURATED) != 0
}

/** Exact CARDIAC positional-input V2 payload codec. */
internal object CardiacInputShapeCodec {
    const val PAYLOAD_VERSION = 2
    const val EVIDENCE_KIND_INPUT_SHAPE = 3
    const val HEADER_BYTES = 45
    const val SLOT_BYTES = 10
    const val FLAG_SHAPE_ANOMALY = 0x08
    const val FLAG_ORIGINAL_SLOTS_TRUNCATED = 0x10
    const val FLAG_LEGACY_POINT_METADATA_SATURATED = 0x20
    private const val KNOWN_FLAGS = 0x3F

    fun encode(chunk: CardiacInputShapeChunk, deliveryFlags: Int, batchSize: Int): ByteArray {
        require(deliveryFlags and KNOWN_FLAGS.inv() == 0) { "Unknown cardiac input-shape flags" }
        val flags = (deliveryFlags and 0x07) or
            (if (chunk.shape.contractAnomaly) FLAG_SHAPE_ANOMALY else 0) or
            (if (chunk.shape.truncated) FLAG_ORIGINAL_SLOTS_TRUNCATED else 0) or
            (if (chunk.pointIndex > 65_535 || chunk.pointCount > 65_535) FLAG_LEGACY_POINT_METADATA_SATURATED else 0)
        val buffer = ByteBuffer.allocate(HEADER_BYTES + chunk.chunkSlotCount * SLOT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .put(PAYLOAD_VERSION.toByte())
            .put(EVIDENCE_KIND_INPUT_SHAPE.toByte())
            .put(flags.toByte())
            .putShort(batchSize.coerceIn(0, 65_535).toShort())
            .putInt(chunk.callbackId)
            .putInt(chunk.pointIndex)
            .putInt(chunk.pointCount)
            .putLong(chunk.anchorRecordIndex)
            .put(chunk.shape.valuesField.state.wireCode.toByte())
            .put(chunk.shape.statusesField.state.wireCode.toByte())
            .putInt(chunk.shape.originalValuesLength)
            .putInt(chunk.shape.originalStatusesLength)
            .putInt(chunk.shape.capturedSlotCount)
            .putInt(chunk.chunkStartIndex)
            .putShort(chunk.chunkSlotCount.toShort())
        chunk.slots.forEach { slot ->
            buffer.put(slot.valueKind.wireCode.toByte())
                .put(slot.statusKind.wireCode.toByte())
                .putInt(slot.valueBits)
                .putInt(slot.statusBits)
        }
        return buffer.array()
    }

    fun decode(payload: ByteArray): DecodedCardiacInputShapeChunk {
        require(payload.size >= HEADER_BYTES) { "Cardiac input-shape V2 payload is shorter than its 45-byte header" }
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.get().toInt() and 0xFF
        val kind = buffer.get().toInt() and 0xFF
        require(version == PAYLOAD_VERSION && kind == EVIDENCE_KIND_INPUT_SHAPE) {
            "Unsupported cardiac input-shape payload version/kind $version/$kind"
        }
        val flags = buffer.get().toInt() and 0xFF
        require(flags and KNOWN_FLAGS.inv() == 0) { "Cardiac input-shape payload has unknown flags" }
        val batchSize = buffer.short.toInt() and 0xFFFF
        require(((flags and 0x04) != 0) == (batchSize > 1)) { "Cardiac input-shape batch flag is inconsistent" }
        val callbackId = buffer.int
        val pointIndex = buffer.int
        val pointCount = buffer.int
        val anchorRecordIndex = buffer.long
        val valuesState = CardiacInputFieldState.fromWireCode(buffer.get().toInt() and 0xFF)
        val statusesState = CardiacInputFieldState.fromWireCode(buffer.get().toInt() and 0xFF)
        val valuesLength = buffer.int
        val statusesLength = buffer.int
        val capturedSlotCount = buffer.int
        val chunkStart = buffer.int
        val chunkSlotCount = buffer.short.toInt() and 0xFFFF

        require(callbackId >= 0) { "Cardiac input-shape callbackId is negative" }
        require(pointIndex >= 0 && pointCount > 0 && pointIndex < pointCount) { "Cardiac input-shape point identity is invalid" }
        require(((flags and FLAG_LEGACY_POINT_METADATA_SATURATED) != 0) == (pointIndex > 65_535 || pointCount > 65_535)) { "Cardiac input-shape legacy coordinate saturation must be explicit" }
        require(anchorRecordIndex > 0) { "Cardiac input-shape anchor record index is invalid" }
        requireFieldLength(valuesState, valuesLength, "values")
        requireFieldLength(statusesState, statusesLength, "statuses")
        val originalMaximum = maxOf(valuesLength.coerceAtLeast(0), statusesLength.coerceAtLeast(0))
        val expectedCaptured = minOf(originalMaximum, CardiacInputShape.MAX_CAPTURED_SLOTS)
        require(capturedSlotCount == expectedCaptured) { "Cardiac input-shape captured slot count is inconsistent" }
        val expectedTruncated = originalMaximum > CardiacInputShape.MAX_CAPTURED_SLOTS
        require(((flags and FLAG_ORIGINAL_SLOTS_TRUNCATED) != 0) == expectedTruncated) {
            "Cardiac input-shape truncation flag is inconsistent"
        }
        require(chunkStart >= 0 && chunkStart % CardiacInputShape.MAX_SLOTS_PER_CHUNK == 0 && chunkStart <= capturedSlotCount) {
            "Cardiac input-shape chunk start is inconsistent"
        }
        require(capturedSlotCount == 0 || chunkStart < capturedSlotCount) { "Nonempty shape cannot have a trailing empty chunk" }
        val expectedChunkCount = minOf(CardiacInputShape.MAX_SLOTS_PER_CHUNK, capturedSlotCount - chunkStart)
        require(chunkSlotCount == expectedChunkCount) { "Cardiac input-shape chunk range is incomplete or oversized" }
        require(payload.size == HEADER_BYTES + chunkSlotCount * SLOT_BYTES) { "Cardiac input-shape payload length is inconsistent" }

        val slots = ArrayList<CardiacInputSlot>(chunkSlotCount)
        repeat(chunkSlotCount) { localIndex ->
            val originalIndex = chunkStart + localIndex
            val valueKind = CardiacInputSlotKind.fromWireCode(buffer.get().toInt() and 0xFF)
            val statusKind = CardiacInputSlotKind.fromWireCode(buffer.get().toInt() and 0xFF)
            val valueBits = buffer.int
            val statusBits = buffer.int
            require(valueKind == CardiacInputSlotKind.INT32 || valueBits == 0) {
                "Cardiac input-shape non-INT32 value filler is non-zero"
            }
            require(statusKind == CardiacInputSlotKind.INT32 || statusBits == 0) {
                "Cardiac input-shape non-INT32 status filler is non-zero"
            }
            requireSlotPosition(valuesState, valuesLength, originalIndex, valueKind, "value")
            requireSlotPosition(statusesState, statusesLength, originalIndex, statusKind, "status")
            slots += CardiacInputSlot(valueKind, statusKind, valueBits, statusBits)
        }

        val locallyRequiredAnomaly = CardiacInputShape.anomalyForMetadata(
            valuesState,
            valuesLength,
            statusesState,
            statusesLength,
            slots,
            expectedTruncated,
        )
        require(!locallyRequiredAnomaly || (flags and FLAG_SHAPE_ANOMALY) != 0) {
            "Cardiac input-shape anomaly flag omits a declared malformed shape"
        }
        if (chunkStart == 0 && chunkSlotCount == capturedSlotCount) {
            require(((flags and FLAG_SHAPE_ANOMALY) != 0) == locallyRequiredAnomaly) {
                "Cardiac input-shape complete-group anomaly flag is inconsistent"
            }
        }
        return DecodedCardiacInputShapeChunk(
            flags = flags,
            batchSize = batchSize,
            callbackId = callbackId,
            pointIndex = pointIndex,
            pointCount = pointCount,
            anchorRecordIndex = anchorRecordIndex,
            valuesFieldState = valuesState,
            statusesFieldState = statusesState,
            originalValuesLength = valuesLength,
            originalStatusesLength = statusesLength,
            capturedSlotCount = capturedSlotCount,
            chunkStartIndex = chunkStart,
            slots = slots,
        )
    }

    /**
     * Validates one complete positional group after individual chunks have
     * decoded. A clean earlier chunk may legitimately carry the anomaly bit
     * because an anomalous member exists in a later chunk; that fact is known
     * only here, after all bounded positions are present.
     */
    fun validateCompleteGroup(chunks: List<DecodedCardiacInputShapeChunk>) {
        require(chunks.isNotEmpty()) { "Cardiac input-shape group is empty" }
        val first = chunks.first()
        val ordered = chunks.sortedBy { it.chunkStartIndex }
        require(ordered.size == maxOf(1, (first.capturedSlotCount + CardiacInputShape.MAX_SLOTS_PER_CHUNK - 1) / CardiacInputShape.MAX_SLOTS_PER_CHUNK)) { "Cardiac input-shape group chunk count is inconsistent" }
        require(ordered.all { chunk ->
            chunk.flags == first.flags &&
                chunk.batchSize == first.batchSize &&
                chunk.callbackId == first.callbackId &&
                chunk.pointIndex == first.pointIndex &&
                chunk.pointCount == first.pointCount &&
                chunk.anchorRecordIndex == first.anchorRecordIndex &&
                chunk.valuesFieldState == first.valuesFieldState &&
                chunk.statusesFieldState == first.statusesFieldState &&
                chunk.originalValuesLength == first.originalValuesLength &&
                chunk.originalStatusesLength == first.originalStatusesLength &&
                chunk.capturedSlotCount == first.capturedSlotCount
        }) { "Cardiac input-shape group metadata differs between chunks" }
        var expectedStart = 0
        val allSlots = ArrayList<CardiacInputSlot>(first.capturedSlotCount)
        ordered.forEach { chunk ->
            require(chunk.chunkStartIndex == expectedStart) { "Cardiac input-shape group has duplicate or missing chunk ranges" }
            allSlots += chunk.slots
            expectedStart += chunk.chunkSlotCount
        }
        require(expectedStart == first.capturedSlotCount) { "Cardiac input-shape group is incomplete" }
        val anomaly = CardiacInputShape.anomalyForMetadata(
            first.valuesFieldState,
            first.originalValuesLength,
            first.statusesFieldState,
            first.originalStatusesLength,
            allSlots,
            first.truncated,
        )
        require(first.shapeAnomaly == anomaly) { "Cardiac input-shape group anomaly flag is inconsistent" }
    }

    private fun requireFieldLength(state: CardiacInputFieldState, length: Int, fieldName: String) {
        require((state == CardiacInputFieldState.LIST && length >= 0) ||
            (state != CardiacInputFieldState.LIST && length == -1)
        ) { "Cardiac input-shape $fieldName length is inconsistent with its field state" }
    }

    private fun requireSlotPosition(
        state: CardiacInputFieldState,
        originalLength: Int,
        originalIndex: Int,
        kind: CardiacInputSlotKind,
        fieldName: String,
    ) {
        val valid = when (state) {
            CardiacInputFieldState.NULL_OR_ABSENT,
            CardiacInputFieldState.NON_LIST -> kind == CardiacInputSlotKind.ABSENT
            CardiacInputFieldState.ACCESS_ERROR -> kind == CardiacInputSlotKind.ACCESS_ERROR
            CardiacInputFieldState.LIST -> if (originalIndex >= originalLength) {
                kind == CardiacInputSlotKind.ABSENT
            } else {
                kind != CardiacInputSlotKind.ABSENT
            }
        }
        require(valid) { "Cardiac input-shape $fieldName slot kind is inconsistent with original position" }
    }

}
