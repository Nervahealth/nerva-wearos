# Existing diagnostics cannot reliably match the Phone timeout to a Watch response

**23 October 2026 — read-only proposal. No implementation, build or device action is authorized or performed.** The previous receipt/ACK source/test work remains completed within its stated coverage. It is not reopened here and is not a demonstrated cure for the physical timeout.

The existing diagnostics are **insufficient for an exact cross-device association**, especially when the Watch returned a timely successful platform response. The missing pieces are a retained timely-success observation, a proven native request mapping and an incident report that survives disconnect without silently losing the decisive context. I would not build the already-tested partial diagnostics alone and present that pair as ready for a conclusive physical attempt.

## What exists on each side

### Phone

The committed prospective Phone code records an **attempt-local operation ordinal**, logical kind, safe R1 receipt-page offset where known, and JavaScript submission/settlement/deadline/cancellation/late-settlement states. Each event has local wall time; the detail contains elapsed Phone-monotonic time from submission and explicitly says **`native_att_dispatch=UNKNOWN`**.

Kinds include `RECEIPT_R1_WRITE`, `RECEIPT_PAGE_READ`, `RECEIPT_C1_WRITE`, generic receipt write and `SOURCE_ACK_WRITE`. The wrapper's `hugr-receipt-<attempt>-<ordinal>` transaction ID associates a local cancellation with a BLE-PLX operation. It is not a wire field, is not currently retained in the diagnostic report, and is not a demonstrated Android ATT or Watch request identifier.

A page offset is not unique: R1:0 is repeated during polling. A subsequent Phone read inherits the last successfully submitted R1 offset. It is a receipt pagination byte offset, **not a canonical record index**. Ordinal equality, a matching offset, or similar displayed clock time does not establish a cross-device match.

The existing storage key is `@hugr_recovery_boundary_diagnostic_v1`. It retains **40 events**, each detail capped at **160 characters**. Writes are fire-and-forget AsyncStorage operations with swallowed errors; there is no acknowledged disconnect flush. Loading after process restart therefore retrieves only whichever suffix was actually persisted. This is best-effort persistence, not an immutable incident receipt.

The ordinary Phone connection UI shows only the **newest eight events**, while disconnected, and has no recovery-diagnostic copy/share route. The separate **Saved Source Evidence** information launcher reads a durable custody report, **not this AsyncStorage operation timeline**. An initial receipt timeout may have no newly selected run report; an older report must not be substituted for it.

Source locators: `phone/lib/bluetooth.ts:243–318, 913–999`; `phone/lib/recoveryDiagnostic.ts:13–70`; `phone/components/WatchConnect.tsx:305–310, 1327–1338`; the isolated native `HUGRSavedSourceEvidenceActivity.kt` and `PhoneCustodyRunReportStore.kt`.

### Watch

The committed prospective Watch ATT outcome report records coarse operation class (`RUN_RECEIPT` or `SOURCE_CONTROL`), read/write direction, R1 page offset for a page-request write, a **Watch-service-local callback ordinal**, local callback-entry/pre-response/response-return markers, and `TRUE`, `FALSE` or `NULL` platform outcome. The derived local delay is compared with ten seconds as a **reporting threshold**, not a new Watch deadline.

**Timely `TRUE` is not saved.** Reports are queued only for false, null or locally late responses. Receipt reads have no persisted page offset; receipt writes do not fully distinguish R1 from C1; source-control writes do not fully distinguish resume from ACK. The ATT report does not contain the native Android callback `requestId`, process/GATT lineage, ATT status, characteristic handle or a shared Phone operation key. Richer generic crash-context fields do not fill these omissions retrospectively.

There are at most **eight published reports** in `filesDir/hugr_operational_diagnostics_v1`. Each completed report has a SHA-256 trailer, file flush/fsync and rename. The viewer checks integrity. Each current exceptional ATT observation creates a separate report; later reports can rotate the original one away. Post-response handler scheduling leaves a loss window if the process dies or the worker does not execute. A valid saved file proves that file was published, not that every callback was captured.

The separate **HUGR Saved Diagnostics** launcher reads these existing files locally after disconnect. It starts no journal, BLE, sensing or recovery. Its Share control uses an explicit FileProvider intent; if no compatible app exists, it leaves the report saved and offers local viewing/recording. Sharing availability on the Watch is **not proven**. Backup/device-transfer rules exclude this diagnostic root, so migration/cloud backup is not a substitute for preserving it locally or explicitly saving it privately.

Source locators: `watch/BleGattService.kt:1149–1162, 1212–1240, 1289–1333, 1711–1761`; `SavedDiagnosticReport.kt:46–57, 117–174`; `WatchDiagnosticRuntime.kt:70–86`; `SavedDiagnosticActivity.kt:15–65`.

### Why a timely Watch response still cannot be inferred

A Watch `TRUE` means `BluetoothGattServer.sendResponse` returned true. It does not prove on-air transmission or Phone settlement. Even a Watch callback finishing in less than ten seconds may have arrived after a long Phone-side queue/native delay. The Phone's deadline starts at JS submission; the Watch interval starts at its own callback entry. **No synchronized clocks or conversion exist.**

Phone and Watch ordinals count different operations and have different reset boundaries. Neither is shared. Android server `requestId`, BLE-PLX transaction ID and an ATT trace occurrence must not be declared equivalent without a demonstrated mapping.

Absence of a Watch report means only **no retained matching observation was found**. Timely success is silent, and rotation, persistence failure or process interruption can remove other observations. It is never proof of request non-arrival.

The installed Watch84/Phone66 APKs do not contain the completed new source diagnostics. Updating to those source corrections alone would still leave the correlation gaps above.

## Smallest bounded addition I propose

The addition is **one incident-sized diagnostic window on each side**, not a thirty-minute event archive or another telemetry system. It requires separate source/test authorization; nothing below is implemented by this task.

1. **Record every relevant operation outcome, including timely Watch `TRUE`.** Use the same logical vocabulary on both sides: R1 write, receipt read, C1 write, source resume and source ACK. Keep only safe page offset, characteristic/handle metadata, native request identifier where actually available, request offset, ATT status and byte length—not payloads, receipt contents, addresses, source IDs, manifest hashes or sensor values.
2. **Retain the local epoch and complete per-operation lifecycle.** Give the diagnostic session a fresh non-device-derived pseudonym; preserve local process/connection epoch, local ordinal, entry/submission, pre-response, outcome, cancellation/disconnect and late settlement. On the Phone retain the local wrapper transaction ID as a **local** key, not a purported shared key. On the Watch retain Android requestId and characteristic instance/handle information as native-local metadata, without assuming they match a Phone ID.
3. **Use a fixed 64-operation window, with pending operations protected from eviction and explicit overflow/loss counts.** Freeze the first decisive failure and its preceding window; reserve bounded room for late settlement. Aggregate lifecycle fields into operation rows rather than spending the existing 40-event ring on every stage. Enforce the existing Watch 24-KiB report byte limit and eight-report bound. One diagnostic connection/incident is one file, not one file per successful poll. Do not let the current frozen incident silently rotate before retrieval. If capacity is insufficient, report `TRACE_WINDOW_OVERFLOW`; do not claim complete coverage.
4. **Keep diagnostics off the response critical path.** Callback entry is an in-memory observation. Original response/ACK work and timers retain their order. A dedicated bounded diagnostic worker checkpoints only after response, with acknowledged atomic publication and explicit commit/loss state. On Phone failure/disconnect, publish the frozen diagnostic independently of run identity, so an initial-R1 timeout has a report even before a run has been selected. This publication must not hold, retry or extend the BLE operation. A crash before checkpoint remains an explicit uncertainty; it cannot be labelled non-arrival.
5. **Retrieve the complete report offline.** Reuse the Watch Saved Diagnostics viewer. On Phone add a clearly labelled **Saved Receipt Diagnostics** choice in the isolated information-launcher surface, reading only the diagnostic file—not React/Main, BLE or canonical journal recovery. Provide an explicit private Save/Share route for the complete report, not just the UI's newest eight lines. Keep the original custody-reader function distinct.

This solves retention, outcome coverage and retrieval. **It does not, by itself, create a shared request key.** Equal counters or page offsets must still not be joined.

### Native trace needed for an exact association

To preserve the receipt/source wire contracts, my proposal uses a **bounded native ATT/HCI mapping**, not a new R1/C1 envelope, diagnostic characteristic, extra request, retry or parser relaxation.

The required chain is: Phone logical transaction → actual native queued/dispatched operation → the ATT request occurrence on its connection/bearer → corresponding Watch callback and response attempt → ATT response/error or link termination → Phone callback/deadline. Native metadata observations must be read-only. Where BLE-PLX owns the queue/callback, a narrowly reviewed observation hook must preserve the transaction-to-native mapping; a bridge-entry log alone is not dispatch proof.

The matching key must come from a **verified mapping within the same native connection/bearer**, using characteristic handle, ATT opcode/offset and ordered request/response occurrence. Android requestId is retained as a native-local discriminator. It is not assumed to be an on-wire transaction number. Multiple ATT bearers, overlapping requests, missing trace spans or an unverified callback mapping make the result **`CORRELATION_UNRESOLVED`**. We must prove the mapping rather than assume one outstanding request or equate the local counters.

Android documents Bluetooth HCI snoop capture and extraction from bug reports; full capture requires enabling HCI snoop and restarting Bluetooth. Android also documents on-device **Take bug report** and explicit sharing of the saved report. This is a viable proposed retrieval route that does not depend on a continuing Watch wireless-ADB connection.[1] [2]

**Concrete unverified prerequisite:** we have not shown that this Honor software build exports the needed full ATT span, or that the native transaction/callback mapping can be observed and recovered on the actual pair. The documented general Android route is not proof for these devices. A separately authorized setup/retrieval preflight is required; Bluetooth must not be restarted while the current run remains unassessed. If the needed span or mapping cannot be obtained, hold the physical attempt rather than ship a generic-timeout procedure. Do not replace this prerequisite with another screenshot or an inferred clock join.

Full OS HCI/bugreport material can contain unrelated app/system data, device identifiers and transmitted receipt/physiological bytes. It is **private, sensitive material**: enable it only for the bounded authorized window, save privately, derive only the needed ATT metadata, keep originals controlled, and never stage it to GitHub. Ordinary filtered always-on snoop is not promised to retain the needed packet mapping.[1]

## Assess and preserve the unresolved current Watch run first

The current run remains **UNKNOWN / physical thirty-minute gate NOT PASSED**. Elapsed wall time, Phone timeout, disconnected Bluetooth or an app update cannot establish finalization. No update, Bluetooth restart, receipt closeout or new recording is proposed as a way to discover its state.

Before any update or new recording, a **separately authorized bounded local assessment** would proceed in this order:

1. **Preserve existing diagnostic evidence first.** Open only Watch Saved Diagnostics once, enumerate its finite existing reports within five minutes, and privately retain the relevant original report where Share works. If Share is unavailable, a readable complete report recording is acceptable for the diagnostic observation; label it a visual derivative, not exact original bytes. Do not open Main, press recovery controls or change Bluetooth during this step. These old reports cannot supply the missing new correlation fields, but they may preserve existing exception/stop evidence.
2. **Then obtain one current-receipt observation on the old Watch84, before updating.** Existing Main is the available current-receipt surface, but it is **not a zero-mutation inspector**: opening writes startup diagnostic/normal-startup metadata, and a failed receipt read captures a diagnostic and sets the in-memory session's `accepting=false` through `quiesce()`. This read does not silently repair pending ACKs or itself rewrite the receipt, but its side effects must be included in the explicit authorization. Open once, no buttons, allow only the current receipt display/scroll, and stop at the first clear result/failure or 60 seconds. No relaunch on a blank/unresponsive screen.
3. **Preserve the actual identity and state privately.** Capture selected source/run, receipt revision, V1/V2 duration, phase/failure, supported/started masks, manifest/ACK counts and any pending/unreadable indication. Match the actual run to the earlier admitted-run observation; do not substitute the older five-minute run or a Phone report for Watch state. Keep actual identifiers and observations off GitHub. Sanitized controls can record only the state decision and private evidence locator.
4. **Apply the state gate before update or recording.** `STARTED`, unreadable/pending publication/ACK state or an unknown result remains unresolved: stop and preserve it. Any interruption closeout needs separate explicit authority and must preserve its reason and existing bytes. `FINALIZED` preserves the recording but does not prove delivery; it remains blocked for fresh Start. `COMPLETED` requires its own exact Watch and Phone custody/report evidence before qualification. A terminal `FAILED` remains failed, not repaired by elapsed time. **Do not press “Serve current fresh receipt only” to inspect:** it can rewrite STARTED or FINALIZED to FAILED/PROCESS_INTERRUPTED.

Main's receipt projection calls `FreshRunRuntime.current()` and does not invoke pending-ACK recovery. Store reads refuse interrupted selection/publication/ACK gates. Main has no automatic new recording from this read; service-start paths remain explicit controls. The assessment above is nevertheless a device action with disclosed side effects, **not authorized now**. If those Main side effects are unacceptable, the existing launchers cannot supply a strictly inert current-phase read; no private-storage workaround is proposed.

The accepted five-minute recording, current run's original bytes, saved receipts and all historical failures remain unchanged by this task. Ordinary data-preserving update is considered only after the observed state and evidence are preserved and installation is separately authorized. No new run can bypass a saved unresolved STARTED/FINALIZED receipt.

## One coordinated build/admission proposal

**Proposed candidates: Watch85 / Phone67, diagnostic-correlation candidate lane**, subject to confirming no later version reservation. These are proposed identities, not existing APKs or build authority. Use packages `com.hugr.wearos` and `health.hugr.app`, the existing signers and the controlled source branches. No SDK, acquisition, endpoint/drain, scientific selection or unrelated portal change.

The sequence is:

1. Authorize and implement only the bounded diagnostic window, offline retrieval and read-only native mapping observation above. Reuse the accepted receipt/ACK tests. Add checks **only for these additions**: timely Watch success capture; local/connection epoch separation; repeated R1 offsets not falsely joined; proven transaction/native/callback mapping or explicit unresolved status; overlap/cancellation/late response; fixed capacity/pending retention/first-failure freeze; overflow and crash-loss reporting; atomic publication and offline reopen; redaction; complete Save/Share; and no disk I/O ahead of ATT or mutation of receipt/source custody.
2. Qualify the capture/retrieval path under separate device/setup authority after the current-run assessment. At minimum show a privately retrievable native trace with the required connection/bearer span and a mapping mechanism that does not depend on equal local counters or synchronized clocks. If that prerequisite is not earned, the physical-use proposal remains **HELD**. There is no fallback “just try the same generic timeout again.”
3. Only after those software checks pass, separately authorize **one coordinated signer-continuous Watch85/Phone67 build/admission**. Use the existing cache/toolchain and normal pinned-source preflights. Verify package/version/signer/APK checksums, compiled presence and bounds of the diagnostic path, unchanged source/receipt/ACK verification, legacy readability and exact predecessor compatibility components. Preserve the admitted artifacts and controls through the standing non-force GitHub/exact-readback procedure. No automatic replacement build if either candidate stops.
4. Installation and the physical attempt remain separate. Updating never authorizes launch, recording, Connect or diagnostic sharing. The completed source/test evidence and the proposed correlation capability must not be described as a physical cure.

No second capacity programme, lower-rung repetition or deadline review is needed for this proposal. The ten-second operation timer, original finite run/grace horizon, 180-second custody budget, sampling cutoff, exact manifest/ACK rules and no-retry boundaries remain unchanged.

## Short capture plan for one informative attempt — held for authorization

**Before:** preserve/resolve the current Watch state; confirm the exact admitted candidate versions; qualify private trace retrieval and mapping. Only then enable the bounded native trace, with any required Bluetooth restart expressly authorized. Verify the saved diagnostic incident path is available without Main/Connect after disconnect.

**During:** perform one already-approved recording/delivery procedure, with one Connect and its original deadline/STOP rules. Trace the existing requests; add no probe command, retry, new poll or reconnect. Preserve first-timeout context on Phone and all relevant Watch response outcomes, including timely success. Stop at the first decisive failure or existing terminal/deadline boundary. Diagnostic success means an attributable trace, not recording qualification or a cured timeout.

**After:** do not reconnect to retrieve evidence. Use the isolated information/diagnostic viewers to privately save the complete frozen Phone and Watch reports. Take/save the on-device native bugreport promptly, before further Bluetooth activity can rotate the relevant span. Where Watch Share is unavailable, use a readable incident card recording and retain the complete file on Watch; do not claim full-byte retrieval. If the necessary exact matching fields/span cannot be recovered, mark the attempt's correlation unresolved—no non-arrival inference. Restore temporary logging settings only under the bounded capture authorization, without restarting HUGR.

**Match:** use the proven native connection/bearer/request mapping, not wall clocks or equal ordinals. Report separately: Phone submission/native dispatch/deadline; Watch callback/ATT status/platform outcome; observed response/error/disconnect in the trace; and Phone settlement. A Watch timely TRUE plus a mapped response before the Phone's own deadline can be evidence of response progression, while Promise settlement is assessed on the Phone. Any missing stage stays explicitly unknown.

## Preservation and limits

This is a read-only decision/proposal. It preserves the completed source/test result and introduces a **separate diagnostic correlation admission gate**, not a new substrate development track. No implementation, test rerun, APK build, device action, real-record access or private transfer occurred. The actual pre-update observation, native capture and proposed additions all require future explicit authority.

The source-owned proposal is preserved first, followed by Master and Alignment last, bounded documentation-only commits, both-repository write preflight, non-force mirror and exact remote readback. Actual run identifiers, device reports, HCI/physiological material, APKs and credentials remain excluded. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, learning and Loop A stay unchanged; Samsung native-sequence/final-batch and endpoint/drain remain separate. No IDC or N1 action.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

## References

[1]: https://source.android.com/docs/core/connect/bluetooth/verifying_debugging "Android Bluetooth verification and debugging, HCI snoop and bugreport extraction"
[2]: https://developer.android.com/studio/debug/bug-report "Android capture and retrieve a bug report"

END_OF_RECEIPT_CORRELATION_DECISION_AND_CANDIDATE_PROPOSAL
