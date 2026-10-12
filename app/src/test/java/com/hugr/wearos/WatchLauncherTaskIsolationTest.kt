package com.hugr.wearos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.ShadowSystemClock

/**
 * Launcher-task isolation regression for the Watch Main and Saved Diagnostics icons.
 *
 * The PackageManager assertions read Robolectric's manifest-derived ActivityInfo, while the
 * activities and their views are real production activities. Robolectric does not implement
 * Android's ActivityTaskManager task-selection algorithm. [ManifestNewTaskRestorationModel] is
 * therefore an explicitly named, minimal in-test model of NEW_TASK affinity restoration: it
 * derives its key from the actual declared ActivityInfo.taskAffinity, restores only that key's
 * top activity, and creates the selected production activity through an ActivityController when
 * there is no matching top. It is deliberately not a framework shadow and never claims a real
 * Android taskId or physical Wear launcher result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FreshRunDirectoryLinuxShadow::class])
class WatchLauncherTaskIsolationTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun before() {
        FreshRunRuntime.end()
        ShadowSystemClock.reset()
        FreshRunRuntime.receiptRoot(app).deleteRecursively()
        WatchDiagnosticRuntime.root(app).deleteRecursively()
        ShadowStatFs.registerStats(app.noBackupFilesDir, 4_096, 1_000_000, 1_000_000)
        assertTrue(Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 6_934))
    }

    @After
    fun after() {
        // The production singleton is process scoped. Test cleanup intentionally removes only
        // the disposable Robolectric app roots created by this test.
        FreshRunRuntime.end()
        ShadowStatFs.unregisterStats(app.noBackupFilesDir)
        FreshRunRuntime.receiptRoot(app).deleteRecursively()
        WatchDiagnosticRuntime.root(app).deleteRecursively()
        ShadowSystemClock.reset()
    }

    /**
     * This is intentionally red until the parent applies only the two explicit affinities.
     * ActivityInfo is retrieved from the PackageManager, not by parsing the source manifest.
     */
    @Test
    fun `merged manifest gives Main and Saved Diagnostics distinct standard launcher affinities`() {
        val packageManager = app.packageManager
        val main = activityInfo(packageManager, MAIN_COMPONENT)
        val saved = activityInfo(packageManager, SAVED_COMPONENT)

        assertEquals("Main must retain standard launch mode", ActivityInfo.LAUNCH_MULTIPLE, main.launchMode)
        assertEquals("Saved Diagnostics must retain standard launch mode", ActivityInfo.LAUNCH_MULTIPLE, saved.launchMode)
        assertEquals("com.hugr.wearos.main", main.taskAffinity)
        assertEquals("com.hugr.wearos.diagnostics", saved.taskAffinity)
        assertNotEquals("The two launcher activities must not select one affinity key", main.taskAffinity, saved.taskAffinity)

        assertEquals(MAIN_COMPONENT, resolveLauncher(packageManager, MAIN_COMPONENT).component)
        assertEquals(SAVED_COMPONENT, resolveLauncher(packageManager, SAVED_COMPONENT).component)
    }

    @Test
    fun `manifest driven NEW_TASK model returns the intended launcher after Home without touching pending admission or saved report`() {
        withFixture(FixturePhase.PENDING_STARTED) { fixture ->
            val model = ManifestNewTaskRestorationModel(app.packageManager)
            val before = fixture.snapshot()

            // Information/Saved is the existing top; after Home, the blue Main icon must select
            // Main's own declared-affinity task rather than restore this Saved top.
            val savedFirst = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(savedFirst)
            assertNoPassiveDispatch(savedFirst)
            model.goHome()

            val mainFirst = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(mainFirst)
            awaitMainReceiptRead(mainFirst)
            assertNoPassiveDispatch(mainFirst)
            assertNotEquals(mainFirst.key, savedFirst.key)
            assertEquals(setOf(mainFirst.key, savedFirst.key), model.taskKeys())

            // Reverse the launcher order twice. These are warm foreground restorations of the
            // task tops created above, not clear-task, singleTask, or task-id based behavior.
            model.goHome()
            val savedWarm = model.launchFromLauncher(SAVED_COMPONENT)
            assertSame(savedFirst.activity, savedWarm.activity)
            assertSavedDiagnosticsScreen(savedWarm)
            assertNoPassiveDispatch(savedWarm)
            model.goHome()

            val mainWarm = model.launchFromLauncher(MAIN_COMPONENT)
            assertSame(mainFirst.activity, mainWarm.activity)
            assertMainScreen(mainWarm)
            awaitMainReceiptRead(mainWarm)
            assertNoPassiveDispatch(mainWarm)

            // Configuration recreation is an actual ActivityController lifecycle path. The
            // restored top after its next Home/re-entry remains the recreated Main screen.
            mainWarm.recreateForConfiguration()
            assertMainScreen(mainWarm)
            awaitMainReceiptRead(mainWarm)
            val recreatedMain = mainWarm.activity
            model.goHome()
            val mainAfterReentry = model.launchFromLauncher(MAIN_COMPONENT)
            assertSame(recreatedMain, mainAfterReentry.activity)
            assertMainScreen(mainAfterReentry)
            awaitMainReceiptRead(mainAfterReentry)
            assertNoPassiveDispatch(mainAfterReentry)

            fixture.assertUnchanged(before)
            model.destroyAll()
            fixture.assertUnchanged(before)
        }
    }

    @Test
    fun `same passive launcher route preserves actual finalized unacknowledged receipt journal and report`() {
        withFixture(FixturePhase.FINALIZED_UNACKNOWLEDGED) { fixture ->
            val model = ManifestNewTaskRestorationModel(app.packageManager)
            val before = fixture.snapshot()

            val main = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(main)
            awaitMainReceiptRead(main)
            assertNoPassiveDispatch(main)
            model.goHome()

            val saved = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(saved)
            assertNoPassiveDispatch(saved)
            assertNotEquals(main.key, saved.key)
            model.goHome()

            val mainWarm = model.launchFromLauncher(MAIN_COMPONENT)
            assertSame(main.activity, mainWarm.activity)
            assertMainScreen(mainWarm)
            awaitMainReceiptRead(mainWarm)
            assertNoPassiveDispatch(mainWarm)

            fixture.assertUnchanged(before)
            model.destroyAll()
            fixture.assertUnchanged(before)
        }
    }

    private fun withFixture(phase: FixturePhase, block: (RuntimeFixture) -> Unit) {
        val fixture = RuntimeFixture(app, phase)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    /** Real FreshRunRuntime admission and real runtime report persistence in disposable app roots. */
    private class RuntimeFixture(private val context: Context, phase: FixturePhase) : AutoCloseable {
        private val session = FreshRunRuntime.admit(context)
        private val receiptRoot = FreshRunRuntime.receiptRoot(context)
        private val reportStore = WatchDiagnosticRuntime.store(context)

        init {
            if (phase == FixturePhase.FINALIZED_UNACKNOWLEDGED) {
                assertTrue(session.begin())
                session.scope(31, 31)
                SourceStreamCode.entries.forEach { stream ->
                    assertTrue(session.append(stream, System.currentTimeMillis(), byteArrayOf(1, 2, 3, 4)) != null)
                }
                ShadowSystemClock.advanceBy(300_000L, TimeUnit.MILLISECONDS)
                session.finalizeRecording()
                val finalized = session.snapshot()
                assertEquals(FreshRunPhase.FINALIZED, finalized.phase)
                assertTrue(finalized.manifests.isNotEmpty())
                assertTrue(finalized.acks.isEmpty())
            } else {
                assertEquals(FreshRunPhase.STARTED, session.snapshot().phase)
            }

            // Save through the production runtime and production SavedDiagnosticStore. The viewer
            // below reads this report through SavedDiagnosticActivity's actual onCreate path.
            WatchDiagnosticRuntime.begin(context, null, null)
            WatchDiagnosticRuntime.capture(context, DiagnosticStage.STARTUP_SELECTION, DiagnosticOutcome.OTHER_STOP)
            assertEquals(1, reportStore.reports().size)
        }

        fun snapshot(): RuntimeSnapshot {
            val receipt = requireNotNull(FreshRunRuntime.current(context)).bytes()
            val journalFiles = fileDigests(File(receiptRoot, "${session.runId}/source"))
            assertTrue("Preservation must hash actual production journal files", journalFiles.isNotEmpty())
            return RuntimeSnapshot(
                session = requireNotNull(FreshRunRuntime.session),
                receipt = receipt,
                journalLastRecordIndex = session.journal.latestRecordIndex(),
                journalFiles = journalFiles,
                reports = reportStore.reports().associate { it.name to it.readBytes() },
            )
        }

        fun assertUnchanged(before: RuntimeSnapshot) {
            assertSame("Passive launcher activity must not replace or end the admitted session", before.session, FreshRunRuntime.session)
            assertArrayEquals("Passive launcher activity must not rewrite the receipt", before.receipt, requireNotNull(FreshRunRuntime.current(context)).bytes())
            assertEquals("Passive launcher activity must not append or remove journal records", before.journalLastRecordIndex, session.journal.latestRecordIndex())
            assertEquals("Passive launcher activity must not alter journal files", before.journalFiles, fileDigests(File(receiptRoot, "${session.runId}/source")))

            val afterReports = reportStore.reports().associate { it.name to it.readBytes() }
            assertEquals("Passive navigation must not add, remove, or rotate a saved report", before.reports.keys, afterReports.keys)
            before.reports.forEach { (name, bytes) ->
                assertArrayEquals("Passive navigation must not rewrite saved report $name", bytes, requireNotNull(afterReports[name]))
            }
        }

        override fun close() {
            FreshRunRuntime.end()
        }
    }

    private data class RuntimeSnapshot(
        val session: FreshRunSession,
        val receipt: ByteArray,
        val journalLastRecordIndex: Long,
        val journalFiles: Map<String, String>,
        val reports: Map<String, ByteArray>,
    )

    private enum class FixturePhase { PENDING_STARTED, FINALIZED_UNACKNOWLEDGED }

    /**
     * Synthetic key, not Activity.getTaskId(): Android task ids are intentionally outside this
     * unit test. The key is only the declared affinity that ActivityTaskManager would use for a
     * minimal NEW_TASK affinity lookup.
     */
    private data class SyntheticTaskKey(val declaredAffinity: String)

    private data class ResolvedLauncher(
        val component: ComponentName,
        val info: ActivityInfo,
        val intent: Intent,
    )

    /**
     * Minimal model of NEW_TASK affinity restoration for this test. Its metadata comes from the
     * actual PackageManager and every produced screen is a real production ActivityController.
     * It neither shadows Android nor pretends to emulate all ActivityTaskManager rules.
     */
    private class ManifestNewTaskRestorationModel(private val packageManager: PackageManager) {
        private val tasks = linkedMapOf<SyntheticTaskKey, ModelTask>()
        private var foreground: ModelTask? = null

        fun launchFromLauncher(requested: ComponentName): ModelScreen {
            val resolved = resolveLauncher(packageManager, requested)
            val key = SyntheticTaskKey(requireNotNull(resolved.info.taskAffinity) { "Launcher activity must declare an affinity" })
            val task = tasks.getOrPut(key) { ModelTask(key) }

            if (foreground !== task) foreground?.top?.moveHome()
            foreground = task
            val top = task.top ?: createActualTop(task, resolved).also { task.top = it }
            top.foreground()
            return top
        }

        fun goHome() {
            foreground?.top?.moveHome()
            foreground = null
        }

        fun taskKeys(): Set<SyntheticTaskKey> = tasks.keys

        fun destroyAll() {
            foreground = null
            tasks.values.forEach { task -> runCatching { task.top?.destroy() } }
            tasks.clear()
        }

        private fun createActualTop(task: ModelTask, resolved: ResolvedLauncher): ModelScreen = when (resolved.component) {
            MAIN_COMPONENT -> MainModelScreen(
                task.key,
                Robolectric.buildActivity(MainActivity::class.java, resolved.intent).create().start().resume(),
            )
            SAVED_COMPONENT -> SavedModelScreen(
                task.key,
                Robolectric.buildActivity(SavedDiagnosticActivity::class.java, resolved.intent).create().start().resume(),
            )
            else -> error("Only the two launcher components are admitted to this focused model: ${resolved.component}")
        }

        private class ModelTask(val key: SyntheticTaskKey) {
            var top: ModelScreen? = null
        }
    }

    private sealed class ModelScreen {
        abstract val key: SyntheticTaskKey
        abstract val component: ComponentName
        abstract val activity: android.app.Activity
        abstract fun foreground()
        abstract fun moveHome()
        abstract fun recreateForConfiguration()
        abstract fun destroy()
    }

    private class MainModelScreen(
        override val key: SyntheticTaskKey,
        private val controller: ActivityController<MainActivity>,
    ) : ModelScreen() {
        override val component: ComponentName = MAIN_COMPONENT
        override val activity: MainActivity get() = controller.get()
        private var stopped = false

        override fun foreground() {
            if (stopped) controller.restart().start().resume()
            stopped = false
        }

        override fun moveHome() {
            if (!stopped) controller.pause().stop()
            stopped = true
        }

        override fun recreateForConfiguration() {
            controller.configurationChange()
            stopped = false
        }

        override fun destroy() {
            controller.destroy()
        }
    }

    private class SavedModelScreen(
        override val key: SyntheticTaskKey,
        private val controller: ActivityController<SavedDiagnosticActivity>,
    ) : ModelScreen() {
        override val component: ComponentName = SAVED_COMPONENT
        override val activity: SavedDiagnosticActivity get() = controller.get()
        private var stopped = false

        override fun foreground() {
            if (stopped) controller.restart().start().resume()
            stopped = false
        }

        override fun moveHome() {
            if (!stopped) controller.pause().stop()
            stopped = true
        }

        override fun recreateForConfiguration() {
            controller.configurationChange()
            stopped = false
        }

        override fun destroy() {
            controller.destroy()
        }
    }

    private fun activityInfo(packageManager: PackageManager, component: ComponentName): ActivityInfo =
        packageManager.getActivityInfo(component, 0)

    private fun assertMainScreen(screen: ModelScreen) {
        assertEquals(MAIN_COMPONENT, screen.component)
        assertTrue("Expected actual MainActivity, got ${screen.activity.javaClass.name}", screen.activity is MainActivity)
        assertTrue("Main screen did not attach its actual HUGR layout: ${screenText(screen.activity)}", screenText(screen.activity).any { it.contains("HUGR") })
    }

    private fun assertSavedDiagnosticsScreen(screen: ModelScreen) {
        assertEquals(SAVED_COMPONENT, screen.component)
        assertTrue("Expected actual SavedDiagnosticActivity, got ${screen.activity.javaClass.name}", screen.activity is SavedDiagnosticActivity)
        val text = screenText(screen.activity)
        assertTrue("Saved diagnostics heading missing: $text", text.any { it.contains("HUGR Saved Diagnostics") })
        assertTrue("Saved report was not read through the actual viewer: $text", text.any { it.contains("HUGR SAVED DIAGNOSTIC v1") })
        assertTrue("Share control should be present but never clicked: $text", text.any { it.contains("Share this diagnostic") })
    }

    private fun assertNoPassiveDispatch(screen: ModelScreen) {
        val shadow = Shadows.shadowOf(screen.activity)
        assertNull("Passive navigation must not request permissions", shadow.lastRequestedPermission)
        assertNull("Passive navigation must not start a service", shadow.nextStartedService)
        assertNull("Passive navigation must not launch Share or another activity", shadow.peekNextStartedActivityForResult())
    }

    private fun awaitMainReceiptRead(screen: ModelScreen) {
        if (screen !is MainModelScreen) return
        repeat(200) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val pending = screen.activity.javaClass.getDeclaredField("freshReadPending").let { field ->
                field.isAccessible = true
                field.getBoolean(screen.activity)
            }
            if (!pending) return
            Thread.sleep(5)
        }
        throw AssertionError("MainActivity passive receipt read did not complete")
    }

    private fun screenText(activity: android.app.Activity): List<String> = collectText(activity.window.decorView)

    private fun collectText(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(collectText(view.getChildAt(it))) }
    }

    private companion object {
        val MAIN_COMPONENT = ComponentName("com.hugr.wearos", MainActivity::class.java.name)
        val SAVED_COMPONENT = ComponentName("com.hugr.wearos", SavedDiagnosticActivity::class.java.name)

        fun resolveLauncher(packageManager: PackageManager, requested: ComponentName): ResolvedLauncher {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(requested)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val info = requireNotNull(intent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY)) {
                "PackageManager did not resolve launcher $requested"
            }
            return ResolvedLauncher(ComponentName(info.packageName, info.name), info, intent)
        }

        fun fileDigests(root: File): Map<String, String> =
            if (!root.exists()) emptyMap() else root.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(root).path }.associate {
                it.relativeTo(root).path to FreshRunReceipt.digest(it.readBytes())
            }
    }
}
