# HUGR NSTAIR — Watch68 Preservation Recovery and Ordinary-Run Route Decision

**Date:** 2026-10-03  
**Scope completed:** Standing preservation/closeout recovery, bounded protected-source provenance check, Android toolchain restoration, recovered-baseline validation, and source-only determination of the installed Watch68 ordinary-run stop/finalisation path.  
**No new Watch candidate, installation, device action, Phone connection, normal BLE delivery, egress activation, transfer, receipt, verifier, or Watch58 storage access occurred in this work.**

## Literal frozen-evidence status

> `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`

This remains the only allowed statement about Watch58. Nothing below independently verifies the artifact, regenerates it, accesses its root, exports it, transfers it, or alters it.

## 1. What was recovered and preserved

### 1.1 Recoverable Watch baseline and toolchain

- Recovered repository: `Nervahealth/nerva-wearos`, baseline `origin/main` commit `0e1c10808635d6a1ac3fb25ff58451e0521bf805`.
- The recovered checkout contains the Gradle 8.5 wrapper, Android Gradle Plugin 8.1.0 configuration, and the normal source/test baseline.
- Restored a clean Android build environment locally:
  - OpenJDK 17.0.20;
  - Android platform `android-34`;
  - Android Build-Tools 34.0.0 (and the build-required 33.0.1);
  - platform-tools.
- `./gradlew testDebugUnitTest --no-daemon` passed against the recovered baseline: **BUILD SUCCESSFUL; 21 Gradle tasks executed**. Warnings are compiler/manifest deprecation warnings only; no test failure occurred.

### 1.2 Source that was actually preserved

The surviving Watch68 partial source/test/configuration mirror was committed locally as an explicitly non-buildable WIP snapshot:

| Item | Status |
|---|---|
| Repository / branch | `Nervahealth/nerva-wearos` / `preservation/watch68-partial-source-2026-10-03` |
| Local commit | `cf89de1a2a73841561eb9aa0679234e7302a8606` |
| Remote confirmation | **NOT CONFIRMED** — push was rejected by GitHub HTTP 403 |
| Durable fallback | Checksummed Git bundle and snapshot archive at `/home/ubuntu/HUGR_SOURCE_PRESERVATION_FALLBACK_2026_10_03/` |

The current Phone source/control mirror was committed and remotely confirmed:

| Item | Status |
|---|---|
| Repository / branch | `Nervahealth/nerva-mobile` / `preservation/phone-control-and-source-2026-10-03` |
| Remote-confirmed commit | `2072731c19dd3a3a5fe5101ba3a2c2428ac6431c` |
| Remote branch | `origin/preservation/phone-control-and-source-2026-10-03` |

The preservation snapshots deliberately exclude credentials, keystores, APKs/build outputs, `.env*`, user-uploaded media, and private physiological data. They make **no validation claim**.

### 1.3 Protected-source provenance

The protected sources have different recovery outcomes:

| Protected source | Required SHA-256 | Outcome |
|---|---:|---|
| `ExactRangeReadback.kt` | `5464fac5cd399fa1627071f139e0724a8135bff5d40a34e9d34fb994c4448ca6` | **Original source remains unrecovered**: absent from active partial tree, all reachable recovered Git refs, authorised organisation search, preserved task history, and non-expired workflow material. The Watch68 APK proves a compiled class existed; it is not an exact source recovery. |
| `RetainedTimingExporter.kt` | `d8239b8ba59367221ff59446fc994b70e2e67835d221785eccc724397ae0fd36` | Present in the surviving source snapshot and previously hash-verified. |
| `RetainedTimingExportActivity.kt` | `96cd6a3c7ae46ab5b1330a36170668e4d91c78d2d8779041ba129403be9de96d` | Present in the surviving source snapshot and previously hash-verified. |

No original source was fabricated, decompiled into a replacement, or reconstructed from memory.

## 2. Dependency result: ordinary recording versus exact readback

**The missing `ExactRangeReadback.kt` is not on the fresh ordinary-recording execution path.**

The normal path is:

```text
MainActivity
  → prepareFreshOrdinaryScopeInBackground()
  → FreshOrdinaryRunScope.fresh_ordinary_source_journal_v1
  → permission gate
  → standard BleGattService + HealthSensorService ACTION_START_TRACKING
  → WatchSourceRuntime.journal(fresh scope)
```

Source inspection found no code reference from `MainActivity`, `HealthSensorService`, `WatchSourceRuntime`, or `FreshOrdinaryRunScope` to `ExactRangeReadback` or `ExactRangeReadbackActivity`.

However, `AndroidManifest.xml` still declares `ExactRangeReadbackActivity` as a launcher activity and assigns its drawable icon. The existing ordinary-runtime contract expects the original readback source hash. That is a **preservation/build-governance blocker**, not an ordinary-run data dependency.

## 3. Current Watch68 lifecycle result

The controlled observation establishes `HEALTH_FOREGROUND_STARTED`, but it does **not** establish active sensor samples, run duration, controlled terminal stop, active-segment finalisation, Phone receipt, or canonical verification.

Source inspection confirms that Watch68 exposes an internal `ACTION_STOP_TRACKING` branch. That branch:

1. disconnects Samsung tracker listeners;
2. stops flush/journal-sync timers;
3. unregisters the screen receiver;
4. releases the wake lock;
5. removes the foreground notification; and
6. stops the `HealthSensorService`.

It does **not** call `SourceJournal.forceSync()`, `SourceJournal.finalizeActiveSegment()`, `SourceJournal.close()`, or a `WatchSourceRuntime` close/reset operation. It also does not stop the separately started standard `BleGattService`.

Therefore:

> The installed Watch68 has no ordinary user-visible stop control and no source-established controlled finalisation route. Sending its internal stop action would stop sensor service activity, but would **not** prove durable finalisation of a bounded recording. Force-stopping the app would be worse: Android does not guarantee service cleanup callbacks, and the next fresh-root recovery may need to repair an orphaned active file.

No device command is issued by this receipt. A possibly active Watch68 ordinary session remains **unclassified and not a verified recording**.

## 4. Concrete reviewable continuation route

The shortest source-supported route to a verified short ordinary recording is a narrowly labelled, temporary **ordinary-only continuation** based on the preserved partial Watch68 line:

1. **Do not recreate or claim `ExactRangeReadback.kt`.**
2. Explicitly remove only the dangling `ExactRangeReadbackActivity` launcher declaration and its icon reference from the candidate manifest, because its source is absent. Do not alter other protected roots, egress code, or the retained exporter.
3. Add a fresh-scope-only lifecycle around the existing ordinary services:
   - explicit bounded run start;
   - elapsed-time bound;
   - durable start, sample-seen, stop-requested, finalisation-success/failure markers;
   - controlled health-service stop;
   - `forceSync()` and `finalizeActiveSegment()` on the **fresh** journal only;
   - standard GATT shutdown after finalisation;
   - unchanged ordinary Phone delivery and canonical verification path.
4. Add preservation contracts proving that this code does not enumerate, open, hash, migrate, acknowledge, prune, delete, or otherwise modify `build45_source_journal` or the completed-package root.
5. Run focused and full Watch suites from the restored toolchain. Only then build one same-package/signer, greater-version candidate with the ordinary-only limitation declared in the admission receipt.

### Consequences of this route

| Surface | Consequence |
|---|---|
| Fresh ordinary recording | Can progress through a bounded, independently sessioned fresh root and existing Phone delivery/verification chain after tests and a new candidate admission. |
| Watch58 / legacy roots | Remain untouched by the fresh scope; the build must explicitly contract-test this. |
| Exact-range readback / independent Watch58 verification | **Unavailable in the temporary ordinary-only candidate.** No claim, transfer, receipt, or verifier can be made from it. |
| Egress | Existing code remains out of the ordinary execution path, but the route does not validate or advance egress. Any later egress work requires the original readback source or a separately authorised, explicitly limited recovery decision. |
| In-place update | Same package/signer, greater-version Android update semantics preserve app-private storage; the candidate must not include migration/deletion code. The removed launcher component merely makes the missing readback function inaccessible, rather than leaving a dangling launcher that can fail. |

## 5. Decision required before implementation

This receipt does **not** silently alter the historic protection contract. It presents the minimum decision:

> **Approve or reject the temporary ordinary-only continuation** that explicitly disables the missing exact-range-readback launcher while preserving all Watch58/legacy bytes, then implements and tests the bounded fresh-run lifecycle for a verified short recording.

A separate small physical decision is also needed for the possibly active installed Watch68 session. The source shows that the only available stop action is not a verified finalisation action; no device command should be sent until a specific stop policy is approved.

## 6. Current state

| Item | State |
|---|---|
| Recovered Watch baseline/toolchain | **GREEN** — baseline JVM suite passed. |
| Surviving partial Watch68 source | **LOCALLY PRESERVED** — remote Watch push blocked by HTTP 403; checksummed local fallback exists. |
| Phone source/control mirror | **REMOTE WIP PRESERVED** on the named branch above. |
| Original `ExactRangeReadback.kt` | **UNRECOVERED**. |
| Existing Watch68 ordinary run | **UNCLASSIFIED / NOT VERIFIED / NO CONTROLLED FINALISATION PATH**. |
| Fresh ordinary recording lifecycle | **NOT IMPLEMENTED**; decision required before the proposed ordinary-only route. |
| Watch58 exact artifact | `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`. |

## 7. No overclaim

This work makes the engineering route shorter and more explicit. It does **not** show that the Watch has collected valid sensor records, that any current run has stopped cleanly, that Phone delivery occurred, that a record verified, or that the original Watch58 artifact has been independently verified.
