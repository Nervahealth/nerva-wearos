# Phone61 durable report — acceptance trace and indispensable Watch dependency

**2026-10-10. Source/test work in progress. No APK build, installation, device operation, private-data transfer or N1 execution.** Installed Honor Phone60 and Watch77 remain unchanged. User authority at 17:36 permits the minimal Phone report and production-chain tests, then one combined Phone61 build only if the required acceptance evidence can actually be supplied. A necessary Watch change must be presented before building an incomplete Phone-only solution.

## Concrete build decision

**Hold the Phone61 APK build.** Two source-established dependencies prevent the requested fresh complete-lifecycle acceptance with unchanged Watch77:

1. **Watch77 is intentionally recovery-only.** `MainActivity.kt:180–205` selects a retained finalized v2 target; when none exists it stops before permission/sensor start. The fresh-scope/start methods below remain in source, but no ordinary launcher branch calls them. A Phone update cannot make this Watch launcher start a fresh isolated recording. Do not invoke its Health service by an alternate shell/hidden component as a workaround.
2. **Final completion is local, not a Phone-bound receipt.** `BleGattService.kt:1870–1939` validates exact session/queued endpoint/full segment hash, invokes the existing journal acknowledgement, then may record `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, close the journal and request GATT stop. The ordinary Phone protocol has no final-run lifecycle receipt binding finalization, the final manifest set and terminal completion to the actual source session. A disconnect, caught-up index, segment manifest, Phone write response, or historical local marker must not be promoted to that missing receipt.

Finish the authorized Phone source/tests with unsupported fields **UNKNOWN**, then request the bounded Watch dependency. Do not generate a diagnostic-only APK or require optional historical Watch69 metadata readback first.

## Acceptance-field trace

| Required field | Evidence available from current source | What the Phone report may claim | Remaining dependency |
|---|---|---|---|
| Actual recording/source identity | Canonical record UUID, exact manifest UUID, native accepted file and selected SQLite rows | Exact actual source session; no historical bounded UUID substitution | Fresh Watch launch must create an explicitly new scoped recording/session |
| Records, streams and timestamps | Native production append + full verifier; canonical source index/stream/sequence/timestamp; Phone receipt time/lineage | Verified stored record counts and available timestamps, not beat count or guaranteed duration; absent streams explicit | Watch lifecycle receipt for run start/stop clocks, requested duration and final stream scope |
| Controlled finalization | Watch `HealthSensorService.kt:196–233`: stop admission, quiesce producers, lifecycle lock, force-sync/finalize/force-sync, then local marker/broadcast | UNKNOWN on Phone; receiving a sealed segment proves sealing, not final run stop | Session-bound finalization receipt containing immutable final manifest inventory/extent |
| Verified Phone canonical custody | Native `PhoneSourceJournal.verifyManifest`, exact canonical record bytes/hash and ranges; guarded missing/short custody | Exact verified segment tuple; durable report before ACK eligibility; fresh offline recomputation after restart | Physical confirmation still required |
| ACK sent/result | Exact encoded 59-byte ACK and connection-lineage-bound `writeCharacteristicWithResponseForService` | Durable Phone INTENT and ATT response success/failure only | No blanket Watch acceptance/completion claim from a Phone event |
| Exact Watch ACK accepted | Watch handler invokes exact journal check before its normal ATT success response; errors become GATT failure | A delivered response supports that specific handler-returned write; persist only ATT response fact in this package | Explicit Watch-originated accepted ACK tuple, durable and replayable if final response/connection is lost |
| Watch final completion | Watch local `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` and recovery exact-completion diagnostic; normal fresh path marker uses startup run ID | UNKNOWN on Phone; no completion inferred from index equality or disconnect | Watch receipt binds actual source session, all final manifests and accepted exact ACKs before shutdown |
| Gaps/failure/unknowns | Native source gaps, current verifier/readback failures, sanitized Phone write result | Preserve detected gaps/fixed failure codes; unknowns remain unknown | Watch acquisition/finalization failures must also travel in the bounded receipt |
| Offline read after restart | Passive native Activity; copied selected canonical files/SQLite envelope; before/after fingerprint checks, scratch-only writes | Reopen integrity-checked run report, select only its session, recheck exact canonical custody and report-manifest tuples | Physical restart/read gate after later candidate admission/installation |

Neither these source paths nor Android/Robolectric tests prove actual radio delivery, Honor custody, sensor physiology or power-loss durability on hardware.

## Smallest Watch dependency to authorize — proposal only

One bounded **fresh-run admission and durable lifecycle-receipt bridge**, not a general telemetry or observer project:

- Add an explicit fresh-short-run action using a newly isolated per-run scope/session. Normal launch remains passive/recovery-only; no automatic sensing. Preserve v1, existing v2/Watch69, legacy and frozen roots; no migration, deletion, re-encoding or historical proof reconstruction. Source-session identity is generated by the actual selected new journal, not invented from an old bounded UUID. Keep protected `SourceJournal.kt` unchanged unless a concrete indispensable dependency is separately presented.
- Reuse the tested five-minute acquisition gate, quiescence/finalization and ordinary canonical manifest/ACK route. Audit and test the fresh delivery service lifetime so transport can survive finalization without admitting additional samples. No new sensors, estimator, haptics, egress or scientific claim.
- Persist one small versioned checksummed Watch-originated receipt outside canonical roots: actual source session; available run monotonic/wall timing with clock provenance; start and controlled-finalization outcome; immutable final manifest set/range/count/bytes/full hashes; exact accepted ACK tuples; terminal completion/failure. Unknown or interrupted receipt publication never becomes completed. No backfill of Watch69 lifecycle proof.
- Phone receives that receipt on a separate ordinary lifecycle read/notification path, never an egress or physiological record path. Bind receipt revision/digest/session to the verified final inventory and exact ACK evidence. Duplicate/stale/foreign/corrupt receipt handling fails closed. Do not add source records after finalization merely to transport status.
- Preserve terminal receipt before shutdown. Provide a bounded, explicitly authorized receipt-only read after restart/lost final notification, with no sensing/recording or legacy access. Do not depend on a race between final ACK and `stopSelf`; delivery lifetime and final receipt readback must be tested together. Ordinary receipt persistence/read completion must not weaken the exact canonical ACK deletion rule.
- Join tests across new Watch receipt generation/ACK acceptance/finalization and actual native Phone writer→verifier→report→restart→offline-reader. Phone write success alone remains insufficient.

**No Watch code change or Watch build is authorized by the current Phone-only instruction.** Present this dependency before any APK build.

## Prepared short-run gate — not executable with Watch77

After a separately admitted compatible Watch candidate and Phone61, and separately approved installation/run gates:

1. Select one explicit fresh five-minute run with the Watch worn normally. Confirm start/identity once; no keep-awake toggle or routine Watch filming.
2. Use one ordinary Phone connection. Let automatic controlled finalization and existing exact delivery finish within a bounded, separately frozen observation window; stop at first failure/deadline without retry.
3. Read one durable Phone report. Acceptance requires actual canonical records, Watch-originated controlled finalization and exact accepted ACK/completion for the same final inventory, Phone full canonical equality, and no unresolved acceptance gaps. A partial result stays partial.
4. Close/restart Main Phone, then open the separate passive information-icon reader offline once. It must reopen the same immutable report identity and recompute current canonical bytes/full hashes. No reconnect or new sensing during readback.

Exact buttons, candidate versions and deadline must be frozen from the admitted pair. This is a design gate, **not permission to run now**.

## Route and preserved milestones

Watch69 remains **NOT QUALIFIED**; optional historical metadata is not a prerequisite for the fresh rehearsal. The next useful step is the minimum receipt/admission bridge, not another reconnect or unavailable-proof reconstruction loop.

- **Dependable recording instrument:** record → controlled finalization → exact verified delivery → durable Phone report → offline recheck after restart, with no routine Watch filming.
- **Live Watchtower observation:** preserved subsequent milestone; not implemented or required to qualify this short acceptance. No observer stack/server work is in this package.

Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## Authorized Phone source/test completion — 2026-10-10

The minimal Phone side is implemented, without a candidate build:

- Native exact `verifyManifest` publishes and integrity-reads a checksummed immutable per-run custody report **before** returning true or setting the historical SQLite verified flag. Report publication failure prevents that verifier call from returning ACK eligibility. Previously stored flags do not manufacture report evidence.
- Report storage is app-private `noBackupFilesDir/hugr_phone_run_reports_v1`, not a canonical journal, share/export or network path. Available canonical full-file identity, counts/ranges, stream/source/Phone receipt timestamps and lineage counts, exact manifests, gaps and Phone ACK facts are retained. Acquisition/finalization/Watch acceptance/completion remain explicitly UNKNOWN.
- Phone BLE ACK path preserves existing exact manifest verification/export-before-ACK and current connection-lineage checks. It now publishes durable INTENT, rechecks lineage immediately before the actual 59-byte write, then retains only observed ATT response success or sanitized failure. A failed result-report publication is not mislabeled a radio rejection. A Phone response is not terminal run completion.
- Passive information-icon reader selects only the latest integrity-checked app-owned report, recomputes the selected canonical bytes/manifests/ranges/gaps on disposable copied SQLite/files, matches the report full custody and each verified manifest tuple, checks source before/after fingerprints and rechecks report identity against concurrent publication. It makes no report/source repair or writes outside its disposable cache.
- Missing report is STOP without automatic historical canonical access. Missing/corrupt bytes, partial custody, stale report contents, invalid/truncated report, interrupted publication or unsafe paths are STOP, not reconstructed proof. Only an explicit separately governed route could inspect optional historical metadata.
- Publication uses temporary-file fsync, atomic rename, directory fsync and exact readback. A durable pending-publication gate spans report plus selection-index publication, including a newly selected different session. Interruption stays fail-closed; no silent previous-session fallback or automatic repair. Repeated same tuple/lineage/phase preserves its first retained observation. Bounds are 512 revisions per source session, 4096 global index revisions, 256 ACK facts and 128 KiB per report; limits stop, never evict canonical bytes or silently discard proof.

### Concrete pre-admission corrections

Final tests/review exposed and corrected the report-index envelope/body parse error; an unsupported Android `O_DIRECTORY` public constant; revision/ACK budget mismatch; before-allocation report length check; report-directory ancestor safety; absent-report historical fallback; and unindexed cross-session publication fallback. The implementation now opens a directory with public `Os.open(O_RDONLY)`, verifies `S_ISDIR(fstat)`, then fsyncs it. No APK was produced from the pre-correction source.

Robolectric's default Linux shadow cannot open directories and its default rename does not perform the needed filesystem move. **Test-only** `ReportDirectoryLinuxShadow` uses actual disposable Linux directory `FileChannel.force(true)` and `Files.move(ATOMIC_MOVE)`. Production Android durability remains mandatory and unchanged by this adapter. Tests are not Android hardware fsync/power-loss proof. Exact production storage method bodies and report-store bytes are compiled in the synthetic harness; only Expo module-registration/parameter scaffolding is omitted and separately checked. Harness duplicate-source exclusion was repaired by renaming the generated report source file, with unchanged content hashes.

### Applicable validation

- Full native production custody/report/offline suite: **64 tests, 64 pass, 0 failures/errors/skips**. Includes real production-created storage, new actual source UUID, per-segment writer restart, exact manifest verification, Phone ACK intent/result, report reopen, missing/corrupt custody, selected-only access, corrupt report, interrupted same/different-session publication, write/rename/directory-sync failures, size/identity/path rejection, and production verifier publication failure leaving canonical bytes intact and stored verified flag false.
- Full Phone deterministic suite: **119 total, 117 pass, 2 existing fixture-dependent skips, 0 failures**. Includes seven ACK-wrapper cases: intent before write, failed intent prevents write, sanitized rejection, success-report failure not radio failure, double-failure causes, disconnect during intent, and exact production wrapper ordering.
- `git diff --check` passes. Watch77 application source is byte-unchanged against admitted baseline `93f32e57e7ddf6a25774470da196ff36306ae6de`.
- The existing five-phase joined Watch→Phone→Watch synthetic regression is separately rerun. Its final result belongs in the final validation addendum below; Phone persistence there remains a deterministic model, not Android native/radio proof.

This closes the **Phone-only source/test preparation**, not complete-lifecycle acceptance and not artifact admission. No Phone61 build, signing submission, installation, device launch/run/reconnect, private-data upload or N1 execution occurred. The current installed pair stays Watch77/Honor Phone60.

### Next action — exact bounded dependency

Authorize the minimal **Watch fresh-start + durable session-bound lifecycle receipt bridge**, paired Phone receipt integration and joined source/tests, with no APK build or device action until the joined acceptance evidence passes. This permits one explicit fresh isolated five-minute run route while preserving Watch69 unqualified and all existing roots, and exposes actual Watch finalization/accepted exact ACK/completion evidence through a bounded repeatable ordinary GATT receipt read. It is not an observer, telemetry expansion, new sensor/algorithm, egress or historical proof reconstruction project.

After that source/test closure, the already-requested Phone61 candidate must be built against its compatible separately authorized signer-continuous Watch candidate; do not build the knowingly incomplete Phone-only artifact now. Device installation and one fresh run remain separate gates.

## Final source/test result — 2026-10-10 18:00 CEST

**PHONE SOURCE/TEST COMPLETE / PHONE61 APK BUILD HELD AT EXPLICIT WATCH DEPENDENCY.** The five-phase joined synthetic regression completed successfully: Watch produce, historical produce, Phone ingest, Watch verify, historical verify. Both ordinary multi-manifest advancement and historical C=8184/R=8182 manifest-only exact ACK/restart cases pass without new records. This existing joined harness still uses a deterministic Phone persistence model; the separate native 64/64 suite supplies actual production Phone filesystem/SQLite/report-method testing on synthetic Android/Linux storage. They are complementary, not a claim that the new lifecycle receipt is implemented or that hardware was validated.

The focused report review's three remaining P1 findings were corrected and directly regression-tested: absent report now stops without canonical historical access; a durable pending-publication gate prevents prior-session fallback after an interrupted different-session report/index publication; and the `reports` ancestor is validated before session report access. The final strengthened native suite is **64/64 pass**; Phone Node suite is **117 pass / 2 existing fixture-dependent skips / 0 failures**. No unresolved test failure remains in this source closure. The independent review file is a historical review snapshot; these final dispositions are parent-reviewed source corrections backed by the named regressions, not a claim of a separate final auditor recheck.

All acceptance traces remain honest: a successful exact source-control ATT response is implementation-specific evidence that its synchronous Watch handler accepted that ACK tuple, but it does not prove terminal finalization, the final manifest inventory, final completion or recoverable receipt after a lost response/restart. This Phone package conservatively stores the observed ATT fact only. It must not be admitted as a complete recording instrument without the Watch fresh-start/lifecycle-receipt bridge.

Final documentation ordering and bounded non-force remote preservation are completed only after exact GitHub branch-head/tree readback. Remote identities are recorded in the external final preservation readback, avoiding self-referential commit identifiers in this source-owned receipt.
