package com.hugr.wearos

import android.Manifest
import android.app.Application
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowStatFs

/**
 * Watch81 readiness correction regression.
 *
 * These are actual Robolectric MainActivity tests.  The receipt reader is the Activity's real
 * worker and every selected receipt is written/read through [FreshRunReceiptStore].  The only
 * substitutes are Robolectric Android services and [FreshRunActivityAdmissionDisplayTestNoBackupShadow],
 * which redirects the app-owned no-backup anchor to a new disposable Linux directory per case.
 * No hardware, Samsung SDK callback, service instance, or physiological payload is invoked.
 *
 * The FINALIZED/COMPLETED display fixtures are deliberately receipt-store fixtures, not claims
 * that a physical Watch reached those phases: generating them through the real five-minute
 * clock/custody path would be outside this UI/readiness test's scope.  STARTED and the newly
 * admitted case use the real production runtime admission path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [
        FreshRunDirectoryLinuxShadow::class,
        FreshRunActivityAdmissionDisplayTestNoBackupShadow::class,
    ],
)
class FreshRunActivityAdmissionDisplayTest {
    companion object {
        /** Stable display contract shared with the Main correction. */
        private const val READY_TO_START = "READY TO START"
        private const val PREVIOUS_RUN_UNRESOLVED = "PREVIOUS RUN UNRESOLVED"
        private const val NEW_RUN_ADMITTED = "NEW RUN ADMITTED"
        private const val START = "Start fresh 5 minutes"
        private const val RECEIPT_ONLY = "Serve current fresh receipt only"
        private const val RETAINED = "Explicit retained v2 delivery"
    }

    /** The only test fixture filesystem root; it is deleted after each test. */
    private var anchor: File? = null
    /** Exact production nested v3 root below [anchor]. */
    private var root: File? = null

    @Before
    fun before() {
        resetRuntimeWithoutReceiptRewrite()
        newDisposableRoot()
    }

    @After
    fun after() {
        // Deliberately do not call FreshRunRuntime.end(): it changes an unresolved STARTED
        // receipt to FAILED.  Tests model process loss by clearing only process-local fields.
        resetRuntimeWithoutReceiptRewrite()
        anchor?.let {
            ShadowStatFs.unregisterStats(it)
            it.deleteRecursively()
        }
        anchor = null
        root = null
        FreshRunActivityAdmissionDisplayTestNoBackupShadow.root = null
    }

    @Test
    fun `saved STARTED stays previous unresolved through process reset recreation passive reads and refused taps`() {
        val receipt = admitStartedThenModelProcessLoss()
        val before = rootDigest()
        val startedServices = freshServiceCount()

        val first = launchAndResolve()
        try {
            assertUnresolvedAndPassive(first.activity, receipt, before, startedServices)
            first.controller.pause().stop().destroy()

            // A second Activity with no singleton session models recreation after process-state
            // loss.  It must reopen the same durable receipt, not turn it into a fresh admission.
            resetRuntimeWithoutReceiptRewrite()
            val recreated = launchAndResolve()
            try {
                assertUnresolvedAndPassive(recreated.activity, receipt, before, startedServices)
                assertEquals(before, rootDigest())
            } finally {
                recreated.controller.destroy()
            }
        } finally {
            // destroy() is harmless if already destroyed and does not mutate the receipt.
            runCatching { first.controller.destroy() }
        }
    }

    @Test
    fun `saved FINALIZED stays previous unresolved and passive UI never rewrites its bytes`() {
        val receipt = finalizedReceipt()
        persist(receipt)
        val before = rootDigest()
        val startedServices = freshServiceCount()
        val launched = launchAndResolve()
        try {
            assertUnresolvedAndPassive(launched.activity, receipt, before, startedServices)
            invoke(launched.activity, "renderEvidence")
            await("finalized passive reread") { !booleanField(launched.activity, "freshReadPending") }
            assertUnresolved(launched.activity, receipt)
            assertEquals(before, rootDigest())
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `absent receipt is READY TO START with independently gated retained and receipt-only controls`() {
        val launched = launchAndResolve()
        try {
            assertReadyControls(launched.activity, selectedReceiptExists = false)
            assertEquals(emptyList<String>(), rootDigest())
            assertNull(FreshRunRuntime.session)
            assertFalse(FreshRunRuntime.receiptOnly)
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `FAILED and COMPLETED selected receipts are READY TO START without losing their selected identities`() {
        listOf(failedReceipt(), completedReceipt()).forEach { receipt ->
            newDisposableRoot()
            persist(receipt)
            val before = rootDigest()
            val launched = launchAndResolve()
            try {
                assertReadyControls(launched.activity, selectedReceiptExists = true)
                assertIdentity(launched.activity, receipt)
                assertEquals("Passive ready display cannot publish a terminal receipt", before, rootDigest())
                assertNull(FreshRunRuntime.session)
            } finally {
                launched.controller.destroy()
            }
        }
    }

    @Test
    fun `same phase selection change updates displayed run identity and passive reads preserve the selected bytes`() {
        val first = failedReceipt()
        val second = failedReceipt()
        assertNotEquals(first.runId, second.runId)
        assertNotEquals(first.sourceId, second.sourceId)
        persist(first)
        val launched = launchAndResolve()
        try {
            assertReadyControls(launched.activity, selectedReceiptExists = true)
            assertIdentity(launched.activity, first)

            // This is a real selection publication of a second FAILED receipt, not a mocked UI
            // result.  The Activity must follow the selected run even though phase is unchanged.
            persist(second)
            val afterSelection = rootDigest()
            invoke(launched.activity, "renderEvidence")
            await("same-phase current receipt reread") { !booleanField(launched.activity, "freshReadPending") }
            assertReadyControls(launched.activity, selectedReceiptExists = true)
            assertIdentity(launched.activity, second)
            assertFalse("Old selected run must not remain presented as current", allText(launched.activity).contains(first.runId.toString()))
            assertEquals(afterSelection, rootDigest())
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `same run source mismatch fails closed in production store and UI keeps the accepted receipt`() {
        val accepted = failedReceipt()
        persist(accepted)
        val store = FreshRunReceiptStore(requireNotNull(root))
        val beforeRejectedPublication = rootDigest()
        try {
            store.publish(
                accepted.copy(
                    revision = accepted.revision + 1,
                    sourceId = UUID.randomUUID(),
                ),
            )
            fail("Production store accepted a source mismatch for the same run")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals("Rejected source mismatch must leave selected bytes exact", beforeRejectedPublication, rootDigest())

        val launched = launchAndResolve()
        try {
            assertReadyControls(launched.activity, selectedReceiptExists = true)
            assertIdentity(launched.activity, accepted)
            assertEquals(beforeRejectedPublication, rootDigest())
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `STARTED persisted after ready before tap is rechecked and cannot become a false new admission`() {
        val launched = launchAndResolve()
        try {
            assertReadyControls(launched.activity, selectedReceiptExists = false)
            val prior = startedReceiptForStoreOnly()
            persist(prior)
            val beforeTap = rootDigest()
            val servicesBeforeTap = freshServiceCount()

            // The selected receipt appears after the passive readiness decision.  A UI-ready
            // Start must revalidate before permissions/admission/service dispatch; pressing it
            // must never label this prior receipt as a new admission.
            button(launched.activity, START).performClick()
            await("race prior receipt is rendered as unresolved") {
                !booleanField(launched.activity, "freshReadPending") &&
                    status(launched.activity).contains(PREVIOUS_RUN_UNRESOLVED)
            }
            assertUnresolved(launched.activity, prior)
            assertFalse(booleanField(launched.activity, "freshRequested"))
            assertFalse(booleanField(launched.activity, "freshAdmissionScheduled"))
            assertNull(FreshRunRuntime.session)
            assertFalse(FreshRunRuntime.receiptOnly)
            assertEquals(servicesBeforeTap, freshServiceCount())
            assertFalse(status(launched.activity).contains(NEW_RUN_ADMITTED))
            assertEquals(beforeTap, rootDigest())
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `new admission confirmation requires returned matching receipt and never reuses old selected identity`() {
        val old = failedReceipt()
        persist(old)
        val launched = launchAndResolve()
        try {
            assertReadyControls(launched.activity, selectedReceiptExists = true)
            assertIdentity(launched.activity, old)
            grantFreshPermissions(launched.activity)
            val servicesBefore = freshServiceCount()

            val start = button(launched.activity, START)
            assertTrue(start.performClick())
            // A button press alone is not evidence of admission.  The old receipt must never be
            // rendered with the new-admission heading while the production admission worker runs.
            assertFalse(status(launched.activity).contains(NEW_RUN_ADMITTED))
            assertTrue("Repeated immediate press is logically rejected", start.performClick())

            await("production admission") { FreshRunRuntime.session != null }
            val session = requireNotNull(FreshRunRuntime.session)
            val admitted = requireNotNull(FreshRunRuntime.current(launched.activity))
            assertEquals(session.runId, admitted.runId)
            assertEquals(session.journal.watchBootSessionId, admitted.sourceId)
            assertNotEquals(old.runId, admitted.runId)
            assertNotEquals(old.sourceId, admitted.sourceId)

            invoke(launched.activity, "renderEvidence")
            await("admitted receipt render") { !booleanField(launched.activity, "freshReadPending") }
            assertTrue("Success is displayed only after a matching durable receipt is read", status(launched.activity).contains(NEW_RUN_ADMITTED))
            assertIdentity(launched.activity, admitted)
            assertFalse("Old selected receipt cannot be presented as the new run", allText(launched.activity).contains(old.runId.toString()))

            val afterAdmission = rootDigest()
            val servicesAfterAdmission = freshServiceCount()
            assertEquals("Exactly one foreground dispatch follows the one admitted run", servicesBefore + 1, servicesAfterAdmission)
            start.performClick()
            assertEquals("Refused repeated tap cannot rewrite admitted receipt", afterAdmission, rootDigest())
            assertEquals("Refused repeated tap cannot dispatch another service", servicesAfterAdmission, freshServiceCount())
        } finally {
            launched.controller.destroy()
        }
    }

    @Test
    fun `320 square layout puts status source run and actions in one scroll hierarchy with identity before controls`() {
        val receipt = failedReceipt()
        persist(receipt)
        val launched = launchAndResolve()
        try {
            val activity = launched.activity
            assertReadyControls(activity, selectedReceiptExists = true)
            assertIdentity(activity, receipt)
            val content = requireNotNull(activity.findViewById<ViewGroup>(android.R.id.content))
            val scroll = requireNotNull(findScroll(content))
            content.measure(exactly(320), exactly(320))
            content.layout(0, 0, 320, 320)

            val critical = listOf<View>(
                field(activity, "statusText"),
                field(activity, "evidenceText"),
            ) + buttons(activity)
            assertTrue("The 320x320 screen must have a real whole-content ScrollView", critical.all { isDescendant(scroll, it) })
            val start = button(activity, START)
            assertTrue("Readiness heading must precede fixed action controls", topWithin(scroll, field(activity, "statusText")) < topWithin(scroll, start))
            assertTrue("Source/run/revision detail must precede controls, not sit below large fixed buttons", topWithin(scroll, field(activity, "evidenceText")) < topWithin(scroll, start))
            assertTrue("Scrolled content must contain the identity detail", scroll.getChildAt(0).height >= topWithin(scroll, field(activity, "evidenceText")))
        } finally {
            launched.controller.destroy()
        }
    }

    private data class Launched(
        val controller: org.robolectric.android.controller.ActivityController<MainActivity>,
        val activity: MainActivity,
    )

    private fun launchAndResolve(): Launched {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        field<TextView>(activity, "evidenceText").viewTreeObserver.dispatchOnDraw()
        invoke(activity, "renderEvidence")
        await("initial readiness read") {
            booleanField(activity, "freshReadinessResolved") && !booleanField(activity, "freshReadPending")
        }
        return Launched(controller, activity)
    }

    private fun assertUnresolvedAndPassive(
        activity: MainActivity,
        receipt: FreshRunReceipt,
        before: List<String>,
        serviceCount: Int,
    ) {
        assertUnresolved(activity, receipt)
        button(activity, START).performClick()
        assertFalse(booleanField(activity, "freshRequested"))
        assertFalse(booleanField(activity, "freshAdmissionScheduled"))
        assertNull(FreshRunRuntime.session)
        assertFalse(FreshRunRuntime.receiptOnly)
        assertEquals("Unresolved receipt tap must not start a service", serviceCount, freshServiceCount())
        assertEquals("Passive read/refused taps must preserve every receipt-store byte", before, rootDigest())
    }

    private fun assertUnresolved(activity: MainActivity, receipt: FreshRunReceipt) {
        assertTrue("Saved ${receipt.phase} needs an explicit preservation heading", status(activity).contains(PREVIOUS_RUN_UNRESOLVED))
        assertTrue(status(activity).contains(receipt.phase.name))
        assertIdentity(activity, receipt)
        assertTrue("Receipt-only remains a separately explicit no-sensing action", button(activity, RECEIPT_ONLY).isEnabled)
        assertFalse("Start is disabled for a saved unresolved run", button(activity, START).isEnabled)
    }

    private fun assertReadyControls(activity: MainActivity, selectedReceiptExists: Boolean) {
        assertTrue("Only absent/FAILED/COMPLETED selection is ready for a fresh start", status(activity).contains(READY_TO_START))
        assertTrue("Start is independently enabled once readiness resolves", button(activity, START).isEnabled)
        assertTrue("Retained delivery has its own explicit gate", button(activity, RETAINED).isEnabled)
        assertEquals(
            "Receipt-only must be independently gated by presence of a selected current receipt",
            selectedReceiptExists,
            button(activity, RECEIPT_ONLY).isEnabled,
        )
    }

    private fun assertIdentity(activity: MainActivity, receipt: FreshRunReceipt) {
        val text = allText(activity)
        assertTrue("Displayed source must identify the selected production receipt", text.contains(receipt.sourceId.toString()))
        assertTrue("Displayed run must identify the selected production receipt", text.contains(receipt.runId.toString()))
        assertTrue("Displayed receipt must expose revision", text.contains(receipt.revision.toString()))
    }

    private fun admitStartedThenModelProcessLoss(): FreshRunReceipt {
        val app = application()
        assertTrue(Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 8101))
        val session = FreshRunRuntime.admit(app)
        val receipt = session.snapshot()
        assertEquals(FreshRunPhase.STARTED, receipt.phase)
        resetRuntimeWithoutReceiptRewrite()
        return receipt
    }

    private fun persist(receipt: FreshRunReceipt) {
        FreshRunReceiptStore(requireNotNull(root)).create(receipt)
    }

    private fun failedReceipt(): FreshRunReceipt = FreshRunReceipt(
        sourceId = UUID.randomUUID(),
        runId = UUID.randomUUID(),
        revision = 2,
        phase = FreshRunPhase.FAILED,
        startWallMs = 1_800_000_000_000L,
        startElapsedMs = 1_000L,
        bootCount = 81,
        supportedMask = 16,
        startedMask = 16,
        failure = "PROCESS_INTERRUPTED",
    )

    private fun startedReceiptForStoreOnly(): FreshRunReceipt = FreshRunReceipt(
        sourceId = UUID.randomUUID(),
        runId = UUID.randomUUID(),
        revision = 1,
        phase = FreshRunPhase.STARTED,
        startWallMs = 1_800_000_000_000L,
        startElapsedMs = 1_000L,
        bootCount = 81,
        supportedMask = 16,
        startedMask = 16,
    )

    private fun finalizedReceipt(): FreshRunReceipt = FreshRunReceipt(
        sourceId = UUID.randomUUID(),
        runId = UUID.randomUUID(),
        revision = 7,
        phase = FreshRunPhase.FINALIZED,
        startWallMs = 1_800_000_000_000L,
        startElapsedMs = 1_000L,
        finalizedWallMs = 1_800_000_300_000L,
        finalizedElapsedMs = 301_000L,
        bootCount = 81,
        supportedMask = 31,
        startedMask = 31,
    )

    private fun completedReceipt(): FreshRunReceipt {
        val source = UUID.randomUUID()
        val hash = "a".repeat(64)
        val ranges = SourceStreamCode.entries.associateWith { SourceStreamRange(1, 1, 1) }
        val manifest = SourceSegmentManifest(
            segmentId = "display_fixture",
            watchBootSessionId = source,
            firstRecordIndex = 1,
            lastRecordIndex = 5,
            recordCount = 5,
            byteCount = 5 * 48L,
            sha256Hex = hash,
            streamRanges = ranges,
        )
        return FreshRunReceipt(
            sourceId = source,
            runId = UUID.randomUUID(),
            revision = 9,
            phase = FreshRunPhase.COMPLETED,
            startWallMs = 1_800_000_000_000L,
            startElapsedMs = 1_000L,
            finalizedWallMs = 1_800_000_300_000L,
            finalizedElapsedMs = 301_000L,
            bootCount = 81,
            supportedMask = 31,
            startedMask = 31,
            manifests = listOf(manifest),
            acks = listOf(FreshAcceptedAck(5, hash, 301_000L)),
        )
    }

    private fun grantFreshPermissions(activity: MainActivity) {
        Shadows.shadowOf(activity).grantPermissions(
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
        assertTrue(Settings.Global.putInt(activity.contentResolver, Settings.Global.BOOT_COUNT, 8102))
    }

    private fun newDisposableRoot() {
        resetRuntimeWithoutReceiptRewrite()
        anchor?.let {
            ShadowStatFs.unregisterStats(it)
            it.deleteRecursively()
        }
        anchor = Files.createTempDirectory("fresh-admission-display-").toFile()
        root = File(requireNotNull(anchor), FreshRunRuntime.DIRECTORY)
        FreshRunActivityAdmissionDisplayTestNoBackupShadow.root = requireNotNull(anchor)
        ShadowStatFs.registerStats(requireNotNull(anchor), 4_096, 1_000_000, 1_000_000)
    }

    private fun resetRuntimeWithoutReceiptRewrite() {
        val runtimeClass = FreshRunRuntime::class.java
        val sessionField = runtimeClass.getDeclaredField("session").apply { isAccessible = true }
        val session = sessionField.get(FreshRunRuntime) as FreshRunSession?
        session?.quiesce()
        runCatching { session?.journal?.close() }
        sessionField.set(FreshRunRuntime, null)
        runtimeClass.getDeclaredField("receiptOnly").apply { isAccessible = true }.setBoolean(FreshRunRuntime, false)
    }

    private fun rootDigest(): List<String> {
        val selected = requireNotNull(root)
        if (!selected.exists()) return emptyList()
        return selected.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(selected).invariantSeparatorsPath }
            .map { file ->
                "${file.relativeTo(selected).invariantSeparatorsPath}:${FreshRunReceipt.digest(file.readBytes())}"
            }
            .toList()
    }

    private fun freshServiceCount(): Int =
        Shadows.shadowOf(application()).allStartedServices.count { intent ->
            intent.action == FreshRunRuntime.ACTION_START_FRESH_FIVE_MINUTES &&
                intent.component?.className == BleGattService::class.java.name
        }

    private fun application(): Application = RuntimeEnvironment.getApplication()
    private fun buttons(activity: MainActivity): MutableList<Button> = field(activity, "freshActionButtons")
    private fun button(activity: MainActivity, label: String): Button =
        buttons(activity).single { it.text.toString() == label }
    private fun status(activity: MainActivity): String = field<TextView>(activity, "statusText").text.toString()
    private fun allText(activity: MainActivity): String = listOf(
        field<TextView>(activity, "statusText").text,
        field<TextView>(activity, "evidenceText").text,
    ).joinToString("\n")

    private fun isDescendant(parent: ViewGroup, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }

    private fun findScroll(view: View): ScrollView? = when (view) {
        is ScrollView -> view
        is ViewGroup -> (0 until view.childCount)
            .asSequence()
            .mapNotNull { findScroll(view.getChildAt(it)) }
            .firstOrNull()
        else -> null
    }

    private fun topWithin(ancestor: ViewGroup, child: View): Int {
        var current: View = child
        var top = 0
        while (current !== ancestor) {
            top += current.top
            current = current.parent as? View ?: error("View is not in expected ScrollView")
        }
        return top
    }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun invoke(target: Any, name: String) = target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    private fun booleanField(target: Any, name: String): Boolean = field(target, name)

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun await(description: String, condition: () -> Boolean) {
        repeat(300) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for $description")
    }
}

/** TEST ONLY: directs ContextImpl's app-owned no-backup anchor to a disposable real directory. */
@Implements(className = "android.app.ContextImpl", callThroughByDefault = true)
class FreshRunActivityAdmissionDisplayTestNoBackupShadow : ShadowContextImpl() {
    companion object {
        @Volatile var root: File? = null
    }

    @Implementation
    fun getNoBackupFilesDir(): File = requireNotNull(root) { "No disposable no-backup root configured" }
}
