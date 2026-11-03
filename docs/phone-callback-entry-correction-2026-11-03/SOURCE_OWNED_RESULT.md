# Phone callback-entry correction and build decision — 3 November 2026

**Recommendation: propose one Phone diagnostic candidate on the established build/admission route. Do not spend another session trying to make Honor HCI export work first.** The focused source/save gate now demonstrates a useful new observation; no APK, version reservation, installation, reconnect or recording has occurred in this task. A build and any physical probe require the next explicit approval.

## What changed, and why it is useful

The old Phone71 callback method already calls an observer before Rx filtering. Its observer/recorder can nevertheless discard an unmatched callback without making a per-entry event. Publisher retention cannot recover that missing entry. This was a diagnostic blind spot, not a demonstrated transport-timeout cause.

The exact callback method now has one added first statement, before ordinary logging. All other bytes of that source file are unchanged. It admits one payload-free observation per callback to an independent, preallocated 64-entry lane: first 32 entries plus rolling last 32. It uses a nonblocking admission lock and makes no filesystem, Rx, payload or selector call during admission. Separate bounded classification cannot remove the admitted entry. Raw GATT/characteristic objects, UUIDs, addresses and payloads do not leave the observer.

A second, version-labelled **current callback probe V1** is saved in its own app-private directory, outside the strict old incident directory. It contains capture instance/epoch, native clock/sequence, status, characteristic/identity classifications, association uncertainty and explicit losses. A process-wide queue permits one off-critical native checkpoint in flight and one coalesced pending outcome. Valid pending write success, rejection or deadline can checkpoint this probe without fabricating a failure or changing the old incident. Coalescing is explicit. Duplicate, unrelated read and late-ignored outcomes cannot relabel a successful or deadline probe.

The existing isolated diagnostics activity adds a read-saved-probe control. It reads only the separate saved file; it does not construct React Main, BLE or the live trace. Save is bound to the exact selected prepared report and probe selection is disabled while a document save is pending. The old incident reader, original failure publisher and original private Save byte-copy helper remain unchanged.

## The old/new saved difference

The preserved baseline invokes the actual `onCharacteristicWrite` method **source slice** with the actual observer and recorder, supplying a same-UUID characteristic object that has no diagnostic identity match. The old recorder counts one unassociated callback but emits no raw callback event. The real serializer, bounded publisher, incident store, isolated reader and Save helper produce the preserved old saved file with that entry absent. The baseline intentionally ends in an expected failure; its logs, compiled output, exact method slice and checksum-bound original driver are retained.

The corrected case invokes the same method with the same mismatch. The old incident path still has no invented matched event. The separately saved new probe has one original callback entry, `SELECTED_RECEIPT`, different characteristic identity, `UNMATCHED`, and `UNCERTAIN` timing. The old incident and native sidecar are hash-checked unchanged before and after the new publication. This is a concrete hook-to-saved-output distinction, not a hand-authored downstream callback row.

The test boundary is explicit: the exact callback-method body is exercised with declared Logger/native-dispatch/output collaborators and minimal Bluetooth object doubles. The actual production Java observer/lane/recorder and Kotlin stores/serializer/Save helper are exercised. It is not an execution of the complete RxAndroidBle class, Android framework callback, radio, Watch or physical app.

## Verification earned

**Twenty focused cases passed.** They cover unmatched and successful saved probes; late callback after observed cancellation; duplicate/stale and multiple identity candidates; unexpected characteristic and nonzero status; ordinary logger/selector exceptions; ordinary 128-event-ring crowding; first-32/last-32 overflow; busy admission and association loss; clock regression; capture restart, inactive and disabled capture; interrupted checkpoint; closed-schema rejection of false timely association; malformed coalescing/oversized file refusal; and unchanged callback collaborator control flow.

**Six actual TypeScript coordinator/wrapper cases passed with a declared native seam.** Success checkpoints a new probe without publishing an old incident, duplicate/late outcomes do not relabel it, unrelated reads do not checkpoint it, publication is detached/coalesced, deadline labels survive late outcomes, and native probe refusal neither retries nor turns into a transport failure.

**Eight existing retention cases passed again.** Oversized protected incident context, ambiguity, first-failure preservation, refusal rather than destructive replacement, and ordinary output remain covered by the unchanged real publisher/store/reader/Save harness. There was no timeout, transport or Watch change.

The complete changed isolated Android reader compiled against the already-available Android API and focused classes. The exact new native AsyncFunction **body slice** also compiled with a declared Context seam. The whole Expo module, full application and actual RxJava runtime were not compiled/exercised here. Compiler/API resources were reused; no dependency acquisition or new build framework was introduced.

An older broad regression runner is **not PASS**: its direct Node loader cannot resolve extensionless TypeScript imports, and it has dated 512-KiB/per-row-identity source expectations that differ from the unchanged accepted source. With a loader-only resolver, five of six old model assertions passed; the remaining one expects identities repeated on rows rather than inherited from the compact V3 root. Those original tests and product files were not edited to manufacture a pass. Their logs and exact unchanged-source hashes are retained separately; this is not a full-suite claim.

## Timing, association and missing observations

`TIMELY_NATIVE_PENDING_WINDOW` means a unique retained local identity, the first observed callback for that identity, a valid native clock and a pending native write window. It does **not** mean “before the JS deadline”: JS/native clocks were not joined. `LATE_AFTER_CANCEL_OR_TERMINAL` requires an actually observed local cancel/terminal boundary. A duplicate, multiple candidates, invalid clock or known capture/association/entry loss yields `UNCERTAIN` rather than an exact request match.

Each probe explicitly says its scope is the native capture epoch, not an exact ATT/JS window. A locally generated capture-instance ID distinguishes recreated in-memory lanes. Process death before checkpoint remains UNKNOWN. Entry absence, a zero count or a port/header is never proof of callback non-arrival, ATT delivery or Watch acceptance. The new lane is bounded, not guaranteed complete.

A positive relevant callback entry would establish that Android called this Phone callback method. With the retained ordinary completion chain, that can justify a focused observation/identity or framework-completion investigation. It still cannot identify an exact ATT request/response by local counters or shared characteristic identity. A missing entry with losses remains UNKNOWN; it does not justify another automatic candidate or a timeout increase.

## Preservation and next action

Nine production paths changed: seven modifications and two new diagnostic classes. Every modified preimage matches the admitted Phone71 candidate; both new paths were absent there. Package/version/build profiles, available lock/checksum files, incident model/context, original publisher/stores and Save helper were verified unchanged. Original private Phone71 incident custody remains separate. Source/test GitHub preservation follows the standing Phone source-owned → Watch documentary mirror → Master → Alignment-last order, with actual writes preflighted on both repositories and non-force remote head/tree/blob readback. Its final result is delivered separately, not presumed from this report.

The next proposal is **one new Phone candidate, expected Phone72 only after conflict/reservation checks**, prepared from the admitted Phone71 input and these exact diagnostic changes. Reuse the successful strict-verification, dependency and signing route; do not rerun the old containment programme or change timeouts/dependencies. Complete candidate compile/build/admission before any installation decision. A material failure stops the route with its first actionable cause, not an automatic retry.

After separately approved preservation-safe installation, use one short FAILED/idle receipt-only exchange and save/read back the new current probe before considering another 30-minute run. That future exchange needs a fresh Watch eligibility check, one serve and one Connect. It is not passive. No Start, recovery/backfill, pairing change or retry is authorized by this source-only result. A short successful diagnostic readout would validate this observation route, not cure the old timeout or pass the 30-minute milestone.

The dependable-run objective remains: Watch capture continues, Phone receives records while capture continues, and final saved custody is verifiable. None of those physical outcomes has been newly earned here. Clarification timing/scaling work and historical qualification labels remain unchanged.
