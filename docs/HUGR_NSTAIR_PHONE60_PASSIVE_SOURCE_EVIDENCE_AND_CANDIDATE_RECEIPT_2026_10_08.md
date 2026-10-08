# Phone60 — passive selected-session evidence and truthful delivery status

**2026-10-08. Status: ENGINEERING AUTHORIZED; CODED, final native tests/artifact admission pending.** Source-owned receipt. Watch77 remains unchanged. No device action or actual Phone private-data readback has occurred in this engineering scope.

## 1. Current authority and objective

At **21:59 CEST**, Neil authorizes the combined qualification-closure engineering package: establish and implement only necessary non-mutating existing-session Phone evidence access and demonstrated delivery-status corrections; run applicable tests; prepare one signer-continuous Phone candidate if needed after tests pass. Preserve data and frozen boundaries. No device action, reconnect, new recording, private-data transfer or N1 execution. Return one concise future physical readback gate.

The earlier proposal is `HUGR_NSTAIR_WATCH77_QUALIFICATION_CLOSURE_AND_N1_NEXT_MOVE_PROPOSAL_2026_10_08.md`. The physical Watch finding is `HUGR_NSTAIR_WATCH77_SAVED_EXACT_ACK_COMPLETION_PHONE_EVIDENCE_PENDING_2026_10_08.md`. Target **actual retained source session**: `3c9e99e9-9430-480e-9b5a-ae00990b6b22`, not the historical bounded-run UUID. Honor is the installed Phone custody/delivery device; Fold is not a substitute.

## 2. Answer to stale sensor/current beat question

Retained-delivery recovery deliberately admits no new Watch sensing or samples. Phone live beats/IBIs are an in-memory current buffer that resets on connection/staleness; they are not the durable canonical-record count. Thus stale/MISSING current physiology and zero admitted live beats during this recovery **do not establish lost historical bytes**. The original short run visibly showed sensor values and canonical records, but full native custody and actual stream coverage/quality still require readback. We do not claim all streams were present, complete or clinically useful. Saved Diagnostics is a passive report viewer, not a physiological acquisition mode or a source of historical deletion.

The reader reports verified stored canonical rows by stream and source-clock timestamp bounds, without exposing physiological payloads. Those row counts are explicitly **not individual beat/sample counts or proof of acquisition**. Missing streams are `NO_SELECTED_RECORDS`; Watch source timestamps are not synchronized wall time or independently proved duration.

## 3. Demonstrated presentation corrections

- Remove the false whole-recording completion inference from equality of Phone source indexes. Complete recording/Watch completion remain unknown until adequate separate evidence exists.
- Current-lineage/session-qualified segment ACK metadata can show manifest-only delivery evidence even when no new live/source-health records arrive. A Phone ACK write event is not Watch receipt proof.
- A full-identity last-segment panel displays full source-session ID, index range/count/bytes/SHA-256 and Phone lineage/time, outside the connected-only live panel. It is explicitly the last event in this mounted UI, **not invented durable ACK history**.
- Separate current admitted live beats from saved-recording evidence. Keep existing live freshness eligibility and buffer reset.
- Production sensor acquisition, BLE/Connect/retry, canonical ingestion/native writer/recovery/equality/gap algorithms, source wire ACK and Watch77 are byte-unchanged.

## 4. Native passive existing-session access

Standalone **HUGR Saved Source Evidence** native `Activity` in `:sourceEvidence`, separate single-task launcher/info icon. An explicit Expo config plugin returns before generated Application React/Expo lifecycle initialization in that process; ordinary Main startup is unchanged. No Main HUGR, source recovery, BLE, service, sensing, sync, network, Share/export or external transmission is invoked.

One off-UI bounded operation snapshots the selected accepted binary and necessary SQLite metadata envelope into isolated cache. Any stable complete WAL/SHM pair is retained in the copy; no original SQLite connection, checkpoint or migration occurs. Only exact selected-session data queries are performed. Foreign `accepted_*` files are neither opened nor enumerated; no foreign session rows are queried/rendered. Whole database bytes are a necessary private metadata envelope only, never externalized. Source before/copy/after/post-verification full SHA/presence checks detect change, but do not falsely assert cross-process writer inactivity. Pending append/temp, rollback journal, incomplete WAL/SHM, corruption, source change, symlink and budgets STOP without repair.

Canonical verifier checks magic/version/session/index/stream/sequence/source time/length/CRC, per-row full SHA, complete indexed accepted byte coverage, record-index manifest order independently of accepted-file arrival order, exact manifest count/bytes/full digest/stream ranges, orphan/duplicate metadata and stored versus recomputed gaps/stream states. Missing index prefix/interior, missing/partial manifest coverage and source gaps give **PARTIAL_EVIDENCE**, not qualification. Complete selected indexed equality is **CANONICAL_EQUALITY_MATCH** only; it does not establish that an unknown tail or all streams were acquired.

Saved historical `verified` flags are not current recomputation or ACK proof. `ack_history` and `watch_completion` are explicitly UNKNOWN from this snapshot alone. No deleted source, overwritten Watch marker or absent ACK history is reconstructed.

Limits: 512 MiB total snapshot envelope; 25,000 selected records; 64 manifests; 320 manifest ranges; 256 source/report ranges; five stream states; 45-second monotonic worker budget; 8 KiB I/O buffers; canonical record allocation limited to wire uint16 payload maximum; 48,000 rendered characters with explicit STOP on overflow. Main/source roots are preserved; isolated cache cleanup never follows symlink directories. A cleanup failure is outcome-bearing **STOP / SCRATCH_CLEANUP_FAILED**, never successful verification with secretly retained copies. Process termination can still interrupt Android cleanup; subsequent passive launch removes only its isolated scratch cache before reading, not any source store.

## 5. Concrete pre-admission corrections and review

Initial independent implementation review prompted fixes before admission: arrival order versus index order, mixed monotonic clocks, misleading QUALIFIED literal for partial data, suffix/prefix missingness, orphan/duplicate metadata, stable WAL/SHM copying, per-record allocation/offset overflow, symlink containment, bounded report output and removal of an ineffective process-local writer lock. Production native module was restored unchanged.

Generated prebuild revealed React/Expo Application initialization even in a native secondary process. The precise process guard is now tested/idempotent and rejects unsupported generated templates rather than silently omit isolation. The independent focused final review reported one blocker: swallowed scratch-cleanup failure. That was corrected with an outcome-bearing cleanup boundary and regression test. Remaining reviewed snapshot/session/CRC/manifest/partial evidence/no-mutation/no-transmission boundaries passed the focused source review. A final native test result is still required; no test pending is relabelled passed.

## 6. Current validation

- Phone Node deterministic: **105 total, 103 pass, two existing fixture-dependent skips, zero failures** at the latest run; includes status/scope/native-isolation/Application-plugin contracts.
- Focused transport status12/12; narrow changed-status strict TypeScript check and changed TSX syntax pass.
- Broad repository TypeScript still fails in pre-existing archival/harness paths; no changed status/WatchConnect error was found. Not globally clean.
- Complete joined synthetic Watch→Phone→Watch regression: all five phases pass, including stored current-model records/final data and historical C8184/R8182 three manifest-only exact ACKs with restart after each and no new records. **Simulated persistence/wire only**, not physical Android custody/BLE.
- Native Activity/core compile under standalone Java17/Kotlin1.9/SDK34; Android SQLite/Robolectric regressions loading SDKs and pending final rerun on corrected source. This is a harness only; release uses existing EAS project toolchain. No final native pass or APK claim yet.

## 7. Candidate identity and remaining gates

Prepared configuration only: package `health.hugr.app`, code60, version `2.0.60-saved-source-evidence-candidate`, existing `@neilhugr/hugr-mobile`, project `d0c3af97-5a78-4ba1-bc8b-8a21f169ce44`. Reuse existing managed preview signing with frozen credentials; never create replacement identity. Phone59 baseline signer SHA-256 independently read back: `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`.

Build only after applicable native tests pass. Then verify exact package/version/signer, ZIP/alignment/component/class/source-stage identities, artifact SHA and upload/CDN byte readback. Ordinary update/install and passive viewer opening require **separate device authorization**. No APK admission or installation follows merely from this prepared configuration.

Proposed later concise gate: ordinary Phone-only in-place update on Honor, preserving data/no uninstall or clear, confirm version in Settings and stop without Open. Only under separately explicit passive-readback authority open **HUGR Saved Source Evidence**, not Main HUGR/Watch launch/Connect; allow at most60 seconds for report, capture header through END REPORT for local review, no Share or external private-data transfer. STOP on any prompt/crash/no report/source-integrity/budget failure; no retry/reconnect. Bluetooth grey/disconnected is not a prerequisite blocker for this offline reader. No new recording/N1 is authorized. Actual reader output must be reconciled with existing Watch saved exact-completion evidence; neither one segment nor index equality qualifies the run.

## 8. Preservation and invariant states

WIP was already committed and exact-tree mirrored non-force on the three controlled preservation branches before native/build boundary. Initial checkpoint local→remote/tree:

- Watch documentation `6b2da062d62a07fd576eb4350e7c177e028fb65b` → `eb0165af51501a8b584247a932d78901150be72b`, tree `85b9d777bbf4f41d1a5b301737c34b106e000f82`.
- Phone WIP `5a6647b53c35916e15452914a012e2fa0c46aa4c` → `a3183e4e965f9bf7966e597827af1349e8e7a1d3`, tree `dda58274d2b5fec9b846dc69672361e7a772da5b`.
- Controls `f4eeea42d6c44dad477ca7b76002b48aa320756e` → `3213cc0ea6ea49ddd0706228e908ab10978e488c`, tree `20581ec61975622a8ba682c4a7577f2f0925bf2c`.

Those are valid **incomplete checkpoint** snapshots, not final admission heads. Current corrections require successor closeout. Source-owned receipt/copies → Master → affected authorized accessible private proposal only → Alignment last → consistency/bounded commits → controlled both-repository write preflight/non-force mirror/exact tree readback. Accepted S4 unchanged; known-denied Atlas not retried. No secrets or private physiological/media bytes enter source/control GitHub.

- Watch69 **NOT QUALIFIED**.
- Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 9. Final source/native validation — 22:30 CEST

Native synthetic tests **29/29 pass, zero failures/errors/skips**: Android SQLite adapter11, pure canonical verifier10, existing pending-append3 and source-gap5. Actual Android SQLite/WAL stable-copy fixture observes committed selected metadata from WAL, retains original main/WAL/SHM exact hashes/presence, and verifies selected full canonical digest. Other adapter cases cover foreign-session filtering, pending temp/rollback/corrupt/missing source STOP, suffix-prefix partial evidence, symlink rejection, native Activity isolation and outcome-bearing cleanup failure. These tests exercise the native adapter under Robolectric, not real Honor storage or BLE. Initial failures were fixture expected-code/API mismatches; production fail-closed logic was not weakened. Final105 Node tests103pass2existing skips; scoped typecheck passes. Broad archival typecheck failures remain. Focused independent review blocker fixed with test; generated Application guard returns before React/Expo lifecycle in the passive process. Source-only validation complete; **candidate build now authorized after these passing tests, no artifact yet admitted**.

## 10. Single Phone60 build submitted — 22:32 CEST

One managed preview APK build `4b3c00ed-de65-4953-9d23-f068db36d8d6` submitted with frozen existing signing credentials under existing @neilhugr/hugr-mobile. Canonical tested Phone source `529a5707ea92867ddda8e66e04cfb6e1a0d9be48`; sanitized exact-runtime stage commit `6923e8781c6eb78e3ab779b6ac14e04921210d54`; runtime SHA manifest `ee568a507f2a0348d168b9b66273d1658426e00a4d2ad89dbb5935e2dacb37b3`. Stage117 tracked runtime files byte-read-back checked; generated Application process guard verified before React/Expo initialization, generated Android/.expo removed from upload stage. No source store, private evidence, obsolete build archives or credential entered stage. Build submitted, not yet finished/admitted. No automatic second build. Proposed local-view-only physical gate copied as `HUGR_NSTAIR_PHONE60_PHYSICAL_READBACK_GATE_2026_10_08.md`; actual private evidence sharing needs distinct authority.

Tested source and controls remotely verified: Watch document head49dd461 → remote3f2bffb, Phone529a570 → remote006ccda, controls cb34f65 → remote bcca628. A first Phone readback mismatch caused publication STOP; subsequent independent GET found the exact intended Phone head/tree (no source drift). Controlled successor run skipped already-present Watch/Phone and published only controls non-force. This is not a permission failure or forced overwrite. Final artifact/source successor requires its own preservation/readback.
