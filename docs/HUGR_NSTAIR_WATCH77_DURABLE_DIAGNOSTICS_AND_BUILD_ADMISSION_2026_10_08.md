# Watch77 — durable diagnostics and historical-resume repair: source/test/build admission

**Date:** 2026-10-08. **Scope:** implementation, tests, signer-continuous greater-version candidate build and admission. No installation, app launch, retry, transfer, ACK or device action performed by this task. Installed pair remains operator-reported Watch76–Phone59; this receipt does not change its physical status.

## 1. Controlling request and objective

User: “ok lets build the APK witht this durable capture do research if nesecary”. This adds minimal app-owned durable failure capture to the already-tested captured high-water correction. The target remains delivery of the existing finalized fresh-v2 recording, not a new recording or diagnostic platform. Email/SMS transmission is **not automatic** and has not been implemented. No recipient or external service is configured.

Preceding source receipts:
- `HUGR_NSTAIR_WATCH76_CAPTURED_RESUME_HIGH_WATER_EXCEPTION_2026_10_08.md` — actual captured exception, rather than another hypothesis or radio diagnosis.
- `HUGR_NSTAIR_WATCH76_HISTORICAL_RESUME_BOUND_CORRECTION_SOURCE_TEST_2026_10_08.md` — tested cursor/bound correction; this candidate incorporates it.

## 2. What is now saved without live ADB

New `SavedDiagnosticReport.kt`, `WatchDiagnosticRuntime.kt` and isolated `SavedDiagnosticActivity.kt` provide app-owned operational reports in `hugr_operational_diagnostics_v1`, separate from source/legacy/frozen journals.

The report includes package/version, process/attempt/source-session identifiers, wall and monotonic timestamps, failed stage/outcome, BLE lineage/MTU, available Phone cursor C, retained endpoint R, historical issuance bound H, queued endpoint, a cached 16-event coded tail, exception classes and bounded code-location stack frames/cause chain. Unknown values remain explicitly unknown. It does not copy exception messages, sensor/source payloads, generic Logcat, file paths, Bluetooth addresses, contact details or credentials. C/R/H fields are operational assertions, not proof of deleted bytes or Phone custody.

Capture points cover startup selection/service start, foreground promotion, service initialization, resume preparation/historical-bound validation/application, source-control rejection, replay reads, recovery stops and service destruction. Best-effort uncaught Java/Kotlin exception capture delegates to Android's previous handler; it does not suppress process termination. Caught failure capture runs before abort/shutdown. The original exception remains the primary report while terminal outcomes are annotated into the **same attempt report**, rather than displacing it with generic stop reports.

Persistence uses a temporary file, file-descriptor sync and rename, with a SHA-256 trailer verified before display/share. Up to eight published reports, each at most 24 KiB of report body plus the checksum trailer. Oversized traces are explicitly truncated. Only this new diagnostic root is rotated and excluded from Android cloud backup/device transfer; the existing source backup policy is not rewritten. Persistence is best effort: storage failure is logged and does not replace the original failure. This is not a guarantee against sudden power loss, native fatal crashes, process kill or ANR before a hook can run.

**HUGR Saved Diagnostics** is a distinct launcher. It reads only saved reports, with stable manual scrolling (no forced-scroll-to-tail loop), and never opens source journals or starts sensors/GATT/recovery. “Share this diagnostic” offers an explicit Android `text/plain` FileProvider share for the selected, integrity-checked report. The provider grants only that diagnostic URI and exposes only the diagnostic directory. Whether a compatible email/share app exists on this particular Watch is **not hardware-verified**. If none exists, the report remains available in the viewer and can be recorded legibly without keeping wireless debugging alive. There is no claimed automatic Watch→Phone report transport, email delivery or SMS delivery.

## 3. Retained recording and protocol invariants

The protected `SourceJournal.kt` remains byte-identical to the source admitted before this durable-capture change. `SourceReplayWindow.kt`, `RetainedFinalizedDeliveryRecovery.kt`, `HistoricalSourceResumeBounds.kt`, `SourceMtuReadinessGate.kt`, `FinalizedDeliveryLifetime.kt` and `FinalizedRecoveryReadiness.kt` are unchanged by the diagnostic delta. The GATT changes add failure capture/context only; exact ACK and replay semantics are not replaced.

Both replay entry points retain the earlier correction: Phone durable cursor C, currently retained replay endpoint R and validated historical issuance H are separate. C>R is admitted only through existing strict selected-session contiguous BUFFERED issuance evidence and exact canonical retained anchors. Unsupported/missing/corrupt evidence fails closed. Issuance does not invent historical ACK or deleted-byte provenance. The physical Watch's actual ledger remains uninspected.

Manifest replay stays ordered, including older manifests behind an already-durable Phone cursor. Phone durable append and full canonical range/count/byte/full-hash equality precede its actual 59-byte session/endpoint/hash ACK. Watch exact validation and per-ACK removal/advancement remain. Recovery is selected-session-only, deadline/disconnect bounded and no-new-sensing/no-new-source-append; the retained source identity is not replaced with a historical bounded-run UUID. Completion requires accepted exact ACK and no remaining selected-session finalized manifests.

Frozen/legacy operations and egress remain outside ordinary recovery. The unavailable Exact Range Readback launcher remains disabled. The compatibility DEX is reused unchanged and not newly runtime-qualified.

## 4. Validation and corrections before admission

Independent read-only review identified three concrete defects in the initial diagnostic draft: worst-case trace budget could prevent saving, generic stop/destroy reports could rotate away the actual exception, and reports could enter normal backup/transfer. All three were corrected before the admitted build, with regression coverage. The report viewer was also changed to a single outer scroll to avoid a zero-height text pane on the round display.

Final validation:
- Watch Gradle `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**. 191 tests total; **187 passed, 4 fixture-dependent skips, 0 failures/errors**.
- Joined Watch→Phone→Watch regression: all five phases **PASS**, exercising the four Watch fixture-dependent cases with supplied fixtures.
- Historical case: **C=8184, R=8182**, three ordered manifest-only exact ACKs; restart after every accepted ACK; no new records; all selected synthetic manifests removed only after exact acceptance.
- Current-cursor case: already-stored Phone records; two manifest-only ACKs; final new data durably ingested; third full manifest equality and actual 59-byte Phone ACK accepted by Watch.
- Phone deterministic suite: 88 total; **86 passed, 2 fixture-dependent skips, 0 failures**. Those fixture-dependent cases are exercised in the joined regression.
- Diagnostic tests cover store recreation/readback, integrity rejection, isolated rotation, explicit truncation/limits, primary-exception preservation, privacy omissions, backup exclusions, viewer isolation and capture-before-abort.

These are JVM/Node/source contracts with a **simulated Phone durable-store model**. They do not prove Android native SQLite durability, physical BLE, service survival, on-device report persistence/view/share, actual historical ledger fitness or recording custody. No globally clean Phone TypeScript claim is made; Phone production code/version/APK is unchanged.

## 5. Sole admitted artifact

| Field | Verified value |
|---|---|
| File | `HUGR_Watch77w_0.77.0_durable-diagnostics-historical-resume-candidate.apk` |
| Package | `com.hugr.wearos` |
| Version code | `77` (greater than installed predecessor 76) |
| Version name | `0.77.0-durable-diagnostics-historical-resume-candidate` |
| Size | 12,844,805 bytes |
| APK SHA-256 | `ec074c30aab34540934458d3e7375f226fd60a643a735dc65f78041b782e5c90` |
| Signer SHA-256 | `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` — exact admitted Watch76 continuity |
| Unchanged compatibility DEX SHA-256 | `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784` |
| ZIP/alignment/signature | PASS; aapt2 package/version/manifest verified |
| DEX audit | 8,132 class descriptors, no duplicate classes; report and recovery classes present |

[Download the sole Watch77 APK](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/dogCtbPvJnQEunFP.apk). [SHA-256 sidecar](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/JoCvpiOCdQpQcTuq.sha256). Both uploaded files were downloaded and compared byte-identically against their local originals. The unaugmented Gradle base in build outputs is **not** an alternative admitted APK.

Local artifact: `/home/ubuntu/HUGR_WATCH77_DURABLE_DIAGNOSTICS_2026_10_08/artifacts/HUGR_Watch77w_0.77.0_durable-diagnostics-historical-resume-candidate.apk`.

## 6. Short next-device procedure — review only, not authority

### Gate A: separate installation-only authorization

1. Select only the sole Watch77 APK above with the existing Wear Installer 2 route.
2. Proceed only for an ordinary in-place HUGR update. Stop for uninstall, data-clear/reset, downgrade, different package/signer or any unexpected warning; no retry.
3. Stop at installer result; **do not Open**. Check Settings app-list version `0.77.0 ... durable-diagnostics-historical-resume-candidate` and report whether either Watch/Phone HUGR was opened. Phone59 needs no update.

### Gate B: separately authorized one-shot retained delivery

1. Open normal **Main HUGR** once on Watch77; no Watch controls/new recording. Start timing at Watch recovery service start (using launcher time as a conservative earlier limit if service time is unavailable).
2. Only when the current white heading says **CURRENT RECOVERY ADVERTISING READY**, promptly open normal **Phone59 HUGR** on Honor and press **Connect Galaxy Watch once**.
3. Stop at the first failure/terminal result or 15 minutes from Watch service start, whichever is first. No relaunch/reconnect/retry, sensing control, pairing/reset, egress or legacy/frozen action.
4. Capture Phone final delivery/equality status and complete source-session/range/count/bytes/full digest evidence where available, and Watch exact-accepted ACK progression plus matching `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` source-session/stage/time. An old ACK 8183–8184, partial digest, live values or Connected alone is insufficient.
5. If stopped, preserve the first visible status. Under a **separately explicit passive saved-report observation**, open **HUGR Saved Diagnostics**, not Main HUGR; record the latest matching attempt report from header through `END REPORT`, including any truncation label. Do not press Share unless that distinct external-sharing action is authorized. This passive viewer does not start another recovery. No live ADB/icon hunt is a prerequisite for retrieving app-caught errors now.

Success remains actual retained final canonical equality, attributable exact Watch ACK and completion for the same source session. Underlying Phone native evidence may still be required separately for independent custody verification; do not infer it from the UI alone.

## 7. Preservation and sovereign states

Source-owned receipt → identical Phone/canonical-control/controlled-repository copies → Master → private Atlas impact handling → Alignment last → consistency → bounded commits → controlled non-force GitHub mirror and exact remote tree readback. Accepted private VIS-COUNT S4 remains unchanged; known-denied Atlas access is not retried or repaired as a prerequisite. Locally recorded impact is the new diagnostic capability and candidate readiness, **not** a recording qualification.

Signing credentials, keys, private device media and physiological records are excluded from commits. The packaging recipe uses the existing signing route and unchanged compatibility payload. Remote publication results are recorded separately after commit to avoid a self-referential head claim.

- Watch69: **NOT QUALIFIED**.
- Watch68 v1: **UNCLASSIFIED / NOT VERIFIED**.
- Watch58: `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 8. Installation-only authority and prepared later recovery — 2026-10-08 17:55 CEST

Neil explicitly authorizes the sole admitted Watch77 ordinary in-place installation, preserving existing app/data, no uninstall/clear, Phone59 unchanged, Settings version confirmation and STOP without opening HUGR. This supersedes section 6 Gate A's pending authorization only. APK checksum was rechecked against its sidecar; all three worktrees were clean before this update. No physical installation result has yet been received; Watch76 remains the last observed installed identity. No further audit or build is initiated.

**Current steps:** select only section 5's APK in Wear Installer 2; accept only an ordinary HUGR update; stop at installer result without Open; check Settings app-list version `0.77.0-durable-diagnostics-historical-resume-candidate`. Stop without retry for uninstall/clear/reset/downgrade, different package/signature or any unexpected warning. Report installer result, visible version, whether Watch/Phone HUGR opened and any warning. Do not open Saved Diagnostics at this gate.

**Prepared later gate — NOT yet authorized:** one normal Main HUGR Watch77 launch without Watch controls. Only if the current white heading is `CURRENT RECOVERY ADVERTISING READY`, promptly open normal Phone59 on Honor and press Connect Galaxy Watch once. Observe to first terminal result/failure or 15 minutes from Watch recovery service start (launcher time is the conservative limit if unavailable). No relaunch/reconnect/retry/new recording/sensing/egress/pairing/reset/legacy/frozen action. Failed readiness, black screen/crash, service destruction/disconnect, transport/source failure, deadline or any unexpected prompt/state ends the attempt. Preserve the first visible status.

Success requires underlying Phone final canonical manifest equality with actual retained source-session/range/count/bytes/full SHA-256, attributable exact accepted ACK, and matching Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`. Connected/live values, old/partial ACK 8183–8184 or shortened digest do not qualify the recording.

The later explicit authorization may include one passive **HUGR Saved Diagnostics** opening after failure/STOP, not Main HUGR. Match the newest report's version/time/attempt; if needed, use only `Next saved report` within the eight retained reports to locate the matching attempt. Record legibly from header through `--- END REPORT ---`, including stage/outcome, exception/stack, context and truncation. If none matches, none exists or integrity/read fails, report that literal result; do not retry recovery. This proposed permission covers viewer navigation and legible screen capture only, not `Share this diagnostic` or external transmission; sharing requires distinct explicit authorization. Actual on-Watch capture/view remains untested.

Closeout records authorization/prepared procedure only, never installation or qualification before observation. Known-denied private Atlas access is not retried; accepted S4 unchanged. After operator confirmation, preserve the observed installation fact automatically: source receipt → copies → Master → affected authorized/accessible private proposal only → Alignment last → consistency/commits → non-force GitHub mirror/head/tree readback. All three sovereign state literals above remain unchanged.

## 9. Reported installation and already-running observation authority — 2026-10-08 18:18 CEST

At 18:16:59 Neil reports Watch77 installed/updated by the usual route without anything strange, confirmed under Settings app-list. This is operator-reported installation/version confirmation, not independent package readback or recording qualification. Phone59 remains the established unchanged delivery candidate; a direct confirmation of Honor target and exactly one Connect press was requested and remains unanswered here.

Neil reports opening Main HUGR at 18:09, started/advertising ready at 18:11 and opening the Phone app. At approximately 18:14, he reports Watch advertising ready/GATT connected/MTU changed/source enabled/resume received/source trigger result (last text incomplete), Phone final delivery not yet determined, and a manifest ACK typed as `8181-81812 n2 bytes 212 sha ad86....`. The range is ambiguous and is preserved literally, not silently corrected. No exact source session, complete digest, Watch ACK acceptance, subsequent-manifest advancement, final equality or completion has been observed. Watch wearing was initially forgotten; this retained-delivery attempt must not collect new data and is not restarted for that reason. These are preliminary operator observations, not instrument readback or a causal verdict.

Initial launch/Phone actions preceded the separate recovery authorization; they are recorded as occurring outside the installation-only ceiling, without retroactive authority or an instruction to undo/restart them. At 18:18:45 Neil explicitly selects **Authorize observation of this existing attempt and passive Saved Diagnostics after STOP, exactly as above**. This authorizes only remaining passive observation to first terminal result/failure or **18:24 CEST**, the conservative 15-minute limit from reported 18:09 launch. No new Connect/relaunch/reconnect/retry, Watch controls, recording/sensing, egress, pairing/reset or legacy/frozen action. Stop earlier on service destruction/disconnect, transport/source failure, black screen/crash or unexpected prompt/state. A segment ACK alone is intermediate, not the terminal result; even a Phone final-delivery UI label is insufficient for qualification without exact underlying equality/accepted-ACK/matching-Watch completion evidence.

After STOP without completion, authority includes one passive **HUGR Saved Diagnostics** opening and report navigation/capture of the matching attempt from header through `--- END REPORT ---`, including stage/outcome, exception/stack/context and truncation. No Share/external transmission. Do not reopen Main HUGR if it crashes or goes dark. No terminal outcome or actual report persistence/view has yet been supplied; no source correction, retesting or new build is authorized by this observation.

Source receipt/copies → Master → affected authorized/accessible private proposal only (known-denied Atlas not retried; S4 unchanged) → Alignment last → consistency/commits → non-force GitHub head/tree readback. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 10. Reported end — evidence requested, no terminal classification — 2026-10-08 18:34 CEST

Neil reports: “ok ended at about 20 mins...long video but maybe screen shot of end result is good enough?...would you like the saved diagnostics from the watch?” No image, video, report text, exact terminal label/time or stop mechanism has yet been supplied. The approximate twenty minutes may describe video/observation duration or runtime; clarify whether HUGR ended automatically or Neil stopped filming/watching. Do not infer either. Authorized observation cap remains 18:24; this report does not extend it or establish a compliant in-window terminal result. Any eventual result after that cap must be identified separately, not substituted for bounded acceptance.

Cheapest next evidence: readable end-status screenshot(s) from the existing video showing Phone final-delivery/manifest lines and Watch end status/time, plus the already-authorized passive matching Saved Diagnostics capture if the attempt stopped without established completion. An end screenshot can establish displayed status, not independent canonical equality/ACK/Watch completion or event ordering. Keep the original long video; only inspect its relevant interval if timestamps/order become decision-bearing. Saved report capture from header through `--- END REPORT ---` (and visible integrity/truncation information) is requested without Share/external transmission or Main/Phone relaunch. If no matching report exists or integrity/read fails, preserve that literal result. No additional recovery, diagnostic run, source correction, test or build is initiated.

NEXT: clarify what ended and clock time; obtain end statuses and matching report. Earlier Honor target/exactly-one Connect confirmation remains unanswered. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 11. Supplied physical report materially changes the interpretation — 2026-10-08 18:45–18:48 CEST

Authoritative physical evidence receipt: `HUGR_NSTAIR_WATCH77_SAVED_EXACT_ACK_COMPLETION_PHONE_EVIDENCE_PENDING_2026_10_08.md`. Neil clarified that he stopped **filming** at 18:33, not that HUGR stopped then, and reports the Phone appeared unchanged throughout. Supplied 18:34 Phone screenshot directly resolves ACK as **8181–8182, n2, bytes212, sha prefix ad86b8616fb2**, while Final delivery remains not yet determined and source integrity UNPROVEN.

Supplied readable Saved Diagnostics video shows report 1/1, version77, actual source session `3c9e99e9-9430-480e-9b5a-ae00990b6b22`, **SERVICE_DESTROY / EXACT_ACK_COMPLETED**, C8184/R8182/H8184, report Watch wall time **18:11:46.902**, about 86.871 monotonic seconds after BLE_SERVICE_CREATED. This is physical persistence/view usability evidence and an app-recorded exact-completion outcome, not a deadline/crash report or independently verified recording. Reviewed code reaches that outcome after selected-session exact-ACK completion, not cursor equality. Cached tail does not enumerate ACK hashes/advancement and does not replace native Phone custody/full manifests or direct durable completion readback. No correction/test/build/retry is performed. Next proposed decision: focused source-only route to inspect existing Phone canonical evidence without another BLE attempt; implementation/device/private transfer require separate authority. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
