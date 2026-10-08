# Watch77 — saved exact-ACK completion observed; Phone canonical evidence pending

**Date:** 2026-10-08. **Classification:** physical saved-diagnostic persistence/view demonstrated in this attempt; Watch app reports exact-ACK completion. Watch69 **NOT QUALIFIED**. No source correction, test, build, new installation, device retry or private journal readback performed in this evidence review.

## 1. Controlling context and operator clarification

Watch77 installation/version was operator-confirmed under Settings after the usual in-place update with no strange behavior. Main launch was reported at 18:09, advertising and Phone opening at approximately 18:11. Initial launch/Phone actions preceded the separate recovery authorization. At 18:18:45 Neil explicitly authorized only remaining observation of this existing attempt to first terminal result/failure or conservative 18:24, plus passive Saved Diagnostics navigation/capture after STOP, without Share. This receipt does not retroactively authorize earlier operations or extend that cap.

At 18:45 Neil clarified that **he stopped filming at 18:33**, not that HUGR ended then. Thus prior wording “ended at about twenty minutes” must not be interpreted as a runtime timeout/crash. The reported clock bounds 18:09–18:33 span 24 minutes; the long video itself was not provided in this upload and its start/time course has not been independently established. Neil then reports the Phone did not visibly change from the start of that approximately twenty-minute video. This is operator observation, not a continuous frame audit.

## 2. Supplied evidence and integrity identities

| Evidence | Verified artifact identity | Use/limit |
|---|---|---|
| Phone screenshot `Screenshot_20261008_183451_health_hugr_app_MainActivity.jpg` | SHA-256 `6d96e1eabc52f65f34c67cab35269f75a370277fb4cf320179a26533557dc049`; 380,820 bytes | Displayed Phone clock 18:34. Native private storage not accessed. |
| Watch Saved Diagnostics video `VID_20261008_184107.mp4` | SHA-256 `73d679e4e04679702ebd0e663ede932a48223f13fe10b5116af799f39b1fa856`; 107,275,590 bytes; 50.101854 seconds | Readable rolling view of one saved report, viewer 1/1, header through END REPORT. Frames sampled every two seconds, with key frames inspected at full resolution; not a continuous per-frame audit. |

Original media remain outside Git repositories. Only sanitized evidence metadata and this interpretation are committed. The screenshot was read directly from the user-supplied image, not reopened through a file viewer. The video was deterministically frame-extracted without modifying its original.

## 3. What the Phone screenshot establishes

- `Galaxy Watch8 (TSVV) · Connected · awaiting canonical source`.
- `Transport: MISSING`, cardiac missing; legacy freshness also MISSING.
- `Source journal: integrity=UNPROVEN · MTU=517`; Watch source identity is not displayed (`watch=--`).
- Source delivery awaiting canonical record; backlog and Watch record count unavailable.
- **Manifest equality: ACK 8181–8182, n=2, bytes=212, sha=ad86b8616fb2…** (12-character prefix, not a full digest).
- **Final delivery: not yet determined from current source evidence**.

This resolves the previously ambiguous typed range `8181-81812` as the displayed **8181–8182**; the earlier literal remains preserved as history. This is not the complete main recording manifest and is not enough for canonical qualification. The screenshot does not establish fresh connection liveness, the Watch's accepted ACK, all previous manifest equality, native durable custody or final Watch completion. Static `Connected` must not be mistaken for a live Watch service.

## 4. What the Watch saved report visibly says

The separate HUGR Saved Diagnostics viewer visibly renders report 1/1, version code **77**, version name **0.77.0-durable-diagnostics-historical-resume-candidate**. The stable report can be scrolled to `--- END REPORT ---` without live wireless ADB. No exception/cause/stack or explicit truncation is rendered in this report's body.

| Field | Readable value |
|---|---|
| process | `ea87167d-ed0c-4d9f-813d-1ae41b6b8ee0` |
| attempt | `269d49e5-a96a-49a9-89bf-08a22e7ac09a` |
| sourceSession | `3c9e99e9-9430-480e-9b5a-ae00990b6b22` |
| wallMs | `1791475906902` |
| elapsedMs | `622792303` |
| stage | **SERVICE_DESTROY** |
| outcome | **EXACT_ACK_COMPLETED** |
| lineage / MTU | **1 / 517** |
| C_phone / R_retained / H_issuance | **8184 / 8182 / 8184** |
| queuedEndpoint | **unknown** |

These C/R/H fields are operational assertions, not independent Phone custody or deleted-byte proof. The physical viewer invokes the store's checksum-validation read before rendering; the visible body is evidence of that app behavior, but the report text/checksum was not independently acquired or recomputed on the host. No process-restart durability or report sharing/email/SMS was tested.

Readable cached event tail:

| Event | Watch wall timestamp converted to Europe/Oslo | Meaning |
|---|---|---|
| BLE_SERVICE_CREATED `1791475820032` | 18:10:20.032 CEST | Runtime event, not complete acquisition proof |
| GATT_SERVICE_READY `1791475820278` | 18:10:20.278 CEST | Service readiness event |
| GATT_ADVERTISING_READY `1791475820400` | 18:10:20.400 CEST | Advertisement readiness event |
| GATT_CONNECTED `1791475902198` | 18:11:42.198 CEST | Connection event |
| MTU_CHANGED `1791475902566` | 18:11:42.566 CEST | MTU event |
| SOURCE_CCCD_ENABLED `1791475902850` | 18:11:42.850 CEST | Source subscription event |
| RESUME_RECEIVED `1791475902947` | 18:11:42.947 CEST | Resume received |
| SOURCE_TRIGGER_RESULT `1791475904126`, reason=1 | 18:11:44.126 CEST | Code maps reason 1 to TRIGGERED, not completion |
| Report at `1791475906902` | **18:11:46.902 CEST** | SERVICE_DESTROY / EXACT_ACK_COMPLETED saved |

Monotonic elapsed difference from BLE_SERVICE_CREATED (`622705432`) to report (`622792303`) is **86.871 seconds**. Wall-time difference is 86.870 seconds. Converted wall timestamps are Watch-clock assertions, not independently synchronized clocks. This report therefore concerns an early app-recorded terminal event, not evidence of a twenty-minute deadline stop. Its terminal event precedes the 18:18 remaining-observation authorization; provenance/authorization chronology is preserved, not retroactively repaired.

The cached tail ends at the source-trigger result. It does **not** enumerate all SOURCE_SEGMENT_ACK_ACCEPTED events or provide endpoint/full-hash ACK history. Missing tail entries must not be invented or treated as proof of their absence from all other stores.

## 5. Source-bounded interpretation

Existing `BleGattService.kt` has one explicit EXACT_ACK_COMPLETED stop request: `closeBoundedFreshRuntimeAfterDeliveryIfComplete` (lines 1917–1931 in reviewed source). Its recovery branch returns while selected-session finalized segments remain. After they are absent, it writes `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` for the actual selected sourceSession, closes fresh runtime, writes GATT stop requested and requests exact-completion shutdown. `onDestroy` persists that stop reason as SERVICE_DESTROY. The reviewed completion call site follows `handleSourceAcknowledgement`.

That ACK handler validates active/selected session, queued endpoint and retained manifest, then calls `SourceJournal.acknowledgeCompletedSegment`. The journal requires matching session, exact endpoint and complete SHA-256 before its ordinary exact-ACK removal. Completion is not selected merely because C equals or exceeds R. No device journal removal or ACK was initiated by this review; this describes the already-installed source path.

**Strongest warranted conclusion:** the captured Watch77 app saved an exact-completion outcome for this actual source session after its completion path, and this is real progress beyond the captured Watch76 resume abort. It is not yet independent verification of all recording bytes, all manifests/ACKs or the Phone's canonical custody. No direct readback of the durable completion marker was obtained, and the report is not itself that marker artifact.

The Phone final-delivery label has a concrete source limitation for this manifest-only route: `WatchConnect.tsx` supplies the classifier's latest Watch endpoint from `latestSourceHealth`, and freshness from live/current source events. Recovery suppresses new device-health source append. With no new source-health/current record event, the screenshot can remain MISSING/awaiting/not determined while the manifest ACK updates. This explains why that label cannot settle recovery completion. The full reason for the stale Connected state or missing later diagnostic tail remains unproved; no speculative correction is made.

Phone production source already verifies native canonical range/order/count/bytes/full hash and stream ranges before issuing its exact ACK, and retains native accepted bytes plus manifest/verified tables. The `exportAccepted` API returns an app-private accepted-file path; it is **not** a host transfer/share or a complete manifest-evidence export. No usable passive operator-facing native custody review/host-copy route is claimed from it.

## 6. Remaining blocker and next bounded decision

Do **not** repeat recovery or record new data because the Phone looked static. Preserve the installed Watch77/Phone59 pair and their stores. Smallest next engineering question is how to expose/read the already-durable Phone canonical manifest evidence and bind it to this sourceSession/accepted Watch completion **without starting sensing or another BLE attempt**. A focused read-only source investigation may propose the smallest evidence-preserving route; implementation/correction, build, installation, device-private readback/transfer or new physical action still require explicit authority. No additional broad audit or diagnostic-only candidate is a prerequisite.

For independent qualification, obtain the actual Phone canonical recording manifests with full range/count/bytes/full SHA-256 and native equality/custody evidence, attributable exact ACK evidence, and matching durable Watch completion proof. A 212-byte two-record manifest, UI label, cached tail or generic saved outcome alone cannot substitute.

## 7. Preservation and sovereign states

Source-owned receipt → identical Phone/canonical/controlled copies → Watch77 admission pointer → Master → affected authorized/accessible private proposal only (known-denied Atlas not retried; accepted S4 immutable) → Alignment last → consistency/commits → controlled non-force GitHub head/tree readback. Media/private physiological payloads excluded. Publication readback is recorded outside the commit to avoid self-reference.

- Watch69 **NOT QUALIFIED**.
- Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 8. Later system-Bluetooth observation — 2026-10-08 19:39 CEST

Neil reports looking on the Phone and finding the Watch again “seperated from blue tooth”; he does not know when this occurred and wonders whether it relates to obtaining the new Phone. Preserve this as an operator-reported later connection-status observation with unknown transition time, **not established unpairing**, Bluetooth-radio OFF, companion reassignment or a causal diagnosis. No exact Settings wording or screenshot was supplied here. Ask whether the Watch remained listed but disconnected or disappeared/required pairing; no setting changes or HUGR reopening are requested to answer.

The saved attempt report already contains GATT_CONNECTED, MTU_CHANGED, SOURCE_CCCD_ENABLED and RESUME_RECEIVED at approximately 18:11:42, followed by app-recorded EXACT_ACK_COMPLETED at approximately 18:11:46. Therefore an actual HUGR BLE link existed during that exchange, independently of the Phone's stale Connected label. This does not prove continuous connection throughout the twenty-minute observation or the Samsung system companion connection's state. A later Settings status cannot time or negate the earlier recorded exchange.

Ordinary recovery completion closes HUGR's GATT server; cessation of that application link is distinct from losing the system pairing/bond or the companion connection. Do not attribute a true unpairing to normal HUGR shutdown, and do not assert that the new Fold caused the system status. Intermittent connectivity remains a separate operational concern; it does not yet explain every earlier failure or this attempt's reported completion. The Watch76 source-resume high-water exception was independently captured and corrected, rather than inferred from Bluetooth settings.

NEXT unchanged: resolve the smallest source-only route to existing Phone canonical evidence without another BLE attempt; source corrections/device/private transfer remain separately authorized. No reconnect, pairing change/reset, reinstall, retry, new recording or build follows from this observation. Accepted private S4 remains unchanged; known-denied Atlas access not retried. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
