# Related upstream issue findings — bounded advisory

The external record supports separating the currently unobserved native boundaries in the saved Android BLE write incident. It does **not** establish that the present incident is an RxAndroidBle defect, an Android-platform defect, a peripheral defect, or a React Native bridge defect. It does not support changing time budgets, retry behaviour, or dependency versions as a remedy.

## What the sources support

1. **Missing raw write callback is a documented upstream symptom class, not a diagnosis.** In RxAndroidBle issue #118, a reporter described a platform `writeCharacteristic` call followed by no corresponding `onCharacteristicWrite`, after which the serialized process stopped. The maintainer agreed that read/write operations should time out under this stack-misbehaviour case and later stated that a fix had been pushed. This is a historical reporter symptom with a maintainer-addressed handling change, not a proof of root cause or a version-matched defect in the pinned integration. It is the strongest direct precedent for treating “start occurred, callback absent” as a distinct observable branch. [1]

2. **A cannot-start branch can coexist with notification/receipt-like activity.** In RxAndroidBle2 issue #463, the reporter reproduced repeated writes in a setup that also received `onCharacteristicChanged` events, but showed a `BleGattCannotStartException` for a characteristic long write. The public issue labels it a library bug, but its rendered discussion provides neither a maintainer causal explanation nor a confirmed fix. It is therefore a reported symptom/reproducer only. Its practical relevance is narrow: notification or application-response activity is not evidence that a subsequent raw write was admitted by the GATT layer. [2]

3. **The pinned dependency version does not make newer behaviour applicable by default.** The official release entry for RxAndroidBle `1.17.2` identifies a ProGuard log-tag-generation fix. It does not, by itself, establish that the historical write-callback handling in #118, the reported cannot-start pattern in #463, or any later write-queue behaviour is present in the pinned build. These discussions should guide observation labels only, not a claim of version-matched causality. [3]

4. **Android distinguishes initiation from completion.** The legacy `BluetoothGatt.writeCharacteristic` contract says `true` means the write was initiated successfully; it separately says the completion result is reported through `BluetoothGattCallback.onCharacteristicWrite`. Therefore a native start return and the raw callback are two necessary, non-interchangeable observations. A rejected/non-successful start is not “a missing callback,” and a successful initiation does not establish callback arrival or write outcome. [4]

5. **An unresolved JavaScript Promise does not locate the loss boundary.** React Native documents that an Android native-module method may retain a Promise and later resolve or reject it, once only. Consequently, the JS-side continued await establishes neither that the native write was started nor that the GATT callback was received; it also does not establish whether native settlement was never attempted versus attempted too late for the app guard. [5]

No technical literature was used: the directly applicable platform and upstream primary sources are sufficient for this narrow instrumentation question.

## Consequences for the current bounded hooks

Use one correlation token already scoped to the native write, without adding an event stream or a new research programme. Its only purpose is to order these existing boundaries:

- **Queue leaves for execution → native write start call → immediate start result.** This separates queue residence from an attempt that could not start. It is particularly important because the native callback budget is understood to begin only once the queued operation has left the queue, whereas the JS guard includes earlier queue/bridge waiting.
- **Raw `onCharacteristicWrite` receipt and status → Rx/native terminal path and serialized-operation release.** Record callback arrival and status before any library-to-bridge interpretation. Record the terminal success/error/cancellation branch and the actual release of the serialized operation separately. Android’s contract makes the callback, not an application receipt/ACK, the platform completion boundary. [4]
- **Native `Promise.resolve` / `Promise.reject` invocation attempt.** Record only the attempt and its branch, not payloads. This is the bridge boundary that React Native describes; it is distinct from the JS caller observing settlement. [5]

The existing JS guard firing should remain a final, separate marker. It should not be used to backfill any of the native events above.

## Bounded test/observable consequences

- **No native start marker or non-success start result:** the incident is before raw callback waiting. No raw write callback should be expected from that attempt. This fits a start/admission branch, not a callback-silence conclusion. It is compatible with the reported cannot-start shape in #463, but does not establish that issue’s cause. [2]
- **Successful start marker but no raw callback before cancellation/disconnect:** this is the same *shape* as the historical RxAndroidBle report, but remains only a platform/library/peripheral-compatible observation. It would make the raw callback boundary, rather than the JS await alone, the evidenced missing observation. [1]
- **Raw callback with non-success status:** completion arrived at Android with a failure result. The next discriminating question is whether the native terminal path and Promise-rejection attempt followed.
- **Raw callback received, but no native terminal path or Promise-settlement attempt:** the unresolved interval is downstream of the platform callback, within operation handling, serialized-operation release, or bridge settlement.
- **Native Promise-settlement attempt recorded, but the JS guard still fires:** this narrows the mismatch to bridge/JS observation or ordering. It does not prove a React Native defect; the one-settlement rule means a second native settlement must not be used to overwrite evidence. [5]
- **Application-level receipt/ACK or unrelated append activity:** keep these as independent observations. They neither substitute for `onCharacteristicWrite` nor prove serialized-operation release. The Android completion contract and the reported #463 pattern both support that separation. [2] [4]

These branches are advisory test interpretations only. They neither replay the incident nor make a causal claim about the saved write, and they do not authorize device, source, timeout, dependency, or transport changes.

## References

[1]: https://github.com/Polidea/RxAndroidBle/issues/118 "RxAndroidBle issue 118 library should timeout read and write operations in case of BLE stack misbehaviour"
[2]: https://github.com/dariuszseweryn/RxAndroidBle/issues/463 "RxAndroidBle issue 463 connection write characteristic fails with characteristic changed callback"
[3]: https://github.com/Polidea/RxAndroidBle/releases "RxAndroidBle official releases including version 1.17.2"
[4]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt "Android BluetoothGatt writeCharacteristic API reference"
[5]: https://reactnative.dev/docs/legacy/native-modules-android "React Native Android native modules Promise documentation"
