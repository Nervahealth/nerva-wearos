# Watch77 — recommended qualification closure and route toward N1

**Date:** 2026-10-08, 21:48 CEST. **Status: proposal, not execution authority.** No implementation, test, build, installation, device-private readback, transfer, reconnect or new recording performed by this planning pass.

## Decision in one sentence

Combine **existing-session canonical evidence access, truthful delivery presentation and future repeatable qualification** into one bounded engineering package; do not restart retained delivery or start a separate Bluetooth/debugging programme by default.

## 1. What is now clarified

Neil confirms the Watch remained **listed on the Honor but disconnected/grey**. This resolves his earlier “separated” description as a disconnected listing, not observed removal or a new-pairing request. It does not independently establish radio state, bond validity, disconnect time or cause. It does not justify resetting or transferring companion ownership to the Fold.

The supplied Watch77 diagnostic reports GATT connection, subscription and resume around 18:11:42 and `SERVICE_DESTROY / EXACT_ACK_COMPLETED` around 18:11:46 for sourceSession `3c9e99e9-9430-480e-9b5a-ae00990b6b22`. That is app-reported completion progress, not independent recording qualification. The Phone screenshot still says integrity UNPROVEN and final delivery not determined; only ACK8181–8182/n2/212bytes/short hash is visible. No new device action follows from this status clarification.

## 2. Immediate combined engineering package — recommend authorizing together

### A. Establish the smallest existing-evidence readback route first

Use the existing Phone canonical accepted records, native manifest/range/verified tables and actual sourceSession; join them to the already-saved Watch terminal report and any genuinely surviving completion evidence. Prefer an existing safe access route if one actually exists. Do not put Neil through another live-ADB or icon-exploration session merely to discover a route.

The inspected Phone interface exposes `exportAccepted` as a private pathname, not an operator-facing complete evidence report or host transfer. A Phone-side passive canonical-evidence reader may therefore be the smallest necessary addition. It should expose full session/range/count/bytes/SHA-256, stream ranges, gaps, receipt/lineage and historical stored verification separately from current recomputation. Verify canonical identity/CRC/order from actual bytes, not database labels alone. Bound work to the selected ordinary session; stop on missing artifacts, uncertainty, budget or inconsistency.

**Important source findings that constrain implementation:**

- The normal Phone journal constructor and its query/export methods can invoke pending-append recovery; the existing `verifyManifest` also writes the verified flag. Those methods cannot simply be reused and called “non-mutating”. The proposed reader needs a demonstrated non-writing path, no schema creation/migration/repair, and a consistent existing database/accepted-file view. Pending transaction/WAL/active-writer ambiguity must fail closed or use a reviewed coherent read-only snapshot; no silent checkpoint/recovery/truncation.
- The Watch startup marker store retains only the latest stage, and completion writes `BOUNDED_RUN_GATT_STOP_REQUESTED` after `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`. Do not send Neil to the existing viewer expecting an ACK-stage history that it does not store. Determine what genuine existing saved/causal evidence can support completion; do not fabricate the overwritten marker or individual ACK history.
- Native manifests do not by themselves constitute durable proof of every ACK exchange. If an exact historical ACK/completion requirement cannot be satisfied by surviving evidence, name that residual gap. Do not issue a replacement ACK, re-create deleted Watch bytes or relabel the old run to manufacture proof.

No physiology needs to be sent through GitHub or an automatic email/SMS path. A later exact selected-session host-copy/independent verifier, if required, is a separately authorized private evidence action; this is not activation of frozen Watch58 egress.

### B. Make the Phone status honest for this delivery path

Within the same source/test package, correct the demonstrated presentation gap only where justified: manifest-only recovery must not depend on new sensor/device-health samples to present saved canonical evidence. Distinguish acquisition stopped, connection liveness, segment equality, native custody and Watch completion. Never infer complete-session success from one small segment, an ACK write promise, cursor equality or current `Connected`.

The stale Connected behavior is still not fully diagnosed. Trace its concrete source/lifecycle seam before modifying it; do not presume Bluetooth/companion failure or add automatic reconnect. Avoid expanding into transport redesign or a telemetry platform.

### C. Test and prepare at most one necessary integrated candidate

After explicit authority, test selected-session identity, complete and partial/missing manifests, canonical corruptions, gaps, pending append/active writer, database/file mismatch, process restart, non-mutation and historical/live status separation. Retain the existing joined Watch→Phone→Watch regression when touched; distinguish JVM/Node simulation from Android native SQLite and physical behavior.

Prefer a **Phone-only** evidence/usability candidate if required. Keep Watch77 unchanged unless a specific indispensable missing capability is demonstrated and separately resolved. A Watch candidate is not the default. Build a same-package/signer greater-version Phone candidate only after applicable tests and preservation pass, or deliver a proven existing route if no build is needed. Candidate existence is not installation or execution authority.

### D. Physical gate afterwards: evidence readback, not delivery retry

Present one short proposed installation/readback procedure on the Honor, preserving app/data. It should not require the Watch to advertise or start another recording. It must name the exact stores/session and output, bounds, non-mutation/STOP conditions and any proposed private transfer. Installation, launch, private-store observation/transfer and host verification remain separate explicit gates. Readback must not modify ordinary data, mutate frozen/legacy roots or activate egress.

**Immediate acceptance:** qualify the existing Watch69 short recording only if complete canonical equality/custody, attributable exact ACK and matching Watch completion evidence actually support it. Otherwise deliver the exact missing evidence boundary and the smallest future correction; preserve Watch69 NOT QUALIFIED. Do not replace this task with another recording merely because the current Phone display looked static.

## 3. Longer-run staircase — conditional, not current authority

| Next stair | Required result before moving on |
|---|---|
| Existing short recording | Complete selected-session evidence decision, including honest residual gaps |
| Repeatable fresh short recording, if needed | Explicit new-session start/sensor records/quiescence/finalization, full Phone equality/custody and Watch exact completion through the same reusable evidence path |
| One intermediate-duration run | Duration fixed after budget review; e.g. approximately 30 minutes as a proposal, not a governing existing protocol. Sleeping-screen behavior, service/sensor lifetime, gaps, storage/battery and final delivery must be evaluated. No guarantee of gap-free data. |
| Eight-hour/waking-day feasibility | Bounded capture/stop/delivery with trustworthy timeline, quality, missingness and source provenance; verify the actual duration/package, not merely foreground-service survival |
| Portable scientific-memory package | Immutable qualified source identities/ranges, clock uncertainty, transformations and reverse trace; ingest/ack/replay/regeneration under the governing staircase |
| Book One N1 | Separately governed human context and fixed/manual Suki question/haptic delivery→perception/response linkage before adaptive foreground policy. A wearable stream alone is Instrument feasibility, not Book One N1 or hypothesis validation. |

Use Honor as the ordinary delivery device; Fold is a diagnostic host only. For later radio-dependent gates, prepare one bounded known-target connection check and record its result. A disconnected listing is not a basis for reset/re-pair/companion reassignment, and Bluetooth troubleshooting is not a prerequisite for passive already-stored Phone evidence. All later physical steps need their own authority.

## 4. Recommended immediate authorization wording

> Authorize the combined existing-recording qualification-closure engineering package: determine the smallest route to read the already-stored Phone canonical evidence and bind it to genuine surviving Watch completion evidence; implement only the necessary passive, non-mutating selected-session evidence access and demonstrated Phone delivery-status corrections; run focused/native and relevant integrated tests; and prepare one signer-continuous greater-version Phone candidate if needed after tests pass. Keep Watch77 unchanged by default and report any indispensable Watch change or unrecoverable historical evidence gap before expanding scope. Preserve all existing app/data, actual source identity, exact-ACK rules, legacy/frozen roots and standing GitHub closeout. No physical device action, reconnect/retry/new recording, egress, private-data transfer or N1 execution is authorized. Return one concise readback gate and its acceptance/STOP conditions.

This proposed text is not approved by the request for a recommendation. No correction is implemented before an explicit reply authorizing it.

## 5. Automatic preservation and unchanged states

Preserve the clarified fact and proposal in source-owned receipts → copies → Master → affected authorized/accessible private proposal only → Alignment last → consistency/bounded commits/non-force GitHub head/tree readback. Accepted private S4 remains immutable; known-denied Atlas access is not retried. No credential or private physiological/media bytes enter Git.

- Watch69 **NOT QUALIFIED**.
- Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
