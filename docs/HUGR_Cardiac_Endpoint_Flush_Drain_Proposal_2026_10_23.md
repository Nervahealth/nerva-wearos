# Cardiac endpoint: flush before listener teardown, without silently extending the recording

**23 October 2026. Source-bound proposal only.** No source implementation, test execution, APK build, installation, device action or recording occurred. This continues the existing acquisition-owned final-batch/endpoint investigation; it does not reopen or alter the completed receipt-operation timeout work.

The supplied support confirmation is consistent with the exact SDK 1.4.1 API and Samsung's public documentation: **`HEART_RATE_CONTINUOUS` supports `HealthTracker.flush(): boolean`; `onFlushCompleted()` is a separate completion callback; stopping tracking uses `unsetEventListener()`.** A listener must still be registered for flush. A successful request does not itself mean that callback data have been durably captured.[1] [2]

The original private correspondence has **not yet been supplied**. The user's received statement is preserved privately and distinctly from an original Samsung quotation; its receipt date is not a Samsung reply date. An exact original, when supplied, should be kept unchanged with byte count and SHA-256, outside GitHub. This proposal's public API/source findings do not depend on pretending that original has been received.

## Exact SDK and source binding

The controlled Watch source reviewed was clean at commit `b9e0b60ceab309bda5ba7b23ea108a0331917c61`, tree `92d86953cb37d7f6f19bf3485b424ea159030ac1`. It includes `app/libs/samsung-health-sensor-api-1.4.1.aar`, **61,063 bytes**, SHA-256 `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`, declared as an implementation dependency. Direct static inspection establishes the methods and listener signatures. Runtime tracker capability and Health Tracking Service behavior remain separate from the AAR identity.

Current source hashes are:

- `HealthSensorService.kt`: `51f4419d2afe4946473bb1d21f4234079015e13cd937f4ddda514d4462464525`.
- `FreshRunSession.kt`: `9ee3d87be4c101c9f7f8463f7ef26e2af859ece259c85a8595ce11c14aa46932`.
- `FreshRunReceipt.kt`: `e6b7a3bf7d7b6eb68e5e00ba52466185f2657c4b035850c8316c5c3a8f49de0c`.
- `BoundedOrdinaryRunGate.kt`: `5b7bbb31d1dd3d3c6799d172872322d1bf104998942559c110525c793264bf11`.

These are current-source facts, not a new attribution of current code to the historical physical timeout or evidence that a particular final batch was lost.

## What the current endpoint actually does

`FreshRunSession.acceptsSamples()` requires elapsed time to remain inside the requested duration. Define **T = receipt.startElapsedMs + receipt.durationMs**. The acceptance interval is `[startElapsedMs, T)`; the SDK-point timestamp is not part of that predicate.

At the timer boundary, `HealthSensorService.finalizeBoundedOrdinaryRun()` sets `stopped=true`, quiesces the fresh session, calls `stopTrackingAndDisconnect()`, and only then synchronizes/seals the journal and publishes finalization. Cleanup calls `hrTracker.unsetEventListener()`, clears tracker references and disconnects the SDK service. There is no terminal HR flush or completion wait (`HealthSensorService.kt:225–264, 819–846`).

The cardiac listener rejects a late callback **before reading its points** (`:627–630`). Each derived HR/IBI/input-shape append rechecks the cutoff (`:660–699, 903–963`; `FreshRunSession.kt:35–53`). Consequently, even a callback admitted just before T can be split during its derived writes when the duration boundary is crossed. These are source-proven potential discard/partial-group mechanisms—not a demonstrated explanation of the historical endpoint.

There is already a screen-off 30-second periodic flush attempt. It ignores the return values, and the cardiac `onFlushCompleted()` body is empty (`HealthSensorService.kt:725–729, 1054–1083`). A flush-attempt counter is not a completion count. Periodic flushing is not a terminal drain, and an outstanding periodic completion must not be mistaken for a terminal one.

## What SDK 1.4.1 guarantees—and what it does not

The exact AAR returns false for an unregistered listener, unsupported local tracker type, absent service connector or remote-call failure; those branches do not necessarily invoke `onError`. It returns true after its remote flush invocation returns normally. `onError(TrackerError)` and no-argument `onFlushCompleted()` are distinct callback paths.

Samsung documents that an empty collection may produce `onFlushCompleted()` without `onDataReceived()`. Empty completion is therefore not corruption or evidence of missing physiology.[1] The listener continues tracking until it is unset; flush is **not an atomic flush-and-stop operation**.[1] [2]

Neither the inspected wrapper nor the public listener contract establishes that all relevant data callbacks happen before the completion callback, a documented callback thread, a cross-callback exactly-once rule or a request ID in `onFlushCompleted()`. The wrapper invokes the application listener directly; there is no app-visible sequencing queue that supplies those missing guarantees.[3]

`DataPoint.getTimestamp()` is documented in milliseconds but does not specify the clock domain or acquisition/IBI/batch referent needed to compare it with T.[4] Therefore a callback delivered after T cannot presently be asserted to contain only pre-T measurements. This remains separate from the clarification agent's scientific selection/window and native-IBI questions.

## Smallest bounded sequence I propose

The requested duration remains unchanged. The proposed **10,000-ms drain-control budget starts at T**, does not restart on a callback, and consumes time inside the existing duration-plus-15-minute finalization/delivery horizon. It is an engineering waiting cap, **not a Samsung latency or hardware-stop guarantee**. It does not change the Phone ten-second receipt-operation timer or 180-second custody budget.

1. **Close ordinary cardiac ingress at T, but finish work already admitted.** A cardiac callback entering before T gets one bounded callback-admission ticket. Its existing HR/IBI/V2-shape group can finish atomically with respect to finalization, including after T. This is an explicit, cardiac-only proposed change to the per-record cutoff behavior—not a relaxation for all streams or proof of individual measurement times. Preserve record capacity, journal I/O checks, original slot/status markers and canonical encoding.
2. **Cancel future periodic HR flush scheduling and establish ownership.** Route HR periodic and terminal flush requests through one small coordinator, preserving the existing cadence/screen condition. Allow only one outstanding HR request. If a periodic request is already pending at T, account for it within the same ten-second budget; do not issue an overlapping terminal request or label an unidentifiable completion as terminal success. If ownership cannot be established, record `FLUSH_OWNERSHIP_UNRESOLVED` and stop rather than guessing. Other trackers' policies are not expanded.
3. **Issue at most one terminal `hrTracker.flush()` while its listener remains registered.** Run SDK commands off the main/control handler and journal lock. Record true, false or thrown/blocked request separately. A true return is request outcome only. False/error is terminal failure, not permission to retry. Completion may be reentrant relative to the command return; the coordinator must handle that without double cleanup.
4. **Receive pending cardiac callbacks through the bounded drain ingress.** Preserve a pre-T ticketed group on its existing canonical path. For newly entering post-T callbacks, preserve their bounded raw-shape/HR/IBI evidence in a **separate run-linked, unqualified cardiac tail**, using the existing positional capture and codec—not the accepted run's manifest inventory. Record callback ingress elapsed time, raw parent timestamp, callback/point/list coordinates and drain status. Never renumber or reinterpret existing recording bytes, invent beat times, broadcast this tail as live physiology, or call it proven pre-T data.
5. **Observe completion/error/deadline without assuming provider order.** Register in-flight callbacks at ingress, before enqueueing work, and release them in `finally` after their whole capture group. Use a bounded serial app queue. On completion, close new admission and request listener unset; drain all work already registered behind an app-owned barrier. A completion-first/data-later race is a recorded late/uncertain event, not proof of an empty tail or successful whole-window drain.
6. **Stop before sealing.** Request `hrTracker.unsetEventListener()` and normal SDK disconnect on success, false/error or expiry. Preserve whether each request actually returned. A hung SDK call cannot be made safe merely by timing out its caller: the control path must stop waiting, close app ingress and report `SDK_STOP_UNCONFIRMED`; it must not claim physical sensing stopped. Use a separately bounded cleanup task if a flush command is stuck, not an unbounded backlog behind it.
7. **Finalize once, only after app-owned work is closed.** The exact in-flight count, capture queue and private-tail publication must be settled before the terminal task acquires `journalLifecycleLock` to synchronize/seal the ordinary journal. No main-handler wait or holding the journal lock while awaiting Samsung callbacks. If the barrier, cleanup or publication misses the finite budget, record the unresolved state and do not force a concurrent seal or report successful finalization. Any later worker result cannot reopen admission or publish a late success for an expired drain generation.

**Tail retention is necessary to capture pending data, not merely count it.** The proposed tail is a narrowly scoped private evidence partition, not a new recording, exporter or telemetry platform. Initial engineering bounds are **2 MiB of encoded tail evidence and 128 callback envelopes**, whichever limit is reached first. Enforce bounded per-callback positional capture as today. Overflow preserves the admitted prefix and exact omission counts/reasons, marks `DRAIN_EVIDENCE_OVERFLOW`, and blocks endpoint-completeness claims. A publication failure remains explicit; no claim that lost payloads were preserved. These proposed limits require focused capacity checks; they are not expected sample frequencies or provider guarantees.

A payload-free count alone would expose discard but would **not retrieve the pending measurements**. That lower-scope option is diagnostic observation only and must not be presented as completion of this drain requirement. The unqualified tail and pre-T group ticket are specific proposed additions needing future source/test authority; nothing is implemented now.

### Fixed duration versus physical tracker stop

T stays the fixed application window. Arrival after T is delivery timing, not measurement timing. The HR listener must remain active to request/receive flush; Samsung explicitly says tracking continues until unset. Thus this sequence may leave SDK tracking active during the bounded drain. **It does not prove, or authorize by itself, exactly D seconds of physical sensing.**

If an exact physical sensor-stop-at-T rule is required, the known API is insufficient: we need a provider-supported atomic stop/drain or buffered retrieval-after-stop contract before implementation. Do not conceal this tradeoff by moving T or naming all drain data “already pending.” Other streams—including EDA—gain no drain guarantee or post-cutoff admission.

## Finalization and interpretation

Keep ordinary accepted records and their manifest/ACK rules unchanged. An app-owned barrier proves only that **registered app work** finished; it cannot prove that an undispatched Samsung callback never existed. Completion, request success, listener-stop request, private-tail publication, ordinary journal seal, Watch ACK acceptance and Phone durable custody remain separate facts.

Drain diagnostics must retain distinct states: request false, tracker error, timeout, ownership/order unresolved, stop unconfirmed, callback barrier unresolved, tail overflow/I/O failure, and `ENDPOINT_ELIGIBILITY_UNKNOWN`. A structurally valid ordinary FINALIZED receipt must not by itself erase these states or qualify full cardiac endpoint coverage. Publishing an unqualified ordinary prefix must not cause an automatic compatible-pair PASS that ignores its drain result; any necessary failure-state/receipt gate must be explicitly scoped and tested before implementation. No receipt-page or transport-format change is proposed here.

The safe result with current timestamp/order evidence is **ordinary-window bytes preserved plus an explicitly unqualified captured tail**. Promotion of that tail into a fixed measurement-time window remains blocked until the relevant provider/run-applicable membership evidence is available. No retrospective recording is repaired.

## Focused tests to add after source/test authorization

Reuse the existing fresh-service, ordinary-runtime, cardiac positional, receipt and duration tests. These additions are proposed—not executed:

- A callback admitted just before T finishes its whole HR/IBI/input-shape group after T; ordinary post-T ingress remains blocked. Test cutoff equality, journal/record-capacity failure, worker exception and no partial group mislabeled successful.
- One terminal flush with data-before-completion, completion-before-data, synchronous/reentrant completion and valid empty completion. Completion never substitutes for the app barrier or data persistence.
- Pending periodic flush at T, periodic-completion/terminal-request races, false return without `onError`, duplicate/unsolicited completion and ownership uncertainty. No overlapping flush, retry or invented request correlation.
- Mixed/pre/post-cutoff and clock-ambiguous points remain in the unqualified private tail with exact raw positional evidence; no comparison of unknown SDK clock to elapsed T. Bounds, overflow and I/O failure preserve explicit omissions and prevent qualification.
- Error, absent completion, blocked flush/unset and late results after ten seconds. The independent deadline is never reset; stop-unconfirmed is distinct from stopped; no late worker reopens or finalizes an expired generation.
- Concurrent callbacks, in-flight registration before enqueue, callback/journal-lock contention and blocked persistence. Seal cannot race admitted work; unresolved barriers cannot be bypassed.
- Offline restart/read of the bounded tail/diagnostic without ordinary journal recovery, and unchanged original run bytes/receipts. No new export route or real-record access is included.
- Isolation: EDA and other streams retain their existing cutoff; V1 readability/V2 slot/status bytes, manifests, exact ACK checks, receipt-operation correlation, deadlines and capacity limits remain unchanged except the explicitly tested cardiac group-ticket/tail behavior.

Two narrowly necessary provider questions remain: **does the applicable runtime deliver every flush-associated callback before completion, and how is a delivered point assigned to the fixed acquisition cutoff?** The original reply may answer part of this. No provider contact is performed.

## Existing work remains separate

This narrows the acquisition owner's existing endpoint/final-batch investigation from “pending” to **API confirmed/source mechanism identified/prospective drain proposed; historical cause and endpoint completeness unresolved**. It does not attribute the receipt-operation timeout to missing flush, change the held diagnostic-correlation candidate proposal, rerun its tests or absorb this drain into that build lane.

Only this sanitized source/API proposal is eligible for GitHub preservation: source-owned document first, Master then Alignment last, bounded documentation commits, controlled non-force mirrors with both-repository write preflight and exact remote readback. The received statement, original correspondence, provider-private material, physiological bytes and SDK binary remain excluded.

T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, learning and Loop A are unchanged. Samsung native-sequence semantics remain separate. No IDC or N1 action. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

## References

[1]: https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/HealthTracker.html "Samsung HealthTracker flush and listener teardown API"
[2]: https://developer.samsung.com/health/sensor/guide/tracking-data.html "Samsung tracking, batching, flush and stop guide"
[3]: https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/HealthTracker.TrackerEventListener.html "Samsung data, error and flush-completion listener contract"
[4]: https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/DataPoint.html "Samsung DataPoint timestamp contract"

END_OF_CARDIAC_ENDPOINT_FLUSH_DRAIN_PROPOSAL
