# HUGR NSTAIR — Watch69 Final-Delivery Replay Correction: Source/Test Receipt

**Date:** 2026-10-03
**Scope:** Authorised source/test-only correction of the Watch69 ordinary bounded-run final-delivery seam, with a narrowly truthful Phone-status clarification.
**Physical-device operations in this work:** **None.** No retry, reconnect, relaunch, new recording, egress action, transfer, verifier, ADB/Termux operation, or legacy/frozen-root operation occurred.

## Result

> **WATCH69 FINAL-DELIVERY SEAM — SOURCE DEFECT ESTABLISHED / MINIMAL REPLAY CORRECTION GREEN IN SOURCE TESTS / EXISTING FINALIZED RUN IS SOURCE-ELIGIBLE FOR ONE FUTURE STANDARD REPLAY ATTEMPT.**

The existing five-minute run remains **not qualified**. Its observed state is unchanged:

> **WATCH FINALISATION OBSERVED / FINAL PHONE DELIVERY ACKNOWLEDGEMENT NOT OBSERVED / SHORT RECORDING NOT QUALIFIED. STOP.**

This correction establishes a bounded engineering route; it does not retroactively create a final Phone acknowledgement, canonical verification, `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, Gate-1 result, longer run, eight-hour result, or independent verification.

## Source-established defect

### 1. Finalization occurred after the resume window froze

At normal Phone resume, `BleGattService.prepareSourceReplay()` freezes `replayHighWaterRecordIndex` from the durable finalized journal state. During the Watch69 run, the Phone had already acknowledged the early `1–11` segment. The later bounded stop force-synced and finalized the active fresh segment after that frozen high-water.

`HealthSensorService.finalizeBoundedOrdinaryRun()` then correctly records `BOUNDED_RUN_FINALIZED` and broadcasts `ACTION_SOURCE_FINALIZED`. However, the receiver previously called `enqueueNewlyFinalizedManifests()`, which:

1. drained the in-memory newly-finalized signal;
2. rejected the new final manifest because its endpoint lay above the stale high-water; and
3. returned without expanding the replay window or scheduling the normal manifest/data replay.

This precisely fits the observed physical evidence: the Phone retained early acknowledgement `1–11`, while the final session records did not produce a final manifest acknowledgement.

### 2. Direct final-manifest queueing also bypassed the acknowledgement endpoint

The former finalization helper directly enqueued a manifest notification. It did not establish `queuedReplayManifestEndIndex`, but `handleSourceAcknowledgement()` correctly requires the acknowledgement endpoint to equal that value before allowing durable segment deletion. Thus bypassing the normal queue could not safely acknowledge a final manifest.

Both defects are source-established. No diagnostic-only build is required to choose the correction.

## Implemented Watch correction

`SourceReplayWindow.planFinalizedManifestDelivery()` now derives a bounded delivery plan from the **durable finalized manifests for the active session**:

- it extends the current replay high-water only through the last durable unacknowledged finalized manifest;
- it selects the next manifest through the existing normal replay-window ordering rule; and
- it never treats an acknowledgement as valid unless the normal queued-manifest endpoint has been set.

`BleGattService.enqueueNewlyFinalizedManifests()` now drains the short-lived signal only to notice finalization, obtains the durable manifest set, applies the bounded plan, and calls the existing `enqueueNextManifestForReplayWindow()` path. That path sets `queuedReplayManifestEndIndex`; `pumpReplay()` sends canonical records only through that manifest endpoint; the existing Phone acknowledgement then remains subject to exact session/index/hash equality before any segment can be deleted.

The correction does **not**:

- change the wire protocol or Phone acknowledgement format;
- change source persistence, canonical bytes, manifest hashing, or deletion rules;
- alter normal/egress separation or activate egress;
- access, enumerate, decode, hash, acknowledge, migrate, prune, export, or delete legacy/frozen roots; or
- alter the unclassified Watch68 v1 session.

`HealthSensorService` still stops sample admission, quiesces trackers/timers/receivers, force-syncs, finalizes, force-syncs, marks finalization, broadcasts delivery availability, and leaves standard GATT alive until existing exact acknowledgement handling completes.

## Phone status clarification

The Phone’s durable ingestion sequence was already correct: it persists a received manifest, durably appends contiguous canonical frames, verifies exact equality, exports the accepted path, and only then writes the existing exact acknowledgement.

A narrow UI/status classifier now distinguishes:

| Evidence state | Rendered meaning |
|---|---|
| Live streams are stale and Watch-record count exceeds the latest exact manifest acknowledgement | `Live acquisition no longer current · final canonical delivery pending` |
| Live streams are stale and the acknowledged endpoint reaches the Watch-record count | `Live acquisition ended · final canonical delivery acknowledged` |
| Explicit integrity failure | `Source integrity failed` |

This prevents an expected end of live acquisition from being labelled as a proven transport failure while still not masking an actual integrity failure. It is a source-only Phone change; **no Phone build, installation, or runtime observation is included**.

## Regression evidence

### RED

The new Watch stale-window regression initially failed compilation because `planFinalizedManifestDelivery()` did not exist. This is the intended RED proof that the prior source had no safe finalization-window repair primitive.

### GREEN

| Validation | Result |
|---|---:|
| Watch focused: `SourceReplayWindowTest`, `SourceJournalTest`, `Build49ManifestWindowRedTest`, `OrdinaryRuntimeCandidateContractTest` | **32 passed; 0 failed/errors/skipped** |
| Watch complete JVM: `:app:testDebugUnitTest --rerun-tasks` | **137 passed; 0 failed/errors/skipped** |
| Phone focused protocol/ingestion/status suite | **24 passed; 0 failed/errors/skipped** |
| Phone complete source-learning Node suite | **75 passed; 0 failed/errors/skipped** |

The new Watch regressions prove:

1. a terminal segment finalized after a frozen resume high-water extends delivery only to its durable endpoint;
2. its manifest is queued through the ordinary endpoint mechanism;
3. no later sensor sample is required for the terminal records to become replayable;
4. exact acknowledgement validates at that terminal endpoint and deletes no other segment; and
5. the same retained final segment is discoverable by the existing standard reconnect/resume query.

The new Phone regressions prove the pending/completed status distinction without changing integrity-failure precedence or the existing exact-manifest acknowledgement path.

Compiler warnings were pre-existing/deprecation/non-fatal warnings; neither full suite reported a test failure, error, or skip.

## Existing Watch69 run: replay eligibility

The existing run is source-eligible for a **single future standard normal BLE reconnect/resume delivery attempt**, not a new recording:

1. The observed `BOUNDED_RUN_FINALIZED` marker is written only after the fresh active segment’s guarded sync/finalization sequence succeeds.
2. Finalized segment bytes are retained until `acknowledgeCompletedSegment()` accepts the exact session, endpoint, and SHA-256 acknowledgement. No final acknowledgement was observed for this run.
3. On normal Phone resume, `prepareSourceReplay()` re-enumerates retained finalized session(s), obtains their durable high-water, and `enqueueNextManifestForReplayWindow()` can emit the next retained manifest. The Phone ingestor already supports manifest-first replay and verifies/acknowledges after durable contiguous canonical data.
4. The new correction repairs the immediate post-finalization path so no later sensor sample is needed to trigger that normal manifest/data delivery.

This is a source/test conclusion. It does **not** independently inspect the Watch’s private storage or prove the final manifest is physically available until a future authorised standard reconnect/resume attempts it. No source path permits its deletion without the exact acknowledgement, and no such acknowledgement was observed.

## Exact limits and preserved states

- Watch69 run `b561b698-8206-4cb5-8717-95a5c9f533e2` remains **not qualified** until the final Phone manifest equality, exact acknowledgement, and Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` marker are actually observed.
- The late passive opening of Startup Readiness was delayed observation only; it did not change the marker chronology or cause the missing acknowledgement.
- Watch68 v1 remains **UNCLASSIFIED / NOT VERIFIED** and untouched.
- The original `ExactRangeReadback.kt` remains unrecovered; no launcher/source was fabricated or restored.
- The retained egress binary compatibility payload is unchanged and unvalidated. No egress claim follows.
- Watch58 remains exactly: **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**.

## Future physical qualification gate — separately authorised only

Do not act on either device under this receipt. If separately authorised, the shortest route is **not another recording**:

1. Open normal HUGR on Phone57 and make one ordinary `Connect Galaxy Watch` action; do not use egress.
2. Allow the unchanged retained Watch69 session to resume/replay. Do not relaunch the Watch normal app or start a new run.
3. Stop and preserve the first result if any prompt, unexpected surface, connection failure, integrity/data-loss indicator, or changed session identity appears.
4. Treat the existing run as qualified only if all of the following are observed and attributable to its final session: final manifest equality at the final range/hash, exact Phone acknowledgement, Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, and canonical/verification evidence with no integrity/data-loss condition.
5. If that route does not reach all conditions, stop without retry. A fresh one-run procedure would require new authority and must begin only after this source correction is built/admitted through its own signer-continuous candidate path.

No candidate build is included in this correction receipt.

## Preservation closeout

Local source commits preserve the tested correction as Watch `31fc70c`, Phone/control `6107dd5`, and standalone control records `c276c50`. GitHub publication was attempted through the configured `Nervahealth` account for the Watch branch, but Git smart-HTTP returned **403** before any branch could be claimed remotely updated. The Phone/control pushes were not attempted after that stop. GitHub API metadata reported `ADMIN` visibility, which does not make the failed Git write successful.

The fallback is a sanitized source/control snapshot containing those commits, with `debug.keystore`, credential-like filenames, private tabular/database/media evidence, and Git metadata excluded. Its SHA-256 is `e37cb9d41b3d8ad3921414f7528111aedfa7bfe12e3528ce2c3c92b95c77a60f`.

- Archive: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/oxbxUrugMipkPIXq.gz
- Checksum file: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/AUwSBQSibTgfcege.sha256

This fallback is durably uploaded but **does not replace GitHub remote verification**. No credential/token/key has been stored in it. Remote branch heads remain unverified until GitHub write access is restored and the local commits are pushed and read back.
