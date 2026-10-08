# Phone60 — passive selected-session evidence and truthful delivery status

**2026-10-08 23:58 CEST. CURRENT STATUS: REPLACEMENT Phone60 APK ARTIFACT ADMISSION GREEN; NOT INSTALLED. First APK remains REJECTED / DO NOT INSTALL.** Source-owned receipt. Watch77 remains unchanged. No device action or actual Phone private-data readback has occurred in this engineering scope.

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

## 11. Actual APK admission STOP and concrete packaging correction — 23:04 CEST

The sole managed build4b3c00ed-de65-4953-9d23-f068db36d8d6 finished at2026-10-08T20:55:33.785Z. Its artifact download initially403 with Python; ordinary redirect-aware curl obtained the same APK, resuming the remaining bytes after a bounded timeout. This was download handling, not a failed build or GitHub permission issue. **No second APK build was submitted.**

Downloaded151,851,054-byte APK SHA-256 `5d173ef15a8ee7aa9478d59ee98d406abb8e8d3a405ced19814df51311f74fe4`: same package health.hugr.app, code60, expected version, exact Phone59 signer a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811; signer verification/page-alignment passed before admission failed. **REJECTED / DO NOT INSTALL**, not uploaded/offered as a candidate. Quarantined as `artifacts/rejected/HUGR_Phone60p_2.0.60_REJECTED_DO_NOT_INSTALL.apk` with checksum sidecar. The original filename is no longer a deliverable.

Concrete admission failure: finished APK manifest lacks the Saved Source Evidence launcher, and DEX definitions lack **both HUGRSavedSourceEvidenceActivity and existing HUGRSourceJournalModule**. Independent Phone59 APK comparison confirms its existing native writer IS present. All controlled source/native files remain recoverable; no installed device/data is affected because no installation occurred. This is an agent-owned build-packaging mistake, not an operator/Bluetooth/data-loss finding.

Exact cause reproduced with git check-ignore: staging `.gitignore` used unanchored `android/`, which excludes not just generated root Android but `modules/hugr-source-journal/android/`. Files had been copied and hashed on disk, but excluded from the committed/actual EAS upload tree. Earlier on-disk stage/guard checks did not establish upload custody; the initial stage117files assertion is explicitly insufficient. APK admission correctly stopped the bad artifact.

Small correction implemented/tested: anchor `/android/` and `/ios/` at stage root, require seven critical native module/config/plugin paths, and verify **every staged payload's presence/full byte equality in HEAD** before declaring the stage verified. Three regressions reproduce the old omission, verify actual corrected ignore rule includes native writer/manifest and excludes generated root, and bind stage custody to committed bytes. Full Node **108 total,106 pass,2 existing fixture skips,zero failures**. Native29/29 and joined five phases remain applicable/passing; production/native behavior has not changed since those tests.

Additionally ran **EAS build:inspect stage archive only**, not a native build/submission. Corrected upload archive includes118/118 runtime files at their exact full SHA values, all11 native module files including the unchanged writer and new Activity/manifest, excludes generated root Android/node_modules. This tests the actual archive route that failed, not another speculative code audit. No source store/credential/private physiology was staged or transmitted.

**Remaining blocker:** produce and admit exactly one replacement APK from the corrected upload recipe, under separately explicit replacement-build authority. Same package/existing signer/code60/version remains possible because the rejected APK was never installed/admitted; freeze credentials. Then require native writer AND reader classes, exact isolated launcher/guard, full package/version/signature/SHA/class/alignment/upload/readback checks. No replacement build, installable APK, device update/readback/Connect/recording/private-data transfer/N1 is claimed. Physical readback gate remains blocked until replacement artifact admission plus separate device authority.

Watch77 unchanged. Installed Phone59 unchanged. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 12. Explicit replacement-build-only authority — 23:20 CEST

Neil authorizes **one replacement Phone60 build and admission only**. Reuse the corrected final stage whose complete118-file actual EAS upload archive byte identity/native inclusion was already checked. No production source change, new signing identity, device installation/launch/readback/Connect/reconnect/private transfer/new sensing/recording/N1 is authorized. Watch77 unchanged; installed Phone59 unchanged. Preserve the rejected predecessor under its exact SHA/status; do not offer both APKs as equivalent. Build once with existing managed preview credentials frozen; admit only after native writer/reader/isolated manifest/Application guard, exact package/version/signer/class/ZIP/alignment/SHA/upload byte checks pass. Stop/report any concrete failure; no third automatic submission.

## 13. Authorized replacement submitted — 23:22 CEST

Exactly one replacement preview APK build `0c2651df-ffe8-488e-a4d0-a40897d32c25` submitted from corrected stage commit `be1453c2a54ded9261e29de336776234ba827881` with existing frozen managed credentials. Runtime SHA manifest `fc717be1a0c856310c6de83ab7778784903cc43eedc3d54516d974b0ebabb10b`; all118 actual EAS upload runtime files were full-byte equal to controlled source, including native module, prior to submission. Canonical runtime source commit `8fbdf41b9d7a1f5e888c3b5c8a158ce6dc535860`; later receipt/control-only descendants do not change runtime. Build IN PROGRESS, **not finished or admitted**. Watch77/installed Phone59 untouched; no device or private-data access. Rejected predecessor remains immutable/quarantined. Wait only on this ID, then required full artifact admission; no third automatic build.

## 14. Sole replacement Phone60 artifact admission GREEN — 23:58 CEST

Explicit23:20 build/admission-only authority was executed with exactly one replacement build. Managed preview `0c2651df-ffe8-488e-a4d0-a40897d32c25` finished at`2026-10-08T21:46:16.659Z` from corrected exact-runtime stage commit`be1453c2a54ded9261e29de336776234ba827881`; no third build or device action. The stage's actual EAS upload included all118 runtime files with exact full-byte identity, including the existing native writer and passive reader/manifest.

### Sole admitted artifact

- Filename: **HUGR_Phone60p_2.0.60_saved-source-evidence_REPLACEMENT.apk**.
- Package`health.hugr.app`; code**60**; version**2.0.60-saved-source-evidence-candidate**; minSDK26/targetSDK36.
- Size**151,933,130 bytes**.
- APK SHA-256`10c135f734a7e07ca144b9ffef630a19f1cebf395b6c7581d78cbd96b7d1c98f`.
- Signer SHA-256`a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`, exactly Phone59 lineage. No new account/project/signing identity.
- [Sole admitted Phone60 APK](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/dWYzDgHluvRgCpvm.apk) · [Exact SHA-256 sidecar](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/HAttVLgRnHBTiFxU.sha256).

The first151,851,054-byte Phone60 artifact SHA`5d173ef15a8ee7aa9478d59ee98d406abb8e8d3a405ced19814df51311f74fe4` is **REJECTED / DO NOT INSTALL**, immutable/quarantined and not an equivalent or download option. Only the replacement link/identity above is admitted.

### Direct artifact checks

- Package/version/SDK, exact signer, ZIP CRC/integrity and supported build-tools4KiB/page alignment PASS.
- **39,226 class definitions, no duplicates**; exactly one existing`HUGRSourceJournalModule`, passive`HUGRSavedSourceEvidenceActivity` and normal`MainApplication`. The reader extends native Android Activity, not ReactActivity.
- Finished merged APK manifest confirms labelled information-icon launcher **HUGR Saved Source Evidence**, isolated`:sourceEvidence` process/singleTask/taskAffinity, standard MAIN/LAUNCHER filter. Info icon is framework resource`0x0108009b`=public`drawable/ic_dialog_info`, independently resolved in Android platform resources. No additional standalone app installation is needed after an eventual ordinary update.
- Compiled MainApplication DEX **onCreate** and **onConfigurationChanged** invoke process guard and branch to immediate return before React/Expo lifecycle initialization. Native snapshot cleanup helper and exact selected session embedded; honest current-live/segment-event UI strings present in compiled HermesUTF16 storage.
- Admission tooling initially hit AAPT1 framework-icon resolution, HermesUTF16 and DEX modified-UTF8 decoding issues. Corrected **admission helper only** to use successful AAPT2 badging and correct encoding handling. APK/build/production source bytes were not changed or rebuilt; checks remained exact and were completed.
- APK uploaded and independently downloaded from CDN; **full151,933,130-byte SHA equals local admitted artifact**. Uploaded125-byte sidecar matches local bytes exactly. No private data/diagnostic report/source store was uploaded.
- Production Phone BLE/Connect/retry/ingestion/native journal writer/recovery/gap logic/sync/ACK wire remain byte-identical to Phone59 preserved baseline; Watch77 production source/APK remains unchanged (APK SHAec074c30aab34540934458d3e7375f226fd60a643a735dc65f78041b782e5c90).

### Validation and limits

Final Phone deterministic**108 total:106 pass,2 existing fixture-dependent skips,zero failures**. Native core/Android SQLite adapter+pending/gaps**29/29 pass**. Joined Watch→Phone→Watch regression passes allfive phases with simulated persistence. Scoped status typecheck passes; broad archival repository type errors remain, not globally clean. Actual Honor snapshot/report, real data custody, stream completeness/quality and Watch69 qualification remain unobserved. APK admission is not hardware/runtime qualification.

### Exact next physical boundary, not execution authority

`HUGR_NSTAIR_PHONE60_PHYSICAL_READBACK_GATE_2026_10_08.md` now names this sole artifact. **Installation remains separately authorized**: Honor ordinary in-place Phone59→Phone60 update, preserve app/data/no uninstall or clear, stop at installer result then Settings version check without Open. Watch77 unchanged/unopened. Later **separate passive readback authorization** can open the information-icon HUGR Saved Source Evidence only, one report/60-second cap, no normal Main/Watch launch/Connect/Bluetooth repair/new recording/private-data transfer/N1. The reader is offline; current grey system Bluetooth listing is not a readback prerequisite. Private report sharing/upload remains separately authorized.

Source-owned receipt/copies → Master → no known-denied Atlas retry/acceptedS4 mutation → Alignment last → bounded commits → non-force both-repository preflight/mirror → independent exact head/tree readback must complete before declaring final preservation. Prior source/test/submission snapshots remain historical.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
