# HUGR bounded-paging eight-hour terminal result — 22 October 2026

## Decision

**PASS: affected retained synthetic terminal → durable completion → restart/offline recheck → private export → independent reader.** This repairs the demonstrated software terminal-verifier boundary without changing the 180,000 ms production deadline. It is not Honor hardware performance, a physical eight-hour recording, native IBI continuity, physiological validity, RMSSD admission or N1 qualification.

The candidate-build authority remains conditional on the full existing release evidence and source-bound staging/admission checks. No build, signing, installation or physical recording was performed to obtain this result. The current final release-evidence review is separate from the earned terminal PASS.

## Measured work and correction

The pre-paging corrected forward-only path still spent most time evaluating projected metadata and performing repeated SQLite ordering/refill work. Exact selected-session query plans showed an existing record-identity access path and stream ordering, but a temporary B-tree for file-offset ordering; there is no new index, schema change or migration.

The selected-record provider now uses fixed **16,384-row keyset pages**, selecting only keys before projecting the original bounded metadata/lineage. Strict total orders are record index; file offset plus record index; stream/source sequence plus record index. Parsed integer continuation values retain numeric SQL semantics on both typed production columns and untyped adversarial fixtures. No OFFSET, rowid pagination, coercive CAST/COALESCE, row/page List, ignored corruption or relaxed proof is introduced.

Each page owns the same fixed **8 MiB**, forward-only CursorWindow. One absolute terminal deadline is passed into every page. The active cursor validates types, identity cardinality, strict progression and total expected count, closes pages on exhaustion/error/cancellation, and advances its token only after successful row validation. All core canonical-byte/hash, manifest, file-coverage, sequence/gap, report-store, failure-latch and budget source bodies remain byte-identical. Original V1 recordings are not rewritten.

## Before/after timings

Native x86 measurements on the same retained synthetic writer evidence; instrumentation is not an Android hardware attestation.

| Stage | Before paging: corrected forward-only path | After paging: one cumulative core pass |
|---|---:|---:|
| Initial SHA/metadata | See retained measurement artifact | 0.838 s |
| Manifest plans | See retained measurement artifact | 0.021 s |
| Record index / canonical bytes / manifests | 41.988 s, completed cumulative stage | 17.396 s, all 967,830 rows |
| File-offset coverage | 109.105 s, completed cumulative stage | 95.273 s, all 967,830 rows |
| Stream sequence / gaps | Cumulative deadline stopped at 717,334 rows; separate isolated pass took 51.677 s | 22.129 s, all 967,830 rows |
| Final file SHA/size | Unreached in failed cumulative attempt | 0.329 s |
| Complete cumulative core | Failed under the existing 180 s deadline | **135.997 s, COMPLETE** |

The stream's isolated pre-paging time is not a completed cumulative stage and must not be added to the failed run as though it completed. The remaining measured dominant stage is file-offset ordering/coverage; it now passes. No further optimization is proposed to obtain this PASS.

Source fingerprint/copy measurement was 5.621 s. The independent reader took 44.522 s. The native terminal/report/restart/offline/export phase took 819.461 s including multiple separately bounded verifier calls and Gradle/JUnit overhead; this is **not** one verification's duration and does not mean the 180 s deadline was enlarged. Process RSS includes the JVM/Gradle/native test environment, not just provider memory; sampled summed peak was approximately 2.17 GB in the measurement phase. Provider state and each window remain fixed-size.

## Executed compatibility and terminal evidence

- **14 native tests, zero failures/errors/skips:** eight keyset tests; two API-28 20,000-row/window tests; generic-cursor preservation; three original metadata-projection tests. Cover page sizes 1/7/16,384; real page crossings; foreign sessions; file-offset and stream/sequence ties; corrupt/null/type metadata; duplicate identity; stale count/EOF; strict continuations; read-only/schema preservation; absolute deadline and page closure.
- **One retained per-stage/query-plan measurement test passed.** Saved first/continued page plans describe the actual query helper. No ingestion replay occurred.
- **One retained-writer fresh-envelope terminal/report/restart/offline/export test passed**, with pre-assertion `failureReason=NONE`, `instrumentResult=COMPLETE_VERIFIED`, and `custodyStatus=VERIFIED`.
- Independent package verification passed using external source/run, original writer byte-count/SHA and exact manifest/ACK anchors: **967,830 canonical records, 78,936,626 bytes, 478 exact manifests/accepted ACKs**. Every package member retains the existing eight-member export format; original ZIP was unchanged by independent reading.
- The production-cursor API-27 branch was not rerun because that pinned runtime was not transferred. This omission is explicit; native API-28 and unchanged generic behavior were executed. Do not turn it into a claimed skip-free all-Android suite.

The original synthetic failed-state files were all checksum-read-back unchanged. The failed terminal receipt's failure latch was not reset or relabelled PASS. Completion uses an explicitly fresh disposable synthetic report envelope over the exact retained writer bytes and prior exact Watch-origin ACK evidence. This is source-bound **compositional** proof, not another Watch recording or an independently repeated hardware execution.

## Scope and preservation

Retain all earlier failed attempts and measured source snapshots. The already-passing lower-rung Watch writer/live decoding/ACK/report/export evidence remains history and is not routinely regenerated. Final candidate release must explicitly bind any reuse to unchanged source and current provider-equivalence evidence; it must not silently claim every lower rung ran again on the paged source.

No real recording or physiological findings, provider correspondence, credentials, keystore or IDC material belongs in GitHub. Only source, synthetic tests and sanitized result/control documents are eligible. No Samsung semantics or endpoint/cutoff/drain change, extra receipt export, acquisition change, analytical window policy, RMSSD, baseline update, learning, Loop A or N1 is authorized here. T2.2, accepted adapter/companion, October reconciliation and S4 remain unchanged.

Historical literals remain: Watch69 `NOT QUALIFIED`; Watch68 v1 `UNCLASSIFIED / NOT VERIFIED`; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

END_OF_BOUNDED_PAGING_EIGHT_HOUR_TERMINAL_PASS
