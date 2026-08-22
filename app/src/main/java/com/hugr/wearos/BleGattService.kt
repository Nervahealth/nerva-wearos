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
import android.os.Binder
import android.os.BatteryManager
import android.os.IBinder
import android.os.ParcelUuid
import android.os.Build
import android.os.Handler
import android.os.Looper
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

/**
 * BleGattService — BLE GATT Peripheral Server for HUGR Watchtower v2.
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
 * BLE DATA CONTRACT (Build 42w Watchtower candidate):
 * - EDA (UUID 11111111): [conductance:float32] = 4 bytes
 * - PPG/Cardiac (UUID 44444444): [format:uint8][d0:int32][d1:int32][d2:int32] = 13 bytes
 *     format=0x01: raw PPG → d0=Green, d1=IR, d2=Red
 *     format=0x02: HR+IBI fallback → d0=HR, d1=IBI_ms, d2=hrStatus
 * - Accel (UUID 33333333): [x:float32][y:float32][z:float32] = 12 bytes
 * - SkinTemp (UUID 55555555): [skinTemp:float32][ambientTemp:float32][status:int32] = 12 bytes
 * - Haptic (UUID 0000fff5): write-only, variable length
 */
class BleGattService : Service() {

    private val TAG = "HUGR-BleGatt"
    private val binder = LocalBinder()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var connectedDevice: BluetoothDevice? = null
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()
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
    private val healthHandler = Handler(Looper.getMainLooper())
    private val healthTicker = object : Runnable {
        override fun run() {
            notifyDeviceHealth()
            healthHandler.postDelayed(this, DEVICE_HEALTH_INTERVAL_MS)
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

        // Client Characteristic Configuration Descriptor (required for NOTIFY)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val ACTION_HAPTIC_COMMAND = "com.hugr.wearos.HAPTIC_COMMAND"
        private const val WATCHTOWER_V2_MARKER = 0xA2
        private const val WATCHTOWER_TELEMETRY_VERSION = 2
        private const val HAPTIC_POLICY_VERSION = 1
        private const val HAPTIC_CHANNEL_ID = "hugr_research_haptic_v1"
        private const val HAPTIC_CHANNEL_NAME = "HUGR research haptics"
        private const val DETAIL_OK = 0
        private const val DETAIL_DUPLICATE_REPLAY = 10
        private const val DETAIL_UNSUPPORTED_POLICY = 11
        private const val DETAIL_NOTIFICATIONS_DISABLED = 12
        private const val DETAIL_PERMISSION_MISSING = 13
        private const val DETAIL_CHANNEL_DISABLED = 14
        private const val DETAIL_NOTIFY_EXCEPTION = 15
        private const val DETAIL_STOP_ACCEPTED = 16
        private const val DEVICE_HEALTH_INTERVAL_MS = 30_000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleGattService = this@BleGattService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BleGattService created (Build 42w Watchtower candidate)")
        initializeVibrator()
        initializeHapticNotificationChannel()
        initializeBluetooth()
        registerSensorReceivers()
        healthHandler.post(healthTicker)
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
        super.onDestroy()
        Log.d(TAG, "BleGattService destroyed (Build 42w Watchtower candidate)")
        vibrator?.cancel()
        healthHandler.removeCallbacks(healthTicker)
        unregisterSensorReceivers()
        stopAdvertising()
        closeGattServer()
    }

    // ─── Initialization ─────────────────────────────────────────────────────────

    private fun initializeBluetooth() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                Log.e(TAG, "Bluetooth not available or disabled")
                return
            }

            advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e(TAG, "BLE advertiser not available")
                return
            }

            openGattServer(bluetoothManager)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth: ${e.message}", e)
        }
    }

    // ─── GATT Server Setup ──────────────────────────────────────────────────────

    private fun openGattServer(bluetoothManager: BluetoothManager) {
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            return
        }

        // Create the HUGR service
        val service = BluetoothGattService(
            HUGR_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

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

        // Haptic Command Characteristic (WRITE)
        val hapticCharacteristic = BluetoothGattCharacteristic(
            HAPTIC_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(hapticCharacteristic)

        // Add service to GATT server
        val added = gattServer!!.addService(service)
        if (added) {
            Log.i(TAG, "HUGR GATT service registered with ${service.characteristics.size} characteristics")
        } else {
            Log.e(TAG, "Failed to add HUGR service to GATT server")
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
                    connectedDevice = device
                    if (device != null) subscribedDevices.add(device)
                    Log.i(TAG, "Phone connected: ${device?.address}")
                    broadcastStatus("BLE: Phone CONNECTED (${device?.address})")
                    notifyDeviceHealth()
                    // Start advertising after connection? No — stop advertising to save power
                    // KEEP ADVERTISING for reconnection
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    if (device != null) subscribedDevices.remove(device)
                    Log.i(TAG, "Phone disconnected: ${device?.address}")
                    broadcastStatus("BLE: Phone DISCONNECTED")
                    // Resume advertising so phone can reconnect
                    startAdvertising()
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Service added successfully — starting advertising")
                startAdvertising()
            } else {
                Log.e(TAG, "Failed to add service, status: $status")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d(TAG, "Read request for ${characteristic?.uuid}")
            val value = characteristic?.value ?: byteArrayOf(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            if (descriptor?.uuid == CCCD_UUID) {
                // Return notifications enabled
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
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
                descriptor.value = value
                val charUuid = descriptor.characteristic?.uuid
                val enabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
                Log.i(TAG, "Notifications ${if (enabled) "ENABLED" else "DISABLED"} for $charUuid")
                broadcastStatus("BLE: Notifications ${if (enabled) "ON" else "OFF"} for ${charUuid.toString().substring(0, 8)}")
                // If client subscribes, make sure we know about them
                if (enabled && device != null) {
                    connectedDevice = device
                    subscribedDevices.add(device)
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    // ─── BLE Advertising ────────────────────────────────────────────────────────

    private fun startAdvertising() {
        val adv = advertiser ?: return

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
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}", e)
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE advertising started — UUID visible to phone")
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
            notifyDeviceHealth()
        }
    }

    private fun registerSensorReceivers() {
        registerReceiver(edaReceiver, IntentFilter("com.hugr.wearos.EDA_DATA"), RECEIVER_EXPORTED)
        registerReceiver(ppgReceiver, IntentFilter("com.hugr.wearos.PPG_DATA"), RECEIVER_EXPORTED)
        registerReceiver(accelReceiver, IntentFilter("com.hugr.wearos.ACCEL_DATA"), RECEIVER_EXPORTED)
        registerReceiver(skinTempReceiver, IntentFilter("com.hugr.wearos.TEMP_DATA"), RECEIVER_EXPORTED)
        registerReceiver(hrReceiver, IntentFilter("com.hugr.wearos.HR_DATA"), Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(healthMetadataReceiver, IntentFilter(HealthSensorService.ACTION_DEVICE_HEALTH_UPDATE), Context.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Sensor broadcast receivers registered")
    }

    private fun unregisterSensorReceivers() {
        try {
            unregisterReceiver(edaReceiver)
            unregisterReceiver(ppgReceiver)
            unregisterReceiver(accelReceiver)
            unregisterReceiver(skinTempReceiver)
            unregisterReceiver(hrReceiver)
            unregisterReceiver(healthMetadataReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers: ${e.message}")
        }
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
        val device = connectedDevice ?: return
        val buffer = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(WATCHTOWER_V2_MARKER.toByte())
        buffer.putInt(commandSequence.toInt())
        buffer.putLong(System.currentTimeMillis())
        buffer.put(status.toByte())
        buffer.put(patternId.coerceIn(0, 255).toByte())
        buffer.put(detailCode.coerceIn(0, 255).toByte())
        buffer.put(policyVersion.coerceIn(0, 255).toByte())
        characteristic.value = buffer.array()
        try {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying haptic acknowledgement: ${e.message}")
        }
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

    private fun transmitSensor(characteristic: BluetoothGattCharacteristic, payload: ByteArray, stream: String): Boolean {
        characteristic.value = payload
        val device = connectedDevice
        if (device == null) {
            droppedNoConnectionCount = (droppedNoConnectionCount + 1) and 0xFFFF_FFFFL
            if (droppedNoConnectionCount % 100L == 1L) Log.w(TAG, "$stream packet unavailable to phone: no connected device")
            return false
        }
        return try {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false) == true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying $stream: ${e.message}")
            false
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
        val characteristic = statusCharacteristic ?: return
        val device = connectedDevice ?: return
        healthSequence = (healthSequence + 1) and 0xFFFF_FFFFL
        val (batteryPercent, charging) = batteryState()
        var flags = 0
        if (charging) flags = flags or 0x01
        flags = flags or 0x02 // A connected device exists.
        if (healthSdkConnected) flags = flags or 0x04
        if (healthScreenOn) flags = flags or 0x08
        val buffer = ByteBuffer.allocate(35).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(WATCHTOWER_V2_MARKER.toByte())
        buffer.putInt(healthSequence.toInt())
        buffer.putLong(System.currentTimeMillis())
        buffer.put(batteryPercent.toByte())
        buffer.put(flags.toByte())
        buffer.put(activeSensorMask.toByte())
        buffer.put(healthSdkStatus.toByte())
        buffer.putInt(appVersionCode())
        buffer.putInt(totalSensorPackets.toInt())
        buffer.putInt(healthFlushCount.toInt())
        buffer.putInt(droppedNoConnectionCount.toInt())
        buffer.put(WATCHTOWER_TELEMETRY_VERSION.toByte())
        buffer.put(HAPTIC_POLICY_VERSION.toByte())
        characteristic.value = buffer.array()
        try {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying device health: ${e.message}")
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
        if (transmitSensor(char, buffer.array(), "EDA") && notifyCount++ % 50 == 0) {
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
        transmitSensor(char, buffer.array(), "PPG")
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
        transmitSensor(char, buffer.array(), "CARDIAC")
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
        transmitSensor(char, buffer.array(), "SKIN_TEMP")
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
        transmitSensor(char, buffer.array(), "ACCEL")
    }

    // ─── Cleanup ────────────────────────────────────────────────────────────────

    private fun closeGattServer() {
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
