package com.hugr.wearos

enum class CardiacEvidenceKind(val wireCode: Int) {
    HEART_RATE(1),
    IBI(2)
}

data class SamsungCardiacPoint(
    val sourceTimestamp: Long,
    val heartRate: Int,
    val heartRateStatus: Int,
    val ibiValuesMs: List<Int>,
    val ibiStatuses: List<Int>,
    /**
     * Existing synthetic constructors remain clean typed-list inputs by
     * default. The live producer supplies its once-captured raw snapshot.
     */
    val inputShape: CardiacInputShape = CardiacInputShape.fromCleanIntLists(ibiValuesMs, ibiStatuses),
)

data class CardiacEvidenceRecord(
    val kind: CardiacEvidenceKind,
    val value: Int,
    val status: Int,
    val sourceTimestamp: Long,
    val callbackId: Int,
    val pointIndex: Int,
    val pointCount: Int,
    val listIndex: Int,
    val listCount: Int,
    val contractAnomaly: Boolean
)

fun flattenCardiacBatch(callbackId: Int, points: List<SamsungCardiacPoint>): List<CardiacEvidenceRecord> =
    buildList {
        points.forEachIndexed { pointIndex, point ->
            val inputShape = point.inputShape
            // Legacy V1 is deliberately a bounded positional projection of
            // the immutable raw snapshot. It never traverses a raw SDK list.
            val ibiCount = inputShape.capturedSlotCount
            add(
                CardiacEvidenceRecord(
                    CardiacEvidenceKind.HEART_RATE,
                    point.heartRate,
                    point.heartRateStatus,
                    point.sourceTimestamp,
                    callbackId,
                    pointIndex,
                    points.size,
                    -1,
                    ibiCount,
                    inputShape.contractAnomaly
                )
            )
            inputShape.slots.forEachIndexed { listIndex, slot ->
                add(
                    CardiacEvidenceRecord(
                        CardiacEvidenceKind.IBI,
                        // The V1 cell has no kind field. Non-INT32 source
                        // members are zero fillers and the common V1 anomaly
                        // bit prevents any generated value/status being read
                        // as native measurement evidence.
                        slot.legacyValue(),
                        slot.legacyStatus(),
                        point.sourceTimestamp,
                        callbackId,
                        pointIndex,
                        points.size,
                        listIndex,
                        ibiCount,
                        inputShape.contractAnomaly
                    )
                )
            }
        }
    }
