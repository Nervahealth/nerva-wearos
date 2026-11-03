from pathlib import Path
import os, hashlib, json
root=Path(os.environ['ROOT']); phone=Path(os.environ['PHONE']); out=Path(os.environ['OUT']); mode=os.environ['MODE']
source=(root/'preimages' if mode=='baseline' else phone)/'modules/hugr-rxandroidble-observation/android/src/main/java/com/polidea/rxandroidble2/internal/connection/RxBleGattCallback.java'
text=source.read_text(); start=text.index('        @Override\n        public void onCharacteristicWrite('); end=text.index('\n        @Override',start+10)
method=text[start:end]
def write(path,text):
 p=out/'slice'/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n')
write('android/bluetooth/BluetoothGatt.java','package android.bluetooth; public class BluetoothGatt { public static final int GATT_SUCCESS=0; }')
write('android/bluetooth/BluetoothGattCharacteristic.java','''package android.bluetooth;
public class BluetoothGattCharacteristic {
 private final java.util.UUID uuid;
 public BluetoothGattCharacteristic(java.util.UUID uuid) { this.uuid=uuid; }
 public java.util.UUID getUuid(){return uuid;}
 public byte[] getValue(){return new byte[0];}
}''')
for name in ['CharacteristicReadOperation','CharacteristicWriteOperation']:
 write('com/polidea/rxandroidble2/internal/operations/'+name+'.java','package com.polidea.rxandroidble2.internal.operations; public class '+name+' {}')
write('com/polidea/rxandroidble2/internal/connection/CallbackSourceSlice.java','''package com.polidea.rxandroidble2.internal.connection;
import android.bluetooth.*;
import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteObserver;
/** Test-only exact production method slice, NOT a compiled complete RxBleGattCallback or Rx runtime. */
public final class CallbackSourceSlice {
 public static boolean throwLogger; public static int loggingCalls, nativeCalls, values, errors;
 public static boolean observers=true;
 static class LoggerUtil { static void logCallback(String s,Object g,int status,Object c,boolean read){ loggingCalls++; if(throwLogger)throw new IllegalStateException("SYNTHETIC_LOGGER_FAILURE"); } }
 static class Dispatcher { void notifyNativeWriteCallback(Object g,Object c,int s){nativeCalls++;} }
 static class Relay { void accept(Object o){values++;} }
 static class Output { Relay valueRelay=new Relay(); boolean hasObservers(){return observers;} }
 static class ByteAssociation<T>{ ByteAssociation(T id,byte[] b){} }
 static class BleGattOperationType { static final Object CHARACTERISTIC_WRITE=new Object(); }
 static boolean propagateErrorIfOccurred(Output o,Object g,Object c,int s,Object type){ if(s!=0){errors++;return true;}return false; }
 static final Dispatcher nativeCallbackDispatcher=new Dispatcher();
 static final Output writeCharacteristicOutput=new Output();
 public static void invoke(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,int status){ new BluetoothGattCallback(){
'''+method+'''
 }.onCharacteristicWrite(gatt,characteristic,status); }
}''')
(out/'EXACT_CALLBACK_METHOD.java.txt').write_text(method+'\n')
(out/'SLICE_BOUNDARY.json').write_text(json.dumps({'source':str(source),'whole_source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'method_sha256':hashlib.sha256(method.encode()).hexdigest(),'exact_method_text':True,'whole_rx_class_compiled':False,'doubles':['BluetoothGatt','BluetoothGattCharacteristic','LoggerUtil','native dispatcher','hasObservers/relay','error propagation','operation marker types'],'actual_production':['callback method body','HugrReceiptWriteObserver','HugrReceiptWriteTrace','frame serializer','publisher','stores','private copy/stream helpers'],'not_claimed':'RxJava/actual platform callback/physical ATT'},indent=2)+'\n')
