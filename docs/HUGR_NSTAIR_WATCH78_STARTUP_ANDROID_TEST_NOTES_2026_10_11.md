# Focused Watch78 Android-facing startup regression

User authority: source/test investigation and correction only plus one passive local Saved Diagnostics read. No APK build, installation or recording retry.

Production source under test: `FreshRunRuntime.admit/current` and `MainActivity` asynchronous receipt-read/action containment. Android SDK 34 under Robolectric 4.12.2, real disposable filesystem canonical/aliased app directories, `ContextWrapper.noBackupFilesDir` using `/data/user/0 -> /data/data` shape.

## Covered cases

- Raw app-anchor alias rejected by original strict receipt path before admission; corrected trusted-anchor path creates a real protected `SourceJournal`, publishes the initial receipt, begins the synthetic session and publishes revision 2 over the existing receipt.
- No automatic record admission before `begin`; no fabricated sample or identity. Runtime teardown writes interrupted failure and fresh-process-style current read reopens the same actual run/source UUID.
- Direct package/no-backup symlinks rejected. Pending selection, corrupt pointer, pointer symlink and selected-root symlink fail closed with no fallback.
- Actual Main layout async failure persists diagnostic and latches STOP, disables all three fresh-action buttons, prevents another read from overwriting STOP.
- Original READY-gate/false-request combination demonstrably throws on re-entry; corrected terminal latch blocks the real Start control.
- A real receipt worker held on the runtime monitor while the UI is tapped cannot defer the gate, admit a session or schedule admission. Controls are disabled while the read is pending.

## Test-platform corrections (not production relaxations)

- Default Robolectric `StatFs` reports less than the protected journal's 510,373,396-byte requirement. Only the disposable alias fixture was registered with sufficient available blocks through `ShadowStatFs`; production capacity logic is unchanged.
- Android `Os` directory descriptors use a test-only shadow backed by real Linux directory `FileChannel.force(true)` and actual atomic rename. No fsync or rename is made a no-op.
- An initial joined compile happened before the test dependency change landed, producing unresolved Robolectric references; that interrupted run is retained as failed history. A full-suite stale source-string guard assertion was updated to examine the shared equivalent guard; exclusion and failure conditions remain tested.

## Final full Watch result

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/android-sdk-watch-recovery-2026-10-03 ./gradlew testDebugUnitTest --no-daemon --console=plain`

**217 total; 213 passed, 4 existing fixture-dependent skips; 0 failures/errors.** Includes 6 Android-admission/Activity methods and 2 pure before/after path methods. XML copied to `validation/watch-final-xml` before the joined run can replace Gradle's latest test output.

All data is synthetic. This tests real production methods through Robolectric and Linux fixtures, not physical Watch filesystem/Os/power-loss durability, sensor callbacks, service lifetime or BLE. A normal source/test compilation may produce Gradle's unit-test container; it does not assemble, sign or admit an installable candidate APK. Installed Watch78 and Phone61 remain unchanged.
