# HUGR NSTAIR — Watch72 / Phone58 One Resume Attempt: Service-Lifetime Stop

**Observation date:** 2026-10-06  
**Scope:** separately authorised bounded Phone identity-check and ordinary recovery-resume attempt  
**Status:** **STOP — WATCH STANDARD-GATT SERVICE DESTROYED BEFORE DELIVERY**

> This receipt records a single bounded physical attempt and a targeted source trace. It does not establish final delivery, canonical equality, exact acknowledgement, Watch completion, qualification, sensing, a new recording, egress, readback, verifier action, reset, pairing change, or any legacy/frozen-root operation.

## 1. Governing identities and immutable boundaries

| Line | Candidate identity | Candidate SHA-256 |
|---|---|---|
| Watch | `com.hugr.wearos`, code `72`, `0.72.0-recovery-boundary-diagnostic-candidate` | `9fcfca6ac0f926acfb20ca402ca09c37ea9f2e40ab849f62795b9cffa64ed5f1` |
| Phone | expected `health.hugr.app`, code `58`, `2.0.58-recovery-boundary-diagnostic-candidate` | `5ba0e207b1a33c94294c7e02cd7c4db5cfd95d60dcf793f84e176ae0bec4376c` |

- **Watch69 five-minute run remains NOT QUALIFIED.**
- **Watch68 v1 remains UNCLASSIFIED / NOT VERIFIED.**
- **Watch58 remains exactly:** `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 2. Observed bounded attempt

1. The preceding authorised readiness gate had already shown Watch72's recovery diagnostic surface with `ADVERTISING READY` and Phone HUGR's non-black Home surface.
2. During this separately authorised attempt, the Phone ordinary connection control was pressed once. The Phone later showed a connected presentation with `awaiting canonical source`, `Transport: MISSING`, and `Manifest equality: no exact segment acknowledgement yet`.
3. The Watch diagnostic surface subsequently showed that it was no longer advertising and recorded `BLE_SERVICE_DESTROYED`. The accompanying 8.024271-second observation video, `VID_20261006_213343.mp4`, has SHA-256 `aaf6d271e91a6aea3044fbb25ceefc1ba380131f8a4d07e7ecb25a28f260ca07` and shows the static final Watch state; it shows no further user interaction, retry, egress action, reset, sensing, or recording.
4. The supplied Watch causal display shows standard-GATT connection/MTU activity before the terminal service-destroyed state. Accordingly, this is **not evidence that the Phone failed before GATT connection**, and it is **not evidence that the operator merely began before the readiness UI became visible**.
5. No final source manifest equality, exact segment acknowledgement, `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`, `BOUNDED_RUN_GATT_STOP_REQUESTED`, canonical final custody, or qualified recording result was observed.

## 3. Targeted source finding

The source establishes a standard-runtime service-lifetime gap, while the precise Android system cause of the destruction remains unobserved:

- `MainActivity.startFinalizedDeliveryRecovery()` launches `BleGattService` with ordinary `startService(...)` for the retained finalized source-session ID.
- `BleGattService.onStartCommand()` returns `START_NOT_STICKY` for that standard recovery path.
- The only temporary foreground connected-device lifetime implemented in `BleGattService` is explicitly confined to `EGRESS_ONLY`; it is deliberately absent from standard GATT recovery.
- `BleGattService.onDestroy()` records `BLE_SERVICE_DESTROYED`, then stops advertising, closes the GATT server and clears in-memory replay state. This exactly explains the Watch display's terminal causal event, but it does not by itself identify why Android called `onDestroy()`.
- The normal recovery success path calls `stopSelf()` only **after** acknowledgement has matched the retained session, cumulative endpoint and terminal SHA-256, the finalized segment is no longer retained, and `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` has been recorded. None of those success preconditions was observed here.

The Phone source additionally contains an automatic reconnect loop after a live disconnect (up to five attempts). That behavior makes an unbounded wait after a disconnected standard session inappropriate for this governed recovery attempt.

## 4. Current safe stop boundary

No further connection or Watch launch is authorised by this receipt. The safe non-destructive cleanup instruction is:

1. If HUGR remains active on Phone, use Android **Settings → Apps → HUGR → Force stop** once. This stops queued automatic reconnect work without clearing HUGR data.
2. Do **not** use Clear storage/data, unpair/reset Bluetooth, reopen Normal Main HUGR, press Connect again, start sensing, or alter Watch controls.
3. Leave the Watch on the ordinary watch face. No action is requested against legacy/frozen roots or Watch58.

The operator's completion of that cleanup is a separate observation; it was not assumed in this source-owned receipt.

## 5. Engineering conclusion and smallest next move

The attempt has eliminated the prior "was the Watch ready?" ambiguity at the GATT connection/MTU boundary. The next engineering target is **not another blind restart**. It is a focused source/test-only correction that gives the **standard finalized-delivery recovery service** a bounded, non-egress, connected-device foreground lifetime and proves:

- the retained fresh-v2 source session is selected before advertising;
- no Health service, sensing, new source records, egress, legacy/frozen-root access, acknowledgement, deletion or completion happens merely because the lifetime guard begins;
- GATT service/server/advertising remain available through the one permitted resume boundary; and
- the guard ends only on verified acknowledgement/completion or an explicit bounded fail-closed stop.

That correction needs its own explicit authority. It must also address the Phone's automatic reconnect policy for recovery mode, so a service-loss does not silently create repeated physical retries.

## 6. Control and preservation status

This receipt is source-owned. The Master Task List is updated before the Shared Alignment Control Surface; alignment is updated last. Atlas synchronization is intentionally not retried because the known portal permission repair is separate from device work. GitHub preservation will be verified after the closeout commits; no claim of remote preservation is made until that readback succeeds.


## 7. Verified remote preservation

The governed local commits are preserved as non-force GitHub tree-identical mirrors. The GitHub mirror commits are expected to have distinct IDs because they retain the branch's remote parent, while their read-back tree IDs exactly equal the local commit trees.

| Scope | Local commit | Verified GitHub mirror commit | Verified shared tree |
|---|---|---|---|
| Watch source/receipt | `48b189d78a658e7d72fae1b5c86cc9c9095f9174` | `b7adf090eb39f2a104fe370fb73df7ea987049bc` | `cf0026ef2dce7b7b96cd9199480dcea2465ab920` |
| Phone controls/receipt | `8ec19c8def45282d45ce1870308e907e088273f5` | `aea9f0db38baef258f2a30e51ebf360dc7801aeb` | `67c618dfb52aa9fbb473d90a6a02f442de97d6b5` |
| Control repository | `73888bb796a4f990d70494249b2fb50aed022ff2` | `7d90b21477c7450361e5988cba1d41051bb9ef58` | `0a0cb18458e4bbe576746e49996d618e663bede4` |

GitHub access is therefore working for these preservation branches. No force-push or merge to `main` occurred.
