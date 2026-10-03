package com.hugr.wearos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupBreadcrumbDiagnosticTest {
    @Test
    fun `diagnostic normal path retains the minimum ordered startup boundaries`() {
        assertEquals(
            listOf(
                StartupBreadcrumbStage.ACTIVITY_ONCREATE_ENTER,
                StartupBreadcrumbStage.FIRST_FRAME_LAYOUT_ATTACHED,
                StartupBreadcrumbStage.FIRST_FRAME_PRESENTATION_SET,
                StartupBreadcrumbStage.FIRST_DRAW_OBSERVED,
                StartupBreadcrumbStage.RECORDER_INITIALIZATION_ENTER,
                StartupBreadcrumbStage.BASELINE_INITIALIZATION_ENTER,
                StartupBreadcrumbStage.PERMISSION_PATH_ENTER,
            ),
            StartupBreadcrumbPlan.normalPath,
        )
    }

    @Test
    fun `diagnostic stages expose startup boundaries not HUGR evidence claims`() {
        val names = StartupBreadcrumbStage.entries.joinToString(" ") { it.name }

        assertTrue(names.contains("FIRST_DRAW_OBSERVED"))
        assertTrue(names.contains("RECORDER_INITIALIZATION_FAILURE"))
        assertTrue(names.contains("BASELINE_INITIALIZATION_FAILURE"))
        assertFalse(names.contains("BLE"))
        assertFalse(names.contains("SENSOR"))
        assertFalse(names.contains("EXPORT"))
        assertFalse(names.contains("PHYSIOLOGY"))
    }

    @Test
    fun `Watch57 activity performs passive inspection without launching MainActivity`() {
        val source = diagnosticSource()
        val onCreate = source.indexOf("override fun onCreate(savedInstanceState: Bundle?)")
        val inspect = source.indexOf("RetainedStateInspector.inspect(filesDir)", onCreate)

        assertTrue(onCreate >= 0)
        assertTrue(inspect > onCreate)
        assertFalse(source.contains("startActivity("))
        assertFalse(source.contains("Intent(this, MainActivity::class.java)"))
        assertFalse(source.contains("Button("))
    }

    @Test
    fun `Watch57 viewer reads prior candidate metadata without rewriting it`() {
        val source = diagnosticSource()
        val readRequest = source.indexOf("store.readAnyCandidateLaunchRequest()")
        val readSnapshot = source.indexOf("store.readAnyCandidate()", readRequest)
        val inspection = source.indexOf("RetainedStateInspector.inspect(filesDir)", readSnapshot)

        assertTrue(readRequest >= 0)
        assertTrue(readSnapshot > readRequest)
        assertTrue(inspection > readSnapshot)
        val activitySource = source.substring(source.indexOf("class StartupBreadcrumbDiagnosticActivity"))
        assertFalse(activitySource.contains("recordLaunchRequest("))
        assertFalse(activitySource.contains("store.record("))
    }

    @Test
    fun `passive viewer remains isolated from runtime exporter and state-changing calls`() {
        val source = diagnosticSource().substringAfter("class StartupBreadcrumbDiagnosticActivity")
        listOf(
            "WatchCausalRuntime",
            "WatchSourceRuntime",
            "RetainedTimingExporter",
            "RetainedTimingExportActivity",
            "BleGattService",
            "HealthSensorService",
            "requestPermissions",
            "startService",
            "startForegroundService",
            "sendBroadcast",
            "delete(",
            "renameTo(",
        ).forEach { forbidden ->
            assertFalse("passive viewer must not access $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `Watch57 manifest exposes one unmistakable retained-state label and icon`() {
        val manifest = sourceAt("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        val block = manifest.substringAfter(".StartupBreadcrumbDiagnosticActivity").substringBefore("</activity>")

        assertTrue(block.contains("HUGR Retained State Check"))
        assertTrue(block.contains("@drawable/ic_retained_state_check"))
        assertTrue(block.contains("android.intent.category.LAUNCHER"))
    }

    private fun diagnosticSource(): String = sourceAt(
        "src/main/java/com/hugr/wearos/StartupBreadcrumbDiagnostic.kt",
        "app/src/main/java/com/hugr/wearos/StartupBreadcrumbDiagnostic.kt",
    )

    private fun sourceAt(vararg candidates: String): String =
        candidates.map(::File).first { it.isFile }.readText()
}
