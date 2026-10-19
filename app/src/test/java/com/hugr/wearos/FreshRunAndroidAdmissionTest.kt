package com.hugr.wearos

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowStatFs

/**
 * Android-facing regression only. The user-zero alias fixture is a disposable Linux analogue of
 * Android app storage; it is not a device durability or physical-storage claim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FreshRunDirectoryLinuxShadow::class])
class FreshRunAndroidAdmissionTest {
    private class AliasNoBackupContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getNoBackupFilesDir(): File = directory
    }

    /** Synthetic /data/user/0 -> /data/data alias with a real canonical no_backup directory. */
    private class AndroidAliasFixture(base: Context) : AutoCloseable {
        private val fixture = Files.createTempDirectory("fresh-android-admission-").toFile()
        private val dataData = File(fixture, "data/data").apply { check(mkdirs()) }
        private val user = File(fixture, "data/user").apply { check(mkdirs()) }
        val canonicalNoBackup = File(dataData, "${base.packageName}/no_backup").apply { check(mkdirs()) }
        val aliasedNoBackup: File
        val context: Context

        init {
            Files.createSymbolicLink(File(user, "0").toPath(), dataData.toPath())
            aliasedNoBackup = File(user, "0/${base.packageName}/no_backup")
            check(aliasedNoBackup.isDirectory)
            // Production admission calls StatFs on the Context-provided alias. This test fixture
            // provides the required capacity through Robolectric, not through a fake journal.
            ShadowStatFs.registerStats(aliasedNoBackup, 4_096, 1_000_000, 1_000_000)
            context = AliasNoBackupContext(base, aliasedNoBackup)
        }

        override fun close() {
            ShadowStatFs.unregisterStats(aliasedNoBackup)
            fixture.deleteRecursively()
        }
    }

    @Before
    fun before() {
        // The production singleton is intentionally process scoped; each test leaves it terminal.
        FreshRunRuntime.end()
    }

    @After
    fun after() {
        FreshRunRuntime.end()
    }

    @Test
    fun `production admission canonicalizes only trusted user-zero anchor and survives restart with same UUID`() {
        AndroidAliasFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            assertTrue(Settings.Global.putInt(fixture.context.contentResolver, Settings.Global.BOOT_COUNT, 77))

            val root = FreshRunRuntime.receiptRoot(fixture.context)
            assertEquals(File(fixture.canonicalNoBackup, FreshRunRuntime.DIRECTORY).canonicalFile, root.canonicalFile)
            assertNotEquals(File(fixture.aliasedNoBackup, FreshRunRuntime.DIRECTORY).absoluteFile, root.absoluteFile)

            val admitted = FreshRunRuntime.admit(fixture.context)
            val initial = admitted.snapshot()
            assertEquals(initial.runId, FreshRunRuntime.current(fixture.context)?.runId)
            assertEquals(77, initial.bootCount)
            assertEquals(0L, admitted.journal.latestRecordIndex())
            assertNull(admitted.append(SourceStreamCode.DEVICE_HEALTH, System.currentTimeMillis(), byteArrayOf(1)))
            assertEquals(0L, admitted.journal.latestRecordIndex())
            assertTrue(admitted.begin())
            admitted.scope(31, 31)
            assertEquals(2L, FreshRunRuntime.current(fixture.context)!!.revision)
            assertEquals(31, admitted.snapshot().startedMask)
            assertEquals(0L, admitted.journal.latestRecordIndex())

            // end() models process teardown: it writes the terminal interrupted receipt then clears
            // the in-memory session. current() must reopen that same selected receipt from storage.
            FreshRunRuntime.end()
            val restarted = requireNotNull(FreshRunRuntime.current(fixture.context))
            assertEquals(initial.runId, restarted.runId)
            assertEquals(initial.sourceId, restarted.sourceId)
            assertEquals(FreshRunPhase.FAILED, restarted.phase)
            assertEquals("SERVICE_INTERRUPTED", restarted.failure)
        }
    }

    @Test
    fun `direct no-backup or package symlink is not a trusted alias`() {
        AndroidAliasFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            val directNoBackup = File(fixture.canonicalNoBackup.parentFile, "no_backup_alias")
            Files.createSymbolicLink(directNoBackup.toPath(), fixture.canonicalNoBackup.toPath())
            failsClosed {
                FreshRunRuntime.receiptRoot(AliasNoBackupContext(RuntimeEnvironment.getApplication(), directNoBackup))
            }

            val linkedApp = File(fixture.canonicalNoBackup.parentFile.parentFile, "linked_app")
            Files.createSymbolicLink(linkedApp.toPath(), fixture.canonicalNoBackup.parentFile.toPath())
            failsClosed {
                FreshRunRuntime.receiptRoot(AliasNoBackupContext(RuntimeEnvironment.getApplication(), File(linkedApp, "no_backup")))
            }
        }
    }

    @Test
    fun `selected pending corrupt and symlink children fail closed without fallback`() {
        listOf("PENDING", "CORRUPT_CURRENT", "SYMLINK_CURRENT", "SYMLINK_ROOT").forEach { case ->
            AndroidAliasFixture(RuntimeEnvironment.getApplication()).use { fixture ->
                assertTrue(Settings.Global.putInt(fixture.context.contentResolver, Settings.Global.BOOT_COUNT, 88))
                val admitted = FreshRunRuntime.admit(fixture.context)
                val selected = admitted.snapshot()
                FreshRunRuntime.end()
                assertEquals(selected.runId, FreshRunRuntime.current(fixture.context)?.runId)

                val root = FreshRunRuntime.receiptRoot(fixture.context)
                val outside = Files.createTempDirectory("fresh-android-outside-").toFile()
                try {
                    when (case) {
                        "PENDING" -> File(root, "selection_pending").writeText("publication interrupted")
                        "CORRUPT_CURRENT" -> File(root, "current").writeText("not an envelope")
                        "SYMLINK_CURRENT" -> {
                            val pointer = File(root, "current")
                            check(pointer.delete())
                            Files.createSymbolicLink(pointer.toPath(), File(outside, "current").toPath())
                        }
                        "SYMLINK_ROOT" -> {
                            check(root.deleteRecursively())
                            Files.createSymbolicLink(root.toPath(), outside.toPath())
                        }
                    }
                    failsClosed { FreshRunRuntime.current(fixture.context) }
                } finally {
                    outside.deleteRecursively()
                }
            }
        }
    }

    @Test
    fun `asynchronous receipt read failure latches real layout and blocks repeated fresh actions`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val root = FreshRunRuntime.receiptRoot(activity)
        val outside = Files.createTempDirectory("fresh-main-root-link-").toFile()
        try {
            root.parentFile?.mkdirs()
            root.deleteRecursively()
            Files.createSymbolicLink(root.toPath(), outside.toPath())

            invoke(activity, "renderEvidence")
            waitForUi("fresh receipt read failure") { booleanField(activity, "freshTerminalFailure") }

            val status = field<TextView>(activity, "statusText")
            val buttons = field<MutableList<Button>>(activity, "freshActionButtons")
            assertTrue(status.text.toString().contains("Fresh receipt unavailable / interrupted IO · no completion"))
            assertTrue(status.text.toString().contains("STOP · no retry"))
            assertFalse(booleanField(activity, "freshReadPending"))
            assertEquals(4, buttons.size)
            assertEquals(
                setOf(
                    "Start fresh 5 minutes",
                    "Start selected ladder duration",
                    "Serve current fresh receipt only",
                    "Explicit retained v2 delivery",
                ),
                buttons.map { it.text.toString() }.toSet(),
            )
            assertTrue(buttons.all { !it.isEnabled })

            val before = status.text.toString()
            buttons.forEach { button -> assertTrue(button.performClick()) }
            assertFalse(booleanField(activity, "freshRequested"))
            assertEquals(before, status.text.toString())

            // Re-render after terminal failure must not schedule a second read or overwrite the STOP.
            invoke(activity, "renderEvidence")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before, status.text.toString())
            assertFalse(booleanField(activity, "freshReadPending"))
        } finally {
            controller.destroy()
            Files.deleteIfExists(root.toPath())
            outside.deleteRecursively()
        }
    }

    @Test
    fun `pending actual receipt read prevents a tap from admitting a new session`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            // The real current() worker blocks on its runtime monitor until the UI action is tested.
            synchronized(FreshRunRuntime) {
                invoke(activity, "renderEvidence")
                assertTrue(booleanField(activity, "freshReadPending"))
                val buttons = field<MutableList<Button>>(activity, "freshActionButtons")
                assertTrue(buttons.all { !it.isEnabled })
                buttons.forEach { it.performClick() }
                assertFalse(booleanField(activity, "freshRequested"))
                assertFalse(booleanField(activity, "freshAdmissionScheduled"))
                assertEquals(BoundedNormalStartupGate.Phase.NOT_STARTED,
                    field<BoundedNormalStartupGate>(activity, "startupRecoveryGate").snapshot())
                assertNull(FreshRunRuntime.session)
            }
            waitForUi("blocked receipt read completion") { !booleanField(activity, "freshReadPending") }
            assertFalse(booleanField(activity, "freshRequested"))
            assertNull(FreshRunRuntime.session)
        } finally { controller.destroy() }
    }

    @Test
    fun `terminal latch contains the concrete stale ready gate and false-request second press`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            val gate = field<BoundedNormalStartupGate>(activity, "startupRecoveryGate")
            gate.deferFreshScope()
            gate.beginFreshScope()
            gate.markFreshScopeReady()
            assertTrue(gate.permitsServiceStart())
            assertFalse(booleanField(activity, "freshRequested"))

            // This is the pre-correction second-click transition: a reset request flag would
            // re-enter deferFreshScope and throw because the gate is already READY.
            failsClosed { gate.deferFreshScope() }

            invokeWithString(activity, "latchFreshFailure", "Fresh admission failed · no sensing")
            val buttons = field<MutableList<Button>>(activity, "freshActionButtons")
            assertTrue(booleanField(activity, "freshTerminalFailure"))
            assertTrue(buttons.all { !it.isEnabled })
            assertTrue(buttons.first { it.text == "Start fresh 5 minutes" }.performClick())
            assertEquals(BoundedNormalStartupGate.Phase.FRESH_SCOPE_READY, gate.snapshot())
            assertFalse(booleanField(activity, "freshRequested"))
        } finally {
            controller.destroy()
        }
    }

    private fun failsClosed(block: () -> Unit) {
        try {
            block()
            fail("Expected production storage path to fail closed")
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
    }

    private fun waitForUi(description: String, predicate: () -> Boolean) {
        repeat(100) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for $description")
    }

    private fun invoke(target: Any, name: String) {
        val method: Method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(target)
    }

    private fun invokeWithString(target: Any, name: String, value: String) {
        val method = target.javaClass.getDeclaredMethod(name, String::class.java)
        method.isAccessible = true
        method.invoke(target, value)
    }

    private fun booleanField(target: Any, name: String): Boolean = field(target, name)

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }
}
