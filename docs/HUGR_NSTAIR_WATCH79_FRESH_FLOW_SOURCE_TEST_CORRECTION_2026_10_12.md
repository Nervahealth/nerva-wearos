# Watch79 focused fresh-flow source/test correction — 2026-10-12

## Result and authority

**SOURCE/TEST COMPLETE — physical attempt NOT ACCEPTED / INCOMPLETE EVIDENCE.** Neil authorized focused Watch79 source/test investigation and demonstrated correction only, then corroborated Settings `0.79.0 corrected fresh start…`. No new APK, version change, signing, installation, device launch, connection, recording retry, private-data transfer or N1 action was performed. Phone61 production source and its admitted APK remain unchanged.

The supplied Watch79 clip showed `Fresh STARTED · NONE`. STARTED means admission, not sensor records, Health entry, an armed timer or finalization. NONE means no recorded failure reason. Neil identified the later button as the **physical side button**, not a reported second Main launcher tap. Phone61 remained unopened, with no Connect. Exact Start time, process continuity and the final receipt state remain unknown. The supplied Saved Diagnostics video showed the **historical Watch78** repeat-Start exception, not a new Watch79 report. The historical second-Start crash is source-matched; the first receipt-unavailable error remains not physically diagnosed. Nothing here retroactively diagnoses either Watch79's black screen or its observed flashing as an Android crash.

## Demonstrated defects and bounded corrections

| Boundary | Before-correction evidence | Correction and tested limit |
|---|---|---|
| Unchanged UI receipt refresh | Real Activity regressions: three identical reads produce six TextView resets; pending reads visibly disable/re-enable ready controls | Assign changed text only; stable ready-control presentation with logical read/action exclusion; block initial pre-read actions; preserve fresh receipt on foreground return. Label explicitly separates admission from sensor proof. This demonstrates presentation churn, not the physical black-screen mechanism. |
| Fresh GATT foreground rejection | Actual Main Start → actual GATT start dispatch under injected Android promotion rejection: FAILED receipt/zero records/no Health dispatch, but zero diagnostic reports instead of one | Preserve the original `SecurityException`, failed stage and actual run/source identities before cleanup. The original is no longer swallowed in the foreground helper/outer catch. |
| Health dispatch/stop handoff | Source trace: STARTED receipt predates Health entry and stop timer; missing Health previously had only the longer delivery timeout | Require same-session actual `begin()` and successful timer posting; GATT fails closed at 15 seconds if that handoff is absent. The acknowledgement is process-local liveness, not a durable receipt, sample, finalization or ACK proof. |
| SDK setup and failure callback | Capability lookup outside containment; tracker failures could continue later registration; actual failed-SDK-callback regression produced zero saved reports instead of one | Arm the five-minute elapsed stop before SDK connection; capture original constructor/capability/tracker/callback exception before quiescence and cleanup; fail receipt and return without later registration. Preserve the SDK handle until cleanup disconnects it. Ignore a failed SDK callback after controlled finalization. |

The first service-flow test attempt also exposed test-platform failures: Robolectric cannot instrument the vendor AAR as supplied, and an Activity-only dispatch shadow missed inherited Context dispatch. The final tests target ContextImpl dispatch correctly and use explicitly named **test-classpath-only Samsung API/data substitutes** and synthetic Bluetooth. They execute the actual production Main/GATT/Health methods, callback-to-canonical conversion, append, controlled timer/finalization and receipt code. They do **not** validate the real Samsung SDK, physical sensors, radio, foreground policy, power management or display.

The stop timer is armed before Samsung constructor/connect work, but a Handler is **not hard preemption** of a blocked Android main thread. Hardware responsiveness/service stability remains a physical acceptance requirement, not a test-earned claim.

## Final validation

All results were read from completed logs/XML; no APK assembly task was invoked.

| Validation | Result |
|---|---|
| Full Watch unit/Android-facing suite | **230 total: 226 pass, 4 fixture-dependent skips, 0 failures/errors** |
| Actual Main/GATT/Health service-flow cases | **7/7 pass** |
| Real Activity refresh/foreground-return cases | **3/3 pass** |
| Joined Watch writer → Phone wire/exact ACK → Watch acceptance | **PASS** |
| Production native Phone custody/report/restart/offline-reader suite | **94/94 pass** |
| Complete Phone deterministic suite in joined fixture context | **142 total: 140 pass, 2 fixture-dependent skips, 0 failures** |
| Six-phase joined acceptance runner | **6/6 phases PASS — synthetic only** |

The joined runner exercises the Watch-generated canonical fixture, Phone ordinary wire ingestion and exact ACK bytes, Watch acceptance of those Phone-generated ACKs, extraction of actual native Phone storage bodies, native writer → verifier → durable report → restart → offline custody recheck, and the complete Phone deterministic suite. It does not stand in for private-device custody or actual sensor/radio evidence.

Additional actual service tests cover rejected GATT promotion before Health dispatch, original Main service-request rejection capture, absent Health entry/15-second stop, Health capability rejection, SDK failure callback capture, unsupported physiological scope that cannot qualify, supported synthetic callback records and automatic five-minute finalization, and late callback non-mutation. The separate session handoff tests establish that timer posting alone changes neither receipt bytes nor sample/ACK qualification.

One full-suite source contract initially failed because it expected the old inline foreground cleanup. Its correction now checks the shared helper's **capture → quiesce → failed receipt → timer cancellation → stop** ordering, rather than weakening the failure condition. Before-correction failures and final passing XML remain in the validation workspace.

Validation workspace: `/home/ubuntu/HUGR_WATCH79_FLOW_CORRECTION_2026_10_12/validation/`; completed joined summary: `../joined-final/summary.json`.

## Scope and unchanged identities

- Production edits are limited to Main receipt presentation/action readiness, fresh GATT/Health startup failure containment and handoff, process-local handoff fields in FreshRunSession, and diagnostic identifier binding.
- Protected `SourceJournal.kt`, FreshRunRuntime admission/root selection, FreshRunReceipt wire/store format, BoundedOrdinaryRunGate, Android manifest, Gradle package/version declarations, canonical delivery/ACK matching and completion inventory remain unchanged.
- Phone61 runtime, native code, UI, wire, tests and admitted artifact are unchanged. Phone repository successor changes are documentation/control copies only.
- Existing APK identities are unchanged: Watch79 SHA-256 `91d3337b963c7fb31c510ba58cab8f59cd4e9ee62899b2f4762bedc5383ce5fb`; Phone61 SHA-256 `61b9a674fea6b705563fcf80157390ae8d6b7fac36f696e8ad041c960cbd2c5c`.
- Test-only vendor substitutes remain under `src/test`; no production vendor replacement, new signer, Phone build, new telemetry platform or historical reconstruction was introduced.
- No physical legacy/frozen/source root was accessed, reset, migrated, re-encoded, deleted, acknowledged or transferred.

## Remaining complete-workflow dependency and one next action

**The final state of the already-admitted physical Watch79 run is unknown.** The unchanged admission policy rejects another fresh Start if its current receipt is STARTED or FINALIZED/unacknowledged. Passing these source tests does not clear that run or authorize a retry. Installing a future corrected candidate would not itself resolve this state, so do not offer another fresh Start as if it were guaranteed to be admissible.

The smallest proposed next action is **one passive Main HUGR receipt-state check on installed Watch79**, separately authorized: open Main once, press no controls, read the current fresh heading and, if readily visible, manifest/accepted-ACK counts; stop on the first receipt result/failure or 60 seconds. Keep Phone61 unopened; no Start, Serve, retained delivery, Connect, sensing, retry, pairing/reset, Share/export or upload. Main is passive on this installed fresh UI route; this check is not a recording retry. No routine video is needed.

That result chooses the gate without deleting evidence: FAILED/COMPLETED may permit later fresh admission; STARTED/FINALIZED requires a specific preservation-respecting current-session decision before a fresh run. Do not conflate this with recovery of historical Watch69. No such device action or new build is authorized by this source/test closure.

Future acceptance remains one fresh five-minute run → controlled finalization → full canonical custody → attributable Watch-origin exact ACK/completion receipt → one durable Phone result → offline recheck after app restart. Once physically accepted, advance to the duration ladder and the existing live-observation/Watchtower bridge; neither full observer infrastructure nor historical proof reconstruction is a prerequisite for this short-run acceptance.

## Preservation and sovereign statuses

Apply source-owned receipt → Master → affected authorized private proposal only if needed/available → Alignment last → bounded coherent commits → controlled non-force GitHub mirror and independent exact-tree readback. No accepted S4 or private Atlas mutation is required by this localized source correction; no denied access retry is made. Private media and physiological payloads are excluded from Git. This file records validation, not a remote-preservation claim until independent readback succeeds; the separate final preservation receipt supplies the exact heads/trees.

- Watch69 **NOT QUALIFIED**.
- Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## Subsequent screen-identity observation — 2026-10-12, 23:03 CEST

Neil reports that opening what he calls Main HUGR now looks like the information app and supplies `VID_20261012_220056.mp4` (34.600479 seconds). Reviewed representative frames visibly show **HUGR Saved Diagnostics 1/4**, **NEXT SAVED REPORT**, **SHARE THIS DIAGNOSTIC**, and the historical report: code 78 / `0.78.0-compatible-fresh-run-receipt-candidate`, wallMs `1791706251463`, `UNCAUGHT_EXCEPTION`, stack `BoundedNormalStartupGate.deferFreshScope:25`, with report end visible. This identifies the displayed viewer as Saved Diagnostics, not the fresh Main receipt screen. The stored creation version 78 does not contradict the confirmed installed Watch79 version. No newer report, sensor/stop/finalization result or current receipt state is established. No Main-to-viewer transition or app-drawer launcher selection is shown in the reviewed frames; do not infer that Neil tapped the wrong icon or that Main redirected automatically.

Read-only installed build-source comparison confirms Main's separate controls `Start fresh 5 minutes`, `Serve current fresh receipt only`, and `Explicit retained v2 delivery`; its source has no explicit SavedDiagnostic/startActivity route. Manifest still declares separate Main and SavedDiagnostic launcher activities. This establishes software identity, not the actual device route. Clarification needed from recollection only: was this reached by tapping the blue/cyan Main icon in the app grid, or by returning with the physical side button/Recent Apps? No repeat tap, icon exploration, control press, Next/Share, Phone action, retry, correction or build is directed. The proposed current fresh receipt read has **not produced a receipt result**. Prior source/test closure and all status literals remain unchanged. Original video and frames stay private outside GitHub; only this sanitized observation is preserved.

## Blue Main icon selection corroborated — 2026-10-12, subsequent 22:46 message

Neil explicitly confirms the **blue icon**, not only the physical side button, and says the same information-like surface appeared when he began the Watch79 test. The new `VID_20261012_224240.mp4` is 45.308944 seconds. The reviewed early transition shows the app grid, scrolling past a distinct information `i` icon, a tap on the blue/white-striped icon matching Main's `ic_startup_diagnostic_main`, then **HUGR Saved Diagnostics / NEXT SAVED REPORT / SHARE THIS DIAGNOSTIC**. This supersedes the earlier *unobserved launcher selection* uncertainty for this new clip. It is not appropriate to attribute this to Neil selecting the information icon. His recollection about the initial test is preserved as a report, not retrospective video proof. This does not erase the separate earlier visible `Fresh STARTED · NONE` frame or establish its current receipt state.

Read-only examination of the exact admitted Watch79 APK (SHA-256 `91d3337b963c7fb31c510ba58cab8f59cd4e9ee62899b2f4762bedc5383ce5fb`) confirms Main and SavedDiagnostic remain separate MAIN/LAUNCHER activities but **neither declares taskAffinity or launchMode isolation**; the application also supplies no distinct task affinities. Main has no explicit SavedDiagnostic navigation. The source/icon and signed manifest agree. Android documents that app activities share affinity by default, an existing task may be brought forward with its top activity restored when its launcher icon is selected, and Home/root-Back need not finish the task. Thus **shared-task restoration of an already-open diagnostic viewer is a concrete, source-supported explanation consistent with the recorded transition**. Actual device task IDs, stack, launcher flags and Main lifecycle entry are not captured, so this is not a conclusively measured device root cause, nor a new crash diagnosis. It does not diagnose the original receipt-unavailable error or the admitted recording's finalization/sensor outcome.

The narrow proposed engineering step is launcher/task-isolation source/tests: reproduce Main → passive Diagnostics → Home → blue Main launcher, plus the reverse information-launcher route; verify each brings forward its own intended screen, never sensing/BLE on a passive route, and does not delete reports, alter journals/current receipt or clear the admitted run. Evaluate the smallest manifest/task correction together with already-tested flow changes, without broad task clearing or touching legacy/frozen launchers. **New explicit authority is required before implementing this launcher correction.** No production/test source, build, installation, device invocation or reset is performed here. No further icon exploration, report upload or reconnect is requested. Phone61 remains unchanged; current physical receipt state remains unknown and acceptance incomplete. The future complete short-run/offline acceptance, duration ladder and existing live-observation milestone remain controlling.

Sources read for this bounded mechanism:
- [Android tasks and back stack](https://developer.android.com/guide/components/activities/tasks-and-back-stack): launcher task restoration, default affinity and Android 12+ root Back behavior.
- [Android activity manifest reference](https://developer.android.com/guide/topics/manifest/activity-element): default taskAffinity/launchMode and isolation attributes.

Private original/derived frames remain outside GitHub; only sanitized observation and source-supported interpretation are preserved. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## Authorized launcher/task-isolation source/test closure — 2026-10-12

Neil subsequently authorized the focused launcher/task-isolation source/tests and demonstrated correction only. See `HUGR_NSTAIR_WATCH79_LAUNCHER_TASK_ISOLATION_SOURCE_TEST_2026_10_12.md`: exactly two explicit Main/Diagnostics task affinities; before suite three failures, corrected three passes; full Watch **233 total / 229 pass / 4 fixture-dependent skips / zero failures/errors**. Actual Activity lifecycle/receipt/journal/report preservation checks and an explicitly synthetic manifest-driven NEW_TASK restoration model cover both launcher directions without passive services/permissions/sharing. All other manifest semantics, production Kotlin/resources/version and Phone61 remain unchanged. Completed six-phase joined/native94 validation reused by unchanged-code check, not rerun as new hardware evidence.

This is not an installed repair or a measured Wear launcher/task-stack result; existing pre-update task migration is unproved. No broad task clearing, source/session deletion, build or physical action was performed. Current Watch79 receipt remains unknown; no new Start/retry is authorized. Proposed next decision is one greater-version Watch-only build/admission integrating the already-tested flow and task changes, followed only under separate device authority by a passive intended-Main/current-receipt check before selecting any recording action. Historical first receipt-unavailable remains physically undiagnosed; captured second-Start exception remains historical/source-matched. Phone61 unchanged, full short-run/offline then duration/liveWatchtower objective preserved, sovereign literals unchanged.
