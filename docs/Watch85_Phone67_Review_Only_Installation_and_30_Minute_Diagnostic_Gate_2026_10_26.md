# Watch85 / Phone67 — review-only installation and informative 30-minute diagnostic gate

**Status:** Proposal only. Neither installation nor physical operation is authorized by this document.

## Purpose

This pair is designed to make **one 30-minute V2 custody attempt informative**, not to claim a timeout cure. It retains the previously admitted duration/custody safeguards and adds bounded Watch and Phone local diagnostic evidence around receipt/ACK operations. The prior Watch84 run remains preserved as failed at `DELIVERY_DEADLINE`; it is not retried, closed out or used as a qualification result.

## What the added diagnostics can establish

After a terminal outcome or an earlier Phone timeout, the isolated diagnostic viewers can provide separately retained, bounded local evidence:

- **Watch85:** request-side operation metadata available locally; callback outcome (`TRUE`, `FALSE`, `NULL`, pending or late accepted where recorded); local monotonic timing; whether an ATT response was attempted; bounded publication outcome/overflow information.
- **Phone67:** operation classification; bounded local incident/receipt state; explicit correlation-unresolved state; and a detached, user-initiated local reader with explicit private Save/Share only.

They can make each side’s local failure boundary inspectable after disconnect. They do **not** turn equal clocks, counters, offsets or timestamps into a cross-device transaction match. An absent Watch diagnostic is not proof that no Watch request arrived.

## Native tracing: narrow conditional use only

No native trace is a blanket prerequisite for attempting the 30-minute gate. It becomes useful only if the informative attempt produces a Phone receipt-operation timeout while the retained Watch result is timely local `TRUE` or otherwise insufficient to establish whether the same request reached the Watch/response boundary.

In that case, the smallest next capture is a **single Android Bluetooth HCI-snoop/bugreport trace** limited to that one attempt, retrieved after STOP. It is needed only to answer the named wire-level mapping question; it is not a general telemetry programme. If the relevant HCI/bugreport route is unavailable or cannot be safely retrieved, preserve the two local diagnostics and stop rather than infer correlation.

## Future installation-only sequence — separate authorization required

1. Confirm the devices are charged and no HUGR run is active. Do not open either HUGR app.
2. On the Honor, use the ordinary Android installer/update route for the exact Phone67 APK. Preserve app data; do not uninstall, clear data, reset Bluetooth or open HUGR. Verify only **Settings → Apps → HUGR** shows `2.0.67-receipt-diagnostic-correlation-candidate`.
3. On the Watch, use ordinary Wear Installer 2 to update the exact Watch85 APK. Preserve app data; do not uninstall, reset or open HUGR. Verify only **Settings → Apps → HUGR** shows `0.85.0-receipt-diagnostic-correlation-candidate`.
4. Stop. Report the two Settings identities and any installer warning. Installation confirmation alone does not authorize opening, connecting or recording.

## Future one-shot 30-minute gate — separate authorization required

Only after successful installation-only confirmation and a fresh explicit gate authorization:

1. Wear the Watch normally. Confirm ordinary Bluetooth association is present; do not change pairing/reset settings.
2. Open **Main HUGR** on the Watch once. Do not press Start until its current surface explicitly permits the fresh selected ladder duration.
3. Select **30 minutes** and press **Start Selected Ladder Duration once**. Note the start time. Do not press a second Start.
4. Only when the Watch shows its current readiness/advertising state, open normal Phone67 HUGR once and press **Connect Galaxy Watch once**.
5. Observe until the first terminal result or **45 minutes from the admitted Watch start** (30 minutes plus the existing 15-minute finalization grace), whichever occurs first. No relaunch, reconnect, retry, Bluetooth reset, new run, control closeout, private export or manual recovery action.
6. Stop at the first unexpected prompt, crash/black screen, service destruction, connection failure, receipt-operation timeout, failure phase, deadline, or terminal result.

**Success-only offline recheck:** only if the Phone reports durable completion with an exact accepted Watch ACK/completion outcome, force-stop normal Phone HUGR once, leave the Watch untouched, then use the isolated Phone receipt-diagnostics reader once to confirm the durable local report after restart. Stop at the first report/failure or 60 seconds. Do not run this step after a timeout/failure.

**Failure evidence route:** after a failure, preserve the Watch **Saved Diagnostics** viewer first; then the isolated Phone receipt-diagnostics viewer. Capture a readable complete visual record if exact private sharing is unavailable. Do not open Main again, reconnect or retry. If the defined native-trace condition above applies, seek a separate bounded trace-capture authorization rather than improvising a capture.

## What would advance the ladder

A 30-minute success requires the fixed run to finalise, verified canonical custody, matching required manifests, exact Watch-accepted ACK/completion evidence, a durable Phone result and the success-only offline restart/readback. It remains a software/custody acceptance, not a physiological validation or a pending-callback drain claim.

If earned, the next physical rungs are two hours and then eight hours, one at a time with the same first-terminal-result rule. The passed V2 export from a successful run would contain original canonical selected-run bytes, relevant manifests/receipt/report and provenance for private independent verification/inventory. It can unlock the clarification agent’s existing real-evidence continuation, while native-IBI selection/window qualification, RMSSD, baseline/learning and the separate cardiac endpoint/final-batch work remain distinct.
