# HUGR NSTAIR — Watch70 Artifact and Startup-Marker Lifecycle Assessment

**Date:** 2026-10-05
**Scope:** Source/test-only assessment after the single stopped Watch70 Main-HUGR launch. No build, source correction, installation, retry, Phone57 HUGR action, recording, egress/readback/export, verifier, ADB/Termux, legacy/frozen-root, Watch58, or Watch68 action.

> **NO DIAGNOSTIC BUILD WARRANTED YET.** The existing signed Watch70 admission artifact contains the recovery branch. The stopped generic screen is therefore narrowed to the state present at branch selection versus installed-runtime identity; it is not explained by a later GATT/Phone/Bluetooth wait.

## 1. Exact artifact identity and installation evidence boundary

| Item | Result |
|---|---|
| **Only admitted/installable Watch70 artifact** | `HUGR_Watch70w_0.70.0_finalized-delivery-recovery-candidate.apk` |
| SHA-256 | `4ecd3ed78e233ba657ffae073d7a3f5798a2ce39ee8b1e89cacd1822614c52b1` |
| Package / version | `com.hugr.wearos` / code `70`, `0.70.0-finalized-delivery-recovery-candidate` |
| Signature | Valid one-signer APK Signature Scheme v3; continuing signer SHA-256 `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` |
| DEX confirmation | The signed APK contains the `BOUNDED_RUN_FINALIZED` selector, shared-preference name `hugr_normal_startup_markers_v1`, and both recovery texts: `Finalized delivery recovery · standard GATT only` and `… waiting for Phone resume`. |
| Source admission commit | `ae55f5bd4657e0c23dd03fd3095c16feb05a71c6` |
| Operator installation observation | Exact candidate selected; ordinary HUGR update; literal installer result `succness`; HUGR Watch and Phone reported unopened. This is **not** independent device package/hash inspection. |

The other local Watch70-named file is explicitly **not an install candidate**:

| File | SHA-256 | Status |
|---|---|---|
| `HUGR_Watch70w_0.70.0_finalized-delivery-recovery-candidate-unsigned-compat.apk` | `4e84db27704daa0fb4b0a0daca80f1f037c0593638e775b9c7a2e9e93d591dc7` | Unsigned compatibility packaging intermediate; `apksigner verify` fails (`Missing META-INF/MANIFEST.MF`). It was not admitted, linked, or eligible for installation. |

## 2. What the Watch70 first frame actually decides

Watch70 reads `NormalStartupMarkerStore` inside `MainActivity.onCreate()` **before** it schedules a first draw, requests permissions, starts GATT, starts Health, waits for Bluetooth, or writes a new ordinary-start marker.

- If `lastStage == BOUNDED_RUN_FINALIZED`, it synchronously replaces the generic layout text with **`Finalized delivery recovery · standard GATT only`**, then schedules the standard-GATT-only recovery continuation.
- Otherwise, it enters the ordinary branch and synchronously writes `UI_REACHED` with a new normal-startup run ID, then later attempts `FIRST_DRAW_OBSERVED` and ordinary fresh-scope stages.

The observed generic `Local startup check pending` screen persisted after several minutes. Given the verified signed Watch70 artifact, that screen means the finalized-recovery condition was **not selected at `onCreate()`**. It rules out a mere post-draw/GATT/Phone/Bluetooth delay as the explanation for the missing recovery headline.

The observation cannot by itself distinguish these two remaining alternatives:

1. the stored marker was missing or had a stage other than `BOUNDED_RUN_FINALIZED` when Watch70 read it; or
2. the installed Watch runtime was not the admitted code-70 artifact despite the installation-only operator report.

The stopped generic Main-HUGR launch itself may now have replaced the prior marker with a normal-startup stage. The marker store retains **one latest record only**, not a stage history, so no existing passive screen can reconstruct an earlier overwritten value or identify exactly which earlier writer made it.

## 3. Complete direct marker-writer trace

`NormalStartupMarkerStore` is a small internal `SharedPreferences` record with three values: `run_id`, `stage`, and `recorded_at_epoch_millis`. It is separate from the fresh source journal and has no journal/file operations. Its preference name and keys are byte-for-byte source-identical between Watch69 and Watch70:

```text
hugr_normal_startup_markers_v1
run_id
stage
recorded_at_epoch_millis
```

There are only three direct writers in the ordinary Watch source:

| Writer | Stages it can write | Could it overwrite `BOUNDED_RUN_FINALIZED` after the Watch69 run? | Relevant observed/intervening status |
|---|---|---|---|
| `MainActivity` | `UI_REACHED`, `FIRST_DRAW_OBSERVED`, fresh-scope, permission, and service-start stages | **Yes**, whenever normal Main HUGR starts without taking Watch70's recovery branch. | The documented Watch69→Watch70 sequence contains no known normal Main-HUGR launch before the stopped Watch70 attempt. The stopped Watch70 generic launch is itself such a writer if it reached the ordinary branch. |
| `HealthSensorService` | Health entry/foreground, bounded start/sample/stop/finalization/failure stages | **Yes**, but only if that Health service is explicitly started/restarted. | Watch69 finalization wrote `BOUNDED_RUN_FINALIZED`, then explicitly called `stopSelf()`. No boot/package receiver exists in the manifest, no system-Bluetooth receiver starts it, and no intervening Health action was reported. |
| `BleGattService` | `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, then `BOUNDED_RUN_GATT_STOP_REQUESTED` | **Yes**, but only after the exact source-session/endpoint/SHA acknowledgement deletes the last finalized manifest. | The bounded Phone57 recovery showed no terminal manifest, exact acknowledgement, or completion marker; no intervening GATT completion is evidenced. |

The following do **not** write this marker in source:

- **HUGR Startup Readiness:** reads only the small marker and starts no service, BLE, sensor, journal, acknowledgement, cleanup, permission, export, or payload path.
- **Watch70 ordinary in-place update itself:** executes no HUGR code according to the operator report; same-package/signer updates preserve app data under Android’s standard update route, and Watch69/Watch70 use the unchanged preference name/keys.
- **Phone57 normal resume / Phone Bluetooth state:** no Phone action occurred in the stopped pass. In the earlier Phone57 resume attempt, standard GATT could only have written a terminal marker after the missing exact acknowledgement.
- **System Bluetooth association/restoration:** no manifest receiver or component path starts Main HUGR or HealthSensorService on Bluetooth association, boot, package replacement, or lock state. The later system connection restoration occurred after the stopped Watch70 launch and cannot explain its branch selection.
- **Egress:** has no direct normal-startup-marker writer and was not activated.

## 4. Known chronology and what it supports

1. On 2026-10-03, the Watch69 short run visibly reached `runId=b561b698-8206-4cb5-8717-95a5c9f533e2`, `lastStage=BOUNDED_RUN_FINALIZED`, epoch `1791056794547` (`2026-10-03T19:46:34.547Z`).
2. The 2026-10-04 Phone57 recovery procedure expressly had **no Watch Main-HUGR launch**. Its observation did not obtain terminal Phone acknowledgement or a Watch completion marker.
3. Watch70 installation was reported as an ordinary in-place update with no HUGR app opening, reset, data clear, or warning.
4. The documented first Watch70 Main-HUGR launch showed the generic ordinary first frame, so it did not visibly select recovery. It may therefore have overwritten the one-record marker after evaluating it.
5. System Bluetooth restoration followed that stop and had no HUGR app/control use.

**Conclusion:** on the documented evidence, there is **no established intervening action before the Watch70 launch** that wrote a non-finalized stage. The source nevertheless proves that such an overwrite is possible through an unrecorded normal Main-HUGR/Health start. The one-record store cannot retrospectively resolve that historical question. It would be incorrect to blame Bluetooth, to declare a source defect, or to build again without first checking the surviving current identity/marker state.

## 5. Smallest existing, non-destructive discriminator

No new app, build, device shell, private-root access, or artifact change is needed. The smallest existing check is two passive observations, performed **only under separate physical authorization** and with no normal Main-HUGR relaunch:

1. **Watch system app-details check:** read the ordinary HUGR version in Watch Settings/App list. Record the displayed version as `0.70.0` / code `70` if shown. This is an identity check only; it does not independently prove the installed APK SHA-256.
2. **Open `HUGR Startup Readiness` once:** capture all three lines exactly:

   ```text
   runId=...
   lastStage=...
   stageEpochMs=...
   ```

   This launcher is source-proven read-only for the marker metadata and does not launch Main HUGR, GATT, Health, sensors, permissions, egress, journal traversal, acknowledgement, or cleanup.

Interpretation is deliberately bounded:

| Passive result | What it distinguishes | What it cannot establish |
|---|---|---|
| Settings shows **not code 70** | Installation/runtime identity problem; do not diagnose marker logic or rebuild. | Exact byte hash or why the wrong version is present. |
| Code 70 plus `runId=b561…`, `lastStage=BOUNDED_RUN_FINALIZED` | The retained marker survives; the prior generic screen contradicts the admitted branch and warrants source/runtime investigation before any recovery attempt. | Android runtime cause or qualified delivery. |
| Code 70 plus a different run ID and an ordinary stage such as `UI_REACHED`, `FIRST_DRAW_OBSERVED`, or fresh-scope stage | The normal branch ran and replaced the one-record marker; this is consistent with the selector seeing a non-finalized/missing marker at the stopped launch. | Which earlier action first overwrote the historical finalized marker. |
| Code 70 plus `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` or `BOUNDED_RUN_GATT_STOP_REQUESTED` | A terminal marker exists and needs separate evidence reconciliation; do not assume qualification. | Phone final equality/acknowledgement, canonical custody, or delivery success. |
| No marker / malformed marker | Marker state is unavailable; do not infer journal loss or build a fix from it. | Why it is absent or whether the retained manifest still exists. |

Because the existing store is latest-state-only, this two-view check is the smallest legitimate discriminator. It can choose the next engineering question; it cannot recreate the pre-launch timeline.

## 6. Source-test verification

No implementation was changed. The focused existing marker/recovery contracts were rerun from source:

| Test command | Result |
|---|---:|
| `:app:testDebugUnitTest --tests NormalStartupMarkersTest --tests OrdinaryRuntimeCandidateContractTest --rerun-tasks --no-daemon` | **10 passed; 0 failures; 0 errors; 0 skipped** |

These contracts establish marker metadata isolation, passive Startup Readiness behavior, recovery-selector ordering before ordinary marker writes, standard-GATT-only recovery isolation, and exact-acknowledgement completion ordering. They are not Android hardware/runtime proof.

## 7. No-action result and preservation boundaries

No diagnostic candidate is proposed. No current physical operation is authorized by this assessment.

- Watch69 run `b561b698-8206-4cb5-8717-95a5c9f533e2` remains **NOT QUALIFIED**.
- Watch58 remains exactly `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
- Watch68 v1 remains **UNCLASSIFIED / NOT VERIFIED**.
- The retained egress binary remains compatibility-only; no egress validation, activation, transfer, or verification occurred.
- The earlier Watch70 pre-Phone stop receipt is superseded only in its narrow suggestion that a post-draw continuation delay remained an equal explanation. This assessment corrects that: with the admitted artifact, the generic screen is decided before first-draw/GATT continuation.
