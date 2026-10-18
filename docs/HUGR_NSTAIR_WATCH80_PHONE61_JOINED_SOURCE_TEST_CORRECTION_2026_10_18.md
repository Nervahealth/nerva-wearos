# Watch80 / Phone61 — joined source/test correction, 18 October 2026

**Authority:** Neil authorised source/test investigation and correction of demonstrated defects at 19:14 Europe/Oslo. **No APK build, version/signing change, installation or device action is authorised or performed.** The installed pair remains Watch80 / Phone61 on Honor, containing the earlier source, not these new corrections.

**Checkpoint status: WORK IN PROGRESS — focused corrections validated; full joined validation running.** This is not final acceptance, candidate admission, or physical qualification. The full actual-wire joined result will be recorded in a dated completion addendum, not guessed from passing focused tests.

## Physical evidence remains separate

The 18 October fresh attempt remains **physically attempted / STOP / NOT QUALIFIED**. Its source-ingestion/subscription rejection, cancellation, receipt-write failure and Watch finalisation ordering are unresolved. The first passive diagnostic video showed a historical Watch78 exception on 11 October; it does not diagnose Watch80. The system grey-to-blue reconnect is preserved context, not operator blame or a demonstrated cause. No current canonical bytes, actual Watch run UUID, final manifest inventory, accepted ACK/completion or offline restart custody is inferred or reconstructed.

Owning physical record: `docs/HUGR_NSTAIR_WATCH80_PHONE61_FIRST_FRESH_PHYSICAL_RESULT_STOP_2026_10_18.md`. Historical diagnostic record: `docs/HUGR_NSTAIR_WATCH80_SAVED_DIAGNOSTIC_READBACK_HISTORICAL_2026_10_18.md`.

## Demonstrated defects and bounded corrections

### 1. Watch recording lifetime was coupled to Phone transport loss

In fresh mode, `BleGattService.STATE_DISCONNECTED` unconditionally called `stopFresh("PHONE_DISCONNECTED")`, failing the admitted session and stopping Health before the independent five-minute timer could quiesce and finalise. A new deterministic Android-service regression was RED before correction and GREEN afterward.

The correction leaves the original Health-owned recording deadline unchanged after one post-connection transport interruption. It resets only the transport lineage and permits at most one subsequent ordinary connection for the same admitted fresh run. Receipt-only disconnection, pre-connection/reused connection and another disconnect fail closed. It neither scans nor automatically reconnects the Phone, grants a retry, starts new sensing, extends the original timer, changes source bytes, nor weakens ACK checks. Source eligibility for a rejoin is not operator authority to perform one.

Files: `app/src/main/java/com/hugr/wearos/BleGattService.kt`; `app/src/test/java/com/hugr/wearos/FreshRunAndroidServiceFlowTest.kt`.

### 2. Phone callbacks could misattribute transport failure or affect a newer connection

The actual production `connectToDevice` callbacks lacked equivalent current-lineage guards on source-ingestor and source-subscription errors. Stale callbacks could fail a newer explicit connection; repeated callbacks could republish failure; a source-subscription transport error was classified as source-integrity failure. The actual production-callback regression gives the preserved baseline **1 pass / 3 fail**, versus **4 pass / 0 fail** after correction. Native/radio seams are explicit mocks; the production callback closures and receipt parser/observer are executed.

The correction gates source failure on the exact current connection lineage, records one initiating fixed substep before cancellation, and separates source-subscription/ACK write transport failure from exact source validation/persistence failure. Terminal results and delayed native-failure publication are guarded against later replacement. A pure fixed-code classifier adds no payload, device identity, raw exception, source export, server, email/SMS or telemetry infrastructure. Existing Phone recovery timeline and native per-run failure APIs are reused.

Files: `lib/bluetooth.ts`; `lib/freshRunFailureBoundary.ts`; `lib/recoveryDiagnosticModel.ts`; `tests/phoneFreshConnectionBoundary.test.ts`; `tests/freshRunFailureBoundary.test.ts`.

### 3. Repeated manifest endpoints could overwrite custody range metadata

The production TypeScript ingestor/native manifest writer accepted a repeated selected session/endpoint/hash whose stream-range tuples changed. Native persistence compared the main index/count/byte/hash fields but deleted/reinserted `manifest_ranges`. The production-wire altered-range regression was RED before correction.

The correction makes the complete manifest identity immutable: first/last indices, record/byte counts, SHA-256 and sorted range tuples. A collision is rejected before native persistence or ACK; the native writer independently preserves existing rows, including an empty-range-set guard. Exact duplicate replay still works. Canonical bytes, hashes and ranges must still pass exact native verification before ACK; metadata alone never proves custody.

Files: `lib/build45SourceIngestion.ts`; `tests/build45SourceIngestion.test.ts`; `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/HUGRSourceJournalModule.kt`; corresponding `PhoneProductionCustodyIntegrationTest.kt`.

## Joined coverage strengthened, not a wire-format redesign

The existing joined fixture used real Watch canonical records but fabricated single-record notification wrappers on the Phone. Read-only serializer/decoder comparison found layouts aligned; the wrapper was a coverage gap, not a demonstrated physical defect.

The corrected fixture exports actual `SourceReplayProtocol.buildDataFrames` notification bytes, in live/replay mode and per-manifest order, and the Phone consumes those exact bytes. The existing joined runner then checks exact Phone-generated ACK frames against a preserved real Watch pre-final-ACK replica, durable completed receipt identity, production native report publication, restart/offline reader and the complete Phone deterministic suite. No production wire format is changed.

Files: Watch `FreshRunReceiptTest.kt`; Phone `tests/freshRunJoinedAcceptance.test.ts`; `scripts/hugr_fresh_run_joined_acceptance.py`.

## Validation at the WIP checkpoint

| Check | Verified result |
|---|---|
| Phone production-callback baseline / correction | RED 1 pass / 3 fail; GREEN 4 pass / 0 fail |
| Initial safe-failure, receipt and integration checks | 22 pass / 0 fail |
| Phone protocol/ingestion focused tests | 23 pass / 0 fail |
| Native production custody integration | 15 tests / 0 failures or errors |
| Watch focused lifecycle/receipt/replay tests | 62 tests / 0 failures or errors |
| Watch full unit suite after production correction | 236 total / 0 failures or errors / 4 skipped |
| Actual-wire full joined runner | Running — not yet asserted GREEN |
| Repository-wide Phone TypeScript check | Not GREEN: archived harness/snapshot path errors; no diagnostics reported for current changed production files in the initial check |

Focused checks and Robolectric tests are simulated source validation, not physical Android/radio/sensor/runtime, clinical or N1 evidence. Protected SourceJournal and RetainedTimingExportActivity bytes remained unchanged. No egress route or legacy/frozen capability was changed.

## Next boundary — proposal only

If the full joined check passes, the smallest next authorised scope to request is one signer-continuous **integrated Watch and Phone candidate build/admission**, with a reviewed handling of the existing failed/unresolved run and one concise later physical acceptance gate. Building and all device steps remain separate; do not issue an installation or retry now. Do not reuse today's consumed physical permission or infer finalisation from elapsed time. No further blind diagnostic reproduction is required for these demonstrated source defects.

The target remains **one fresh five-minute recording → controlled finalisation → exact canonical Phone custody → attributable Watch accepted ACK/completion → durable Phone report → restart/offline readback**, then the duration ladder/eight-hour rehearsal and existing live Watchtower milestone. Research/clarification/GUI work remains a separate lane, not a prerequisite or new foreground feature.

## Preservation and exclusions

Standing closeout is automatic: source-owned receipt → Master → affected authorised private proposal only if applicable → Alignment last → bounded Watch/Phone/control commits → controlled non-force `scripts/hugr_preserve_mirrors.py` with both-repository write preflight → independent remote heads/exact trees and changed source/receipt blob readback. A local commit alone is not remote preservation. This WIP checkpoint must be labelled pending full joined validation; a later completion addendum will not erase it.

No S4/Atlas/private proposal change is affected or authorised. No image/video/private physiological bytes, device addresses, tokens, keystores, raw private logs, ignored clarification ZIP or Word edits enter this correction. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 exactly **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

## Preservation safety clarification — no evidence rewrite

The publication scan identified a new synthetic test-device literal and obsolete device-address literals already present in affected current control copies. The test address is now generated from fixture bytes, with identical simulated behavior. Complete obsolete address tokens in current Master/Alignment copies are replaced by `[DEVICE_ADDRESS]`; all surrounding factual statements, source/status identities and historical commits remain intact. Longer public signer SHA-256 fingerprints are not device addresses and remain unchanged. This is bounded data minimization, not source recovery, private-data export, evidence reclassification or a Git history rewrite.
