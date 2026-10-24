package com.hugr.wearos

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline source-bound tests for the diagnostic window only. They exercise no Bluetooth hardware,
 * radio, package build, APK, DEX, Gradle, retained production records, or wire protocol.
 */
class DiagnosticIncidentWindowTest {
    private class Clock(private var value: Long = 1_000_000_000L) {
        fun now(): Long = value
        fun advance(ms: Long) { value += ms * 1_000_000L }
    }

    private fun enter(
        window: WatchIncidentDiagnosticWindow,
        requestId: Int,
        offset: Int = 0,
        page: Long? = null,
        kind: IncidentOperationKind = IncidentOperationKind.R1_WRITE,
    ): IncidentOperationToken? = window.enter(
        kind = kind,
        access = IncidentAccess.WRITE,
        nativeRequestId = requestId,
        characteristicInstanceId = 7,
        r1PageIndex = page,
        attOffset = offset,
    )

    private fun settle(
        window: WatchIncidentDiagnosticWindow,
        token: IncidentOperationToken?,
        clock: Clock,
        outcome: IncidentResponseOutcome = IncidentResponseOutcome.TRUE,
        status: Int = 0,
        length: Int = 0,
    ) {
        window.preResponse(token, status, length)
        window.settle(token, outcome, status, length, clock.now())
    }

    @Test fun `timely true stays retained in local rows but does not freeze or claim cross device correlation`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        window.onConnected()
        val token = enter(window, requestId = 18, page = 0)
        clock.advance(9_999)
        settle(window, token, clock)
        val snapshot = window.snapshot()
        assertFalse(snapshot.frozen)
        assertEquals(1, snapshot.rows.size)
        assertEquals(IncidentResponseOutcome.TRUE, snapshot.rows.single().responseOutcome)
        val rendered = IncidentDiagnosticFormat.render(snapshot)
        assertTrue(rendered.contains("nativeRequestIdScope=WATCH_LOCAL_ONLY_NOT_CROSS_DEVICE_KEY"))
        assertTrue(rendered.contains("CORRELATION_UNRESOLVED"))
        assertTrue(rendered.contains("late TRUE is Android platform response acceptance only; it is not Phone delivery or Phone acceptance"))
        assertFalse(rendered.contains("payload="))
    }

    @Test fun `false null and late true freeze first decisive row while preserving prior timely rows and late settlement`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        window.onConnected()
        val timely = enter(window, 1, page = 0)
        clock.advance(1)
        settle(window, timely, clock)
        val falseToken = enter(window, 2, page = 1)
        settle(window, falseToken, clock, IncidentResponseOutcome.FALSE, status = 0, length = 0)
        val late = enter(window, 3, page = 2)
        clock.advance(10_000)
        settle(window, late, clock, IncidentResponseOutcome.TRUE, status = 0, length = 4)
        val nullToken = enter(window, 4, page = 3)
        settle(window, nullToken, clock, IncidentResponseOutcome.NULL, status = 257, length = 0)
        val snapshot = window.snapshot()
        assertTrue(snapshot.frozen)
        assertEquals(2L, snapshot.decisiveOrdinal)
        assertEquals(listOf(1L, 2L, 3L, 4L), snapshot.rows.map { it.ordinal })
        assertEquals(IncidentResponseOutcome.TRUE, snapshot.rows[0].responseOutcome)
        assertEquals(IncidentResponseOutcome.FALSE, snapshot.rows[1].responseOutcome)
        assertEquals(IncidentResponseOutcome.TRUE, snapshot.rows[2].responseOutcome)
        assertEquals(IncidentResponseOutcome.NULL, snapshot.rows[3].responseOutcome)
        val report = IncidentDiagnosticFormat.render(snapshot)
        assertTrue(report.contains("freezeReason=FIRST_DECISIVE_CALLBACK_OUTCOME"))
        assertTrue(report.contains("delayMs=10000"))
    }

    @Test fun `repeated offsets and request ids remain distinct local rows and are not joined across epochs`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        window.onConnected()
        val first = enter(window, requestId = 5, offset = 0, page = 0)
        settle(window, first, clock)
        val initial = window.snapshot()
        window.onConnected()
        val second = enter(window, requestId = 5, offset = 0, page = 0)
        settle(window, second, clock)
        val current = window.snapshot()
        assertEquals(2, current.rows.size)
        assertEquals(listOf(5, 5), current.rows.map { it.nativeRequestId })
        assertEquals(listOf(0L, 0L), current.rows.map { it.r1PageIndex })
        assertTrue(initial.connectionEpoch != current.connectionEpoch)
        assertTrue(current.rows[0].connectionEpoch != current.rows[1].connectionEpoch)
        val report = IncidentDiagnosticFormat.render(current)
        assertTrue(report.contains("equal ordinals, offsets, request IDs, or clocks are not joins"))
    }

    @Test fun `disconnect freezes first incident checkpoints pending rows and retains later settlement without changing context`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        window.onConnected()
        val pending = enter(window, 7, page = 3)
        val connection = window.snapshot().rows.single().connectionEpoch
        window.onDisconnected()
        val frozen = window.snapshot()
        assertTrue(frozen.frozen)
        assertEquals(IncidentFreezeReason.DISCONNECTED_WITH_PENDING_OPERATION, frozen.freezeReason)
        assertEquals(connection, frozen.freezeConnectionEpoch)
        assertEquals(IncidentRowState.DISCONNECTED_AWAITING_LATE_SETTLEMENT, frozen.rows.single().state)
        window.beginDiagnosticEpoch()
        assertEquals("a frozen lifecycle incident cannot be silently replaced", frozen.diagnosticEpoch, window.snapshot().diagnosticEpoch)
        clock.advance(10_000)
        settle(window, pending, clock, IncidentResponseOutcome.TRUE)
        val settled = window.snapshot()
        assertEquals(IncidentFreezeReason.DISCONNECTED_WITH_PENDING_OPERATION, settled.freezeReason)
        assertEquals(connection, settled.freezeConnectionEpoch)
        assertEquals(IncidentResponseOutcome.TRUE, settled.rows.single().responseOutcome)
        assertEquals(IncidentRowState.DISCONNECTED_LATE_SETTLED, settled.rows.single().state)
    }

    @Test fun `disconnect after only timely success freezes and preserves available settled row without rewriting outcome`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        window.onConnected()
        val success = enter(window, 71, page = 4)
        clock.advance(2)
        settle(window, success, clock, IncidentResponseOutcome.TRUE)
        assertFalse(window.snapshot().frozen)
        window.onDisconnected()
        val snapshot = window.snapshot()
        assertTrue(snapshot.frozen)
        assertEquals(IncidentFreezeReason.DISCONNECTED_AFTER_SETTLED_OPERATION, snapshot.freezeReason)
        assertEquals(IncidentResponseOutcome.TRUE, snapshot.rows.single().responseOutcome)
        assertEquals(IncidentRowState.SETTLED, snapshot.rows.single().state)
        val root = Files.createTempDirectory("incident-disconnect-success").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            assertEquals(
                IncidentPublicationResult.ACKNOWLEDGED_ATOMIC,
                store.saveIncident(IncidentDiagnosticFormat.render(snapshot), snapshot.diagnosticEpoch),
            )
            val reopened = SavedDiagnosticStore(root)
            val report = reopened.read(reopened.incidentReports().single())
            assertTrue(report.contains("freezeReason=DISCONNECTED_AFTER_SETTLED_OPERATION"))
            assertTrue(report.contains("out=T life=S"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `cancellation freezes first incident and completed failed row cannot be rewritten`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        val pending = enter(window, 8)
        window.onCancellationRequested()
        assertEquals(IncidentFreezeReason.CANCELLATION_REQUESTED_WITH_PENDING_OPERATION, window.snapshot().freezeReason)
        assertEquals(IncidentRowState.CANCELLATION_REQUESTED_AWAITING_LATE_SETTLEMENT, window.snapshot().rows.single().state)
        settle(window, pending, clock, IncidentResponseOutcome.FALSE, status = 257, length = 11)
        val failed = window.snapshot().rows.single()
        assertEquals(IncidentRowState.CANCELLATION_REQUESTED_LATE_SETTLED, failed.state)
        window.settle(pending, IncidentResponseOutcome.TRUE, status = 0, responseLength = 99, responseNanos = clock.now())
        val immutable = window.snapshot().rows.single()
        assertEquals(IncidentResponseOutcome.FALSE, immutable.responseOutcome)
        assertEquals(257, immutable.status)
        assertEquals(11, immutable.responseLength)
        assertEquals(IncidentRowState.CANCELLATION_REQUESTED_LATE_SETTLED, immutable.state)
    }

    @Test fun `pending rows are protected and capacity overflow freezes explicitly without silent rotation`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        repeat(WatchIncidentDiagnosticWindow.PRE_FREEZE_ROWS) { index ->
            assertTrue(enter(window, index + 1) != null)
        }
        assertEquals(null, enter(window, 99))
        val snapshot = window.snapshot()
        assertTrue(snapshot.frozen)
        assertEquals(1L, snapshot.overflowCount)
        assertEquals(WatchIncidentDiagnosticWindow.PRE_FREEZE_ROWS, snapshot.rows.size)
        assertTrue(snapshot.rows.all { it.responseOutcome == IncidentResponseOutcome.PENDING })
        window.beginDiagnosticEpoch()
        val retained = window.snapshot()
        assertEquals("overflow-only frozen incident cannot be silently replaced", snapshot.diagnosticEpoch, retained.diagnosticEpoch)
        assertEquals(snapshot.overflowCount, retained.overflowCount)
        assertEquals(snapshot.rows.map { it.ordinal }, retained.rows.map { it.ordinal })
        assertTrue(IncidentDiagnosticFormat.render(snapshot).contains("TRACE_WINDOW_OVERFLOW:1"))
    }

    @Test fun `post freeze reserve retains late settlement and never evicts frozen prior rows`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        val decisive = enter(window, 1)
        settle(window, decisive, clock, IncidentResponseOutcome.FALSE)
        repeat(WatchIncidentDiagnosticWindow.POST_FREEZE_RESERVE) { index ->
            val token = enter(window, 10 + index)
            settle(window, token, clock)
        }
        assertEquals(WatchIncidentDiagnosticWindow.POST_FREEZE_RESERVE + 1, window.snapshot().rows.size)
        repeat(WatchIncidentDiagnosticWindow.PRE_FREEZE_ROWS) { index ->
            enter(window, 100 + index)
        }
        val beforeOverflow = window.snapshot()
        assertEquals(WatchIncidentDiagnosticWindow.MAX_OPERATION_ROWS, beforeOverflow.rows.size)
        assertEquals(1L, beforeOverflow.rows.first().ordinal)
        assertEquals(null, enter(window, 999))
        assertEquals(1L, window.snapshot().rows.first().ordinal)
        assertTrue(window.snapshot().overflowCount >= 1L)
    }

    @Test fun `overflow freeze cannot be reset and worst case 64 row report remains complete within 24 KiB`() {
        val clock = Clock(Long.MIN_VALUE)
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        // Full UUID lineage is the longest legal row value; all 64 rows retain this connection epoch.
        window.onConnected()
        val decisive = window.enter(
            IncidentOperationKind.SOURCE_CONTROL_OTHER,
            IncidentAccess.WRITE,
            Int.MIN_VALUE,
            Int.MIN_VALUE,
            Long.MIN_VALUE,
            Int.MIN_VALUE,
        )
        window.preResponse(decisive, Int.MIN_VALUE, Int.MAX_VALUE)
        window.settle(decisive, IncidentResponseOutcome.FALSE, Int.MIN_VALUE, Int.MAX_VALUE, Long.MIN_VALUE)
        repeat(WatchIncidentDiagnosticWindow.MAX_OPERATION_ROWS - 1) {
            val token = window.enter(
                IncidentOperationKind.SOURCE_CONTROL_OTHER,
                IncidentAccess.WRITE,
                Int.MIN_VALUE,
                Int.MIN_VALUE,
                Long.MIN_VALUE,
                Int.MIN_VALUE,
            )
            window.preResponse(token, Int.MIN_VALUE, Int.MAX_VALUE)
            window.settle(token, IncidentResponseOutcome.NOT_REQUESTED, Int.MIN_VALUE, Int.MAX_VALUE, Long.MIN_VALUE)
        }
        assertEquals(WatchIncidentDiagnosticWindow.MAX_OPERATION_ROWS, window.snapshot().rows.size)
        assertEquals(null, window.enter(IncidentOperationKind.SOURCE_CONTROL_OTHER, IncidentAccess.WRITE,
            Int.MIN_VALUE, Int.MIN_VALUE, Long.MIN_VALUE, Int.MIN_VALUE))
        val before = window.snapshot()
        assertEquals(IncidentFreezeReason.FIRST_DECISIVE_CALLBACK_OUTCOME, before.freezeReason)
        window.beginDiagnosticEpoch()
        assertEquals(before.diagnosticEpoch, window.snapshot().diagnosticEpoch)
        val report = IncidentDiagnosticFormat.render(window.snapshot())
        assertTrue(report.toByteArray(Charsets.UTF_8).size <= SavedDiagnosticFormat.MAX_REPORT_BYTES)
        assertTrue(report.contains("traceCompleteness=COMPLETE traceOmittedRows=0 traceByteLoss=0"))
        assertTrue(report.contains("p=-9223372036854775808") && report.contains("in=-9223372036854775808") &&
            report.contains("pre=-9223372036854775808") && report.contains("res=-9223372036854775808"))
        assertTrue(report.contains("TRACE_WINDOW_OVERFLOW:1"))
        assertTrue(report.contains("freezeReason=FIRST_DECISIVE_CALLBACK_OUTCOME"))
        assertTrue(report.endsWith("--- END REPORT ---\n"))
    }

    @Test fun `atomic incident reopen preserves generic compatibility and explicit incident-only capacity outcome`() {
        val root = Files.createTempDirectory("incident-store").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            val generic = SavedDiagnosticFormat.render(
                DiagnosticContext(1, "test", UUID.randomUUID()),
                DiagnosticStage.SOURCE_CONTROL, DiagnosticOutcome.OTHER_STOP, null, 1, 1,
            )
            val genericFile = store.save(generic, UUID.randomUUID())
            assertEquals(generic, store.read(genericFile))
            val epoch = UUID.randomUUID()
            val incident = "HUGR SAVED ATT INCIDENT DIAGNOSTIC v1\nredacted\n--- END REPORT ---\n"
            assertEquals(IncidentPublicationResult.ACKNOWLEDGED_ATOMIC, store.saveIncident(incident, epoch))
            val reopened = SavedDiagnosticStore(root)
            assertEquals(incident, reopened.read(reopened.incidentReports().single()))
            repeat(7) { reopened.saveIncident(incident, UUID.randomUUID()) }
            assertEquals(8, reopened.reports().size)
            assertEquals(IncidentPublicationResult.CAPACITY_EXHAUSTED, reopened.saveIncident(incident, UUID.randomUUID()))
            assertEquals(8, reopened.reports().size)
            assertTrue(reopened.incidentReports().all { reopened.read(it).startsWith("HUGR SAVED ATT INCIDENT") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `publication acknowledgement and crash loss remain explicit report facts`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        val token = enter(window, 1)
        settle(window, token, clock, IncidentResponseOutcome.FALSE)
        assertTrue(window.notePublication(IncidentPublicationResult.ACKNOWLEDGED_ATOMIC))
        var report = IncidentDiagnosticFormat.render(window.snapshot())
        assertTrue(report.contains("publication=ACKNOWLEDGED_ATOMIC"))
        assertTrue(report.contains("crashBeforeCheckpoint=UNKNOWN_NOT_EVIDENCE_OF_CALLBACK_NON_ARRIVAL"))
        assertTrue(window.notePublication(IncidentPublicationResult.WRITE_FAILED))
        report = IncidentDiagnosticFormat.render(window.snapshot())
        assertTrue(report.contains("checkpointCrashLoss=CHECKPOINT_LOSS_UNCERTAIN:1"))
        assertTrue(report.contains("publication=WRITE_FAILED"))
    }

    @Test fun `directory sync or exact reopen failure is never acknowledged`() {
        val root = Files.createTempDirectory("incident-publication-failure").toFile()
        try {
            val incident = "incident\n--- END REPORT ---\n"
            val directorySyncFailure = SavedDiagnosticStore(
                root = root,
                syncDirectory = { throw IOException("test directory sync failure") },
            )
            assertEquals(
                IncidentPublicationResult.WRITE_FAILED,
                directorySyncFailure.saveIncident(incident, UUID.randomUUID()),
            )
            val reopenFailure = SavedDiagnosticStore(
                root = root,
                exactReopenVerifier = { _, _ -> throw IOException("test exact reopen failure") },
            )
            assertEquals(
                IncidentPublicationResult.WRITE_FAILED,
                reopenFailure.saveIncident(incident, UUID.randomUUID()),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `separate store instances serialize shared root capacity without incident rotation`() {
        val root = Files.createTempDirectory("incident-shared-root").toFile()
        try {
            val generic = SavedDiagnosticStore(root)
            generic.save("generic\n--- END REPORT ---\n", UUID.randomUUID())
            val ready = CountDownLatch(SavedDiagnosticStore.MAX_REPORTS)
            val start = CountDownLatch(1)
            val done = CountDownLatch(SavedDiagnosticStore.MAX_REPORTS)
            val results = java.util.Collections.synchronizedList(mutableListOf<IncidentPublicationResult>())
            repeat(SavedDiagnosticStore.MAX_REPORTS) { index ->
                Thread {
                    val store = SavedDiagnosticStore(root)
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    results += store.saveIncident("incident-$index\n--- END REPORT ---\n", UUID.randomUUID())
                    done.countDown()
                }.start()
            }
            assertTrue("all competing instances reached the shared-root barrier", ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue("all competing instances completed", done.await(10, TimeUnit.SECONDS))
            assertEquals(SavedDiagnosticStore.MAX_REPORTS, results.size)
            assertTrue(results.all {
                it == IncidentPublicationResult.ACKNOWLEDGED_ATOMIC ||
                    it == IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC
            })
            val reopened = SavedDiagnosticStore(root)
            assertEquals(SavedDiagnosticStore.MAX_REPORTS, reopened.reports().size)
            assertEquals(SavedDiagnosticStore.MAX_REPORTS, reopened.incidentReports().size)
            assertEquals(
                IncidentPublicationResult.CAPACITY_EXHAUSTED,
                SavedDiagnosticStore(root).saveIncident("ninth\n--- END REPORT ---\n", UUID.randomUUID()),
            )
            assertEquals(SavedDiagnosticStore.MAX_REPORTS, reopened.incidentReports().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `atomic incident can explicitly replace generic capacity but never pending temp files`() {
        val root = Files.createTempDirectory("incident-generic-capacity").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            repeat(SavedDiagnosticStore.MAX_REPORTS) { store.save("generic-$it\n--- END REPORT ---\n", UUID.randomUUID()) }
            val pending = File(root, ".unfinished.tmp").apply { writeText("half") }
            val result = store.saveIncident("incident\n--- END REPORT ---\n", UUID.randomUUID())
            assertEquals(IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC, result)
            assertEquals(SavedDiagnosticStore.MAX_REPORTS, store.reports().size)
            assertTrue(store.incidentReports().isNotEmpty())
            assertEquals("half", pending.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `incident report redacts payload uuid hash address and uses only native local fields`() {
        val clock = Clock()
        val window = WatchIncidentDiagnosticWindow(UUID.randomUUID(), clock::now)
        val token = window.enter(IncidentOperationKind.SOURCE_ACK, IncidentAccess.WRITE, 17, 9, null, 0)
        window.preResponse(token, 0, 64)
        window.settle(token, IncidentResponseOutcome.FALSE, 0, 64, clock.now())
        val report = IncidentDiagnosticFormat.render(window.snapshot())
        listOf("00:11:22:33:44:55", "deadbeef", "payload=", "uuid=", "hash=").forEach {
            assertFalse("report must not contain $it", report.contains(it, ignoreCase = true))
        }
        assertTrue(report.contains("rid=17"))
        assertTrue(report.contains("ci=9"))
        assertTrue(report.contains("len=64"))
    }

    @Test fun `source keeps all diagnostic work after ATT and preserves source ACK guards before response`() {
        val root = File("/home/ubuntu/HUGR_DIAGNOSTIC_INCIDENT_WINDOW_2026_10_24/watch/production-source")
        val gatt = File(root, "BleGattService.kt").readText()
        val wrapper = gatt.substringAfter("private fun sendAttResponse(").substringBefore("private fun receiptPageIndex")
        assertTrue(wrapper.indexOf("gattServer?.sendResponse") < wrapper.indexOf("settleIncidentOperationAfterResponse"))
        assertFalse(wrapper.contains("store("))
        assertFalse(wrapper.contains("saveIncident("))
        assertFalse(wrapper.contains("File("))
        val callback = gatt.substringAfter("if (characteristic?.uuid == SOURCE_CONTROL_CHARACTERISTIC_UUID && value != null)")
            .substringBefore("if (characteristic?.uuid == EGRESS_CONTROL_CHARACTERISTIC_UUID")
        assertTrue(callback.indexOf("handleSourceAcknowledgement") < callback.indexOf("writeResponseStatus = BluetoothGatt.GATT_FAILURE"))
        assertTrue(gatt.contains("private fun handleSourceAcknowledgement(acknowledgement: SourceSegmentAcknowledgement)"))
        assertTrue(gatt.contains("SourceReplayWindow.validateQueuedManifestAcknowledgement("))
        assertTrue(gatt.contains("sourceJournal.acknowledgeCompletedSegment("))
    }

    @Test fun `saved diagnostics viewer remains an offline complete-report reader without receipt canonical or runtime initialization`() {
        val root = File("/home/ubuntu/HUGR_DIAGNOSTIC_INCIDENT_WINDOW_2026_10_24/watch/production-source")
        val viewer = File(root, "SavedDiagnosticActivity.kt").readText()
        assertTrue(viewer.contains("store.reports()"))
        assertTrue(viewer.contains("store.read(file)"))
        listOf(
            "WatchSourceRuntime", "FreshRunRuntime", "WatchCausalRuntime", "BleGattService",
            "startService(", "startForegroundService(", "readRecordsAfter", "delivery_states.log",
        ).forEach { forbidden -> assertFalse("viewer must remain offline-only: $forbidden", viewer.contains(forbidden)) }
    }
}
