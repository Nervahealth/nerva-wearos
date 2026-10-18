# Watch82 readable-receipt candidate — Watch-only build and artifact admission

## Authority and latest disposition

Neil authorized **one signer-continuous Watch-only candidate build and artifact admission at 23:59 Europe/Oslo, 18 October 2026**. Preparation crosses midnight into 19 October. Phone62's application, production code, artifact and installed state remain unchanged; documentation-only standing preservation mirrors are not a Phone rebuild.

**Preparation only; artifact build/admission pending.** No installation, app launch, passive device receipt read, receipt closeout, connection, new recording or qualification is authorized or performed by this record.

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

Exact artifact hash and complete uploaded-byte readback will be added only after build/admission succeeds. Intermediate debug/unsigned/aligned APKs are not installation artifacts and will not be offered as alternatives.

## Preserved physical and historical boundaries

Installed Watch81/Phone62 remain unchanged. Latest physical selected receipt identity/revision is unknown; STARTED was visible before the earlier approximately18:03 Start press. The earlier attempt remains STOP / NOT QUALIFIED. No current sensor, timer, finalization, canonical-byte, exact ACK/completion or physical acceptance proof is inferred from source/build admission.

No receipt-only action, retained delivery, Start, Connect/reconnect, pairing/reset, uninstall/data clearing, frozen/legacy action, private physiological/media transfer, Word edit, accepted S4 change, engine/clarification implementation or N1 is authorized here.

Watch69: **NOT QUALIFIED**. Watch68 v1: **UNCLASSIFIED / NOT VERIFIED**. Watch58: **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

Standing closeout remains automatic: source-owning receipt → Master → affected private proposal only when authorized → Alignment last → bounded commits → controlled both-repository preflight/non-force mirror → independent exact remote head/tree/byte readback. The objective remains one completely verified short recording, then the duration ladder/eight-hour rehearsal and existing live-observation bridge.
