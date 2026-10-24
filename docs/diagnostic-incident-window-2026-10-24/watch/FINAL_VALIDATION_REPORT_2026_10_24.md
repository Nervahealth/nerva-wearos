# Watch diagnostic incident-window final validation — task-local

## Final result

**PASS.** The prescribed task-local direct offline runner completed with production compiler exit `0`, test compiler exit `0`, and JUnit `OK (18 tests)` in `15,808.466 ms`. It invoked no Gradle, used no network, produced no APK, and performed no device, Bluetooth, radio, retained-data, or native-capture action.

The controlled Watch worktree at `/home/ubuntu/HUGR_PHONE_EXPORT_SCRATCH_CORRECTION_2026_10_20/watch` remained clean. The task-local final six-target integration set and hashes are in `PARENT_INTEGRATION_MANIFEST_2026_10_24.json`.

## Requirement closure

- **Timely TRUE is durably retained.** After the original `sendResponse` attempt returns, the runtime records the result in memory and queues one bounded worker checkpoint even while the incident window is open. The actual callback contract proves an independently constructed `SavedDiagnosticStore` can reopen a durable timely-`TRUE` report before any false/null/late outcome or disconnect.
- **Bounded coalescing, not per-poll files.** Checkpoints use one Watch-local diagnostic epoch and one `.incident.txt` filename. The single worker accepts only one queued task; later callback settlements update that same file with the latest bounded window. No disk I/O occurs on the ATT callback path before or during the original response attempt.
- **First failure/disconnect is latched; late settlement updates the same file.** First decisive false/null/late and pending disconnect/cancellation retain their frozen context. A disconnect after only settled timely-success rows now freezes `DISCONNECTED_AFTER_SETTLED_OPERATION`, preserves the settled `TRUE` outcome/state unchanged, publishes the available row, and has an explicit save/reopen contract.
- **Crash and publication truthfulness remain explicit.** Pre-checkpoint crash loss remains `UNKNOWN_NOT_EVIDENCE_OF_CALLBACK_NON_ARRIVAL`. `WRITE_FAILED` is now both a publication result and visible publication state; it increments explicit checkpoint loss rather than leaving an old acknowledgement state visible.
- **No stranded queue/no infinite retry.** Worker exceptions and executor-submission rejection record an explicit bounded `WRITE_FAILED` loss and clear the queued flag. Successful concurrent changes schedule at most one later coalesced checkpoint; failed/capacity results do not self-retry.
- **Durable acknowledgement is real.** `SavedDiagnosticStore.saveIncident` now file-fsyncs the temporary file, renames it, forces the parent directory, then freshly rereads the final file and verifies the exact body and SHA-256 trailer before returning either `ACKNOWLEDGED_ATOMIC` result. Directory-sync and exact-reopen failures return `WRITE_FAILED`, never acknowledgement.
- **Shared-root concurrency is serialized.** All store instances for the same diagnostic-root path use a shared lock for generic save, incident save, enumeration, and read. The focused competing-instance contract verifies the eight-report cap, generic replacement compatibility, and incident-only `CAPACITY_EXHAUSTED` outcome without silent incident rotation.

## Focused contract coverage

The exact runner’s 18 tests include the pre-existing model/overflow/budget/ordering/viewer/source-ACK contracts plus the focused additions required by this closure:

1. Timely `TRUE` actual production callback persistence and separate-store reopening before false/null/late/disconnect.
2. Disconnect after only successful settled rows freezes and preserves the row outcome; the available-window report saves and reopens.
3. Multi-instance shared diagnostic-root contention cannot exceed the eight-report capacity or rotate an incident silently.
4. Actual success path executes directory `FileChannel.force(true)` and exact final-file reopen/trailer verification before ACK; injected directory-sync and reopen failures are not acknowledged.
5. `WRITE_FAILED` is rendered as the publication state with explicit loss accounting.

The test source also retains the source-bound assertion that `gattServer?.sendResponse` precedes `settleIncidentOperationAfterResponse`, and that the wrapper contains no direct store/file write. No deadline, transport, source/ACK safeguard, ATT ordering, request/poll, sensor, acquisition, or connection/retry policy changed.

## Evidence boundaries

This is source-level, synthetic direct-JVM/Robolectric evidence only. It demonstrates bounded local persistence semantics and the actual production callback-to-post-response seam in the test harness. It does **not** demonstrate native dispatch-to-callback mapping, radio behavior, Phone receipt/delivery/acceptance, cross-device correlation, or a physical Watch outcome. `nativeRequestIdScope=WATCH_LOCAL_ONLY_NOT_CROSS_DEVICE_KEY` and `CORRELATION_UNRESOLVED` remain deliberate. Timely platform `TRUE` remains platform response acceptance only, not delivery proof.

## Preserved evidence

Prior test outputs were preserved before correction under `pre-requirement-correction-snapshot-2026-10-24T1001CEST/` and before the last two hardening steps under `passed-attempt-7-before-worker-submission-hardening/` and `passed-attempt-8-before-disconnect-success-reopen-test/`. Existing earlier preserved attempts remain untouched.

See `PARENT_INTEGRATION_MANIFEST_2026_10_24.json` for final source/test hashes, final runner evidence hashes, and all six parent integration targets.
