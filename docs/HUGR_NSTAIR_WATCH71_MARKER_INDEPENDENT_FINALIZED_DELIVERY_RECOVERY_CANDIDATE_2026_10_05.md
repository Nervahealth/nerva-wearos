# NSTAIR Watch71 Marker-Independent Finalized-Delivery Recovery Candidate

**Date:** 2026-10-05
**Scope:** Source/test correction and signer-continuous Watch candidate admission only.
**Physical state:** Watch70 remains the installed line. Watch71 is **not installed, launched, connected, or device-tested**. No Phone build/change, Watch58 action, egress activation, fresh recording, sensor collection, storage reset, data clear, uninstall, legacy-root operation, or frozen-root operation occurred in this work.

## Current decisive fact

The existing Watch69 five-minute ordinary run remains **NOT QUALIFIED**. It reached its Watch finalization boundary, but Phone final manifest equality, exact acknowledgement and Watch completion were not observed.

The prior Watch70 recovery route wrongly made GATT-only recovery depend on the latest ordinary startup marker still being `BOUNDED_RUN_FINALIZED`. The passive Watch70 observation established that a later normal Main-HUGR launch overwrote that single marker with `FIRST_DRAW_OBSERVED`. That is expected marker-store behavior, but it makes the marker an invalid selector for retained delivery. It does **not** prove that the retained fresh-v2 finalized data were deleted or invalid.

> The historical bounded-run UUID `b561b698-8206-4cb5-8717-95a5c9f533e2` remains lifecycle-marker metadata. It is not re-created, re-used or presented as the Phone source session.

## Minimal correction

Watch71 adds `RetainedFinalizedDeliveryRecovery` and changes ordinary Main-HUGR recovery selection as follows:

1. After first draw, a named background probe opens **only the isolated fresh v2 ordinary journal**.
2. The probe selects the oldest retained, unacknowledged finalized source session and its terminal manifest directly from durable v2 files; it does **not** consult `NormalStartupMarkerStore`.
3. If a target exists, Main starts only standard `BleGattService`, passing the real durable `watchBootSessionId` as `EXTRA_FINALIZED_DELIVERY_SOURCE_SESSION_ID`.
4. GATT validates that that exact session still has retained finalized manifests, then resumes ordinary replay for that source session. It does not start `HealthSensorService`, sensing, permissions, a fresh run, the device-health ticker, egress, or a new source record.
5. The Watch writes `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` only after the Phone's exact session/endpoint/SHA acknowledgement has removed the selected terminal manifest and no finalized v2 manifest remains. It then writes `BOUNDED_RUN_GATT_STOP_REQUESTED` and stops GATT.

This repairs the selector—not the frozen journal. `SourceJournal.kt` is unchanged, SHA-256 `24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361`.

## Tests

### RED → GREEN

The new retained-session regression initially failed to compile because the selector did not exist. The correction then made it GREEN.

The regression simulates all of the following without accessing device data:

- an isolated fresh-v2 session with data and a finalized terminal manifest;
- process-memory loss and journal recreation;
- replacement of the latest ordinary marker by a later `FIRST_DRAW_OBSERVED` marker with a different ID;
- durable recovery selection of the actual retained `watchBootSessionId`, terminal endpoint and SHA;
- exact acknowledgement and deletion only for that manifest.

Source contracts additionally require the probe to run after first draw on a background thread, disallow marker-store consultation in the probe, require an explicit GATT-only source-session extra, forbid Health service/fresh-run startup, and require the completion marker to follow exact acknowledgement and retained-manifest removal.

| Validation | Result |
|---|---:|
| New targeted retained-finalized/process-restart/marker-overwrite regression | GREEN |
| Focused journal, startup-gate, fresh-root, marker and ordinary-runtime contracts | GREEN |
| Full Watch JVM `:app:testDebugUnitTest --rerun-tasks --no-daemon` | **143 passed, 0 failures, 0 errors, 0 skipped** |
| Diff whitespace check | GREEN |
| `SourceJournal.kt` preservation hash | GREEN |

These are source/JVM proofs only. They do not establish Android service lifecycle, Bluetooth connection, Phone57 runtime acknowledgement, or Watch hardware completion.

## Watch71 candidate admission

| Property | Verified value |
|---|---|
| Package | `com.hugr.wearos` |
| Version | code `71`; `0.71.0-marker-independent-delivery-recovery-candidate` |
| Artifact SHA-256 | `4c5e6538c1abbd029f79bd300f503aaf201a003e6628d2a953ce567d1a1e47aa` |
| Signer | Continuing signer SHA-256 `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` (one v3 signer) |
| ZIP/DEX | ZIP alignment verified; 8,102 descriptors; 0 duplicates |
| Retained egress compatibility payload | `classes5.dex`, SHA-256 `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784`, byte-identical to admitted Watch70 payload |
| Candidate file | `HUGR_Watch71w_0.71.0_marker-independent-delivery-recovery-candidate.apk` |
| Download | https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/WEsqoxYqOguJBZFP.apk |
| Checksum sidecar | https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/mdXHDSOWljQEhUSp.sha256 |

The binary payload is retained **compatibility-only** because its original source is unrecovered. It is unchanged and excluded from the ordinary recovery path; its presence is not egress recovery or egress validation.

## Concise future recovery procedure — requires separate physical authority

This is the shortest proposed one-attempt procedure. It is not authorization to perform it.

### A. Installation-only boundary

1. Select only the exact Watch71 APK above.
2. Continue only if the Watch installer shows an ordinary **HUGR update**; stop for uninstall, clear/reset, downgrade, package/signer mismatch, warning or an unexpected installer surface.
3. Stop at the installer result. Do not open Watch or Phone HUGR as part of installation.

### B. One retained-session recovery attempt

Precondition: ordinary system Bluetooth/companion status already shows the Watch connected to the Phone57 Honor phone. This is system pairing only, not a HUGR action.

1. Tap normal **Main HUGR** on Watch exactly once. Do not use Evidence Egress, Startup Readiness, or any recording control.
2. Wait up to **90 seconds** for the background retained-v2 probe and standard-GATT-only recovery launch. Do not relaunch or tap again. No new sensing or recording should appear.
3. Open normal Phone57 HUGR, enter its existing Full surface if required, and press ordinary **Connect Galaxy Watch** exactly once.
4. Keep both devices together and the Phone app foregrounded for at most **5 minutes**. Do not background/relaunch either HUGR app, press any egress/readback control, start a run, or retry.
5. Capture the first terminal Phone delivery state and then open Watch **Startup Readiness** once, only after Phone becomes terminal or the five-minute bound expires.

### Required success evidence

All of these must be captured before the Watch69 run can be called qualified:

- Phone identifies the actual retained source session (`watchBootSessionId`), final range/endpoint and terminal manifest SHA, with canonical equality verification and exact acknowledgement. It must not use the old bounded-run UUID as the source-session identity.
- The acknowledgement corresponds to that exact manifest; equal record indices alone are insufficient.
- Watch Startup Readiness shows `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, followed by `BOUNDED_RUN_GATT_STOP_REQUESTED`, for the actual retained source session.
- No new sensor records, fresh run, egress, legacy/frozen-root action, data-clear, uninstall, or retry occurred.

### Stop conditions

Stop and preserve the first screen if any of these occurs: no terminal Phone acknowledgement by five minutes; source/transport stall; manifest/session/range/hash mismatch; any new live sensor/device-health records; a new recording or permission prompt; egress/readback surface; Watch/Phone crash or unexpected warning; or a second connection/retry demand.

## Boundaries retained

- Watch58 remains exactly **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**.
- Legacy/frozen roots and the unclassified Watch68 session are untouched and remain unfinalized/unverified by this work.
- No historical bounded-run UUID is reconstructed.
- The complete Watch69 run is still unqualified until the separate physical recovery attempt earns all success evidence above.


## Preservation closeout — GitHub push blocked; fallback retained

Before publication, each proposed GitHub branch was fetched and confirmed fast-forwardable from its hosted head. The Watch-source non-force push to `Nervahealth/nerva-wearos` branch `preservation/watch69-bounded-ordinary-run-2026-10-03` then returned **HTTP 403** (`Permission to Nervahealth/nerva-wearos.git denied to Nervahealth`). The Phone/control branches were intentionally not attempted after this first failure, so no remote-advance claim is made for any Watch71 commit.

The candidate-source and governed-control deltas below were committed before this closeout-record amendment; the later local documentation-only commit is preserved in the final fallback patch history.

| Scope | Candidate/control commit | Hosted baseline before failed push |
|---|---|---|
| Watch source/candidate | final `055de4704d850c31438df03b022eab90d3514808` (the failed push attempted pre-amend `eaea4ad5772e5455dcb10cbfc78e49e946ce91ba`) | `a6f77371981ab33d5b9d73571f68fca4dd798c4e` |
| Phone control copies | `359617ca0c9a69967775ac44e2eaf9a708fa2238` | `8ae684a5ad2997cb5486942e60c27cf336fbe8f6` |
| Independent control record | `e6ec2555117bd544e10e56b15f8dd8899368cc93` | `6b4d8d382e7454888c3020f18f3c45427debc24e` |

A sanitized external fallback archive preserves the exact git-format deltas, current receipt/control records and APK checksum sidecar while excluding Git metadata, signing keys/keystores, credentials, private physiological data, device media and APK binaries:

- Final archive: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/oyIlBsaQnbDIpumU.gz
- Final archive SHA-256: `f0e9aeb9f8934c71bb5139d09215d3d9a9964efd8d8e704736939297cf06bee3`
- Final checksum sidecar: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/qSCpPBBOePHpiXXK.sha256

This fallback remains retained. It is not a GitHub remote verification, artifact-device verification or qualification result.
