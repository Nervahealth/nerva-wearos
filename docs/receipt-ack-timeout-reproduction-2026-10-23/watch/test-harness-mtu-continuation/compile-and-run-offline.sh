#!/usr/bin/env bash
# TEST-HARNESS ONLY. No Gradle, network, APK, production-source, or production-class change.
set -euo pipefail
ROOT=/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/direct-offline-continuation
DEST=/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/test-harness-mtu-continuation
JAVA=/usr/lib/jvm/java-17-openjdk-arm64/bin/java
COMPILER=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.0/2c8c3a7402becca891f12739b3e9fd2dc2adbd7a/kotlin-compiler-embeddable-1.9.0.jar
STDLIB=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.0/8ee15ef0c67dc83d874f412d84378d7f0eb50b63/kotlin-stdlib-1.9.0.jar
REFLECT=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.6.10/1cbe9c92c12a94eea200d23c2bbaedaf3daf5132/kotlin-reflect-1.6.10.jar
SCRIPT_RUNTIME=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-script-runtime/1.9.0/4b3102cbbb7e1b58d9d1adf89563f00069ffa7d1/kotlin-script-runtime-1.9.0.jar
DAEMON=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-daemon-embeddable/1.9.0/186543f5e28cf8d9a0290fecc2cf34301c40a65c/kotlin-daemon-embeddable-1.9.0.jar
TROVE=/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar
ANNOTATIONS=/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1/org.jetbrains/annotations/23.0.0/8cc20c07506ec18e0834947b84a864bfc094484e/annotations-23.0.0.jar
CP=$(python3 - <<'PY'
import json
p='/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/direct-offline-continuation/frozen-runtime-closure-continuation/exact-command.json'
a=json.load(open(p)); print(a[a.index('-cp') + 1])
PY
)
rm -rf "$DEST/classes"; mkdir -p "$DEST/classes"
"$JAVA" -cp "$COMPILER:$STDLIB:$REFLECT:$SCRIPT_RUNTIME:$DAEMON:$TROVE:$ANNOTATIONS" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -Xfriend-paths="$ROOT/capture/runtime/production-kotlin-debug-classes" \
  -classpath "$CP" -d "$DEST/classes" "$DEST/ReceiptAckTimeoutActualCallbackReproductionTest.kt" \
  >"$DEST/compiler.log" 2>&1
python3 - <<'PY'
import json
root='/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/direct-offline-continuation'
dest='/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/test-harness-mtu-continuation'
a=json.load(open(root+'/frozen-runtime-closure-continuation/exact-command.json'))
i=a.index('-Dhugr.repro.operationLog='+root+'/frozen-runtime-closure-continuation/payload-free-operation.log')
a[i]='-Dhugr.repro.operationLog='+dest+'/payload-free-operation.log'
cpidx=a.index('-cp')+1
a[cpidx]=dest+'/classes:'+a[cpidx]
with open(dest+'/exact-command.json','w') as f: json.dump(a,f,indent=2); f.write('\n')
PY
set +e
START_NS=$(date +%s%N)
timeout 90s "${JAVA}" $(python3 - <<'PY'
import json, shlex
p='/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/test-harness-mtu-continuation/exact-command.json'
a=json.load(open(p)); print(' '.join(shlex.quote(x) for x in a[1:]))
PY
) >"$DEST/execution.log" 2>&1
STATUS=$?
END_NS=$(date +%s%N)
set -e
python3 - "$STATUS" "$START_NS" "$END_NS" <<'PY'
import hashlib,json,pathlib,sys
D=pathlib.Path('/home/ubuntu/HUGR_RECEIPT_ACK_TIMEOUT_REPRODUCTION_2026_10_23/watch/test-harness-mtu-continuation')
status,start,end=map(int,sys.argv[1:])
files=['ReceiptAckTimeoutActualCallbackReproductionTest.kt','inputs/ReceiptAckTimeoutActualCallbackReproductionTest.original.kt','inputs/FreshRunDirectoryLinuxShadow.captured.kt','exact-command.json','compile-and-run-offline.sh']
hashes={f:hashlib.sha256((D/f).read_bytes()).hexdigest() for f in files}
events=(D/'payload-free-operation.log').read_text().splitlines() if (D/'payload-free-operation.log').exists() else []
log=(D/'execution.log').read_text() if (D/'execution.log').exists() else ''
result={
 'exitCode':status,'outcome':'PASS' if status==0 else 'FAIL','elapsedMs':round((end-start)/1_000_000,3),
 'testClass':'com.hugr.wearos.ReceiptAckTimeoutActualCallbackReproductionTest',
 'testsRun': 1 if 'Tests run: 1' in log else None,
 'testOnlyChanges':['actual callback.onMtuChanged(syntheticPhone, 128) after actual connection-state callback','payload-free System.nanoTime timestamp on each observer event'],
 'productionClasses':'frozen captured production-kotlin-debug-classes; unchanged','newApks':0,'gradleInvoked':False,'networkUsed':False,'conscryptMode':'OFF','operationEvents':events,'operationEventCount':len(events),'sourceSha256':hashes,
 'originalPreparedFailureRetained': {'path':'../direct-offline-continuation/frozen-runtime-closure-continuation/result.json','failure':'R1_ZERO_READ response status 257 before MTU negotiation'},
 'notes':['Synthetic direct-JVM/Robolectric callback reproduction only; no SDK, radio, device, or historical-cause claim.','Invalid ACK expected GATT_FAILURE; false response and null-server R1 paths remain asserted.']
}
(D/'result.json').write_text(json.dumps(result,indent=2)+'\n')
PY
exit "$STATUS"
