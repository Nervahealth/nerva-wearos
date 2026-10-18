package com.hugr.wearos

import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.TextView
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/** Real Activity views and production receipt reads; synthetic Android/filesystem, not display hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FreshRunDirectoryLinuxShadow::class])
class FreshRunActivityRefreshTest {
    @Before fun before() { FreshRunRuntime.end() }
    @After fun after() { FreshRunRuntime.end() }

    @Test fun `unchanged durable receipt does not reset TextViews or their layout on every tick`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val root = FreshRunRuntime.receiptRoot(activity)
        root.deleteRecursively()
        // Use actual admission and writer, not a mocked receipt returned by a UI fixture.
        org.robolectric.shadows.ShadowStatFs.registerStats(activity.noBackupFilesDir, 4096, 1000000, 1000000)
        android.provider.Settings.Global.putInt(activity.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 11)
        val run = FreshRunRuntime.admit(activity)
        try {
            invoke(activity, "renderEvidence")
            await { !field<Boolean>(activity, "freshReadPending") }
            val status = field<TextView>(activity, "statusText")
            val evidence = field<TextView>(activity, "evidenceText")
            val beforeStatus = status.text.toString()
            val beforeEvidence = evidence.text.toString()
            var updates = 0
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updates++ }
                override fun afterTextChanged(s: Editable?) {}
            }
            status.addTextChangedListener(watcher); evidence.addTextChangedListener(watcher)
            repeat(3) {
                invoke(activity, "renderEvidence")
                await { !field<Boolean>(activity, "freshReadPending") }
            }
            assertEquals(beforeStatus, status.text.toString())
            assertEquals(beforeEvidence, evidence.text.toString())
            assertEquals("Unchanged polling must not reset TextView text/layout", 0, updates)
            // Actual pause/resume callbacks must not transiently restore the startup
            // headline while an already displayed receipt is being read again.
            controller.resume().pause().resume()
            await { !field<Boolean>(activity, "freshReadPending") }
            assertEquals(beforeStatus, status.text.toString())
            assertEquals(beforeEvidence, evidence.text.toString())
            assertEquals("Resume must preserve the same receipt presentation", 0, updates)
            assertEquals(0L, run.journal.latestRecordIndex())
            assertFalse(run.acceptsSamples())
        } finally {
            controller.destroy(); FreshRunRuntime.end(); root.deleteRecursively()
            org.robolectric.shadows.ShadowStatFs.unregisterStats(activity.noBackupFilesDir)
        }
    }

    @Test fun `passive empty receipt does not flicker action enabled state each second`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val root = FreshRunRuntime.receiptRoot(activity)
        root.deleteRecursively()
        try {
            invoke(activity, "renderEvidence")
            await { !field<Boolean>(activity, "freshReadPending") }
            val buttons = field<MutableList<Button>>(activity, "freshActionButtons")
            assertReadyWithoutReceipt(buttons)
            // Keep the real worker pending. Actions remain logically blocked by freshReadPending;
            // after the initial ready decision a passive refresh need not repaint all buttons.
            synchronized(FreshRunRuntime) {
                invoke(activity, "renderEvidence")
                assertTrue(field<Boolean>(activity, "freshReadPending"))
                assertReadyWithoutReceipt(buttons)
                buttons.forEach { it.performClick() }
                assertFalse(field<Boolean>(activity, "freshRequested"))
            }
            await { !field<Boolean>(activity, "freshReadPending") }
            assertReadyWithoutReceipt(buttons)
        } finally { controller.destroy(); root.deleteRecursively() }
    }

    @Test fun `fresh action before first readiness resolution cannot admit or request a service`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            assertFalse(field<Boolean>(activity, "freshReadinessResolved"))
            field<MutableList<Button>>(activity, "freshActionButtons").forEach { it.performClick() }
            assertFalse(field<Boolean>(activity, "freshRequested"))
            assertFalse(field<Boolean>(activity, "freshAdmissionScheduled"))
            assertNull(FreshRunRuntime.session)
            assertNull(Shadows.shadowOf(activity).nextStartedService)
        } finally { controller.destroy() }
    }

    private fun invoke(target: Any, name: String) = target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    @Suppress("UNCHECKED_CAST") private fun <T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T
    private fun assertReadyWithoutReceipt(buttons: List<Button>) {
        assertTrue(buttons.single { it.text == "Start fresh 5 minutes" }.isEnabled)
        assertTrue(buttons.single { it.text == "Explicit retained v2 delivery" }.isEnabled)
        assertFalse("Receipt-only must remain independently unavailable without a current receipt",
            buttons.single { it.text == "Serve current fresh receipt only" }.isEnabled)
    }
    private fun await(test: () -> Boolean) {
        repeat(300) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (test()) return; Thread.sleep(10) }
        fail("Receipt worker did not complete")
    }
}
