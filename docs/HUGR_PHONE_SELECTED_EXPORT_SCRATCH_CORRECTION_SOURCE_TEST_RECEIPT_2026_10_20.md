# HUGR Phone selected-export scratch-directory correction — source/test receipt

Date: 20 October 2026. Source owner: Phone repository, `docs/HUGR_PHONE_SELECTED_EXPORT_SCRATCH_CORRECTION_SOURCE_TEST_RECEIPT_2026_10_20.md`.

## Authority and disposition

Neil authorized: “Authorize the focused Phone export scratch-directory source/test investigation and correction of any demonstrated defect.” This grants source/test work only. No APK build, installation, app/device action, export retry, real-record access, new collection or N1 is included.

**Disposition: focused scratch correction source/test PASS, including native exporter → independent verifier → descriptive inventory. Wider native suite not completed in the reset/emulated environment. No candidate admitted or real-record handoff completed.**

The reported Phone64 failure followed exactly one Prepare selected package press, before Save/Share and without retry. Keep that physical STOP intact. The reported message is consistent with `EXPORT_SCRATCH_ROOT_UNSAFE`, but the exact Honor path/predicate is not independently established. This is not evidence of missing or corrupted canonical records; the device’s “Originals were not changed” wording is not an independent custody recheck.

## Authoritative baseline recovered after reset

Three clean exact branch checkouts were recovered by GitHub read-only access and their heads/trees checked before mutation:

| Owner | Branch | Verified starting head | Exact tree |
|---|---|---|---|
| Phone | `preservation/phone-control-and-source-2026-10-03` | `b1d199a5c754ffe6cf094d5b1d77d0772db8d189` | `0b0a58749f8736ada4881914defedae2515a2c3b` |
| Watch | `preservation/watch68-partial-source-2026-10-03` | `e1d29a5887325e9d112e2a07388955bb38072267` | `66cb5ad99c0a885894645173b597c82fb12895dc` |
| Controls | `preservation/watch69-control-records-2026-10-03` | `39522836fca24a7b1f13f20d7574904b61dce957` | `725d54b80641b5f9a440be67aabb5451e553e9fc` |

Recovered fragments were not treated as a replacement source baseline. Previously checksummed installation/export/STOP documentation snapshots remain historical evidence; bringing them into restored local commits does not retroactively make them remotely preserved.

## Demonstrated source defect

The original exporter’s `createSafeDirectory` calls `requireNoSymlinkAncestors` both before and after creating scratch. That helper walks the lexical absolute path to filesystem root and rejects every symlink, including a legitimate framework/platform ancestor alias strictly above the app cache. Its desired rejection of unsafe managed scratch links therefore also falsely rejects a safe cache path exposed through an ancestor alias.

A disposable baseline-only JUnit test invoked the exact private helper from the original committed exporter. The aliased cache path was rejected with `EXPORT_SCRATCH_ROOT_UNSAFE`; the corresponding physical path was accepted. This is executed source-helper proof, not an Android/Honor runtime diagnosis. The original exporter SHA-256 is `4ca1799ff361e402e2009972e65aba5a6daf59467d90b086ff408967b9cd4766`.

## Narrow correction

`SelectedRunExportScratchSpace` resolves the framework-supplied cache root once. A symlink at the supplied cache leaf is still refused. Only aliases above that root are admitted; managed scratch parent/run/member links are not canonicalized into authority.

The helper constructs a fixed direct-child scratch parent and a UUID-only per-invocation run directory. Existing managed directories must be non-symlink directories with unchanged canonical identity. Creation is single-level; collisions fail closed. Scratch member writes use create-new/no-follow channels and durable force, then recheck the exact member. Publication refuses an existing destination. Cleanup only applies to the run this invocation actually created, walks without following links, rejects unexpected child directories, and never deletes cache, the shared scratch parent or a pre-existing colliding run.

Only exporter scratch creation/copy/write/cleanup and focused tests changed. Original-record selection, report/receipt binding, manifest verification, exact ACK checks, independent verifier, source writer, pending-recovery boundaries, output ZIP protocol, explicit Save/Share, Activity/provider, package version, signing, permissions and Watch runtime remain unchanged. General output/provider hardening identified during review is deferred, not silently incorporated into this focused repair.

This is not an OS-level descriptor-rooted defense against malicious concurrent same-UID namespace replacement between validation and path operations. App-private cache ownership and invocation-unique names remain explicit assumptions; no universal race-proof claim is made.

## Test evidence and retained failures

| Check | Currently established result |
|---|---|
| Exact committed helper alias reproduction | PASS: old helper rejects safe ancestor alias, admits physical path |
| Separate Python package-reader suite | 11 passed, no failures |
| Complete Phone source/deterministic suite | 163 cases: 160 passed, zero failures, three explicit unprovided-fixture skips |
| Protected runtime/config scope | 124 unaffected Phone files byte-identical to baseline; physical five-minute PASS receipt unchanged; Watch runtime unchanged |
| Independent static correction review | Approved within scratch-only scope; same-UID race limitation retained |
| Original exporter production-writer alias regression | Expected RED: one executed failure, `EXPORT_SCRATCH_ROOT_UNSAFE`, zero setup errors/skips |
| Corrected native scratch/exporter tests | 19 passed: ten exporter tests and nine scratch filesystem tests; zero failures/errors/skips |
| Wider complete native suite | NOT COMPLETED: emulated Gradle test-worker startup deadline; no aggregate PASS claimed |
| Independent verification of corrected native-produced ZIPs | Alias, V1 and V2 all PASS against independently encoded synthetic parent anchors; exact members/binding/manifests/receipt/report/ACKs and readable inventories |

The three Phone skips are the unprovided fresh Watch fixture and two historical joined-recovery fixture prerequisites. They are not successes and do not replace prior preserved duration/physical evidence. This task does not start another capacity programme.

The reset sandbox is Linux ARM64. The preserved Robolectric 4.12.2 stack failed before test setup because its Conscrypt native dependency lacks Linux AArch64 JNI. Android/Kotlin source/test harness compilation is separate from app artifact building; no APK was produced. The original setup failure and toolchain receipts are retained. A local edit initially produced a checksum-map `put` compiler error; restoring the unchanged two-argument map insertion corrected it, with the failed log and WIP source snapshot retained rather than omitted. No assertions were weakened. An official Temurin 17 x86 test JVM ran under QEMU while Gradle/Kotlin compilation remained ARM Java 17. Production dependencies, Robolectric 4.12.2, Conscrypt and native SQLite remained unchanged. The old production exporter reached its original guard and failed as expected; corrected exporter tests executed successfully. All 34 native production/test files matched the active corrected harness input. These are synthetic JVM/Robolectric/filesystem tests, not Honor Android physical qualification.

The canonical core verifier’s 180,000 ms budget and separate 120,000 ms packaging budget are unchanged. Test-environment elapsed time is not Honor performance or physiological evidence.


### Final synthetic package evidence

Each corrected native-produced package contains the declared synthetic parent of five records, one manifest and one exact Watch-accepted ACK, 260 canonical bytes. The external synthetic parent SHA-256 is `868e5ca233eadda1846bdf300aa7ddd4508e956ddb5bc7e65dba47e0a1185388`. It was independently encoded from the test input, not taken on trust from exporter success or package metadata. Separate inventories describe the five synthetic streams without physiological conclusions.

| Package case | ZIP bytes | Independently checked ZIP SHA-256 |
|---|---:|---|
| alias | 5655 | `2fa48ed18be3cbbdaf455dfc38722cebe93da1874db7d843a67b79d78c4dd80e` |
| v1 | 5654 | `16f03a39d7db613aa284b099e549bcc11eb7a1b705ba7971430a90e9e4b74d8f` |
| v2 | 5645 | `af6166c6f4bddbf23b2aca69bab5907b28446f9d887fe633f3168a8414e740da` |

No real five-minute bytes are included in these tests. The existing eight-hour exporter case was explicitly excluded, not silently counted as a passed/failed test; its prior genuinely executed result remains preserved. No eight-hour capacity programme was repeated.

The initial wider-suite attempt contained a baseline-only helper reproduction test in a disposable test snapshot and was invalid for current-source proof. That leakage was removed only from the disposable corrected harness and its failure retained. A replacement invocation naming the 20 active-source native classes encountered the emulated worker’s 120-second startup deadline before a new full-suite count was produced. This is an environment/run limitation, not evidence of recording corruption or a widened canonical-verifier budget. The leftover job-owned full-suite launcher/children were terminated; no unattended broad testing is intended.

Final corrected exporter SHA-256 `efbc65d5ad3d6e4c38568de2bf9f7d081cb2de4aa9b7a13e83fcf948dc522964`; scratch helper SHA-256 `2db8734a5f26b25e8984b8c53d3b36005d753c15e679da74c15094939e0281cc`. The independent reader remained byte-identical to the preserved Phone branch, SHA-256 `bde121230dfb16f8fed3982e1a2c843e14a32ded5224ea4e9ab20ad0c7303c5e`.

## Preservation and privacy

Required closeout: source-owned receipt, Master Task List, no affected/authorized private VIS-COUNT proposal, Alignment last, bounded coherent local commits, then non-force mirror and exact remote tree/blob readback only if both-repository write preflight is satisfied.

Current GitHub reads succeeded, but the previously established repository-write gap remains: the existing route returned HTTP 403 at the `Nervahealth/nerva-wearos` Git-object write preflight; the dedicated persistent write login was lost in reset. No changed-access evidence has been established, so the known-denied write route is not blindly retried. Remote installation/export/correction closeout remains pending. Local commits or a sanitized checksummed fallback must never be called remote source preservation.

No physiological records, actual reports, real export ZIPs, private media, credentials, keystores or APK payloads belong in GitHub or this documentation fallback. Synthetic package bytes remain test-owned and outside repository preservation. Source backup is not canonical-recording backup.

## Separate next gates

The focused native exporter and independent synthetic-package checks have passed. The smallest proposal is one signer-continuous Phone-only candidate, proposed Phone65, containing this scratch correction and keeping Watch83 unchanged. This task does not change the version or build configuration. Before any build, disclose the current wider-native-suite environment limitation and establish whether preserved unaffected-suite evidence plus these focused executed regressions satisfies the candidate’s admission gate; do not claim a fresh complete-native PASS. A build/admission requires separate authority and earned source-preservation prerequisites. Installation-only and a fresh one-shot private export are subsequent separate gates. No retry of installed Phone64 is implied by a source correction.

The actual five-minute ZIP is still absent. Its later independent verification must use the established external anchors: source `24ca3a75-62ab-4912-91a0-d50613b02928`, run `46628727-a977-4dd7-b4e6-19e196efe383`, 8,349 records, six manifests/six exact Watch-accepted ACKs, 539,746 canonical bytes, SHA-256 `a71ff129b07b53c5c67f2d2160746d8a5de4c887997cde921dfb8898b9f6b470`. Anchors and receipts are not the canonical records themselves.

Recording/custody PASS, prior synthetic duration/export results and this source correction are separate from **real research-data handoff NOT COMPLETE**. No qualified RMSSD, native beat-chronology claim, baseline/learning ownership, Loop A or physiological-validity conclusion follows. S4, accepted adapter/companion, T2.2, October reconciliation and prior failures/corrections remain unchanged.

Watch69 `NOT QUALIFIED`; Watch68 v1 `UNCLASSIFIED / NOT VERIFIED`; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.


## Later authority — GitHub write recovery preparation, 21 October 2026

Neil separately authorized restoring GitHub preservation while keeping Phone64 STOP/no retry and leaving a Phone65 build/admission for separate authority. A fresh official Nervahealth CLI authorization and actual nonsensitive Git-blob write preflights succeeded on BOTH repositories with injected token variables removed. The current source/control branch heads/trees still matched the exact reset-recovered remote baselines. The reviewed controlled helper now uses those remote ancestors and persistent-write-login-first plus write-login-only mode; eight offline control-helper regressions pass. The earlier denied/default/task route was not blindly retried.

See `docs/HUGR_GITHUB_PRESERVATION_RESTORED_AND_PHONE65_BUILD_GATE_2026_10_21.md` for current publication/checkpoint status. This later authority does not change the focused scratch test evidence, known wider-native-suite limitation, phone version, runtime, Watch83 or any physical/data gate. All earlier failures/local-only snapshots remain history; no real five-minute package has appeared. Final bounded publication and exact remote readback are separate from mere restored credential access.


## Later preservation closure — independently verified GitHub checkpoint, 21 October 2026

GitHub preservation is now COMPLETE at the exact published checkpoint in `docs/HUGR_GITHUB_PRESERVATION_RESTORED_AND_PHONE65_BUILD_GATE_2026_10_21.md`. Fresh official Nervahealth write-login authority, both-repository actual Git-blob probes, non-force existing-branch publication and a separate complete-tree/all-changed-blob byte readback passed for Watch/Phone/controls. This closes the prior in-scope installation/export/STOP and scratch-source-control publication gap; it does not erase the earlier legitimate local-only/fallback history or prove another task/repository/private recording backup.

Final documentation closure is itself subject to the same non-force mirror and exact independent readback. Phone64 remains STOP/no retry, no real ZIP has been received, and Phone65 build/admission still requires a new separate authorization explicitly disclosing the wider native-suite limit. No new engineering, tests, build, device or private-record action occurred through this preservation closure.
