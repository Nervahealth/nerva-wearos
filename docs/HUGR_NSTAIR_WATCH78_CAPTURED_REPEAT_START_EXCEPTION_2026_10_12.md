# Watch78 saved diagnostic video — captured repeat-Start exception

## Decision

**The video is sufficient to identify the captured uncaught UI exception. It matches the repeat-Start gate defect already corrected and tested on 2026-10-11. No further filming, physical diagnostic retry, source correction or diagnostic-only build is needed to identify this exception.**

It does **not** capture the first `receipt unavailable` exception or establish its cause. The trusted Android app-anchor alias correction remains source/test-demonstrated, not proven as that first physical exception.

## Directly visible evidence

Uploaded by Neil on 2026-10-12: `VID_20261012_154331.mp4`, 21.4155 seconds. Original retained unmodified in the private upload area; checksum and extracted inspection frames retained outside Git. No raw video/frame/private physiological data published to GitHub or elsewhere by this task.

| Field | Visible value |
|---|---|
| Report type | HUGR SAVED DIAGNOSTIC v1; operational report, NOT recording verification |
| Package | `com.hugr.wearos` |
| Version | `78 / 0.78.0-compatible-fresh-run-receipt-candidate` |
| Report wall time | `1791706251463` |
| Elapsed time | `853151238` |
| Attempt / source session | `unknown / unknown` |
| Stage / outcome | `UNCAUGHT_EXCEPTION / EXCEPTION` |
| Cause | `java.lang.IllegalStateException` |
| First source frame | `com.hugr.wearos.BoundedNormalStartupGate.deferFreshScope:25` |
| Caller | `MainActivity.beginFreshOrdinaryScopeAfterRetainedDeliveryProbe:241` |
| Button callback | `MainActivity.createEvidenceLayout$lambda$6$lambda$5:114` |
| Android dispatch | `View.performClick`, then Handler/Looper main-thread frames |
| Terminal fields | `UNCAUGHT_EXCEPTION / EXCEPTION`, wall time `1791706251508` |
| End | `END REPORT` visible |

Wall-time conversion with Europe/Oslo: **2026-10-11 10:10:51.463 CEST**; terminal field at **10:10:51.508 CEST**. This is the saved report's own clock, not an independently synchronized acquisition timestamp. It is dated the morning of the reported Watch78 failure, not the upload day. No new recording/retry inferred from the video filename or upload time.

Inspection references: header at approximately 3–4 seconds, cause/top frames at approximately 6–7 seconds, caller/button frames approximately 8–11 seconds, terminal/end around 16 seconds. Some text is finger-obscured, round-screen-clipped or blurred during scrolling; this is a selected-field transcription, not a byte-exact full-report export. The essential class/top source/caller frames are readable across adjacent frames; no missing text was reconstructed from guesses.

## Exact source match and interpretation

Pre-correction controlled source has at line 25 of `BoundedNormalStartupGate.kt`:

```kotlin
check(phase == Phase.NOT_STARTED) { "Normal fresh scope is already scheduled" }
```

Pre-correction `MainActivity.kt` line 241 calls `startupRecoveryGate.deferFreshScope()`; line 114 is the Start-button call to `beginFreshOrdinaryScopeAfterRetainedDeliveryProbe()`. Those three source locations match the captured stack. The exception message itself is deliberately omitted by the diagnostic format; its expected source message is not represented as captured text.

This establishes a real **uncaught main-thread exception in the repeat/scheduled-Start boundary**, consistent with Neil's second-press tray return. It is not a BLE exception, Phone custody failure or timeout stack. It does not prove the complete earlier state sequence, why the first admission/read failed, whether sensors ever ran, byte loss, completion or qualification. `sourceSession=unknown` prevents attribution to a newly recorded source session.

Already-preserved correction: local Watch source `c25bc2536099a8111a6d4d08ec9a8a6b5521c068`, remote mirror `40d5b0355c87c8b24f5dd9fb4bfcdb942d75bcfe`, exact tree `284986522fcd8725fef6a83a8417f388036a59a9`. See `HUGR_NSTAIR_WATCH78_FRESH_START_SOURCE_CORRECTION_2026_10_11.md`: failure latch, blocked repeat action, one admission schedule, off-UI serialized receipt reads and pending-read/action exclusion; trusted anchor normalization preserves child-path/integrity rejection.

Prior successful validation remains unchanged, not newly rerun: Watch217 total/213 pass/4 fixture-dependent skips; Phone142 total/139 pass/3 fixture-dependent skips; all six joined acceptance phases pass including94/94 native report/restart/offline tests. Synthetic only; the corrected source is not yet installed or physically validated.

## One proposed next boundary

Recommend **one greater-version signer-continuous Watch-only corrected candidate build/admission (Watch79)** from the already-tested correction, keeping Phone61 unchanged. It is a recording-instrument repair, not a diagnostic-only candidate. This upload does not authorize that build, installation, Main launch, Start, Connect, recording retry or private-data sharing. Ask for explicit build/admission-only authority; device actions remain separate. If build/admission identifies a new complete-workflow blocker, present it before proposing physical execution.

Target remains one fresh five-minute run → controlled finalization → exact verified Phone custody → source-session-bound Watch ACK acceptance and completion → durable Phone report → offline custody recheck after app restart, without routine Watch filming. Duration ladder and existing live-observation bridge follow physical acceptance; unavailable historical proof is not a prerequisite.

## Boundaries and preservation

This turn: video inspection, read-only source comparison and sanitized documentation only; no production change/test rerun/build/device/recording/Bluetooth/ACK/egress/storage change. Do not infer viewer-button presses from finger movement. Existing private VIS-COUNT proposal and accepted S4 unchanged; denied access not retried. Automatic source-owned receipt → Master → Alignment last → documentation commits → controlled non-force GitHub mirror/readback applies. Only sanitized findings are committed.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
