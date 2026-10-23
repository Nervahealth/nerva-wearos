# HUGR receipt/ACK timeout reproduction — partial result

**23 October 2026 — bounded source/test work only.**

## Decision

**The joined reproduction is NOT PASSED.** Seven Phone source-bound cases executed and passed. Five actual Watch callback response attempts succeeded in the final synthetic harness, but the single Watch JUnit test failed before reaching a ready source manifest for ACK acceptance. No production correction or physical retry is justified by this partial result alone. The historical Phone receipt-operation timeout remains unexplained at the Watch/native/radio boundary.

No application source, existing controlled tests, production class, APK, dependency version or deadline was changed. No network dependency download, signing, installation, device action, private storage access, recording or export occurred. Both admitted APK checksums were rechecked unchanged. Earlier physical and build failures remain preserved.

## Phone: seven executed source-bound cases passed

The harness transpiles the unchanged production `connectToDevice` module and its receipt transport, duration contract, ACK-evidence helper and source-failure classifier. A deterministic fake clock, fake BLE adapter and captured production callback closures replace native execution. Source hashes match the earlier reviewed Git blobs despite intervening documentation-only HEAD advances.

| Executed case | Result |
|---|---|
| Timely R1 write/read and existing completed-receipt C1 confirmation | PASS |
| R1 operation expires at exactly 10,000 fake milliseconds; one transaction-ID cancellation and connection cleanup | PASS |
| Receipt-read timeout followed by late fulfillment; no late C1, ACK success or terminal completion | PASS |
| Receipt-read timeout followed by late rejection; same containment | PASS |
| Selected ACK adapter rejection is wrapped as `SOURCE_ACK_WRITE_REJECTED`; evidence remains INTENT then sanitized ATT_WRITE_FAILURE / PHONE_ACK_WRITE_FAILED | PASS |
| Receipt and ACK submissions can overlap at the injected adapter seam while the fake queue holds one operation | PASS — injected scheduling only |
| Production receipt transport rejects a changed immutable page hash | PASS |

Executed output: `phone/prepared-continuation/execution-attempt-1.log`; launcher, exact command, source pin and review-to-current content-equality proof are retained beside it. Total launcher/test wall time was approximately **1.46 seconds**. The 10-second cases use virtual time, not measured device latency.

The task-local harness was tightened before its first execution to load the **exact production duration policy**, rather than substitute a mocked V2 receipt-size limit. Ten-second operation, closed durations, grace and receipt limits remain unchanged. The native parser/store/ingestor are test seams: this is not another canonical-custody or Android/BLE qualification.

## Watch: actual receipt callbacks reached; ACK chain incomplete

The initial Gradle test attempt stopped on unavailable offline `kotlin-stdlib-jdk8:1.8.20`; no callback ran. A direct JVM route reused captured precompiled production classes and cached test tooling instead. Its original ARM64 Conscrypt setup failure is retained.

The cached Robolectric 4.12.2 bytecode was inspected and its supported `robolectric.conscryptMode=OFF` option verified. Test-only JDK crypto allowed progress without a download, application crypto change or weakened SHA checks. The copied classpath was completed from the already-admitted runtime graph's existing local AAR/JAR artifacts. An unexercised legacy compile-only stub was included for class loading; no legacy behavior or SDK sensing coverage is claimed.

The original test then invoked R1 write successfully but received GATT_FAILURE on the read. Its setup had left MTU at the default 23, below the receipt framing requirement. This is a **test-setup failure**, not evidence that the historical devices failed to negotiate MTU. The original result is retained, and only a new task-local test copy was corrected to invoke the real MTU callback with 128.

The final copy uses callback-local bootstrap, not the full Samsung sensing/service-start lifecycle: it begins an isolated synthetic session, binds the real journal, then invokes actual production callbacks. Later test-only resume/setup changes are explicitly recorded in `test-only.diff`; production code is not substituted.

### Final observed callback timings

These are `System.nanoTime` deltas from the test observer's entry marker to its response-attempt marker, not radio round trips. The response observer returns true without native ATT transport. The small fixture is not a 30-minute workload or Honor performance measurement.

| Actual production callback invoked | Response attempt | Observed milliseconds |
|---|---|---:|
| R1:0 write | GATT_SUCCESS, observer true | 8.609 |
| R1:0 read | GATT_SUCCESS, observer true | 2.627 |
| Nonzero R1 write | GATT_SUCCESS, observer true | 0.127 |
| Nonzero R1 read | GATT_SUCCESS, observer true | 0.112 |
| Valid source-resume command | GATT_SUCCESS, observer true | 3.073 |

**Final overall Watch result: one JUnit test, one failure.** After source-resume command acceptance, the synthetic harness's five-second readiness wait expected queued manifest endpoint **2**, but it remained **null**. No valid source-ACK durability-before-response assertion was reached. Command acceptance does not establish successful asynchronous resume/apply, manifest queueing or delivery.

The following remain **NOT EXECUTED / NOT PROVED** in the completed Watch harness: exact ACK acceptance/durability-before-response, invalid ACK rejection, concurrent receipt/ACK callbacks, the deliberately injected 10.1-second session-lock delay, false `sendResponse` handling and null-server response omission. Assertions still present in test source are not executed evidence.

Final result, timestamped payload-free observations, compiler/JUnit command and retained setup failures are under `watch/test-harness-mtu-continuation/`. No naturally occurring ten-second callback delay was observed in the callbacks reached; that does not rule out real storage contention, native dispatch, provider or radio causes.

## Smallest next step

Complete **only the synthetic Watch harness's actual source-resume → asynchronous prepare/apply → queued-manifest readiness setup**, using the existing production test seams. Establish the exact selected synthetic session, ready endpoint and scheduling state before an ACK; do not manufacture a hash, remove a guard, widen a deadline or treat a success ATT response as delivery.

Then execute the still-missing ACK/overlap/delayed-response cases. Do not propose a production behavior correction, timer increase, blind FIFO, new APK or device retry until a demonstrated defect or named remaining hardware uncertainty supports it. This is a specific unfinished harness dependency, not a request to restart the substrate audit or restore unavailable historical recording proof.

## Evidence and preservation

`bounded-reproduction-evidence-index.json` binds the exercised Phone source, tests/commands/results and retained Watch failures with SHA-256 hashes. Test code and sanitized result evidence are eligible for the standing bounded source-owned closeout; captured runtime binaries, SDK/provider artifacts, copied application trees, APKs and any real recordings/images/video/identifiers are excluded from GitHub.

Preservation order: this source-owned receipt and bounded synthetic evidence, Master, Alignment last, bounded existing-branch commits, both-repository write preflight, non-force mirror and independent exact remote head/tree/changed-byte readback. The readback proof is stored in the Watch84 task folder as `receipt-ack-reproduction-partial-result-independent-remote-readback.json`; completion must be verified before delivery. No private VIS-COUNT or clinician portal update is included.

The physical thirty-minute gate remains **NOT PASSED**, and current Watch recording/finalization state is not inferred. The five-minute physical PASS, artifact admission and historical synthetic capacity evidence remain separate and unchanged. T2.2, the accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, learning and Loop A boundaries are intact. Samsung native-sequence/final-batch and endpoint/drain work remain separate. No IDC or N1 action.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_BOUNDED_RECEIPT_ACK_TIMEOUT_PARTIAL_RESULT
