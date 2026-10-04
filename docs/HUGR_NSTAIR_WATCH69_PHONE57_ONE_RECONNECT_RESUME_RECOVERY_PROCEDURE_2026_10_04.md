# HUGR NSTAIR — Watch69 Existing-Run, One Phone57 Reconnect/Resume Recovery Procedure

**Date:** 2026-10-04
**Status:** **PREPARED ONLY — NOT YET EXECUTED.**
**Scope:** One ordinary normal-BLE reconnect/resume attempt for the already finalized Watch69 run. It is not an installation, launcher, sensing, recording, egress, transfer, verifier, Watch58, legacy-root, or Watch68 action.

## Objective

Attempt delivery of the retained terminal segment belonging to the existing Watch69 ordinary run, without creating any new sample or recording.

The target ordinary-launch marker is:

| Field | Required identity |
|---|---|
| Marker run ID | `b561b698-8206-4cb5-8717-95a5c9f533e2` |
| Prior terminal marker | `BOUNDED_RUN_FINALIZED` |
| Prior marker time | `2026-10-03T19:46:34.547Z` |
| Prior visible ordinary acknowledgement | `ACK 1-11 n=11 bytes=917 sha=4d5f2fe334bf…` |

The previous `ACK 1-11` is **not** a successful result for this attempt. It is the known pre-final acknowledgement and must not be relabelled as the terminal manifest.

> The ordinary-launch marker run ID is not assumed to be the source-journal `watchBootSessionId`. The latter is derived by the Watch source journal and must be observed as the replay session shown on Phone rather than guessed from the marker ID.

## Preconditions

- **Phone57 and the installed Watch69 remain unchanged.** No installation, update, data clear, app force-stop, reboot, Watch Main-HUGR launch, or Watch control is used.
- The Watch and Phone have adequate charge and remain close together for the bounded observation.
- Begin a continuous Phone screen recording before the connection action. Keep normal HUGR foregrounded and the Phone display awake during the observation.
- Do not enter Evidence Egress, Exact Range Readback, any haptic/observation/credential action, or any other HUGR control.

## One bounded procedure

1. On **Phone57 only**, open normal HUGR. If the ordinary wearable section is not visible, use **Full** once solely to reveal the normal ordinary connection control.
2. Start the continuous screen recording. Ensure the ordinary control reads **Connect Galaxy Watch**.
3. Press **Connect Galaxy Watch exactly once**. Do not make a second scan, reconnect, disconnect, or other connection attempt.
4. Allow up to **120 seconds** for the first ordinary connected state. If it does not connect in that period, preserve the first displayed status and stop. Do not retry.
5. Once connected, leave both devices untouched for a maximum of **15 minutes**. This is the whole replay observation window; it is an operational bound, not a claim that source code guarantees a particular BLE throughput.
6. If the terminal Phone acknowledgement appears earlier, preserve the visible Phone evidence and wait only long enough for any automatic GATT closure/status transition to settle—maximum **60 additional seconds**. Do not press Disconnect.
7. Only after the Phone terminal evidence is preserved, open **HUGR Startup Readiness** once on the Watch. It is the read-only marker viewer and must be the only Watch surface opened in this attempt. Record its three lines, then stop.

## Phone evidence to capture

Capture a continuous video or clear stills showing the final state. The evidence must contain all of the following.

| Required capture | Exact criterion | Why it matters |
|---|---|---|
| Normal path | Ordinary HUGR only; one connection action; no egress/readback/other control | Keeps this a normal replay recovery attempt |
| Replay session evidence | `Source delivery` shows canonical data marked `REPLAY`; record the visible `watch=<first 8 characters>` from the `Source journal` line | Identifies the replay source session as displayed by Phone57 |
| Final manifest range | A **new** `Manifest equality: ACK <first>-<last> n=<count> bytes=<bytes> sha=<prefix>…` line, not the old `ACK 1-11` | Phone persists contiguous canonical frames and performs exact manifest equality before it writes the acknowledgement |
| Terminal endpoint | The new ACK’s `<last>` is the final endpoint reported by that newly received terminal manifest; capture the accompanying `watch records=<value>` line | Captures the actual endpoint without assuming the old visible count `8173` is the final manifest range |
| Hash evidence | Capture the displayed SHA-256 prefix in the new ACK line | Phone57 displays only a 12-character prefix; do **not** invent or claim an independently visible full SHA-256. The Phone source writes the full 32-byte manifest hash only after internal equality verification. |
| No negative integrity evidence | No `Source integrity failed`, `DATA LOSS`, `REMOVED:…`, or connection failure before the new terminal ACK | Prevents a connection or partial replay from being called a qualified result |
| Phone acknowledgement | The new ACK line itself | Source shows this event is emitted only after durable append, exact manifest verification, accepted-path export, and the exact acknowledgement write completes on the active Phone connection lineage |

A connected state, a replay frame, the old `ACK 1-11`, `watch records=8173`, or visible sensor values alone is not sufficient.

## Watch completion evidence to capture

The passive **HUGR Startup Readiness** screen must show:

| Field | Required value |
|---|---|
| `runId` | `b561b698-8206-4cb5-8717-95a5c9f533e2` |
| `lastStage` | `BOUNDED_RUN_GATT_STOP_REQUESTED` **or**, if captured between writes, `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` |
| `stageEpochMs` | Record the literal displayed value |

`BOUNDED_RUN_GATT_STOP_REQUESTED` is the expected terminal passive observation. In source it is recorded **only after** `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` and the fresh ordinary scope is closed after all finalized manifests have been acknowledged. It is therefore valid terminal evidence of the completed acknowledgement path even though the immediately preceding intermediate marker may no longer be the most recent displayed line.

If the screen remains `BOUNDED_RUN_FINALIZED`, shows a different run ID, or shows any failure stage, the attempt is **not qualified**.

## Success decision

The existing run may be recorded as a qualified short ordinary recording only if one attributable attempt produces all of the following:

1. a new terminal `Manifest equality: ACK` whose range and displayed hash prefix are captured, rather than the old `1–11` acknowledgement;
2. source-session replay evidence in the same ordinary connection observation;
3. no displayed integrity/data-loss/connection failure before that acknowledgement; and
4. the correct-run passive Watch terminal marker `BOUNDED_RUN_GATT_STOP_REQUESTED` or `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`.

This is **Phone canonical verification plus exact acknowledgement and Watch completion**. It does not establish independent verification of Watch58, egress success, an eight-hour run, clinical validity, or any result for the Watch68 v1 session.

## Stop conditions

Stop immediately without retry, reconnection, relaunch, new recording, installation, or manual cleanup if any of these occurs:

- no connection within the 120-second connection bound;
- any changed/unknown device identity, prompt, warning, unexpected app surface, egress/readback/export surface, automatic sensing start, crash, or connection failure;
- any integrity/data-loss indication;
- no new terminal acknowledgement by the 15-minute replay bound;
- an acknowledgement that is only the old `1–11` value, a different/ambiguous session, or lacks the required Watch terminal marker; or
- any condition that makes the ordinary source identity or result uncertain.

Preserve the first relevant screen/video and report the literal visible text. Do not diagnose on-device or retry in the moment.

## Preserved boundaries

- Watch58 remains exactly **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**.
- The Watch68 v1 session remains **UNCLASSIFIED / NOT VERIFIED** and untouched.
- No frozen/legacy-root access, egress activation, range readback, artifact transfer, verifier, ADB, Termux, root, data clear, or reinstall is part of this procedure.
