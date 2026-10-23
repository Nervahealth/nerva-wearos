package com.hugr.wearos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import java.io.File
import java.lang.reflect.Field
import java.util.Collections
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
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.util.ReflectionHelpers

/**
 * TEST ONLY: source-matched callback reproduction for the Watch receipt/ACK timeout review.
 *
 * This test invokes production [BleGattService]'s actual BluetoothGattServerCallback write/read
 * methods. The GATT server below is only a response observer: it neither substitutes receipt,
 * pager, session, journal, ACK, durability, nor callback production code. Its operation log has
 * no receipt page, source IDs, record values, hashes, addresses, or other payload/private data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [
        FreshRunDirectoryLinuxShadow::class,
        ReceiptAckTimeoutResponseObserverGattServerShadow::class,
    ],
)
class ReceiptAckTimeoutActualCallbackReproductionTest {
    private val permissionSet = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @Before fun before() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        ReceiptAckTimeoutResponseObserver.reset()
    }

    @After fun after() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        ReceiptAckTimeoutResponseObserver.reset()
    }

    @Test
    fun `actual BleGattService receipt callbacks ACK durability and response omission exposure are bounded and observable`() {
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
        ReceiptAckTimeoutResponseObserver.begin(logFile)
        val session = FreshRunRuntime.admit(activity)
        // TEST ONLY synthetic setup: begin the newly admitted session before service startup.
        // This enables one synthetic journal append/finalize; it does not alter production code.
        assertTrue("test setup begins newly admitted synthetic session", session.begin())
        val serviceIntent = Intent(activity, BleGattService::class.java).apply {
            action = FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES
        }
        var serviceController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        try {
            // TEST ONLY callback-local bootstrap. Calling startCommand would launch the full Health
            // service in this no-Samsung-SDK JVM harness and fail/terminate the admitted session before
            // the callback contract. Set only the callback prerequisites, then invoke actual production
            // connection, MTU, resume, ACK, read and write callback methods below.
            serviceController = Robolectric.buildService(BleGattService::class.java, serviceIntent).create()
            val service = serviceController.get()
            setField(service, "freshMode", true)
            setField(service, "sourceJournal", session.journal)
            val callback: BluetoothGattServerCallback = field(service, "gattServerCallback")
            val syntheticPhone = adapter.getRemoteDevice("00:11:22:33:44:55")
            callback.onConnectionStateChange(syntheticPhone, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            // TEST ONLY: model the negotiated ATT MTU required by the source protocol's 44-byte
            // receipt header. The preceding connection callback alone retains the source default 23.
            callback.onMtuChanged(syntheticPhone, 128)
            setField(service, "gattServer", ReflectionHelpers.newInstance(BluetoothGattServer::class.java))

            val receiptCharacteristic = BluetoothGattCharacteristic(
                BleGattService.RUN_RECEIPT_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
            val sourceControlCharacteristic = BluetoothGattCharacteristic(
                BleGattService.SOURCE_CONTROL_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )

            // Actual R1:0 write callback then actual read callback. The read must use the immutable
            // snapshot created by the production pager in the preceding write callback.
            val r10 = ReceiptAckTimeoutResponseObserver.enter("R1_ZERO_WRITE")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, r10, receiptCharacteristic, false, true, 0, "R1:0".toByteArray(Charsets.US_ASCII),
            )
            ReceiptAckTimeoutResponseObserver.assertOne(r10, BluetoothGatt.GATT_SUCCESS)
            val read0 = ReceiptAckTimeoutResponseObserver.enter("R1_ZERO_READ")
            callback.onCharacteristicReadRequest(syntheticPhone, read0, 0, receiptCharacteristic)
            ReceiptAckTimeoutResponseObserver.assertOne(read0, BluetoothGatt.GATT_SUCCESS)

            val r1Nonzero = ReceiptAckTimeoutResponseObserver.enter("R1_NONZERO_WRITE")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, r1Nonzero, receiptCharacteristic, false, true, 0, "R1:1".toByteArray(Charsets.US_ASCII),
            )
            ReceiptAckTimeoutResponseObserver.assertOne(r1Nonzero, BluetoothGatt.GATT_SUCCESS)
            val readNonzero = ReceiptAckTimeoutResponseObserver.enter("R1_NONZERO_READ")
            callback.onCharacteristicReadRequest(syntheticPhone, readNonzero, 0, receiptCharacteristic)
            ReceiptAckTimeoutResponseObserver.assertOne(readNonzero, BluetoothGatt.GATT_SUCCESS)

            // Form one synthetic source record through the admitted session. Actual production resume
            // below then seals the active segment and captures its manifest; no retained/device fixture.
            assertNotNull("admitted synthetic session accepts setup sample", session.append(
                SourceStreamCode.DEVICE_HEALTH, 1L, byteArrayOf(1, 2, 3),
            ))
            val expectedManifestEnd = session.journal.latestRecordIndex()

            // Actual source-control resume invokes production handleSourceResume and its asynchronous
            // prepare/apply lineage, including the source seal/capture that establishes ACK endpoint.
            val resume = ReceiptAckTimeoutResponseObserver.enter("SOURCE_RESUME_VALID")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, resume, sourceControlCharacteristic, false, true, 0,
                SourceReplayProtocol.encodeResumeRequest(
                    SourceResumeRequest(session.journal.watchBootSessionId, 0L),
                ),
            )
            ReceiptAckTimeoutResponseObserver.assertOne(resume, BluetoothGatt.GATT_SUCCESS)
            awaitProductionSourceResume(service, session.journal.watchBootSessionId, expectedManifestEnd)
            val manifest = session.snapshot().manifests.single()

            val acknowledgement = SourceSegmentAcknowledgement(
                session.journal.watchBootSessionId,
                manifest.lastRecordIndex,
                manifest.sha256Hex,
            )
            val ackBytes = SourceReplayProtocol.encodeSegmentAcknowledgement(acknowledgement)
            val ack = ReceiptAckTimeoutResponseObserver.enter("SOURCE_ACK_VALID")
            // This predicate executes in the fake server at the response attempt, proving the
            // source session/journal durable acceptance has already happened before ATT reply.
            ReceiptAckTimeoutResponseObserver.requireBeforeResponse(ack) {
                session.snapshot().acks.size == 1 &&
                    !session.journal.hasFinalizedSegments(session.journal.watchBootSessionId)
            }
            callback.onCharacteristicWriteRequest(
                syntheticPhone, ack, sourceControlCharacteristic, false, true, 0, ackBytes,
            )
            ReceiptAckTimeoutResponseObserver.assertOne(ack, BluetoothGatt.GATT_SUCCESS)
            assertEquals("real source ACK commits exactly once", 1, session.snapshot().acks.size)

            // Invalid source ACK is rejected by the production source-control callback and receives
            // exactly one failure response. It does not create a second durable ACK.
            val invalidAck = ReceiptAckTimeoutResponseObserver.enter("SOURCE_ACK_INVALID")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, invalidAck, sourceControlCharacteristic, false, true, 0, byteArrayOf(0, 0, 0),
            )
            ReceiptAckTimeoutResponseObserver.assertOne(invalidAck, BluetoothGatt.GATT_FAILURE)
            assertEquals(1, session.snapshot().acks.size)

            // Concurrent actual callback invocations: one immutable nonzero R1 page operation and
            // one rejected duplicate ACK. Each must have exactly one response and no extra ACK.
            val concurrentR1 = ReceiptAckTimeoutResponseObserver.enter("CONCURRENT_R1_NONZERO")
            val concurrentAck = ReceiptAckTimeoutResponseObserver.enter("CONCURRENT_ACK_DUPLICATE")
            val start = CountDownLatch(1)
            val complete = CountDownLatch(2)
            val t1 = Thread {
                start.await()
                callback.onCharacteristicWriteRequest(
                    syntheticPhone, concurrentR1, receiptCharacteristic, false, true, 0,
                    "R1:1".toByteArray(Charsets.US_ASCII),
                )
                complete.countDown()
            }
            val t2 = Thread {
                start.await()
                callback.onCharacteristicWriteRequest(
                    syntheticPhone, concurrentAck, sourceControlCharacteristic, false, true, 0, ackBytes,
                )
                complete.countDown()
            }
            t1.start(); t2.start(); start.countDown()
            assertTrue("bounded concurrent callback completion", complete.await(15, TimeUnit.SECONDS))
            ReceiptAckTimeoutResponseObserver.assertOne(concurrentR1, BluetoothGatt.GATT_SUCCESS)
            ReceiptAckTimeoutResponseObserver.assertOne(concurrentAck, BluetoothGatt.GATT_FAILURE)
            assertEquals(1, session.snapshot().acks.size)

            // Deliberate test-only lock contention is explicitly separate from natural timing. It
            // blocks the real R1:0 callback before production response; it is not historical cause.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val holder = Thread {
                synchronized(session) { entered.countDown(); release.await(15, TimeUnit.SECONDS) }
            }
            holder.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val stalledR1 = ReceiptAckTimeoutResponseObserver.enter("R1_ZERO_INJECTED_SESSION_LOCK_DELAY")
            val releaser = Thread { Thread.sleep(10_100L); release.countDown() }
            releaser.start()
            val elapsedStart = System.nanoTime()
            callback.onCharacteristicWriteRequest(
                syntheticPhone, stalledR1, receiptCharacteristic, false, true, 0, "R1:0".toByteArray(Charsets.US_ASCII),
            )
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - elapsedStart)
            ReceiptAckTimeoutResponseObserver.assertOne(stalledR1, BluetoothGatt.GATT_SUCCESS)
            assertTrue("deliberately injected lock delay crosses Phone policy only in this synthetic case: $elapsedMs ms", elapsedMs > 10_000L)
            holder.join(2_000L); releaser.join(2_000L)

            // A false return is observable to this test observer, but the production R1/source
            // branches ignore it. This is a source observability exposure, not an incident claim.
            val falseResponse = ReceiptAckTimeoutResponseObserver.enter("R1_FALSE_SEND_RESPONSE")
            ReceiptAckTimeoutResponseObserver.returnFalseFor(falseResponse)
            callback.onCharacteristicWriteRequest(
                syntheticPhone, falseResponse, receiptCharacteristic, false, true, 0, "R1:0".toByteArray(Charsets.US_ASCII),
            )
            ReceiptAckTimeoutResponseObserver.assertOne(falseResponse, BluetoothGatt.GATT_SUCCESS, expectedResult = false)

            // The nullable production server call is an explicit omission path. This invokes the
            // actual callback with no server; no fake response is fabricated and no durability rule
            // is relaxed. It cannot establish that the historical server was null.
            setField<BluetoothGattServer?>(service, "gattServer", null)
            val nullServer = ReceiptAckTimeoutResponseObserver.enter("R1_NULL_SERVER")
            callback.onCharacteristicWriteRequest(
                syntheticPhone, nullServer, receiptCharacteristic, false, true, 0, "R1:0".toByteArray(Charsets.US_ASCII),
            )
            assertFalse("null production server means no source-level response attempt", ReceiptAckTimeoutResponseObserver.hasResponse(nullServer))
        } finally {
            runCatching { serviceController?.destroy() }
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            controller.destroy()
        }
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

    /** TEST ONLY: waits for the actual source-resume executor → main-handler production lineage. */
    private fun awaitProductionSourceResume(service: BleGattService, sessionId: java.util.UUID, manifestEnd: Long) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        while (System.nanoTime() < deadlineNanos) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (
                field<java.util.UUID?>(service, "activeReplaySessionId") == sessionId &&
                field<Long?>(service, "queuedReplayManifestEndIndex") == manifestEnd
            ) return
            Thread.sleep(10L)
        }
        assertEquals(sessionId, field<java.util.UUID?>(service, "activeReplaySessionId"))
        assertEquals(manifestEnd, field<Long?>(service, "queuedReplayManifestEndIndex"))
    }
}

/** TEST ONLY payload-free event sink consumed by the report driver after the test. */
private object ReceiptAckTimeoutResponseObserver {
    data class Response(val ordinal: Int, val status: Int, val result: Boolean)
    private val responses = Collections.synchronizedList(mutableListOf<Response>())
    private val kinds = Collections.synchronizedMap(mutableMapOf<Int, String>())
    private val expected = Collections.synchronizedMap(mutableMapOf<Int, () -> Boolean>())
    private val falseResults = Collections.synchronizedSet(mutableSetOf<Int>())
    private var ordinal = 0
    private var log: File? = null

    fun reset() = synchronized(this) {
        responses.clear(); kinds.clear(); expected.clear(); falseResults.clear(); ordinal = 0; log = null
    }

    fun begin(file: File) = synchronized(this) {
        file.parentFile?.mkdirs(); file.writeText("")
        log = file
    }

    fun enter(kind: String): Int = synchronized(this) {
        val next = ++ordinal
        kinds[next] = kind
        emit(next, kind, "callback_entry")
        next
    }

    fun requireBeforeResponse(requestId: Int, predicate: () -> Boolean) { expected[requestId] = predicate }
    fun returnFalseFor(requestId: Int) { falseResults.add(requestId) }

    fun response(requestId: Int, status: Int): Boolean {
        val kind = kinds[requestId] ?: "UNATTRIBUTED"
        val durable = expected.remove(requestId)?.invoke()
        emit(requestId, kind, "pre_response" + if (durable == null) "" else " durable_before_response=$durable")
        assertTrue("required source ACK durability is complete before response", durable != false)
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

    private fun emit(ordinal: Int, kind: String, event: String) {
        synchronized(this) {
            log?.appendText("timestampNanos=${System.nanoTime()} operation_ordinal=$ordinal kind=$kind event=$event\n")
        }
    }
}

/** TEST ONLY response observer for the production callback's BluetoothGattServer.sendResponse call. */
@Implements(BluetoothGattServer::class)
class ReceiptAckTimeoutResponseObserverGattServerShadow {
    @Implementation
    fun sendResponse(
        device: BluetoothDevice?,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ): Boolean = ReceiptAckTimeoutResponseObserver.response(requestId, status)
}
