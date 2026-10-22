# Watch84 / Phone66 — Prepared Physical Duration Ladder Cards

**Proposed pair only:** Watch84 `0.84.0-positional-v2-duration-ladder-candidate` and Phone66 `2.0.66-positional-v2-duration-ladder-candidate`.

## Concrete device-workflow blocker — before any card

**PREPARED, NOT CURRENT DEVICE AUTHORITY.** Neither Watch84 nor Phone66 APK has been built, artifact-admitted, installed, or device-tested. The parent final-release gate must first establish the exact signed artifacts, artifact admission, and a separately explicit installation and per-rung run authority. **Do not install, launch, connect, record, export, or retry from this document.**

These cards become eligible only after the exact pair is confirmed installed in place, the exact versions are confirmed, the paired Watch–Honor system link is normal, both apps are fresh/idle, and the normal Watch Main screen shows **`READY TO START`** with no unresolved selected receipt. A visible `STARTED`, `FINALIZED`, unavailable, failed, frozen, or other unresolved receipt is **STOP**; do not clear it to make a new run possible. Preserve app data: no uninstall, clear data/cache, reset, re-pair, or substitute phone.

**Shared run rule for every future, separately authorized rung**

1. On the ordinary Watch **Main** screen (not Diagnostics), inspect the prior source/run/revision/phase. At **`READY TO START`**, cycle the exact selector until it reads the card’s **`Ladder duration: …`** label. Selection alone does not admit a run.
2. Press **`Start selected ladder duration` once**. Do **not** use **`Start fresh 5 minutes`** and do not press Start again. Record the new source/run and selected duration only after the Watch shows **`NEW RUN ADMITTED`**.
3. Wear the Watch normally on the wrist; keep the charged Honor nearby. Normal screen sleep or display timeout is **not failure** and is not a reason to keep the Watch screen awake. Do not use a manual off/stop/cutoff control: the application’s cutoff/finalization is automatic.
4. **Only if the future explicit per-rung authority includes the run and the Watch has `NEW RUN ADMITTED`:** on the Honor’s ordinary HUGR **Full** surface, press **`Connect Galaxy Watch` once**. No earlier Connect test, reconnect, repeat attempt, Bluetooth repair, pairing change, or recovery path is permitted.
5. **STOP immediately** at an unexpected popup, crash, failed/unavailable/frozen readiness, rejected proof, disconnect/error, or any unexpected state. No retry, relaunch, reconnect, reset, clear, receipt-only closeout, or frozen/legacy/N1 action follows.

For every rung, record only operational observations: displayed Watch and Honor battery state at start/end, any low-battery or low-storage warning, and displayed free-storage status if available. Start only with both devices charged and neither reporting low battery or low storage. This is a conservative operational check, **not** a claimed numeric battery/storage threshold, consumption rate, or hardware-performance estimate. No routine Watch filming is required.

---

## Card 1 — 30 minutes

**Precondition:** The shared blocker is cleared by later authority, and this is one explicitly authorized 30-minute run. It does **not** authorize installation or a physical action now.

- **Selector:** set the exact Watch label to **`Ladder duration: 30 minutes`**; then press **`Start selected ladder duration` once** and wait for **`NEW RUN ADMITTED`**.
- **Automatic time boundary:** the Watch source deadline is **start + 30 minutes + 15-minute finalization/delivery grace = 45 minutes from Watch start**. With a matching V2 receipt, the Phone’s own horizon is **Phone connection start + 45 minutes**; this does not map Phone and Watch clocks. Before matching V2 receipt validation, the source retains its separate bootstrap horizon rather than inventing a clock conversion.
- **During the run:** wrist wear and ordinary screen sleep continue; leave the pair alone. Note battery/storage observations and any observed stream/status indication without interpreting it.
- **Online success requires all of the following for the same new source/run:** **`SAVED RUN: COMPLETE_VERIFIED`**; Watch-origin **`COMPLETED`** proof; every exact accepted Watch ACK; durable Phone report; and no unresolved custody/finalization gap. Connection, live values, counters, a requested duration, or a Phone write alone are intermediate facts.
- **Restart/offline gate:** only after online success, use the approved Honor **force-stop** process restart once—**never Clear data/cache/storage**—then open **HUGR Saved Source Evidence** once. No Connect/Bluetooth/Share action. Stop at first report/failure or the existing **180-second** reader budget. Require the same source/run plus `instrumentResult=COMPLETE_VERIFIED`, `report_integrity=VERIFIED`, `current_canonical_custody_recheck=MATCH`, and `watch_receipt_proof_current_recheck=MATCH`.
- **Private export/verifier:** only after that offline PASS, use the existing **selected completed-run export** once for this current source/run only. Independently verify it against the external source/run, original Watch-receipt bytes/digest, canonical parent byte-count/SHA-256, and exact manifest/ACK inventory anchors. No bulk/history export, Share, publication, or raw-media transfer.
- **Ladder decision:** a full 30-minute PASS is required before a separate proposal for Card 2. No repeat of the already-passed five-minute physical run is requested.

---

## Card 2 — two hours

**Precondition:** Card 1 has a full physical PASS through online completion, force-stop/restart, one offline recheck, and selected current-run export/independent verification; later authority expressly admits this one two-hour run. The shared artifact/installation/fresh-idle conditions still apply.

- **Selector:** set the exact Watch label to **`Ladder duration: 2 hours`**; press **`Start selected ladder duration` once**; continue only when **`NEW RUN ADMITTED`** is visible.
- **Automatic time boundary:** the Watch source deadline is **start + 2 hours + 15-minute finalization/delivery grace = 135 minutes from Watch start**. With a matching V2 receipt, the Phone horizon is **Phone connection start + 135 minutes**; the clocks remain independent.
- **During the run:** normal wrist wear and screen sleep are expected. Keep Watch and Honor nearby without keeping the display awake. Record start/end battery and storage observations, low-resource warnings, and only rendered stream/status facts; do not calculate a battery rate or claim a measured storage requirement.
- **Completion evidence:** require the same complete chain as Card 1: new source/run; **`SAVED RUN: COMPLETE_VERIFIED`**; exact Watch-origin accepted ACK inventory; Watch **`COMPLETED`**; durable Phone report; no custody/finalization gap; one approved Honor force-stop with no clear; and one **HUGR Saved Source Evidence** report within its existing 180-second reader budget with all four Card 1 offline fields matching.
- **Private export/verifier:** once only, export the current selected completed run through the existing selected-export route and independently verify the same external anchors. Do not reopen/re-export another run.
- **Ladder decision:** a full two-hour PASS is required before a separate proposal for Card 3. Any STOP leaves the rung unresolved and does not admit a retry.

---

## Card 3 — eight hours

**Precondition:** Card 2 has a full physical PASS through its offline and selected-export/independent-verifier gates; later authority expressly admits this one eight-hour run. This card remains preparatory until the shared candidate/admission blocker is cleared.

- **Selector:** set the exact Watch label to **`Ladder duration: 8 hours`**; press **`Start selected ladder duration` once**; continue only after **`NEW RUN ADMITTED`**.
- **Automatic time boundary:** the Watch source deadline is **start + 8 hours + 15-minute finalization/delivery grace = 495 minutes from Watch start**. With a matching V2 receipt, the Phone horizon is **Phone connection start + 495 minutes**, which is also the source’s absolute V2 Phone lifetime cap. Do not add an operator timeout, reset a timer, or extend a deadline.
- **During the run:** ordinary wrist wear and screen sleep are permitted; no display-awake workaround, manual cutoff, connection intervention, or routine filming. Record only start/end battery/free-storage observations and warnings. Do not use the synthetic native timing result as an Honor performance prediction.
- **Completion evidence:** require the same exact custody chain: current source/run; **`SAVED RUN: COMPLETE_VERIFIED`**; all exact Watch-accepted ACKs; Watch-origin **`COMPLETED`**; durable Phone report; one approved Honor force-stop with no clear; then exactly one passive **HUGR Saved Source Evidence** recheck within the existing 180-second reader budget showing the same source/run and the four required Card 1 fields.
- **Private export/verifier:** after that offline PASS only, perform one existing selected current-run export and independent verification against the external source/run, original Watch receipt, canonical parent bytes/hash, and manifest/ACK anchors. Do not select another run, use a bulk export, Share, or publish.
- **End state:** eight-hour instrument/custody success does not authorize N1, adaptive Suki, a next run, physiological interpretation, or any frozen-root action.

## Evidence language and limits for all cards

Report canonical evidence, not a physiological conclusion: source/run identity; selected duration; Watch receipt start/finalization/completion state; exact ACK/manifests; canonical stream identities/counts; source-sequence/index gaps; rendered raw acquisition/status/quality markers; and `sourceTimestampMs` ranges. A Watch source timestamp is **not** synchronized wall time or duration truth. If a raw status/quality value is not rendered, report it as unknown—do not infer it.

The current software evidence includes a synthetic eight-hour terminal pass with core cumulative processing **135.997 s within the unchanged 180 s** deadline; it is **not** Honor performance, battery, radio, screen-sleep, storage, or hardware proof. Samsung native IBI consecutiveness remains **not qualified**. Do not report RMSSD, IBI continuity, physiological validity, acquisition completeness, clinical status, or state validity. The existing hard application cutoff is unchanged: it does not prove provider endpoint/terminal-drain completeness, and no flush/wait/late-callback rule may be substituted for it.
