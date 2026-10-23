#!/usr/bin/env bash
# TEST-HARNESS ONLY. Direct Java/Kotlin compilation; no Gradle, APK, signing, device, or network.
set -uo pipefail
BASE=/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/direct-offline-continuation
DEST=/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_CONTINUATION_2026_10_23/watch
SOURCE=/home/ubuntu/HUGR_PHONE_EXPORT_SCRATCH_CORRECTION_2026_10_20/watch
JAVA=/usr/lib/jvm/java-17-openjdk-arm64/bin/java
COMPILER=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.0/2c8c3a7402becca891f12739b3e9fd2dc2adbd7a/kotlin-compiler-embeddable-1.9.0.jar
STDLIB=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.0/8ee15ef0c67dc83d874f412d84378d7f0eb50b63/kotlin-stdlib-1.9.0.jar
REFLECT=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.6.10/1cbe9c92c12a94eea200d23c2bbaedaf3daf5132/kotlin-reflect-1.6.10.jar
SCRIPT_RUNTIME=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-script-runtime/1.9.0/4b3102cbbb7e1b58d9d1adf89563f00069ffa7d1/kotlin-script-runtime-1.9.0.jar
DAEMON=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-daemon-embeddable/1.9.0/186543f5e28cf8d9a0290fecc2cf34301c40a65c/kotlin-daemon-embeddable-1.9.0.jar
TROVE=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar
ANNOTATIONS=/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/org.jetbrains/annotations/23.0.0/8cc20c07506ec18e0834947b84a864bfc094484e/annotations-23.0.0.jar
TOOL_CP="$COMPILER:$STDLIB:$REFLECT:$SCRIPT_RUNTIME:$DAEMON:$TROVE:$ANNOTATIONS"
BASE_PROD="$BASE/capture/runtime/production-kotlin-debug-classes"
ANDROID_COMPILE_API=/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/android-sdk/platforms/android-34/android.jar
CP=$(python3 - <<PY
import json
p='$BASE/frozen-runtime-closure-continuation/exact-command.json'
a=json.load(open(p)); print(a[a.index('-cp') + 1])
PY
)
rm -rf "$DEST/classes" "$DEST/recompiled-production"; mkdir -p "$DEST/classes" "$DEST/recompiled-production"
# Changed production sources compile to a first-classpath overlay; unchanged production classes stay
# in the captured admitted closure. This is JVM class output only, never an Android artifact.
PRODUCTION_COMPILER_STATUS=0
"$JAVA" -cp "$TOOL_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -Xfriend-paths="$BASE_PROD" -classpath "$ANDROID_COMPILE_API:$CP" -d "$DEST/recompiled-production" \
  "$SOURCE/app/src/main/java/com/hugr/wearos/BleGattService.kt" \
  "$SOURCE/app/src/main/java/com/hugr/wearos/SavedDiagnosticReport.kt" \
  "$SOURCE/app/src/main/java/com/hugr/wearos/WatchDiagnosticRuntime.kt" \
  >"$DEST/production-compiler.log" 2>&1
PRODUCTION_COMPILER_STATUS=$?
TEST_COMPILER_STATUS=125
if [ "$PRODUCTION_COMPILER_STATUS" -eq 0 ]; then
  "$JAVA" -cp "$TOOL_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
    -Xfriend-paths="$BASE_PROD" -classpath "$CP" \
    -d "$DEST/classes" "$DEST/ReceiptAckTimeoutActualCallbackContinuationTest.kt" \
    >"$DEST/test-compiler.log" 2>&1
  TEST_COMPILER_STATUS=$?
else
  printf 'SKIPPED: production compiler exit %s\n' "$PRODUCTION_COMPILER_STATUS" >"$DEST/test-compiler.log"
fi
if [ "$PRODUCTION_COMPILER_STATUS" -eq 0 ] && [ "$TEST_COMPILER_STATUS" -eq 0 ]; then
python3 - <<PY
import json
base='$BASE/frozen-runtime-closure-continuation/exact-command.json'; dest='$DEST'
a=json.load(open(base)); a[a.index('-Dhugr.repro.operationLog='+ '$BASE/frozen-runtime-closure-continuation/payload-free-operation.log')]='-Dhugr.repro.operationLog='+dest+'/payload-free-operation.log'
cpidx=a.index('-cp')+1; a[cpidx]=dest+'/classes:'+dest+'/recompiled-production:'+a[cpidx]
a[-1]='com.hugr.wearos.ReceiptAckTimeoutActualCallbackContinuationTest'
with open(dest+'/exact-command.json','w') as f: json.dump(a,f,indent=2); f.write('\n')
PY
START_NS=$(date +%s%N)
timeout 105s "$JAVA" $(python3 - <<PY
import json,shlex
a=json.load(open('$DEST/exact-command.json')); print(' '.join(shlex.quote(x) for x in a[1:]))
PY
) >"$DEST/execution.log" 2>&1
STATUS=$?
END_NS=$(date +%s%N)
else
  START_NS=$(date +%s%N)
  END_NS=$START_NS
  STATUS=${PRODUCTION_COMPILER_STATUS:-$TEST_COMPILER_STATUS}
  if [ "$STATUS" -eq 0 ]; then STATUS=$TEST_COMPILER_STATUS; fi
  printf 'JUnit not launched: productionCompilerExitCode=%s testCompilerExitCode=%s\n' \
    "$PRODUCTION_COMPILER_STATUS" "$TEST_COMPILER_STATUS" >"$DEST/execution.log"
fi
python3 - "$STATUS" "$START_NS" "$END_NS" "$PRODUCTION_COMPILER_STATUS" "$TEST_COMPILER_STATUS" <<PY
import hashlib,json,pathlib,sys
D=pathlib.Path('$DEST'); S=pathlib.Path('$SOURCE')
status,start,end,production_compiler_status,test_compiler_status=map(int,sys.argv[1:])
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
files=['ReceiptAckTimeoutActualCallbackContinuationTest.kt','inputs/ReceiptAckTimeoutActualCallbackReproductionTest.previous.kt','inputs/FreshRunDirectoryLinuxShadow.captured.kt','compile-and-run-offline.sh','exact-command.json']
prod=['BleGattService.kt','SavedDiagnosticReport.kt','WatchDiagnosticRuntime.kt']
events=(D/'payload-free-operation.log').read_text().splitlines() if (D/'payload-free-operation.log').exists() else []
log=(D/'execution.log').read_text() if (D/'execution.log').exists() else ''
result={'exitCode':status,'outcome':'PASS' if status==0 else 'FAIL','elapsedMs':round((end-start)/1_000_000,3),'testClass':'com.hugr.wearos.ReceiptAckTimeoutActualCallbackContinuationTest','testsRun':1 if 'Tests run: 1' in log else None,'productionCompilerExitCode':production_compiler_status,'testCompilerExitCode':test_compiler_status,'gradleInvoked':False,'networkUsed':False,'newApks':0,'conscryptMode':'OFF (test-only JDK crypto)','operationEvents':events,'operationEventCount':len(events),'harnessSha256':{f:digest(D/f) for f in files},'recompiledProductionSourceSha256':{f:digest(S/'app/src/main/java/com/hugr/wearos'/f) for f in prod},'recompiledProductionClassSha256':{str(p.relative_to(D/'recompiled-production')):digest(p) for p in sorted((D/'recompiled-production').rglob('*.class'))},'oldHarnessPreservationManifest':'/home/ubuntu/jobs/job_Dsc0ykUs/old-harness-pre-continuation.sha256','preservedAttemptOne':'preserved-attempt-1-before-harness-correction/SHA256SUMS','notes':['Actual production source CCCD callback and source characteristic initialization are prerequisites; no ready endpoint, manifest, hash, ACK state, or source release field is assigned.','Synthetic direct-JVM/Robolectric only; no Samsung SDK service start, radio, device, retained production data, or historical-cause claim.','The >10s case is deliberate session-lock injection, not natural timing or a Phone/Honor measurement.','Changed production classes are freshly compiled first on the test classpath; no APK/DEX/Gradle artifact was made.','The runner writes result.json after compiler or JUnit failure; previous attempt artifacts are preserved separately before this routine harness correction.']}
(D/'result.json').write_text(json.dumps(result,indent=2)+'\n')
PY
exit "$STATUS"
