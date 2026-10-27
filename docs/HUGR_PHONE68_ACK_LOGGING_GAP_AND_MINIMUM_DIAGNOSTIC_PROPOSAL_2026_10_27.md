# Phone68 ACK logging gap — completed read-only decision

**27 October 2026.** Authorized combined preparation: exact logging inspection read-only, plus existing Watch85 Saved Diagnostics preservation followed by one bounded passive current-receipt observation. **Logging inspection complete; physical Watch evidence/readback remains pending. No correction, build or new run performed.** Neil directly confirmed no HUGR app/control use since the overnight marker-file check.

## Decision

**The Fold→Honor capture route works for the observed synthetic marker, but Phone68 does not currently emit or save the underlying source-ACK rejection fields. Another unchanged run would not reliably resolve the named ACK uncertainty.** A computer instead of the Fold would not repair this instrumentation gap.

This is a demonstrated source-level visibility gap, not a demonstrated cause or cure for the physical ACK failure. Preserve the stopped physical attempts and all earned five-minute and synthetic capacity evidence.

| Boundary | Evidence-supported result | Not established |
|---|---|---|
| Source ACK write | Actual app calls BLE-PLX write-with-response and distinguishes its local 10-second deadline from other rejections. | ATT/radio dispatch or Watch acceptance. |
| Original BLE error | Pinned BLE-PLX 3.5.1 exposes numeric error category and nullable ATT/Android status. App keeps original rejection only in an in-memory `cause`, then classifies the fixed `SOURCE_ACK_WRITE_REJECTED` wrapper. | Which error fields were populated in the failed Honor run. |
| Phone durable diagnostics | Corrected Phone68 saver retains bounded local operation chronology; ACK evidence records fixed `PHONE_ACK_WRITE_FAILED`. Incident deliberately says `nativeAttDispatch=UNKNOWN`. | Underlying BLE error category/status; native write callback; exact cross-device match. |
| Native library logging | Retained BLE-PLX adapter defaults to `RxBleLog.NONE`; inspected app contains no `setLogLevel` override. Verbose characteristic logs include full hex values and identifiers. | A safe, sufficiently informative existing source-ACK Logcat tag on Honor. |
| Disconnect | Current-lineage disconnect callback is observed only while lineage remains current. Source-failure cleanup clears active lineage before requesting `cancelConnection()`. | Cleanup request equals an observed link disconnect or explains its initiator; later ignored callbacks cannot be reconstructed. |
| Fold capture | Connected synthetic marker saved/read; Neil confirmed offline file contents after USB disconnect. | Continuous native capture, runtime log emission, independently transferred/checksummed marker bytes or prolonged stability. |

Do not use broad `ReactNativeJS`, generic Bluetooth capture or global BLE verbose logging as a substitute. Such output can include identifiers/payloads and lacks a supported exact ACK association. This review did not establish a compiled/runtime RxAndroidBle tag; do not guess one.

## Smallest useful correction proposal — not implemented

**First retain the structured error already supplied by the current library, rather than immediately modifying the BLE dependency or requiring comprehensive native correlation.** At the exact source-ACK write rejection boundary, copy only a strict allowlist:

- fixed library-origin/category enum and validated numeric `errorCode`;
- validated nullable `attErrorCode` and `androidErrorCode`, preserving absent/invalid/unknown as explicit states rather than zero;
- local attempt/operation pseudonym and monotonic local ordering, labelled Phone-local only;
- existing promise submission/result/deadline/late/cancel observations, plus distinct cleanup-cancel requested/result facts and actual disconnect callback observation only when it occurs.

Never serialize raw error object, message, `reason`, `internalMessage`, stack, payload/base64/hex, manifest hash, source/run/device identifiers, address or UUID. Numeric error fields are a library-reported classification, **not direct `onCharacteristicWrite` or ATT/radio evidence**. Preserve generic transport error classification and exact custody/ACK rules unchanged.

Carry this metadata through the existing bounded incident store and isolated complete reader. If needed for the proven Fold capture route, mirror the same small allowlisted event set through an application-owned Android tag, with explicit caps and publication/loss status. Diagnostic publication must remain detached and must not await or delay the BLE operation, change retries/cancellation, extend deadlines or alter ACK acceptance. Ensure successful, rejected, deadline and late outcomes remain distinguishable and retrievable after disconnect; no report absence is proof of non-arrival.

**Targeted checks, only if implementation is later authorized:** exact error-field normalization; nullable/invalid/unrecognized and hostile free-text fields; pre-library guard rejection versus library rejection; library start/disconnect/write/callback-timeout categories; success/deadline/late settlement; stale-lineage cleanup versus diagnostic-only observation; producer→strict Kotlin publication→atomic acknowledgement→isolated reopen/export; field and byte caps; payload/identifier exclusion; unchanged rejection/deadline/ACK/custody behavior. Reuse all unaffected completed tests; no capacity-ladder rerun.

### Native escalation is conditional, not a blanket prerequisite

The precise residual question is **whether a library rejection arose before native write submission or from an Android GATT/link result after submission**. Allowlisted library error codes may resolve a particular local-start/disconnect/callback/write category; they cannot guarantee that every failure is distinguishable. If these fields remain missing/ambiguous, identify one supported hook in the pinned native path to record actual write invocation/return, genuine callback status and disconnect order. Do not invent that hook, modify a dependency, call a JS log “native dispatch,” or assume emitted status establishes Watch acceptance. Propose a narrow native hook or private HCI trace only for that remaining named uncertainty and obtain separate authority. No HCI capture is authorized now.

## Current Watch85 run — pending authorized observation

First observe only the isolated Watch Saved Diagnostics counter/header. Reuse the complete privately preserved 26 October newest incident if it is the same; counter alone cannot prove report identity. Preserve any genuinely new report before Main, labelled visual-only if raw sharing is unavailable. Do not routinely refilm the historical inventory.

Only after saved-evidence status is established, guide one Main opening and scrolling-only observation: stop first clear result/error or 60 seconds, no buttons or relaunch if unresponsive. Record actual source/run, revision, phase, duration, manifest/exact ACK counts and pending/error fields privately. Main writes startup metadata and a failed receipt read may quiesce the in-memory session; saved-run rewrite or interruption closeout is not authorized. Unresolved STARTED/FINALIZED is not permission for another Start.

No USB reconnect/capture, Phone HUGR launch, Bluetooth/pairing change, Connect/retry, recording, update, reset, private app-root access or cardiac-drain implementation. After readback report state and next decision before any new physical attempt.

## Source binding and evidence limits

Phone68 APK: `health.hugr.app` / `2.0.68-receipt-diagnostic-saver-candidate` / vCode68; 152128886 bytes; SHA-256 `69e12d47e755234e7f1f74215b17a9dc842895533b2272ffa4a29be550dd80c1`. Unchanged app `lib/bluetooth.ts` SHA-256 `f8a3fc5ede17ebf131f0e9bbedb73587790a4b204c9031539744266f0096458b`. Current documentation-only authority successors leave runtime/source/config and APK bytes unchanged.

Exact source locators: `lib/bluetooth.ts:1151–1185` ACK guards/write/wrapper; `:941–1022` local operation ledger; `:897–918` disconnect; `:1087–1115` cleanup; `lib/phoneRunAckEvidence.ts:17–42`; `lib/receiptIncidentDiagnosticModel.ts`; native `ReceiptIncidentDiagnosticStore.kt`; retained BLE-PLX `android/src/main/java/com/bleplx/adapter/BleModule.java` default level and write/error conversion; `errors/ErrorConverter.java`; `converter/BleErrorToJsObjectConverter.java`. Retained signed source-journal owner DEX verifies publication path, not native BLE callback behavior. Retained dependency/source binding and staged JS custody are not physical runtime/log-emission proof.

Detailed task-local reviews: `native-logging-review.md` and `phone-app-logging-review.md`. No live logs, new tests, app-source edits, builds or device/private-record inspection performed.

## Preservation

Source-owner receipt → Master → Alignment last → bounded documentation-only commits → controlled both-repository write preflight/non-force mirrors → independent exact head/tree/changed-byte readback. Private diagnostics/history/media remain off GitHub. Keep T2.2, adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, endpoint/drain, learning, Loop A, IDC and N1 separate. Watch69 `NOT QUALIFIED`; Watch68 v1 `UNCLASSIFIED / NOT VERIFIED`; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
