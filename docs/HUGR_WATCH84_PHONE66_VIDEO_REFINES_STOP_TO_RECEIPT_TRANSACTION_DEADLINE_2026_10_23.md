# Watch84 / Phone66 — video refines STOP to receipt transaction deadline

**Dated evidence:** 23 October 2026. This is a sanitized engineering successor to `HUGR_WATCH84_PHONE66_30_MINUTE_GATE_PHONE_CONNECTION_STOP_2026_10_23.md`. The original reported STOP and prior authorities remain unchanged and retained. No implementation, build or physical action is authorized by the supplied video.

## Refined result

The supplied Phone screen recording begins with an established HUGR connection and selected-session receipt/source traffic already flowing. Multiple displayed segment-equality/Phone ACK-write events and receipt revision advances precede failure. These screen indicators are not independent accepted-byte custody, exact Watch ACK acceptance, whole-run completion or physiological validation.

The video shows the specific failure:

> Connection failed: RECEIPT_TRANSACTION_DEADLINE.
>
> Make sure the HUGR app is running on your Galaxy Watch Ultra.

Thus the first preserved broad operator report is refined: this is a **later receipt GATT-operation timeout during a previously connected session**, not merely failure to discover or initially connect to the Watch. The generic running-on-Watch hint does not diagnose absence of the Watch app.

Displayed Phone recovery-boundary diagnostics show GATT_CONNECT_REQUESTED at13:41:02 and GATT_CONNECTED at13:41:03. These are Phone app wall-time diagnostics, not independently verified physical tap time/count or cross-device clock conversion. The initial physical Connect action is not captured in the recording; do not claim its count from the recording. Direct private sampling places the first visible failure after clip235s and at/before236s, with Phone-clock13:45. No unsupported exact failure-timer origin is invented.

The subsequent system Bluetooth page says connected for calls. That does not establish HUGR's BLE GATT link, and its appearance after the error is not evidence that a system setting caused the earlier timeout. No setting change is inferred or operator behavior blamed.

## Source-matched failure boundary

Preserved Phone source, no changes:

| Locator | Established source fact | Limit |
|---|---|---|
| `lib/durationLadderContract.ts:21` | RECEIPT_GATT_OPERATION_DEADLINE_MS is10,000ms | Distinct from recording/grace horizon and native custody-verifier budget |
| `lib/bluetooth.ts:834–855` | Bounded GATT-operation race cancels transaction and rejects RECEIPT_TRANSACTION_DEADLINE; receipt write/read use helper | Visible error does not identify read vs write, page offset, OS/radio or queue cause |
| `lib/watchRunReceiptTransport.ts:157–220` | Immutable paged receipt acquisition, source/run/revision/digest and page bounds remain strict | Cannot diagnose why an individual hardware operation stopped resolving |
| `lib/bluetooth.ts:876–897` and receipt observer223–278 | Existing-connection observer reads/persists matching receipts and awaits guarded terminal custody | No full-run observation deadline or final custody verification failure is demonstrated by this error |
| `lib/bluetooth.ts:1441–1460` | Error catch records receipt deadline failure, cancels attempt/connection, clears lineage, emits generic connection-failed hint | Later disconnection can be timeout containment; do not infer it preceded/caused failure |

The shared operation helper also bounds source ACK writes, whose call site wraps failure as SOURCE_ACK_WRITE_REJECTED. Do not replace the missing exact transaction substep with a guessed read/write diagnosis. No deadline is extended and no acceptance proof is relaxed.

## Decision and next proposal

**Thirty-minute physical gate remains STOP / NOT PASSED.** No completed Watch proof, durable verified whole-run Phone result or success-only offline recheck has been earned. The earlier admitted artifacts and synthetic tests remain valid as their own evidence, not a substitute for physical qualification.

Keep further device controls stopped: no Connect retry, relaunch, Bluetooth repair, new Start, selector tap, manual stop, Force stop, Clear data/cache/storage, receipt closeout, export or diagnostics launcher. Leave any existing Watch recording alone for its automatic boundary, without claiming it remains active or has finalized.

Smallest next engineering proposal: obtain explicit authority for a bounded source/test investigation of RECEIPT_TRANSACTION_DEADLINE, including receipt read/write/page scheduling and source-transfer contention, retained diagnostics and truthful error wording. Reproduce a specific failure in the relevant joined transport tests before correcting a demonstrated defect. Keep10s per-operation,180s custody and closed duration/grace budgets unchanged during investigation; keep exact source/run/revision/hash/page/ACK/custody proofs and bounded memory. If evidence cannot identify the cause, report the exact remaining diagnostic gap and propose only the minimum passive existing-run read. No build, installation, reconnect, new recording or real-record export follows under this video review.

This proposal is **NOT YET AUTHORIZED**. No test or source mutation is performed by this read-only evidence review. It is not permission to restart archaeology or the wider substrate audit.

## Private custody and preservation

Original supplied video checksum, frames, exact real source/run identifiers, detailed displayed recording-derived fields and direct-frame analysis are retained privately in the task workspace and excluded from GitHub. The automated video analysis is supplementary; direct-frame observations govern when its clocks/identifier transcription disagree. No canonical records or private app storage are accessed. The original video remains unchanged.

Preserve only this sanitized source-owned engineering receipt, Master next, Alignment last, bounded documentation-only commits, controlled both-repository write preflight/non-force existing-branch mirror and independent exact remote head/tree/changed-byte readback. No private VIS-COUNT or portal changes. Actual recordings/images/physiological findings/identifiers, APKs, credentials, keystores and provider material are not staged.

Prior five-minute physical PASS, admitted pair, synthetic duration evidence, all original failed markers and STOPs remain distinct and intact. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4 and scientific selection/window ownership unchanged. Samsung native-sequence/final-batch and endpoint/drain work separate; no IDC, RMSSD/baseline/learning/Loop A/N1 action.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_SANITIZED_RECEIPT_TRANSACTION_DEADLINE_VIDEO_REFINEMENT
