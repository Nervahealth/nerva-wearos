# Watch82 readable-receipt candidate — Watch-only build and artifact admission

## Authority and latest disposition

Neil authorized **one signer-continuous Watch-only candidate build and artifact admission at 23:59 Europe/Oslo, 18 October 2026**. Preparation crosses midnight into 19 October. Phone62's application, production code, artifact and installed state remain unchanged; documentation-only standing preservation mirrors are not a Phone rebuild.

**Latest disposition: Watch82 ordinary installation/update and Settings confirmation OPERATOR-REPORTED; Main remains unopened.** Build/admission evidence below remains valid and preserved. No app launch, passive device receipt read, receipt closeout, connection, new recording or qualification is authorized/performed by this update. The reported installation is recorded as evidence, not retroactive authority for further device actions.

Source baseline: Watch local `2f01475910bda33a13bd83baf4072a8ce4b16a3f`, remote `0085be32b75c0d41aeb07f2b9417015730ac6001`, exact tree `55ab3aa837f93190b56f3b238e19fc39c4ddb514`; completed readiness correction receipt `HUGR_NSTAIR_WATCH81_READINESS_ADMISSION_UI_SOURCE_TEST_CORRECTION_2026_10_18.md`. The exact clean local Watch/Phone heads and production/protected hashes at preparation are captured in `validation/preparation-baseline.json`.

The source correction already passed 33 focused tests, the complete Watch suite (248 passed / four explicit skips), all six fresh actual-wire joined acceptance phases, 96 native Phone report/custody/restart/offline tests, and 148 Phone deterministic passes / two explicit skips. These are synthetic production-path tests, not hardware proof.

## Why the existing receipt does not have to be resolved before this build

Building and inspecting an APK occurs on the sandbox computer. It neither opens nor modifies the Watch's existing receipt. We can therefore build the readable candidate while that receipt remains preserved and unresolved.

Resolving its state is required **before another recording, not before an offline candidate build**. After a separately authorized update and passive Main read, the actual source ID, run ID, receipt revision and phase must be exposed before any state-dependent decision. Saved STARTED/FINALIZED never authorize another Start. No data clearing, fabricated historical UUID, silent interruption or qualification is permitted.

A phase-specific source review accompanies final admission. In particular, the current receipt-only action is not a general finalized-data recovery command: `FreshRunRuntime.openReceiptOnly()` currently rewrites both STARTED and FINALIZED to FAILED/PROCESS_INTERRUPTED if its no-active-runtime guard passes. It is not authorized here, and must not be pressed merely to reveal a FINALIZED receipt. The explicit retained-v2 action must not be assumed to select an isolated v3 run. Any missing FINALIZED-v3 recovery dependency must be stated, not worked around or implemented under this build-only authority.

## Exact authorized computer-side build steps

1. Verify the controlled Watch branch is clean and its source matches the exact preserved correction. Confirm protected SourceJournal, retained export, receipt/runtime, Health and GATT hashes; keep Phone62 unchanged.
2. Change only Watch `versionCode 81 → 82` and version name to `0.82.0-readable-receipt-admission-candidate`. Add dedicated Watch82 packaging/admission helpers by adapting the verified Watch81 helpers. No permission, source-root, journal, timer, protocol, ACK, sensor, recovery, signer or behavior changes are included.
3. Preserve this preparation on the existing Watch/Phone/control preservation branches using the controlled both-repository write preflight and non-force mirror; independently read back exact heads, trees and changed bytes before building.
4. In the controlled Watch worktree, run one established Gradle build/test invocation with the existing Java17/Android SDK:

   ```sh
   JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
   ANDROID_HOME=/home/ubuntu/android-sdk-watch-recovery-2026-10-03 \
   ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
   ```

5. Stage that source-built APK with the exact unchanged previously admitted compatibility DEX, align it and sign via the existing local Watch signing configuration using `scripts/package_watch82.py`. Do not expose/copy credentials or replace the signer.
6. Run `scripts/admit_watch82.py <the-one-final-APK> --out <admission-directory>`. Verify package `com.hugr.wearos`, code82/name, exact Watch81 signer continuity, ZIP/alignment/unique classes, compiled readiness presentation and recheck/identity controls, launcher task isolation, exact receipt/ACK bridge, unchanged compatibility payload, protected source, and absence of test-only mock classes. Correlate actual compiled admission/identity branches where available rather than claiming causality from string presence alone.
7. Upload only the final admitted APK and SHA-256 sidecar; independently download and compare complete bytes/hashes. Preserve the final admission receipt and controls on GitHub and read back exact remote trees/source/artifact-record bytes. Deliver one definitive artifact link; leave installation and passive read for separate approval.

All signing and build commands are executed by Manus in the authorized workspace, not by Neil on the hospital computer. No Watch/Phone debugging connection is needed for this build.

## Intended sole artifact

`HUGR_Watch82w_0.82.0_readable-receipt-admission-candidate.apk`

Candidate package: `com.hugr.wearos`. Candidate version code: `82`. Candidate version name: `0.82.0-readable-receipt-admission-candidate`. Expected existing Watch signer SHA-256: `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706`.

Exact artifact identity and complete uploaded-byte readback are recorded below after the sole build/admission succeeded. Intermediate debug/unsigned/aligned APKs are not installation artifacts and will not be offered as alternatives.

## Preserved physical and historical boundaries

At build/admission time, installed Watch81/Phone62 remained unchanged. The later Watch82 installation report is recorded below; Phone62 has no reported new action in this message. Latest physical selected receipt identity/revision is unknown; STARTED was visible before the earlier approximately18:03 Start press. The earlier attempt remains STOP / NOT QUALIFIED. No current sensor, timer, finalization, canonical-byte, exact ACK/completion or physical acceptance proof is inferred from source/build admission.

No receipt-only action, retained delivery, Start, Connect/reconnect, pairing/reset, uninstall/data clearing, frozen/legacy action, private physiological/media transfer, Word edit, accepted S4 change, engine/clarification implementation or N1 is authorized here.

Watch69: **NOT QUALIFIED**. Watch68 v1: **UNCLASSIFIED / NOT VERIFIED**. Watch58: **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

Standing closeout remains automatic: source-owning receipt → Master → affected private proposal only when authorized → Alignment last → bounded commits → controlled both-repository preflight/non-force mirror → independent exact remote head/tree/byte readback. The objective remains one completely verified short recording, then the duration ladder/eight-hour rehearsal and existing live-observation bridge.

## Completed artifact admission — 19 October 2026

The authorized Gradle build/test command was invoked **once** and completed successfully. Exact build input: local Watch `73086cc26667d2f78cce8590ce5b8ee471611a27`, tree `0c4b1872e45daebb7c26f42821ffda5a07b54048`, independently preserved/read back before the build. Candidate metadata and packaging/admission helpers were the only changes after the validated readiness source baseline. No recording/receipt/runtime/SDK/transport/ACK/recovery behavior source changed during this build.

| Field | Verified result |
|---|---|
| Sole final APK | `HUGR_Watch82w_0.82.0_readable-receipt-admission-candidate.apk` |
| Package | `com.hugr.wearos` |
| Version code | `82` (greater than installed Watch81) |
| Version name | `0.82.0-readable-receipt-admission-candidate` |
| Exact APK size | `12947205` bytes |
| SHA-256 | `d8045ef0a2764bfd4c817488a301dbbd88a4456db7c038b2a4c8e7d4d5df8617` |
| Signer SHA-256 | `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706` — exact Watch81 match |
| Watch test suite | 252 total: 248 passed, 0 failed, 0 errors, 4 explicit skips |
| ZIP / alignment / unique DEX classes | PASS; 8172 unique class definitions |
| Protected journal and retained exporter | Exact unchanged SHA-256 matches |
| Compatibility DEX | Exact admitted Watch77 bytes, SHA-256 `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784` |
| Launcher isolation, receipt/ACK bridge, readiness/recheck/identity classes | PASS |
| Test-only Activity/model/NoBackup/Samsung harness packaging checks | PASS; same-named real SDK classes not misclassified as test shadows |
| Signed DEX branch/order correlation | PASS; 9 explicit checks |
| Uploaded APK and SHA-256 sidecar | Complete independent downloads match local bytes and hash |

**Definitive artifact:** [Watch82 APK](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/QYcdCRgJTUGuZluW.apk). [Matching SHA-256 sidecar](https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/RUiHeOnZnYRLYgxg.sha256). Intermediate debug/unsigned/aligned APKs are not admitted installation choices.

The signed DEX inspection confirms Main's active-runtime/receipt-read/action gates, click-time eligibility recheck before scheduling, both source/run identity comparisons, phase-dependent headings, unresolved-state rejection before new UUID generation, and whole-content scroll with identity preceding actions. Kotlin lays the rejected admission throw in a cold block after the successful body; inspection followed the actual conditional branch target rather than assuming source-string order. Neither that inspection nor synthetic tests prove the current physical receipt state, Android round-display legibility, sensors/radio, finalized source bytes or clinical validity. No second APK build or source change was needed.

## Resolving the existing receipt — before a recording, not before this build

**The receipt is unchanged. We do not yet know its current source/run/revision/phase.**

After separately authorized Watch82 installation and one passive Main read (first stable result/error, at most60 seconds; a short passive scroll only to read existing ID/revision/phase detail; no buttons/Phone/Connect), use this decision:

- **STARTED with no active runtime:** propose a separately authorized one-shot guarded receipt-only interruption. The current action changes only the selected receipt to revision+1 `FAILED / PROCESS_INTERRUPTED`, retaining source/run IDs, timing, masks and manifest/ACK inventory under store invariants. It preserves source files but replaces the current receipt file rather than archiving prior receipt-file versions. Preserve the observed pre-action identity/revision and verify the post-action identity/revision/phase/reason. This is abandonment as an unqualified interrupted run, **not finalization or qualified data delivery**.
- **Active runtime:** STOP. Receipt-only refuses it; do not kill/force-stop or bypass. Any actual run stop/finalization requires a separately authorized controlled, bounded Health-owned path.
- **FINALIZED:** preserve it. **Do not press Serve current fresh receipt only**: that implementation also rewrites FINALIZED to FAILED. The explicit retained-v2 control selects another root and is not v3 recovery. Persisted/inactive finalized-v3 delivery after process loss is an existing technical gap, not permission to reinterpret or discard the final receipt. Only if this phase is actually observed do we take a separately authorized source/test decision for exact v3 recovery/delivery.
- **FAILED / COMPLETED / no receipt:** potentially eligible under the existing guard, but no fresh Start is authorized here. App completion remains distinct from independently verified Phone custody.
- **Error, ambiguous/unknown ID, corrupt receipt or no stable result:** STOP without retry, closeout or new Start.

The code's receipt-only service is bounded to two minutes; this is not physical shutdown proof. No such service was started by this task. Detailed read-only source findings and exact functions/roots are preserved in `docs/evidence/watch82-readable-receipt-build-2026-10-19/UNRESOLVED_RECEIPT_ROUTE_REVIEW.md`.

## Final preservation and remaining boundary

The sole final artifact, SHA-256 sidecar and admission metadata have complete uploaded-byte readback. Final source-owned receipt → Master → no affected authorized private proposal → Alignment last → bounded commits → controlled non-force both-repository GitHub preflight/mirror → independent exact head/tree/record-byte readback is the completion route. Exact final remote identities are in `validation/final-independent-remote-readback.json` after successful publication; no main merge or force push is allowed.

Watch82 installation is now operator-reported below. One passive current-receipt read remains proposed and is not authorized/performed now. Phone62 application, production/build code, artifact and device remain unchanged. Historical failed/unclassified/frozen statuses above remain exact. No qualified fresh physical recording or N1 is claimed. Known archived Phone whole-repository TypeScript errors remain unchanged; this Watch-only build is not a Phone typecheck repair or Phone build.

## Watch82 installation report — 19 October 2026, 00:28 Europe/Oslo

Neil reports: “ok new watch apk installed the normal way and confirmed on settings app list not opened”. In the immediately preceding admitted-candidate context, this records the ordinary Watch82 update, Settings app-list confirmation and explicit unopened state. It is operator-reported installation evidence, not an independent installed-binary checksum or literal version-name transcription. Do not request another installation merely because no literal Settings version was typed.

The message reports no new Phone action; the last admitted/installed Phone62 baseline remains retained without inventing a fresh confirmation. No Main launch, new Start, receipt-only action, retained delivery, Connect, Bluetooth/pairing change, source-root/private-data access, sensing, recording or receipt mutation was performed by Manus. No test rerun, build or production change is needed to preserve this report.

**Next proposed action, requiring separate explicit authorization:** one passive Watch82 Main receipt read, first stable result/error or at most 60 seconds. Read the actual source ID, run ID, receipt revision and phase/reason; a short passive scroll only for existing detail is acceptable. No action buttons, Phone HUGR/Connect, Bluetooth/settings change, retry/relaunch or receipt closeout. This is identification, not a new recording or a fix.

The earlier receipt remains unresolved until that observation. If inactive STARTED is actually found, consider a separately authorized guarded metadata-only interruption; if FINALIZED, preserve it and do not use receipt-only (which rewrites it FAILED) or assume retained-v2 selects v3. Active runtime/error/unclear identity stops. No automatic closeout, no Start through STARTED/FINALIZED, no manufactured finalization or qualification. Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.
