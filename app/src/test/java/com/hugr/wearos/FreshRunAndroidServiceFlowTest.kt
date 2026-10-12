package com.hugr.wearos

import android.Manifest
import android.app.Notification
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.samsung.android.service.health.tracking.FreshRunAndroidServiceFlowTestOnlySamsungSdk
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowService
import org.robolectric.shadows.ShadowStatFs

/**
 * Watch79 Android-facing service-flow regression.
 *
 * These are synthetic Robolectric/Linux tests. They execute production MainActivity,
 * BleGattService and HealthSensorService methods and use real disposable SourceJournal bytes.
 * Test-only substitutes are named below: an enabled in-memory Bluetooth adapter/GATT server and
 * an explicit TEST-CLASSPATH Samsung SDK substitute. The latter supplies only declared test
 * capabilities and callbacks requested by a test; it neither validates the vendor AAR nor
 * represents Samsung hardware. These tests cannot establish physical foreground-service
 * admission, Samsung tracker availability, Bluetooth radio/Phone delivery, Watch process
 * survival, power-loss durability, or a physical Watch79 failure cause.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    // The explicit TEST-CLASSPATH fake Samsung SDK is deliberately not instrumented. The vendor
    // AAR's HealthTracker bytecode has an ASM frame defect under Robolectric; HealthSensorService
    // itself still executes production onStart/timer/callback/finalization code below.
    shadows = [FreshRunDirectoryLinuxShadow::class],
)
class FreshRunAndroidServiceFlowTest {
    private val permissionSet = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.BODY_SENSORS_BACKGROUND,
        "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA",
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @Before
    fun before() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        FreshRunAndroidServiceFlowTestOnlySamsungSdk.reset()
    }

    @After
    fun after() {
        FreshRunRuntime.end()
        WatchSourceRuntime.resetForTests()
        FreshRunAndroidServiceFlowTestOnlySamsungSdk.reset()
    }

    @Test
    fun `supported TEST ONLY SDK callbacks reach actual production canonical append and controlled finalization after Activity destruction`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7901)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        enableSyntheticBluetooth(activity)

        var bleController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        var healthController: org.robolectric.android.controller.ServiceController<HealthSensorService>? = null
        try {
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.supportedTypes = setOf(
                HealthTrackerType.HEART_RATE_CONTINUOUS,
                HealthTrackerType.EDA_CONTINUOUS,
                HealthTrackerType.ACCELEROMETER_CONTINUOUS,
                HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
            )
            enterRealFreshActionSurface(activity)
            val start = freshButtons(activity).single { it.text == "Start fresh 5 minutes" }
            assertTrue("resolved no-receipt UI permits exactly the real Start control", start.isEnabled)
            assertTrue(start.performClick())

            await("real Main queued BLE foreground intent") {
                startedServices(activity).any { it.isFreshBleStart() }
            }
            val session = requireNotNull(FreshRunRuntime.session)
            val startedReceipt = session.snapshot()
            // This is the actual meaning of the observed Watch79 label, before sensor input.
            assertEquals(FreshRunPhase.STARTED, startedReceipt.phase)
            assertEquals("NONE", startedReceipt.failure)
            assertEquals(0L, session.journal.latestRecordIndex())

            val bleIntent = startedServices(activity).single { it.isFreshBleStart() }
            bleController = Robolectric.buildService(BleGattService::class.java, bleIntent).create().startCommand(0, 79)
            val ble = bleController.get()
            val healthIntent = startedServices(ble).single { it.isFreshHealthStart() }
            healthController = Robolectric.buildService(HealthSensorService::class.java, healthIntent).create().startCommand(0, 80)
            val health = healthController.get()

            assertTrue("actual Health onStart began its bounded production session", session.acceptsSamples())
            assertTrue("only successful actual Health Handler.post may acknowledge BLE handoff", session.isRecordingStopScheduled())
            assertEquals(
                BoundedOrdinaryRunGate.State.ACCEPTING_SAMPLES,
                field<BoundedOrdinaryRunGate>(health, "boundedRunGate").state(),
            )

            // A physical side/Home press can pause/destroy Main. It must not own the service timer.
            controller.pause().stop().destroy()

            // TEST ONLY fake SDK delivery. Each callback enters the production listener and then
            // the real guarded append/SourcePayloadCodec/SourceJournal route. No canonical source
            // record is pre-positioned by this test.
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.emit(
                HealthTrackerType.EDA_CONTINUOUS,
                listOf(DataPoint(1_791_000_000_001L, mapOf(ValueKey.EdaSet.SKIN_CONDUCTANCE to 1.25f))),
            )
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.emit(
                HealthTrackerType.HEART_RATE_CONTINUOUS,
                listOf(
                    DataPoint(
                        1_791_000_000_002L,
                        mapOf(
                            ValueKey.HeartRateSet.HEART_RATE to 72,
                            ValueKey.HeartRateSet.HEART_RATE_STATUS to 0,
                            ValueKey.HeartRateSet.IBI_LIST to listOf(800),
                            ValueKey.HeartRateSet.IBI_STATUS_LIST to listOf(0),
                        ),
                    ),
                ),
            )
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.emit(
                HealthTrackerType.ACCELEROMETER_CONTINUOUS,
                listOf(
                    DataPoint(
                        1_791_000_000_003L,
                        mapOf(
                            ValueKey.AccelerometerSet.ACCELEROMETER_X to 1,
                            ValueKey.AccelerometerSet.ACCELEROMETER_Y to 2,
                            ValueKey.AccelerometerSet.ACCELEROMETER_Z to 3,
                        ),
                    ),
                ),
            )
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.emit(
                HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
                listOf(
                    DataPoint(
                        1_791_000_000_004L,
                        mapOf(
                            ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE to 33.5f,
                            ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE to 21.0f,
                            ValueKey.SkinTemperatureSet.STATUS to 0,
                        ),
                    ),
                ),
            )
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("production callbacks, not a fixture, appended canonical source", session.journal.latestRecordIndex() >= 4L)

            // Actual Handler-delayed five-minute stop runs after Main has gone away.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MINUTES)
            val terminal = requireNotNull(FreshRunRuntime.current(health))
            assertEquals(FreshRunPhase.FINALIZED, terminal.phase)
            assertEquals("NONE", terminal.failure)
            assertEquals(31, terminal.supportedMask)
            assertEquals(31, terminal.startedMask)
            assertEquals(1, terminal.manifests.size)
            assertTrue(terminal.manifests.single().recordCount >= 4L)
            assertTrue(
                terminal.manifests.single().streamRanges.keys.containsAll(
                    setOf(
                        SourceStreamCode.DEVICE_HEALTH,
                        SourceStreamCode.EDA,
                        SourceStreamCode.CARDIAC,
                        SourceStreamCode.ACCEL,
                        SourceStreamCode.SKIN_TEMP,
                    ),
                ),
            )
            assertEquals(
                BoundedOrdinaryRunGate.State.FINALIZED,
                field<BoundedOrdinaryRunGate>(health, "boundedRunGate").state(),
            )
            assertFalse(session.acceptsSamples())
            val finalIndex = session.journal.latestRecordIndex()
            field<com.samsung.android.service.health.tracking.ConnectionListener>(health, "connectionListener")
                .onConnectionFailed(com.samsung.android.service.health.tracking.HealthTrackerException("TEST ONLY late failed callback"))
            assertEquals("late SDK failure cannot rewrite finalized receipt", terminal.revision, session.snapshot().revision)
            assertEquals(FreshRunPhase.FINALIZED, session.snapshot().phase)
            assertEquals(finalIndex, session.journal.latestRecordIndex())
            assertTrue("late callback is not a new current-attempt failure", WatchDiagnosticRuntime.store(health).reports().isEmpty())
        } finally {
            runCatching { healthController?.destroy() }
            runCatching { bleController?.destroy() }
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
        }
    }

    @Test
    @Config(
        sdk = [34],
        shadows = [
            FreshRunDirectoryLinuxShadow::class,
            FreshRunAndroidServiceFlowMainDispatchRejectingShadow::class,
        ],
    )
    fun `actual Main inherited ContextWrapper foreground dispatch rejection captures original exception bound to admitted source and run`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        WatchDiagnosticRuntime.root(activity).deleteRecursively()
        // WatchDiagnosticRuntime intentionally preserves a first exception for an attempt. Its
        // process-local state outlives Robolectric test methods, so begin a fresh unbound attempt
        // before Main admits/binds this test's real run.
        WatchDiagnosticRuntime.begin(activity, null, null)
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7902)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("Main captured foreground dispatch rejection") { booleanField(activity, "freshTerminalFailure") }

            val receipt = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, receipt.phase)
            assertEquals("SERVICE_START_FAILED", receipt.failure)
            // Main intentionally ends the process-local runtime after its own dispatch catch.
            // Read the admitted run's durable root; do not preserve/recreate a session to test it.
            assertNull(FreshRunRuntime.session)
            SourceJournal(
                FreshRunReceiptStore(receiptRoot).sourceRoot(receipt.runId),
                7902,
                { 1_000_000L },
                { System.currentTimeMillis() },
            ).use { reopened -> assertEquals(0L, reopened.latestRecordIndex()) }
            assertTrue(field<TextView>(activity, "statusText").text.toString().contains("Fresh service start failed"))
            assertTrue(freshButtons(activity).all { !it.isEnabled })
            assertFalse(startedServices(activity).any { it.isFreshHealthStart() })
            val report = WatchDiagnosticRuntime.store(activity).reports().singleOrNull()
                ?.let { WatchDiagnosticRuntime.store(activity).read(it) }
            assertNotNull("Main catch must persist the actual dispatch rejection", report)
            assertTrue("Actual saved synthetic Main rejection report:\n$report", requireNotNull(report).contains("cause[0]=java.lang.SecurityException"))
            assertReportBoundToAdmittedReceipt(report, receipt)
        } finally {
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            WatchDiagnosticRuntime.root(activity).deleteRecursively()
        }
    }

    @Test
    @Config(
        sdk = [34],
        shadows = [
            FreshRunDirectoryLinuxShadow::class,
            FreshRunAndroidServiceFlowForegroundRejectingServiceShadow::class,
        ],
    )
    fun `actual BLE foreground rejection saves original exception before cleanup bound to admitted source and run`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        WatchDiagnosticRuntime.root(activity).deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7903)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("real Main queued BLE") { startedServices(activity).any { it.isFreshBleStart() } }
            val session = requireNotNull(FreshRunRuntime.session)
            val bleIntent = startedServices(activity).single { it.isFreshBleStart() }
            Robolectric.buildService(BleGattService::class.java, bleIntent).create().startCommand(0, 81)

            val receipt = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, receipt.phase)
            assertEquals("SERVICE_START_FAILED", receipt.failure)
            assertEquals(0L, session.journal.latestRecordIndex())
            assertFalse(startedServices(activity).any { it.isFreshHealthStart() })

            val reports = WatchDiagnosticRuntime.store(activity).reports()
            assertEquals("foreground rejection must be saved before BLE cleanup", 1, reports.size)
            val report = WatchDiagnosticRuntime.store(activity).read(reports.single())
            assertTrue(report.contains("stage=FOREGROUND_PROMOTION"))
            assertTrue(report.contains("cause[0]=java.lang.SecurityException"))
            assertTrue(report.contains("FreshRunAndroidServiceFlowForegroundRejectingServiceShadow"))
            assertReportBoundToAdmittedSession(report, session)
        } finally {
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            WatchDiagnosticRuntime.root(activity).deleteRecursively()
        }
    }

    @Test
    fun `actual BLE no Health entry terminates after its fifteen second run-bound handoff watchdog`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        WatchDiagnosticRuntime.root(activity).deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7904)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        enableSyntheticBluetooth(activity)
        var bleController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("real Main queued BLE") { startedServices(activity).any { it.isFreshBleStart() } }
            val session = requireNotNull(FreshRunRuntime.session)
            bleController = Robolectric.buildService(BleGattService::class.java, startedServices(activity).single { it.isFreshBleStart() })
                .create().startCommand(0, 82)

            assertTrue("BLE did request Health, but this test deliberately never enters its Service", startedServices(bleController.get()).any { it.isFreshHealthStart() })
            assertFalse("Health did not enter, therefore it cannot acknowledge a timer post", session.isRecordingStopScheduled())
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(15, TimeUnit.SECONDS)
            await("BLE fail-closed missing-Health watchdog") {
                FreshRunRuntime.current(activity)?.failure == "HEALTH_START_TIMEOUT"
            }

            val receipt = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, receipt.phase)
            assertEquals("HEALTH_START_TIMEOUT", receipt.failure)
            assertEquals(0L, session.journal.latestRecordIndex())
            val report = singleReport(activity)
            assertTrue(report.contains("stage=SERVICE_INITIALIZATION"))
            assertTrue(report.contains("outcome=DEADLINE"))
            assertReportBoundToAdmittedSession(report, session)
        } finally {
            runCatching { bleController?.destroy() }
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            WatchDiagnosticRuntime.root(activity).deleteRecursively()
        }
    }

    @Test
    fun `actual Health capability getter failure saves original exception and fails initialization closed`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        WatchDiagnosticRuntime.root(activity).deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7905)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        enableSyntheticBluetooth(activity)
        var bleController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        var healthController: org.robolectric.android.controller.ServiceController<HealthSensorService>? = null
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("real Main queued BLE") { startedServices(activity).any { it.isFreshBleStart() } }
            val session = requireNotNull(FreshRunRuntime.session)
            bleController = Robolectric.buildService(BleGattService::class.java, startedServices(activity).single { it.isFreshBleStart() })
                .create().startCommand(0, 83)
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.capabilityFailure = IllegalStateException("TEST ONLY capability getter rejection")
            healthController = Robolectric.buildService(
                HealthSensorService::class.java,
                startedServices(bleController.get()).single { it.isFreshHealthStart() },
            ).create().startCommand(0, 84)

            await("actual Health capability failure receipt") {
                FreshRunRuntime.current(activity)?.failure == "SDK_CAPABILITY_FAILED"
            }
            val receipt = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, receipt.phase)
            assertEquals("SDK_CAPABILITY_FAILED", receipt.failure)
            assertEquals(0L, session.journal.latestRecordIndex())
            assertTrue("successful timer post preceded capability access", session.isRecordingStopScheduled())
            val report = singleReport(activity)
            assertTrue(report.contains("stage=SERVICE_INITIALIZATION"))
            assertTrue(report.contains("cause[0]=java.lang.IllegalStateException"))
            assertReportBoundToAdmittedSession(report, session)
        } finally {
            runCatching { healthController?.destroy() }
            runCatching { bleController?.destroy() }
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            WatchDiagnosticRuntime.root(activity).deleteRecursively()
        }
    }

    @Test
    fun `actual Health SDK failed callback preserves original vendor exception before cleanup`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        WatchDiagnosticRuntime.root(activity).deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7907)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        enableSyntheticBluetooth(activity)
        var bleController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        var healthController: org.robolectric.android.controller.ServiceController<HealthSensorService>? = null
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("real Main queued BLE") { startedServices(activity).any { it.isFreshBleStart() } }
            val session = requireNotNull(FreshRunRuntime.session)
            bleController = Robolectric.buildService(BleGattService::class.java, startedServices(activity).single { it.isFreshBleStart() })
                .create().startCommand(0, 83)
            FreshRunAndroidServiceFlowTestOnlySamsungSdk.connectFailure = com.samsung.android.service.health.tracking.HealthTrackerException("TEST ONLY SDK rejected connection")
            healthController = Robolectric.buildService(
                HealthSensorService::class.java,
                startedServices(bleController.get()).single { it.isFreshHealthStart() },
            ).create().startCommand(0, 84)

            await("actual Health SDK failure receipt") {
                FreshRunRuntime.current(activity)?.failure == "SDK_CONNECTION_FAILED"
            }
            val receipt = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, receipt.phase)
            assertEquals("SDK_CONNECTION_FAILED", receipt.failure)
            assertEquals(0L, session.journal.latestRecordIndex())
            assertTrue("successful timer post preceded SDK callback", session.isRecordingStopScheduled())
            val report = singleReport(activity)
            assertTrue(report.contains("stage=SERVICE_INITIALIZATION"))
            assertTrue(report.contains("cause[0]=com.samsung.android.service.health.tracking.HealthTrackerException"))
            assertReportBoundToAdmittedSession(report, session)
        } finally {
            runCatching { healthController?.destroy() }
            runCatching { bleController?.destroy() }
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
            WatchDiagnosticRuntime.root(activity).deleteRecursively()
        }
    }

    @Test
    fun `actual Health absent TEST ONLY SDK types stays honest then finalizes SENSORS_UNSUPPORTED`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val receiptRoot = FreshRunRuntime.receiptRoot(activity)
        receiptRoot.deleteRecursively()
        ShadowStatFs.registerStats(activity.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 7906)
        Shadows.shadowOf(activity).grantPermissions(*permissionSet)
        enableSyntheticBluetooth(activity)
        var bleController: org.robolectric.android.controller.ServiceController<BleGattService>? = null
        var healthController: org.robolectric.android.controller.ServiceController<HealthSensorService>? = null
        try {
            enterRealFreshActionSurface(activity)
            assertTrue(freshButtons(activity).single { it.text == "Start fresh 5 minutes" }.performClick())
            await("real Main queued BLE") { startedServices(activity).any { it.isFreshBleStart() } }
            val session = requireNotNull(FreshRunRuntime.session)
            bleController = Robolectric.buildService(BleGattService::class.java, startedServices(activity).single { it.isFreshBleStart() })
                .create().startCommand(0, 85)
            healthController = Robolectric.buildService(
                HealthSensorService::class.java,
                startedServices(bleController.get()).single { it.isFreshHealthStart() },
            ).create().startCommand(0, 86)

            assertTrue(session.isRecordingStopScheduled())
            assertEquals(16, session.snapshot().supportedMask)
            assertEquals(16, session.snapshot().startedMask)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MINUTES)
            val terminal = requireNotNull(FreshRunRuntime.current(activity))
            assertEquals(FreshRunPhase.FAILED, terminal.phase)
            assertEquals("SENSORS_UNSUPPORTED", terminal.failure)
            assertEquals(16, terminal.supportedMask)
            assertEquals(16, terminal.startedMask)
            assertTrue("no absent SDK type is fabricated as a canonical sensor record", terminal.manifests.all { manifest ->
                manifest.streamRanges.keys.all { it == SourceStreamCode.DEVICE_HEALTH }
            })
        } finally {
            runCatching { healthController?.destroy() }
            runCatching { bleController?.destroy() }
            controller.destroy()
            ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
            receiptRoot.deleteRecursively()
        }
    }

    private fun enterRealFreshActionSurface(activity: MainActivity) {
        field<TextView>(activity, "evidenceText").viewTreeObserver.dispatchOnDraw()
        await("Main first receipt decision") { booleanField(activity, "freshReadinessResolved") }
    }

    private fun enableSyntheticBluetooth(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = requireNotNull(manager.adapter)
        Shadows.shadowOf(adapter).setEnabled(true)
        assertTrue(adapter.isEnabled)
        assertNotNull("Robolectric test-only adapter supplies a synthetic LE advertiser", adapter.bluetoothLeAdvertiser)
    }

    private fun startedServices(context: Context): List<Intent> =
        Shadows.shadowOf(context.applicationContext as android.app.Application).allStartedServices
    private fun Intent.isFreshBleStart(): Boolean = action == FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES &&
        component?.className == BleGattService::class.java.name
    private fun Intent.isFreshHealthStart(): Boolean = action == FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES &&
        component?.className == HealthSensorService::class.java.name

    private fun freshButtons(activity: MainActivity): MutableList<Button> = field(activity, "freshActionButtons")
    private fun booleanField(target: Any, name: String): Boolean = field(target, name)
    private fun singleReport(context: Context): String {
        val reports = WatchDiagnosticRuntime.store(context).reports()
        assertEquals("one original failure report must survive terminal cleanup", 1, reports.size)
        return WatchDiagnosticRuntime.store(context).read(reports.single())
    }

    private fun assertReportBoundToAdmittedSession(report: String?, session: FreshRunSession) {
        val text = requireNotNull(report)
        assertTrue("diagnostic attempt must remain the admitted fresh run", text.contains("attempt=${session.runId}"))
        assertTrue("diagnostic source must remain the admitted fresh source", text.contains("sourceSession=${session.journal.watchBootSessionId}"))
    }

    private fun assertReportBoundToAdmittedReceipt(report: String?, receipt: FreshRunReceipt) {
        val text = requireNotNull(report)
        assertTrue("diagnostic attempt must remain the admitted fresh run", text.contains("attempt=${receipt.runId}"))
        assertTrue("diagnostic source must remain the admitted fresh source", text.contains("sourceSession=${receipt.sourceId}"))
    }

    private fun await(description: String, condition: () -> Boolean) {
        repeat(200) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for $description")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }
}

/**
 * TEST ONLY: rejects MainActivity's actual delegated ContextImpl dispatch.
 *
 * A ShadowActivity method does not observe this call. A custom ContextWrapper shadow also
 * conflicts with the actual ShadowActivity subtype and throws a test-only ClassCastException.
 * Shadow the delegated ContextImpl instance, not either parent-class look-alike.
 */
@Implements(className = "android.app.ContextImpl", callThroughByDefault = true)
class FreshRunAndroidServiceFlowMainDispatchRejectingShadow : ShadowContextImpl() {

    @Implementation
    override fun startForegroundService(intent: Intent): ComponentName {
        if (intent.isFreshBleStart()) throw SecurityException("synthetic Main dispatch rejection")
        return super.startForegroundService(intent)
    }

    private fun Intent.isFreshBleStart(): Boolean = action == FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES &&
        component?.className == BleGattService::class.java.name
}

/** TEST ONLY: throws the concrete platform-style foreground-promotion failure inside actual BLE onStart. */
@Implements(Service::class, callThroughByDefault = true)
class FreshRunAndroidServiceFlowForegroundRejectingServiceShadow : ShadowService() {
    @Implementation
    override fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) {
        throw SecurityException("synthetic BLE foreground promotion rejection")
    }
}

/*
 * TEST RUNTIME ONLY — these exact bytecode names are supplied by the admitted compatibility DEX
 * in a packaged Watch candidate, while app/build.gradle intentionally exposes only compileOnly
 * declarations to JVM unit tests. Robolectric therefore cannot reflectively load BleGattService
 * without the minimal no-op equivalents below. They are co-located with, and used only by, this
 * owned test source; they do not alter production source, Gradle, DEX, packaging, or egress use.
 * The fresh STANDARD branch never invokes the egress methods during these tests.
 */
enum class EvidenceEgressGattRuntimeMode { STANDARD, EGRESS_ONLY }

object EvidenceEgressGattStartPolicy {
    fun isExplicitEgressStop(action: String?): Boolean = false
    fun requestedMode(action: String?): EvidenceEgressGattRuntimeMode = EvidenceEgressGattRuntimeMode.STANDARD
    fun acceptsModeTransition(
        current: EvidenceEgressGattRuntimeMode?,
        requested: EvidenceEgressGattRuntimeMode,
    ): Boolean = current == null || current == requested
}

object EvidenceEgressVolatileDiagnostics {
    fun onForegroundStopped() = Unit
    fun onRuntimeReused(foregroundActive: Boolean, advertiserReady: Boolean) = Unit
    fun onRuntimeInitialized() = Unit
    fun onForegroundStarted() = Unit
    fun onReadinessFailure(reason: String) = Unit
    fun onRuntimeDestroyed() = Unit
    fun onAdvertiserStartFailed(reason: String) = Unit
    fun beginConnectionAttempt(status: Int, state: Int) = Unit
    fun onConnectionStateChange(status: Int, state: Int) = Unit
    fun onMtuChanged(mtu: Int) = Unit
    fun onGattServiceAdded(status: Int) = Unit
    fun onOfferReadEntered(offset: Int) = Unit
    fun onOfferResponse(status: Int, sent: Boolean) = Unit
    fun onAdvertiserStarted() = Unit
}

object EvidenceEgressActivation {
    fun offerBytes(): ByteArray = byteArrayOf()
    fun selectedChunkBytes(): ByteArray? = null
    fun receiptBytes(): ByteArray = byteArrayOf()
    fun recordSelectedChunkRead() = Unit
    fun handleControl(value: ByteArray): Boolean = false
}

object EvidenceEgressContract {
    const val MINIMUM_GATT_MTU = 128
    fun isEgressRead(uuid: java.util.UUID): Boolean = false
    fun isEgressWrite(uuid: java.util.UUID): Boolean = false
}
