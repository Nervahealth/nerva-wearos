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
    val ibiStatuses: List<Int>
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
            val ibiCount = maxOf(point.ibiValuesMs.size, point.ibiStatuses.size)
            val shapeMismatch = point.ibiValuesMs.size != point.ibiStatuses.size
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
                    shapeMismatch
                )
            )
            repeat(ibiCount) { listIndex ->
                val hasValue = listIndex < point.ibiValuesMs.size
                val hasStatus = listIndex < point.ibiStatuses.size
                add(
                    CardiacEvidenceRecord(
                        CardiacEvidenceKind.IBI,
                        point.ibiValuesMs.getOrElse(listIndex) { 0 },
                        point.ibiStatuses.getOrElse(listIndex) { -1 },
                        point.sourceTimestamp,
                        callbackId,
                        pointIndex,
                        points.size,
                        listIndex,
                        ibiCount,
                        shapeMismatch || !hasValue || !hasStatus
                    )
                )
            }
        }
    }
