#!/usr/bin/env bash
set -euo pipefail
ROOT=/home/ubuntu/HUGR_PHONE_READ_PATH_CORRECTION_2026_11_03
PHONE=/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone
RUNTIME=/home/ubuntu/HUGR_PHONE70_RETENTION_CORRECTION_2026_11_01/test-runtime
PRIOR=/home/ubuntu/HUGR_PHONE_CALLBACK_ENTRY_CORRECTION_2026_11_03/test-results/patched-20261103T013605Z-271832
OUT=$(cat "$ROOT/LATEST_FOCUSED_OUT.txt")
SRC=$PHONE/modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal
CP="$OUT/java:$RUNTIME/json-20180813.jar:$RUNTIME/android-all-14-robolectric-10818077.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$PRIOR/java:$PRIOR/kotlin"
CCP=$(printf '%s:' "$RUNTIME"/*.jar)
mkdir -p "$OUT/full-reader-classes" "$OUT/lifecycle-classes" "$ROOT/test-results/ts"
java -cp "$CCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CP" -d "$OUT/full-reader-classes" "$SRC/ReceiptReadProbeStore.kt" "$SRC/ReceiptCallbackProbeStore.kt" "$SRC/ReceiptIncidentDiagnosticStore.kt" "$SRC/ReceiptNativeWriteObservationStore.kt" "$SRC/ReceiptDiagnosticPrivateExport.kt" "$SRC/HUGRSavedReceiptDiagnosticsActivity.kt" > "$OUT/full-reader-compile.log" 2>&1
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$OUT/java:$PRIOR/java:$RUNTIME/android-all-14-robolectric-10818077.jar" -d "$OUT/lifecycle-classes" "$SRC/nativeobservation/NativeObservationModuleLifecycle.java" > "$OUT/lifecycle-compile.log" 2>&1
TYPE_STUB_FILE="$ROOT/test-results/ts/native-external-seams.d.ts" RESULT_FILE="$ROOT/test-results/ts/READ_COORDINATOR_RESULT.json" node "$ROOT/tests/run-read-coordinator.cjs" > "$ROOT/test-results/ts/execution.log" 2>&1
python3 -c "import runpy;runpy.run_path('$ROOT/tests/read-source-guards.py')"
echo PASS_READ_INTEGRATION_COMPILERS_TS_AND_SOURCE_GUARDS
