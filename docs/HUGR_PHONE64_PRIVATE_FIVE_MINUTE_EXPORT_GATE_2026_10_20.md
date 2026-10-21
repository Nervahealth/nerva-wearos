# Phone64 — authorized private five-minute export and independent inventory gate

## Current status and authority

**PRIVATE EXPORT AUTHORIZED; USER DEVICE STEP NOT YET REPORTED; ACTUAL ZIP NOT RECEIVED; INDEPENDENT REAL VERIFICATION NOT RUN. Real research-data handoff NOT COMPLETE.**

On 20 October 2026 at 08:51:58 Europe/Oslo, Neil authorized: “Authorize one private export of the completed five-minute run on Honor, followed by independent package verification and descriptive inventory.” Phone64 installation-only was already operator-confirmed: normal in-place update, expected Settings version `2.0.64-selected-run-private-export-candidate`, HUGR unopened, nothing unusual.

This authority is one existing-run private export, not a new recording or recovery. No duration rehearsal may be active. Do not initiate or interrupt duration work. Keep Watch83 and Bluetooth untouched. No Main HUGR, Saved Source Evidence, Connect/reconnect, pairing/reset, uninstall/data clearing, sensing, replay, pending-append recovery, ACK/state changes, frozen/legacy-root action, live portal ingestion or N1. Raw records stay outside GitHub and outside shared/public destinations.

## Exact selected identity and external anchors

| Field | Required value |
|---|---|
| Source session | `24ca3a75-62ab-4912-91a0-d50613b02928` |
| Completed run | `46628727-a977-4dd7-b4e6-19e196efe383` |
| Expected canonical records | 8,349 |
| Expected manifests / exact Watch-accepted ACKs | 6 / 6 |
| Original accepted canonical parent bytes | 539,746 |
| Canonical parent SHA-256 | `a71ff129b07b53c5c67f2d2160746d8a5de4c887997cde921dfb8898b9f6b470` |
| Requested duration | 300,000 ms |

The source/run/counts match the preserved Watch82/Phone62 physical acceptance receipt. The parent hash is the preserved report-declared comparison anchor carried in the export source/test receipt, not a previously recomputed hash of real canonical bytes in this environment. These expectations are not the recording artifact. They must be compared to actual exported bytes before any inventory is accepted.

## One-shot Honor procedure

1. Only if no duration rehearsal is active, open **HUGR Selected Completed-Run Export** once on Honor. Select by the full launcher name: both this surface and **HUGR Saved Source Evidence** use Android's information “i” icon. Do not choose by icon alone. If the label is ambiguous or the opened surface does not display the exact exporter title and two UUID fields, stop and report; do not try another HUGR launcher.
2. Enter the source UUID above in **Exact source session UUID** and the run UUID above in **Exact completed run UUID**. Do not choose a latest-run fallback or another source.
3. Press **Prepare selected package** once. Leave the exporter visible. Wait for **Prepared selected package. bytes=…; sha256=…**. Note the displayed ZIP byte count and SHA-256 or capture only that status. This ZIP checksum is different from the canonical-parent checksum.
4. Press **Save package…** once. Use Android's document chooser to save locally on **Honor internal storage → Downloads**, not a shared/cloud folder. Keep the proposed `.zip` filename. If a destination asks to overwrite an existing file, stop instead of overwriting. Do not use Share for this pass.
5. Wait for **Saved. The externally saved copy is user-owned. Original source and report remain unchanged.** Then leave HUGR and upload the original saved ZIP as an attachment in this existing private Manus conversation, along with the displayed ZIP byte count/SHA if available. Do not unzip, edit, rename package members or repackage it. Uploading the original ZIP is the authorized private handoff; no email, public link or GitHub upload is needed.

Stop at the first **Export stopped**, Save failure, crash, missing/mismatched identity, unexpected permission/reset/connection prompt, ambiguous launcher, or five minutes after Prepare without a prepared result. Stop at two minutes after confirming the save destination without **Saved**. These are observation caps, not evidence of corruption. Stop controls and report the exact displayed text; no retry, second Prepare, recovery workaround, force-stop or device reboot is authorized. If the initial correct exporter fields never appear within 60 seconds, stop without relaunch.

The exporter uses the existing 180,000 ms bounded snapshot/core-verification deadline and a separate 120,000 ms packaging allowance after success; Save has its own 120,000 ms I/O limit. The caps do not widen production budgets. App success is an operator/device result only; it does not substitute for independent ZIP verification.

ZIP SHA/CRC check integrity but do not encrypt the package or authenticate its origin. A local device save followed by this private task attachment is chosen to avoid unintended shared destinations. The Activity/provider are isolated in `:selectedRunExport`, but ordinary React/Expo Application bootstrap remains as recorded in admission; this is not a claim that generic lifecycle/initialization is absent.

## Independent reader readiness and later verification

The restored independent verifier has been compared byte-for-byte to `scripts/verify_hugr_export.py` at preserved Phone commit `b1d199a5c754ffe6cf094d5b1d77d0772db8d189`. SHA-256: `bde121230dfb16f8fed3982e1a2c843e14a32ded5224ea4e9ab20ad0c7303c5e`; exact remote-source equality and Python syntax check PASS. This reuses the completed source/test reader rather than creating a new mechanism or repeating a synthetic capacity programme.

A task-specific private analysis directory is prepared with owner-only directory permissions. No actual private ZIP has arrived; no real-data verifier has run. After the original ZIP is attached, preserve it unchanged and compute its ZIP SHA/byte count. If the operator provides the displayed ZIP checksum, compare it. Then execute the existing independent reader with all external anchors:

```bash
python3 verify_hugr_export.py actual-selected-run.zip \
  --expected-source 24ca3a75-62ab-4912-91a0-d50613b02928 \
  --expected-run 46628727-a977-4dd7-b4e6-19e196efe383 \
  --expected-canonical-bytes 539746 \
  --expected-canonical-sha256 a71ff129b07b53c5c67f2d2160746d8a5de4c887997cde921dfb8898b9f6b470 \
  --output-dir private-inventory
```

Require exactly eight package members, member/ZIP integrity, full canonical-parent hash/size, per-record UUID/framing/CRC/indices, exact record-index manifest membership/hashes/ranges, source/run agreement, unchanged original report/decoded receipt binding and six exact completed Watch ACK tuples. Also compare the resulting record count 8,349, six manifests/six Watch ACKs, requested duration and observed exporter Phone package/version against the anchors. Reject missing, altered, mismatched or incomplete material. No canonical re-encoding or automatic repair.

On PASS, read the actual inventory and deliver descriptive counts for CARDIAC, EDA, ACCEL, SKIN_TEMP and DEVICE_HEALTH; recorded timestamp ranges, shared/nonmonotonic timestamp observations, established schema-unit descriptions and explicit unknown producer/status/clock/chronology limits. Canonical row counts are not beat counts. The existing reader does not render physiological payload values or qualify native IBI chronology/RMSSD. Earlier historical report-chain parents are not included in the minimal package and are not independently requalified. No qualified RMSSD, baseline, learning-ownership or Loop A action.

## Preservation and research separation

The selected recording, old acceptance/installation receipts and substrate/source are unchanged by this preparation. Analysis outputs and the actual ZIP belong in this task-specific private area, not in a source/control archive, project shared-file folder, public server or GitHub. This prepares the existing portable evidence/Watchtower boundary, not a second telemetry platform or live ingestion.

Controlled Git worktrees and dedicated repository-write login remain absent after reset; the previous write-preflight HTTP 403 is unresolved and no changed-access evidence is supplied. No known-denied write is repeated. The source-owned gate receipt, pending Master and Alignment-last documentation are preserved only in a separately checksummed/read-back sanitized fallback. **REMOTE CLOSEOUT PENDING**; this is not a Git commit or remote preservation claim. Real recording data/report/ZIP bytes are excluded from the fallback.

Physical five-minute recording/custody PASS, synthetic capacity PASS, exporter source/test PASS and installation-only completion remain distinct from real research-data handoff **NOT COMPLETE**. S4, accepted adapter/companion, T2.2, October reconciliation, RMSSD, Loop A and learning ownership are unchanged.

Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

## Physical export refusal — 20 October 2026 09:01:04 Europe/Oslo

# Phone64 private export — scratch-root refusal STOP

## Current disposition

**OPERATOR-REPORTED EXPORT REFUSAL / STOP. No usable real ZIP supplied; independent package verification not run; real research-data handoff NOT COMPLETE. No correction, build or retry authorized by this report.**

At 09:01:04 Europe/Oslo on 20 October 2026, Neil reported: “It says expor5 stopped.export scratch root unsafe orinals were not changed”. This is consistent with the existing Activity-rendered `Export stopped: EXPORT_SCRATCH_ROOT_UNSAFE. Originals were not changed.` No screenshot or independent device trace is supplied; preserve the operator report rather than invent an exact observed code transcription or trigger/tap history.

The one-shot gate has reached its stop rule. No second Prepare, Save/Share, relaunch, force-stop, reboot, cache/data clearing, Main HUGR, Connect/reconnect, Bluetooth/Watch action, recording or recovery follows. Ask Neil, without further device interaction, whether this followed one Prepare press and whether any Save/Share or retry occurred. Do not assume deviations or blame the operator.

## Read-only source interpretation — not a physical root-cause claim

The existing `SelectedCompletedRunExporter.kt` `createSafeDirectory` calls `requireNoSymlinkAncestors` before and after scratch-directory creation. That helper walks the absolute path upward to the filesystem root and rejects any symbolic-link ancestor. The same `EXPORT_SCRATCH_ROOT_UNSAFE` code also covers directory creation failure, non-directory/symlink scratch root, and the no-symlink-ancestor checks. The reported code alone does not identify which predicate or ancestor failed.

This is an export scratch/cache-directory safety refusal, not a verdict of missing or corrupt canonical recording bytes. A legitimate Android app-directory alias/symlink is a plausible source hypothesis, not a demonstrated Honor cause. No safety guard is removed or bypassed. The app's generic “Originals were not changed” wording is a device-reported/source-backed guarantee statement, not independent reinspection of Honor custody. Source paths that create/copy/export use scratch; canonical/report originals are not an authorized correction target.

The report does not establish completion of package preparation, successful external Save/Share or actual data transfer. These remain unreported rather than falsely audited. No actual ZIP was received in this task.

## Proposed next scope — requires explicit authorization

One focused source/test-only investigation of Android scratch-directory handling: reproduce normal Android app-directory aliases and genuine unsafe symlinks/escapes; determine the demonstrated failing predicate; correct only a demonstrated scratch-root admission defect while retaining containment, no traversal/escape, state-preservation and read-only-source safeguards. Test exporter→independent verifier using permitted synthetic material plus rejection cases. No build/install/device retry/private-record access or new collection is included. First recover authoritative source/control lineage and the necessary test environment; do not patch incomplete recovered fragments as a new source baseline. Any candidate and real export retry remain separate gates.

## Preservation

This STOP receipt precedes pending Master and Alignment-last updates. No private VIS-COUNT proposal is authorized/affected. Missing controlled Git worktrees and unresolved earlier write-preflight403 remain; no changed-access evidence or known-denied write retry. Local sanitized fallback only, not commits or GitHub preservation; remote closeout PENDING. Previous authority/installation snapshots preserved unchanged. No physiological records, real reports, ZIP payloads or APK binaries included.

Physical five-minute recording/custody PASS, synthetic capacity/export source-test PASS and operator installation completion remain distinct from this failed physical export. No retrospective invalidation of the passed recording or independent retained-byte claim. S4, accepted adapter/companion, T2.2, October reconciliation, RMSSD, Loop A, learning and N1 unchanged.

Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

## Single Prepare confirmed — 20 October 2026 10:35:25 Europe/Oslo

# Phone64 export STOP — single Prepare confirmed

At 10:35:25 Europe/Oslo on 20 October 2026, Neil confirmed: “Yes after one Prepare selected package press, before any Save/Share or retry”.

**Tap-sequence uncertainty resolved: one Prepare press; refusal before any Save/Share; no retry. Export remains STOPPED.** The earlier 09:01:04 reported scratch-root refusal remains the physical observation. This later confirmation supersedes only the earlier unresolved tap-history field; the earlier receipt/archive remain unchanged.

This supports a preparation-stage failure, not user misuse or an external save/share failure. It does not establish the particular failed scratch-path predicate or Android ancestor. A legitimate Android directory alias remains a source hypothesis, not a demonstrated Honor cause. No missing/corrupt canonical-byte verdict or independent retained-data readback follows.

No ZIP supplied; independent real verification not run; real research-data handoff NOT COMPLETE. No further device check, Prepare, Save/Share, relaunch, force-stop, cache/data clear, Bluetooth/Watch action or recording is requested. No engineering correction or build is authorized by this factual confirmation.

Next proposed gate remains a focused source/test-only Android scratch-directory investigation and demonstrated correction with containment, symlink-escape, read-only original-state and independent synthetic-package regression coverage. Explicit authorization is required; builds and device retries remain separate.

Source-owned export gate → pending Master → Alignment last, no affected private proposal. Missing controlled Git worktrees/write login and unresolved previous403 remain; no denied write retried. Sanitized checksum/exact-byte-read-back fallback only; remote closeout PENDING, not Git commits or GitHub preservation. No real physiology/report/media/APK payloads included. Prior recording/custody PASS, synthetic tests/capacity, installation, S4, adapter/companion, T2.2, October reconciliation, RMSSD, Loop A, learning and N1 unchanged.

Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.
