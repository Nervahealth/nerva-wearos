package com.hugr.wearos

internal class FreshRunSession(
    val journal: SourceJournal,
    private val store: FreshRunReceiptStore,
    initial: FreshRunReceipt,
    private val elapsed: () -> Long,
    private val wall: () -> Long,
) {
    private var receipt = initial
    private var accepting = false
    private var ioFailed = false
    private fun update(next: FreshRunReceipt) {
        check(!ioFailed)
        try { store.publish(next); receipt = next } catch (e: Exception) { ioFailed = true; accepting = false; throw e }
    }
    @Synchronized fun snapshot(): FreshRunReceipt { check(!ioFailed); return store.load(receipt.runId) }
    @Synchronized fun begin(): Boolean {
        if (accepting || receipt.phase != FreshRunPhase.STARTED || elapsed() - receipt.startElapsedMs >= receipt.durationMs) return false
        accepting = true; return true
    }
    @Synchronized fun acceptsSamples(): Boolean = accepting && !ioFailed && receipt.phase == FreshRunPhase.STARTED &&
        elapsed() >= receipt.startElapsedMs && elapsed() - receipt.startElapsedMs < receipt.durationMs
    @Synchronized fun quiesce() { accepting = false }
    @Synchronized fun remainingRecordingMs(): Long = (receipt.durationMs - (elapsed() - receipt.startElapsedMs)).coerceAtLeast(0)
    @Synchronized fun scope(supported: Int, started: Int) {
        if (!accepting || receipt.phase != FreshRunPhase.STARTED) return
        if (receipt.supportedMask == supported && receipt.startedMask == started) return
        update(receipt.copy(revision = receipt.revision + 1, supportedMask = supported, startedMask = started))
    }
    @Synchronized fun append(stream: SourceStreamCode, timestamp: Long, payload: ByteArray): WatchSourceRecord? {
        if (!acceptsSamples()) return null
        return try {
            val record = journal.append(stream, timestamp, payload)
            captureManifests(); record
        } catch (e: Exception) { runCatching { fail("SOURCE_IO") }; throw e }
    }
    @Synchronized fun captureManifests() {
        if (ioFailed) error("Receipt IO failed")
        val found = journal.finalizedManifests(journal.watchBootSessionId)
        val merged = receipt.manifests.toMutableList()
        found.forEach { m ->
            val previous = merged.firstOrNull { it.firstRecordIndex == m.firstRecordIndex }
            if (previous != null) require(FreshRunReceiptStore.sameInventory(listOf(previous), listOf(m)))
            else { check(receipt.phase == FreshRunPhase.STARTED); merged += m }
        }
        require(merged.size <= 64)
        val ordered = merged.sortedBy { it.firstRecordIndex }
        if (!FreshRunReceiptStore.sameInventory(receipt.manifests, ordered)) update(receipt.copy(revision = receipt.revision + 1, manifests = ordered))
    }
    @Synchronized fun finalizeRecording() {
        check(!ioFailed && receipt.phase == FreshRunPhase.STARTED)
        accepting = false
        try {
            journal.forceSync(); journal.finalizeActiveSegment(); journal.forceSync(); captureManifests()
            val final = receipt.copy(revision = receipt.revision + 1, phase = FreshRunPhase.FINALIZED, finalizedWallMs = wall(), finalizedElapsedMs = elapsed())
            update(final)
            val failure = final.completionFailure()
            if (failure != null) fail(failure) else completeIfExact()
        } catch (e: Exception) { runCatching { fail("FINALIZATION_IO") }; throw e }
    }
    /** The unmodified validator deletes on acceptance. Pending exact tuple precedes that call; public a follows true return. */
    @Synchronized fun acceptAck(ack: SourceSegmentAcknowledgement, queuedEnd: Long?): Boolean {
        check(!ioFailed && receipt.phase != FreshRunPhase.FAILED && receipt.phase != FreshRunPhase.COMPLETED)
        captureManifests()
        require(queuedEnd == ack.cumulativeRecordIndex && ack.watchBootSessionId == receipt.sourceId)
        val m = receipt.manifests.firstOrNull { it.lastRecordIndex == ack.cumulativeRecordIndex } ?: return false
        require(m.sha256Hex == ack.completedSegmentSha256 && receipt.acks.none { it.last == m.lastRecordIndex })
        val tuple = FreshAcceptedAck(m.lastRecordIndex, m.sha256Hex, elapsed())
        try { store.pendingAck(receipt, tuple) }
        catch (e: Exception) {
            accepting = false
            runCatching { fail("RECEIPT_IO") }
            ioFailed = true
            throw e
        }
        val accepted = try { journal.acknowledgeCompletedSegment(ack.watchBootSessionId, ack.cumulativeRecordIndex, ack.completedSegmentSha256) }
            catch (e: Exception) { ioFailed = true; accepting = false; throw e }
        if (!accepted) { store.finishAck(receipt.runId); fail("ACK_DELETE_FAILED"); return false }
        try {
            update(receipt.copy(revision = receipt.revision + 1, acks = receipt.acks + tuple))
            store.finishAck(receipt.runId)
        } catch (e: Exception) { ioFailed = true; accepting = false; throw e }
        completeIfExact()
        return true
    }
    private fun completeIfExact() {
        if (receipt.phase != FreshRunPhase.FINALIZED || receipt.completionFailure() != null) return
        if (receipt.acks.size != receipt.manifests.size || receipt.manifests.any { m -> receipt.acks.none { it.last == m.lastRecordIndex && it.hash == m.sha256Hex } }) return
        if (journal.latestRecordIndex() != receipt.manifests.last().lastRecordIndex || journal.hasFinalizedSegments(receipt.sourceId)) {
            fail("SOURCE_CHANGED"); return
        }
        update(receipt.copy(revision = receipt.revision + 1, phase = FreshRunPhase.COMPLETED))
    }
    @Synchronized fun fail(code: String) {
        accepting = false
        if (ioFailed || receipt.phase == FreshRunPhase.FAILED || receipt.phase == FreshRunPhase.COMPLETED) return
        update(receipt.copy(revision = receipt.revision + 1, phase = FreshRunPhase.FAILED, failure = code))
    }
    @Synchronized fun forceSync() { if (accepting) journal.forceSync() }
    @Synchronized fun permitsDelivery(): Boolean = !ioFailed && elapsed() >= receipt.startElapsedMs && elapsed() - receipt.startElapsedMs < MAX_LIFETIME_MS
    companion object { const val MAX_LIFETIME_MS = 20 * 60 * 1000L; const val RECEIPT_ONLY_MS = 2 * 60 * 1000L }
}
