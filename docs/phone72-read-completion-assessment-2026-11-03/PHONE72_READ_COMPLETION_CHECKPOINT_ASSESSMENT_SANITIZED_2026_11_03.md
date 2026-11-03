# Phone72 read-completion and checkpoint assessment — 3 November 2026

**READ-ONLY ASSESSMENT COMPLETE. Diagnostic coverage/interpretation limitations explained; physical read-timeout cause UNKNOWN.** The closed attempt remains closed. This is not source-change, test, APK, connection, recording or device authority.

## Target and comparator

The preserved latest failure is a RECEIPT_READ for application page 4224, primary local operation 1061, reaching its JS deadline in 10,024 ms. Its preceding same-page R1_WRITE, primary local operation 1060, fulfilled in 129 ms. The preceding page-3840 RECEIPT_READ, primary local operation 1059, fulfilled in 98 ms. Counters and domains remain separate; application page offsets are not Android GATT read-offset arguments.

The target read explicitly retains nativeAttDispatch UNKNOWN. Android API acceptance of the preceding write cannot establish that the failing read reached the API. Target cancellation and a late OperationCancelled rejection follow the app deadline. Subsequent connection cleanup/disconnect cannot alone establish spontaneous Honor background-process death or the initiating link cause.

## Demonstrated checkpoint explanation

1. The independent entry hook is first in onCharacteristicWrite, not onCharacteristicRead. The lane is WRITE-only, including its OTHER-characteristic entries.
2. The detached probe coordinator admits R1_WRITE/C1_WRITE/OTHER_RECEIPT_WRITE/SOURCE_ACK outcomes only. RECEIPT_READ is excluded, including its deadline. Primary incident freezing/publication still occurs. Thus a fresh failed READ primary plus an older success-labelled WRITE checkpoint is compatible by construction, not demonstrated failed READ-trigger persistence. A READ whitelist addition alone would still snapshot WRITE events only.
3. The probe has 542 attempted WRITE callbacks, 64 retained entries and 478 ring evictions. Its snapshot intentionally downgrades every timing label to UNCERTAIN after any capture gap/loss; the store validates that rule. This explains all 64 uncertain labels. It does not prove every callback was late or a pending-window hook was absent. Overflow and eviction counters describe the same bounded-ring removals, not double the lost events.
4. Up to 32 historical operations retain the same private GATT/characteristic object pair even after completion; annotation counts all matching retained pairs. Increasing association ambiguity is expected and is not proof of concurrent ATT requests. No exact ATT join or arbitrary active-row repair is warranted.
5. The separate private-copy preparation failure occurs after complete incident text is obtained and disables Save/Share. It is not callback capture or probe storage failure. Its underlying preparation cause was not audited. Copy/paste body integrity remains earned; successful Phone-local Save/reopen remains unearned.

## Read completion path and ceiling

Accepted code subscribes to the read callback before Android start, then uses the characteristic-UUID-filtered first callback, native relay and BLE-PLX promise completion. No incident-linked subscription-order, UUID-match, dispatcher, queue, native API or promise defect was demonstrated. The successful read comparator proves earlier operation of the path, not this read’s outcome.

The reviewed Watch server source remains identical to the preceding accepted baseline. It handles a receipt read by obtaining the prepared frame and attempting a response or explicit failure. Exact installed/runtime source equivalence is not newly established. No target-linked Watch read/response row or complete HCI trace was received. A server response API true return, if observed later, would not itself prove host delivery. Watch terminal classification is not proof of another user Connect or of the target transport cause.

Last confirmed forward event: the JS read invocation after the preceding R1 write fulfilled. First unknown: whether the exact read entered/dequeued natively and whether Android readCharacteristic was called and accepted it. Raw READ callback entry/status and relay/promise completion are downstream unknowns.

## Decision

No blind next APK, timeout change or repeat recording is justified by this assessment alone. Recommended next move: focused Android BLE specialist review of the exact native READ dispatch/API-return → raw READ callback → observer/UUID filter → promise boundary, using the retained same-attempt comparator. Any diagnostic extension needs a read-specific entry/dispatch observation and outcome/provenance contract; merely refreshing the WRITE checkpoint does not answer the question. No specialist contact or execution occurred.

No application source, dependencies, timeouts, Watch, physical state, old incident or scientific input changed. No tests, builds, APKs, device actions, recording, Bugjaeger or logging changes. One independent read-only checkpoint subassessment was used. Raw reports, media, private identifiers and APKs remain off GitHub. Source-owning Phone receipt precedes Watch mirror, Master continuation and Alignment last; VIS-COUNT is unchanged.
