# HUGR receipt-operation timeout — focused source investigation result

**Date:** 23 October 2026. **Scope:** Neil's 13:56:54 Europe/Oslo authorization for source investigation only. The scope was kept read-only: no application changes, test execution, build, deadline extension, device/information-launcher action, private app-storage access, record export or network research.

## Engineering decision

**The exact physical root cause is not yet proved.** The supplied evidence identifies Phone66's ten-second GATT-operation timeout after an established connection. Source review now identifies the actual response-critical paths and missing coverage; it does not justify a blind reconnect, a longer timeout, a new candidate or a speculative Bluetooth rewrite.

The smallest next engineering step is **one bounded source/test reproduction of the production receipt-operation and Watch reply paths**, with payload-free operation/response attribution. Implement only a defect demonstrated by that reproduction, with separate explicit correction authority. Until then, the physical thirty-minute gate remains STOP / NOT PASSED and both devices remain untouched.

## What the source establishes

| Boundary | Exact locator | Established fact | What remains unproved |
|---|---|---|---|
| Phone timer | `phone/lib/bluetooth.ts:837–846`; `lib/durationLadderContract.ts:21` | Unique named error producer races a submitted BLE operation against10,000ms and requests transaction cancellation on expiry | Operation role/page; actual native dispatch; queued vs in-flight; cancellation or late settlement |
| Phone receipt flow | `lib/watchRunReceiptTransport.ts:157–220, 223–278` | R1 page write then read; immutable snapshot, source/run/revision/hash/page guards; guarded final C1 | Which specific invocation stalled in the historical attempt |
| Phone source/control overlap | `lib/bluetooth.ts:876–897, 974–994`; `lib/build45SourceIngestion.ts:50–103` | Receipt observer and serialized source-ingestion ACK path have no shared application GATT gate; their library calls may overlap | Whether native library already serializes correctly or overlap caused any delay |
| Watch R1:0 response | `watch/.../BleGattService.kt:1219–1228`; `FreshRunReceipt.kt:283–301`; `FreshRunRuntime.kt:84` | Synchronous current-receipt read/parse, serialization and hash occur before write response; nonzero-page work is smaller | Callback scheduling, lock wait and measured duration on the Watch |
| Watch source ACK response | `BleGattService.kt:1283–1322, 2081–2136`; `FreshRunSession.kt:99–121`; `SourceJournal.kt:448–457, 577–597, 749–775` | Exact ACK validation, receipt durability/gates/fsync, manifest read/decode/hash and replay preparation can run before ATT response | Whether the observed operation was an ACK or waited behind that work; whether any operation exceeded10s |
| Watch response outcome | `BleGattService.kt:1157, 1228, 1322` | Receipt/source response calls are direct, nullable and ignore the sendResponse Boolean; no response timing/outcome is recorded | Whether a null/false send or missed callback occurred here |
| Failure containment | `phone/lib/bluetooth.ts:1441–1461` | Phone records deadline failure, cancels attempt/connection, then emits generic connection-failed/running-on-Watch hint | Watch app absence/crash or a prior spontaneous disconnect |

The Phone timer covers time after submission to the BLE library; it is **not demonstrated pure radio/ATT execution time**. The receipt/source-control overlap is a proven coordination exposure, **not automatically an implementation defect**: a lower library may serialize these calls. The controlled tree resolves `react-native-ble-plx`3.5.1, but installed native-library source was absent there; native queue/cancel semantics were not invented or externally fetched.

On Watch, the response-critical ACK path includes deliberately strict durability work. Removing verification or replying with success before the required durable acceptance is **not** a proposed shortcut. Synchronous work is a source-matched latency exposure, not a measured historical cause. No reversed application lock-order deadlock was found in the reviewed paths. Framework callback thread/serialization, lower BLE/radio scheduling and physical storage latency remain unresolved.

## What the existing tests actually cover

Existing receipt tests exercise page bounds, immutable snapshots, hashes, identity and completion order. Source-ingestion and ACK tests exercise durable equality, intent-before-write and serialized source work. Android service-flow tests cover synthetic service/lifecycle behavior and connection-state callbacks.

They do **not** exercise the production10s operation race/cancel behavior under concurrent receipt/source-control work. No reviewed Watch test invokes the actual characteristic-read/write callbacks and observes their sendResponse timing/result under storage/hash contention. These are concrete coverage gaps—not a retraction of previously earned synthetic custody tests, and not a claim that synthetic PASS qualified hardware GATT behavior.

## Smallest next proposal — NOT AUTHORIZED by this source-only instruction

### 1. Reproduce the named failure boundary without a device run

Use the production Phone operation wrapper under a deterministic clock/adapter seam and the actual Watch R1:0/read and source-ACK callback branches under a response observer. Reuse existing synthetic receipt/manifest fixtures and explicit controlled storage/response delays; do not replay an entire duration ladder or start another capacity programme.

Measure and attribute operation submission→settlement/cancel on Phone and callback entry→response attempt on Watch. Include receipt/ACK overlap, timely success, stalled operation, response failure, cancellation and late settlement. Verify that exactly one terminal failure wins, no retry occurs, stale settlement cannot create completion/ACK success, and exact source/run/revision/hash/page/ACK/custody checks remain intact.

Test the actual production branches, not only an unrelated mock helper. A controlled artificial delay establishes branch behavior/coverage; it does **not**, by itself, prove that the historical Watch incurred that delay. Separate natural synthetic measurements, controlled-injection outcomes and hardware attribution in the result.

### 2. Correct the demonstrated attribution gap narrowly

With explicit source/test correction authority, add only bounded payload-free diagnostics sufficient to identify operation class and attempt-local ordinal/page class, monotonic elapsed time, deadline/cancel/late-settlement outcome and Watch response timing/Boolean or normalized exception. Distinguish app/library submission from true native dispatch; do not label an unavailable native timestamp as measured.

Keep diagnostic persistence outside the critical pre-response path where possible and test its bound/failure containment. No raw receipt bodies, record values, actual source/run/device IDs or raw error payloads should enter shareable logs. Correct the misleading “make sure ... running” hint at this receipt-timeout boundary without changing failed-report or stop semantics.

### 3. Make a scheduling correction only if demonstrated

Do not install a new FIFO gate merely because two application calls can overlap. Establish the relevant native/library scheduling contract from already-retained exact source if available and/or a focused production-path test first. If a common gate is warranted, retain bounded queue residence and cancellation; moving the10s timer from submission to dispatch without a queue ceiling can silently widen total request lifetime even when the numeric constant stays unchanged. That is **not** authorized as an automatic fix.

Similarly, do not offload or reorder Watch ACK commits until a measured/reproduced response-critical defect and proof-equivalent adjustment are specified. Return the smallest named correction and regression. If the cause still cannot be localized, report the missing discriminator rather than proposing another speculative optimization/build cycle.

The follow-up remains source/test-only, with no APK build, install, launch, connection, recording, private export or current-device diagnostics. A passive information-launcher read is not necessary for this static investigation and is not currently authorized; it cannot recover telemetry the old source never recorded.

## Unchanged limits and evidence status

Ten-second per-operation policy, forty-five-minute operator horizon for the selected rung and180-second custody budget remain unchanged. Exact identity, receipt revision/hash/page rules, manifests, Watch-origin ACK/completion evidence, canonical custody, cutoff and no-retry rules are retained. No current Watch finalization/recording state is inferred from elapsed wall time or Phone failure.

Earlier extra Start/selection/scrolling observations remain historical and unexplained; they are not assigned as the cause. The initial Phone connection did succeed; the generic failure hint is not a reason to reopen Watch HUGR. Exact failed transaction, native dispatch state, Watch handler entry, reply result and historical root cause remain unresolved.

Prior Watch82/Phone62 five-minute physical PASS, admitted Watch84/Phone66 artifacts, synthetic duration results and all failed markers/STOPs remain intact and separate. Neither Phone ACK-write facts nor this source review establish completed whole-run custody or physiology.

## Source custody and preservation

Before and after read-only investigation, controlled Watch/Phone/control worktrees remained clean at the previously preserved heads:

| Owner | Head before documentation-only closeout | Root tree before closeout |
|---|---|---|
| Watch | `c9366d0cccf07bd3ae7b425ffd69e8f649e2cc88` | `b01828cac2e69ef95e05c7a7a06412b9c707d2b4` |
| Phone | `41450a84e5773904748670fb5bde71d5cb6e9928` | `154fb19a47750c4f3bd8ad6841487e50458dcaae` |
| Controls | `0fd1f273587bf5c89b5a26476a813edd93fdd421` | `31ecd793a0b40c7be526c328d7960ff002640e3e` |

Documentation-only closeout can advance these heads, not application source. Preserve the sanitized authority and this source-owned result, Master next, Alignment last; existing controlled both-repository write preflight, non-force branches and independent exact head/tree/changed-byte readback. No private VIS-COUNT or portal change. Video/images/actual real-run identities/recording-derived private details, APKs, provider correspondence, credentials and keystores remain off GitHub.

Detailed independent Phone and Watch review notes remain task-local supporting analyses. The controlling synthesis deliberately classifies unmeasured concurrency/blocking as exposure rather than confirmed repair evidence. One unrelated terminal-routing rejection was contained without using or changing the clinician portal; file-level source review completed.

T2.2, accepted adapter/RMSSD companion, October reconciliation, S4 and scientific selection/window ownership remain unchanged. Samsung native-sequence/final-batch and endpoint/drain work separate; no IDC, RMSSD/baseline/learning/Loop A/N1 action.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_FOCUSED_RECEIPT_TIMEOUT_SOURCE_INVESTIGATION_RESULT
