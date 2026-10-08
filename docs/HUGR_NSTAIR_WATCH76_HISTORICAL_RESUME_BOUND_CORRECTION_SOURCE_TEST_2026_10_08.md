# Captured resume high-water correction — source/tests only

**Authority:** Neil’s 2026-10-08 12:05:25 CEST instruction. Correct the captured failure, cover both replay entry points and downstream bounds, run joined Watch→Phone→Watch multi-manifest regression, preserve automatically. **No APK build, installation or device action.** Installed Watch76 and Phone59 remain unchanged. This receipt follows `HUGR_NSTAIR_WATCH76_CAPTURED_RESUME_HIGH_WATER_EXCEPTION_2026_10_08.md`.

## Diagnosis and correction

The captured `SourceJournalCorruptionException: Phone resume index exceeds watch journal` came from `prepareSourceReplay`, not an Android foreground-service kill. The erroneous guard compared the Phone cursor to the maximum currently retained segment endpoint. Exact ACK/deletion of a later segment can shrink that retained maximum after restart while earlier manifests remain unacknowledged.

Three quantities are now distinct:

| Quantity | Meaning | Permitted use |
|---|---|---|
| C: Phone durable cursor | Same-session Phone assertion of contiguous durable records | Skip replay of records already reported durable; never evidence of manifest verification or Watch ACK acceptance |
| R: retained replay endpoint | Maximum currently retained finalized endpoint | Bound actual data reads and exact queued-manifest ACKs |
| H: historically justified session bound | Existing Watch issuance metadata, strictly validated when C > R | Admit a plausible historical cursor; never recreate deleted records or authorize deletion |

`HistoricalSourceResumeBounds.kt` is a new external validator. `SourceJournal.kt` is preserved byte-for-byte. When C ≤ R, existing retained-bound admission is kept. When C > R, admission additionally requires:

- Eligible journal and an existing selected-session retained anchor.
- Existing `delivery_states.log` with newline-terminated, structurally valid rows, canonical UUIDs, valid state/index/time values and no numeric overflow.
- Selected-session singleton `BUFFERED` rows in exact append order covering 1..H, without gaps, duplicate issuance or ranges; H ≥ C.
- Exact retained filename/session/range/count/bytes/full SHA-256 and complete CRC-decoded, contiguous canonical records; anchors cannot overlap or exceed the ledger’s issuance bound.
- Negative, unsupported future, wrong-session-without-anchor and Long.MAX_VALUE cursors fail closed.

This ledger already exists: canonical append precedes a singleton `BUFFERED` event, and exact segment deletion does not delete that log. It is **issuance evidence only**, not a cryptographically tamper-evident ACK ledger, deleted-byte hash, historical Phone durability proof or independent custody verification. Missing/truncated/inconsistent metadata causes rejection, not reconstructed provenance. No physical ledger was read in this task; its adequacy on the actual Watch remains a runtime condition, not an observed fact.

Both `prepareSourceReplay` and `beginSourceReplaySession` call the shared validator. `PreparedSourceResumePlan` carries C, R and H separately. Queue release, post-ACK progress, finalized-manifest extension and empty-page backlog use actual retained-record counts instead of R−C. With C=8184 and R=8182, data backlog is zero but retained manifests still queue in ascending order. H is never substituted as a data/ACK endpoint.

Exact session/queued endpoint/full-hash validation, monotonic cursor, next-manifest enqueue after every accepted ACK, selected-session completion, disconnected lineage rejection and 15-minute deadline checks remain intact. Zero data backlog does not imply completion. The existing recovery-only service excludes sensor receiver registration and the health ticker, and suppresses `notifyDeviceHealth`; no new source data is admitted by this correction. The protected journal’s general append API is not redesigned or claimed globally sealed; recovery retains its existing execution-path exclusion of writers. In particular, historical H is not used to resume sampling in the same session: reconstructing per-stream sequences from this ledger would be unsupported.

## Validation

**Final validation completed successfully at 12:27 CEST.** Fresh repeatable run: `/home/ubuntu/HUGR_HIGHWATER_SOURCE_TEST_2026_10_08/joined_restart_final`. All five phases passed: current-cursor producer, historical producer, actual Phone ingestion/ACK encoder, Watch current verifier and historical verifier. The strengthened historical verifier restarted the synthetic journal after every accepted ACK, checked decreasing retained-manifest count, rejected wrong/repeated ACKs and reached no remaining manifests without changing the Phone cursor from 8184.

- Watch full JVM suite: **179 tests, 175 passed, 4 fixture/phase-gated skips, 0 failures/errors**. The four joined methods were separately exercised by the five-phase runner rather than counted as full-suite passes when no fixture is supplied.
- Phone full deterministic suite: **88 tests, 86 passed, 2 fixture-gated skips, 0 failures**. Both joined wire cases were separately exercised with the actual Watch-generated fixtures.
- Logs: `watch_full_final.log` and `phone_full_final.log` under the same source-test output directory. No APK assembly/package/version change.

Regression cases include missing/malformed/truncated/gapped/duplicated/ranged/foreign issuance, future/negative/overflow cursors, corrupted retained anchor, restart, same-session identity, stale MTU/CCCD lineage, disconnected release, deadline rejection, wrong/repeated endpoint/session/hash ACK and data-empty-but-manifests-pending.

The historical joined fixture creates real synthetic Watch records 1..8184, exactly ACKs/deletes a valid two-health-record segment 8183–8184 (212 canonical bytes), restarts with three retained manifests through 8182, and seeds Phone’s test store with the actual Watch canonical bytes. Phone’s production ingestion and protocol encoder verify each manifest’s full SHA/session/range/count/bytes/stream ranges and produce real 59-byte ACK frames. Watch’s production decoder, replay checks and journal validate each ACK and reduce pending manifests one at a time, including process-memory loss between ACKs. The current-cursor case also covers two manifest-only ACKs followed by final data persistence and ACK.

**Limits:** The Phone durable-store implementation in this joined test is an in-memory test double, not Android native SQLite. These are source/wire/JVM tests, not physical BLE callbacks, Android scheduling, 15-minute hardware lifetime or on-device canonical custody proof. Full repository Phone TypeScript is not newly asserted clean. The synthetic prior 8183–8184 acceptance is not evidence that the historical physical Watch accepted that earlier Phone UI ACK.

## Preservation scope

Production changes: `BleGattService.kt`, `SourceMtuReadinessGate.kt`, new `HistoricalSourceResumeBounds.kt`. Tests: historical bounds, captured index-8184 scenario, joined Watch producer/verifier, Phone joined regression. Repeatable runner: `scripts/hugr_integrated_wire_regression.py`.

Unchanged protected journal SHA-256: `24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361`.
Unchanged `SourceReplayWindow.kt`: `a4623e83af1f9253965e27eb745818a8071f598022f8d9b4d1b3287de3198856`.
Unchanged `SourceReplayProtocol.kt`: `f5548f6b509327660965e7862939022c5468a82cd32f5bef97c997b25c546550`.

Package/version, manifest, signing configuration, retained selector, Phone production/native ingestion and compatibility egress payload are unchanged. No private media or physiological stores enter GitHub. Source receipt → copies → Master → private VIS-COUNT impact recorded locally (known-denied access not retried; accepted S4 untouched) → Alignment last → consistency → bounded commits/non-force GitHub head and exact tree readback.

## Shortest next qualification route — review only

1. Separately authorize one same-package/signer greater-version **Watch** candidate build/admission of this tested correction; no Phone production change currently requires a new Phone APK. Do not retry installed Watch76 for this known failure.
2. Separately authorize normal in-place Watch update, Settings version confirmation and stop unopened; no uninstall or data clear.
3. Separately authorize one retained-delivery attempt: start filtered Fold capture before one normal Main HUGR Watch launch; only on current white `CURRENT RECOVERY ADVERTISING READY`, promptly open normal Honor Phone59 and press Connect once. Stop at first failure/terminal state or 15 minutes from service start. No relaunch, retry, sensing, new recording, egress, pairing/reset or legacy/frozen operation. If historical metadata rejects C, preserve the exact error; do not weaken admission or invent history.
4. Capture accepted ACK progression for every remaining selected-session manifest and Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` with actual source-session identity/time. Qualification additionally needs underlying Phone canonical full session/range/count/bytes/SHA and finalisation evidence; visible connection, truncated SHA, segment ACK alone or zero backlog are insufficient. Any further private-store evidence/export requires its own bounded authority.

**Status:** Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`. Legacy/frozen storage remains untouched.
