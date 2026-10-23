package com.hugr.wearos

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import java.io.File
import java.lang.reflect.Field
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothGattServer
import org.robolectric.shadows.ShadowBluetoothManager
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.util.ReflectionHelpers

/**
 * TEST ONLY — source-bound Watch continuation. It uses the actual production callback, source
 * prepare executor→main Handler apply, SourceMtuReadinessGate, CCCD callback, journal, receipt
 * transaction and ATT call. The synthetic server observes response calls only; it never supplies
 * readiness, endpoint, manifest, hash, receipt, ACK state, or durable acceptance.
 *
 * No Samsung SDK/service-start path, device, radio or retained source fixture is modeled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [
    FreshRunDirectoryLinuxShadow::class,
    ReceiptAckTimeoutContinuationBluetoothManagerShadow::class,
    ReceiptAckTimeoutContinuationResponseObserverGattServerShadow::class,
])
class ReceiptAckTimeoutActualCallbackContinuationTest {
    private val permissionSet = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @Before fun before() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        ReceiptAckTimeoutContinuationResponseObserver.reset()
        ReceiptAckTimeoutContinuationResponseObserverGattServerShadow.reset()
    }

    @After fun after() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        ReceiptAckTimeoutContinuationResponseObserver.reset()
        ReceiptAckTimeoutContinuationResponseObserverGattServerShadow.reset()
    }

    @Test
    fun `actual source CCCD resume ACK receipt contention and ATT outcome diagnostics are bounded`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 231023)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        val manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = requireNotNull(manager.adapter)
        Shadows.shadowOf(adapter).setEnabled(true)
        val logFile = File(requireNotNull(System.getProperty("hugr.repro.operationLog")))
        ReceiptAckTimeoutContinuationResponseObserver.begin(logFile)
        val session = FreshRunRuntime.admit(activity)
        assertTrue("test setup begins a newly admitted disposable synthetic session", session.begin())
        val serviceIntent = Intent(activity, BleGattService::class.java).apply {
            action = FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES
        }
        var serviceController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        try {
            // TEST ONLY callback-local bootstrap. It avoids startCommand's Samsung sensing path but
            // calls production initializeBluetooth so the production source characteristic exists.
            serviceController = Robolectric.buildService(BleGattService::class.java, serviceIntent).create()
            val service = serviceController.get()
            setField(service, "freshMode", true)
            setField(service, "sourceJournal", session.journal)
            invokeNoArg(service, "initializeBluetooth")
            val callback: BluetoothGattServerCallback = field(service, "gattServerCallback")
            val syntheticPhone = adapter.getRemoteDevice("00:11:22:33:44:55") // generated fixture only
            callback.onConnectionStateChange(syntheticPhone, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            // TEST ONLY: use the Phone production requestMTU (512), not receipt-only MTU 128.
            // Source release retains its independent minimum ATT payload requirement (364 bytes).
            callback.onMtuChanged(syntheticPhone, 512)

            val gattServer: BluetoothGattServer = requireNotNull(field(service, "gattServer"))
            val hugrService: BluetoothGattService = requireNotNull(gattServer.getService(BleGattService.HUGR_SERVICE_UUID))
            val receiptCharacteristic: BluetoothGattCharacteristic = requireNotNull(
                hugrService.getCharacteristic(BleGattService.RUN_RECEIPT_CHARACTERISTIC_UUID),
            )
            val sourceControlCharacteristic: BluetoothGattCharacteristic = requireNotNull(field(service, "sourceControlCharacteristic"))
            val sourceRecordCharacteristic: BluetoothGattCharacteristic = requireNotNull(field(service, "sourceRecordCharacteristic"))
            assertTrue("production initialization registered its real source control characteristic",
                hugrService.characteristics.contains(sourceControlCharacteristic))
            assertTrue("production initialization registered its real source record characteristic",
                hugrService.characteristics.contains(sourceRecordCharacteristic))
            // Natural source-release prerequisite: actual production CCCD callback on the actual
            // initialized source characteristic and CCCD. No readiness field or endpoint is assigned.
            val sourceCccd: BluetoothGattDescriptor = requireNotNull(
                sourceRecordCharacteristic.getDescriptor(BleGattService.CCCD_UUID),
            )
            assertTrue("production source CCCD remains attached to its production characteristic",
                sourceCccd.characteristic === sourceRecordCharacteristic)
            val cccdRequest = ReceiptAckTimeoutContinuationResponseObserver.enter("SOURCE_CCCD_ENABLE")
            callback.onDescriptorWriteRequest(
                syntheticPhone, cccdRequest, sourceCccd, false, true, 0,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            )
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(cccdRequest, BluetoothGatt.GATT_SUCCESS)
            val cccdReadiness = field<SourceMtuReadinessGate>(service, "sourceMtuReadinessGate").snapshot()
            assertTrue("actual connection callback established the readiness lineage", cccdReadiness.connected)
            assertTrue("actual MTU callback established source frame capacity", cccdReadiness.mtuCallbackReceived)
            assertTrue("test negotiation meets the unchanged production source payload minimum",
                cccdReadiness.attPayloadBytes >= SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL)
            assertTrue("actual production source CCCD callback enabled source release", cccdReadiness.sourceCccdEnabled)

            // Actual immutable R1 snapshot sequence under the negotiated MTU.
            val r10 = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_ZERO_WRITE")
            callback.onCharacteristicWriteRequest(syntheticPhone, r10, receiptCharacteristic, false, true, 0, ascii("R1:0"))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(r10, BluetoothGatt.GATT_SUCCESS)
            val read0 = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_ZERO_READ")
            callback.onCharacteristicReadRequest(syntheticPhone, read0, 0, receiptCharacteristic)
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(read0, BluetoothGatt.GATT_SUCCESS)
            val r1Nonzero = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_NONZERO_WRITE")
            callback.onCharacteristicWriteRequest(syntheticPhone, r1Nonzero, receiptCharacteristic, false, true, 0, ascii("R1:1"))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(r1Nonzero, BluetoothGatt.GATT_SUCCESS)
            val readNonzero = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_NONZERO_READ")
            callback.onCharacteristicReadRequest(syntheticPhone, readNonzero, 0, receiptCharacteristic)
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(readNonzero, BluetoothGatt.GATT_SUCCESS)

            // One production journal append only. Production resume force-syncs, seals, captures,
            // prepares and applies the manifest; the test neither supplies a hash nor an endpoint.
            assertNotNull("admitted session accepts one disposable setup sample", session.append(
                SourceStreamCode.DEVICE_HEALTH, 1L, byteArrayOf(1, 2, 3),
            ))
            val expectedManifestEnd = session.journal.latestRecordIndex()
            val resume = ReceiptAckTimeoutContinuationResponseObserver.enter("SOURCE_RESUME_VALID")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, resume, sourceControlCharacteristic, false, true, 0,
                SourceReplayProtocol.encodeResumeRequest(SourceResumeRequest(session.journal.watchBootSessionId, 0L)),
            )
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(resume, BluetoothGatt.GATT_SUCCESS)
            awaitProductionSourceResume(service, session.journal.watchBootSessionId, expectedManifestEnd)
            val manifest = session.snapshot().manifests.single()
            assertEquals("production-captured manifest endpoint", expectedManifestEnd, manifest.lastRecordIndex)

            val validAck = SourceSegmentAcknowledgement(session.journal.watchBootSessionId, manifest.lastRecordIndex, manifest.sha256Hex)
            val invalidHash = validAck.copy(completedSegmentSha256 = "00".repeat(32))
            val invalidSession = validAck.copy(watchBootSessionId = UUID.randomUUID())
            val invalidEndpoint = validAck.copy(cumulativeRecordIndex = manifest.lastRecordIndex + 1L)
            listOf(
                "SOURCE_ACK_INVALID_HASH" to invalidHash,
                "SOURCE_ACK_INVALID_SESSION" to invalidSession,
                "SOURCE_ACK_INVALID_ENDPOINT" to invalidEndpoint,
            ).forEach { (kind, acknowledgement) ->
                val request = ReceiptAckTimeoutContinuationResponseObserver.enter(kind)
                callback.onCharacteristicWriteRequest(syntheticPhone, request, sourceControlCharacteristic, false, true, 0,
                    SourceReplayProtocol.encodeSegmentAcknowledgement(acknowledgement))
                ReceiptAckTimeoutContinuationResponseObserver.assertOne(request, BluetoothGatt.GATT_FAILURE)
                assertEquals("invalid ACK must not create a durable receipt ACK", 0, session.snapshot().acks.size)
                assertTrue("invalid ACK must retain exact queued manifest", field<Long?>(service, "queuedReplayManifestEndIndex") == manifest.lastRecordIndex)
            }

            // Actual receipt/ACK overlap. The ACK is intentionally hash-invalid so a concurrent
            // test cannot alter the endpoint needed for the later exact durable acceptance case.
            val concurrentReceipt = ReceiptAckTimeoutContinuationResponseObserver.enter("OVERLAP_R1_NONZERO")
            val concurrentInvalidAck = ReceiptAckTimeoutContinuationResponseObserver.enter("OVERLAP_ACK_INVALID_HASH")
            val start = CountDownLatch(1)
            val completed = CountDownLatch(2)
            val receiptThread = Thread {
                start.await()
                callback.onCharacteristicWriteRequest(syntheticPhone, concurrentReceipt, receiptCharacteristic, false, true, 0, ascii("R1:1"))
                completed.countDown()
            }
            val ackThread = Thread {
                start.await()
                callback.onCharacteristicWriteRequest(syntheticPhone, concurrentInvalidAck, sourceControlCharacteristic, false, true, 0,
                    SourceReplayProtocol.encodeSegmentAcknowledgement(invalidHash))
                completed.countDown()
            }
            receiptThread.start(); ackThread.start(); start.countDown()
            assertTrue("bounded receipt/ACK overlap completion", completed.await(15, TimeUnit.SECONDS))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(concurrentReceipt, BluetoothGatt.GATT_SUCCESS)
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(concurrentInvalidAck, BluetoothGatt.GATT_FAILURE)
            assertEquals("overlap invalid ACK retains zero durable ACKs", 0, session.snapshot().acks.size)

            // Exact ACK is real callback → real receipt pending gate/journal removal/receipt update.
            val ack = ReceiptAckTimeoutContinuationResponseObserver.enter("SOURCE_ACK_VALID")
            ReceiptAckTimeoutContinuationResponseObserver.requireBeforeResponse(ack) {
                val inMemory = FreshRunRuntime.current(activity)
                val reopened = FreshRunReceiptStore(receiptRoot).load(session.runId)
                val exactInMemory = inMemory?.acks?.singleOrNull()?.let { it.last == manifest.lastRecordIndex && it.hash == manifest.sha256Hex } == true
                val exactReopened = reopened.acks.singleOrNull()?.let { it.last == manifest.lastRecordIndex && it.hash == manifest.sha256Hex } == true
                exactInMemory && exactReopened && !session.journal.hasFinalizedSegments(session.journal.watchBootSessionId)
            }
            callback.onCharacteristicWriteRequest(syntheticPhone, ack, sourceControlCharacteristic, false, true, 0,
                SourceReplayProtocol.encodeSegmentAcknowledgement(validAck))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(ack, BluetoothGatt.GATT_SUCCESS)
            assertEquals("exact source ACK creates one durable receipt ACK", 1, session.snapshot().acks.size)
            assertFalse("exact ACK removes only the accepted manifest", session.journal.hasFinalizedSegments(session.journal.watchBootSessionId))

            // A duplicate exact ACK is rejected by the actual current contract after removal; no
            // fabricated expected failure is used if that contract changes.
            val duplicate = ReceiptAckTimeoutContinuationResponseObserver.enter("SOURCE_ACK_DUPLICATE_POST_ACCEPT")
            callback.onCharacteristicWriteRequest(syntheticPhone, duplicate, sourceControlCharacteristic, false, true, 0,
                SourceReplayProtocol.encodeSegmentAcknowledgement(validAck))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(duplicate, BluetoothGatt.GATT_FAILURE)
            assertEquals("duplicate cannot create another ACK", 1, session.snapshot().acks.size)

            // Deliberately injected session lock contention. This is not a device/radio cause and
            // changes no timeout: it demonstrates the real synchronous R1 request can outlast 10s.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val holder = Thread { synchronized(session) { entered.countDown(); release.await(15, TimeUnit.SECONDS) } }
            holder.start(); assertTrue("session lock holder entered", entered.await(5, TimeUnit.SECONDS))
            val stalled = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_ZERO_INJECTED_SESSION_LOCK_DELAY")
            val releaser = Thread { Thread.sleep(10_100L); release.countDown() }
            releaser.start()
            val delayStart = System.nanoTime()
            callback.onCharacteristicWriteRequest(syntheticPhone, stalled, receiptCharacteristic, false, true, 0, ascii("R1:0"))
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - delayStart)
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(stalled, BluetoothGatt.GATT_SUCCESS)
            assertTrue("injected session lock crosses the unchanged 10s Phone operation policy only in this synthetic case: $elapsedMs ms", elapsedMs > 10_000L)
            holder.join(2_000L); releaser.join(2_000L)

            // Demonstrated source observability correction: false/null response results are recorded
            // after, never before, the actual response attempt. No retry or ACK rollback follows.
            val falseResponse = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_FALSE_SEND_RESPONSE")
            ReceiptAckTimeoutContinuationResponseObserver.returnFalseFor(falseResponse)
            callback.onCharacteristicWriteRequest(syntheticPhone, falseResponse, receiptCharacteristic, false, true, 0, ascii("R1:0"))
            ReceiptAckTimeoutContinuationResponseObserver.assertOne(falseResponse, BluetoothGatt.GATT_SUCCESS, expectedResult = false)
            setField<BluetoothGattServer?>(service, "gattServer", null)
            val nullServer = ReceiptAckTimeoutContinuationResponseObserver.enter("R1_NULL_SERVER")
            callback.onCharacteristicWriteRequest(syntheticPhone, nullServer, receiptCharacteristic, false, true, 0, ascii("R1:1"))
            assertFalse("null server means no fabricated source-level ATT attempt", ReceiptAckTimeoutContinuationResponseObserver.hasResponse(nullServer))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val attReports = WatchDiagnosticRuntime.store(service).reports().map { WatchDiagnosticRuntime.store(service).read(it) }
                .filter { it.startsWith("HUGR SAVED ATT RESPONSE DIAGNOSTIC v1") }
            assertEquals("late accepted, false and null outcomes each persist one bounded post-response observation", 3, attReports.size)
            assertTrue(attReports.any { it.contains("responseOutcome=TRUE") && it.contains("lateForPhoneOperation=true") &&
                it.contains("clock=PROCESS_MONOTONIC_NANOTIME") })
            assertTrue(attReports.any { it.contains("operation=RUN_RECEIPT access=WRITE pageIndex=0") && it.contains("responseOutcome=FALSE") })
            assertTrue(attReports.any { it.contains("operation=RUN_RECEIPT access=WRITE pageIndex=1") && it.contains("responseOutcome=NULL") })
            attReports.forEach { report ->
                assertFalse("ATT diagnostic excludes fixture address", report.contains("00:11:22:33:44:55"))
                assertFalse("ATT diagnostic excludes exact manifest hash", report.contains(manifest.sha256Hex))
                assertTrue("ATT diagnostic declares operation uncertainty boundary", report.contains("NOT delivery, radio, Phone-ACK"))
            }
        } finally {
            runCatching { serviceController?.destroy() }
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            controller.destroy()
        }
    }

    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)

    private fun awaitProductionSourceResume(service: BleGattService, sessionId: UUID, manifestEnd: Long) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L) // unchanged fixed readiness wait
        while (System.nanoTime() < deadlineNanos) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (field<UUID?>(service, "activeReplaySessionId") == sessionId &&
                field<Long?>(service, "queuedReplayManifestEndIndex") == manifestEnd) return
            Thread.sleep(10L)
        }
        val readiness = field<SourceMtuReadinessGate>(service, "sourceMtuReadinessGate").snapshot()
        assertEquals("production apply chose actual source session", sessionId, field<UUID?>(service, "activeReplaySessionId"))
        assertTrue("actual source prepare/apply completed before the bounded wait", readiness.preparedResumePlan != null)
        assertTrue(
            "actual source gate at bounded wait connected=${readiness.connected} mtuCallback=${readiness.mtuCallbackReceived} " +
                "attPayload=${readiness.attPayloadBytes} sourceCccd=${readiness.sourceCccdEnabled} released=${readiness.released}",
            readiness.connected && readiness.mtuCallbackReceived && readiness.sourceCccdEnabled && readiness.released)
        assertEquals("production source queue pump queued actual manifest endpoint", manifestEnd,
            field<Long?>(service, "queuedReplayManifestEndIndex"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun <T> setField(target: Any, name: String, value: T) {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invokeNoArg(target: Any, name: String) {
        val method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(target)
    }
}

/**
 * TEST ONLY. Robolectric's API-34 BluetoothManager route leaves the two-argument production
 * openGattServer call null. Return a normally constructed server solely so production
 * openGattServer creates/registers its real characteristics and CCCD; inherited server shadow
 * owns addService/getService. This supplies neither source readiness nor any receipt/ACK state.
 */
@Implements(BluetoothManager::class)
class ReceiptAckTimeoutContinuationBluetoothManagerShadow : ShadowBluetoothManager() {
    @Implementation
    fun openGattServer(context: Context, callback: BluetoothGattServerCallback): BluetoothGattServer {
        val server = ReflectionHelpers.newInstance(BluetoothGattServer::class.java)
        Shadows.shadowOf(server).setGattServerCallback(callback)
        return server
    }
}

/** TEST ONLY payload-free ATT response observer. */
private object ReceiptAckTimeoutContinuationResponseObserver {
    data class Response(val ordinal: Int, val status: Int, val result: Boolean)
    private val responses = Collections.synchronizedList(mutableListOf<Response>())
    private val kinds = Collections.synchronizedMap(mutableMapOf<Int, String>())
    private val expected = Collections.synchronizedMap(mutableMapOf<Int, () -> Boolean>())
    private val falseResults = Collections.synchronizedSet(mutableSetOf<Int>())
    private var ordinal = 0
    private var log: File? = null

    fun reset() = synchronized(this) { responses.clear(); kinds.clear(); expected.clear(); falseResults.clear(); ordinal = 0; log = null }
    fun begin(file: File) = synchronized(this) { file.parentFile?.mkdirs(); file.writeText(""); log = file }
    fun enter(kind: String): Int = synchronized(this) { (++ordinal).also { kinds[it] = kind; emit(it, kind, "callback_entry") } }
    fun requireBeforeResponse(requestId: Int, predicate: () -> Boolean) { expected[requestId] = predicate }
    fun returnFalseFor(requestId: Int) { falseResults.add(requestId) }
    fun response(requestId: Int, status: Int): Boolean {
        val kind = kinds[requestId] ?: "UNATTRIBUTED"
        val durable = expected.remove(requestId)?.invoke()
        emit(requestId, kind, "pre_response" + if (durable == null) "" else " durable_before_response=$durable")
        assertTrue("required exact source ACK was durable and reopened before ATT response", durable != false)
        val result = !falseResults.contains(requestId)
        responses += Response(requestId, status, result)
        emit(requestId, kind, "response_attempt result=$result status=$status")
        return result
    }
    fun assertOne(requestId: Int, status: Int, expectedResult: Boolean = true) {
        val matching = responses.filter { it.ordinal == requestId }
        assertEquals("exactly one ATT response attempt", 1, matching.size)
        assertEquals(status, matching.single().status)
        assertEquals(expectedResult, matching.single().result)
    }
    fun hasResponse(requestId: Int): Boolean = responses.any { it.ordinal == requestId }
    private fun emit(ordinal: Int, kind: String, event: String) = synchronized(this) {
        log?.appendText("timestampNanos=${System.nanoTime()} operation_ordinal=$ordinal kind=$kind event=$event\n")
    }
}

/** TEST ONLY: observes production BluetoothGattServer.sendResponse return values. */
@Implements(BluetoothGattServer::class)
class ReceiptAckTimeoutContinuationResponseObserverGattServerShadow : ShadowBluetoothGattServer() {
    companion object { fun reset() = Unit }
    /**
     * The production queue advances only after a platform notification trigger. This is transport
     * acceptance at the Android API seam, not receipt delivery or an ACK. The test invokes the
     * actual onNotificationSent callback separately where a completed queue item is required.
     */
    @Implementation
    fun notifyCharacteristicChanged(
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
        confirm: Boolean,
        value: ByteArray?,
    ): Int = BluetoothStatusCodes.SUCCESS

    @Implementation override fun sendResponse(device: BluetoothDevice?, requestId: Int, status: Int, offset: Int, value: ByteArray?): Boolean =
        ReceiptAckTimeoutContinuationResponseObserver.response(requestId, status)
}
