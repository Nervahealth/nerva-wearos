# Watch76 — actionable resume high-water exception captured

**2026-10-08 CEST. Evidence review only; no production modification, build or new device procedure.** This is a separate operator-initiated repeat after the 11:40 STOP, not a continuation or retrospective authorization of that earlier attempt.

## Actual supplied evidence

Neil reports reconnecting Bugjaeger, checking logs, entering the prior shell command, dismissing the old Main HUGR card (not force-stop), reaching current white advertising READY and pressing normal Phone59 Connect. He reports destruction immediately afterward. The supplied Fold recording actually contains readable Watch throwable output; the earlier 11:40 exit-1 outcome remains separate and unqualified.

Video: `Screen_Recording_20261008_114806_Bugjaeger.mp4`, 128.478078 seconds, 29,260,079 bytes, SHA-256 `f502bfe80f8674147fd947dc9131e58c81b7e643e7d7abc370d5c634152e971a`.

Watch photo identities (already displayed by user; not reopened):

- `IMG_20261008_114751.webp`: `ab9b1156689025a7f49580b17d32ad0bf8a84d8f96868e87190b3a63479f43cb`.
- `IMG_20261008_114749.webp`: `84c75ebfc3757de04c0fcc7920b67008cca282c2aef47e7d207f9aba062a3a11`.
- `IMG_20261008_114745.webp`: `8ab7e8c89905bdc3ce1e7b17e8e60c5e43c6afe34ee02130f4f8595edbfef5c0`.

The recording shows the Fold Log page, then a Watch shell and ARMED command. Representative +90s and +127s frames contain the complete relevant exception, worker caller frames and subsequent shutdown warning. No capture exit-code line is visible in those final inspected frames. This proves receipt of this exception, not general ADB reliability. The opening Log page includes unrelated system entries; private original media and broad logs are not copied into GitHub.

## Restricted exception transcript

All following HUGR lines have Watch timestamp `10-08 11:47:07` and process ID `31903`:

```text
11:47:07.815 E/HUGR-BleGatt: Source resume preparation failed: Phone resume index exceeds watch journal
11:47:07.815 E/HUGR-BleGatt: com.hugr.wearos.SourceJournalCorruptionException: Phone resume index exceeds watch journal
11:47:07.815 E/HUGR-BleGatt: at com.hugr.wearos.BleGattService.prepareSourceReplay(BleGattService.kt:1715)
11:47:07.815 E/HUGR-BleGatt: at com.hugr.wearos.BleGattService.scheduleSourceReplayPreparation$lambda$37(BleGattService.kt:1683)
[Two generated BleGattService lambda/adapter frames follow; source locations Unknown Source:0 and Unknown Source:6.]
11:47:07.815 E/HUGR-BleGatt: at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
11:47:07.815 E/HUGR-BleGatt: at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
11:47:07.815 E/HUGR-BleGatt: at java.lang.Thread.run(Thread.java:1119)
11:47:07.837 W/HUGR-BleGatt: Retained source delivery stopped without acknowledgement: source_resume_prepare_failed
11:47:07.882 W/HUGR-BleGatt: Error unregistering receivers: Receiver not registered: com.hugr.wearos.BleGattService$ppgReceiver$1@96ce0ad
```

This is a caught application rejection followed by an explicit delivery stop, **not evidence of Android killing the foreground service**. The receiver-unregister warning occurs afterward; it is not the initiating failure.

The supplied Watch photos visibly show:

```text
RETAINED DELIVERY STOPPED · TRANSPORT_ABORT
#251 +435588 L1 RESUME_RECEIVED · 8184–8184
#252 +435730 L1 ABORT_REQUESTED · r=255
#253 +435735 L1 CANCEL_CONNECTION_REQUESTED · r=255
#254 +435744 L1 FINALIZED_RECOVERY_STOP_REQUESTED · r=3
+435787 L1 BLE_SERVICE_DESTROYED · r=3
```

Within this recorder clock, abort follows resume by 142 ms and destruction by 199 ms. These are relative marker intervals, not a synchronized Fold/Phone clock join or complete source-session identity.

## Source-established cause

Current `BleGattService.prepareSourceReplay`, lines 1704–1722:

1. Select the retained finalized fresh-v2 session.
2. If Phone request session matches that selected session, use its cumulative index (nonnegative); otherwise accepted index is zero.
3. Compute `highWater = sourceJournal.highestFinalizedRecordIndex(session)`.
4. At line 1715 throw when `acceptedIndex > highWater`.

**The captured failure confirms this exact guard rejected the Phone cursor before a replay plan or manifest could be released.** With the photographed cursor 8184, the selected retained high-water was below 8184. The actual numeric retained high-water and full selected session UUID are not logged; do not fabricate them.

`SourceJournal.highestFinalizedRecordIndex` returns the maximum endpoint among **currently retained** segments. `acknowledgeCompletedSegment` deletes a segment only after exact session/endpoint/full-hash verification. Consequently retained maximum is not necessarily the maximum historically issued record index. The previously committed `SourceResumeIndex8184ScenarioTest` already reproduces an exact later-segment ACK/deletion followed by restart with older retained manifests: Phone 8184, retained maximum 8182, and this guard aborts even though the next older manifest is valid for manifest-only equality. Existing test log records BUILD SUCCESSFUL; no new tests were run in this evidence-only review.

That scenario supplies a demonstrated mechanism, but the capture does not independently prove which old segment was deleted, the old Watch ACK's acceptance or the exact remaining manifest list. Do not upgrade historical 8183–8184 Phone UI ACK to confirmed historical Watch acceptance.

The Watch76 multi-manifest ordering fix can only run **after** preparation succeeds. It does not repair this earlier guard. `SourceReplayWindow` already supports older currently queued manifest ACKs, with exact session/endpoint/full-hash checks and ascending retained-manifest ordering. The same guard appears in `beginSourceReplaySession`; any repair must review both entry points and all downstream bounds/backlog calculations.

## Proposed narrow repair for explicit approval

No further physical diagnostic is needed to identify the initiating code branch. Before another candidate:

- Separate the same-session Phone durable-record cursor from the maximum remaining retained segment endpoint; establish and test the journal-backed bound needed to distinguish a legitimate cursor after prior exact-ACK deletion from an unsupported future cursor. Do not just remove the corruption check or trust an arbitrary Phone index.
- Plan older retained manifests in order even when their records are already durable on Phone. Bound data reads to actual retained ranges, keep cursor monotonic and backlog nonnegative; do not fabricate missing records, recreate deleted bytes or extend recording.
- Preserve exact queued session/endpoint/full SHA acceptance, per-ACK advancement, final completion only after all selected-session retained manifests are exactly acknowledged, and process-restart behavior.
- Exercise the actual rejected 8184 condition through the joined Watch→Phone→Watch regression, including already-stored Phone bytes, older manifests, every ACK and terminal completion. Test unsupported future/wrong-session cursors, mismatched hash/endpoint, repeat ACK, disconnect and deadline. Scope replay only to fresh v2; preserve legacy/frozen roots and no-new-sensing enforcement.
- Receiver teardown should unregister only receivers actually registered in the current runtime; its warning is secondary and cannot substitute for the cursor correction.

Implementation, candidate build/install or another recovery attempt is **not** performed or newly authorized by this evidence review. Ask Neil for explicit source/test correction authority against this finding before editing production code.

## Preserved boundaries

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

Source-owned receipt → copied Phone/control docs → Master → private VIS-COUNT impact noted without retrying known-denied access or mutating accepted S4 → Alignment last → bounded commit/non-force remote head and exact tree readback. Supplied private media remains local, unmodified. This record confirms an actionable exception, **not final recording verification or canonical custody**.
