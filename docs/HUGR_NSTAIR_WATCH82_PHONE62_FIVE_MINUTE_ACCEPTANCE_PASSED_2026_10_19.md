# Watch82 / Phone62 — five-minute end-to-end acceptance passed

## Disposition

**PASS for the authorized fresh five-minute canonical recording/delivery/durable-report/restart/offline-recheck gate**, based on the supplied online video, the supplied native offline-reader report and Neil's direct force-stop confirmation. This is a bounded physical instrument milestone—not whole-substrate completion, eight-hour readiness, full raw physiology/PPG completeness or scientific/clinical validation. No further physical run is authorized by this result.

Authority: `docs/HUGR_NSTAIR_WATCH82_PHONE62_AUTHORIZED_FIVE_MINUTE_ACCEPTANCE_2026_10_19.md`, explicitly approved at13:51 Europe/Oslo. Installed Watch82 and Phone62 on Honor remain unchanged. No new source correction, test, build, installation or device command was performed during assessment.

## Exact new identity

- Source: `24ca3a75-62ab-4912-91a0-d50613b02928`.
- Watch run: `46628727-a977-4dd7-b4e6-19e196efe383`.
- Terminal Watch receipt: **COMPLETED · revision20**, persisted, integrity **VERIFIED**.
- Durable Phone report: revision22; **recording_acceptance=COMPLETE_VERIFIED**, **instrumentResult=COMPLETE_VERIFIED**, **failureReason=NONE**.

This source/run differs from the old interrupted receipt. No earlier run has been retrospectively qualified.

## Evidence chain

| Boundary | Observed evidence |
| --- | --- |
| Fresh recording identity | New source shown in online video and offline report; full new run ID is supplied by the attached Watch receipt in the offline report. |
| Five-minute boundary | Requested300000ms; Watch monotonic start155280447 / finalized155580712, delta300265ms (**300.265 seconds**). Attached receipt labels WATCH_ORIGIN_CONTROLLED_FINALIZATION. |
| Canonical records and exact custody | Native offline reader reports **8349 indexed canonical records, range1–8349**, accepted selected bytes539746, six complete manifest ranges, no missing selected indices, source gaps NONE. Expected/current counts, bytes, hashes and stream-range descriptors agree for all six manifests in the supplied report. These are canonical-record rows, not counts of beats or raw samples. |
| Attributable Watch ACK and completion | Attached persisted Watch receipt reports **six exact accepted ACKs / six final manifests**, phase COMPLETED; this is separate from Phone ATT write-success facts. |
| Online terminal report | Video shows the matching source move from IN PROGRESS / STARTED to **COMPLETE_VERIFIED / COMPLETED revision20** around4:11 into the recording, Phone clock14:18, retained to its end. A private verification frame at270s confirms this terminal source/status/revision. |
| App restart | At14:21 Neil directly reports completion and that Phone HUGR was force-stopped as instructed. The video does not show the force-stop; keep operator confirmation distinct from visual evidence. |
| Read-only offline recheck | Supplied report reaches END REPORT with **report_integrity=VERIFIED**, **report_reopened_without_mutation=YES**, **current_canonical_custody_recheck=MATCH**, manifest tuple match YES, **watch_receipt_proof_current_recheck=MATCH**. Reader declares no network/BLE/sensor/share/export, no recovery/source write and no foreign sessions queried. |

### Timing clarification

Neil reported Start at14:12. Attached Watch wall-clock fields correspond to14:13:05.162–14:18:05.428 Europe/Oslo; their wall delta is300266ms. Retain both records as distinct clocks/observations; no synchronized-clock claim or operator-error inference. The Watch monotonic duration is the bounded-run timing evidence.

**Opening Saved Source Evidence later did not invalidate this result.** The approved60-second limit concerns obtaining the first report/error **after opening the reader**, not an expiry after recording completion. The source has no report-age/expiry requirement. No repeat read is needed to address the reported concern. Exact offline launch latency is not measured by the supplied video; the complete successful reader output is supplied.

### Apparent UNKNOWN fields are separate evidence-layer limitations

The lower snake-case `failure_reason=UNKNOWN_NOT_FINALIZED_OR_COMPLETION_ATTRIBUTED` and nested canonical `ack_history`/`watch_completion` UNKNOWN fields describe the Phone-only canonical snapshot, which does not independently store/prove Watch lifecycle or ACK acceptance. They do **not** override the report's separately attached, integrity-checked Watch receipt (**COMPLETED / failure NONE / six ACKs**) and its current proof recheck MATCH.

Read-only source check: `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/HUGRSavedSourceEvidenceActivity.kt:523–599` renders/rechecks attached proof separately; `PhoneCustodyRunReportStore.kt:120–146,588–635` retains Phone-only unknowns; `HUGRSavedSourceEvidenceActivity.kt:168–175` states canonical-snapshot limits; `WatchRunReceipt.kt:53–103,147–165` contains exact inventory/ACK/duration/completion qualification predicates. No implementation change is implied or performed.

The video includes transient **live** FLOWING/TRANSPORT_STALLED labels, but no observed terminal saved-run failure; do not invent a failure/reconnect or discard the exact completion/custody evidence. Live freshness/status presentation is not canonical custody or Watch ACK proof. No manual Connect/reconnect press is shown in the clip; the clip begins with the run in progress. Pre-Start preparation, Start press and exact tap counts are not visually audited by this clip.

## Provenance and privacy

Original text: `pasted_content_3.txt`, SHA256 `25e7154b74a9d4b67617ccd2ccd2eaef97f61c815f22439ac01c51bfe85c62a6`.

Original312.553333sec video: `SVID_20261019_141409_1.mp4`, SHA256 `a1e9d7f4f0483f643fce24ba46df20ca9eb4fb7f171eb3ccbafe6ccfce54037d`.

Report-declared body SHA256 `644467912c5c2a7799052389119921c8d7e4b5e9e2d89cb17876a296e3468515`; report-declared file SHA256 `ab4930fb58be2d00f9c715a52daaefc730db64302506e671786d116c82d3d353`. These are device-reader claims, **not** independently recomputed hashes of the original native report file in Manus. Exact hash of the supplied text projection is recorded separately above.

Only this sanitized outcome/provenance record is committed. Raw report/video/frames, physiological values, Bluetooth addresses, database hashes and private source payloads remain excluded from GitHub. No raw Watch/Phone canonical artifact was fetched or independently verified in Manus; qualification rests on the source-backed native checks and attributable Watch receipt exposed in the supplied evidence.

## Next boundary

**No further action now.** Preserve the installed pair and successful run. The next engineering objective is a separately bounded longer-duration rehearsal using this baseline, then the existing duration ladder/eight-hour goal and live Watchtower bridge. No demonstrated dependency currently requires another build merely to repeat this passed short gate. Longer recording, live observer operation, research integration and N1 need their own current authorization/evidence boundaries.

Standing source-owner → Master → affected authorized private proposal only (none here; accepted S4 unchanged) → Alignment last → bounded documentation commits → controlled both-repository write preflight/non-force mirror → independent remote head/tree/record-byte readback. No main merge or force push.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**. Old interrupted source remains FAILED/PROCESS_INTERRUPTED, not finalized or qualified by this fresh success.

## 14:32 operator observation — old Main screens dismissed before Start

Neil states this was the first time **all old Watch Main HUGR screens were closed before starting**. This is a useful procedural observation, not proof of superstition or operator fault.

Current `MainActivity.kt:56–84,141–154,246` defines Activity-local `freshRequested`, `freshReceiptOnlyRequested` and pending-admission flags, and rejects further actions when those locks or process-local runtime guards are set. A recreated Main Activity starts with new screen-local flags, then rereads actual persisted receipt/runtime eligibility. The Main manifest has a distinct main-task affinity and `documentLaunchMode="always"` (`AndroidManifest.xml:59–60`), so multiple old task cards are plausible. Returning to an old screen can retain its screen-local action lock; dismissing old Main cards and opening a fresh Main can avoid that stale screen. It does **not** bypass saved STARTED/FINALIZED guards or prove a sensor service was stopped, and is not app-data clearing.

Therefore fresh-screen preparation is a **source-supported practical precaution**, already part of the approved pre-Start card. We cannot identify dismissal of all cards as the sole cause of success: the integrated Watch/Phone source fixes and prior explicit interruption closeout also changed the conditions, and this run was not a controlled causal comparison. No extra run, old-screen experiment, fix or build is needed to establish the already-passed short acceptance. Retain bounded fresh-Main preparation before a future separately approved run; do not infer permission to dismiss screens or stop services during an active recording.
