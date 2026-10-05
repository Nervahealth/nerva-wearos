# HUGR NSTAIR — Watch70 Finalized-Delivery Recovery Candidate: Admission and Existing-Run Procedure

**Date:** 2026-10-05
**Scope:** Watch70 source correction, tests, same-package/signer candidate build, and artifact admission only.
**Device status:** No Watch or Phone installation, launch, connection, new recording, egress action, or Watch58/legacy-storage action occurred in this work.

> **WATCH70 FINALIZED-DELIVERY RECOVERY CANDIDATE — SOURCE / TEST / ARTIFACT ADMISSION GREEN.**
>
> Watch70 is a narrowly scoped in-place successor to the installed Watch69 line. It is designed to resume delivery of the already-finalized Watch69 fresh-session data **without starting another recording**. It does not qualify the existing run by itself. Qualification still requires a later separately authorized physical recovery attempt to obtain Phone canonical manifest equality, exact acknowledgement, and the Watch completion marker.

## 1. Starting evidence and reason for this candidate

The sole Watch69 five-minute run reached durable Watch marker `BOUNDED_RUN_FINALIZED` for bounded marker run ID `b561b698-8206-4cb5-8717-95a5c9f533e2` at `2026-10-03T19:46:34.547Z`. It did **not** earn final Phone manifest equality/acknowledgement or Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`; therefore it remains **NOT QUALIFIED**.

A later one-Phone57 reconnect/resume observation was bounded and stopped without a final result. It showed the Phone still presenting pre-final `Manifest equality: ACK 1–11 n=11 bytes=917` while source activity reached `Device health #8183 live`; no attributable terminal-manifest acknowledgement or Watch delivery-completion marker was observed.

Source/test work then established two delivery defects:

1. a terminal manifest finalized after the frozen resume high-water could be dropped from the in-memory new-manifest signal; and
2. if the Phone had already durably appended the terminal records but had not yet received its terminal manifest, the normal `>` selection excluded an endpoint equal to the Phone's durable contiguous index. That prevented manifest/hash verification and exact acknowledgement even though no data replay was needed.

The already remote-preserved Watch69 corrections handle both finalization-window expansion and equality-only manifest queueing. Watch70 adds the necessary **post-update/process-loss entry route**: it restarts standard GATT for that retained finalized session without unintentionally starting sensing or creating further source records.

## 2. Corrected post-update recovery route

### 2.1 Durable selector and no-new-recording boundary

At normal Main-HUGR startup, Watch70 reads the small `NormalStartupMarkerStore` before creating a new ordinary startup marker. If the preserved marker stage is exactly `BOUNDED_RUN_FINALIZED`, it enters `finalizedDeliveryRecoveryOnly`:

1. Main HUGR renders a passive status: **“Finalized delivery recovery · standard GATT only.”**
2. After first draw it starts `BleGattService` with `EXTRA_FINALIZED_DELIVERY_RECOVERY_ONLY=true`.
3. `BleGattService` starts in existing **STANDARD** GATT mode only; it does **not** start `HealthSensorService`, request an ordinary bounded run, request permissions, enter a foreground sensor service, register sensor/health broadcast receivers, schedule the device-health ticker, or activate egress.
4. The existing fresh v2 `SourceJournal` is reopened from its durable files. Normal `prepareSourceReplay()` chooses `oldestFinalizedSessionId()` first, so the retained finalized Watch69 session is selected in preference to a currently open session.
5. The Phone's normal resume request supplies its stored source-session ID and durable contiguous record index. The normal replay path then queues either missing data plus its manifest, or—where endpoints are equal—the retained terminal manifest alone. It does **not** replay duplicate records in the equality-only case.
6. Phone-side pre-existing logic still must durable-append/locate all records, verify the final manifest range and SHA-256 equality against canonical records, then write the exact acknowledgement.
7. Watch-side acknowledgement validation still requires the matching source-session ID and queued manifest endpoint. Only after `acknowledgeCompletedSegment(...)` has removed the exact finalized manifest does `closeBoundedFreshRuntimeAfterDeliveryIfComplete()` record `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, close the v2 fresh runtime, record `BOUNDED_RUN_GATT_STOP_REQUESTED`, and stop GATT.

The `finalizedDeliveryRecoveryOnly` guard returns before `notifyDeviceHealth()` can append a new device-health source record. That prevents the retained endpoint from changing during recovery.

### 2.2 Process-memory loss test boundary

The regression explicitly closes and recreates `SourceJournal` around a finalized retained segment. After recreation it proves:

- the same durable source session is found as the oldest finalized session;
- its manifest is selected when the Phone index equals its endpoint;
- `readRecordsAfter(equalEndpoint, equalEndpoint)` is empty, so equality recovery sends no duplicate source record;
- exact session/endpoint/SHA acknowledgement deletes the finalized manifest once.

This is a source/JVM simulation of process-memory loss and durable journal reopening. It is **not** a claim that Android process death, Bluetooth connection, or Phone delivery has yet succeeded on hardware.

## 3. Bounded marker run ID versus Phone source-session ID

These are deliberately different identities:

| Identity | Meaning | Storage / wire role | Relation |
|---|---|---|---|
| `b561b698-8206-4cb5-8717-95a5c9f533e2` | Watch69 **bounded marker run ID** | `NormalStartupMarkerStore` metadata used to correlate lifecycle markers for the controlled five-minute run | It is a marker correlation ID. It is not used as the source-manifest/acknowledgement session identity. |
| `watchBootSessionId` | Watch **source-session UUID** | Durable fresh `SourceJournal` session metadata; embedded in every source record, manifest, resume request, and acknowledgement | This is the identity the Phone must return in its resume request and exact acknowledgement. It is created/persisted by the source journal and is not derived from the bounded marker run ID. |

The normal Phone UI is not a full raw-manifest inspector. A later physical recovery must retain the visible `Manifest equality` final range/count/hash evidence and terminal acknowledgement status; the underlying durable Phone receipt is the authoritative binding of source-session UUID, range and full hash. No source change in this candidate fabricates or substitutes either identity.

## 4. Tests and artifact checks

| Check | Result |
|---|---:|
| Initial new recovery-route contract | RED before implementation |
| Focused process-loss/replay/lifecycle contracts | PASS |
| Full Watch JVM suite (`testDebugUnitTest --rerun-tasks --no-daemon`) | **142 passed; 0 failures; 0 errors; 0 skipped** |
| Retained terminal manifest after journal recreation | PASS |
| Equal-endpoint manifest-only selection / no duplicate data replay | PASS |
| Wrong-session, backwards, endpoint-mismatched, repeated-resume and repeated-ack rejection/handling | PASS in existing and strengthened replay regressions |
| Completion ordering: exact acknowledgement before delivery marker/GATT stop | PASS by source contract |
| Standard-vs-egress isolation / retained binary payload contract | PASS |
| Protected `RetainedTimingExporter.kt` SHA-256 | `d8239b8ba59367221ff59446fc994b70e2e67835d221785eccc724397ae0fd36` |
| Protected `RetainedTimingExportActivity.kt` SHA-256 | `96cd6a3c7ae46ab5b1330a36170668e4d91c78d2d8779041ba129403be9de96d` |
| Protected `SourceJournal.kt` SHA-256 | `24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361` |
| APK ZIP / 4-byte alignment | PASS |
| APK signature | One signer; v3 verifies; continuing signer SHA-256 `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` |
| DEX descriptors | 8,098 descriptors; **0 duplicates** |
| Retained compatibility `classes5.dex` SHA-256 | `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784` (unchanged) |

Compiler warnings are unchanged deprecation/static-analysis warnings; no build or test error occurred.

## 5. Candidate identity

| Field | Verified value |
|---|---|
| Candidate | `HUGR_Watch70w_0.70.0_finalized-delivery-recovery-candidate.apk` |
| SHA-256 | `4ecd3ed78e233ba657ffae073d7a3f5798a2ce39ee8b1e89cacd1822614c52b1` |
| Package | `com.hugr.wearos` |
| Version | code **70**; `0.70.0-finalized-delivery-recovery-candidate` |
| Required installed predecessor | Watch69, code **69** |
| Signer | continuing `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` |
| Android API | min 30; target 34; compile 34 |
| DEX layout | `classes.dex` through `classes5.dex` |
| Candidate artifact | `/home/ubuntu/HUGR_WATCH70_FINALIZED_DELIVERY_RECOVERY_CANDIDATE_2026_10_05/artifacts/HUGR_Watch70w_0.70.0_finalized-delivery-recovery-candidate.apk` |

The exact candidate and its SHA-256 sidecar are also externally preserved for controlled transfer after a separately authorized installation decision:

- Artifact: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/oARtFBqLyqCnSCcK.apk
- SHA-256 sidecar: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/ZpqdUzFfDjHuDmnF.sha256

The binary `classes5.dex` compatibility payload remains an unrecovered-source compatibility component. Its exact byte identity and ordinary-path exclusion are checked; that is **not** egress validation, restoration, activation, transfer, or verification.

## 6. Preserved boundaries

- **Watch58 remains exactly:** `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
- No Watch58, legacy journal/root, frozen completed-package root, or unclassified Watch68 session is opened, migrated, acknowledged, deleted, or represented as finalized/verified.
- Exact Range Readback remains unavailable; its source was not reconstructed and its launcher remains absent.
- The current Watch69 five-minute run remains **NOT QUALIFIED** until its own final manifest equality, exact Phone acknowledgement, and Watch completion marker are captured.
- Watch70 does not start a new recording when the finalized marker is present. A later recording needs its own authority after this existing-run recovery is resolved.

## 7. Shortest next physical decision — not executed here

The next action requires **separate physical authority** and is deliberately limited to existing-run recovery, not a new recording:

1. **Installation only:** use the exact Watch70 artifact above in the ordinary Wear Installer. Proceed only if it says an ordinary HUGR update; stop at the installer result. No uninstall, clear/reset, alternate package/signer, launcher launch, Phone action, egress, or Watch58 action.
2. **One recovery launch:** after separately authorized runtime admission, open only normal Main HUGR once. Its expected headline is **“Finalized delivery recovery · standard GATT only”** or **“waiting for Phone resume.”** Do not press the test-only screen-wake switch or any egress/readback control. Do not create a new recording.
3. **One ordinary Phone57 resume:** with Phone57 already open normally, use its existing ordinary Watch connection/resume control once. No egress panel/control. Hold the Phone and Watch together and allow a bounded **15-minute maximum** for the retained-session replay/verification path; do not background either app, retry, relaunch, or start a second connection.
4. **Success capture requires all four:**
   - Phone final source delivery/canonical evidence for the terminal retained manifest, including its final displayed range/count and SHA-256 evidence as exposed;
   - Phone `Manifest equality` that advances beyond the old `ACK 1–11` pre-final state and shows an attributable final acknowledgement with no integrity/data-loss mismatch;
   - Watch passive Startup Readiness marker `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` for the preserved bounded marker run; and
   - subsequent Watch `BOUNDED_RUN_GATT_STOP_REQUESTED` / ordinary GATT closure evidence if displayed.
5. **Immediate stop:** any changed package/signer/update surface, permission prompt, egress/readback/export surface, sensing indication, new bounded-run start, changed offer/control, connection/write/manifest/hash failure, stale pre-final `ACK 1–11` at the 15-minute bound, missing Watch completion marker, unexpected prompt/crash/freeze, or any need for a retry.

A connection, live value, `Device health #…`, installer success, source-record count, service start, or `BOUNDED_RUN_FINALIZED` alone is not success. If the four terminal criteria are not all observed, preserve the first final screen/marker and stop; do not restart the physical staircase.
