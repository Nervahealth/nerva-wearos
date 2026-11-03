from pathlib import Path
import hashlib,json,subprocess
root=Path('/home/ubuntu/HUGR_PHONE_READ_PATH_CORRECTION_2026_11_03');p=root/'preimages';out=root/'test-results'/'baseline';out.mkdir(parents=True,exist_ok=True)
rx=p/'modules/hugr-rxandroidble-observation/android/src/main/java/com/polidea/rxandroidble2/internal'
read=rx/'operations/CharacteristicReadOperation.java';cb=rx/'connection/RxBleGattCallback.java'
r=read.read_text();c=cb.read_text();start=r[r.index('    protected boolean startOperation('):r.index('\n    @Override',r.index('    protected boolean startOperation('))];a=c.index('        public void onCharacteristicRead(');callback=c[a:c.index('\n        @Override',a)]
source='''public class BaselineReadGap {
 static int apiCalls, nativeCalls, values; static boolean apiResult;
 static class BluetoothGatt { boolean readCharacteristic(Object c){apiCalls++;return apiResult;} }
 static class BluetoothGattCharacteristic {Object getUuid(){return new Object();}byte[] getValue(){return new byte[0];}}
 static class Base {void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int s){}}
 static class LoggerUtil {static void logCallback(Object... args){}}
 static class Dispatcher {void notifyNativeReadCallback(Object... args){nativeCalls++;}}
 static class Output {boolean hasObservers(){return true;}Relay valueRelay=new Relay();}
 static class Relay {void accept(Object v){values++;}}
 static class ByteAssociation<T> {ByteAssociation(T t,byte[] b){}}
 static class BleGattOperationType {static Object CHARACTERISTIC_READ=new Object();}
 static boolean propagateErrorIfOccurred(Object... args){return false;}
 static Dispatcher nativeCallbackDispatcher=new Dispatcher(); static Output readCharacteristicOutput=new Output();
 BluetoothGattCharacteristic bluetoothGattCharacteristic=new BluetoothGattCharacteristic();
'''+start+'''
 static class Callback extends Base {'''+callback+'''}
 public static void main(String[] args) { BaselineReadGap o=new BaselineReadGap();BluetoothGatt g=new BluetoothGatt(); apiResult=false;if(o.startOperation(g))throw new AssertionError();apiResult=true;if(!o.startOperation(g))throw new AssertionError();new Callback().onCharacteristicRead(g,o.bluetoothGattCharacteristic,0);if(apiCalls!=2||nativeCalls!=1||values!=1)throw new AssertionError();System.out.println("ORDINARY_ACTUAL_READ_METHODS_EXECUTED apiFalse=true apiTrue=true callback=true");System.out.println("FAIL_EXPECTED: no READ dispatch/API return/raw entry/completion checkpoint instrumentation in baseline");System.exit(1);}}
'''
(out/'BaselineReadGap.java').write_text(source)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),str(out/'BaselineReadGap.java')],check=True)
result=subprocess.run(['java','-cp',str(out),'BaselineReadGap'],capture_output=True,text=True)
(out/'probe.log').write_text(result.stdout+result.stderr)
(out/'BASELINE_FAILURE.json').write_text(json.dumps({'expectedFailure':result.returncode==1,'exitCode':result.returncode,'actualProductionMethods':['startOperation','onCharacteristicRead'],'seams':'exact method bodies; API, callback dispatcher, relay and logger doubles; no Rx runtime','sourceBindings':{str(f.relative_to(p)):hashlib.sha256(f.read_bytes()).hexdigest() for f in [read,cb]},'absenceChecks':{'readDispatchHookAbsent':'HugrReceiptReadObserver' not in start,'rawReadHookAbsent':'HugrReceiptReadObserver' not in callback}},indent=2)+'\n')
print(result.stdout,end='');assert result.returncode==1
