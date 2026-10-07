package com.hugr.wearos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.BatteryManager
import android.os.IBinder
import android.os.ParcelUuid
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * BleGattService — BLE GATT Peripheral Server for HUGR Watchtower v3.
 *
 * This service:
 * 1. Opens a BluetoothGattServer with a custom HUGR service
 * 2. Registers characteristics for EDA, PPG/Cardiac, Accel, SkinTemp (NOTIFY) and Haptic (WRITE)
 * 3. Advertises the HUGR service UUID so the phone can discover it
 * 4. Listens for sensor data broadcasts from HealthSensorService
 * 5. Pushes sensor data to connected phone via GATT notifications
 * 6. Routes explicit research haptic commands through one versioned, silent,
 *    high-importance notification channel. Direct vibrator code remains
 *    contained below for research comparison and is not the active command path.
 *
 * BLE DATA CONTRACT (Build 43w cardiac evidence candidate):
 * - EDA (UUID 11111111): [conductance:float32] = 4 bytes
 * - PPG/Cardiac (UUID 44444444): [format:uint8][d0:int32][d1:int32][d2:int32] = 13 bytes
 *     format=0x01: raw PPG → d0=Green, d1=IR, d2=Red
 *     format=0x02: legacy scalar HR+IBI → d0=HR, d1=IBI_ms, d2=hrStatus
 *     format=0x03: typed HR/IBI evidence with source/callback/list provenance
 * - Accel (UUID 33333333): [x:float32][y:float32][z:float32] = 12 bytes
 * - SkinTemp (UUID 55555555): [skinTemp:float32][ambientTemp:float32][status:int32] = 12 bytes
 * - Haptic (UUID 0000fff5): write-only, variable length
 */
class BleGattService : Service() {

    private val TAG = "HUGR-BleGatt"
    private val binder = LocalBinder()
    private var runtimeMode: EvidenceEgressGattRuntimeMode? = null
    private var finalizedDeliveryRecoveryOnly = false
    private var finalizedDeliverySourceSessionId: UUID? = null
    private var finalizedDeliveryRecoveryAttemptId: UUID? = null
    @Volatile private var finalizedRecoveryStopReason: FinalizedRecoveryStopReason? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var egressForegroundActive = false
    private var egressAdvertiserReady = false
    private var finalizedRecoveryForegroundActive = false
    private val finalizedRecoveryLifetime = FinalizedDeliveryLifetime()
    private val finalizedRecoveryHeartbeat = object : Runnable {
        override fun run() {
            val attempt = finalizedDeliveryRecoveryAttemptId ?: return
            if (!finalizedDeliveryRecoveryOnly || !finalizedRecoveryForegroundActive ||
                finalizedRecoveryStopReason != null
            ) return
            val nowMs = SystemClock.elapsedRealtime()
            if (!finalizedRecoveryLifetime.permitsDelivery(nowMs)) {
                stopFinalizedRecoveryWithoutAcknowledgement("DEADLINE")
                return
            }
            FinalizedRecoveryReadiness.heartbeat(attempt, causalComponentInstanceId, nowMs)
            transportHandler.postDelayed(this, 1_000L)
        }
    }
    private val finalizedRecoveryDeadline = Runnable {
        if (finalizedDeliveryRecoveryOnly &&
            !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
        ) {
            stopFinalizedRecoveryWithoutAcknowledgement("DEADLINE")
        }
    }
    private var connectedDevice: BluetoothDevice? = null
    private val notificationSubscriptions = mutableMapOf<String, MutableSet<UUID>>()
    private var notificationCompletionBlocked = false
    private var causalNotificationGattStatus = BluetoothGatt.GATT_SUCCESS
    private var notifyCount = 0

    // GATT Characteristics (held as references for notification updates)
    // Direct vibrator reference — no broadcast middleman
    private var vibrator: Vibrator? = null
    private lateinit var notificationManager: NotificationManager
    private val processedHapticCommands = HapticCommandRegistry(128)
    private val activeHapticNotificationIds = mutableSetOf<Int>()

    private var edaCharacteristic: BluetoothGattCharacteristic? = null
    private var ppgCharacteristic: BluetoothGattCharacteristic? = null
    private var accelCharacteristic: BluetoothGattCharacteristic? = null
    private var skinTempCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var hapticReceiptCharacteristic: BluetoothGattCharacteristic? = null
    private var sourceRecordCharacteristic: BluetoothGattCharacteristic? = null
    private var sourceControlCharacteristic: BluetoothGattCharacteristic? = null
    // Evidence Egress v1 is deliberately separate from typed telemetry and source replay.
    // It is passive until EvidenceEgressActivity receives explicit watch-side consent.
    private var egressOfferCharacteristic: BluetoothGattCharacteristic? = null
    private var egressChunkCharacteristic: BluetoothGattCharacteristic? = null
    private var egressControlCharacteristic: BluetoothGattCharacteristic? = null
    private var egressReceiptCharacteristic: BluetoothGattCharacteristic? = null

    private var edaSequence = 0L
    private var ppgSequence = 0L
    private var cardiacSequence = 0L
    private var accelSequence = 0L
    private var skinTempSequence = 0L
    private var healthSequence = 0L
    private var totalSensorPackets = 0L
    private var droppedNoConnectionCount = 0L
    private var healthSdkConnected = false
    private var healthSdkStatus = 0
    private var activeSensorMask = 0
    private var healthFlushCount = 0L
    private var healthScreenOn = true
    private var healthSourceDataLoss = false
    private var healthSourceDataLossStreamCode = 0
    private var healthSourceDataLossFirstSequence = 0L
    private var healthSourceDataLossLastSequence = 0L
    private var healthSourceDataLossReasonCode = 0
    private lateinit var sourceJournal: SourceJournal
    private var negotiatedMtu = 23
    private var durablePhoneRecordIndex = 0L
    private var lastReplayQueuedRecordIndex = 0L
    private var replayHighWaterRecordIndex = 0L
    private var replayBacklogCount = 0L
    private var replayActive = false
    private var activeReplaySessionId: UUID? = null
    private var queuedReplayManifestEndIndex: Long? = null
    private val sourceMtuReadinessGate = SourceMtuReadinessGate()
    @Volatile private var sourceMtuLineageGeneration = -1L
    private val replayStartLineageGuard = ReplayStartLineageGuard()
    private var replayStartGeneration = -1L
    @Volatile private var pendingSourceResumeRequest: SourceResumeRequest? = null
    private val sourceResumeExecutor = Executors.newSingleThreadExecutor()
    private val sourceResumePreparationGeneration = AtomicLong(-1L)
    private val pendingLiveSourceRecords = ArrayList<WatchSourceRecord>()
    private var liveSourceFlushScheduled = false
    private val healthHandler = Handler(Looper.getMainLooper())
    private val transportHandler = Handler(Looper.getMainLooper())
    private val causalComponentInstanceId = UUID.randomUUID()
    private val causalLineageState = CausalLineageState()
    private val causalSourceDetailCountByLineage = mutableMapOf<Long, Int>()
    private val liveSourceFlush = Runnable {
        liveSourceFlushScheduled = false
        flushLiveSourceRecords()
    }
    private val replayStartAfterLiveOpportunity = Runnable {
        if (!replayStartLineageGuard.isCurrent(replayStartGeneration)) return@Runnable
        flushLiveSourceRecords()
        pumpReplay()
    }
    private val notificationQueue = GattNotificationQueue(
        maxDepth = 256,
        ppgSoftLimit = 96,
        timeoutMs = GATT_NOTIFICATION_TIMEOUT_MS,
        nowElapsedMs = { SystemClock.elapsedRealtime() },
        nowWallMs = { System.currentTimeMillis() },
        trigger = { triggerGattNotification(it) },
        onCriticalFault = { reason ->
            Log.e(TAG, "CRITICAL BLE transport fault: $reason")
            recordCausal(
                CausalEventCode.QUEUE_CRITICAL_FAULT,
                reasonCode = CausalReasonCode.CRITICAL_QUEUE_FAULT.code,
            )
            broadcastStatus("BLE TRANSPORT FAULT: $reason")
            transportHandler.post { abortTransportLineage("critical_queue_fault") }
        },
        onTriggered = { item -> handleNotificationTriggered(item) },
        onCompleted = { item -> handleNotificationCompleted(item, causalNotificationGattStatus) },
        onFailed = { item -> handleNotificationFailed(item, causalNotificationGattStatus) },
        onTriggerResult = { item, result -> recordNotificationTriggerResult(item, result) },
        onTimedOut = { item -> recordNotificationTimeout(item) },
    )
    private val healthTicker = object : Runnable {
        override fun run() {
            notifyDeviceHealth()
            healthHandler.postDelayed(this, DEVICE_HEALTH_INTERVAL_MS)
        }
    }
    private val transportTicker = object : Runnable {
        override fun run() {
            if (notificationQueue.checkTimeout()) {
                Log.e(TAG, "BLE notification timed out; transport evidence marked failed")
                broadcastStatus("BLE TRANSPORT TIMEOUT")
                abortTransportLineage("notification_timeout")
            }
            transportHandler.postDelayed(this, GATT_TIMEOUT_CHECK_INTERVAL_MS)
        }
    }

    companion object {
        val HUGR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val EDA_CHARACTERISTIC_UUID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val PPG_CHARACTERISTIC_UUID: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val ACCEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val SKIN_TEMP_CHARACTERISTIC_UUID: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val HAPTIC_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
        val STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")
        val HAPTIC_RECEIPT_CHARACTERISTIC_UUID: UUID = UUID.fromString("99999999-9999-9999-9999-999999999999")
        val SOURCE_RECORD_CHARACTERISTIC_UUID: UUID = UUID.fromString("77777777-7777-7777-7777-777777777777")
        val SOURCE_CONTROL_CHARACTERISTIC_UUID: UUID = UUID.fromString("88888888-8888-8888-8888-888888888888")
        // Dedicated Evidence Egress v1 characteristics. These are not source-record/control UUIDs.
        val EGRESS_OFFER_CHARACTERISTIC_UUID: UUID = UUID.fromString("eeee0001-1234-5678-1234-567812345678")
        val EGRESS_CHUNK_CHARACTERISTIC_UUID: UUID = UUID.fromString("eeee0002-1234-5678-1234-567812345678")
        val EGRESS_CONTROL_CHARACTERISTIC_UUID: UUID = UUID.fromString("eeee0003-1234-5678-1234-567812345678")
        val EGRESS_RECEIPT_CHARACTERISTIC_UUID: UUID = UUID.fromString("eeee0004-1234-5678-1234-567812345678")

        // Client Characteristic Configuration Descriptor (required for NOTIFY)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val ACTION_HAPTIC_COMMAND = "com.hugr.wearos.HAPTIC_COMMAND"
        /**
         * Standard-GATT-only recovery of a retained, already-finalized ordinary
         * session. This mode never starts sensing and never appends a new source
         * record; it exists only to accept the ordinary Phone resume/replay/ack.
         */
        const val EXTRA_FINALIZED_DELIVERY_RECOVERY_ONLY = "finalized_delivery_recovery_only"
        const val EXTRA_FINALIZED_DELIVERY_SOURCE_SESSION_ID = "finalized_delivery_source_session_id"
        const val EXTRA_FINALIZED_RECOVERY_ATTEMPT_ID = "finalized_delivery_attempt_id"
        private const val WATCHTOWER_V2_MARKER = 0xA2
        private const val WATCHTOWER_V3_MARKER = 0xA3
        private const val WATCHTOWER_CARDIAC_EVIDENCE_VERSION = 3
        private const val WATCHTOWER_TELEMETRY_VERSION = 4
        private const val HAPTIC_POLICY_VERSION = 1
        private const val HAPTIC_CHANNEL_ID = "hugr_research_haptic_v1"
        private const val HAPTIC_CHANNEL_NAME = "HUGR research haptics"
        private const val EGRESS_FOREGROUND_CHANNEL_ID = "hugr_evidence_egress_v1"
        private const val EGRESS_FOREGROUND_CHANNEL_NAME = "HUGR Evidence Egress"
        private const val EGRESS_FOREGROUND_NOTIFICATION_ID = 4_001
        private const val RECOVERY_FOREGROUND_CHANNEL_ID = "hugr_finalized_source_delivery_v1"
        private const val RECOVERY_FOREGROUND_NOTIFICATION_ID = 4_002
        private const val DETAIL_OK = 0
        private const val DETAIL_DUPLICATE_REPLAY = 10
        private const val DETAIL_UNSUPPORTED_POLICY = 11
        private const val DETAIL_NOTIFICATIONS_DISABLED = 12
        private const val DETAIL_PERMISSION_MISSING = 13
        private const val DETAIL_CHANNEL_DISABLED = 14
        private const val DETAIL_NOTIFY_EXCEPTION = 15
        private const val DETAIL_STOP_ACCEPTED = 16
        private const val DEVICE_HEALTH_INTERVAL_MS = 30_000L
        private const val GATT_NOTIFICATION_TIMEOUT_MS = 3_000L
        private const val GATT_TIMEOUT_CHECK_INTERVAL_MS = 250L
        private const val ATT_PROTOCOL_OVERHEAD_BYTES = 3
        private const val REPLAY_PAGE_RECORDS = 96
        private const val LIVE_SOURCE_BATCH_MS = 200L
        private const val MAX_CAUSAL_SOURCE_DETAILS_PER_LINEAGE = 32
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleGattService = this@BleGattService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BleGattService created; awaiting explicit runtime mode")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (EvidenceEgressGattStartPolicy.isExplicitEgressStop(intent?.action)) {
            if (isEgressOnlyRuntime()) {
                EvidenceEgressVolatileDiagnostics.onForegroundStopped()
                stopEgressForegroundLifetime()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        val requestedMode = EvidenceEgressGattStartPolicy.requestedMode(intent?.action)
        val currentMode = runtimeMode
        if (!EvidenceEgressGattStartPolicy.acceptsModeTransition(currentMode, requestedMode)) {
            Log.w(TAG, "Rejected GATT runtime mode transition: $currentMode -> $requestedMode")
            if (requestedMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY) {
                EvidenceEgressVolatileDiagnostics.onAdvertiserStartFailed("MODE_CONFLICT")
            }
            return START_NOT_STICKY
        }
        if (currentMode == null) {
            runtimeMode = requestedMode
            val requestedFinalizedRecovery = requestedMode == EvidenceEgressGattRuntimeMode.STANDARD &&
                intent?.getBooleanExtra(EXTRA_FINALIZED_DELIVERY_RECOVERY_ONLY, false) == true
            val requestedSourceSessionId = intent
                ?.getStringExtra(EXTRA_FINALIZED_DELIVERY_SOURCE_SESSION_ID)
                ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            val requestedAttemptId = intent?.getStringExtra(EXTRA_FINALIZED_RECOVERY_ATTEMPT_ID)
                ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
            finalizedDeliveryRecoveryOnly = requestedFinalizedRecovery
            finalizedDeliverySourceSessionId = requestedSourceSessionId
            finalizedDeliveryRecoveryAttemptId = requestedAttemptId
            if (requestedFinalizedRecovery && requestedAttemptId != null &&
                !FinalizedRecoveryReadiness.enter(requestedAttemptId, causalComponentInstanceId)
            ) {
                markFinalizedRecoveryStop(FinalizedRecoveryStopReason.ATTEMPT_REUSED)
                stopSelf()
                return START_NOT_STICKY
            }
            if (requestedFinalizedRecovery && (requestedSourceSessionId == null || requestedAttemptId == null)) {
                Log.e(TAG, "Refusing finalized-delivery recovery without valid source and attempt identities")
                markFinalizedRecoveryStop(FinalizedRecoveryStopReason.INVALID_SOURCE_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            if (requestedMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY) {
                if (!startEgressForegroundLifetime()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            } else if (requestedFinalizedRecovery) {
                if (!startFinalizedRecoveryForegroundLifetime()) {
                    markFinalizedRecoveryStop(FinalizedRecoveryStopReason.FOREGROUND_PROMOTION_FAILED)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            if (requestedFinalizedRecovery) {
                runCatching { initializeRuntime(requestedMode) }.onFailure { failure ->
                    Log.e(TAG, "Retained source delivery could not initialize", failure)
                    stopFinalizedRecoveryWithoutAcknowledgement("INITIALIZATION_FAILED")
                }
            } else {
                initializeRuntime(requestedMode)
            }
        } else if (currentMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY) {
            EvidenceEgressVolatileDiagnostics.onRuntimeReused(
                foregroundActive = egressForegroundActive,
                advertiserReady = egressAdvertiserReady,
            )
        } else if (finalizedDeliveryRecoveryOnly &&
            intent?.getBooleanExtra(EXTRA_FINALIZED_DELIVERY_RECOVERY_ONLY, false) == true
        ) {
            // Reopening the launcher is not authority for another delivery attempt.
            stopFinalizedRecoveryWithoutAcknowledgement("ATTEMPT_REUSED")
        }
        return START_NOT_STICKY
    }

    private fun initializeRuntime(mode: EvidenceEgressGattRuntimeMode) {
        if (mode == EvidenceEgressGattRuntimeMode.STANDARD) {
            recordCausal(CausalEventCode.BLE_SERVICE_CREATED)
            sourceJournal = WatchSourceRuntime.journal(this)
            if (finalizedDeliveryRecoveryOnly) requireRetainedFinalizedDeliverySession()
            initializeVibrator()
            initializeHapticNotificationChannel()
            if (!finalizedDeliveryRecoveryOnly) {
                registerSensorReceivers()
                healthHandler.post(healthTicker)
            }
            transportHandler.post(transportTicker)
            Log.d(
                TAG,
                if (finalizedDeliveryRecoveryOnly) {
                    "Standard BLE GATT finalized-delivery recovery initialized; no sensing or source append"
                } else {
                    "Standard BLE GATT runtime initialized"
                },
            )
        } else {
            EvidenceEgressVolatileDiagnostics.onRuntimeInitialized()
            Log.i(TAG, "Evidence Egress-only BLE GATT runtime initialized")
        }
        initializeBluetooth()
    }

    private fun isEgressOnlyRuntime(): Boolean =
        runtimeMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY

    private fun isStandardRuntime(): Boolean =
        runtimeMode == EvidenceEgressGattRuntimeMode.STANDARD

    private fun requireRetainedFinalizedDeliverySession(): UUID {
        if (!sourceJournal.preflight().eligible) {
            throw SourceJournalCorruptionException("Retained finalized journal is not integrity eligible")
        }
        val session = finalizedDeliverySourceSessionId
            ?: throw SourceJournalCorruptionException("Finalized delivery recovery is missing its source session identity")
        if (sourceJournal.finalizedManifests(session).isEmpty()) {
            throw SourceJournalCorruptionException("Requested finalized delivery source session is no longer retained")
        }
        return session
    }

    /**
     * A temporary lifetime guard for an already explicitly activated, egress-only
     * GATT advertiser. This service type is never entered by STANDARD runtime,
     * sensing, telemetry, haptic, replay, or automatic reconnect paths.
     */
    private fun startEgressForegroundLifetime(): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            EGRESS_FOREGROUND_CHANNEL_ID,
            EGRESS_FOREGROUND_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Temporary read-only HUGR Evidence Egress session"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, EGRESS_FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("HUGR Evidence Egress")
            .setContentText("Read-only session preparing; no sensing or telemetry")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        return try {
            startForeground(
                EGRESS_FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            egressForegroundActive = true
            EvidenceEgressVolatileDiagnostics.onForegroundStarted()
            true
        } catch (error: SecurityException) {
            Log.e(TAG, "Egress foreground start rejected", error)
            EvidenceEgressVolatileDiagnostics.onReadinessFailure("FOREGROUND_START_REJECTED")
            false
        } catch (error: Exception) {
            Log.e(TAG, "Egress foreground start failed", error)
            EvidenceEgressVolatileDiagnostics.onReadinessFailure("FOREGROUND_START_FAILED")
            false
        }
    }

    private fun stopEgressForegroundLifetime() {
        if (!egressForegroundActive) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        egressForegroundActive = false
    }

    /** Only the already-finalized fresh-v2 session receives this connected-device lifetime. */
    private fun startFinalizedRecoveryForegroundLifetime(): Boolean = try {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                RECOVERY_FOREGROUND_CHANNEL_ID,
                "HUGR retained source delivery",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Temporary ordinary source delivery; no sensing or new recording"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        val notification = NotificationCompat.Builder(this, RECOVERY_FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("HUGR retained recording delivery")
            .setContentText("Waiting for one ordinary Phone resume; no sensing")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(
            RECOVERY_FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        finalizedRecoveryForegroundActive = true
        val recoveryStartedAtMs = SystemClock.elapsedRealtime()
        finalizedRecoveryLifetime.start(recoveryStartedAtMs)
        finalizedDeliveryRecoveryAttemptId?.let { attempt ->
            FinalizedRecoveryReadiness.foreground(attempt, causalComponentInstanceId, recoveryStartedAtMs)
        }
        transportHandler.post(finalizedRecoveryHeartbeat)
        transportHandler.postDelayed(finalizedRecoveryDeadline, FinalizedDeliveryLifetime.MAX_DURATION_MS)
        true
    } catch (failure: Exception) {
        Log.e(TAG, "Standard retained delivery foreground promotion rejected", failure)
        finalizedRecoveryLifetime.stop()
        false
    }

    private fun stopFinalizedRecoveryWithoutAcknowledgement(reason: String) {
        if (!finalizedDeliveryRecoveryOnly) return
        markFinalizedRecoveryStop(FinalizedRecoveryStopReason.fromStopLabel(reason))
        finalizedRecoveryLifetime.stop()
        notificationCompletionBlocked = true
        Log.w(TAG, "Retained source delivery stopped without acknowledgement: $reason")
        broadcastStatus("RETAINED DELIVERY STOPPED: $reason; source preserved")
        stopSelf()
    }

    private fun markFinalizedRecoveryStop(reason: FinalizedRecoveryStopReason) {
        synchronized(this) {
            if (finalizedRecoveryStopReason != null) return
            finalizedRecoveryStopReason = reason
            finalizedDeliveryRecoveryAttemptId?.let { attempt ->
                FinalizedRecoveryReadiness.stop(attempt, causalComponentInstanceId, reason)
            }
            recordCausal(CausalEventCode.FINALIZED_RECOVERY_STOP_REQUESTED, reasonCode = reason.code)
        }
    }

    private fun stopFinalizedRecoveryForegroundLifetime() {
        finalizedRecoveryLifetime.stop()
        transportHandler.removeCallbacks(finalizedRecoveryHeartbeat)
        transportHandler.removeCallbacks(finalizedRecoveryDeadline)
        if (finalizedRecoveryForegroundActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            finalizedRecoveryForegroundActive = false
        }
    }

    private fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val hasAmp = vibrator?.hasAmplitudeControl() ?: false
        val supportsPrimitives = checkPrimitiveSupport()
        Log.i(TAG, "Vibrator initialized: hasAmplitudeControl=$hasAmp, supportsPrimitives=$supportsPrimitives")
    }

    private var usePrimitives = false

    private fun initializeHapticNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            HAPTIC_CHANNEL_ID,
            HAPTIC_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Silent research-only HUGR wrist haptic delivery"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
        Log.i(TAG, "Haptic notification channel ready: policy=$HAPTIC_POLICY_VERSION channel=$HAPTIC_CHANNEL_ID")
    }

    private fun checkPrimitiveSupport(): Boolean {
        val vib = vibrator ?: return false
        return try {
            val supported = vib.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )
            usePrimitives = supported
            Log.i(TAG, "Primitive support check: CLICK+THUD+TICK = $supported")
            supported
        } catch (e: Exception) {
            Log.w(TAG, "Primitive support check failed: ${e.message}")
            usePrimitives = false
            false
        }
    }

    override fun onDestroy() {
        if (finalizedDeliveryRecoveryOnly) {
            val reason = finalizedDeliveryRecoveryAttemptId?.let { attempt ->
                FinalizedRecoveryReadiness.destroyed(attempt, causalComponentInstanceId)
            } ?: finalizedRecoveryStopReason ?: FinalizedRecoveryStopReason.UNEXPLAINED_DESTROY
            recordCausal(CausalEventCode.BLE_SERVICE_DESTROYED, reasonCode = reason.code)
        } else if (isStandardRuntime()) {
            recordCausal(CausalEventCode.BLE_SERVICE_DESTROYED)
        }
        if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onRuntimeDestroyed()
        if (isEgressOnlyRuntime()) stopEgressForegroundLifetime()
        if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryForegroundLifetime()
        super.onDestroy()
        Log.d(TAG, "BleGattService destroyed: mode=$runtimeMode")
        vibrator?.cancel()
        if (isStandardRuntime()) {
            healthHandler.removeCallbacks(healthTicker)
            transportHandler.removeCallbacks(transportTicker)
            transportHandler.removeCallbacks(liveSourceFlush)
            transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
            sourceMtuLineageGeneration = sourceMtuReadinessGate.onDisconnected()
            sourceResumePreparationGeneration.set(-1L)
            replayStartLineageGuard.advanceLineage()
            pendingSourceResumeRequest = null
            queuedReplayManifestEndIndex = null
            pendingLiveSourceRecords.clear()
            liveSourceFlushScheduled = false
            notificationQueue.reset()
            unregisterSensorReceivers()
        }
        stopAdvertising()
        closeGattServer()
        sourceResumeExecutor.shutdownNow()
    }

    // ─── Initialization ─────────────────────────────────────────────────────────

    private fun initializeBluetooth() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                Log.e(TAG, "Bluetooth not available or disabled")
                recordStandardGattReadinessFailure()
                if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onReadinessFailure("BLUETOOTH_UNAVAILABLE")
                return
            }

            advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e(TAG, "BLE advertiser not available")
                recordStandardGattReadinessFailure()
                if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onReadinessFailure("ADVERTISER_UNAVAILABLE")
                return
            }

            openGattServer(bluetoothManager)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth: ${e.message}", e)
            recordStandardGattReadinessFailure()
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onReadinessFailure("BLUETOOTH_INITIALIZATION_FAILED")
        }
    }

    // ─── GATT Server Setup ──────────────────────────────────────────────────────

    private fun openGattServer(bluetoothManager: BluetoothManager) {
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            recordStandardGattReadinessFailure()
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onReadinessFailure("GATT_SERVER_UNAVAILABLE")
            return
        }
        if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_SERVER_OPENED)

        // Create the HUGR service
        val service = BluetoothGattService(
            HUGR_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        if (isEgressOnlyRuntime()) {
            addEgressCharacteristics(service)
            registerGattService(service)
            return
        }

        // EDA Characteristic (NOTIFY + READ)
        edaCharacteristic = BluetoothGattCharacteristic(
            EDA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
        }
        service.addCharacteristic(edaCharacteristic!!)

        // PPG Characteristic (NOTIFY + READ) — carries Green, IR, Red raw PPG at 25 Hz
        ppgCharacteristic = BluetoothGattCharacteristic(
            PPG_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
        }
        service.addCharacteristic(ppgCharacteristic!!)

        // Accelerometer Characteristic (NOTIFY + READ)
        accelCharacteristic = BluetoothGattCharacteristic(
            ACCEL_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
        }
        service.addCharacteristic(accelCharacteristic!!)

        // Skin Temperature Characteristic (NOTIFY + READ) — continuous skin + ambient temp
        skinTempCharacteristic = BluetoothGattCharacteristic(
            SKIN_TEMP_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
        }
        service.addCharacteristic(skinTempCharacteristic!!)

        // Status Characteristic (READ + NOTIFY)
        statusCharacteristic = BluetoothGattCharacteristic(
            STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
            // Initial status: all sensors active
            value = byteArrayOf(0x01) // 0x01 = active
        }
        service.addCharacteristic(statusCharacteristic!!)

        // Haptic acknowledgement (READ + NOTIFY). This reports watch receipt and
        // Android API acceptance/failure; it never claims physical perception.
        hapticReceiptCharacteristic = BluetoothGattCharacteristic(
            HAPTIC_RECEIPT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
            value = byteArrayOf(WATCHTOWER_V2_MARKER.toByte())
        }
        service.addCharacteristic(hapticReceiptCharacteristic!!)

        // Build 45 canonical source records (READ + NOTIFY). Live and replay use
        // the same CRC-framed canonical bytes; the frame carries replay truth.
        sourceRecordCharacteristic = BluetoothGattCharacteristic(
            SOURCE_RECORD_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(createCccdDescriptor())
            value = byteArrayOf(SourceReplayProtocol.MARKER.toByte(), SourceReplayProtocol.VERSION.toByte())
        }
        service.addCharacteristic(sourceRecordCharacteristic!!)

        // Build 45 resume and completed-segment acknowledgement control writes.
        sourceControlCharacteristic = BluetoothGattCharacteristic(
            SOURCE_CONTROL_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(sourceControlCharacteristic!!)

        addEgressCharacteristics(service)

        // Haptic Command Characteristic (WRITE)
        val hapticCharacteristic = BluetoothGattCharacteristic(
            HAPTIC_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(hapticCharacteristic)

        registerGattService(service)
    }

    /**
     * Evidence Egress v1 is an explicit pull protocol. In EGRESS_ONLY mode these
     * are the only characteristics registered; no telemetry, source, or haptic
     * characteristic is present and the notification queue remains untouched.
     */
    private fun addEgressCharacteristics(service: BluetoothGattService) {
        egressOfferCharacteristic = BluetoothGattCharacteristic(
            EGRESS_OFFER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(egressOfferCharacteristic!!)
        egressChunkCharacteristic = BluetoothGattCharacteristic(
            EGRESS_CHUNK_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(egressChunkCharacteristic!!)
        egressControlCharacteristic = BluetoothGattCharacteristic(
            EGRESS_CONTROL_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(egressControlCharacteristic!!)
        egressReceiptCharacteristic = BluetoothGattCharacteristic(
            EGRESS_RECEIPT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(egressReceiptCharacteristic!!)
    }

    private fun registerGattService(service: BluetoothGattService) {
        val added = gattServer!!.addService(service)
        if (added) {
            Log.i(TAG, "HUGR GATT service registered with ${service.characteristics.size} characteristics")
        } else {
            Log.e(TAG, "Failed to add HUGR service to GATT server")
            if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_SERVICE_FAILED)
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onReadinessFailure("GATT_SERVICE_REGISTER_REJECTED")
            if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryWithoutAcknowledgement("GATT_SERVICE_REGISTER_REJECTED")
        }
    }

    private fun createCccdDescriptor(): BluetoothGattDescriptor {
        return BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
    }

    // ─── GATT Server Callback ───────────────────────────────────────────────────

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (isEgressOnlyRuntime()) {
                        // Each egress GATT connection starts a fresh, volatile correlation record.
                        // This prevents an earlier offer callback from being mistaken for this attempt.
                        EvidenceEgressVolatileDiagnostics.beginConnectionAttempt(status, newState)
                        connectedDevice = device
                        negotiatedMtu = 23
                        Log.i(TAG, "Evidence Egress-only phone connected: ${device?.address}")
                        return
                    }
                    if (finalizedDeliveryRecoveryOnly &&
                        !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
                    ) {
                        stopFinalizedRecoveryWithoutAcknowledgement("DEADLINE_BEFORE_CONNECTION")
                        return
                    }
                    val lineage = causalLineageState.onConnected()
                    sourceMtuLineageGeneration = sourceMtuReadinessGate.onConnected()
                    sourceResumePreparationGeneration.set(-1L)
                    causalSourceDetailCountByLineage[lineage] = 0
                    recordCausal(
                        CausalEventCode.GATT_CONNECTED,
                        bleLineage = lineage,
                        arg0 = status.toLong(),
                    )
                    notificationQueue.reset()
                    notificationCompletionBlocked = true
                    connectedDevice = device
                    pendingSourceResumeRequest = null
                    queuedReplayManifestEndIndex = null
                    negotiatedMtu = 23
                    replayActive = false
                    durablePhoneRecordIndex = 0L
                    lastReplayQueuedRecordIndex = 0L
                    replayHighWaterRecordIndex = 0L
                    replayBacklogCount = 0L
                    activeReplaySessionId = null
                    transportHandler.removeCallbacks(liveSourceFlush)
                    transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
                    pendingLiveSourceRecords.clear()
                    liveSourceFlushScheduled = false
                    replayStartLineageGuard.advanceLineage()
                    if (device != null) synchronized(notificationSubscriptions) {
                        notificationSubscriptions.getOrPut(device.address) { mutableSetOf() }
                    }
                    Log.i(TAG, "Phone connected: ${device?.address}")
                    broadcastStatus("BLE: Phone CONNECTED (${device?.address})")
                    notifyDeviceHealth()
                    // Start advertising after connection? No — stop advertising to save power
                    // KEEP ADVERTISING for reconnection
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (isEgressOnlyRuntime()) {
                        EvidenceEgressVolatileDiagnostics.onConnectionStateChange(status, newState)
                        connectedDevice = null
                        negotiatedMtu = 23
                        Log.i(TAG, "Evidence Egress-only phone disconnected: ${device?.address}")
                        startAdvertising()
                        return
                    }
                    if (finalizedDeliveryRecoveryOnly) finalizedRecoveryLifetime.stop()
                    val disconnect = causalLineageState.onDisconnected(status)
                    sourceMtuLineageGeneration = sourceMtuReadinessGate.onDisconnected()
                    sourceResumePreparationGeneration.set(-1L)
                    recordCausal(
                        CausalEventCode.GATT_DISCONNECTED,
                        bleLineage = disconnect.bleLineage,
                        arg0 = disconnect.gattStatus.toLong(),
                        arg1 = if (disconnect.localCancelRequested) 1L else 0L,
                        reasonCode = disconnect.abortReasonCode,
                    )
                    notificationCompletionBlocked = true
                    connectedDevice = null
                    pendingSourceResumeRequest = null
                    queuedReplayManifestEndIndex = null
                    negotiatedMtu = 23
                    replayActive = false
                    durablePhoneRecordIndex = 0L
                    lastReplayQueuedRecordIndex = 0L
                    replayHighWaterRecordIndex = 0L
                    replayBacklogCount = 0L
                    activeReplaySessionId = null
                    transportHandler.removeCallbacks(liveSourceFlush)
                    transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
                    pendingLiveSourceRecords.clear()
                    liveSourceFlushScheduled = false
                    replayStartLineageGuard.advanceLineage()
                    notificationQueue.reset()
                    if (device != null) synchronized(notificationSubscriptions) {
                        notificationSubscriptions.remove(device.address)
                    }
                    Log.i(TAG, "Phone disconnected: ${device?.address}")
                    broadcastStatus("BLE: Phone DISCONNECTED")
                    if (finalizedDeliveryRecoveryOnly) {
                        // One bounded recovery attempt: a fresh explicit launch is required
                        // before any further connection; the durable segment is not deleted.
                        stopFinalizedRecoveryWithoutAcknowledgement("PHONE_DISCONNECTED")
                    } else {
                        startAdvertising()
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            val activeDevice = connectedDevice
            if (device == null || activeDevice == null || device.address != activeDevice.address) return
            negotiatedMtu = mtu.coerceAtLeast(23)
            if (isEgressOnlyRuntime()) {
                EvidenceEgressVolatileDiagnostics.onMtuChanged(negotiatedMtu)
                Log.i(TAG, "Evidence Egress-only negotiated GATT MTU=$negotiatedMtu")
                return
            }
            if (!sourceMtuReadinessGate.onMtuChanged(sourceMtuLineageGeneration, negotiatedMtu)) return
            recordCausal(
                CausalEventCode.MTU_CHANGED,
                arg0 = negotiatedMtu.toLong(),
                arg1 = maximumAttPayloadBytes().toLong(),
            )
            Log.i(TAG, "Negotiated GATT MTU=$negotiatedMtu ATT payload=${maximumAttPayloadBytes()}")
            broadcastStatus("BLE MTU: $negotiatedMtu (payload ${maximumAttPayloadBytes()})")
            val readiness = sourceMtuReadinessGate.snapshot()
            if (readiness.released && readiness.attPayloadBytes < SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL) {
                transportHandler.post { abortTransportLineage("unsafe_mtu_after_release") }
                return
            }
            transportHandler.post {
                if (!releasePreparedSourceIfReady()) notifyDeviceHealth()
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onGattServiceAdded(status)
            if (finalizedDeliveryRecoveryOnly &&
                !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
            ) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Service added successfully — starting advertising")
                if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_SERVICE_READY)
                startAdvertising()
            } else {
                Log.e(TAG, "Failed to add service, status: $status")
                if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_SERVICE_FAILED, arg0 = status.toLong())
                if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryWithoutAcknowledgement("GATT_SERVICE_FAILED")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d(TAG, "Read request for ${characteristic?.uuid}")
            val isEgressRead = characteristic?.uuid in setOf(
                EGRESS_OFFER_CHARACTERISTIC_UUID,
                EGRESS_CHUNK_CHARACTERISTIC_UUID,
                EGRESS_RECEIPT_CHARACTERISTIC_UUID,
            )
            val isEgressOfferRead = characteristic?.uuid == EGRESS_OFFER_CHARACTERISTIC_UUID
            if (isEgressOnlyRuntime() && isEgressOfferRead) {
                EvidenceEgressVolatileDiagnostics.onOfferReadEntered(requestId)
            }
            if (isEgressRead && negotiatedMtu < EvidenceEgressContract.MINIMUM_GATT_MTU) {
                val sent = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, byteArrayOf()) == true
                if (isEgressOnlyRuntime() && isEgressOfferRead) {
                    EvidenceEgressVolatileDiagnostics.onOfferResponse(BluetoothGatt.GATT_FAILURE, sent)
                }
                return
            }
            if (isEgressRead && offset != 0) {
                val sent = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, byteArrayOf()) == true
                if (isEgressOnlyRuntime() && isEgressOfferRead) {
                    EvidenceEgressVolatileDiagnostics.onOfferResponse(BluetoothGatt.GATT_INVALID_OFFSET, sent)
                }
                return
            }
            val value = when (characteristic?.uuid) {
                EGRESS_OFFER_CHARACTERISTIC_UUID -> EvidenceEgressActivation.offerBytes()
                EGRESS_CHUNK_CHARACTERISTIC_UUID -> EvidenceEgressActivation.selectedChunkBytes() ?: byteArrayOf()
                EGRESS_RECEIPT_CHARACTERISTIC_UUID -> EvidenceEgressActivation.receiptBytes()
                else -> characteristic?.value ?: byteArrayOf(0)
            }
            val validOffset = offset in 0..value.size
            val responseStatus = if (validOffset) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
            val responseValue = if (validOffset) value.copyOfRange(offset, value.size) else byteArrayOf()
            val sent = gattServer?.sendResponse(device, requestId, responseStatus, offset, responseValue) == true
            if (isEgressOnlyRuntime() && isEgressOfferRead) {
                EvidenceEgressVolatileDiagnostics.onOfferResponse(responseStatus, sent)
            }
            if (
                sent &&
                characteristic?.uuid == EGRESS_CHUNK_CHARACTERISTIC_UUID &&
                responseValue.isNotEmpty() &&
                offset + responseValue.size >= value.size
            ) {
                // The next ordered selection is available only after the phone has performed
                // the dedicated chunk read; this does not alter telemetry queue state.
                EvidenceEgressActivation.recordSelectedChunkRead()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "Write request for ${characteristic?.uuid}, ${value?.size} bytes")
            var writeResponseStatus = BluetoothGatt.GATT_SUCCESS

            // Handle haptic command writes
            if (characteristic?.uuid == HAPTIC_CHARACTERISTIC_UUID && value != null) {
                Log.i(TAG, "Haptic command received: ${value.size} bytes")
                val isV2 = value.size >= 7 && (value[0].toInt() and 0xFF) == WATCHTOWER_V2_MARKER
                val commandSequence = if (isV2) {
                    ByteBuffer.wrap(value, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL
                } else {
                    0L
                }
                val patternId = if (isV2) value[5].toInt() and 0xFF else value.firstOrNull()?.toInt()?.and(0xFF) ?: 3
                val intensity = if (isV2) value[6].toInt() and 0xFF else value.getOrNull(1)?.toInt()?.and(0xFF) ?: 255
                val policyVersion = if (isV2 && value.size >= 8) value[7].toInt() and 0xFF else 0

                notifyHapticReceipt(commandSequence, 1, patternId, DETAIL_OK, policyVersion) // Watch application received command.
                val previous = if (isV2) processedHapticCommands[commandSequence] else null
                val execution = when {
                    previous != null -> {
                        Log.w(TAG, "Duplicate haptic command suppressed and prior result replayed: $commandSequence")
                        notifyHapticReceipt(commandSequence, 1, patternId, DETAIL_DUPLICATE_REPLAY, previous.policyVersion)
                        previous
                    }
                    isV2 && policyVersion != HAPTIC_POLICY_VERSION -> {
                        HapticCommandRegistry.Result(false, DETAIL_UNSUPPORTED_POLICY, patternId, policyVersion)
                    }
                    else -> executeNotificationHaptic(commandSequence, patternId, intensity, HAPTIC_POLICY_VERSION)
                }
                if (isV2 && previous == null) processedHapticCommands[commandSequence] = execution
                notifyHapticReceipt(
                    commandSequence,
                    if (execution.accepted) 2 else 3,
                    patternId,
                    execution.detailCode,
                    execution.policyVersion
                )
            }

            if (characteristic?.uuid == SOURCE_CONTROL_CHARACTERISTIC_UUID && value != null) {
                try {
                    when (value.getOrNull(2)?.toInt()?.and(0xFF)) {
                        SourceReplayProtocol.RESUME_REQUEST -> handleSourceResume(
                            SourceReplayProtocol.decodeResumeRequest(value),
                        )

                        SourceReplayProtocol.SEGMENT_ACKNOWLEDGEMENT -> handleSourceAcknowledgement(
                            SourceReplayProtocol.decodeSegmentAcknowledgement(value),
                        )

                        else -> throw SourceJournalCorruptionException("Unknown source-control message")
                    }
                } catch (error: Exception) {
                    writeResponseStatus = BluetoothGatt.GATT_FAILURE
                    Log.e(TAG, "Build 45 source-control rejection: ${error.message}", error)
                    broadcastStatus("SOURCE CONTROL REJECTED: ${error.javaClass.simpleName}")
                }
            }

            if (characteristic?.uuid == EGRESS_CONTROL_CHARACTERISTIC_UUID && value != null) {
                // A control write can only select a chunk for a previously consent-activated
                // in-memory session or submit its final verified phone receipt. It never
                // addresses source-control/replay, telemetry, journal, or package generation.
                if (preparedWrite || offset != 0) {
                    writeResponseStatus = BluetoothGatt.GATT_FAILURE
                    Log.w(TAG, "Evidence Egress v1 rejects prepared or offset control writes")
                } else if (negotiatedMtu < EvidenceEgressContract.MINIMUM_GATT_MTU) {
                    writeResponseStatus = BluetoothGatt.GATT_FAILURE
                    Log.w(TAG, "Evidence Egress v1 requires negotiated MTU ${EvidenceEgressContract.MINIMUM_GATT_MTU}")
                } else if (!EvidenceEgressActivation.handleControl(value)) {
                    writeResponseStatus = BluetoothGatt.GATT_FAILURE
                    Log.w(TAG, "Evidence Egress v1 control rejected")
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, writeResponseStatus, offset, value)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            if (descriptor?.uuid == CCCD_UUID) {
                val characteristicUuid = descriptor.characteristic?.uuid
                val value = if (device != null && characteristicUuid != null && isNotificationEnabled(device, characteristicUuid)) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, descriptor?.value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor?.uuid == CCCD_UUID) {
                // Client is subscribing/unsubscribing to notifications
                val charUuid = descriptor.characteristic?.uuid
                val enabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
                Log.i(TAG, "Notifications ${if (enabled) "ENABLED" else "DISABLED"} for $charUuid")
                broadcastStatus("BLE: Notifications ${if (enabled) "ON" else "OFF"} for ${charUuid?.toString()?.take(8) ?: "unknown"}")
                if (device != null && charUuid != null) {
                    connectedDevice = device
                    synchronized(notificationSubscriptions) {
                        val enabledCharacteristics = notificationSubscriptions.getOrPut(device.address) { mutableSetOf() }
                        if (enabled) enabledCharacteristics.add(charUuid) else enabledCharacteristics.remove(charUuid)
                    }
                    if (enabled) notificationCompletionBlocked = false
                    if (charUuid == SOURCE_RECORD_CHARACTERISTIC_UUID) {
                        sourceMtuReadinessGate.onSourceCccdChanged(sourceMtuLineageGeneration, enabled)
                        recordCausal(
                            if (enabled) CausalEventCode.SOURCE_CCCD_ENABLED else CausalEventCode.SOURCE_CCCD_DISABLED,
                        )
                        if (enabled) {
                            val request = pendingSourceResumeRequest
                            if (request != null && sourceMtuReadinessGate.snapshot().preparedResumePlan == null) {
                                scheduleSourceReplayPreparation(request)
                            } else {
                                transportHandler.post { releasePreparedSourceIfReady() }
                            }
                            transportHandler.post { scheduleLiveSourceFlushIfReady() }
                        }
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            if (notificationCompletionBlocked) {
                Log.w(TAG, "Ignoring notification completion from an aborted transport lineage")
                return
            }
            val activeDevice = connectedDevice
            if (device == null || activeDevice == null || device.address != activeDevice.address) {
                Log.w(TAG, "Ignoring notification completion from a non-active device")
                return
            }
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) Log.e(TAG, "BLE notification completion failed: status=$status device=${device?.address}")
            causalNotificationGattStatus = status
            try {
                notificationQueue.onNotificationSent(success)
            } finally {
                causalNotificationGattStatus = BluetoothGatt.GATT_SUCCESS
            }
        }
    }

    // ─── BLE Advertising ────────────────────────────────────────────────────────

    private fun startAdvertising() {
        if (isEgressOnlyRuntime() && egressAdvertiserReady) return
        val adv = advertiser ?: run {
            recordStandardGattReadinessFailure()
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(0) // Advertise indefinitely
            .build()

        // Primary advertisement: service UUID only (fits in 31 bytes)
        // DO NOT include device name here — it causes overflow with 128-bit UUID
        val advertisingData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(HUGR_SERVICE_UUID))
            .build()

        // Scan response: include device name (sent only when phone actively scans)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()

        try {
            adv.startAdvertising(settings, advertisingData, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting advertising: ${e.message}")
            recordStandardGattReadinessFailure()
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onAdvertiserStartFailed("SECURITY_EXCEPTION")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}", e)
            recordStandardGattReadinessFailure()
            if (isEgressOnlyRuntime()) EvidenceEgressVolatileDiagnostics.onAdvertiserStartFailed("START_EXCEPTION")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            egressAdvertiserReady = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            if (finalizedDeliveryRecoveryOnly &&
                !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
            ) {
                stopFinalizedRecoveryWithoutAcknowledgement("DEADLINE")
                stopAdvertising()
                return
            }
            Log.i(TAG, "BLE advertising started — UUID visible to phone")
            if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_ADVERTISING_READY)
            if (finalizedDeliveryRecoveryOnly) finalizedDeliveryRecoveryAttemptId?.let { attempt ->
                FinalizedRecoveryReadiness.advertising(attempt, causalComponentInstanceId, SystemClock.elapsedRealtime())
            }
            if (isEgressOnlyRuntime()) {
                egressAdvertiserReady = true
                EvidenceEgressVolatileDiagnostics.onAdvertiserStarted()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN($errorCode)"
            }
            Log.e(TAG, "BLE advertising FAILED: $reason")
            if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_ADVERTISING_FAILED, arg0 = errorCode.toLong())
            if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryWithoutAcknowledgement("ADVERTISING_FAILED")
            if (isEgressOnlyRuntime()) {
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED && egressAdvertiserReady) {
                    EvidenceEgressVolatileDiagnostics.onAdvertiserStarted()
                } else {
                    EvidenceEgressVolatileDiagnostics.onAdvertiserStartFailed(reason)
                }
            }
        }
    }

    // ─── Sensor Data Receivers ──────────────────────────────────────────────────

    private val edaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val conductance = intent?.getFloatExtra("conductance", 0f) ?: return
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifyEda(
                conductance,
                timestamp,
                intent.getStringExtra("deliveryMode") ?: "REALTIME",
                intent.getIntExtra("batchSize", 1),
                intent.getBooleanExtra("screenOn", true)
            )
        }
    }

    private val ppgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ppgGreen = intent?.getIntExtra("ppgGreen", 0) ?: return
            val ppgIR = intent.getIntExtra("ppgIR", 0)
            val ppgRed = intent.getIntExtra("ppgRed", 0)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val deliveryMode = intent.getStringExtra("deliveryMode") ?: "REALTIME"
            val batchSize = intent.getIntExtra("batchSize", 1)
            val screenOn = intent.getBooleanExtra("screenOn", true)
            if (deliveryMode == "FALLBACK") {
                // HR+IBI fallback — format byte 0x02
                notifyHeartRateFallback(ppgGreen, ppgIR, ppgRed, timestamp, deliveryMode, batchSize, screenOn)
            } else {
                // Raw PPG — format byte 0x01
                notifyPpg(ppgGreen, ppgIR, ppgRed, timestamp, deliveryMode, batchSize, screenOn)
            }
        }
    }

    // HR dual-stream receiver — sends hardware-derived HR+IBI via BLE (format 0x02)
    private val hrReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val hr = intent?.getIntExtra("heartRate", 0) ?: return
            val ibiMs = intent.getIntExtra("ibiMs", 0)
            val hrStatus = intent.getIntExtra("hrStatus", -1)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifyHeartRateFallback(
                hr,
                ibiMs,
                hrStatus,
                timestamp,
                intent.getStringExtra("deliveryMode") ?: "REALTIME",
                intent.getIntExtra("batchSize", 1),
                intent.getBooleanExtra("screenOn", true)
            )
        }
    }

    private val cardiacEvidenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            notifyCardiacEvidence(
                kind = intent.getIntExtra("kind", 0),
                value = intent.getIntExtra("value", 0),
                status = intent.getIntExtra("status", -1),
                sourceTimestamp = intent.getLongExtra("timestamp", System.currentTimeMillis()),
                callbackId = intent.getIntExtra("callbackId", 0),
                pointIndex = intent.getIntExtra("pointIndex", 0),
                pointCount = intent.getIntExtra("pointCount", 0),
                listIndex = intent.getIntExtra("listIndex", -1),
                listCount = intent.getIntExtra("listCount", 0),
                contractAnomaly = intent.getBooleanExtra("contractAnomaly", false),
                deliveryMode = intent.getStringExtra("deliveryMode") ?: "REALTIME",
                batchSize = intent.getIntExtra("batchSize", 1),
                screenOn = intent.getBooleanExtra("screenOn", true)
            )
        }
    }

    private val skinTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val skinTemp = intent?.getFloatExtra("skinTemp", 0f) ?: return
            val ambientTemp = intent.getFloatExtra("ambientTemp", 0f)
            val status = intent.getIntExtra("status", -1)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifySkinTemp(
                skinTemp,
                ambientTemp,
                status,
                timestamp,
                intent.getStringExtra("deliveryMode") ?: "REALTIME",
                intent.getIntExtra("batchSize", 1),
                intent.getBooleanExtra("screenOn", true)
            )
        }
    }

    private val accelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getIntExtra("x", 0) ?: return
            val y = intent.getIntExtra("y", 0)
            val z = intent.getIntExtra("z", 0)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifyAccel(
                x,
                y,
                z,
                timestamp,
                intent.getStringExtra("deliveryMode") ?: "REALTIME",
                intent.getIntExtra("batchSize", 1),
                intent.getBooleanExtra("screenOn", true)
            )
        }
    }

    private val healthMetadataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            healthSdkConnected = intent.getBooleanExtra("sdkConnected", healthSdkConnected)
            healthSdkStatus = intent.getIntExtra("sdkStatus", healthSdkStatus)
            activeSensorMask = intent.getIntExtra("activeSensorMask", activeSensorMask)
            healthFlushCount = intent.getLongExtra("flushCount", healthFlushCount)
            healthScreenOn = intent.getBooleanExtra("screenOn", healthScreenOn)
            healthSourceDataLoss = intent.getBooleanExtra("sourceDataLoss", healthSourceDataLoss)
            healthSourceDataLossStreamCode = intent.getIntExtra("sourceDataLossStreamCode", healthSourceDataLossStreamCode)
            healthSourceDataLossFirstSequence = intent.getLongExtra("sourceDataLossFirstSequence", healthSourceDataLossFirstSequence)
            healthSourceDataLossLastSequence = intent.getLongExtra("sourceDataLossLastSequence", healthSourceDataLossLastSequence)
            healthSourceDataLossReasonCode = intent.getIntExtra("sourceDataLossReasonCode", healthSourceDataLossReasonCode)
            notifyDeviceHealth()
        }
    }

    private val sourceRecordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HealthSensorService.ACTION_SOURCE_FINALIZED) {
                try {
                    enqueueNewlyFinalizedManifests()
                    pumpReplay()
                } catch (error: Exception) {
                    Log.e(TAG, "Bounded final manifest enqueue failed: ${error.message}", error)
                    broadcastStatus("SOURCE DELIVERY FAILED: bounded final manifest pending reconnect/resume")
                }
                return
            }
            val bytes = intent?.getByteArrayExtra("canonicalBytes") ?: return
            try {
                val decoded = SourceJournalCodec.decodeAll(bytes)
                if (decoded.records.size != 1 || decoded.validBytes != bytes.size) {
                    throw SourceJournalCorruptionException("Live source broadcast was not one canonical record")
                }
                if (activeReplaySessionId != null && activeReplaySessionId != sourceJournal.watchBootSessionId) {
                    return
                }
                val record = decoded.records.single()
                recordSourceDetail(
                    CausalEventCode.SOURCE_BROADCAST_RECEIVED,
                    stream = CausalStreamCode.fromSourceStream(record.stream),
                    recordIndexStart = record.recordIndex,
                    recordIndexEnd = record.recordIndex,
                    arg0 = record.sourceSequence,
                )
                enqueueNewlyFinalizedManifests()
                queueLiveSourceRecord(record)
            } catch (error: Exception) {
                Log.e(TAG, "Build 45 live source-record rejection: ${error.message}", error)
                broadcastStatus("DATA LOSS: invalid live source record")
                transportHandler.post { abortTransportLineage("live_source_record_rejected") }
            }
        }
    }

    private fun registerSensorReceivers() {
        registerReceiver(ppgReceiver, IntentFilter("com.hugr.wearos.PPG_DATA"), RECEIVER_EXPORTED)
        registerReceiver(healthMetadataReceiver, IntentFilter(HealthSensorService.ACTION_DEVICE_HEALTH_UPDATE), Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(
            sourceRecordReceiver,
            IntentFilter().apply {
                addAction(HealthSensorService.ACTION_SOURCE_RECORD)
                addAction(HealthSensorService.ACTION_SOURCE_FINALIZED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        Log.d(TAG, "Sensor broadcast receivers registered")
    }

    private fun unregisterSensorReceivers() {
        try {
            unregisterReceiver(ppgReceiver)
            unregisterReceiver(healthMetadataReceiver)
            unregisterReceiver(sourceRecordReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers: ${e.message}")
        }
    }

    // ─── Build 45 durable source record and replay path ──────────────────────────

    private fun maximumAttPayloadBytes(): Int = (negotiatedMtu - ATT_PROTOCOL_OVERHEAD_BYTES).coerceAtLeast(20)

    private fun abortTransportLineage(reason: String) {
        if (finalizedDeliveryRecoveryOnly) finalizedRecoveryLifetime.stop()
        val stalledDevice = connectedDevice
        val reasonCode = CausalReasonCode.fromAbortReason(reason).code
        causalLineageState.markAbort(reasonCode)
        recordCausal(CausalEventCode.ABORT_REQUESTED, reasonCode = reasonCode)
        notificationCompletionBlocked = true
        notificationSubscriptions.clear()
        sourceMtuLineageGeneration = sourceMtuReadinessGate.onDisconnected()
        connectedDevice = null
        pendingSourceResumeRequest = null
        activeReplaySessionId = null
        queuedReplayManifestEndIndex = null
        replayActive = false
        durablePhoneRecordIndex = 0L
        lastReplayQueuedRecordIndex = 0L
        replayHighWaterRecordIndex = 0L
        replayBacklogCount = 0L
        pendingLiveSourceRecords.clear()
        liveSourceFlushScheduled = false
        transportHandler.removeCallbacks(liveSourceFlush)
        transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
        replayStartLineageGuard.advanceLineage()
        notificationQueue.reset()
        if (stalledDevice != null) {
            causalLineageState.markCancelRequested()
            recordCausal(CausalEventCode.CANCEL_CONNECTION_REQUESTED, reasonCode = reasonCode)
            runCatching { gattServer?.cancelConnection(stalledDevice) }
                .onFailure { Log.e(TAG, "Failed to cancel stalled GATT connection after $reason", it) }
        }
        if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryWithoutAcknowledgement(reason)
    }

    private fun queueStreamFor(stream: SourceStreamCode): GattNotificationStream = when (stream) {
        SourceStreamCode.CARDIAC -> GattNotificationStream.CARDIAC
        SourceStreamCode.EDA -> GattNotificationStream.EDA
        SourceStreamCode.ACCEL -> GattNotificationStream.ACCEL
        SourceStreamCode.SKIN_TEMP -> GattNotificationStream.SKIN_TEMP
        SourceStreamCode.DEVICE_HEALTH -> GattNotificationStream.DEVICE_HEALTH
    }

    private fun queueLiveSourceRecord(record: WatchSourceRecord) {
        connectedDevice ?: return
        val readiness = sourceMtuReadinessGate.snapshot()
        if (!readiness.released) {
            val plan = readiness.preparedResumePlan ?: return
            if (record.watchBootSessionId != plan.watchBootSessionId ||
                record.recordIndex <= plan.replayHighWaterRecordIndex
            ) return
        } else {
            val activeSession = activeReplaySessionId ?: return
            if (record.watchBootSessionId != activeSession || record.recordIndex <= replayHighWaterRecordIndex) return
        }
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) {
            when (sourceMtuReadinessGate.reservePendingLiveRecord(sourceMtuLineageGeneration)) {
                PendingLiveReservationResult.ACCEPTED -> Unit
                PendingLiveReservationResult.STALE_LINEAGE -> return
                PendingLiveReservationResult.CAPACITY_EXCEEDED -> {
                    broadcastStatus("DATA LOSS RISK: pre-MTU live pending capacity reached")
                    transportHandler.post { abortTransportLineage("critical_queue_fault") }
                    return
                }
            }
        }
        pendingLiveSourceRecords += record
        scheduleLiveSourceFlushIfReady()
    }

    private fun scheduleLiveSourceFlushIfReady() {
        if (pendingLiveSourceRecords.isNotEmpty() &&
            sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration) &&
            !liveSourceFlushScheduled
        ) {
            liveSourceFlushScheduled = true
            transportHandler.postDelayed(liveSourceFlush, LIVE_SOURCE_BATCH_MS)
        }
    }

    private fun flushLiveSourceRecords() {
        if (pendingLiveSourceRecords.isEmpty()) return
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) return
        val device = connectedDevice
        if (device == null || !isNotificationEnabled(device, SOURCE_RECORD_CHARACTERISTIC_UUID)) {
            pendingLiveSourceRecords.clear()
            return
        }
        val records = pendingLiveSourceRecords.sortedBy { it.recordIndex }
        pendingLiveSourceRecords.clear()
        enqueueSourceRecords(records, replay = false)
    }

    private fun enqueueSourceRecords(records: List<WatchSourceRecord>, replay: Boolean) {
        if (records.isEmpty()) return
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) return
        val characteristic = sourceRecordCharacteristic ?: return
        val payloadLimit = maximumAttPayloadBytes()
        if (replay && payloadLimit < SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL) {
            replayActive = false
            broadcastStatus("DATA LOSS RISK: MTU $negotiatedMtu cannot meet five-ACCEL replay gate")
            return
        }
        try {
            SourceReplayProtocol.buildDataFrames(records, replay, payloadLimit).forEach { frameBytes ->
                val frame = SourceReplayProtocol.decodeDataFrame(frameBytes)
                val first = frame.records.first()
                val result = notificationQueue.enqueue(
                    stream = queueStreamFor(first.stream),
                    characteristicUuid = characteristic.uuid,
                    payload = frameBytes,
                    sourceSequence = first.sourceSequence,
                    sourceTimestampMs = first.sourceTimestampMs,
                    origin = if (replay) GattNotificationOrigin.REPLAY else GattNotificationOrigin.LIVE,
                    recordCount = frame.records.size,
                    lossless = true,
                )
                recordSourceDetail(
                    CausalEventCode.SOURCE_ENQUEUED,
                    stream = CausalStreamCode.fromSourceStream(first.stream),
                    recordIndexStart = first.recordIndex,
                    recordIndexEnd = frame.records.last().recordIndex,
                    arg0 = frame.records.size.toLong(),
                    reasonCode = result.ordinal,
                )
                if (result == GattEnqueueResult.CRITICAL_OVERFLOW) {
                    replayActive = false
                    broadcastStatus("DATA LOSS: canonical source queue overflow")
                    return
                }
                if (replay && (result == GattEnqueueResult.QUEUED || result == GattEnqueueResult.COALESCED)) {
                    lastReplayQueuedRecordIndex = maxOf(lastReplayQueuedRecordIndex, frame.records.last().recordIndex)
                }
            }
        } catch (error: Exception) {
            replayActive = false
            Log.e(TAG, "Build 45 source-frame enqueue failed: ${error.message}", error)
            broadcastStatus("DATA LOSS: source frame enqueue failed")
            transportHandler.post { abortTransportLineage("source_frame_enqueue_failed") }
        }
    }

    private fun enqueueNewlyFinalizedManifests() {
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) return
        if (sourceJournal.drainNewlyFinalizedManifests().isEmpty()) return
        val activeSession = activeReplaySessionId ?: return
        val plan = SourceReplayWindow.planFinalizedManifestDelivery(
            activeSession = activeSession,
            durablePhoneRecordIndex = durablePhoneRecordIndex,
            replayHighWaterRecordIndex = replayHighWaterRecordIndex,
            queuedManifestEndIndex = queuedReplayManifestEndIndex,
            finalizedManifests = sourceJournal.finalizedManifests(activeSession),
        ) ?: return

        replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex
        replayBacklogCount = replayHighWaterRecordIndex - durablePhoneRecordIndex
        replayActive = replayBacklogCount > 0L
        // Use the normal manifest queue so every final manifest establishes the
        // exact queued endpoint required by acknowledgement validation.
        enqueueNextManifestForReplayWindow(activeSession, replayHighWaterRecordIndex)
    }

    private fun handleSourceResume(request: SourceResumeRequest) {
        if (finalizedDeliveryRecoveryOnly &&
            !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
        ) {
            stopFinalizedRecoveryWithoutAcknowledgement("RESUME_AFTER_DEADLINE_OR_DISCONNECT")
            throw SourceJournalCorruptionException("Retained delivery attempt is no longer active")
        }
        recordCausal(
            CausalEventCode.RESUME_RECEIVED,
            recordIndexStart = request.cumulativeRecordIndex.coerceAtLeast(0L),
        )
        val existingRequest = pendingSourceResumeRequest
        if (existingRequest != null) {
            if (existingRequest != request) {
                throw SourceJournalCorruptionException("Conflicting source resume in active BLE lineage")
            }
            transportHandler.post { releasePreparedSourceIfReady() }
            return
        }
        pendingSourceResumeRequest = request
        scheduleSourceReplayPreparation(request)
    }

    private fun scheduleSourceReplayPreparation(request: SourceResumeRequest) {
        val generation = sourceMtuLineageGeneration
        while (true) {
            val scheduled = sourceResumePreparationGeneration.get()
            if (scheduled == generation) return
            if (sourceResumePreparationGeneration.compareAndSet(scheduled, generation)) break
        }
        sourceResumeExecutor.execute {
            if (generation != sourceMtuLineageGeneration) return@execute
            val result = runCatching { prepareSourceReplay(request) }
            transportHandler.post {
                if (generation != sourceMtuLineageGeneration || pendingSourceResumeRequest != request) return@post
                result.fold(
                    onSuccess = { plan ->
                        runCatching { applyPreparedSourceReplay(generation, plan) }
                            .onFailure(::failSourceResumePreparation)
                    },
                    onFailure = ::failSourceResumePreparation,
                )
                sourceResumePreparationGeneration.compareAndSet(generation, -1L)
            }
        }
    }

    private fun failSourceResumePreparation(error: Throwable) {
        Log.e(TAG, "Source resume preparation failed: ${error.message}", error)
        broadcastStatus("SOURCE CONTROL REJECTED: ${error.javaClass.simpleName}")
        abortTransportLineage("source_resume_prepare_failed")
    }

    private fun prepareSourceReplay(request: SourceResumeRequest): PreparedSourceResumePlan {
        sourceJournal.finalizeActiveSegment()
        sourceJournal.discardNewlyFinalizedManifests()
        val session = if (finalizedDeliveryRecoveryOnly) {
            requireRetainedFinalizedDeliverySession()
        } else {
            sourceJournal.oldestFinalizedSessionId() ?: sourceJournal.watchBootSessionId
        }
        val acceptedIndex = if (request.watchBootSessionId == session) request.cumulativeRecordIndex.coerceAtLeast(0L) else 0L
        val highWater = sourceJournal.highestFinalizedRecordIndex(session)
        if (acceptedIndex > highWater) {
            throw SourceJournalCorruptionException("Phone resume index exceeds watch journal")
        }
        return PreparedSourceResumePlan(
            watchBootSessionId = session,
            acceptedRecordIndex = acceptedIndex,
            replayHighWaterRecordIndex = highWater,
            replayBacklogCount = highWater - acceptedIndex,
        )
    }

    private fun applyPreparedSourceReplay(generation: Long, plan: PreparedSourceResumePlan) {
        when (sourceMtuReadinessGate.prepareResume(generation, plan)) {
            ResumePreparationResult.PREPARED,
            ResumePreparationResult.DUPLICATE -> Unit
            ResumePreparationResult.CONFLICT -> throw SourceJournalCorruptionException(
                "Conflicting source resume changed the frozen replay window",
            )
            ResumePreparationResult.STALE_LINEAGE -> throw SourceJournalCorruptionException(
                "Source resume arrived outside the active BLE lineage",
            )
        }
        activeReplaySessionId = plan.watchBootSessionId
        durablePhoneRecordIndex = plan.acceptedRecordIndex
        lastReplayQueuedRecordIndex = plan.acceptedRecordIndex
        replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex
        replayBacklogCount = plan.replayBacklogCount
        replayActive = false
        queuedReplayManifestEndIndex = null
        if (!releasePreparedSourceIfReady()) {
            recordCausal(
                CausalEventCode.RESUME_DEFERRED,
                recordIndexStart = plan.acceptedRecordIndex,
                recordIndexEnd = plan.replayHighWaterRecordIndex,
                arg0 = replayBacklogCount,
            )
            broadcastStatus(
                "SOURCE WAITING: frozen ${plan.acceptedRecordIndex + 1L}-${plan.replayHighWaterRecordIndex}; " +
                    "MTU=$negotiatedMtu payload=${maximumAttPayloadBytes()}",
            )
        }
    }

    private fun releasePreparedSourceIfReady(): Boolean {
        val release = sourceMtuReadinessGate.takeReleaseIfReady(sourceMtuLineageGeneration) ?: return false
        val plan = release.preparedResumePlan
        activeReplaySessionId = plan.watchBootSessionId
        durablePhoneRecordIndex = plan.acceptedRecordIndex
        lastReplayQueuedRecordIndex = plan.acceptedRecordIndex
        replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex
        replayBacklogCount = plan.replayBacklogCount
        replayActive = replayBacklogCount > 0L
        queuedReplayManifestEndIndex = null

        notifyDeviceHealth()
        flushLiveSourceRecords()
        enqueueNextManifestForReplayWindow(plan.watchBootSessionId, plan.replayHighWaterRecordIndex)
        recordCausal(
            CausalEventCode.RESUME_APPLIED,
            recordIndexStart = durablePhoneRecordIndex,
            recordIndexEnd = replayHighWaterRecordIndex,
            arg0 = replayBacklogCount,
            arg1 = release.pendingLiveRecordCount.toLong(),
        )
        if (replayActive) {
            broadcastStatus(
                "REPLAYING ${plan.watchBootSessionId.toString().take(8)}: $replayBacklogCount source records " +
                    "from index ${durablePhoneRecordIndex + 1L} through frozen $replayHighWaterRecordIndex",
            )
            transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
            replayStartGeneration = replayStartLineageGuard.capture()
            transportHandler.postDelayed(replayStartAfterLiveOpportunity, LIVE_SOURCE_BATCH_MS)
        } else {
            broadcastStatus("CAUGHT UP: frozen source replay window empty")
        }
        return true
    }

    private fun beginSourceReplaySession(session: UUID, acceptedIndex: Long) {
        val highestRecordIndex = sourceJournal.highestFinalizedRecordIndex(session)
        if (acceptedIndex > highestRecordIndex) {
            throw SourceJournalCorruptionException("Phone resume index exceeds watch journal")
        }
        activeReplaySessionId = session
        durablePhoneRecordIndex = acceptedIndex
        lastReplayQueuedRecordIndex = acceptedIndex
        replayHighWaterRecordIndex = highestRecordIndex
        replayBacklogCount = replayHighWaterRecordIndex - durablePhoneRecordIndex
        replayActive = replayBacklogCount > 0
        queuedReplayManifestEndIndex = null
        recordCausal(
            CausalEventCode.RESUME_APPLIED,
            recordIndexStart = durablePhoneRecordIndex,
            recordIndexEnd = replayHighWaterRecordIndex,
            arg0 = replayBacklogCount,
        )

        enqueueNextManifestForReplayWindow(session, replayHighWaterRecordIndex)
        broadcastStatus(
            "REPLAYING ${session.toString().take(8)}: $replayBacklogCount source records " +
                "from index ${durablePhoneRecordIndex + 1L} through frozen $replayHighWaterRecordIndex"
        )
        transportHandler.removeCallbacks(replayStartAfterLiveOpportunity)
        replayStartGeneration = replayStartLineageGuard.capture()
        transportHandler.postDelayed(replayStartAfterLiveOpportunity, LIVE_SOURCE_BATCH_MS)
    }

    private fun enqueueNextManifestForReplayWindow(session: UUID, highWaterRecordIndex: Long) {
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) return
        if (queuedReplayManifestEndIndex != null) return
        val manifest = SourceReplayWindow.nextManifestToQueue(
            activeSession = session,
            durablePhoneRecordIndex = durablePhoneRecordIndex,
            replayHighWaterRecordIndex = highWaterRecordIndex,
            queuedManifestEndIndex = queuedReplayManifestEndIndex,
            manifests = sourceJournal.finalizedManifests(session),
        ) ?: return
        val bytes = SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(manifest))
        if (bytes.size > maximumAttPayloadBytes()) {
            replayActive = false
            throw SourceJournalCorruptionException("Manifest exceeds negotiated ATT payload")
        }
        val result = notificationQueue.enqueue(
            stream = GattNotificationStream.DEVICE_HEALTH,
            characteristicUuid = SOURCE_RECORD_CHARACTERISTIC_UUID,
            payload = bytes,
            sourceSequence = manifest.firstRecordIndex,
            sourceTimestampMs = System.currentTimeMillis(),
            origin = GattNotificationOrigin.REPLAY,
            lossless = true,
        )
        if (result == GattEnqueueResult.CRITICAL_OVERFLOW || result == GattEnqueueResult.DROPPED_LOW_PRIORITY) {
            replayActive = false
            throw SourceJournalCapacityException("Retained manifest could not enter the transport queue")
        }
        if (result == GattEnqueueResult.QUEUED || result == GattEnqueueResult.COALESCED) {
            queuedReplayManifestEndIndex = manifest.lastRecordIndex
        }
    }

    private fun handleSourceAcknowledgement(acknowledgement: SourceSegmentAcknowledgement) =
        synchronized(finalizedRecoveryLifetime) {
        if (finalizedDeliveryRecoveryOnly &&
            !finalizedRecoveryLifetime.permitsDelivery(SystemClock.elapsedRealtime())
        ) {
            stopFinalizedRecoveryWithoutAcknowledgement("ACK_AFTER_DEADLINE_OR_DISCONNECT")
            throw SourceJournalCorruptionException("Retained delivery attempt is no longer active")
        }
        val activeSession = SourceReplayWindow.validateAcknowledgement(
            activeSession = activeReplaySessionId,
            durablePhoneRecordIndex = durablePhoneRecordIndex,
            replayHighWaterRecordIndex = replayHighWaterRecordIndex,
            acknowledgement = acknowledgement,
            queuedManifestEndIndex = queuedReplayManifestEndIndex,
        )
        if (finalizedDeliveryRecoveryOnly && activeSession != finalizedDeliverySourceSessionId) {
            throw SourceJournalCorruptionException("Recovery ACK crossed the selected retained source session")
        }
        SourceReplayWindow.validateQueuedManifestAcknowledgement(
            queuedManifestEndIndex = queuedReplayManifestEndIndex,
            acknowledgement = acknowledgement,
        )
        val acceptedManifest = sourceJournal.finalizedManifests(activeSession)
            .firstOrNull { it.lastRecordIndex == acknowledgement.cumulativeRecordIndex }
            ?: throw SourceJournalCorruptionException("Queued acknowledgement has no retained finalized manifest")
        if (!sourceJournal.acknowledgeCompletedSegment(
                acknowledgement.watchBootSessionId,
                acknowledgement.cumulativeRecordIndex,
                acknowledgement.completedSegmentSha256,
            )
        ) {
            throw SourceJournalCorruptionException("Acknowledgement endpoint/hash did not match a finalized segment")
        }
        recordCausal(
            CausalEventCode.SOURCE_SEGMENT_ACK_ACCEPTED,
            recordIndexStart = acceptedManifest.firstRecordIndex,
            recordIndexEnd = acceptedManifest.lastRecordIndex,
            arg0 = replayHighWaterRecordIndex,
        )
        // An older unacknowledged manifest can be verified after the Phone has
        // already stored later records. Never regress the durable resume index
        // or resend data merely because its manifest ACK arrived later.
        durablePhoneRecordIndex = maxOf(durablePhoneRecordIndex, acknowledgement.cumulativeRecordIndex)
        queuedReplayManifestEndIndex = null
        lastReplayQueuedRecordIndex = maxOf(lastReplayQueuedRecordIndex, durablePhoneRecordIndex)
        replayBacklogCount = replayHighWaterRecordIndex - durablePhoneRecordIndex
        replayActive = replayBacklogCount > 0
        broadcastStatus(if (replayActive) "REPLAYING: $replayBacklogCount remain" else "CAUGHT UP: source journal acknowledged")
        // A durable contiguous Phone endpoint does not mean older manifests
        // were acknowledged. Always offer the next retained manifest, even
        // when no data records need replaying in this window.
        enqueueNextManifestForReplayWindow(activeSession, replayHighWaterRecordIndex)
        pumpReplay()
        advanceReplaySessionIfReady()
        closeBoundedFreshRuntimeAfterDeliveryIfComplete()
    }

    private fun closeBoundedFreshRuntimeAfterDeliveryIfComplete() {
        if (finalizedDeliveryRecoveryOnly) {
            val sourceSessionId = finalizedDeliverySourceSessionId ?: return
            if (sourceJournal.hasFinalizedSegments(sourceSessionId)) return
            // This attempt is scoped to exactly one retained source session.
            // Other fresh-v2 sessions stay untouched for their own authorization.
            val markers = NormalStartupMarkerStore(this)
            markers.record(sourceSessionId.toString(), NormalStartupStage.BOUNDED_RUN_DELIVERY_ACKNOWLEDGED)
            WatchSourceRuntime.closeFreshAfterDelivery()
            markers.record(sourceSessionId.toString(), NormalStartupStage.BOUNDED_RUN_GATT_STOP_REQUESTED)
            markFinalizedRecoveryStop(FinalizedRecoveryStopReason.EXACT_ACK_COMPLETED)
            finalizedRecoveryLifetime.stop()
            transportHandler.removeCallbacks(finalizedRecoveryDeadline)
            stopSelf()
            return
        }
        val marker = NormalStartupMarkerStore(this).read() ?: return
        if (marker.stage != NormalStartupStage.BOUNDED_RUN_FINALIZED) return
        if (sourceJournal.finalizedManifests().isNotEmpty()) return
        NormalStartupMarkerStore(this).recordCurrent(NormalStartupStage.BOUNDED_RUN_DELIVERY_ACKNOWLEDGED)
        WatchSourceRuntime.closeFreshAfterDelivery()
        NormalStartupMarkerStore(this).recordCurrent(NormalStartupStage.BOUNDED_RUN_GATT_STOP_REQUESTED)
        stopSelf()
    }

    private fun pumpReplay() {
        if (!replayActive || notificationCompletionBlocked) return
        if (!sourceMtuReadinessGate.canConstructSourceFrames(sourceMtuLineageGeneration)) return
        val device = connectedDevice ?: return
        if (!isNotificationEnabled(device, SOURCE_RECORD_CHARACTERISTIC_UUID)) return
        if (notificationQueue.snapshot().pendingReplayFrames >= 4) return
        val activeSession = activeReplaySessionId ?: return
        val replayReadUpperBound = SourceReplayWindow.replayReadUpperBound(
            replayHighWaterRecordIndex = replayHighWaterRecordIndex,
            queuedManifestEndIndex = queuedReplayManifestEndIndex,
        ) ?: return
        if (lastReplayQueuedRecordIndex >= replayReadUpperBound) return
        val page = sourceJournal.readRecordsAfter(
            activeSession,
            lastReplayQueuedRecordIndex,
            replayReadUpperBound,
            REPLAY_PAGE_RECORDS,
        )
        if (page.isEmpty()) {
            replayBacklogCount = replayHighWaterRecordIndex - durablePhoneRecordIndex
            if (replayBacklogCount == 0L) {
                replayActive = false
                broadcastStatus("CAUGHT UP: no source replay backlog")
                advanceReplaySessionIfReady()
            }
            return
        }
        enqueueSourceRecords(page, replay = true)
    }

    private fun advanceReplaySessionIfReady() {
        if (finalizedDeliveryRecoveryOnly) return // Never traverse another retained source session.
        val completedSession = activeReplaySessionId ?: return
        if (completedSession == sourceJournal.watchBootSessionId) return
        if (durablePhoneRecordIndex < replayHighWaterRecordIndex) return
        if (sourceJournal.hasFinalizedSegments(completedSession)) return
        if (notificationQueue.snapshot().pendingReplayFrames != 0) return
        val nextSession = sourceJournal.retainedSessionIds().firstOrNull()
        if (nextSession == null) {
            activeReplaySessionId = sourceJournal.watchBootSessionId
            replayHighWaterRecordIndex = 0L
            replayActive = false
            queuedReplayManifestEndIndex = null
            broadcastStatus("CAUGHT UP: all retained watch sessions acknowledged")
            return
        }
        beginSourceReplaySession(nextSession, 0L)
    }

    private fun sourceFrameRecords(item: GattNotification): List<WatchSourceRecord> {
        if (item.characteristicUuid != SOURCE_RECORD_CHARACTERISTIC_UUID) return emptyList()
        if (item.payload.size < 3 || (item.payload[2].toInt() and 0xFF) != SourceReplayProtocol.DATA_FRAME) return emptyList()
        return SourceReplayProtocol.decodeDataFrame(item.payload).records
    }

    private fun handleNotificationTriggered(item: GattNotification) {
        val records = try {
            sourceFrameRecords(item)
        } catch (error: Exception) {
            broadcastStatus("DATA LOSS: triggered source frame failed canonical decode")
            transportHandler.post { abortTransportLineage("source_trigger_decode_failed") }
            return
        }
        if (records.isNotEmpty()) {
            sourceJournal.recordDelivery(
                records,
                if (item.origin == GattNotificationOrigin.REPLAY) SourceDeliveryState.REPLAY_SENT else SourceDeliveryState.LIVE_SENT,
            )
        }
    }

    private fun handleNotificationCompleted(item: GattNotification, gattStatus: Int) {
        recordNotificationCompletion(item, success = true, gattStatus = gattStatus)
        val records = try {
            sourceFrameRecords(item)
        } catch (error: Exception) {
            broadcastStatus("DATA LOSS: completed source frame failed canonical decode")
            transportHandler.post { abortTransportLineage("source_completion_decode_failed") }
            return
        }
        if (records.isNotEmpty()) {
            sourceJournal.recordDelivery(
                records,
                if (item.origin == GattNotificationOrigin.REPLAY) SourceDeliveryState.REPLAY_CONFIRMED else SourceDeliveryState.LIVE_CONFIRMED,
            )
        }
        if (item.origin == GattNotificationOrigin.REPLAY) {
            pumpReplay()
            advanceReplaySessionIfReady()
        }
    }

    private fun handleNotificationFailed(item: GattNotification, gattStatus: Int) {
        recordNotificationCompletion(item, success = false, gattStatus = gattStatus)
        if (item.characteristicUuid == SOURCE_RECORD_CHARACTERISTIC_UUID) {
            replayActive = false
            lastReplayQueuedRecordIndex = durablePhoneRecordIndex
            broadcastStatus("SOURCE DELIVERY FAILED: reconnect/resume required")
            transportHandler.post { abortTransportLineage("source_notification_failed") }
        }
    }

    private fun recordNotificationTriggerResult(item: GattNotification, result: GattNotificationTrigger) {
        if (item.characteristicUuid != SOURCE_RECORD_CHARACTERISTIC_UUID) return
        val range = sourceRecordRange(item)
        recordSourceDetail(
            CausalEventCode.SOURCE_TRIGGER_RESULT,
            stream = sourceStreamFor(item.stream),
            recordIndexStart = range.first,
            recordIndexEnd = range.second,
            arg0 = item.recordCount.toLong(),
            reasonCode = CausalReasonCode.fromTrigger(result).code,
        )
    }

    private fun recordNotificationTimeout(item: GattNotification) {
        val range = sourceRecordRange(item)
        recordCausal(
            CausalEventCode.SOURCE_NOTIFICATION_TIMEOUT,
            stream = sourceStreamFor(item.stream),
            recordIndexStart = range.first,
            recordIndexEnd = range.second,
            arg0 = item.recordCount.toLong(),
            reasonCode = CausalReasonCode.NOTIFICATION_TIMEOUT.code,
        )
    }

    private fun recordNotificationCompletion(item: GattNotification, success: Boolean, gattStatus: Int) {
        if (item.characteristicUuid != SOURCE_RECORD_CHARACTERISTIC_UUID) return
        val range = sourceRecordRange(item)
        recordSourceDetail(
            if (success) CausalEventCode.SOURCE_NOTIFICATION_COMPLETED else CausalEventCode.SOURCE_NOTIFICATION_FAILED,
            stream = sourceStreamFor(item.stream),
            recordIndexStart = range.first,
            recordIndexEnd = range.second,
            arg0 = item.recordCount.toLong(),
            reasonCode = gattStatus,
        )
    }

    private fun sourceRecordRange(item: GattNotification): Pair<Long, Long> = runCatching {
        val records = sourceFrameRecords(item)
        if (records.isEmpty()) 0L to 0L else records.first().recordIndex to records.last().recordIndex
    }.getOrDefault(0L to 0L)

    private fun sourceStreamFor(stream: GattNotificationStream): CausalStreamCode? = when (stream) {
        GattNotificationStream.CARDIAC -> CausalStreamCode.CARDIAC
        GattNotificationStream.DEVICE_HEALTH -> CausalStreamCode.DEVICE_HEALTH
        GattNotificationStream.EDA -> CausalStreamCode.EDA
        GattNotificationStream.SKIN_TEMP -> CausalStreamCode.SKIN_TEMP
        GattNotificationStream.ACCEL -> CausalStreamCode.ACCEL
        GattNotificationStream.PPG -> CausalStreamCode.PPG
        GattNotificationStream.HAPTIC_RECEIPT -> null
    }

    private fun recordSourceDetail(
        code: CausalEventCode,
        stream: CausalStreamCode? = null,
        recordIndexStart: Long = 0L,
        recordIndexEnd: Long = 0L,
        arg0: Long = 0L,
        reasonCode: Int = CausalReasonCode.NONE.code,
    ) {
        val lineage = causalLineageState.currentLineage()
        val count = causalSourceDetailCountByLineage[lineage] ?: 0
        if (count >= MAX_CAUSAL_SOURCE_DETAILS_PER_LINEAGE) return
        causalSourceDetailCountByLineage[lineage] = count + 1
        recordCausal(
            code,
            bleLineage = lineage,
            stream = stream,
            recordIndexStart = recordIndexStart,
            recordIndexEnd = recordIndexEnd,
            arg0 = arg0,
            reasonCode = reasonCode,
        )
    }

    private fun recordCausal(
        code: CausalEventCode,
        bleLineage: Long = causalLineageState.currentLineage(),
        stream: CausalStreamCode? = null,
        recordIndexStart: Long = 0L,
        recordIndexEnd: Long = 0L,
        arg0: Long = 0L,
        arg1: Long = 0L,
        reasonCode: Int = CausalReasonCode.NONE.code,
    ) {
        WatchCausalRuntime.record(
            this,
            code,
            CausalComponentCode.BLE,
            causalComponentInstanceId,
            bleLineage = bleLineage,
            stream = stream,
            recordIndexStart = recordIndexStart,
            recordIndexEnd = recordIndexEnd,
            arg0 = arg0,
            arg1 = arg1,
            reasonCode = reasonCode,
        )
    }

    private fun recordStandardGattReadinessFailure() {
        if (isStandardRuntime()) recordCausal(CausalEventCode.GATT_ADVERTISING_FAILED)
        if (finalizedDeliveryRecoveryOnly) stopFinalizedRecoveryWithoutAcknowledgement("GATT_READINESS_FAILED")
    }

    // ─── PRODUCTION RESEARCH HAPTIC POLICY v1 ───────────────────────────────────
    // One channel and one vibration pattern only until matched-device delivery and
    // perception are verified. Pattern ID records intended semantic action; it does
    // not select a different physical notification pattern in policy v1.

    private fun executeNotificationHaptic(
        commandSequence: Long,
        patternId: Int,
        intensity: Int,
        policyVersion: Int
    ): HapticCommandRegistry.Result {
        if (patternId == 0) {
            val ids = synchronized(activeHapticNotificationIds) {
                val snapshot = activeHapticNotificationIds.toList()
                activeHapticNotificationIds.clear()
                snapshot
            }
            ids.forEach(notificationManager::cancel)
            Log.i(TAG, "Haptic stop requested: command=$commandSequence cancelled=${ids.size}")
            return HapticCommandRegistry.Result(true, DETAIL_STOP_ACCEPTED, patternId, policyVersion)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return HapticCommandRegistry.Result(false, DETAIL_PERMISSION_MISSING, patternId, policyVersion)
        }
        if (!notificationManager.areNotificationsEnabled()) {
            return HapticCommandRegistry.Result(false, DETAIL_NOTIFICATIONS_DISABLED, patternId, policyVersion)
        }
        val channel = notificationManager.getNotificationChannel(HAPTIC_CHANNEL_ID)
        if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE || !channel.shouldVibrate()) {
            return HapticCommandRegistry.Result(false, DETAIL_CHANNEL_DISABLED, patternId, policyVersion)
        }

        return try {
            val notification = NotificationCompat.Builder(this, HAPTIC_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("HUGR research haptic")
                .setContentText("Manual test · command $commandSequence")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setTimeoutAfter(30_000L)
                .build()
            val notificationId = 4_100 + ((commandSequence and 0x7FFF_FFFFL) % 100_000L).toInt()
            notificationManager.notify(notificationId, notification)
            synchronized(activeHapticNotificationIds) {
                activeHapticNotificationIds.add(notificationId)
            }
            Log.i(TAG, "Haptic notification requested: command=$commandSequence policy=$policyVersion pattern=$patternId intensityMetadata=$intensity")
            HapticCommandRegistry.Result(true, DETAIL_OK, patternId, policyVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Haptic notification request failed: ${e.message}", e)
            HapticCommandRegistry.Result(false, DETAIL_NOTIFY_EXCEPTION, patternId, policyVersion)
        }
    }

    // ─── CONTAINED LEGACY DIRECT HAPTIC ENGINE (INACTIVE) ───────────────────────
    // PRIMITIVE-FIRST architecture: Uses hardware-optimized primitives (CLICK, THUD, SPIN)
    // which are the SAME engine that powers Samsung's Gallop/Heartbeat/Bounce patterns.
    // Falls back to waveform if primitives not supported.
    // Wave made of perceptible grains. Wave shape from grain DENSITY, not amplitude.
    // State-dependent: calm=gentle, activated=breathing, overwhelmed=grounding, disconnected=wake-up

    private fun executeGranularHaptic(data: ByteArray): Pair<Boolean, Int> {
        if (data.isEmpty()) return Pair(false, 3)
        val vib = vibrator ?: run {
            Log.e(TAG, "HAPTIC FAIL: vibrator is null!")
            return Pair(false, 2)
        }
        val patternId = data[0].toInt() and 0xFF
        Log.i(TAG, "HAPTIC executing pattern $patternId (usePrimitives=$usePrimitives)")

        try {
            if (usePrimitives) {
                when (patternId) {
                    1 -> playPrimitiveCalm(vib)
                    2 -> playPrimitiveBreathing(vib)
                    3 -> playPrimitiveGrounding(vib)
                    4 -> playPrimitiveWakeUp(vib)
                    else -> playPrimitiveGrounding(vib)
                }
            } else {
                when (patternId) {
                    1 -> playWaveformCalm(vib)
                    2 -> playWaveformBreathing(vib)
                    3 -> playWaveformGrounding(vib)
                    4 -> playWaveformWakeUp(vib)
                    else -> playWaveformGrounding(vib)
                }
            }
            return Pair(true, 0)
        } catch (e: Exception) {
            Log.e(TAG, "HAPTIC error: ${e.message}", e)
            try {
                vib.vibrate(VibrationEffect.createOneShot(500, 255))
                Log.i(TAG, "HAPTIC fallback: 500ms oneshot at max")
                return Pair(true, 1)
            } catch (e2: Exception) {
                Log.e(TAG, "HAPTIC even fallback failed: ${e2.message}")
                return Pair(false, 4)
            }
        }
    }

    private fun notifyHapticReceipt(
        commandSequence: Long,
        status: Int,
        patternId: Int,
        detailCode: Int,
        policyVersion: Int
    ) {
        val characteristic = hapticReceiptCharacteristic ?: return
        val occurredAtWatchMs = System.currentTimeMillis()
        val buffer = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(WATCHTOWER_V2_MARKER.toByte())
        buffer.putInt(commandSequence.toInt())
        buffer.putLong(occurredAtWatchMs)
        buffer.put(status.toByte())
        buffer.put(patternId.coerceIn(0, 255).toByte())
        buffer.put(detailCode.coerceIn(0, 255).toByte())
        buffer.put(policyVersion.coerceIn(0, 255).toByte())
        transmitSensor(
            characteristic = characteristic,
            payload = buffer.array(),
            stream = GattNotificationStream.HAPTIC_RECEIPT,
            sourceSequence = commandSequence,
            sourceTimestampMs = occurredAtWatchMs,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PRIMITIVE-BASED PATTERNS (hardware-optimized, same engine as Samsung Gallop/Heartbeat)
    // These use CLICK, THUD, SPIN — the strongest haptic primitives available
    // ═══════════════════════════════════════════════════════════════════════════════

    // Pattern 1: CALM — "I see you" — 2 CLICKs with pause
    private fun playPrimitiveCalm(vib: Vibrator) {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 300)
            .compose()
        vib.vibrate(effect)
        Log.i(TAG, "HAPTIC PRIMITIVE: Calm (2 CLICKs)")
    }

    // Pattern 2: ACTIVATED — attention CLICKs + breathing wave (SLOW_RISE → QUICK_FALL)
    private fun playPrimitiveBreathing(vib: Vibrator) {
        val effect = VibrationEffect.startComposition()
            // ATTENTION: 3 rapid CLICKs (strongest possible)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 50)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 50)
            // PAUSE — "listen"
            // BREATHING WAVE: inhale (rise) → exhale (fall) — granular with SPINs
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f, 400)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
            // Second breath cycle
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.9f, 100)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.5f)
            .compose()
        vib.vibrate(effect)
        Log.i(TAG, "HAPTIC PRIMITIVE: Breathing (3 CLICKs + 2 breath cycles)")
    }

    // Pattern 3: OVERWHELMED — strong attention THUDs + grounding SPINs + calming fall
    private fun playPrimitiveGrounding(vib: Vibrator) {
        val effect = VibrationEffect.startComposition()
            // ATTENTION: THUD + CLICKs (maximum physical impact)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 80)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 50)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 50)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 50)
            // GROUNDING WAVE: slow calming descent
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.7f, 500)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.4f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.5f, 200)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.3f, 100)
            .compose()
        vib.vibrate(effect)
        Log.i(TAG, "HAPTIC PRIMITIVE: Grounding (THUD+CLICKs + calming wave)")
    }

    // Pattern 4: DISCONNECTED — aggressive wake-up (maximum everything)
    private fun playPrimitiveWakeUp(vib: Vibrator) {
        val effect = VibrationEffect.startComposition()
            // AGGRESSIVE ATTENTION: alternating THUD and CLICK at max scale
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
            // URGENT WAVE: fast SPINs (wobble/unstable feel)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f, 200)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.9f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 1.0f, 30)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.8f, 30)
            // FINAL THUD — "you're HERE"
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 200)
            .compose()
        vib.vibrate(effect)
        Log.i(TAG, "HAPTIC PRIMITIVE: Wake-up (THUD/CLICK cascade + SPIN wave + final THUD)")
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // WAVEFORM FALLBACK (if primitives not supported — uses createWaveform at max amp)
    // ═══════════════════════════════════════════════════════════════════════════════

    private fun playWaveformCalm(vib: Vibrator) {
        val t = longArrayOf(0, 25, 8, 25, 300, 25, 8, 25)
        val a = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
        vib.vibrate(VibrationEffect.createWaveform(t, a, -1))
        Log.i(TAG, "HAPTIC WAVEFORM: Calm (2 double-taps)")
    }

    private fun playWaveformBreathing(vib: Vibrator) {
        val t = longArrayOf(
            0, 20, 8, 20, 8, 20, 400,
            20, 100, 20, 80, 20, 60, 20, 50, 20, 40, 20, 40, 20, 40, 20, 40,
            20, 60, 20, 80, 20, 100, 20, 120, 20, 140, 20, 160, 20, 200
        )
        val a = intArrayOf(
            0, 255, 0, 255, 0, 255, 0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0
        )
        vib.vibrate(VibrationEffect.createWaveform(t, a, -1))
        Log.i(TAG, "HAPTIC WAVEFORM: Breathing (3-tap + granular wave)")
    }

    private fun playWaveformGrounding(vib: Vibrator) {
        val t = longArrayOf(
            0, 25, 8, 25, 8, 25, 8, 25, 8, 25, 500,
            20, 50, 20, 40, 20, 40, 20, 40,
            20, 80, 20, 100, 20, 120, 20, 140, 20, 160, 20, 180, 20, 200, 20, 220, 20, 250
        )
        val a = intArrayOf(
            0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0,
            255, 0, 255, 0, 255, 0, 255, 0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0
        )
        vib.vibrate(VibrationEffect.createWaveform(t, a, -1))
        Log.i(TAG, "HAPTIC WAVEFORM: Grounding (5-tap + slow exhale)")
    }

    private fun playWaveformWakeUp(vib: Vibrator) {
        val t = longArrayOf(
            0, 25, 8, 25, 8, 25, 8, 25, 8, 25, 8, 25, 8, 25, 8, 25, 300,
            20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 200,
            20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30
        )
        val a = intArrayOf(
            0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0
        )
        vib.vibrate(VibrationEffect.createWaveform(t, a, -1))
        Log.i(TAG, "HAPTIC WAVEFORM: Wake-up (8-tap + fast dense x2)")
    }

    // ─── Notification Methods (push data to connected phone) ────────────────────

    private fun nextSensorSequence(stream: String): Long {
        totalSensorPackets = (totalSensorPackets + 1) and 0xFFFF_FFFFL
        return when (stream) {
            "EDA" -> { edaSequence = (edaSequence + 1) and 0xFFFF_FFFFL; edaSequence }
            "PPG" -> { ppgSequence = (ppgSequence + 1) and 0xFFFF_FFFFL; ppgSequence }
            "CARDIAC" -> { cardiacSequence = (cardiacSequence + 1) and 0xFFFF_FFFFL; cardiacSequence }
            "ACCEL" -> { accelSequence = (accelSequence + 1) and 0xFFFF_FFFFL; accelSequence }
            "SKIN_TEMP" -> { skinTempSequence = (skinTempSequence + 1) and 0xFFFF_FFFFL; skinTempSequence }
            else -> totalSensorPackets
        }
    }

    private fun deliveryFlags(deliveryMode: String, batchSize: Int, screenOn: Boolean): Byte {
        var flags = 0
        if (screenOn) flags = flags or 0x01
        if (deliveryMode == "FLUSH" || (!screenOn && deliveryMode == "FALLBACK")) flags = flags or 0x02
        if (batchSize > 1) flags = flags or 0x04
        return flags.toByte()
    }

    private fun putSensorEnvelope(
        buffer: ByteBuffer,
        sequence: Long,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        buffer.put(WATCHTOWER_V2_MARKER.toByte())
        buffer.putInt(sequence.toInt())
        buffer.putLong(timestamp)
        buffer.put(deliveryFlags(deliveryMode, batchSize, screenOn))
        buffer.putShort(batchSize.coerceIn(0, 65_535).toShort())
    }

    private fun transmitSensor(
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        stream: GattNotificationStream,
        sourceSequence: Long,
        sourceTimestampMs: Long,
    ): Boolean {
        if (connectedDevice == null) {
            droppedNoConnectionCount = (droppedNoConnectionCount + 1) and 0xFFFF_FFFFL
            if (droppedNoConnectionCount % 100L == 1L) Log.w(TAG, "${stream.name} packet unavailable to phone: no connected device")
            return false
        }
        return when (notificationQueue.enqueue(stream, characteristic.uuid, payload, sourceSequence, sourceTimestampMs)) {
            GattEnqueueResult.QUEUED, GattEnqueueResult.COALESCED -> true
            GattEnqueueResult.DROPPED_LOW_PRIORITY -> false
            GattEnqueueResult.CRITICAL_OVERFLOW -> {
                Log.e(TAG, "CRITICAL BLE queue overflow for ${stream.name} sequence=$sourceSequence")
                false
            }
        }
    }

    private fun triggerGattNotification(item: GattNotification): GattNotificationTrigger {
        val device = connectedDevice ?: return GattNotificationTrigger.NO_CONNECTION
        if (!isNotificationEnabled(device, item.characteristicUuid)) return GattNotificationTrigger.NOT_SUBSCRIBED
        val characteristic = characteristicFor(item.characteristicUuid) ?: return GattNotificationTrigger.IMMEDIATE_FAILURE
        val server = gattServer ?: return GattNotificationTrigger.NO_CONNECTION
        return try {
            val triggered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, item.payload.copyOf()) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = item.payload.copyOf()
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
            if (triggered) GattNotificationTrigger.TRIGGERED else GattNotificationTrigger.IMMEDIATE_FAILURE
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying ${item.stream.name}: ${e.message}")
            GattNotificationTrigger.IMMEDIATE_FAILURE
        }
    }

    private fun characteristicFor(uuid: UUID): BluetoothGattCharacteristic? {
        return when (uuid) {
            EDA_CHARACTERISTIC_UUID -> edaCharacteristic
            PPG_CHARACTERISTIC_UUID -> ppgCharacteristic
            ACCEL_CHARACTERISTIC_UUID -> accelCharacteristic
            SKIN_TEMP_CHARACTERISTIC_UUID -> skinTempCharacteristic
            STATUS_CHARACTERISTIC_UUID -> statusCharacteristic
            HAPTIC_RECEIPT_CHARACTERISTIC_UUID -> hapticReceiptCharacteristic
            SOURCE_RECORD_CHARACTERISTIC_UUID -> sourceRecordCharacteristic
            else -> null
        }
    }

    private fun isNotificationEnabled(device: BluetoothDevice, characteristicUuid: UUID): Boolean {
        return synchronized(notificationSubscriptions) {
            notificationSubscriptions[device.address]?.contains(characteristicUuid) == true
        }
    }

    private fun batteryState(): Pair<Int, Boolean> {
        val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(if (level in 0..100) level else 255, charging)
    }

    private fun appVersionCode(): Int {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read package version code: ${e.message}")
            0
        }
    }

    private fun notifyDeviceHealth() {
        // A post-update final-delivery recovery must preserve the exact retained
        // terminal endpoint. Device-health is normally represented as a new source
        // record, so suppress it entirely until the existing manifest is exactly
        // acknowledged and the GATT service closes.
        if (finalizedDeliveryRecoveryOnly) return
        val occurredAtWatchMs = System.currentTimeMillis()
        val (batteryPercent, charging) = batteryState()
        var flags = 0
        if (charging) flags = flags or 0x01
        flags = flags or 0x02 // A connected device exists.
        if (healthSdkConnected) flags = flags or 0x04
        if (healthScreenOn) flags = flags or 0x08
        val transport = notificationQueue.snapshot()
        val packedTransportState =
            (transport.queueDepth.toLong() and 0xFFFFL) or
                ((activeSensorMask.toLong() and 0xFFFFL) shl 16) or
                ((healthSdkStatus.toLong() and 0xFFL) shl 32) or
                ((negotiatedMtu.toLong() and 0xFFFFL) shl 40)
        val packedTransportCounts =
            (transport.completedCount and 0x1F_FFFFL) or
                ((transport.failedCount and 0x1F_FFFFL) shl 21) or
                ((transport.timeoutCount and 0x1F_FFFFL) shl 42)
        try {
            CausalTransportSnapshotPlan.create(
                latestRecordIndex = sourceJournal.latestRecordIndex(),
                replayHighWaterRecordIndex = replayHighWaterRecordIndex,
                packedTransportState = packedTransportState,
                packedTransportCounts = packedTransportCounts,
                replayBacklogCount = replayBacklogCount,
            ).events.forEach { event ->
                recordCausal(
                    event.code,
                    recordIndexStart = event.recordIndexStart,
                    recordIndexEnd = event.recordIndexEnd,
                    arg0 = event.arg0,
                    arg1 = event.arg1,
                    reasonCode = event.reasonCode,
                )
            }
        } catch (failure: Exception) {
            WatchCausalRuntime.markFailure(this, failure)
        }
        val latestAnomaly = sourceJournal.latestAnomaly()
        val dataLoss = healthSourceDataLoss || !sourceJournal.preflight().eligible || latestAnomaly != null
        val dataLossStreamCode = healthSourceDataLossStreamCode.takeIf { it != 0 }
            ?: latestAnomaly?.stream?.wireCode
            ?: 0
        val dataLossFirstSequence = healthSourceDataLossFirstSequence.takeIf { it != 0L }
            ?: latestAnomaly?.firstAffectedSourceSequence
            ?: 0L
        val dataLossLastSequence = healthSourceDataLossLastSequence.takeIf { it != 0L }
            ?: latestAnomaly?.lastAffectedSourceSequence
            ?: dataLossFirstSequence
        val payload = SourcePayloadCodec.deviceHealth(
            batteryPercent = batteryPercent,
            flags = flags,
            activeSensorMask = activeSensorMask,
            sdkStatus = healthSdkStatus,
            buildVersionCode = appVersionCode(),
            totalSourceRecords = sourceJournal.latestRecordIndex(),
            flushCount = healthFlushCount,
            transportCompletedCount = transport.completedCount,
            transportFailedCount = transport.failedCount,
            transportTimeoutCount = transport.timeoutCount,
            transportCoalescedAccelCount = transport.coalescedAccelCount,
            negotiatedMtu = negotiatedMtu,
            replayBacklogCount = replayBacklogCount,
            dataLoss = dataLoss,
            dataLossStreamCode = dataLossStreamCode,
            dataLossFirstSequence = dataLossFirstSequence,
            dataLossLastSequence = dataLossLastSequence,
            dataLossReasonCode = healthSourceDataLossReasonCode,
        )
        try {
            val record = sourceJournal.append(SourceStreamCode.DEVICE_HEALTH, occurredAtWatchMs, payload)
            if (connectedDevice != null) queueLiveSourceRecord(record)
        } catch (error: Exception) {
            healthSourceDataLoss = true
            Log.e(TAG, "DATA LOSS: device-health journal append failed", error)
            broadcastStatus("DATA LOSS: device-health journal append failed")
        }
    }

    private fun notifyEda(
        conductance: Float,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = edaCharacteristic ?: return
        val sequence = nextSensorSequence("EDA")
        val buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        putSensorEnvelope(buffer, sequence, timestamp, deliveryMode, batchSize, screenOn)
        buffer.putFloat(conductance)
        if (transmitSensor(char, buffer.array(), GattNotificationStream.EDA, sequence, timestamp) && notifyCount++ % 50 == 0) {
            broadcastStatus("BLE TX v2: EDA=${String.format("%.3f", conductance)} seq=$sequence")
        }
    }

    private fun notifyPpg(
        ppgGreen: Int,
        ppgIR: Int,
        ppgRed: Int,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = ppgCharacteristic ?: return
        val sequence = nextSensorSequence("PPG")
        val buffer = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x01.toByte())
        putSensorEnvelope(buffer, sequence, timestamp, deliveryMode, batchSize, screenOn)
        buffer.putInt(ppgGreen)
        buffer.putInt(ppgIR)
        buffer.putInt(ppgRed)
        transmitSensor(char, buffer.array(), GattNotificationStream.PPG, sequence, timestamp)
    }

    private fun notifyCardiacEvidence(
        kind: Int,
        value: Int,
        status: Int,
        sourceTimestamp: Long,
        callbackId: Int,
        pointIndex: Int,
        pointCount: Int,
        listIndex: Int,
        listCount: Int,
        contractAnomaly: Boolean,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = ppgCharacteristic ?: return
        val sequence = nextSensorSequence("CARDIAC")
        val buffer = ByteBuffer.allocate(37).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x03.toByte())
        buffer.put(WATCHTOWER_V3_MARKER.toByte())
        buffer.put(WATCHTOWER_CARDIAC_EVIDENCE_VERSION.toByte())
        buffer.put(kind.coerceIn(0, 255).toByte())
        buffer.putInt(sequence.toInt())
        buffer.putLong(sourceTimestamp)
        buffer.putInt(status)
        buffer.putInt(value)
        buffer.putInt(callbackId)
        buffer.putShort(pointIndex.coerceIn(0, 65_535).toShort())
        buffer.putShort(pointCount.coerceIn(0, 65_535).toShort())
        buffer.putShort(if (listIndex < 0) 0xFFFF.toShort() else listIndex.coerceIn(0, 65_535).toShort())
        buffer.putShort(listCount.coerceIn(0, 65_535).toShort())
        var flags = deliveryFlags(deliveryMode, batchSize, screenOn).toInt() and 0xFF
        if (contractAnomaly) flags = flags or 0x08
        buffer.put(flags.toByte())
        transmitSensor(char, buffer.array(), GattNotificationStream.CARDIAC, sequence, sourceTimestamp)
    }

    private fun notifyHeartRateFallback(
        hr: Int,
        ibiMs: Int,
        hrStatus: Int,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = ppgCharacteristic ?: return
        val sequence = nextSensorSequence("CARDIAC")
        val buffer = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x02.toByte())
        putSensorEnvelope(buffer, sequence, timestamp, deliveryMode, batchSize, screenOn)
        buffer.putInt(hr)
        buffer.putInt(ibiMs)
        buffer.putInt(hrStatus)
        transmitSensor(char, buffer.array(), GattNotificationStream.CARDIAC, sequence, timestamp)
    }

    private fun notifySkinTemp(
        skinTemp: Float,
        ambientTemp: Float,
        status: Int,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = skinTempCharacteristic ?: return
        val sequence = nextSensorSequence("SKIN_TEMP")
        val buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        putSensorEnvelope(buffer, sequence, timestamp, deliveryMode, batchSize, screenOn)
        buffer.putFloat(skinTemp)
        buffer.putFloat(ambientTemp)
        buffer.putInt(status)
        transmitSensor(char, buffer.array(), GattNotificationStream.SKIN_TEMP, sequence, timestamp)
    }

    private fun notifyAccel(
        x: Int,
        y: Int,
        z: Int,
        timestamp: Long,
        deliveryMode: String,
        batchSize: Int,
        screenOn: Boolean
    ) {
        val char = accelCharacteristic ?: return
        val sequence = nextSensorSequence("ACCEL")
        val buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        putSensorEnvelope(buffer, sequence, timestamp, deliveryMode, batchSize, screenOn)
        buffer.putFloat(x.toFloat() / 1000f * 9.81f)  // milli-g to m/s²
        buffer.putFloat(y.toFloat() / 1000f * 9.81f)
        buffer.putFloat(z.toFloat() / 1000f * 9.81f)
        transmitSensor(char, buffer.array(), GattNotificationStream.ACCEL, sequence, timestamp)
    }

    // ─── Cleanup ────────────────────────────────────────────────────────────────

    private fun closeGattServer() {
        notificationQueue.reset()
        synchronized(notificationSubscriptions) { notificationSubscriptions.clear() }
        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT server: ${e.message}")
        }
        gattServer = null
    }

    private fun broadcastStatus(msg: String) {
        val intent = Intent(HealthSensorService.ACTION_STATUS_UPDATE).apply {
            putExtra("status", msg)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
