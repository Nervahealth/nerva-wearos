# NSTAIR Watch71 One-Attempt Phone Connection Stop

**Date:** 2026-10-05  
**Scope executed:** The separately authorized Watch71 installation and one ordinary retained-session recovery attempt only.  
**Evidence class:** Operator-reported device observation, plus source-path interpretation. No device log, Phone screenshot/video, manifest acknowledgement, Watch completion marker, or source data was obtained.

## Installation boundary

The operator reported that the exact admitted artifact was installed as an ordinary **HUGR update**, with no HUGR app opened during installation:

- File: `HUGR_Watch71w_0.71.0_marker-independent-delivery-recovery-candidate.apk`
- Package: `com.hugr.wearos`
- Version: code `71`, `0.71.0-marker-independent-delivery-recovery-candidate`
- Artifact SHA-256: `4c5e6538c1abbd029f79bd300f503aaf201a003e6628d2a953ce567d1a1e47aa`

This establishes the reported ordinary-update boundary only. It is not independent device-package inspection and does not establish recovery or delivery.

## One-attempt recovery observation

The operator then reported the following bounded recovery sequence:

1. System Bluetooth settings showed the Watch and the Phone57 Honor phone as connected.
2. Normal Main HUGR was opened once on Watch71.
3. The Watch displayed: **“Preparing a new ordinary recording scope”** and **“The retained legacy journal and frozen package remain outside this startup path.”**
4. The operator attempted the authorized ordinary Phone57 connection, but Phone HUGR could not find or connect to the Watch despite the system Bluetooth pairing state.
5. After that failure, the operator made an unplanned attempt to restart the Phone HUGR app; it was reported as crashed/unusable. No connection, replay, acknowledgement, Phone canonical verification, Watch completion marker, or Phone/Watch terminal delivery screen was reported.

The Phone restart attempt is recorded as an operator-reported deviation after the initial connection failure, not as a confirmed app-process diagnosis. No blame or causal interpretation is assigned.

## Direct interpretation

The Watch text is an expected **protection boundary**, not evidence that a frozen or legacy root was opened, altered, or delivered. `FirstFramePresentation.freshScopePreparing()` supplies that exact text, and `BoundedNormalStartupGate` excludes the retained legacy journal and frozen completed-package root from its normal startup gate.

It does **not** prove that Watch71 selected the retained finalised fresh-v2 session or that its standard GATT server was discoverable. The Watch71 source route selects that session after first draw using the fresh-v2 journal only, then starts GATT-only recovery for the real durable source session; the required Phone discovery/connect step did not complete.

A system Bluetooth companion connection is not itself proof that Phone57 can discover the HUGR custom GATT service. Phone57's ordinary connection path scans for that advertised service and connects only after discovery; it reports a failure if scanning/connection/service discovery does not complete.

## Outcome and stop

**STOP — PHONE57 ORDINARY HUGR DISCOVERY/CONNECTION DID NOT COMPLETE; REPORTED PHONE APP CRASH/UNUSABLE STATE AFTER FAILURE.**

- No terminal Phone manifest equality evidence was observed.
- No exact Phone acknowledgement was observed.
- No Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` or `BOUNDED_RUN_GATT_STOP_REQUESTED` marker was observed.
- No new recording, sensor run, egress action, readback action, legacy-root operation, frozen-root operation, reset, data clear, uninstall, or Watch58 action is claimed.
- The existing Watch69 five-minute run remains **NOT QUALIFIED**.
- Watch68 v1 remains **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 remains exactly **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**.

The one recovery attempt is consumed. No physical retry, re-open, reconnect, re-pair, reset, or further phone/watch action is authorized by this receipt.

## Smallest useful next decision

Before any further physical action, a focused source/test-only investigation should trace the Watch71 recovery-mode GATT advertiser lifecycle and Phone57's ordinary custom-service discovery/connect/crash boundary. It should determine whether the Watch source starts and advertises the standard GATT service after retained-session selection, whether Phone57 can distinguish scan absence from connection/service-discovery failure, and whether the reported Phone app failure is source-addressable. No new build or device action is implied by this recommendation.

## Preservation status

This receipt is the source-owned record of the physical stop. It was copied to the governed Phone/control records, reflected in the Master Task List, entered as a **new** private Atlas Inbox proposal without changing accepted S4, and mirrored in the Alignment Surface last.

The previously blocked GitHub route was restored for this closeout. Non-force publication and direct remote readback confirm:

- Watch source branch `preservation/watch68-partial-source-2026-10-03` at `6a8491b6b51163815f21669b22a63df8ac35fb4f` in `Nervahealth/nerva-wearos`.
- Phone/control-copy branch `preservation/phone-control-and-source-2026-10-03` at `6a96980848371b97d68c05fe83326be560bd1cfc` in `Nervahealth/nerva-mobile`.
- Independent-control branch `preservation/watch69-control-records-2026-10-03` at `72c881d53bb167228232848a94351c1410137d14` in `Nervahealth/nerva-mobile`.

The existing sanitized fallback is retained despite this verified publication. This remote-preservation fact does not qualify Watch69 or authorize any further device action.
