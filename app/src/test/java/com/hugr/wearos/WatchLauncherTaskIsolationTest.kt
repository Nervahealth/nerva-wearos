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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
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
 * Android's ActivityTaskManager task-selection algorithm. [ManifestDocumentLaunchPolicyModel]
 * is therefore an explicitly named, minimal policy simulation: it reads the declared affinity
 * and documentLaunchMode, creates a fresh synthetic document task for `always`, and otherwise
 * restores the matching affinity top. It creates selected production activities through an
 * ActivityController, but is deliberately not a framework shadow. In particular, it makes no
 * claim about a real ATM taskId, physical Wear launcher behavior, hardware, or proof that a
 * stale on-device task is bypassed.
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
     * This is intentionally red until both launcher declarations retain their distinct
     * affinities and declare documentLaunchMode="always". ActivityInfo is retrieved from the
     * PackageManager, not by parsing the source manifest.
     */
    @Test
    fun `merged manifest gives Main and Saved Diagnostics standard distinct document always launcher declarations`() {
        val packageManager = app.packageManager
        val main = activityInfo(packageManager, MAIN_COMPONENT)
        val saved = activityInfo(packageManager, SAVED_COMPONENT)

        assertEquals("Main must retain standard launch mode", ActivityInfo.LAUNCH_MULTIPLE, main.launchMode)
        assertEquals("Saved Diagnostics must retain standard launch mode", ActivityInfo.LAUNCH_MULTIPLE, saved.launchMode)
        assertEquals("com.hugr.wearos.main", main.taskAffinity)
        assertEquals("com.hugr.wearos.diagnostics", saved.taskAffinity)
        assertNotEquals("The two launcher activities must not select one affinity key", main.taskAffinity, saved.taskAffinity)
        assertEquals(
            "Main documentLaunchMode=always must request Android's NEW_DOCUMENT plus MULTIPLE_TASK policy",
            ActivityInfo.DOCUMENT_LAUNCH_ALWAYS,
            main.documentLaunchMode,
        )
        assertEquals(
            "Saved Diagnostics documentLaunchMode=always must request Android's NEW_DOCUMENT plus MULTIPLE_TASK policy",
            ActivityInfo.DOCUMENT_LAUNCH_ALWAYS,
            saved.documentLaunchMode,
        )

        assertEquals(MAIN_COMPONENT, resolveLauncher(packageManager, MAIN_COMPONENT).component)
        assertEquals(SAVED_COMPONENT, resolveLauncher(packageManager, SAVED_COMPONENT).component)
    }

    @Test
    fun `document always model keeps every icon launch on its selected screen through both Home sequences and configuration recreation`() {
        withFixture(FixturePhase.PENDING_STARTED) { fixture ->
            val model = ManifestDocumentLaunchPolicyModel(app.packageManager)
            val before = fixture.snapshot()

            // Exact forward icon sequence: blue Main -> Diagnostics -> Home -> blue Main.
            val blueMainFirst = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(blueMainFirst)
            awaitMainReceiptRead(blueMainFirst)
            assertDocumentSyntheticLaunch(blueMainFirst)

            val diagnosticsFirst = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(diagnosticsFirst)
            assertDocumentSyntheticLaunch(diagnosticsFirst)
            assertDistinctSyntheticTasks(blueMainFirst, diagnosticsFirst)

            model.goHome()

            val blueMainSecond = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(blueMainSecond)
            awaitMainReceiptRead(blueMainSecond)
            assertDocumentSyntheticLaunch(blueMainSecond)
            assertDistinctSyntheticTasks(blueMainFirst, blueMainSecond)

            // Exact reverse sequence: Diagnostics -> blue Main -> Home -> Diagnostics.
            model.goHome()

            val diagnosticsSecond = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(diagnosticsSecond)
            assertDocumentSyntheticLaunch(diagnosticsSecond)
            assertDistinctSyntheticTasks(diagnosticsFirst, diagnosticsSecond)

            val blueMainThird = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(blueMainThird)
            awaitMainReceiptRead(blueMainThird)
            assertDocumentSyntheticLaunch(blueMainThird)
            assertDistinctSyntheticTasks(blueMainSecond, blueMainThird)

            // This is an actual ActivityController configuration lifecycle, not a model-only
            // state change. A later icon launch must still create a new selected Diagnostics UI.
            val blueMainBeforeConfiguration = blueMainThird.activity
            blueMainThird.recreateForConfiguration()
            assertMainScreen(blueMainThird)
            awaitMainReceiptRead(blueMainThird)
            assertNotSame(
                "ActivityController configuration recreation must replace the active MainActivity instance",
                blueMainBeforeConfiguration,
                blueMainThird.activity,
            )

            model.goHome()

            val diagnosticsThird = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(diagnosticsThird)
            assertDocumentSyntheticLaunch(diagnosticsThird)
            assertDistinctSyntheticTasks(diagnosticsSecond, diagnosticsThird)
            assertEquals("Each of the six icon launches must have its own synthetic document task", 6, model.taskKeys().size)

            fixture.assertUnchanged(before)
            model.destroyAll()
            fixture.assertUnchanged(before)
        }
    }

    @Test
    fun `document always passive route preserves actual finalized unacknowledged receipt journal and report`() {
        withFixture(FixturePhase.FINALIZED_UNACKNOWLEDGED) { fixture ->
            val model = ManifestDocumentLaunchPolicyModel(app.packageManager)
            val before = fixture.snapshot()

            val mainFirst = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(mainFirst)
            awaitMainReceiptRead(mainFirst)
            assertDocumentSyntheticLaunch(mainFirst)

            val savedFirst = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(savedFirst)
            assertDocumentSyntheticLaunch(savedFirst)
            assertDistinctSyntheticTasks(mainFirst, savedFirst)
            model.goHome()

            val mainSecond = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(mainSecond)
            awaitMainReceiptRead(mainSecond)
            assertDocumentSyntheticLaunch(mainSecond)
            assertDistinctSyntheticTasks(mainFirst, mainSecond)
            model.goHome()

            val savedSecond = model.launchFromLauncher(SAVED_COMPONENT)
            assertSavedDiagnosticsScreen(savedSecond)
            assertDocumentSyntheticLaunch(savedSecond)
            assertDistinctSyntheticTasks(savedFirst, savedSecond)
            assertEquals(4, model.taskKeys().size)

            fixture.assertUnchanged(before)
            model.destroyAll()
            fixture.assertUnchanged(before)
        }
    }

    @Test
    fun `document always policy bypasses retained pre-update shared tasks without removing them`() {
        withFixture(FixturePhase.PENDING_STARTED) { fixture ->
            val before = fixture.snapshot()

            listOf(
                MAIN_COMPONENT to SAVED_COMPONENT,
                SAVED_COMPONENT to MAIN_COMPONENT,
            ).forEach { (baseRequested, oppositeTop) ->
                val model = ManifestDocumentLaunchPolicyModel(app.packageManager)
                val seeded = model.seedPreUpdateSharedTask(baseRequested, oppositeTop)

                assertEquals(baseRequested, seeded.baseRequested)
                assertEquals(oppositeTop, seeded.top.component)
                assertEquals(LEGACY_SHARED_AFFINITY, seeded.key.declaredAffinity)
                assertEquals(null, seeded.key.documentInstance)
                if (oppositeTop == MAIN_COMPONENT) {
                    assertMainScreen(seeded.top)
                    awaitMainReceiptRead(seeded.top)
                } else {
                    assertSavedDiagnosticsScreen(seeded.top)
                }

                val corrected = model.launchFromLauncher(baseRequested)
                assertEquals("The corrected document launch must create its requested component", baseRequested, corrected.component)
                if (baseRequested == MAIN_COMPONENT) {
                    assertMainScreen(corrected)
                    awaitMainReceiptRead(corrected)
                } else {
                    assertSavedDiagnosticsScreen(corrected)
                }
                assertDocumentSyntheticLaunch(corrected)
                assertDistinctSyntheticTasks(seeded.top, corrected)

                // The seed is an in-memory pre-update base/top example only. Keeping it proves
                // this policy simulation does not clear, reuse, or destroy that retained task.
                assertTrue("Corrected launch must leave the retained pre-update task present", model.containsTask(seeded.key))
                val retainedTop = requireNotNull(model.topFor(seeded.key))
                assertSame("Corrected launch must not replace the retained opposite top", seeded.top.activity, retainedTop.activity)
                assertEquals(oppositeTop, retainedTop.component)

                model.goHome()
                fixture.assertUnchanged(before)
                // Explicit test teardown is separate from the simulated launch policy above.
                model.destroyAll()
                fixture.assertUnchanged(before)
            }
        }
    }

    @Test
    fun `synthetic non-document policy branch retains affinity restoration separately from merged document always contract`() {
        withFixture(FixturePhase.PENDING_STARTED) { fixture ->
            val model = ManifestDocumentLaunchPolicyModel(app.packageManager) {
                ActivityInfo.DOCUMENT_LAUNCH_NONE
            }
            val before = fixture.snapshot()

            val firstMain = model.launchFromLauncher(MAIN_COMPONENT)
            assertMainScreen(firstMain)
            awaitMainReceiptRead(firstMain)
            model.goHome()

            val restoredMain = model.launchFromLauncher(MAIN_COMPONENT)
            assertSame(
                "Only this deliberately forced non-document branch restores the affinity top; the merged manifest is asserted as always above",
                firstMain.activity,
                restoredMain.activity,
            )
            assertEquals(firstMain.key, restoredMain.key)
            assertMainScreen(restoredMain)
            awaitMainReceiptRead(restoredMain)
            assertEquals(1, model.taskKeys().size)

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
     * unit test. The declared affinity models the non-document policy lookup; a non-null
     * [documentInstance] is an in-test identity assigned for each documentLaunchMode=always
     * icon launch. It is a policy-model token, never an Android task id.
     */
    private data class SyntheticTaskKey(
        val declaredAffinity: String,
        val documentInstance: Long? = null,
    )

    private data class ResolvedLauncher(
        val component: ComponentName,
        val info: ActivityInfo,
        val intent: Intent,
    )

    private data class SeededPreUpdateSharedTask(
        val baseRequested: ComponentName,
        val key: SyntheticTaskKey,
        val top: ModelScreen,
    )

    /**
     * Minimal document-launch policy simulation for this test. Metadata comes from the actual
     * PackageManager and each produced screen is a real production ActivityController. The
     * `always` branch gives every icon launch a fresh synthetic document key; the deliberately
     * non-document branch retains the focused affinity-top restoration behavior. This neither
     * shadows Android nor emulates all ActivityTaskManager rules.
     */
    private class ManifestDocumentLaunchPolicyModel(
        private val packageManager: PackageManager,
        private val documentLaunchModeFor: (ActivityInfo) -> Int = { it.documentLaunchMode },
    ) {
        private val tasks = linkedMapOf<SyntheticTaskKey, ModelTask>()
        private var foreground: ModelTask? = null
        private var nextDocumentInstance = 0L

        fun launchFromLauncher(requested: ComponentName): ModelScreen {
            val resolved = resolveLauncher(packageManager, requested)
            val declaredAffinity = requireNotNull(resolved.info.taskAffinity) { "Launcher activity must declare an affinity" }
            val alwaysCreatesNewDocument = documentLaunchModeFor(resolved.info) == ActivityInfo.DOCUMENT_LAUNCH_ALWAYS
            val key = if (alwaysCreatesNewDocument) {
                SyntheticTaskKey(declaredAffinity, ++nextDocumentInstance)
            } else {
                SyntheticTaskKey(declaredAffinity)
            }
            val task = if (alwaysCreatesNewDocument) {
                ModelTask(key).also { tasks[key] = it }
            } else {
                tasks.getOrPut(key) { ModelTask(key) }
            }

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

        fun containsTask(key: SyntheticTaskKey): Boolean = key in tasks

        fun topFor(key: SyntheticTaskKey): ModelScreen? = tasks[key]?.top

        /**
         * Seeds only the test model with a retained pre-update shared-affinity task. Its base
         * requested component and opposite top intentionally expose the old restoration shape;
         * neither represents an ATM task, taskId, or observed device task state.
         */
        fun seedPreUpdateSharedTask(
            baseRequested: ComponentName,
            oppositeTop: ComponentName,
        ): SeededPreUpdateSharedTask {
            require(baseRequested == MAIN_COMPONENT || baseRequested == SAVED_COMPONENT) {
                "Seed base must be one of the two focused launcher components: $baseRequested"
            }
            require(oppositeTop == MAIN_COMPONENT || oppositeTop == SAVED_COMPONENT) {
                "Seed top must be one of the two focused launcher components: $oppositeTop"
            }
            require(baseRequested != oppositeTop) {
                "Pre-update seed must model the requested component with the opposite retained top"
            }
            val key = SyntheticTaskKey(LEGACY_SHARED_AFFINITY)
            check(key !in tasks) { "A pre-update shared task was already seeded" }
            val task = ModelTask(key)
            val top = createActualTop(task, resolveLauncher(packageManager, oppositeTop))
            task.top = top
            tasks[key] = task
            foreground = task
            return SeededPreUpdateSharedTask(baseRequested, key, top)
        }

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
            val changed = android.content.res.Configuration(activity.resources.configuration)
            changed.orientation = if (changed.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controller.configurationChange(changed)
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
        const val LEGACY_SHARED_AFFINITY = "com.hugr.wearos"

        /** Pure policy-model assertions: synthetic document instance tokens are not task ids. */
        fun assertDocumentSyntheticLaunch(screen: ModelScreen) {
            assertTrue(
                "documentLaunchMode=always policy must give each icon launch a synthetic document token",
                screen.key.documentInstance != null,
            )
        }

        /** Pure policy-model assertion; it makes no claim about Android ActivityTaskManager ids. */
        fun assertDistinctSyntheticTasks(first: ModelScreen, second: ModelScreen) {
            assertNotEquals(
                "Separate icon launches must use separate synthetic policy keys",
                first.key,
                second.key,
            )
            assertNotSame(
                "Separate icon launches must create distinct actual ActivityController instances",
                first.activity,
                second.activity,
            )
        }

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
