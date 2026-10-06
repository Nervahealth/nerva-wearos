# HUGR NSTAIR — Watch72 / Phone58 Recovery-Boundary Candidate Admission

**Date:** 2026-10-06
**Scope:** signer-continuous candidate build and artifact verification only
**Status:** **SOURCE / TEST / ARTIFACT GREEN — NOT INSTALLED / NOT LAUNCHED / NOT DEVICE-VALIDATED**

> This receipt admits two versioned diagnostic/recovery candidates at the source, test, signing, managed-build and artifact boundaries. It does **not** authorize installation, application launch, pairing, reconnect, BLE retry, sensor collection, a new recording, egress, acknowledgement, verifier execution, or any operation on legacy/frozen roots.

## 1. Purpose and narrow correction

The preceding Watch71 / Phone57 source investigation isolated two distinct recovery-boundary risks:

1. Watch recovery had not exposed enough causal evidence to distinguish retained-session selection, standard GATT server/service registration, advertising readiness, and early GATT failure.
2. Phone57 could present a black screen after an unhandled root-start failure or scan-start rejection, leaving the scan/connect/service-discovery boundary unobservable.

The candidate work is deliberately bounded:

- **Watch72** remains ordinary-runtime only. It fail-closes when no retained fresh-v2 finalised source session is eligible; it records bounded causal readiness/failure events around retained-session selection and standard GATT registration/advertising. It does not start sensing in recovery mode.
- **Phone58** adds a bounded, payload-free recovery diagnostic timeline spanning root-start, scan, connection, service discovery and resume boundaries. It releases the splash screen on font/startup failure and catches scan-start failure in the visible control. It does not alter the durable source-ingest/equality-before-ack protocol.

The retained fresh-v2 source session remains the sole recovery target. Neither candidate reads, selects, acknowledges, alters, exports, migrates or deletes legacy/frozen roots. The retained binary egress compatibility payload is not exercised or validated by these tests.

## 2. Exact candidate identities

| Line | Candidate | Package / version | SHA-256 | Signing continuity | Download |
|---|---|---|---|---|---|
| Watch | `HUGR_Watch72w_0.72.0_recovery-boundary-diagnostic-candidate.apk` | `com.hugr.wearos` / code `72`, `0.72.0-recovery-boundary-diagnostic-candidate` | `9fcfca6ac0f926acfb20ca402ca09c37ea9f2e40ab849f62795b9cffa64ed5f1` | v3 cert SHA-256 `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706`, equal to Watch71 | [APK](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/irGYHqtkKJlMnGTQ.apk) · [SHA-256 sidecar](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/ZPimZjbDpwJKHYMF.sha256) |
| Phone | `HUGR_Phone58p_2.0.58_recovery-boundary-diagnostic-candidate.apk` | `health.hugr.app` / code `58`, `2.0.58-recovery-boundary-diagnostic-candidate` | `5ba0e207b1a33c94294c7e02cd7c4db5cfd95d60dcf793f84e176ae0bec4376c` | v2 cert SHA-256 `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`, equal to Phone57 | [APK](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/UcAOjkCSGuoDFjJf.apk) · [SHA-256 sidecar](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/yFOXrccnzOUYySuM.sha256) |

The Watch72 artifact verifies as a well-formed ZIP, is page-aligned, has no duplicate DEX descriptors, and exposes the expected package/version identity. Phone58 verifies as a well-formed ZIP and exposes the expected package/version identity.

Phone58 is managed-build `405b6dec-3a7e-4be2-8e05-2e707fc1760c`, profile `preview`, Android internal distribution, completed `2026-10-06T09:44:41.264Z`. The direct managed artifact URL was downloaded and SHA-256 verified before this admission.

## 3. Source and test evidence

| Boundary | Result |
|---|---|
| Watch72 full JVM regression | **144 tests, 0 failures, 0 errors, 0 skipped** |
| Phone58 complete deterministic suite | **80 tests, 0 failures, 0 skipped** |
| Phone recovery diagnostic model | Passing bounded/payload-free timeline, sanitisation, and scan→connect→discovery→resume distinction contracts |
| Phone root/scan safety | Passing splash-release and asynchronous scan-failure containment contracts |
| Watch artifact integrity | ZIP integrity, alignment, package/version, signer and DEX uniqueness verified |
| Phone artifact integrity | ZIP integrity, package/version and signer verified |

The repository-wide Phone TypeScript command remains non-green because `tsconfig` includes pre-existing archived recovery snapshots and evidence-harness sources with unresolved imports and implicit-`any` diagnostics. The diagnostics seen on this run were in those archived/unrelated inclusion paths, not a new candidate source failure. The deterministic test suite and corrected candidate boundaries above are the applicable passing checks.

## 4. Source and remote preservation

The candidate implementation baselines are committed and now independently readable on GitHub:

| Repository | Branch | Verified remote head at initial candidate publication |
|---|---|---|
| `Nervahealth/nerva-wearos` | `preservation/watch68-partial-source-2026-10-03` | `8bcc09532a33e4302f7ebc803f97f86200110310` |
| `Nervahealth/nerva-mobile` | `preservation/phone-control-and-source-2026-10-03` | `d70bfb6bd683d26350441399398526c384e861db` |

The prior GitHub failure was local credential routing plus an unscoped integration credential. On 2026-10-06, the authorized GitHub CLI credential obtained `repo` scope; a non-mutating Git-ref probe reached the write API and returned the expected `422 Reference does not exist`, rather than authorization denial. The persistent Git credential helper now explicitly uses that scoped credential while suppressing the stale injected token. A Git dry-run then passed for both candidate branches before their real no-force pushes. No credential, token, key or signing material is included in source, control records, archives or this receipt.

Earlier sanitized fallback archives remain retained as historical snapshots; GitHub now holds the cited source baseline commits. This receipt's own commit/push verification is recorded in its later closeout section.

## 5. Still-not-established facts and hard boundaries

- **Watch69’s five-minute run remains NOT QUALIFIED.** No final canonical equality/acknowledgement/completion evidence was obtained.
- **Watch68 v1 remains UNCLASSIFIED / NOT VERIFIED.**
- **Watch58 remains exactly:** `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
- No candidate or test establishes that Watch72 selected the actual retained Watch69 source session on hardware, that standard GATT advertised, that Phone58 scanned/connected/discovered services, that the black screen is resolved, or that any final source session was acknowledged.
- No test validates the retained binary egress payload or restores unavailable Exact Range Readback source/launcher capability.

## 6. Next physical gate — separate authority required

The next smallest physical move is **not authorized by this receipt**. It would need a separate explicit installation-only admission for the exact Phone58 and Watch72 hashes above, followed later by a separately bounded no-press/runtime diagnostic observation.

That later observation must stop before any new recording, egress, acknowledgement or verifier action. Its only purpose would be to capture the bounded Phone/Watch recovery-boundary timeline and distinguish (a) retained-session selection, (b) standard GATT readiness, (c) Phone scan/connection/service-discovery progression, and (d) visible Phone root failure if any.

## 7. Governing status

This is an **artifact admission**, not a delivery success, an installation admission, or a physical recovery result. Alignment and Master records must preserve the distinction.

## Preservation closeout — GitHub remote verification

The candidate-admission commits were non-force pushed through the verified scoped GitHub credential and independently read back from the named preservation branches on 2026-10-06:

- Watch source: `Nervahealth/nerva-wearos` / `preservation/watch68-partial-source-2026-10-03` → `6202405b8d4d467f05f5fdd8b5b8b8f268472586`.
- Phone source: `Nervahealth/nerva-mobile` / `preservation/phone-control-and-source-2026-10-03` → `4945d518ca0e3b91b52b40d98e2cea6554897df3`.
- Governed controls: `Nervahealth/nerva-mobile` / `preservation/watch69-control-records-2026-10-03` → `f478ae25734849ab2d40309a8ac76e72e859a33c`.

This proves remote preservation of the candidate admission records at those commits. It does not alter accepted S4, install either artifact, or establish a device, delivery, acknowledgement, completion, or qualification result. Earlier sanitized fallback snapshots remain retained as supplemental preservation; they are no longer the sole remote-preservation route.


## Baseline reconciliation — 2026-10-06

This addendum reconciles the current development histories with their GitHub preservation mirrors. It introduces **no source, test, artifact, installation, or device change**.

### One agreed continuation baseline

| Scope | Local development branch and authoritative current local history | Candidate implementation commit | Verified GitHub preservation mirror at reconciliation | Tree relationship |
|---|---|---|---|---|
| Watch | `preservation/watch68-partial-source-2026-10-03` at `308ae07bbbcebc423098c35294a0bfe9c7cdf31f` | `8bcc09532a33e4302f7ebc803f97f86200110310` | `Nervahealth/nerva-wearos` same-named branch at `e8d7106fae7290ef68d183a7899b79c6a6588ba9` | Both resolve to tree `aae68e5a03da1ef68c9f07650dce61f62b9e6542` |
| Phone | `preservation/phone-control-and-source-2026-10-03` at `473f9234acee965d4bdb83bdc4ce13517de430a9` | `d70bfb6bd683d26350441399398526c384e861db` | `Nervahealth/nerva-mobile` same-named branch at `7b5be5ee7622ec1ae543e1afe592c57b8adc56a2` | Both resolve to tree `8217ee778d36e20bfd955e406134e2fdcd4419f6` |
| Governed controls | `preservation/watch69-control-records-2026-10-03` at `057de66c5b22e9e03234269e62230ce5b7420889` | n/a — records only | `Nervahealth/nerva-mobile` same-named branch at `4ca8d6af17f44141a3b5d1a2a49b2e50f78cd9b2` | Both resolve to tree `cfc707b24890f83e24979e2b41daceb4d7742b60` |

The local Watch and Phone candidates are each ancestors of their current respective local heads, with four subsequent preservation/documentation commits. The local histories remain the development baseline for later work; the differing GitHub commit IDs are intentional non-force Git database mirrors of the same trees after smart-HTTP pack creation failed. They are external preservation evidence, **not a second implementation fork**. Future work must continue on the named local preservation branches and preserve the corresponding mirror-tree relationship; it must not merge the mirror commits into `main`, force-push, or use a remote mirror commit as a substitute code parent.

### Why the Watch branch is named `watch68-partial-source`

The branch name is inherited from the recoverable Watch68 source-overlay workspace from which the fresh-v2 ordinary-recovery line was restored. It is a repository/workspace provenance label only. It does **not** identify the installed candidate, assert Watch68 runtime continuity, access Watch68 storage, or alter the separate status **Watch68 v1: UNCLASSIFIED / NOT VERIFIED**. Watch72’s candidate source is committed at `8bcc095…` within that continuous preserved history.

### Phone58 installation identity

The ordinary Phone update succeeded, but the operator’s original file-selection field remained the literal `yes/no`. The smallest authorized discriminator is a **read-only Android system App info version check** — not another installation and not an HUGR launch. The expected installed identity is package `health.hugr.app`, version name `2.0.58-recovery-boundary-diagnostic-candidate`, version code `58`. This is weaker than an on-device APK hash but sufficient to resolve whether the installed ordinary HUGR update is Phone58 before any runtime observation.

### Next physical boundary remains separate

No runtime step is authorized by this reconciliation. The proposed bounded pair observation is: confirm Phone58’s version in system App info; launch Watch72 normal Main HUGR once and wait at most 90 seconds for either `Finalized delivery recovery unavailable`, `SERVICE FAILED`, `ADVERTISING FAILED`, or `ADVERTISING READY`; launch Phone58 only after the Watch result and do not press its connection control. Any stop state ends the observation. Only **Watch `ADVERTISING READY` plus a non-black Phone58 first surface** would justify seeking a separate authority for Phone scan/connect/service-discovery and later delivery. No new sensing, recording, egress, acknowledgement, verifier, legacy/frozen-root, Watch58, or Watch68 operation is part of this proposed gate.

The Watch69 five-minute run remains **NOT QUALIFIED**. Watch58 remains exactly `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
