#!/usr/bin/env bash
# Existing Phone70 direct JVM/Kotlin harness convention; offline finite work only.
set -euo pipefail
ROOT=/home/ubuntu/HUGR_PHONE_CALLBACK_ENTRY_CORRECTION_2026_11_03
PHONE=/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone
RUNTIME=/home/ubuntu/HUGR_PHONE70_RETENTION_CORRECTION_2026_11_01/test-runtime
OLDTEST=/home/ubuntu/HUGR_PHONE70_RETENTION_CORRECTION_2026_11_01/tests/RetentionCorrectionFocusedProbe.kt
MODE=${1:?baseline or patched}
OUT="$ROOT/test-results/$MODE-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT"/{java,kotlin,slice,synthetic-artifacts}
export OUT PHONE ROOT MODE
python3 "$ROOT/tests/prepare-callback-source-slice.py"
RX=$PHONE/modules/hugr-rxandroidble-observation/android/src/main/java
SRC=$PHONE/modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal
if [[ $MODE == baseline ]]; then RX=$ROOT/preimages/modules/hugr-rxandroidble-observation/android/src/main/java; fi
COMPILER_CP="$RUNTIME/kotlin-compiler-embeddable-1.9.0.jar:$RUNTIME/kotlin-reflect-1.9.0.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$RUNTIME/kotlin-script-runtime-1.9.0.jar:$RUNTIME/trove4j-1.0.20200330.jar:$RUNTIME/annotations-13.0.jar"
APP_CP="$OUT/java:$RUNTIME/json-20180813.jar:$RUNTIME/android-all-14-robolectric-10818077.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$RUNTIME/kotlin-script-runtime-1.9.0.jar"
{
  echo start=$(date --iso-8601=seconds)
  echo mode=$MODE
  echo boundary=EXACT_PRODUCTION_CALLBACK_METHOD_SOURCE_SLICE_WITH_EXPLICIT_COLLABORATOR_DOUBLES
  echo not_proven=actual_RxAndroidBle_class_RxJava_completion_Android_platform_callback_or_device
  find "$OUT/slice" -name '*.java' -print > "$OUT/sources.txt"
  printf '%s\n' "$RX/com/polidea/rxandroidble2/internal/util/HugrReceiptWriteObserver.java" "$RX/com/polidea/rxandroidble2/internal/util/HugrReceiptWriteTrace.java" >> "$OUT/sources.txt"
  if [[ $MODE != baseline ]]; then printf '%s\n' "$RX/com/polidea/rxandroidble2/internal/util/HugrCallbackEntryLane.java" >> "$OUT/sources.txt"; fi
  find "$SRC/nativeobservation" -name '*.java' ! -name 'NativeObservationModuleLifecycle.java' -print >> "$OUT/sources.txt"
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$APP_CP" -d "$OUT/java" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1
  KSOURCES=("$SRC/ReceiptIncidentDiagnosticStore.kt" "$SRC/ReceiptNativeWriteObservationStore.kt" "$SRC/ReceiptNativeWriteFramePublisher.kt" "$SRC/ReceiptDiagnosticPrivateExport.kt" "$OLDTEST" "$ROOT/tests/CallbackEntryFocusedProbe.kt")
  if [[ $MODE == baseline ]]; then KSOURCES[5]="$ROOT/tests/CallbackEntryFocusedProbe.baseline.kt"; fi
  if [[ $MODE != baseline ]]; then KSOURCES+=("$SRC/ReceiptCallbackProbeStore.kt"); fi
  sha256sum $(cat "$OUT/sources.txt") "${KSOURCES[@]}" "$RUNTIME"/*.jar > "$OUT/bindings.sha256"
  java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$APP_CP" -d "$OUT/kotlin" "${KSOURCES[@]}" > "$OUT/kotlinc.log" 2>&1
  set +e
  timeout 120s java -Dhugr.callback.mode="$MODE" -Dhugr.callback.artifacts="$OUT/synthetic-artifacts" -cp "$OUT/kotlin:$APP_CP" health.hugr.sourcejournal.CallbackEntryFocusedProbe > "$OUT/probe.log" 2>&1
  result=$?
  set -e
  echo probe_exit=$result
  echo end=$(date --iso-8601=seconds)
  exit "$result"
} > "$OUT/execution.log" 2>&1
