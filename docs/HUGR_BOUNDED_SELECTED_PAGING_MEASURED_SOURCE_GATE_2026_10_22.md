# Bounded selected-record paging — measured source gate

User authority: 2026-10-22 21:53:29 Europe/Oslo measurement-first terminal-verifier source/test correction. Prior failed attempts and conditional Watch84/Phone66 admission gates remain unchanged.

## Corrected forward-only path, before paging

Native-x86 retained source-bound eight-hour synthetic evidence: 967830 rows, 78936626 canonical bytes, 478 exact manifests and Watch ACKs. No ingestion or lower-rung repeat. Existing retained-state test completed; this is diagnostic measurement, not terminal admission.

| Stage | Elapsed | Cursor-next work | Original cumulative disposition |
|---|---:|---:|---|
| Initial hash/metadata | 0.836 s | none | Complete |
| Manifest plans | 0.024 s | none | Complete |
| Record index/manifests | 41.988 s | 39.573 s | Complete |
| File-offset coverage | 109.105 s | 107.286 s | Complete |
| Stream sequence/gaps | 51.677 s isolated | 50.417 s | Deadline at 717334 rows after 181.684 s cumulative |
| Final SHA/size | 0.366 s isolated | none | Not reached cumulatively |

Initial disposable-copy fingerprint/copy cost 6.397 s. The query plans use primary-key index for record order, stream index plus right-part temporary sort for stream order, and stream-index selected scan plus temporary ORDER BY B-tree for file offsets. No file-offset index exists. Native/JVM measurement is not Honor performance. Isolated clocks do not enlarge production acceptance.

## Narrow correction, validation pending

Strict bounded keyset pages on the same immutable OPEN_READONLY copied SQLite snapshot. Select ordering keys first; apply unchanged type/null/hash/lineage projection only to page members. Explicit record_index tie-breaker for duplicate file offsets and stream sequence keys. All three core passes, byte/hash/CRC/header verification, manifest membership, sequence/gaps, fixed cursor memory and the original one absolute 180-second deadline remain. No new source index/schema, OFFSET, rowid identity, materialized all-record list or database mutation. Page monotonicity/count checks are fail-closed. Exact implementation and adversarial page-equivalence tests are preserved with this pending gate; no native pass or speed-up is claimed yet.

Affected retained-writer path must earn terminal custody, durable completion, restart/offline recheck, exact selected private export and independent reader before conditional builds. Original failure latch and retained report history cannot be reset; test uses a fresh disposable synthetic envelope over exact retained writer bytes. No cutoff/drain, Samsung semantics, IDC, real recording/private data, device, installation, collection, scientific policy, RMSSD/baseline/learning/Loop A/N1 changes. T2.2 accepted adapter/companion October S4 unchanged. Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

END_OF_BOUNDED_SELECTED_PAGING_MEASURED_SOURCE_GATE
