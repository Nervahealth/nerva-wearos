# Watch diagnostic incident-window final validation — task-local

## Result

The task-local offline closure passes: production compiler `0`, test compiler `0`, JUnit `OK (15 tests)` (`16,271.195 ms` harness measurement). The runner used no Gradle or network, made no APK, and did not interact with a device, radio, retained data, or native capture.

## Completed narrow alignment

- A pending row at local disconnect or cancellation now changes lifecycle state **and latches the first frozen incident context**, making the already-present runtime checkpoint call eligible. The frozen first reason and connection epoch are never overwritten.
- Rows carry their actual local connection epoch at entry. A later reconnect receives a new epoch, while prior rows and frozen context retain their original local lineage.
- Late settlement after disconnect/cancellation is retained with lifecycle provenance. A settled callback outcome is immutable; duplicate/later callback attempts cannot rewrite a failed/null/timely/late result.
- `beginDiagnosticEpoch()` now refuses all frozen incidents, including overflow-only incidents.
- The renderer uses compact fixed scalar row codes. It explicitly says that late `TRUE` is Android platform response acceptance, **not Phone delivery or Phone acceptance**. It retains the native-local request-ID and `CORRELATION_UNRESOLVED` boundaries.
- The raw byte truncation path is replaced by complete-row budget handling. Normal legal 64-row worst-case values, including `Int` extrema, `Long` extrema, and full UUID connection epochs, are asserted `COMPLETE` within 24 KiB. If a future format expansion cannot fit, only whole tail rows may be omitted and the report says `traceCompleteness=INCOMPLETE_TRACE_WINDOW_BUDGET` with `traceOmittedRows` and `traceByteLoss`; it cannot silently claim completeness.

## Scope and ordering retained

The current task-local `BleGattService` invokes the diagnostic settle/checkpoint seam only after the original `sendResponse` attempt returns. The existing runtime still queues persistence to its worker; no disk I/O was introduced before ATT, and no deadline, source/ACK, protocol, poll, acquisition, connection/retry, or receipt-completion behavior was changed.

## Tested contracts

The 15 tests include timely `TRUE`; false/null/late outcomes; first-decisive preservation; disconnect/cancellation pending lifecycle freezes plus late settlement; immutable failed results; no reset of lifecycle or overflow frozen incident; 64-row maximum/overflow; full UUID row lineage and 24 KiB complete report; eight-report atomic incident capacity/replacement; payload redaction/local-ID scope; actual callback response ordering; source ACK guards; and offline-only saved-diagnostics reader isolation.

## Limits

This is source-level, synthetic direct-JVM/Robolectric evidence only. It does not demonstrate native dispatch-to-callback mapping, GATT/radio behavior, Phone receipt/delivery, cross-device correlation, historical 10-second cause, or any physical Watch result. `CORRELATION_UNRESOLVED` remains deliberate. The injected >10-second monitor-lock case remains a test fault injection, not a measured device/Phone cause.

See `PARENT_INTEGRATION_MANIFEST_2026_10_24.json` for exact target-relative integration files and hashes; it distinctly labels the three continuation changes and three recompiled, unedited existing dependencies.
