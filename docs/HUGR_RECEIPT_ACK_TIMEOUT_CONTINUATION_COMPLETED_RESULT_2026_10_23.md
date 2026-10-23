# HUGR receipt/ACK timeout — source/test continuation completed

**23 October 2026. Verdict: required source/test cases PASS; physical timeout cause remains UNPROVEN.**

## Engineering decision

The unfinished Watch setup is resolved without bypassing readiness or manufacturing a manifest. The actual synthetic connection → MTU → source CCCD → asynchronous resume prepare/apply → source queue → exact ACK path executes. The final Watch JUnit integration test passes all 16 recorded callback operation cases, including the previously missing branches. The corrected Phone source-bound harness passes 10/10 cases; permanent Phone failure/diagnostic module tests pass 9/9 with exit code 0.

**A specific mechanism can reproduce the timeout boundary:** an injected hold on the real Watch session monitor delays the actual synchronous R1:0 callback response to **10,102.383 ms**, beyond the unchanged Phone 10,000-ms operation deadline. The Phone tests independently execute its exact production deadline/cancellation/late-settlement paths. These establish susceptibility and containment for that mechanism, not that the monitor was blocked during Neil's physical run. No naturally occurring ten-second stall was observed in the small synthetic fixture.

No deadline increase, FIFO, moved timer, weakened validation, early success response, build, signing, installation or physical-device action was performed. There is no remaining external blocker to this source/test investigation.

## Why readiness was null

Three test prerequisites were incorrect or missing; none requires a production readiness change:

1. The old harness did not invoke the actual source-record CCCD enable callback. Source release correctly remained held.
2. Callback-local service creation in the direct Robolectric environment did not produce a GATT server. A narrowly scoped **test-only BluetoothManager shadow** supplies the Android server object; actual production `initializeBluetooth()` / `openGattServer()` constructs and registers the HUGR service, characteristics and source CCCD. The test retrieves those actual objects.
3. The test used **MTU 128**, sufficient for receipt framing but not source release. `SourceMtuReadinessGate` requires `SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL = 364`. The observed ATT payload at 128 was only 125. The harness now invokes the real MTU callback with **512**, matching the Phone production request; no minimum or guard changed. Presence of an MTU callback is not itself proof of sufficient payload.

After these setup corrections, actual resume preparation seals/captures the synthetic journal's manifest, posts the real asynchronous apply, releases the real readiness gate and queues the exact manifest endpoint. The readiness wait remains five seconds. No ready flag, manifest, hash, queue endpoint or ACK state is assigned to evade the gate.

The incomplete runs and low-MTU failures remain immutable as evidence that the guards held. The previously earned callback PASS is separately retained before the final diagnostic change. Hardware/service sensing startup is deliberately excluded from this callback harness.

## Executed coverage

| Required path | Earned result |
|---|---|
| Actual source CCCD and immutable R1 zero/nonzero write/read | PASS |
| Actual connection/MTU/prepare executor/main-handler apply/readiness release/manifest queue | PASS |
| Wrong ACK hash, source session and endpoint | Each GATT_FAILURE; no durable ACK, exact manifest retained |
| Concurrent actual Watch receipt write and hash-invalid ACK callback | Exactly one response each; no extra ACK |
| Exact valid Watch ACK | PASS: exact session/endpoint/hash accepted; durable receipt reopened and exact accepted manifest removed **before the response observer is invoked** |
| Duplicate exact ACK after removal | Current contract rejects it; accepted ACK count remains one |
| Injected real session-monitor contention | Real R1 callback completes after 10,102.383 ms; late response is not timely Phone acceptance |
| False `sendResponse` | One false response result; bounded diagnostic persisted after response; no retry |
| Null GATT server | No fabricated response; bounded null-outcome diagnostic persisted |
| Late accepted `sendResponse` | New bounded post-response diagnostic records TRUE **and** `lateForPhoneOperation=true`; TRUE is not a claim of timely delivery |
| Phone exact ten-second R1/read/ACK deadlines | PASS: one cancellation/cleanup boundary; no deadline expansion |
| Phone late fulfillment/rejection | Diagnostics only; no later C1, ATT-success evidence, ACK success or terminal completion |
| Actual production Phone ingestor's rejected/timed-out ACK | Transport failure, not canonical corruption; durable ACK-write intent/failure semantics retained |
| Explicit fake Phone receipt/ACK submission overlap | PASS at injected adapter queue seam only; native serialization remains unknown |
| Receipt immutable page-hash rejection | PASS |
| Diagnostic redaction and retention | Phone 40 events/160 characters retained; opaque IDs/hashes/addresses/tokens redacted; Watch existing eight-report isolated store retained |
| Permanent typed ACK failure classifier tests | Only typed `SOURCE_ACK_WRITE` + exact deadline code classifies as ACK transport timeout; other contexts/text do not masquerade as one |

The Watch server shadow observes response attempts and supplies Android notification API acceptance only. It does not supply source custody, hashes, manifests, ACK state or radio delivery. The test is one JUnit integration test with 16 recorded operation ordinals, **not 16 independent JUnit tests**.

## Measured timing and limits

Final callback-entry marker → response-attempt marker timings in the tiny direct JVM fixture:

- R1:0 write: **5.674 ms**.
- R1:0 read: **0.269 ms**.
- Source-resume command response: **2.795 ms**; asynchronous source readiness is checked separately afterward.
- Exact valid ACK, including the observer's durable reopen check: **17.954 ms**.
- Largest non-injected recorded interval: **25.686 ms**.
- Deliberately injected session-monitor delay: **10,102.383 ms**.

These are observed host/test intervals, not Honor performance, native ATT dispatch, radio round trips, a thirty-minute workload measurement or physiological evidence. Phone deadline cases use a deterministic clock. The mechanism comparison does not subtract or align Watch and Phone clocks.

## Narrow source/test corrections

### Phone

`lib/bluetooth.ts` now preserves the raw `RECEIPT_TRANSACTION_DEADLINE` compatibility code while explaining the failed operation: receipt page-request write, page read, confirmation write or source ACK write, with an R1 offset where known. It says connection was established and the receipt was not completed; it no longer tells the user that the Watch app must not be running.

Existing bounded recovery diagnostics add attempt-local operation ordinal/kind, safe R1 offset, JS submission, timely/rejected settlement, deadline, cancellation attempt/outcome and ignored late results. **`native_att_dispatch=UNKNOWN` is explicit.** C1 is classified only by its fixed encoded prefix; diagnostic code does not decode its body. Receipt/source protocol bytes remain unchanged.

`lib/freshRunFailureBoundary.ts` retains a typed source-ACK timeout as a transport failure rather than an integrity/corruption claim. An intermediate continuation implementation exposed a classification regression when the new typed timeout escaped the ACK wrapper; that regression was corrected and tested before closeout. It is not retrospectively attributed to Phone66 or the physical failure.

`lib/recoveryDiagnosticModel.ts` adds the bounded operation stage and identifier/hash redaction. `tests/freshRunFailureBoundary.test.ts` adds permanent positive and negative typed-timeout classifier regressions. Exact ten-second behavior, page/source/hash checks, ACK intent/write failure semantics and terminal containment remain intact.

### Watch

`BleGattService.kt`, `SavedDiagnosticReport.kt` and `WatchDiagnosticRuntime.kt` now preserve payload-free response outcome diagnostics for relevant receipt/source-control callbacks: false platform return, missing server, or response accepted after ten seconds. The diagnostic-only clock is explicitly **process-local `System.nanoTime`**, unrelated to acquisition timestamps or cross-device alignment. The ten-second comparison is a reporting threshold, **not a callback timeout, abort rule or changed deadline**.

Persistence is posted **after** the original response attempt. Timely successful operations create no new persisted ATT report. No synchronous diagnostic file I/O is added ahead of ATT; no response retry, fabrication, ACK rollback, status rewrite, source/receipt mutation or transport policy change is introduced. No payload, actual UUID, manifest hash, record value, address, receipt body or exception message is saved in the new ATT outcome report. The actual final test verified three persisted reports: late TRUE, FALSE and NULL.

The demonstrated original shortcomings are inaccurate failure wording and unobserved platform response failure/omission. These corrections improve evidence and truthful failure classification; they do not constitute a proven cure for the historical radio/native timeout.

## Exact remaining uncertainty and minimum evidence

The physical run's raw `RECEIPT_TRANSACTION_DEADLINE` does not identify which receipt write/read failed, whether the request reached the Watch callback, whether a callback waited on storage/session work, or whether Android/native transport omitted or rejected the response. The supplied video and existing installed artifacts do not answer those questions.

The minimum evidence needed for a future separately authorized resolution is:

1. Phone operation kind/offset/ordinal and its own submission → deadline/cancellation/late-settlement timeline.
2. The Watch-side corresponding callback's own entry → pre-response → platform outcome, including a false/null or late-response report when present.
3. **Only if no corresponding Watch response observation exists**, a bounded native GATT dispatch/callback/disconnect trace to distinguish non-arrival from interruption before reporting. Absence of a saved report is not proof of non-arrival.

Do not infer common-clock alignment, corruption, Watch absence, SDK failure or Bluetooth causality from a generic timeout. The source-level traces narrow future collection; native dispatch and the historical cause remain unproved. No further attempt, diagnostic opening, log capture, build or device interaction is initiated by this report.

## Source/evidence binding and preservation

The final Watch runner recompiles the changed production classes and places them ahead of the captured original closure. Production and test compiler exit codes are 0; JUnit exit code is 0. Retained ARM Java 17/Kotlin 1.9/Robolectric SDK34 and supported test-only Conscrypt-OFF/JDK crypto were used offline. No Gradle assembly, DEX or APK was made.

The Phone harness evaluates exact content-pinned production modules, including the actual source ingestor and failure classifier, with fake adapter/clock/native-storage seams. Its ten-case log and `pinned-corrected-source-manifest.json` are retained. Permanent pure-module tests have a separately captured reliable exit code 0 and 9 pass/0 fail; an earlier shell-status reporting failure remains history, not a test failure relabelled as PASS.

`completed-source-test-evidence.json` contains exact tested source hashes, changed paths and calculated timing evidence. Prior reproduction files were checksum-verified unchanged. Watch84 and Phone66 APKs remain the previously admitted bytes and **do not contain these new source corrections**. Their identities/build configurations were not changed.

Source-owned authority/result and bounded synthetic tooling/results are followed by Master and Alignment last; the seven affected source/test files and safe evidence are committed on the existing controlled branches, mirrored non-force after both-repository write preflight, then independently read back by exact remote head/tree/changed bytes. Completion is recorded in `continuation-independent-remote-readback.json` in this task folder; verify it before announcing remote preservation. No private VIS-COUNT or portal update. APKs, provider/runtime binaries, captured application copies and real recordings/images/video/identifiers remain excluded from GitHub.

The physical thirty-minute gate remains **NOT PASSED**; current Watch phase remains **UNKNOWN**. Prior five-minute physical PASS, artifact admission, duration evidence, all failed build markers, private recording evidence, T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, learning and Loop A boundaries remain intact. Samsung native-sequence/final-batch and endpoint/drain work remain separate. No IDC or N1 action.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_RECEIPT_ACK_TIMEOUT_CONTINUATION_COMPLETED_RESULT
