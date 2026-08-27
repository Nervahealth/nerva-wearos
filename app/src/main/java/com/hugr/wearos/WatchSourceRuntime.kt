package com.hugr.wearos

import android.content.Context
import android.os.StatFs
import android.provider.Settings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WatchSourceRuntime {
    private const val JOURNAL_DIRECTORY = "build45_source_journal"

    @Volatile
    private var journal: SourceJournal? = null

    @Synchronized
    fun journal(context: Context): SourceJournal {
        journal?.let { return it }
        val applicationContext = context.applicationContext
        val root = File(applicationContext.filesDir, JOURNAL_DIRECTORY)
        val bootCount = Settings.Global.getInt(
            applicationContext.contentResolver,
            Settings.Global.BOOT_COUNT,
            -1,
        )
        return SourceJournal(
            rootDir = root,
            bootCount = bootCount,
            availableBytes = { StatFs(applicationContext.filesDir.absolutePath).availableBytes },
            nowWallMs = { System.currentTimeMillis() },
        ).also { journal = it }
    }

    @Synchronized
    fun resetForTests() {
        journal = null
    }
}

internal object SourcePayloadCodec {
    const val VERSION = 1

    fun eda(conductance: Float, deliveryMode: String, batchSize: Int, screenOn: Boolean): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION.toByte())
            .put(deliveryFlags(deliveryMode, batchSize, screenOn))
            .putShort(batchSize.coerceIn(0, 65_535).toShort())
            .putFloat(conductance)
            .array()

    fun accel(xMilliG: Int, yMilliG: Int, zMilliG: Int, deliveryMode: String, batchSize: Int, screenOn: Boolean): ByteArray =
        ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION.toByte())
            .put(deliveryFlags(deliveryMode, batchSize, screenOn))
            .putShort(batchSize.coerceIn(0, 65_535).toShort())
            .putFloat(xMilliG.toFloat() / 1000f * 9.81f)
            .putFloat(yMilliG.toFloat() / 1000f * 9.81f)
            .putFloat(zMilliG.toFloat() / 1000f * 9.81f)
            .array()

    fun skinTemperature(
        skinTemp: Float,
        ambientTemp: Float,
        status: Int,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean,
    ): ByteArray = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        .put(VERSION.toByte())
        .put(deliveryFlags(deliveryMode, batchSize, screenOn))
        .putShort(batchSize.coerceIn(0, 65_535).toShort())
        .putFloat(skinTemp)
        .putFloat(ambientTemp)
        .putInt(status)
        .array()

    fun cardiac(
        kind: Int,
        value: Int,
        status: Int,
        callbackId: Int,
        pointIndex: Int,
        pointCount: Int,
        listIndex: Int,
        listCount: Int,
        contractAnomaly: Boolean,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean,
    ): ByteArray {
        var flags = deliveryFlags(deliveryMode, batchSize, screenOn).toInt() and 0xFF
        if (contractAnomaly) flags = flags or 0x08
        return ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
            .put(VERSION.toByte())
            .put(kind.coerceIn(0, 255).toByte())
            .put(flags.toByte())
            .putShort(batchSize.coerceIn(0, 65_535).toShort())
            .putInt(value)
            .putInt(status)
            .putInt(callbackId)
            .putShort(pointIndex.coerceIn(0, 65_535).toShort())
            .putShort(pointCount.coerceIn(0, 65_535).toShort())
            .putShort(if (listIndex < 0) 0xFFFF.toShort() else listIndex.coerceIn(0, 65_535).toShort())
            .putShort(listCount.coerceIn(0, 65_535).toShort())
            .array()
    }

    fun deviceHealth(
        batteryPercent: Int,
        flags: Int,
        activeSensorMask: Int,
        sdkStatus: Int,
        buildVersionCode: Int,
        totalSourceRecords: Long,
        flushCount: Long,
        transportCompletedCount: Long,
        transportFailedCount: Long,
        transportTimeoutCount: Long,
        transportCoalescedAccelCount: Long,
        negotiatedMtu: Int,
        replayBacklogCount: Long,
        dataLoss: Boolean,
        dataLossStreamCode: Int,
        dataLossFirstSequence: Long,
        dataLossLastSequence: Long,
        dataLossReasonCode: Int,
    ): ByteArray = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN)
        .put(VERSION.toByte())
        .put(batteryPercent.coerceIn(0, 255).toByte())
        .put(flags.coerceIn(0, 255).toByte())
        .put(activeSensorMask.coerceIn(0, 255).toByte())
        .put(sdkStatus.coerceIn(0, 255).toByte())
        .put(if (dataLoss) 1.toByte() else 0.toByte())
        .putInt(buildVersionCode)
        .putLong(totalSourceRecords)
        .putInt(flushCount.toInt())
        .putInt(transportCompletedCount.toInt())
        .putInt(transportFailedCount.toInt())
        .putInt(transportTimeoutCount.toInt())
        .putInt(transportCoalescedAccelCount.toInt())
        .putShort(negotiatedMtu.coerceIn(0, 65_535).toShort())
        .putLong(replayBacklogCount)
        .put(dataLossStreamCode.coerceIn(0, 255).toByte())
        .put(dataLossReasonCode.coerceIn(0, 255).toByte())
        .putInt(dataLossFirstSequence.toInt())
        .putInt(dataLossLastSequence.toInt())
        .array()

    fun deliveryFlags(deliveryMode: String, batchSize: Int, screenOn: Boolean): Byte {
        var flags = 0
        if (screenOn) flags = flags or 0x01
        if (deliveryMode == "FLUSH" || (!screenOn && deliveryMode == "FALLBACK")) flags = flags or 0x02
        if (batchSize > 1) flags = flags or 0x04
        return flags.toByte()
    }
}
