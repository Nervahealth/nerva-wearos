# Phone69 receipt-write queue/completion — completed read-only result

**28 October 2026.** Authority: `HUGR_PHONE69_RECEIPT_WRITE_READONLY_AUTHORITY_2026_10_28.md`, following Neil's explicit approval at 00:05:38 +01:00. Scope completed through HUGR scheduling, pinned BLE-PLX 3.5.1 JS/RN/native adapter and retained RxAndroidBle 1.17.2 bytecode, including the adjacent actual timeout provider and active-operation cancellation/release wrapper.

## Decision

**No source-demonstrated transport defect or physical cure was established. A concrete observation gap was established.** The app-submitted receipt write is not observed at native queue admission/start, Android write-call return, raw callback entry, or native-to-JS settlement delivery. Its failure cannot be localized below the submitted Promise using the current saved report.

Two distinct budgets are source-bound:

- HUGR's existing **10-second Promise guard** covers the submitted operation, including bridge/native queue wait. It observes the deadline and requests cancellation.
- The actual pinned native characteristic-write path receives **30 seconds** from its connection setup/provider. Its callback timer starts after the operation leaves the connection queue, immediately before attempting the Android write call; it does not cover prior queue residence.

Thus HUGR can cancel a still-pending native write before its native callback timeout becomes observable. The consequent OperationCancelled is locally constructed by disposal and is not the initiating ATT/Android error. **This is a visibility/cancellation mechanism, not proof that a larger timeout would complete the recording. No deadline change is proposed or performed.** The numeric native default is established from retained source/bytecode, not measured as a timer running on the physical device during this incident.

## Exact source-bound path

All BLE package paths below are within the retained pinned compiler-stage link `.../compilation-preflight/source-stage/node_modules/react-native-ble-plx/`; no dependencies were retrieved or mutated.

| Layer | Actual mechanics | Locator |
|---|---|---|
|HUGR page request |Await `writeCharacteristicWithResponseForService` for the R1 offset request; only then read the receipt page |Controlled Phone `lib/bluetooth.ts:994–1099`; `lib/watchRunReceiptTransport.ts:157–221` |
|HUGR polling/custody |One sequential receipt observer; page reads precede source-idle/native persistence. Source ingestion/ACK work is serialized on its own chain, not a global app GATT lock |`watchRunReceiptTransport.ts:223–279`; `build45SourceIngestion.ts:94–111,194–213`; `bluetooth.ts:1132–1169,1232–1294,1774–1802` |
|JS BLE-PLX |Supplied transaction ID crosses native call; `_callPromise` resolves/rejects and parses native BLE-error JSON |`src/BleManager.js:189–203,224–251,783–804` |
|RN bridge |Native write hands callbacks to the adapter; success resolves, error rejects. SafePromise is first-settlement-wins |`android/.../BlePlxModule.java:608–635`; `utils/SafePromise.java:9–58` |
|Native adapter |Decode command, set WRITE_TYPE_DEFAULT, subscribe to `RxBleConnection.writeCharacteristic`, retain Disposable under BLE transaction ID; terminal success/error calls RN callback |`adapter/BleModule.java:1383–1439`; `adapter/utils/SafeExecutor.java:9–33` |
|Native connection queue |Serialized **priority FIFO** per connection; same-priority FIFO. A queued entry can be removed before start; queue worker advances on release |RxAndroidBle `ConnectionOperationQueueImpl`, its inner worker/cancellation classes, `OperationPriorityFifoBlockingQueue`, `QueueSemaphore` |
|Native write start |Set characteristic value, then call `BluetoothGatt.writeCharacteristic`. False produces BleGattCannotStartException; true proves Android API acceptance only, not ATT/radio dispatch |`CharacteristicWriteOperation.startOperation`; `SingleResponseOperation.protectedRun` |
|Native callback |Write callback matches characteristic UUID, first event; nonzero GATT status is an error. Callback delivery is scheduled even with its zero-duration delay |`CharacteristicWriteOperation.getCallback`; `RxBleGattCallback$2.onCharacteristicWrite` and status/error helpers |
|Native timeout |Builder default 30 seconds; `establishConnection(boolean)` does not override it; connection timeout provider passes duration/unit to `OperationsProviderImpl.provideWriteCharacteristic` |BLE adapter `BleModule.java:1239–1242`; adjacent Rx class/method locators below |
|Cancellation |BLE transaction removal disposes its subscription, constructing OperationCancelled locally. HUGR's cancel-call return does not await native/GATT completion |`BleManager.js:224–251`; `BlePlxModule.java:130–137`; `BleModule.java:1423–1437`; `DisposableMap.java:20–27`; `BleErrorUtils.java:7–10` |
|Active operation release |`QueueReleasingEmitterWrapper.cancel` sets a cancellation flag; it does not directly release the queue or unsubscribe its callback observer. onComplete/onError release |Rx `QueueReleasingEmitterWrapper`, bytecode `cancel bci0–8`, `onComplete/onError bci0–20` |

The explicit timeout chain is: `ConnectionSetup$Builder.<init>` bci 19/22 contains 30/SECONDS → `RxBleDeviceImpl.establishConnection(boolean)` bci 0–24 builds without override → `ConnectionModule.providesOperationTimeoutConf` bci 0–16 copies duration/unit → `OperationsProviderImpl.provideWriteCharacteristic` bci 0–21 passes the same configuration. `TimeoutConfiguration` itself merely stores the injected values. Service-discovery 20 seconds and callback-delay 0 seconds are **not** substituted as write timeouts.

## Relation to the actual saved incident

The already-verified private report records the R1 offset-zero deadline, then cancellation, then a late OperationCancelled, then connection teardown/disconnect. Source inspection explains how that later cancellation rejection is constructed; it does not identify an initiating native write result.

An interval check of every retained tracked operation found **no other retained receipt/source-ACK operation overlapping the failed write**. The ring reports no lost new operations; older evictions were completed rows. This does not establish a global empty native queue: other/untracked GATT work, earlier active native work after JS disposal, or callback/bridge delivery can remain unobserved. The source permits independent receipt/ACK scheduling, but the current report does not support assigning this failure to their overlap. Existing source-seam overlap and deadline tests are preserved, not promoted into physical native evidence and not rerun.

The prior appends while the write was waiting show some data processing continued, not that every Android/GATT scheduler or Promise route was healthy. Source-local callbacks, numeric transaction suffixes and separate diagnostic ordinals are not joined to Watch ATT requests. Native transaction IDs are subscription keys, not ATT request identifiers.

The pinned callback path matches by UUID without an intrinsic per-write/request identifier. A late same-UUID callback crossing operation boundaries is therefore a conditional correlation risk, **not a demonstrated event in this recording or grounds for a transport correction**.

## Smallest named missing observation

The next diagnostic addition, **for separate approval only**, should observe this one receipt-write path natively and persist it through the existing isolated Saved Receipt Diagnostics/Share route. It must distinguish:

1. Native method entry and explicit BLE transaction → native operation association.
2. Queue admission/start and queued removal or active release; bounded classification of any preceding queue owner, without payloads or addresses.
3. Android `writeCharacteristic` call and exact boolean return.
4. Raw Android write-callback entry/status, library callback consumption, and adapter/native Promise terminal emission versus the existing JS settlement observation.
5. Cancellation/disposal order and the effective native timeout value/unit.

Use separately declared native monotonic ordering/domain; do not equate it to JS performance.now or Watch elapsed time. Label Android boolean true **API_ACCEPTED**, never ATT_DISPATCHED/Watch_ACCEPTED. A callback associated with the current subscriber/UUID is not an exact ATT transaction match; retain any same-UUID late-callback ambiguity rather than inventing an identifier. Do not record raw error objects/messages, values/bytes/hashes from recording payloads, device addresses, raw physiological material or verbose library logs.

A wrapper only before the BLE-PLX subscription would still not show dequeue/start/callback and would knowingly leave the named gap. A substitute custom GATT operation that changes queue/timer behavior is not a diagnostic-only shortcut. The affected lower-stack hooks would require **controlled source-level instrumentation of the same pinned native code**, not cache/bytecode patching, an upgrade, generic logging or stubs. Authoritative source for the pinned Rx version and the integration arrangement must be established under subsequent source/test authority; source availability and compile feasibility were not assumed or tested in this read-only task.

This is a Phone-side proposal; no Watch change or HCI capture is justified now. The target is native enqueue/start/API callback/bridge outcome visibility, not comprehensive cross-device tracing. A later specific result could establish a Watch/radio evidence need; that would be a new, named decision, not a blanket prerequisite.

## One next decision

Approve or decline a **bounded Phone-only native-observation source/test package** at the above sites, using authoritative source for the same pinned versions, preserving operation order, queue release, timeouts, signing/package identity and transport/custody safeguards, and reusing the existing durable isolated retrieval route. No transport cure is proposed. No build, install, physical attempt or device action belongs to that approval. Any substantive change beyond observational hooks must be presented before implementation. Do not repeat the thirty-minute run with unchanged observability.

## Preservation and unchanged boundaries

Only read-only source/bytecode/actual-report inspection occurred; no application/native source or dependency/cache byte was changed, no tests or build were executed, and no device or Cloud Computer action was taken. The small task-local native class inspection copy is byte-identical to its retained AAR member. Original failures, signing/artifacts, evidence and current stopped/unqualified run remain unchanged. No two-hour/eight-hour advance, Samsung cardiac drain/sequence work, physiological analysis, learning or N1 action.

Exact operational report data, identities/counters/timings/hashes, private interval check, detailed private evidence bindings and inspection outputs remain off GitHub. Preserve this sanitized source-owned result and authority → Master → Alignment last → controlled non-force both-repository mirror and independent exact remote head/tree/blob readback. No APK/private recording/media/credential publication. Terminal directory-context errors were environmental read failures, resolved using explicit directory context; no storage was reclaimed or environment reset/migrated.
