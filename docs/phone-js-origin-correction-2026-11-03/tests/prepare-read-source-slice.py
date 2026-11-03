from pathlib import Path
import os,hashlib,json
phone=Path(os.environ['PHONE']);out=Path(os.environ['OUT']);rx=phone/'modules/hugr-rxandroidble-observation/android/src/main/java'
def w(path,text):
 p=out/'slice'/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n')
def java(path,text):w(path+'.java',text)
java('android/bluetooth/BluetoothGatt','''package android.bluetooth;public class BluetoothGatt {public boolean result=true,throwApi;public int calls;public Runnable onRead;public boolean readCharacteristic(BluetoothGattCharacteristic c){calls++;if(throwApi)throw new IllegalStateException("SYNTHETIC_API_THROW");if(onRead!=null)onRead.run();return result;}public Device getDevice(){return new Device();}public static class Device{public String getAddress(){return "SYNTHETIC";}}}''')
java('android/bluetooth/BluetoothGattCharacteristic','''package android.bluetooth;public class BluetoothGattCharacteristic {private final java.util.UUID uuid;public BluetoothGattCharacteristic(java.util.UUID u){uuid=u;}public java.util.UUID getUuid(){return uuid;}public byte[] getValue(){return new byte[]{7};}}''')
java('android/bluetooth/BluetoothGattCallback','''package android.bluetooth;public class BluetoothGattCallback{public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int s){com.polidea.rxandroidble2.internal.connection.RxBleGattCallback.superCalls++;}}''')
java('bleshadow/javax/inject/Named','package bleshadow.javax.inject;public @interface Named{String value();}')
java('io/reactivex/functions/Cancellable','package io.reactivex.functions;public interface Cancellable{void cancel() throws Exception;}')
java('io/reactivex/disposables/Disposable','package io.reactivex.disposables;public interface Disposable{}')
java('io/reactivex/Emitter','package io.reactivex;public interface Emitter<T>{void onNext(T v);void onComplete();boolean tryOnError(Throwable t);}')
java('io/reactivex/ObservableEmitter','package io.reactivex;public interface ObservableEmitter<T> extends Emitter<T>{void setCancellable(io.reactivex.functions.Cancellable c);}')
java('io/reactivex/Observer','package io.reactivex;public interface Observer<T>{void onSubscribe(io.reactivex.disposables.Disposable d);void onNext(T t);void onComplete();void onError(Throwable e);}')
java('io/reactivex/Scheduler','package io.reactivex;public class Scheduler{}')
java('io/reactivex/Observable','''package io.reactivex;
import java.util.function.*;
/** TEST DOUBLE ONLY: synchronous callback/filter/first chain; no Rx scheduler/concurrency/disposal/timeout semantics claimed. */
public class Observable<T>{
 public interface Source<T>{void attach(Observer<T> observer);}
 final Source<T> source;public Observable(Source<T> s){source=s;}
 public Observable<T> filter(Predicate<T> p){return new Observable<>(o->source.attach(new Observer<T>(){public void onSubscribe(io.reactivex.disposables.Disposable d){o.onSubscribe(d);}public void onNext(T v){if(p.test(v))o.onNext(v);}public void onComplete(){o.onComplete();}public void onError(Throwable e){o.onError(e);}}));}
 public Single<T> firstOrError(){return new Single<>(o->source.attach(new Observer<T>(){boolean done;public void onSubscribe(io.reactivex.disposables.Disposable d){o.onSubscribe(d);}public void onNext(T v){if(!done){done=true;o.onNext(v);o.onComplete();}}public void onComplete(){}public void onError(Throwable e){if(!done){done=true;o.onError(e);}}}));}
 public void subscribe(Observer<T> o){source.attach(o);}
}''')
java('io/reactivex/Single','''package io.reactivex;import java.util.function.*;public class Single<T> extends Observable<T>{public Single(Source<T>s){super(s);}public <R> Single<R> map(Function<T,R> f){return new Single<>(o->source.attach(new Observer<T>(){public void onSubscribe(io.reactivex.disposables.Disposable d){o.onSubscribe(d);}public void onNext(T v){o.onNext(f.apply(v));}public void onComplete(){o.onComplete();}public void onError(Throwable e){o.onError(e);}}));}public Single<T> timeout(long t,java.util.concurrent.TimeUnit u,Scheduler s,Single<T> fallback){return this;}public Observable<T> toObservable(){return this;}public static <T> Single<T> error(Throwable e){return new Single<>(o->o.onError(e));}}''')
java('com/polidea/rxandroidble2/internal/QueueOperation','''package com.polidea.rxandroidble2.internal;public abstract class QueueOperation<T>{protected abstract void protectedRun(io.reactivex.ObservableEmitter<T> e,com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface q);protected abstract com.polidea.rxandroidble2.exceptions.BleException provideException(android.os.DeadObjectException e);}''')
java('com/polidea/rxandroidble2/internal/serialization/QueueReleaseInterface','package com.polidea.rxandroidble2.internal.serialization;public interface QueueReleaseInterface{void release();}')
for name in ['BleException','BleGattCallbackTimeoutException','BleGattCannotStartException','BleDisconnectedException']:
 parent='RuntimeException' if name=='BleException' else 'BleException'
 java('com/polidea/rxandroidble2/exceptions/'+name,f'package com.polidea.rxandroidble2.exceptions;public class {name} extends {parent}{{public static final int UNKNOWN_STATUS=-1;public {name}(Object... args){{}}}}')
java('com/polidea/rxandroidble2/exceptions/BleGattOperationType','package com.polidea.rxandroidble2.exceptions;public class BleGattOperationType{public static final BleGattOperationType CHARACTERISTIC_READ=new BleGattOperationType();}')
java('com/polidea/rxandroidble2/internal/connection/ConnectionModule','package com.polidea.rxandroidble2.internal.connection;public class ConnectionModule{public static final String OPERATION_TIMEOUT="timeout";}')
java('com/polidea/rxandroidble2/internal/logger/LoggerUtil','''package com.polidea.rxandroidble2.internal.logger;public class LoggerUtil{public static boolean throwLogger;public static int calls;public static void logCallback(Object... args){calls++;if(throwLogger)throw new IllegalStateException("SYNTHETIC_LOGGER_THROW");}public static String commonMacMessage(Object g){return "SYNTHETIC";}public static String wrap(Object c,boolean b){return "SYNTHETIC";}}''')
java('com/polidea/rxandroidble2/internal/util/ByteAssociation','package com.polidea.rxandroidble2.internal.util;public class ByteAssociation<T>{public final T first;public final byte[] second;public ByteAssociation(T t,byte[] b){first=t;second=b;}}')
java('com/polidea/rxandroidble2/internal/util/ByteAssociationUtil','''package com.polidea.rxandroidble2.internal.util;public class ByteAssociationUtil{public static java.util.function.Predicate<ByteAssociation<java.util.UUID>> characteristicUUIDPredicate(java.util.UUID u){return v->u.equals(v.first);}public static <T> java.util.function.Function<ByteAssociation<T>,byte[]> getBytesFromAssociation(){return v->v.second;}}''')
java('com/polidea/rxandroidble2/internal/util/HugrReceiptWriteObserver','''package com.polidea.rxandroidble2.internal.util;public class HugrReceiptWriteObserver{public static void nativeTimeoutConfigured(Object o,long t,java.util.concurrent.TimeUnit u){}public static void terminalSuccess(Object o){}public static void terminalTimeout(Object o){}public static void terminalError(Object o){}public static void releaseCallReturned(Object o,boolean b){}public static void libraryCallbackConsumed(Object o){}public static void activeCancelFlagSet(Object o){}}''')
text=(rx/'com/polidea/rxandroidble2/internal/connection/RxBleGattCallback.java').read_text();a=text.index('        @Override\n        public void onCharacteristicRead(');b=text.index('\n        @Override',a+10);method=text[a:b]
java('com/polidea/rxandroidble2/internal/connection/RxBleGattCallback','''package com.polidea.rxandroidble2.internal.connection;
import android.bluetooth.*;import com.polidea.rxandroidble2.internal.logger.LoggerUtil;import com.polidea.rxandroidble2.internal.util.ByteAssociation;import com.polidea.rxandroidble2.exceptions.BleGattOperationType;
public class RxBleGattCallback{
 public static int nativeCalls,superCalls;public boolean observers=true,throwDispatcher;public io.reactivex.Observer<ByteAssociation<java.util.UUID>> observer;
 public io.reactivex.Observable<ByteAssociation<java.util.UUID>> getOnCharacteristicRead(){return new io.reactivex.Observable<>(o->{observer=o;});}
 class Dispatcher{void notifyNativeReadCallback(Object... args){nativeCalls++;if(throwDispatcher)throw new IllegalStateException("SYNTHETIC_DISPATCHER_THROW");}}
 class Relay{void accept(ByteAssociation<java.util.UUID> v){if(observer!=null)observer.onNext(v);}}
 class Output{boolean hasObservers(){return observers&&observer!=null;}Relay valueRelay=new Relay();}
 final Dispatcher nativeCallbackDispatcher=new Dispatcher();final Output readCharacteristicOutput=new Output();
 boolean propagateErrorIfOccurred(Output o,BluetoothGatt g,BluetoothGattCharacteristic c,int status,BleGattOperationType type){if(status!=0){observer.onError(new IllegalStateException("SYNTHETIC_STATUS_"+status));return true;}return false;}
 final BluetoothGattCallback callback=new BluetoothGattCallback(){
'''+method+'''
 };public void invoke(BluetoothGatt g,BluetoothGattCharacteristic c,int status){callback.onCharacteristicRead(g,c,status);}
}''')
java('com/polidea/rxandroidble2/internal/operations/ReadDriver','''package com.polidea.rxandroidble2.internal.operations;
public class ReadDriver extends CharacteristicReadOperation{
 public ReadDriver(com.polidea.rxandroidble2.internal.connection.RxBleGattCallback cb,android.bluetooth.BluetoothGatt g,android.bluetooth.BluetoothGattCharacteristic c){super(cb,g,new TimeoutConfiguration(10,java.util.concurrent.TimeUnit.SECONDS,new io.reactivex.Scheduler()),c);}
 public void run(io.reactivex.ObservableEmitter<byte[]> e,com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface q){protectedRun(e,q);}
 public boolean dispatchOnly(android.bluetooth.BluetoothGatt g){return startOperation(g);}
}''')
# Exact new bridge body and exact saved reader method compile with context/worker/UI seams.
sj=phone/'modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal'
module=(sj/'HUGRSourceJournalModule.kt').read_text();a=module.index('    AsyncFunction("checkpointCurrentReceiptReadProbeV2")');a=module.index('->',a)+2;b=module.index('\n    }',a);body=module[a:b]
w('ReadBridgeSourceSlice.kt','''package health.hugr.sourcejournal
import android.content.Context
internal class ReadBridgeSourceSlice(private val context:Context){fun checkpoint(observedOutcome:String,coalescedOutcomes:Double,jsObservedAtMonotonicMs:Double,originatingJsReferenceJson:String):String=run{'''+body+'\n}}')
activity=(sj/'HUGRSavedReceiptDiagnosticsActivity.kt').read_text();a=activity.index('  private fun readCurrentReadProbe()');b=activity.index('\n  private fun createDocument()',a);reader=activity[a:b]
w('ReadReaderSourceSlice.kt','''package health.hugr.sourcejournal
import android.content.Context
internal class ReadReaderSourceSlice(val applicationContext:Context){
 class Button{var isEnabled=true};class Output{var text=""}
 val save=Button();val share=Button();val currentReadProbe=Button();val output=Output();val worker=java.util.concurrent.Executor{it.run()}
 val destroyed=false;val isFinishing=false;val isDestroyed=false;val pendingSaveSource:Any?=null
 fun runOnUiThread(r:()->Unit){r()};fun invoke(){readCurrentReadProbe()}
'''+reader+'\n}')
actual=['com/polidea/rxandroidble2/internal/operations/CharacteristicReadOperation.java','com/polidea/rxandroidble2/internal/SingleResponseOperation.java','com/polidea/rxandroidble2/internal/util/QueueReleasingEmitterWrapper.java','com/polidea/rxandroidble2/internal/util/HugrReceiptReadObserver.java','com/polidea/rxandroidble2/internal/operations/TimeoutConfiguration.java']
(out/'sources.txt').write_text('\n'.join(str(p) for p in (out/'slice').rglob('*.java'))+'\n'+'\n'.join(str(rx/p) for p in actual)+'\n')
(out/'SLICE_BOUNDARY.json').write_text(json.dumps({'fullProductionClasses':actual,'exactMethodSlices':['RxBleGattCallback.onCharacteristicRead','Expo checkpointCurrentReceiptReadProbeV2 body','Activity.readCurrentReadProbe'],'doubles':['Android GATT/characteristic/callback','Rx Observable/Single filter first/map chain, timeout is identity; no scheduler/disposal modeling','ByteAssociationUtil predicate/map','QueueOperation base','logger/dispatcher/error relay','write observer no-op','Context/worker/UI around exact reader method'],'actualRxRuntime':False,'actualAndroid':False,'physicalComparator':'SYNTHETIC_SUCCESS_NOT_PHYSICAL_REPLAY','bindings':{p:hashlib.sha256((rx/p).read_bytes()).hexdigest() for p in actual},'callbackMethodSha256':hashlib.sha256(method.encode()).hexdigest()},indent=2)+'\n')
