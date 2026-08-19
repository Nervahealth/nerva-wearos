package com.hugr.wearos

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * BleGattService — BLE GATT Peripheral Server for HUGR Watch (Build 35w)
 *
 * This service:
 * 1. Opens a BluetoothGattServer with a custom HUGR service
 * 2. Registers characteristics for EDA, PPG/Cardiac, Accel, SkinTemp (NOTIFY) and Haptic (WRITE)
 * 3. Advertises the HUGR service UUID so the phone can discover it
 * 4. Listens for sensor data broadcasts from HealthSensorService
 * 5. Pushes sensor data to connected phone via GATT notifications
 * 6. Forwards haptic write commands to HapticService via broadcast
 *
 * BLE DATA CONTRACT (Build 35w):
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
    private var edaCharacteristic: BluetoothGattCharacteristic? = null
    private var ppgCharacteristic: BluetoothGattCharacteristic? = null
    private var accelCharacteristic: BluetoothGattCharacteristic? = null
    private var skinTempCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null

    companion object {
        val HUGR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val EDA_CHARACTERISTIC_UUID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val PPG_CHARACTERISTIC_UUID: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val ACCEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val SKIN_TEMP_CHARACTERISTIC_UUID: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val HAPTIC_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
        val STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")

        // Client Characteristic Configuration Descriptor (required for NOTIFY)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val ACTION_HAPTIC_COMMAND = "com.hugr.wearos.HAPTIC_COMMAND"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleGattService = this@BleGattService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BleGattService created")
        initializeBluetooth()
        registerSensorReceivers()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BleGattService destroyed")
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
                // Forward to HapticService via broadcast
                val intent = Intent(ACTION_HAPTIC_COMMAND).apply {
                    putExtra("pattern", value)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
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
            notifyEda(conductance, timestamp)
        }
    }

    private val ppgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ppgGreen = intent?.getIntExtra("ppgGreen", 0) ?: return
            val ppgIR = intent.getIntExtra("ppgIR", 0)
            val ppgRed = intent.getIntExtra("ppgRed", 0)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val deliveryMode = intent.getStringExtra("deliveryMode") ?: "REALTIME"
            if (deliveryMode == "FALLBACK") {
                // HR+IBI fallback — format byte 0x02
                notifyHeartRateFallback(ppgGreen, ppgIR, ppgRed, timestamp)
            } else {
                // Raw PPG — format byte 0x01
                notifyPpg(ppgGreen, ppgIR, ppgRed, timestamp)
            }
        }
    }

    private val skinTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val skinTemp = intent?.getFloatExtra("skinTemp", 0f) ?: return
            val ambientTemp = intent.getFloatExtra("ambientTemp", 0f)
            val status = intent.getIntExtra("status", -1)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifySkinTemp(skinTemp, ambientTemp, status, timestamp)
        }
    }

    private val accelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getIntExtra("x", 0) ?: return
            val y = intent.getIntExtra("y", 0)
            val z = intent.getIntExtra("z", 0)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            notifyAccel(x, y, z, timestamp)
        }
    }

    private fun registerSensorReceivers() {
        registerReceiver(edaReceiver, IntentFilter("com.hugr.wearos.EDA_DATA"), RECEIVER_EXPORTED)
        registerReceiver(ppgReceiver, IntentFilter("com.hugr.wearos.PPG_DATA"), RECEIVER_EXPORTED)
        registerReceiver(accelReceiver, IntentFilter("com.hugr.wearos.ACCEL_DATA"), RECEIVER_EXPORTED)
        registerReceiver(skinTempReceiver, IntentFilter("com.hugr.wearos.TEMP_DATA"), RECEIVER_EXPORTED)
        Log.d(TAG, "Sensor broadcast receivers registered")
    }

    private fun unregisterSensorReceivers() {
        try {
            unregisterReceiver(edaReceiver)
            unregisterReceiver(ppgReceiver)
            unregisterReceiver(accelReceiver)
            unregisterReceiver(skinTempReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers: ${e.message}")
        }
    }

    // ─── Notification Methods (push data to connected phone) ────────────────────

    private fun notifyEda(conductance: Float, timestamp: Long) {
        val char = edaCharacteristic ?: return
        val device = connectedDevice
        if (device == null) {
            if (notifyCount++ % 100 == 0) Log.w(TAG, "notifyEda: NO connected device (dropped)")
            return
        }

        // Pack: [conductance (4 bytes float32, little-endian)]
        // Phone's parseEDA() expects exactly 4 bytes: DataView.getFloat32(0, true)
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(conductance)
        char.value = buffer.array()

        try {
            gattServer?.notifyCharacteristicChanged(device, char, false)
            if (notifyCount++ % 50 == 0) {
                broadcastStatus("BLE TX: EDA=${String.format("%.3f", conductance)} → ${device.address?.takeLast(5)}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying EDA: ${e.message}")
        }
    }

    private fun notifyPpg(ppgGreen: Int, ppgIR: Int, ppgRed: Int, timestamp: Long) {
        val char = ppgCharacteristic ?: return
        val device = connectedDevice
        if (device == null) {
            return
        }
        // Pack: [format (1 byte)] [d0 (4 bytes int32)] [d1 (4 bytes int32)] [d2 (4 bytes int32)] = 13 bytes
        // format=0x01: raw PPG (d0=Green, d1=IR, d2=Red)
        // format=0x02: HR+IBI fallback (d0=HR, d1=IBI_ms, d2=hrStatus)
        // Determine format from delivery mode
        val deliveryMode = "RAW_PPG" // HealthSensorService sets deliveryMode in intent
        val formatByte: Byte = 0x01 // Raw PPG
        val buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(formatByte)
        buffer.putInt(ppgGreen)
        buffer.putInt(ppgIR)
        buffer.putInt(ppgRed)
        char.value = buffer.array()
        try {
            gattServer?.notifyCharacteristicChanged(device, char, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying PPG: ${e.message}")
        }
    }

    private fun notifyHeartRateFallback(hr: Int, ibiMs: Int, hrStatus: Int, timestamp: Long) {
        val char = ppgCharacteristic ?: return
        val device = connectedDevice
        if (device == null) {
            return
        }
        // Same characteristic, different format byte
        val formatByte: Byte = 0x02 // HR+IBI fallback
        val buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(formatByte)
        buffer.putInt(hr)
        buffer.putInt(ibiMs)
        buffer.putInt(hrStatus)
        char.value = buffer.array()
        try {
            gattServer?.notifyCharacteristicChanged(device, char, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying HR fallback: ${e.message}")
        }
    }

    private fun notifySkinTemp(skinTemp: Float, ambientTemp: Float, status: Int, timestamp: Long) {
        val char = skinTempCharacteristic ?: return
        val device = connectedDevice
        if (device == null) {
            return
        }
        // Pack: [skinTemp:float32][ambientTemp:float32][status:int32] = 12 bytes
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(skinTemp)
        buffer.putFloat(ambientTemp)
        buffer.putInt(status)
        char.value = buffer.array()
        try {
            gattServer?.notifyCharacteristicChanged(device, char, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying SkinTemp: ${e.message}")
        }
    }

    private fun notifyAccel(x: Int, y: Int, z: Int, timestamp: Long) {
        val char = accelCharacteristic ?: return
        val device = connectedDevice
        if (device == null) {
            return
        }

        // Pack: [x (4 bytes float32)] [y (4 bytes float32)] [z (4 bytes float32)]
        // Phone's parseAccel() expects exactly 12 bytes: 3x getFloat32()
        // Convert from Samsung SDK raw int (milli-g) to m/s²
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(x.toFloat() / 1000f * 9.81f)  // milli-g to m/s²
        buffer.putFloat(y.toFloat() / 1000f * 9.81f)
        buffer.putFloat(z.toFloat() / 1000f * 9.81f)
        char.value = buffer.array()

        try {
            gattServer?.notifyCharacteristicChanged(device, char, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException notifying Accel: ${e.message}")
        }
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
