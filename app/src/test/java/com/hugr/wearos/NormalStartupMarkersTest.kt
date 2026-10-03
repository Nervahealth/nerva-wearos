package com.hugr.wearos

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalStartupMarkersTest {
    @Test
    fun `durable stages span UI recovery permission service and health foreground boundaries`() {
        val stages = NormalStartupStage.entries.toSet()

        listOf(
            NormalStartupStage.UI_REACHED,
            NormalStartupStage.FIRST_DRAW_OBSERVED,
            NormalStartupStage.FRESH_SCOPE_DEFERRED,
            NormalStartupStage.FRESH_SCOPE_PREPARING,
            NormalStartupStage.FRESH_SCOPE_READY,
            NormalStartupStage.FRESH_SCOPE_FAILED,
            NormalStartupStage.RECOVERY_DEFERRED,
            NormalStartupStage.RECOVERY_IN_PROGRESS,
            NormalStartupStage.JOURNAL_READY,
            NormalStartupStage.JOURNAL_FAILED,
            NormalStartupStage.RECOVERY_READY_HOLD,
            NormalStartupStage.PERMISSION_PATH_ENTER,
            NormalStartupStage.PERMISSION_DENIED,
            NormalStartupStage.SERVICE_START_REQUESTED,
            NormalStartupStage.HEALTH_ON_START_ENTER,
            NormalStartupStage.HEALTH_FOREGROUND_STARTED,
            NormalStartupStage.BOUNDED_RUN_STARTED,
            NormalStartupStage.BOUNDED_RUN_SAMPLE_SEEN,
            NormalStartupStage.BOUNDED_RUN_STOP_REQUESTED,
            NormalStartupStage.BOUNDED_RUN_FINALIZED,
            NormalStartupStage.BOUNDED_RUN_FINALIZATION_FAILED,
            NormalStartupStage.BOUNDED_RUN_DELIVERY_ACKNOWLEDGED,
            NormalStartupStage.BOUNDED_RUN_GATT_STOP_REQUESTED,
        ).forEach { required -> assertTrue("missing $required", required in stages) }
    }

    @Test
    fun `marker store is metadata only and excludes retained-root operations`() {
        val source = sourceAt("NormalStartupMarkers.kt")
        listOf(
            "build45_source_journal",
            "exact_range_readbacks",
            "WatchSourceRuntime",
            "SourceJournal",
            "RetainedTimingExporter",
            "BleGattService",
            "delete(",
            "renameTo(",
            "FileOutputStream",
            "readBytes",
        ).forEach { forbidden ->
            assertFalse("marker store must exclude $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("getSharedPreferences"))
        assertTrue(source.contains("commit()"))
    }

    @Test
    fun `normal startup markers remain outside explicit egress activation`() {
        val main = sourceAt("MainActivity.kt")
        val health = sourceAt("HealthSensorService.kt")

        assertTrue(main.contains("NormalStartupMarkerStore"))
        assertTrue(health.contains("NormalStartupMarkerStore"))
        assertFalse(main.contains("ACTION_START_EGRESS_ONLY"))
        assertFalse(health.contains("ACTION_START_EGRESS_ONLY"))
    }

    @Test
    fun `marker viewer is a launcher-visible passive metadata read only`() {
        val viewer = sourceAt("NormalStartupMarkerActivity.kt")
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.isFile }.readText()

        assertTrue(viewer.contains("NormalStartupMarkerStore"))
        listOf(
            "WatchSourceRuntime",
            "SourceJournal",
            "RetainedStateInspector",
            "startService(",
            "startForegroundService(",
            "requestPermissions(",
            "Thread {",
        ).forEach { forbidden ->
            assertFalse("marker viewer must exclude $forbidden", viewer.contains(forbidden))
        }
        assertTrue(manifest.contains(".NormalStartupMarkerActivity"))
        assertTrue(manifest.contains("android:label=\"HUGR Startup Readiness\""))
    }

    private fun sourceAt(fileName: String): String = listOf(
        File("src/main/java/com/hugr/wearos/$fileName"),
        File("app/src/main/java/com/hugr/wearos/$fileName"),
    ).first { it.isFile }.readText()
}
