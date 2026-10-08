# Watch76–Phone59: one filtered exception capture

**2026-10-08 CEST — source-owned decision and approval proposal, not a device execution.** Neil directs simplification: live Watch logging and a fresh timestamped marker reaching Fold are established. Stop icon exploration, repeated marker checks, repeated screenshot requests and file-format gating. Current physical result remains Watch76 recovery STOP after resume, not qualification.

## Decision

**Choose a bounded Fold screen recording of an exact-tag-filtered remote-shell stream. It is sufficient for this particular caught resume-preparation/application failure if the exception message and relevant stack are readable and complete.** No personal computer or Bugjaeger text export is required now. A text file is preferable for automated parsing, but parsing thousands of rows is not this question: one exception block should identify the failing source method/line. Video loses machine-searchability, not essential diagnostic content, provided all relevant lines remain visible. It does not independently verify recording bytes, native Phone durability, full digest, ACK custody or historical deletion.

Established checks are accepted without repetition: selected Watch is SM-L320, Fold has connected and displayed live Watch logs, and the fresh timestamped synthetic marker appeared. Connection **now** is not assumed from yesterday; if the approved current session cannot connect once using existing ADB trust/current port, STOP. No pair deletion, companion migration or new marker test.

## Logging verified in source AND admitted APK

Unchanged artifact: `HUGR_Watch76w_0.76.0_multisegment-finalized-delivery-candidate.apk`, SHA-256 `c95d6b01a273dbbefdabfb68c6b0f69f52f7e48bf21d9b73a135c0595a815956`.

Read-only DEX inspection of **classes3.dex** confirms the actual `BleGattService.failSourceResumePreparation` invokes Android **`Log.e(String, String, Throwable)`**, not the message-only overload. Its message begins `Source resume preparation failed: `; the source performs this log before `abortTransportLineage("source_resume_prepare_failed")`. Both asynchronous preparation and transport-thread application catches pass the original throwable to it. APK method names and source line tables are retained, so the throwable stack can distinguish the failed substep despite the generic outer heading:

| Candidate boundary | Source/APK evidence |
|---|---|
| Journal finalization / manifest drain | `prepareSourceReplay`, lines 1705–1706; any thrown journal frame identifies its operation |
| Retained-session integrity/selection | `requireRetainedFinalizedDeliverySession`, called at line 1708; specific corruption messages |
| Resume high-water validation | `prepareSourceReplay`, line **1715**: `Phone resume index exceeds watch journal` |
| Apply conflicting frozen window | `applyPreparedSourceReplay`, lines 1729–1730: `Conflicting source resume changed the frozen replay window` |
| Apply stale BLE lineage | `applyPreparedSourceReplay`, lines 1732–1733: `Source resume arrived outside the active BLE lineage` |
| Release / queue / encoding downstream of apply | Original throwable frames identify `releasePreparedSourceIfReady` and the throwing helper |

This verifies a logging path in source and the admitted binary, **not actual fresh emission on hardware**. No production change, diagnostic candidate, APK build, logger-level change or broad retest is needed before this capture. Android documents that an `UnknownHostException` cause can suppress throwable stacks; the identified local journal/control guards are not network lookups. If the actual output lacks actionable frames for any reason, record that limitation instead of declaring a cause.

## Proposed single capture-and-reproduction pass — approval required

1. **Fold only:** retain Honor as Phone59 companion. If needed, enable temporary Watch system ADB/wireless debugging and connect Fold once to the current Watch port using existing trust. Do not change Bluetooth companion pairing. In the already-used Bugjaeger **brackets/shell**, with Watch selected, run the command below. No `adb` prefix, export button, file permission, `-c`, `-f`, private-root read or remote file write.
2. Start Fold's native **screen recording**, keep the shell visible in landscape at readable size, and confirm the recorder is running and the command has not returned an error. No new Logcat sentinel. A quiet screen is expected before HUGR emits a matching warning/error. If the output is clipped or cannot be recorded legibly, STOP before launching HUGR.
3. Launch **normal Main HUGR on Watch76 once**, without controls; note wall time. Only if the current white heading says **CURRENT RECOVERY ADVERTISING READY**, promptly open **normal Phone59 on Honor once** and press **Connect Galaxy Watch once**. Fold remains recording the shell throughout. No wrist placement is needed: this is retained delivery, not sensing.
4. At the **first failure/terminal result**, STOP all HUGR actions; preserve Honor's first visible source/final-equality status and Watch's current stop/completion heading. If neither occurs, stop at **15 minutes from Watch service start** (use launcher time as a conservative earlier cap if start time is not visible). Capture loss, black screen/crash, failed readiness, service destruction/disconnect, source/transport failure, unexpected prompt or state also ends the attempt. No relaunch, reconnect, retry, new samples, egress, reset or legacy/frozen action.
5. Keep only the passive Fold recording running for **up to two further minutes** so the deliberately slowed traceback can finish; this does not extend HUGR authority or its service deadline. Preserve the exception block from first message through all HUGR frames and any `Caused by`/`Suppressed` sections. Stop/save the screen recording through the familiar native recorder. Disconnect the Fold diagnostic session and switch temporary Watch wireless/ADB debugging off; do not reset/clear data. Review locally and share only the readable exception segment and first terminal statuses, masking pairing addresses or unrelated/private content if present.

**Exact shell command (one paste):**

```sh
printf 'WATCH ERROR CAPTURE ARMED\n'; logcat -b main -v time -T 1 'HUGR-BleGatt:W' '*:S' | while IFS= read -r line; do printf '%s\n' "$line"; sleep 1; done
```

`HUGR-BleGatt:W` includes warnings, errors and higher severity under this exact tag, suppressing unrelated tags. This is a **tag filter**, not a message search that could hide stack continuation lines. `-T 1` starts at the recent tail and continues live; a carried-over row is history, not the new attempt. The shell delays display by one second per line so screen recording cannot miss a traceback appended in one fast UI update. Original log timestamps and text remain intact; no source records are modified. `WATCH ERROR CAPTURE ARMED` is only shell text, not another Watch log sentinel. Do not leave this passive command unattended indefinitely; end the ADB session after the bounded capture.

### Evidence acceptance

Required: new-attempt timestamp and `HUGR-BleGatt` tag; `Source resume preparation failed` message; exception class/message; throwing HUGR method/source line and caller frames distinguishing preparation/application; nested causes if present; first terminal status and approximate launch/Connect times. All these lines must be fully readable. Executor/framework tail frames that add no cause need not become a file-perfection gate. A terminal heading alone, clipped top frame, missing cause, command exit before the event or loss of ADB is **insufficient**, with no automatic retry.

## Concrete fallback if readable video capture is unavailable

Use an **operator-owned Windows 10/11 computer** on the permitted same Wi-Fi, official [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools), and PowerShell. Android Studio, a hospital-computer install, VPN and a new HUGR APK are unnecessary. Extract Platform Tools to `C:\HUGR-debug\platform-tools`; temporarily enable Watch wireless debugging, pair with `adb pair <pair-address:port>` using the code locally, then `adb connect <connection-address:port>` and select the exact Watch from `adb devices -l`. After separate approval, start this **host-side** live capture before one HUGR attempt:

```powershell
.\adb.exe -s <WATCH_ADDRESS:CONNECT_PORT> logcat -b main -v threadtime -T 1 'HUGR-BleGatt:W' '*:S' > C:\HUGR-debug\watch76-resume-error.txt
```

Ctrl+C ends logging after the first result/deadline. Read that known local file in Notepad, retain the relevant exception block, and redact before upload. No file is written on Watch; no `run-as`, `pull`, journal access, log clearing or network bypass. This fallback is conditional, **not a personal-computer requirement now**.

## After evidence

Use the actual throwable and substep, not the unconfirmed synthetic 8184 hypothesis, to select the repair. Reproduce the observed retained-state history through **every manifest** using current Watch and Phone code: Phone-prestored records, manifest-only delivery, advancement after each exact ACK, final segment, restart/interruption and hash/session/index rejection. Preserve source identity/bytes; no blind high-water relaxation. Build one integrated repair candidate only if warranted after tests. A verified recording still requires Phone native durable full range/count/bytes/SHA equality, attributable exact Watch ACK and matching completion—not this log video or a displayed partial ACK.

**Closeout:** source-owned decision → governed copies → Master → affected private VIS-COUNT only if permitted (known-denied Atlas permission is not retried; accepted S4 untouched) → Alignment last → bounded documentation commit and non-force GitHub head/tree readback. No device action has been taken by Manus or granted by this proposal.

**Sovereign status:** Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

**References:** [Android Log API: throwable overload](https://developer.android.com/reference/android/util/Log), [Android Logcat filtering](https://developer.android.com/tools/logcat), [AOSP Logcat `-T` help and implementation](https://android.googlesource.com/platform/system/logging/+/refs/heads/main/logcat/logcat.cpp). These document the command/API, not a tested Fold shell-recording result.
