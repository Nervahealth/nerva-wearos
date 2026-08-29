package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class CausalFlightRecorderTest {
    @Test
    fun `events survive restart with exact fields crc and increasing sequence`() {
        val root = tempDir()
        val session = UUID.randomUUID()
        val firstProcess = UUID.randomUUID()
        val component = UUID.randomUUID()
        var elapsed = 10L
        var wall = 1_000L
        val first = recorder(root, session, firstProcess, { elapsed }, { wall })

        val firstEvent = requireNotNull(first.record(
            code = CausalEventCode.PROCESS_STARTED,
            component = CausalComponentCode.RUNTIME,
            componentInstanceId = component,
            arg0 = 7L,
        ))
        elapsed = 20L
        wall = 2_000L
        val secondEvent = requireNotNull(first.record(
            code = CausalEventCode.FIRST_APPEND,
            component = CausalComponentCode.HEALTH,
            componentInstanceId = component,
            bleLineage = 3L,
            stream = CausalStreamCode.EDA,
            recordIndexStart = 11L,
            recordIndexEnd = 11L,
            arg0 = 2L,
            arg1 = 9L,
            reasonCode = 4,
        ))
        first.close()

        val restarted = recorder(root, session, UUID.randomUUID(), { 30L }, { 3_000L })
        val events = restarted.events()

        assertEquals(CausalRecorderIntegrity.OK, restarted.integrity())
        assertEquals(listOf(1L, 2L), events.map { it.eventSequence })
        assertEquals(firstEvent.copy(), events[0])
        assertEquals(secondEvent.copy(), events[1])
        assertEquals(CausalStreamCode.EDA, events[1].stream)
        assertEquals(11L, events[1].recordIndexStart)
        assertEquals(4, events[1].reasonCode)
    }

    @Test
    fun `restart continues global sequence under a new process identity`() {
        val root = tempDir()
        val session = UUID.randomUUID()
        val component = UUID.randomUUID()
        val first = recorder(root, session, UUID.randomUUID(), { 1L }, { 10L })
        requireNotNull(first.record(CausalEventCode.PROCESS_STARTED, CausalComponentCode.RUNTIME, component))
        first.close()

        val secondProcess = UUID.randomUUID()
        val restarted = recorder(root, session, secondProcess, { 2L }, { 20L })
        val event = requireNotNull(restarted.record(
            CausalEventCode.PROCESS_STARTED,
            CausalComponentCode.RUNTIME,
            UUID.randomUUID(),
        ))

        assertEquals(2L, event.eventSequence)
        assertEquals(secondProcess, event.processInstanceId)
        assertEquals(2, restarted.events().size)
    }

    @Test
    fun `torn tail is truncated and followed by one recovery event`() {
        val root = tempDir()
        val session = UUID.randomUUID()
        val first = recorder(root, session, UUID.randomUUID(), { 1L }, { 10L })
        requireNotNull(first.record(
            CausalEventCode.PROCESS_STARTED,
            CausalComponentCode.RUNTIME,
            UUID.randomUUID(),
        ))
        first.close()
        File(root, CausalFlightRecorder.EVENTS_FILE_NAME).appendText("partial-tail-without-crc")

        val recovered = recorder(root, session, UUID.randomUUID(), { 2L }, { 20L })
        val events = recovered.events()

        assertEquals(CausalRecorderIntegrity.RECOVERED_TAIL, recovered.integrity())
        assertEquals(listOf(CausalEventCode.PROCESS_STARTED, CausalEventCode.RECORDER_RECOVERED_TAIL), events.map { it.code })
        assertEquals(listOf(1L, 2L), events.map { it.eventSequence })
    }

    @Test
    fun `interior corruption degrades recorder and never invents continuation`() {
        val root = tempDir()
        val session = UUID.randomUUID()
        val first = recorder(root, session, UUID.randomUUID(), { 1L }, { 10L })
        requireNotNull(first.record(CausalEventCode.PROCESS_STARTED, CausalComponentCode.RUNTIME, UUID.randomUUID()))
        requireNotNull(first.record(CausalEventCode.SDK_CONNECT_REQUESTED, CausalComponentCode.HEALTH, UUID.randomUUID()))
        first.close()
        val file = File(root, CausalFlightRecorder.EVENTS_FILE_NAME)
        val lines = file.readLines().toMutableList()
        lines[0] = lines[0].replaceFirst('1', '9')
        file.writeText(lines.joinToString("\n", postfix = "\n"))

        val degraded = recorder(root, session, UUID.randomUUID(), { 3L }, { 30L })

        assertEquals(CausalRecorderIntegrity.DEGRADED, degraded.integrity())
        assertNull(degraded.record(CausalEventCode.PROCESS_STARTED, CausalComponentCode.RUNTIME, UUID.randomUUID()))
        assertFalse(degraded.events().any { it.eventSequence > 2L })
    }

    @Test
    fun `event limit compacts to bounded retained suffix and preserves sequence`() {
        val root = tempDir()
        val recorder = CausalFlightRecorder(
            rootDir = root,
            watchBootSessionId = UUID.randomUUID(),
            processInstanceId = UUID.randomUUID(),
            nowElapsedMs = { 1L },
            nowWallMs = { 2L },
            maxEvents = 8,
            retainEvents = 5,
            maxBytes = 4_096L,
        )
        requireNotNull(recorder.record(
            CausalEventCode.B46_BASELINE_SUMMARY,
            CausalComponentCode.RUNTIME,
            UUID.randomUUID(),
            arg0 = 3L,
            arg1 = 42L,
        ))
        repeat(8) {
            requireNotNull(recorder.record(
                CausalEventCode.TRANSPORT_SNAPSHOT,
                CausalComponentCode.BLE,
                UUID.randomUUID(),
                arg0 = it.toLong(),
            ))
        }

        val events = recorder.events()
        assertEquals(5, events.size)
        assertEquals(CausalEventCode.B46_BASELINE_SUMMARY, events.first().code)
        assertEquals(listOf(1L, 6L, 7L, 8L, 9L), events.map { it.eventSequence })
        assertTrue(File(root, CausalFlightRecorder.EVENTS_FILE_NAME).length() <= 4_096L)
    }

    @Test
    fun `serialized diagnostic contains no payload mac address or free text sentinel`() {
        val root = tempDir()
        val recorder = recorder(root, UUID.randomUUID(), UUID.randomUUID(), { 1L }, { 2L })
        requireNotNull(recorder.record(
            code = CausalEventCode.SOURCE_TRIGGER_RESULT,
            component = CausalComponentCode.BLE,
            componentInstanceId = UUID.randomUUID(),
            bleLineage = 4L,
            stream = CausalStreamCode.CARDIAC,
            recordIndexStart = 10L,
            recordIndexEnd = 14L,
            reasonCode = CausalReasonCode.TRIGGERED.code,
        ))
        recorder.close()

        val serialized = File(root, CausalFlightRecorder.EVENTS_FILE_NAME).readText()
        assertFalse(serialized.contains("FORBIDDEN-PHYSIOLOGY-SENTINEL"))
        assertFalse(serialized.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(serialized.contains("payload", ignoreCase = true))
        assertFalse(serialized.contains("note", ignoreCase = true))
    }

    @Test
    fun `explicit diagnostic failure degrades recorder and blocks later events`() {
        val root = tempDir()
        val recorder = recorder(root, UUID.randomUUID(), UUID.randomUUID(), { 1L }, { 2L })
        requireNotNull(recorder.record(CausalEventCode.PROCESS_STARTED, CausalComponentCode.RUNTIME, UUID.randomUUID()))

        recorder.markDegraded()

        assertEquals(CausalRecorderIntegrity.DEGRADED, recorder.integrity())
        assertNull(recorder.record(CausalEventCode.SDK_CONNECTED, CausalComponentCode.HEALTH, UUID.randomUUID()))
        assertEquals(1, recorder.events().size)
    }

    private fun recorder(
        root: File,
        session: UUID,
        process: UUID,
        elapsed: () -> Long,
        wall: () -> Long,
    ) = CausalFlightRecorder(
        rootDir = root,
        watchBootSessionId = session,
        processInstanceId = process,
        nowElapsedMs = elapsed,
        nowWallMs = wall,
    )

    private fun tempDir(): File = Files.createTempDirectory("hugr-causal-recorder-").toFile()
}

class Build46SourceBaselineTest {
    @Test
    fun `baseline counts delivery metadata without returning canonical payloads`() {
        val root = Files.createTempDirectory("hugr-b46-baseline-").toFile()
        val journal = SourceJournal(
            rootDir = root,
            bootCount = 47,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 1L,
        )
        val first = journal.append(SourceStreamCode.EDA, 10L, "FORBIDDEN-PHYSIOLOGY-SENTINEL".toByteArray())
        val second = journal.append(SourceStreamCode.ACCEL, 20L, byteArrayOf(1, 2, 3))
        journal.recordDelivery(listOf(first, second), SourceDeliveryState.LIVE_SENT)

        val baseline = Build46SourceBaseline.from(journal)

        assertEquals(1, baseline.retainedSessionCount)
        assertEquals(2L, baseline.latestRecordIndex)
        assertEquals(2L, baseline.deliveryCounts.getValue(SourceDeliveryState.BUFFERED))
        assertEquals(2L, baseline.deliveryCounts.getValue(SourceDeliveryState.LIVE_SENT))
        assertFalse(baseline.toString().contains("FORBIDDEN-PHYSIOLOGY-SENTINEL"))
    }

    @Test
    fun `baseline store captures once and survives later journal mutations`() {
        val sourceRoot = Files.createTempDirectory("hugr-b46-baseline-source-").toFile()
        val baselineRoot = Files.createTempDirectory("hugr-b46-baseline-store-").toFile()
        val journal = SourceJournal(
            rootDir = sourceRoot,
            bootCount = 47,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 1L,
        )
        journal.append(SourceStreamCode.EDA, 10L, byteArrayOf(1))
        val store = Build46BaselineStore(baselineRoot)

        val first = store.captureOnce { Build46SourceBaseline.from(journal) }
        journal.append(SourceStreamCode.ACCEL, 20L, byteArrayOf(2))
        val second = store.captureOnce { Build46SourceBaseline.from(journal) }

        assertEquals(1L, first.latestRecordIndex)
        assertEquals(first, second)
        assertEquals(first, Build46BaselineStore(baselineRoot).load())
    }

    @Test
    fun `baseline formatter exposes six unambiguous delivery labels`() {
        val counts = SourceDeliveryState.entries.associateWith { state -> (state.ordinal + 1).toLong() }
        val baseline = Build46SourceBaseline(
            retainedSessionCount = 2,
            latestRecordIndex = 99L,
            deliveryCounts = counts,
        )

        val text = Build46BaselineFormatter.format(baseline)

        assertTrue(text.contains("BUF=1"))
        assertTrue(text.contains("LS=2"))
        assertTrue(text.contains("LC=3"))
        assertTrue(text.contains("RS=4"))
        assertTrue(text.contains("RC=5"))
        assertTrue(text.contains("DROP=6"))
    }
}

class FirstCausalEventGateTest {
    @Test
    fun `first markers deduplicate by component event and stream`() {
        val gate = FirstCausalEventGate()
        val component = UUID.randomUUID()

        assertTrue(gate.shouldRecord(component, CausalEventCode.FIRST_CALLBACK, CausalStreamCode.EDA))
        assertFalse(gate.shouldRecord(component, CausalEventCode.FIRST_CALLBACK, CausalStreamCode.EDA))
        assertTrue(gate.shouldRecord(component, CausalEventCode.FIRST_APPEND, CausalStreamCode.EDA))
        assertTrue(gate.shouldRecord(component, CausalEventCode.FIRST_CALLBACK, CausalStreamCode.ACCEL))
        assertTrue(gate.shouldRecord(UUID.randomUUID(), CausalEventCode.FIRST_CALLBACK, CausalStreamCode.EDA))
    }
}

class CausalLineageStateTest {
    @Test
    fun `local abort cancel and disconnect remain distinguishable from external loss`() {
        val state = CausalLineageState()

        assertEquals(1L, state.onConnected())
        state.markAbort(CausalReasonCode.SOURCE_NOTIFICATION_FAILED.code)
        state.markCancelRequested()
        val local = state.onDisconnected(gattStatus = 8)
        assertEquals(1L, local.bleLineage)
        assertTrue(local.localCancelRequested)
        assertEquals(CausalReasonCode.SOURCE_NOTIFICATION_FAILED.code, local.abortReasonCode)

        assertEquals(2L, state.onConnected())
        val external = state.onDisconnected(gattStatus = 19)
        assertEquals(2L, external.bleLineage)
        assertFalse(external.localCancelRequested)
        assertEquals(CausalReasonCode.NONE.code, external.abortReasonCode)
    }

    @Test
    fun `unsafe MTU after release has an explicit fail closed reason`() {
        assertEquals(
            CausalReasonCode.UNSAFE_MTU_AFTER_RELEASE,
            CausalReasonCode.fromAbortReason("unsafe_mtu_after_release"),
        )
        assertEquals(27, CausalReasonCode.UNSAFE_MTU_AFTER_RELEASE.code)
    }
}

class CausalFlightFormatterTest {
    @Test
    fun `formatter emits only latest twenty bounded metadata rows`() {
        val events = (1L..25L).map { sequence ->
            CausalFlightEvent(
                schemaVersion = 1,
                eventSequence = sequence,
                watchBootSessionId = UUID.randomUUID(),
                processInstanceId = UUID.randomUUID(),
                component = CausalComponentCode.BLE,
                componentInstanceId = UUID.randomUUID(),
                bleLineage = 2L,
                elapsedRealtimeMs = sequence * 100L,
                wallTimeMs = 1_000L + sequence,
                code = CausalEventCode.SOURCE_TRIGGER_RESULT,
                stream = CausalStreamCode.EDA,
                recordIndexStart = sequence,
                recordIndexEnd = sequence,
                arg0 = 0L,
                arg1 = 0L,
                reasonCode = CausalReasonCode.NOT_SUBSCRIBED.code,
            )
        }

        val rows = CausalFlightFormatter.format(events, limit = 20)

        assertEquals(20, rows.size)
        assertTrue(rows.first().contains("#6"))
        assertTrue(rows.last().contains("#25"))
        assertTrue(rows.first().contains("+0"))
        assertTrue(rows.last().contains("+1900"))
        assertTrue(rows.all { it.length <= 120 })
        assertFalse(rows.joinToString().contains("payload", ignoreCase = true))
    }
}
