# Phone CARDIAC V2 ordered-anchor correction gate — 22 October 2026

**Disposition: source correction implemented; affected native verification pending; no Watch84/Phone66 build or admission.** Authority is the existing bounded coherent-candidate instruction of 22 October 2026. No new architecture, device, recording, private-data access or receipt-export work is included.

## Earned evidence retained

The exact original compile-only prerequisite was recovered and whole-Android Watch source compilation passed. The executed Watch regression set passed 95 tests. The five-minute synthetic V2 Watch writer, live Phone ingestion and four exact Watch-accepted ACKs passed with 9,433 canonical records. Relevant broader Phone native coverage is an explicit 177-unique-test aggregate: 176 passes in the original emulated run, plus only its unchanged failed pure-JVM two-million-record test rerun on native ARM in 9.752 seconds. The original failure occurred during fixture generation, before verifier invocation. Both 180-second deadlines remained unchanged. This is not a claimed full-suite rerun and is not Honor performance.

The forward-only five-minute native writer/report/restart/offline/export path subsequently stopped after 1,335.106 seconds at `EXPORT_DURATION_OR_CANCELLATION_BUDGET_EXCEEDED`. Its retained exception locates the new CARDIAC V2 second-pass `DiskGroupStore.contains` lookup, which queried SQLite once for every canonical record. The original 180,000 ms processing budget was enforced. This is a synthetic execution deadline result, not corruption or a physical-device result. Larger V2 rungs have not passed; the existing earlier V1-duration results remain distinct.

## Focused correction

Only the Phone V2 verifier, its focused native tests and the source-owned V2 contract change. One ordered private-index anchor cursor now merges with the fresh record-index-ordered canonical scan. The scan still parses and checks every canonical row, including non-CARDIAC rows and rows after the final anchor. Only actual anchors require bounded group load/bind/finish work. All-group accounting remains exact. There is no per-record existence query, all-group List/Set or source-store write.

The correction preserves one active fixed-bitmap group, original byte/identity/slot/status/length/anomaly/batch/saturation checks, missing/opaque/wrong-kind-anchor refusal, duplicate/incomplete-group refusal, record-order and deadline checks, cleanup and historical V1 readability. Native IBI adjacency remains unestablished. No payload grammar, capacity, production processing deadline, acquisition cutoff, Watch runtime, signer or version changes.

Focused synthetic tests cover physical file-order reversal versus record-index order, leading non-CARDIAC rows, shared parent timestamps, a fixed two-group set, one ordered anchor cursor, exact former-versus-new anchor membership, missing source index, opaque/non-HR anchors, invalid order and expired deadlines. Native execution is still pending; static/source checks alone do not qualify this correction.

## Next gate and preservation

Preserve this source-owner receipt, Master entry and Alignment last; use bounded coherent commits, both-repository write preflight, non-force mirror and independent exact branch/tree/changed-blob readback. Then create a fresh Phone source snapshot, run affected V2 native tests and the stopped joined export stage, and continue only unreached duration stages. Keep prior source hashes, snapshots, failed attempts and all passes unchanged as history. No build until the required four-rung native and independent-reader gates pass.

Private recordings, physiological findings, private endpoint/provider material and export ZIPs remain excluded from GitHub. The terminal SDK evidence did not justify safe post-cutoff late admission; the hard acquisition cutoff and precise provider-coverage uncertainty remain unchanged. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, five-minute physical PASS and its verified private export remain intact. No RMSSD qualification, baseline, learning, Loop A, N1 or adaptive Suki action.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_ORDERED_ANCHOR_SOURCE_GATE
