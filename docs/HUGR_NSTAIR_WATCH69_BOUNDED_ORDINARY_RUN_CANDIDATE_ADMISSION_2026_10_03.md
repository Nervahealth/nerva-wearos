# HUGR NSTAIR — Watch69 Bounded Ordinary-Run Candidate: Source, Test, and Artifact Admission

**Date:** 2026-10-03  
**Scope completed:** Authorised temporary ordinary-only source recovery, bounded fresh-session lifecycle implementation, source/test validation, candidate build, signer/package admission, and preservation preparation.  
**Physical operations:** None. No Watch or Phone installation, launch, connection, sensing, recording, delivery, acknowledgement, verification, egress action, transfer, or Watch58 storage action occurred in this work.

## Result

> **WATCH69 TEMPORARY ORDINARY-ONLY CANDIDATE — SOURCE / TEST / ARTIFACT ADMISSION GREEN.**

One signed Watch69 candidate is ready for a separately authorised installation-only decision. It is deliberately narrow: it can create one five-minute ordinary recording attempt in a **new v2 fresh scope**, then finalise and offer that fresh session through the existing standard GATT replay/acknowledgement path. It does not reopen the unclassified Watch68 v1 session, the legacy journal, or the frozen completed-package root.

This is **not** a recording result. No actual sensor sample, finalised session, Phone receipt, canonical verification, Gate-1 result, eight-hour run, egress transfer, or independent Watch58 verification has been earned.

## Frozen evidence status

> `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`

Watch58 is unchanged. This candidate does not enumerate, open, decode, hash, migrate, acknowledge, prune, export, delete, regenerate, or otherwise access the frozen completed-package root.

## Why the v2 fresh scope was necessary

The installed Watch68 line reached `HEALTH_FOREGROUND_STARTED`, but did not establish a controlled stop or finalisation. Its potential fresh v1 session is therefore **unclassified and not a verified recording**. A new candidate must not reuse that scope.

Watch69 consequently uses only these new sibling directories:

```text
fresh_ordinary_source_journal_v2
fresh_ordinary_causal_flight_recorder_v2
```

The source contracts assert that neither v2 directory aliases `fresh_ordinary_*_v1`, `build45_source_journal`, `build47_causal_flight_recorder`, or `exact_range_readbacks`. Fresh-root fixture tests preserve before/after digests of legacy and frozen-package fixtures while a v2 journal is created and appended.

## Implemented bounded lifecycle

The normal Main HUGR route still requires the existing fresh-scope readiness and permission gate. Once admitted, it starts standard `BleGattService` and sends `ACTION_START_BOUNDED_ORDINARY_RUN` to `HealthSensorService` with the fixed policy duration:

```text
5 minutes exactly (300,000 ms)
```

The candidate does not expose an open-ended ordinary collection action. Its run gate has five states:

```text
IDLE → ACCEPTING_SAMPLES → QUIESCING → FINALIZED
                                  └──→ FINALIZATION_FAILED
```

At the timed boundary the service performs the following order under source contracts:

1. Stops admitting sample callbacks.
2. Disconnects tracking and stops producer-side timers/receivers.
3. Releases the wake lock.
4. Serializes fresh-journal `forceSync()`, `finalizeActiveSegment()`, and a second `forceSync()`.
5. Records `BOUNDED_RUN_FINALIZED` only on success, then broadcasts `ACTION_SOURCE_FINALIZED`.
6. Leaves standard GATT alive only for the existing manifest/replay/acknowledgement route.
7. After the Phone acknowledges all finalised source manifests, records `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, closes only the cached v2 fresh runtime, records `BOUNDED_RUN_GATT_STOP_REQUESTED`, and stops the standard GATT service.

Late sensor callbacks are gated before journal append. A callback cannot append after the run enters `QUIESCING`. A failed finalisation records `BOUNDED_RUN_FINALIZATION_FAILED` and stops the health service without claiming a finalised session.

This establishes a tested lifecycle design. It does **not** prove the Samsung tracker will produce records on hardware, that a Phone will connect, or that the existing Phone verification path will complete.

## Missing readback source and temporary ordinary-only limitation

The original source `ExactRangeReadback.kt` remains unrecovered. It was not recreated or decompiled into a substitute. Its dangling launcher declaration was removed from the Watch69 manifest, so this candidate deliberately has **no Exact Range Readback launcher or compiled readback class**.

The two surviving protected sources remain exact:

| Protected source | Required SHA-256 | Watch69 result |
|---|---|---|
| `RetainedTimingExporter.kt` | `d8239b8ba59367221ff59446fc994b70e2e67835d221785eccc724397ae0fd36` | Exact |
| `RetainedTimingExportActivity.kt` | `96cd6a3c7ae46ab5b1330a36170668e4d91c78d2d8779041ba129403be9de96d` | Exact |
| `SourceJournal.kt` | `24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361` | Exact |

The surviving compiled Watch68 egress payload is retained as a hash-pinned binary compatibility payload solely to keep the pre-existing dedicated egress component loadable. It is injected unchanged as `classes5.dex` with SHA-256 `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784`. It defines 76 egress classes, including `EvidenceEgressActivity`, with no duplicate class descriptors in the candidate. The ordinary bounded lifecycle does not call egress activation and does not advance egress. This binary compatibility measure is not a recovery of missing source and is not egress validation.

## Test evidence

The restored Java 17 / Android 34 toolchain ran the complete Watch JVM suite after the final v2 isolation correction:

| Validation | Result |
|---|---:|
| Focused v2 scope, lifecycle, and gate contracts | PASS |
| Full `testDebugUnitTest --rerun-tasks --no-daemon` | **135 passed; 0 failures; 0 errors; 0 skipped** |
| Bounded lifecycle ordering contract | PASS |
| Fresh-root fixture preservation contract | PASS |
| Ordinary versus egress source-boundary contract | PASS |
| Candidate APK signature verification | PASS |
| Candidate DEX duplicate-class check | **0 duplicates** |

Compiler deprecation warnings were present but no build or test error occurred.

## Candidate identity and admission

| Field | Verified value |
|---|---|
| Artifact | `HUGR_Watch69w_0.69.0_bounded-ordinary-run-candidate.apk` |
| SHA-256 | `3c16883ea0d90d09de30a21f7e1a9e051737a932a321d79cb341e58b9787f392` |
| Package | `com.hugr.wearos` |
| Version | code `69`; `0.69.0-bounded-ordinary-run-candidate` |
| Predecessor | Watch68 code `68` |
| Signer SHA-256 | `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` |
| Signature verification | one signer; APK Signature Scheme v3 verifies |
| DEX layout | `classes.dex` through `classes5.dex` |
| Candidate source root | `/home/ubuntu/HUGR_WATCH69_ORDINARY_ONLY_2026_10_03/worktree` |

The earlier Watch69 v1 artifact was never admitted or installed. It is retained only in `artifacts/superseded-v1-not-admitted/` as a superseded local build record. The sole artifact above is the v2 candidate for any future installation decision.

Same package, signer, and greater version code are necessary Android in-place-update conditions. They do not prove device-side preservation. The candidate contains no migration, deletion, re-encoding, pruning, acknowledgement, or frozen-root operation.

## Durable source preservation

The exact source/test worktree used for this admission is committed at `9be88b5c827dd6724f2504c8efc9a0555faae70c`. GitHub device authorization was refreshed for the existing `Nervahealth` account after a prior Git HTTPS 403. A dry-run then passed and the exact commit was pushed and independently resolved on `Nervahealth/nerva-wearos` branch [`preservation/watch69-bounded-ordinary-run-2026-10-03`](https://github.com/Nervahealth/nerva-wearos/tree/preservation/watch69-bounded-ordinary-run-2026-10-03). The source is therefore **remote-Git-confirmed**; it is preserved on a dedicated branch, not merged into `main`.

An external preservation archive was first prepared, then rejected during its own filename exclusion audit because it contained `debug.keystore`. It is **not** an accepted preservation reference and its URL is deliberately omitted here. The replacement archive below was rebuilt from source/configuration only and passed the exclusion scan for signing keys, keystores, local settings, environment configuration and common credential-file names. It contains reviewed source, tests, Gradle/manifest configuration and commit metadata, but excludes version-control history, application packages/build outputs, signing material, credentials, user-uploaded media and private physiological data.

| Accepted external preservation item | SHA-256 | Location |
|---|---|---|
| Sanitized source archive | `1b053c94de98be90d93c12c24feee64aba6b2f359f1a91f8ed0b324c067327a6` | [sanitized tar.gz archive](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/PFRcEcHqZipqpJom.gz) |
| Sanitized source integrity manifest | — | [SHA256SUMS](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/rXyoulVttoZkhJrs.txt) |
| Closeout control-record snapshot | `6a2765e7bdc79c87593ee537d52976e4f9b3aadfa954ffe829714dd9725b7493` | [record archive](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/oScBWBnxnLebGIoI.gz) / [record checksums](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/XYfUDiJYRCjaOarw.txt) |

The control-record snapshot was made after the preservation correction and before this self-referential link was inserted; it preserves the source receipt, Master Task List and Alignment Surface at that closeout state. GitHub branch preservation now provides the primary durable source record; the sanitized external archive and control snapshot remain independent fallback copies. None of these preservation actions makes a source-validation, device, recording, delivery, verification, or Watch58 claim.

## Current Watch68 session policy

No command should be sent to stop or force-stop the current Watch68 runtime merely to manufacture a terminal result. Its internal stop path does not establish `forceSync()`, active-segment finalisation, journal closure, or standard-GATT shutdown. It remains **unclassified / not verified**.

A future Watch69 installation, if separately authorised, must be treated only as an in-place version update. It must not be described as a controlled end, finalisation, receipt, or verification of any Watch68 v1 session. Watch69's v2 scope avoids reopening that session.

## Next physical decision — not included here

The minimum next device decision is an **installation-only** Watch68 → Watch69 pass. It must select this exact v2 artifact, continue only on an ordinary same-app update surface, stop at the installer result, and not open HUGR.

Only after a separately authorised runtime-and-Phone procedure may a five-minute Watch69 bounded run be assessed. Its success criterion must include all of the following: `BOUNDED_RUN_STARTED`, evidence of actual source records, `BOUNDED_RUN_FINALIZED`, Phone manifest receipt, canonical verification, and final acknowledgement. Any missing stage, unexpected prompt, crash, warning, connection failure, finalisation failure, mismatched manifest, or unverified Phone state is a STOP, not a successful recording.

## References

[1]: file:///home/ubuntu/HUGR_NSTAIR_WATCH68_PRESERVATION_RECOVERY_AND_ORDINARY_ROUTE_DECISION_2026_10_03.md "Watch68 preservation recovery and ordinary-run route decision"
[2]: file:///home/ubuntu/HUGR_NSTAIR_WATCH68_NORMAL_START_HEALTH_FOREGROUND_MARKER_OBSERVATION_2026_10_03.md "Watch68 health foreground marker observation"
[3]: file:///home/ubuntu/HUGR_WATCH69_ORDINARY_ONLY_2026_10_03/worktree/app/src/main/java/com/hugr/wearos/BoundedOrdinaryRunGate.kt "Watch69 bounded ordinary run gate"
[4]: file:///home/ubuntu/HUGR_WATCH69_ORDINARY_ONLY_2026_10_03/worktree/app/src/main/java/com/hugr/wearos/HealthSensorService.kt "Watch69 bounded health service lifecycle"
[5]: file:///home/ubuntu/HUGR_WATCH69_BOUNDED_ORDINARY_RUN_CANDIDATE_2026_10_03/artifacts/HUGR_Watch69w_0.69.0_bounded-ordinary-run-candidate.apk "Watch69 signed candidate artifact"
