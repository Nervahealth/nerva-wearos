# Eight-hour terminal investigation — exact remaining blocker

**Disposition: cause captured; narrow cursor correction has equivalence coverage but does not clear terminal admission. Watch84/Phone66 builds remain unsubmitted.** This is native-x86 synthetic evidence, not Honor performance or physiological qualification.

## What the failure actually is

The captured `failureReason` and durable `custodyFailureCode` both say **`DURATION_OR_CANCELLATION_BUDGET_EXCEEDED`**. The original eight-hour failure was not diagnosed as missing canonical bytes, hash corruption, a sensor cutoff failure, or a Samsung sequence problem.

The source-bound V2 synthetic fixture contains **967,830 records, 78,936,626 canonical bytes and 478 exact manifests/Watch-accepted ACKs**. The 478 event-driven segment inventory was already independently reconstructed against the existing journal rotation rules; it is not an inferred 480-minute count.

One ingestion replay was necessary to capture the original failure cause because the first test had removed its temporary app state. That diagnostic reproduction ran one test and failed, with pre-assertion cause and quiescent state retained. No passing lower rung was routinely repeated. Subsequent measurements and the corrected terminal test reused retained production-writer canonical/database bytes without another ingestion replay.

## Measured private-verifier bottleneck

The existing diagnostic seam ran on a read-only disposable copy of retained synthetic state. One measurement test passed in **409.676 seconds**, but this is **not** an admission pass. It retained the original cumulative 180-second clock and used isolated clocks only to measure stages not completed cumulatively.

| Private verifier stage | Measured elapsed | Of which cursor work | Disposition under original cumulative clock |
|---|---:|---:|---|
| Initial SHA/metadata | 1.157 s | none | Completed |
| Manifest plans | 0.023 s | none | Completed |
| Record index and manifests | 57.706 s | 55.278 s | Completed |
| File-offset coverage | 141.755 s in isolated diagnostic | 139.532 s | Cumulative deadline expired after 898,123 rows |
| Stream sequence and gaps | 67.617 s in isolated diagnostic | 66.430 s | Not reached cumulatively |
| Final SHA/size | 0.322 s in isolated diagnostic | none | Not reached cumulatively |

A separate source fingerprint/copy cost was 4.587 seconds. Measurements include test instrumentation; isolated stage completion is not permission to combine widened production clocks. The cost is dominated by repeated SQLite cursor work, particularly the file-offset ordering pass, not final hashing.

## Correction and earned results

Android 28's pinned SQLiteCursor bytecode confirms that normal refills can start behind the requested next position. The existing selected-record abstraction is forward-only. The narrow correction sets **`setFillWindowForwardOnly(true)`** before first movement, retaining the fixed **8 MiB** window and adoption-aware fail-closed cleanup.

All SQL projections, selected-session predicates, three ordering clauses, metadata type/null/hash checks, CRC/header/byte comparisons, manifest membership, file-offset coverage, sequence/gap checks, initial/final hashes and deadline constants are unchanged. API <28 and generic cursors keep their original configuration path. The source equivalence proof confirms exact unchanged code outside this configuration function.

| Affected gate | Actual result |
|---|---|
| Phone source contracts | 163 passed, 0 failed, 3 explicit fixture-dependent skips |
| Native API28 >8 MiB/permuted three-order row equivalence | Passed |
| Native malformed metadata/lineage fail-closed equivalence | Passed |
| Generic cursor behavior and ownership | Passed |
| Native retained-writer fresh-envelope terminal test | **1 failed, 0 errors, 0 skips; 209.839 s JUnit time** |
| Completed verified report, restart/offline, private export, independent reader | **Not reached in that affected terminal run** |

The corrected terminal test still captured **`DURATION_OR_CANCELLATION_BUDGET_EXCEEDED`** before its `COMPLETE_VERIFIED` assertion. Therefore the correction does not meet the end-to-end build condition. Its three cursor tests are not evidence that eight-hour terminal custody now fits; there is no claimed speed-up value for the corrected full verifier because no new per-pass measurement was performed after it.

The new synthetic test deliberately creates a **fresh disposable report envelope** over exact retained writer bytes. It does **not** reset or promote the original failed receipt. The existing `NO_CUSTODY_FAILURE_CANNOT_BECOME_VERIFIED` latch is intentionally preserved. Existing writer/Watch ACK proof is composed with the affected terminal test; the latter is not represented as another complete ingestion replay.

## Exact remaining blocker and next boundary

**The selected-record terminal verifier still exceeds its unchanged 180-second cumulative processing budget for the source-bound V2 eight-hour workload.** Consequently no current eight-hour durable COMPLETE_VERIFIED report, restart/offline recheck, selected private export or independent package readback has been earned for the corrected path.

The next bounded engineering target is the selected SQLite ordered cursor's repeated query/projection/sort work across window refills. A bounded forward paging approach could be proposed and tested against the existing exact row/order/count/type and canonical proof; it is not implemented or declared correct here. There is no justification in this result for raising the deadline, removing a pass, substituting metadata for bytes, or another physical recording.

**Conditional candidate authority remains recorded but unconsumed:** no Watch84 or Phone66 build was submitted because its admission requirements did not all pass.

## Preservation and unchanged boundaries

All **1,920 original retained failed-state files, 662,542,462 bytes**, were independently hash/size checked again after the affected test and match the original retention manifest. Original failures, old measurements and the new pre-assertion failure remain preserved. Lower synthetic 5/30/120-minute passes and the five-minute physical PASS are unchanged. The sanctioned native workspace remains isolated; no IDC evidence or ledger changes occurred.

Sanitized native-result ZIP: `HUGR_EIGHT_HOUR_FORWARD_CURSOR_TERMINAL_STOP_2026_10_22.zip`, **101,390 bytes**, SHA-256 **`63b8d9f8d12f5236b5cb561887336119a57d418f769092167e22b8e311b5fb0d`**. It contains synthetic results, JUnit, pre-assertion diagnostics and retained-state integrity metadata—not canonical bytes, actual recordings, private physiology, credentials or provider correspondence.

The cursor/source gate was already non-force preserved and independently read back on all three branches. This final result receives receipt-first → Master → Alignment-last documentation closeout followed by both-repository write preflight, non-force mirror and independent exact remote head/tree/blob verification. No private proposal is affected. GitHub source preservation is not a recording backup.

No installation, physical recording, sensing, Bluetooth/Watch action, real-data operation, additional receipt export, Samsung contact, cutoff/drain change, architecture review, IBI consecutiveness/RMSSD qualification, baseline update, learning, Loop A or N1. Samsung's native-sequence and final-batch questions remain connected but separate. T2.2, accepted baseline adapter/RMSSD companion, October reconciliation and S4 unchanged.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_EIGHT_HOUR_TERMINAL_INVESTIGATION_BLOCKED
