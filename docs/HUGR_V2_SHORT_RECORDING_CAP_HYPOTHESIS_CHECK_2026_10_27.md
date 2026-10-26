# V2 short-recording-cap hypothesis — bounded read-only check

**Question:** Is the stopped Watch85/Phone67 30-minute attempt suffering from a leftover shorter-recording-only allowance? **Decision:** No such leftover five-minute cap was found in the examined active V2 recording/delivery/ACK path. This specific explanation is not supported by current source and already-earned artifact admission. This does not prove absence of other software defects or establish the physical ACK failure's cause.

Phone67 was installed during the failure. Phone68 is now installed by report; its change aligns the diagnostic model/native saver schema only. `durationLadderContract.ts`, `bluetooth.ts` and `watchRunReceiptTransport.ts` retain identical transport/duration bytes across the retained Phone67/68 inputs. Watch85 remains unchanged. No source/test/build/device action, recording, private-record inspection or capacity-ladder rerun occurred in this check.

## Active values and source evidence

| Boundary | Verified source behavior | Locator |
|---|---|---|
| Watch V2 duration | Explicit 5/30/120/480-minute closed ladder; default 30 minutes | `watch/app/src/main/java/com/hugr/wearos/WatchFreshRunDurationPolicy.kt:8–43` |
| Watch recording cutoff | Admission, append cutoff and stop scheduling use the selected receipt's `durationMs`, not a fixed five minutes | `FreshRunSession.kt:24–38,44–48`; `HealthSensorService.kt:154–159,184–192` |
| Watch delivery and wake lock | V2 selected duration + fixed 900000 ms grace; a 30-minute run has a 45-minute Watch-origin lifetime. ACK requires `permitsDelivery()` against that receipt deadline | `FreshRunReceipt.kt:45–51`; `FreshRunSession.kt:137–138`; `BleGattService.kt:73–91,2242–2249`; `HealthSensorService.kt:307–315` |
| Legacy five-minute defaults | V1 receipt default and `BoundedOrdinaryRunPolicy` five-minute check remain for historical V1 only; the V2 service checks the closed ladder instead | `FreshRunReceipt.kt:14–24,55–59`; `HealthSensorService.kt:156–159`; `BleGattService.kt:318–339` |
| Phone/native V2 duration | Exact 5/30/120/480-minute admission, independent of the retained V1 five-minute contract | `phone/lib/durationLadderContract.ts:8–14,30–37`; native `DurationLadderPolicy.kt:12–24,55–59` |
| Phone 20-minute bootstrap | Only until a strict native-persisted validated receipt rebinds it; validated V2 horizon becomes requested duration + 15 minutes from Phone connection monotonic origin | `durationLadderContract.ts:18–20,43–66`; native `DurationLadderPolicy.kt:86–117` |
| Individual receipt/ACK operation | Unchanged 10000 ms per-operation deadline, not a recording-duration allowance | `durationLadderContract.ts:21`; `bluetooth.ts` bounded receipt operation and source ACK call |
| Terminal copied-source verification | 180000 ms remains finite and unchanged; terminal V2 selected-source verification starts on COMPLETED receipt, not as a live source-ACK write limit | `PhoneCanonicalEvidenceCore.kt:20–33`; `BoundedSelectedSnapshot.kt:602–650`; `PhoneWatchReceiptBridge.kt:24–40` |
| V2 capacity | 2 MiB receipt, 2048 manifests/ACKs, 10240 stream ranges, 2000000 canonical records, 512 MiB accepted source bytes | `WatchFreshRunDurationPolicy.kt:19–29`; Phone `DurationLadderPolicy.kt:26–53,61–84`; `WatchRunReceipt.kt` strict inventory checks |

The Phone's rebind does not synchronize Phone and Watch clocks or reset the Watch's original lifetime. A missing validated rebind could leave the historical bootstrap bound active; this check does not claim that the failed physical attempt's native rebind was independently observed. That would be a deadline-related issue, not evidence of a five-minute-only recording restriction.

## ACK path and why the observed label is different

Phone source ingestion durably appends/verifies records and exact manifest membership before issuing the ACK. `SOURCE_ACK_WRITE_REJECTED` at `phone/lib/bluetooth.ts:1181–1184` wraps a non-typed-deadline rejection from the source-control write-with-response promise. The typed 10-second deadline is explicitly rethrown and classified separately as `SOURCE_ACK_WRITE_TIMEOUT`. The broader source-failure classifier has acknowledgement/lineage fallbacks, so generic classification alone must not replace an actual native cause.

Watch ACK handling (`BleGattService.kt:2242–2297`; `FreshRunSession.kt:99–121`) requires an active receipt-derived delivery lifetime, exact source/window/queued endpoint, retained finalized manifest/hash, no duplicate accepted tuple and durable pending/accepted ACK progression. It has no fixed five-minute or small-rung ACK-count ceiling. These guards remain intact; no guard has been removed or deadline widened.

A native append batch has a 1024-record per-call bound (`HUGRSourceJournalModule.kt:26,180–190`), but a batch-limit rejection would precede manifest verification and the source ACK emission rather than directly create a rejected ACK-write promise. No physical count/cap-proximity calculation was performed here.

Already-earned signed admission receipts for Watch85 and Phone67 assert PASS for closed V2 duration/lifetime and inherited ACK guards; Watch85 also asserts compiled hard sample-cutoff and exact ACK-before-completion checks. Phone68 admission retains these invariants. Compiled evidence does not prove hardware runtime behavior. No admission or synthetic ladder was rerun.

## Remaining question and next move

The exact native write error/status and same-attempt disconnect sequence remain absent. Source alone cannot distinguish local bridge/pre-dispatch failure, native queue/GATT error, link loss/cancellation or a Watch-side ATT failure response. It also cannot prove exact Phone/Watch request association from equal counters, offsets or clocks.

The next proposal remains a bounded **wired Fold→Honor setup-only capture/retrieval proof**, with explicit authority, no HUGR launch/Watch/Bluetooth/Connect/new recording/HCI/log clearing. Confirm actual native logging visibility; a synthetic marker is not proof that required callbacks/error codes are emitted. If visibility is absent, report that named blocker before recording. Separately authorize a passive Watch85 saved-receipt read before any fresh-run decision. No new physical attempt is authorized by this question.

## Preservation and unchanged boundaries

Source-owned result → Master → Alignment last → bounded documentary commits → controlled both-repository write preflight and non-force mirrors → independent exact remote head/tree/changed-byte readback. Previous failures, receipts, tests, admitted artifacts and actual private recordings/media remain unchanged; private content stays off GitHub. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, selection/window ownership, endpoint/drain, baseline/learning, Loop A, IDC and N1 remain separate. Watch69 `NOT QUALIFIED`; Watch68 v1 `UNCLASSIFIED / NOT VERIFIED`; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
