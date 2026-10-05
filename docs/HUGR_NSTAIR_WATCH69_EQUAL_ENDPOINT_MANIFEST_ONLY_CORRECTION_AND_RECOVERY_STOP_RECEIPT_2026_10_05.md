# HUGR NSTAIR — Watch69 Equal-Endpoint Manifest-Only Correction and Phone57 Recovery Stop Receipt

**Date:** 2026-10-05
**Scope:** (1) the previously authorised one ordinary Phone57 reconnect/resume observation for the existing finalised Watch69 run; and (2) the explicitly authorised source/test-only correction of the equal-endpoint terminal-manifest seam discovered by that observation.
**Device action in the source/test correction:** **None.** No build, installation, launcher action, reconnect, retry, new recording, egress action, readback, verifier, ADB/Termux, legacy/frozen-root, Watch58, or Watch68 operation occurred during the correction.

## Result

> **PHONE57 RECOVERY ATTEMPT — NO TERMINAL REPLAY / NO FINAL MANIFEST ACKNOWLEDGEMENT / WATCH69 RUN REMAINS NOT QUALIFIED.**
>
> **EQUAL-ENDPOINT MANIFEST-ONLY DELIVERY SEAM — SOURCE DEFECT ESTABLISHED / MINIMAL WATCH CORRECTION GREEN IN FOCUSED AND FULL TESTS / NOT BUILT OR DEPLOYED.**

The retained Watch69 five-minute run remains **not qualified**. Its historical finalisation marker stays:

| Field | Value |
|---|---|
| Run ID | `b561b698-8206-4cb5-8717-95a5c9f533e2` |
| Last earned Watch marker | `BOUNDED_RUN_FINALIZED` |
| Marker epoch | `1791056794547` |
| UTC marker time | `2026-10-03T19:46:34.547Z` |

No final Phone manifest equality for the retained terminal segment, exact Phone acknowledgement, `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, or `BOUNDED_RUN_GATT_STOP_REQUESTED` was observed.

## A. Bounded Phone57 recovery observation

### Evidence and bound

- Operator-supplied Phone screen recording: `SVID_20261004_103551_1.mp4`
- SHA-256: `c861d2e9a83b7861c60a1432580842b6a4d07786a3a06867613da346507f8250`
- Duration: `1737.769375` seconds (about 28 minutes 58 seconds)
- Planned recovery observation bound: one ordinary normal-BLE connection followed by **15 minutes untouched**; no new recording or Watch Main-HUGR launch.

The recovery condition was already negative at the 15-minute bound. The supplied video continued afterward, but it did not show a later replay or acknowledgement; that extended footage does not turn the bounded recovery attempt into a success.

### What the Phone visibly held, unchanged

Across the recording’s normal-source debug/status region, including sampled early, 2-, 5-, 10-, 15-, 20-, 25-minute, and end frames, the visible result remained materially unchanged:

| Visible field | Observed value |
|---|---|
| Connection context | ordinary wearable connected; no egress transfer action claimed |
| Source delivery | `DEVICE_HEALTH #8183 LIVE` |
| Replay indicator | **No `REPLAY` line observed** |
| Backlog | `0` |
| Watch records | `8182` |
| Source session prefix displayed by Phone | `3c9e99e9` |
| Phone receipt prefix displayed by Phone | `179105649626` |
| Manifest equality | `ACK 1-11 n=11 bytes=917 sha=4d5f2fe334bff…` only |
| Source journal display | `integrity=UNPROVEN` |
| Live status | `TRANSPORT_STALLED` / stale live acquisition |

No visible source-integrity failure, `DATA LOSS`, `REMOVED`, connection failure, new terminal manifest range/count/byte count/SHA, or attributable final acknowledgement appeared in the normal-source status region. The persisted `ACK 1–11` remains the previously observed early acknowledgement; it is not attributed to the terminal finalised Watch69 segment.

### Separate late UI event

At about 28 minutes 30 seconds into the full video—**after** the 15-minute recovery stop boundary—the visual sequence shows `TEST NOTIFICATION HAPTIC v1` being pressed. The resulting Phone text is:

> `Phone write failed. No watch delivery is claimed.`

That is a separate failed Phone haptic-write event. It neither starts a new recording nor proves Watch delivery, source replay, final manifest equality, acknowledgement, or qualification. It does not change the recovery stop result above.

## B. Source-established equal-endpoint defect

The prior Watch correction fixed the stale-high-water finalisation broadcast seam. The Phone57 recovery observation exposed a second retained-manifest seam that the prior correction did not cover.

1. The Phone source ingestor durably appends canonical source frames and persists its contiguous resume index before it can acknowledge a manifest.
2. When the Watch receives a normal resume request, `prepareSourceReplay()` accepts that durable index for the active session and freezes the replay high-water at the retained session’s terminal index.
3. In the observed terminal-manifest-only condition, the Phone has already durably retained all terminal records, so:

   ```text
   terminal manifest lastRecordIndex == durablePhoneRecordIndex == replayHighWaterRecordIndex
   ```

4. The previous selection logic required `manifest.lastRecordIndex > durablePhoneRecordIndex`. It therefore excluded the retained final manifest on both:
   - normal reconnect/resume selection; and
   - finalisation-triggered durable delivery planning.

5. The Watch then had no replay data backlog and, crucially, no final manifest frame to send. The Phone could not perform the required manifest equality verification or write its existing exact acknowledgement.

This precisely matches the recovery observation: connected normal BLE and periodic device-health frames continued, but no final replay/manifest acknowledgement occurred.

## C. Minimal correction

The correction remains in the existing GATT/replay selection layer; the frozen journal implementation is unchanged.

### Changed behavior

- `SourceReplayWindow.nextManifestToQueue()` now permits an **equality-only terminal manifest** when its endpoint equals the durable Phone index.
- It continues to prefer any manifest whose endpoint is **later** than the Phone index. Equality is only eligible when there is no later unacknowledged manifest.
- `SourceReplayWindow.planFinalizedManifestDelivery()` includes the same equality-only terminal case.
- `BleGattService.enqueueNextManifestForReplayWindow()` now selects from `sourceJournal.finalizedManifests(session)` through that replay-window rule, then uses the existing normal manifest queue and its `queuedReplayManifestEndIndex` guard.

### Deliberate non-changes and safety properties

- **No replay data** is produced in the equality-only case: `lastReplayQueuedRecordIndex` already equals the queued manifest endpoint, so `pumpReplay()` has no records to send.
- The Phone still persists the manifest and independently verifies exact canonical equality before sending its existing acknowledgement.
- The Watch still validates active session, non-backward/in-window endpoint, and the queued endpoint before attempting deletion.
- `SourceJournal.acknowledgeCompletedSegment()` still rejects an incorrect hash, session, or endpoint, and rejects a repeated acknowledgement after deletion.
- `closeBoundedFreshRuntimeAfterDeliveryIfComplete()` still writes `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` only after exact acknowledgement has removed all finalised manifests; equality of indices alone cannot complete the run.
- `SourceJournal.kt` was restored byte-identical to its protected digest:

  ```text
  24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361
  ```

- No canonical bytes, journal hash algorithm, acknowledgement wire format, egress behavior, legacy/frozen root, Watch58 material, unclassified Watch68 v1 session, or unavailable Exact Range Readback capability changed.

## D. Regression evidence

### RED

The first targeted run against the pre-correction implementation completed **24 tests** with **2 failures**:

1. retained terminal manifest selection at `lastRecordIndex == durablePhoneRecordIndex`; and
2. finalized-manifest delivery planning at that same equality endpoint.

This reproduces the source defect without a device-side guess.

### Final GREEN

| Validation | Result |
|---|---:|
| Focused Watch replay/journal/lifecycle/frozen-boundary suites | **45 passed; 0 failed; 0 errors; 0 skipped** |
| Complete Watch JVM suite | **140 passed; 0 failed; 0 errors; 0 skipped** |
| Complete Phone Node source-learning suite (unchanged Phone source) | **75 passed; 0 failed; 0 errors; 0 skipped** |

The new/strengthened tests cover:

1. all terminal data already durable at the Phone, then one retained terminal manifest becomes queueable;
2. no duplicate data replay in that equality-only case;
3. ordinary later-manifest replay remains preferred over an earlier equality candidate;
4. repeated resume cannot queue the same manifest twice while its endpoint is already queued;
5. wrong session and wrong endpoint acknowledgements fail before deletion;
6. wrong hash does not delete the retained segment;
7. one exact acknowledgement deletes that segment; a repeated acknowledgement does not; and
8. the final Watch completion marker remains ordered after verified acknowledgement and absence of finalised manifests.

The full suite initially rejected an attempted `SourceJournal.kt` change through its byte-identity contracts. That rejection was accepted as a preservation boundary: the final correction was moved entirely into `SourceReplayWindow` plus the GATT caller, and the protected digest is green again.

## E. Status and next physical boundary

The installed Watch69 application predates this correction. It cannot be used to retry the existing run.

**No device step is authorised by this receipt.** The shortest future path is:

1. separately authorise a same-package, same-signer, greater-version corrected Watch candidate build and artifact admission (**Watch70** if the existing sequential versioning remains unchanged);
2. separately authorise its ordinary in-place installation-only admission, with no uninstall/data clear;
3. only after that, separately authorise one bounded Phone57 normal reconnect/resume observation of the existing retained Watch69 session—no new recording and no Watch Main-HUGR launch.

A future attempt succeeds only if it visibly captures the retained terminal manifest’s **new range, count, byte count and SHA-256**, the exact Phone acknowledgement after canonical equality, no integrity/data-loss condition, and the matching Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` followed by `BOUNDED_RUN_GATT_STOP_REQUESTED`. Any missing condition is a stop, not a retry.

## F. Preserved states

- Watch69 run `b561b698-8206-4cb5-8717-95a5c9f533e2` remains **NOT QUALIFIED**.
- Watch68 v1 remains **UNCLASSIFIED / NOT VERIFIED** and untouched.
- Watch58 remains exactly: **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**.
- The retained egress binary compatibility payload remains unchanged and unvalidated; no egress claim follows.
- The existing external sanitized fallback remains retained until the new remote closeout is independently verified.
