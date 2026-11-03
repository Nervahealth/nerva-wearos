#!/usr/bin/env bash
set -euo pipefail
OUT=/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03/final-two-case
BASE=$(cat "/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03/LATEST_FOCUSED_OUT.txt")
PRIOR=/home/ubuntu/HUGR_PHONE_CALLBACK_ENTRY_CORRECTION_2026_11_03/test-results/patched-20261103T013605Z-271832
RUNTIME=/home/ubuntu/HUGR_PHONE70_RETENTION_CORRECTION_2026_11_01/test-runtime
CP="$BASE/kotlin:$BASE/java:$PRIOR/kotlin:$PRIOR/java:$RUNTIME/json-20180813.jar:$RUNTIME/android-all-14-robolectric-10818077.jar:$RUNTIME/kotlin-stdlib-1.9.0.jar:$RUNTIME/kotlin-script-runtime-1.9.0.jar"
mkdir -p "$OUT/adapter-classes"
printf '%s\n' "$CP" > "$OUT/CLASSPATH.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$CP" -d "$OUT/adapter-classes" "$OUT/FreshnessStoreAdapter.java" > "$OUT/adapter-javac.log" 2>&1
export FRESHNESS_CP="$OUT/adapter-classes:$CP"
timeout 90s node "$OUT/run-freshness.cjs" > "$OUT/execution.log" 2>&1
