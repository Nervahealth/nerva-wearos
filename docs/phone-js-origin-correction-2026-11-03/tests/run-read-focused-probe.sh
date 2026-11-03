#!/usr/bin/env bash
# Existing callback harness extension only. Offline direct javac/Kotlin; no Gradle/app/APK build.
set -euo pipefail
ROOT=/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03
PHONE=/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone
RUNTIME=/home/ubuntu/HUGR_PHONE70_RETENTION_CORRECTION_2026_11_01/test-runtime
OUT="$ROOT/test-results/patched-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT"/{java,kotlin,slice,synthetic-artifacts}
export ROOT PHONE OUT
python3 -c "import runpy;runpy.run_path('$ROOT/tests/prepare-read-source-slice.py')"
SRC=$PHONE/modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal
COMPILER_CP="$RUNTIME/kotlin-compiler-embeddable-1.9.0.jar:$RUNTIME/kotlin-reflect-1.9.0.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$RUNTIME/kotlin-script-runtime-1.9.0.jar:$RUNTIME/trove4j-1.0.20200330.jar:$RUNTIME/annotations-13.0.jar"
APP_CP="$OUT/java:$RUNTIME/json-20180813.jar:$RUNTIME/android-all-14-robolectric-10818077.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$RUNTIME/kotlin-script-runtime-1.9.0.jar"
{
 echo start=$(date --iso-8601=seconds)
 echo boundary=PRODUCTION_READ_SINGLE_RESPONSE_WRAPPER_CLASSES_EXACT_CALLBACK_WITH_EXPLICIT_RX_ANDROID_DOUBLES
 java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$APP_CP" -d "$OUT/java" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1
 KSOURCES=("$SRC/ReceiptReadProbeStore.kt" "$SRC/ReceiptIncidentDiagnosticStore.kt" "$SRC/ReceiptNativeWriteObservationStore.kt" "$ROOT/tests/ReadPathFocusedProbe.kt" "$OUT/slice/ReadBridgeSourceSlice.kt" "$OUT/slice/ReadReaderSourceSlice.kt")
 # Reuse prior compiled native-write helper classes for unchanged legacy incident parsing only.
 PRIOR=/home/ubuntu/HUGR_PHONE_CALLBACK_ENTRY_CORRECTION_2026_11_03/test-results/patched-20261103T013605Z-271832
 APP_CP="$APP_CP:$PRIOR/java:$PRIOR/kotlin"
 sha256sum $(cat "$OUT/sources.txt") "${KSOURCES[@]}" "$RUNTIME"/*.jar > "$OUT/bindings.sha256"
 java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$APP_CP" -d "$OUT/kotlin" "${KSOURCES[@]}" > "$OUT/kotlinc.log" 2>&1
 python3 -c "from pathlib import Path;import json; p=Path('$PRIOR/synthetic-artifacts/unmatched-old-path-still-unchanged.SYNTHETIC.saved.utf8.txt'); lines=p.read_text().splitlines(); bodies=[json.loads(l) for l in lines if l.startswith('{')]; b=next(b for b in bodies if b.get('format')=='HUGR_PHONE_RECEIPT_INCIDENT_V3');Path('$OUT/synthetic-old-fixture.json').write_text(json.dumps(b,separators=(',',':'))+'\\n')"
 set +e
 timeout 120s java -Dhugr.read.artifacts="$OUT/synthetic-artifacts" -Dhugr.read.oldfixture="$OUT/synthetic-old-fixture.json" -cp "$OUT/kotlin:$APP_CP" health.hugr.sourcejournal.ReadPathFocusedProbe > "$OUT/probe.log" 2>&1
 result=$?
 set -e
 echo probe_exit=$result
 echo end=$(date --iso-8601=seconds)
 echo "$OUT" > "$ROOT/LATEST_FOCUSED_OUT.txt"
 exit "$result"
} > "$OUT/execution.log" 2>&1
