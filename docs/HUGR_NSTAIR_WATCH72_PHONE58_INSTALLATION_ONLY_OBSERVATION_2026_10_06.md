# HUGR NSTAIR — Watch72 / Phone58 Installation-Only Observation

**Observation date:** 2026-10-06
**Scope:** separately authorised in-place installation-only pass
**Status:** **STOP — INSTALLATION-ONLY OBSERVATION RECORDED; BOTH HUGR APPS UNOPENED**

> This record is an operator-reported installation observation. It does not establish a runtime, retained-session selection, BLE/GATT advertising, Phone scan/connection/service discovery, delivery, acknowledgement, completion, sensor recording, or qualification result.

## 1. Governing candidate identities

| Line | Admitted candidate | Package / version | SHA-256 |
|---|---|---|---|
| Watch | `HUGR_Watch72w_0.72.0_recovery-boundary-diagnostic-candidate.apk` | `com.hugr.wearos` / code `72`, `0.72.0-recovery-boundary-diagnostic-candidate` | `9fcfca6ac0f926acfb20ca402ca09c37ea9f2e40ab849f62795b9cffa64ed5f1` |
| Phone | `HUGR_Phone58p_2.0.58_recovery-boundary-diagnostic-candidate.apk` | `health.hugr.app` / code `58`, `2.0.58-recovery-boundary-diagnostic-candidate` | `5ba0e207b1a33c94294c7e02cd7c4db5cfd95d60dcf793f84e176ae0bec4376c` |

The governing source/artifact admission is `HUGR_NSTAIR_WATCH72_PHONE58_RECOVERY_BOUNDARY_CANDIDATE_ADMISSION_2026_10_06.md`.

## 2. Operator-reported installation facts

### Phone58

- Exact-file-selection response was supplied as the uncompleted template literal `yes/no`; therefore **exact Phone58 filename selection is not independently confirmed by this observation**.
- The Phone installer surface reported an ordinary **HUGR update**.
- Installer result: **`success`**.
- Phone HUGR was not opened.
- No unexpected warning or deviation was reported.

This establishes a successful ordinary in-place HUGR update route on the Phone, but it does not by itself prove that the selected downloaded file was the exact admitted Phone58 artifact rather than another signer-compatible HUGR update.

### Watch72

- Exact Watch72 APK selected: **yes**.
- The Watch installer surface reported an ordinary **HUGR update**.
- Installer result: **`success`**.
- The Watch app list was reported to show **`0.72 recovery boundary-…`**, consistent with the admitted Watch72 version line.
- No HUGR app was opened on Watch or Phone.
- No unexpected warning or deviation was reported.

This establishes an operator-reported ordinary in-place installation of the exact admitted Watch72 candidate. It is not an on-device package-manager/hash inspection.

## 3. Stop boundary and interpretation

The combined installation-only admission is complete and stops here. No HUGR application launch, Phone58 scan/reconnect, Watch72 retained-session probe, GATT server/service registration, advertising, diagnostic timeline, causal-event inspection, acknowledgement, completion, sensing, new recording, egress, readback, verifier, pairing change, reset, clear-data operation, or legacy/frozen-root operation occurred or is inferred.

A later physical gate, if separately authorised, must be a bounded no-press/runtime diagnostic observation designed only to distinguish:

1. retained fresh-v2 finalised-session eligibility and selection;
2. Watch standard-GATT service/advertising readiness or failure;
3. Phone58 root/startup and scan-to-connect-to-service-discovery boundary; and
4. any visible fail-closed diagnostic result.

It must remain prohibited from new sensing/recording, egress, acknowledgement, verifier execution, and any legacy/frozen-root or Watch58 operation unless separately authorised.

## 4. Unchanged evidence status

- **Watch69 five-minute run remains NOT QUALIFIED.**
- **Watch68 v1 remains UNCLASSIFIED / NOT VERIFIED.**
- **Watch58 remains exactly:** `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
- The retained binary egress compatibility payload was not exercised or validated.

## 5. Preservation closeout

This source-owned record is copied to the governed Phone/control documentation locations, then summarized in the Master Task List, proposed to the private Atlas Inbox without changing accepted S4, and mirrored last to the Alignment Surface. GitHub preservation is completed only after a no-force push and independent remote-head readback; that result is recorded in the closeout addendum.
