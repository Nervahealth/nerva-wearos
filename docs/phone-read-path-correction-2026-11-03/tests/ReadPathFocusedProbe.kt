package health.hugr.sourcejournal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.polidea.rxandroidble2.internal.connection.RxBleGattCallback
import com.polidea.rxandroidble2.internal.operations.ReadDriver
import com.polidea.rxandroidble2.internal.util.HugrReceiptReadObserver
import com.polidea.rxandroidble2.internal.logger.LoggerUtil
import io.reactivex.ObservableEmitter
import io.reactivex.functions.Cancellable
import java.io.*
import java.nio.file.*
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONObject

/** SYNTHETIC inputs. Actual READ/SingleResponse/wrapper classes, exact raw callback method, real
 * store/serializer/reopen. Rx and Android are declared doubles, not a physical pipeline replay. */
object ReadPathFocusedProbe {
 private val results=mutableListOf<String>()
 private val artifacts=File(System.getProperty("hugr.read.artifacts"))
 private val receipt=UUID(0x9999999999994999UL.toLong(),0x8999999999999999UL.toLong())
 private val io=object:ReceiptIncidentFileIo {
  override fun writeAndSync(f:File,b:ByteArray){FileOutputStream(f).use{it.write(b);it.fd.sync()}}
  override fun atomicReplace(a:File,b:File){Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
  override fun syncDirectory(f:File){FileChannel.open(f.toPath(),StandardOpenOption.READ).use{it.force(true)}}
  override fun readFully(f:File)=f.readBytes()
 }
 private class Emitter:ObservableEmitter<ByteArray> {
  var cancellation:Cancellable?=null;var values=0;var completions=0;var errors=0;var releases=0
  val order=mutableListOf<String>()
  override fun setCancellable(c:Cancellable){cancellation=c;order.add("setCancellable")}
  override fun onNext(v:ByteArray){check(v.contentEquals(byteArrayOf(7)));values++;order.add("value")}
  override fun onComplete(){completions++;order.add("complete")}
  override fun tryOnError(t:Throwable):Boolean{errors++;order.add("error");return true}
  fun release(){releases++;order.add("release")}
 }
 private class Fixture {
  var time=1000L;val lane=HugrReceiptReadObserver.Lane{time};val gatt=BluetoothGatt();val c=BluetoothGattCharacteristic(receipt);val callback=RxBleGattCallback()
  init{lane.begin();HugrReceiptReadObserver.installForTest(lane)}
  fun op(g:BluetoothGatt=gatt,characteristic:BluetoothGattCharacteristic=c)=ReadDriver(callback,g,characteristic)
  fun run(op:ReadDriver=op(),cb:Boolean=true,status:Int=0,other:BluetoothGattCharacteristic=c):Emitter {
   val e=Emitter();gatt.onRead=Runnable{check(callback.observer!=null){"subscription must precede API"};if(cb)callback.invoke(gatt,other,status)}
   op.run(e){e.release()};return e
  }
 }
 private fun body(s:String)=JSONObject(s.lines().first{it.startsWith("{")})
 private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 private fun save(root:File,name:String,f:Fixture,outcome:String="JS_DEADLINE"):JSONObject {
  val storeRoot=File(root,"probe");val rendered=ReceiptReadProbeStore(storeRoot,io).publish(f.lane.snapshot(),outcome,0.0,10024.0)
  val reopened=ReceiptReadProbeStore(storeRoot,io).read();check(rendered==reopened)
  val b=body(reopened);check(b.getString("scope")=="DETACHED_JS_OUTCOME_NATIVE_CAPTURE_NOT_EXACT_REQUEST_OR_CLOCK_JOIN")
  check(b.getString("negativeClaim")=="NOT_OBSERVED_IS_NOT_NON_ARRIVAL_OR_NON_DISPATCH_FOR_JS_TARGET")
  check(reopened.contains("body_sha256=${hash(b.toString().toByteArray())}"))
  File(artifacts,"$name.SYNTHETIC.saved-reopened.utf8.txt").writeText(reopened)
  File(artifacts,"$name.SYNTHETIC.hugr-probe").writeBytes(File(storeRoot,RECEIPT_READ_PROBE_FILENAME).readBytes())
  return b
 }
 private fun row(b:JSONObject,i:Int=0)=b.getJSONArray("operations").getJSONObject(i)
 private fun callback(b:JSONObject,i:Int=0)=b.getJSONArray("rawReadCallbacks").getJSONObject(i)
 private fun test(name:String,block:(File)->Unit){val root=File(artifacts,"work-$name");check(root.mkdirs());try{block(root);results.add(name);println("PASS $name")}finally{HugrReceiptReadObserver.installForTest(null);LoggerUtil.throwLogger=false;root.deleteRecursively()}}
 private fun busy(lane:HugrReceiptReadObserver.Lane,block:()->Unit){val field=lane.javaClass.getDeclaredField("lock");field.isAccessible=true;val lock=field.get(lane) as ReentrantLock;val held=CountDownLatch(1);val release=CountDownLatch(1);val t=Thread{lock.lock();try{held.countDown();release.await()}finally{lock.unlock()}};t.start();held.await();try{block()}finally{release.countDown();t.join(2000);check(!t.isAlive)}}
 @JvmStatic fun main(args:Array<String>){
  test("ordinary-success-real-read-path") {root->val f=Fixture();val e=f.run();check(e.values==1&&e.completions==1&&e.errors==0&&e.releases==1);check(e.order==listOf("setCancellable","value","release","complete"));check(f.gatt.calls==1);val b=save(root,"ordinary-success",f,"JS_SETTLED_TIMELY");check(row(b).getString("androidReadReturned")=="TRUE");check(row(b).getString("boundary")=="DOWNSTREAM_COMPLETE_OBSERVED");check(callback(b).getString("assignment")=="UNASSIGNED_NO_ATT_REQUEST_ID")}
  test("stalled-api-true-without-completion") {root->val f=Fixture();val e=f.run(cb=false);check(e.values==0&&e.completions==0&&e.releases==0);val b=save(root,"stalled-api-true",f);check(row(b).getString("boundary")=="ANDROID_TRUE_COMPLETION_NOT_OBSERVED");check(b.getJSONArray("rawReadCallbacks").length()==0)}
  test("created-dispatch-not-observed") {root->val f=Fixture();f.op();val b=save(root,"dispatch-not-observed",f);check(row(b).getString("boundary")=="DISPATCH_NOT_OBSERVED_FOR_NATIVE_ROW")}
  test("android-api-false-original-cancel-error") {root->val f=Fixture();f.gatt.result=false;val e=f.run(cb=false);check(e.errors==1&&e.releases==1&&e.values==0&&e.completions==0);check(e.order==listOf("setCancellable","release","error"));val b=save(root,"android-api-false",f,"JS_SETTLED_REJECTED");check(row(b).getString("androidReadReturned")=="FALSE"&&row(b).getString("boundary")=="ANDROID_RETURNED_FALSE");check(row(b).getLong("cancelNativeMs")>=0&&row(b).getLong("errorNativeMs")>=0)}
  test("api-exception-not-swallowed") {root->val f=Fixture();f.gatt.throwApi=true;check(runCatching{f.run(cb=false)}.exceptionOrNull()?.message=="SYNTHETIC_API_THROW");val b=save(root,"api-return-not-observed",f);check(row(b).getString("boundary")=="DISPATCH_ENTERED_API_RETURN_NOT_OBSERVED")}
  test("raw-callback-before-logger-or-dispatcher") {root->for(kind in listOf("logger","dispatcher")){val f=Fixture();f.op();if(kind=="logger")LoggerUtil.throwLogger=true else f.callback.throwDispatcher=true;check(runCatching{f.callback.invoke(f.gatt,f.c,0)}.isFailure);LoggerUtil.throwLogger=false;val b=save(root,"callback-before-$kind-throw",f);check(b.getJSONArray("rawReadCallbacks").length()==1);check(row(b).getLong("completeNativeMs")==-1L)}}
  test("raw-callback-present-no-downstream-observer") {root->val f=Fixture();f.callback.observers=false;val e=f.run();check(e.values==0&&e.completions==0);val b=save(root,"callback-present-completion-absent",f);check(callback(b).getInt("rawStatus")==0&&row(b).getString("boundary")=="ANDROID_TRUE_COMPLETION_NOT_OBSERVED")}
  test("nonzero-callback-downstream-error") {root->val f=Fixture();val e=f.run(status=5);check(e.errors==1&&e.values==0&&e.releases==1);val b=save(root,"callback-status-error",f,"JS_SETTLED_REJECTED");check(callback(b).getInt("rawStatus")==5&&row(b).getString("boundary")=="DOWNSTREAM_ERROR_OBSERVED")}
  test("uuid-filter-keeps-unmatched-callback-unassigned") {root->val f=Fixture();val other=BluetoothGattCharacteristic(UUID(0L,3L));val e=f.run(other=other);check(e.values==0&&e.completions==0);val b=save(root,"unmatched-filtered",f);check(callback(b).getString("context")=="UNMATCHED");check(row(b).getString("boundary")=="ANDROID_TRUE_COMPLETION_NOT_OBSERVED")}
  test("late-after-completed-operation-unassigned") {root->val f=Fixture();f.run();f.time++;f.callback.invoke(f.gatt,f.c,0);val b=save(root,"late-after-completed",f);check(callback(b,1).getString("context")=="LATE_OR_DUPLICATE_UNASSIGNED");check(callback(b,1).getString("assignment")=="UNASSIGNED_NO_ATT_REQUEST_ID")}
  test("historical-object-reuse-never-picks-active") {root->val f=Fixture();f.run();val second=f.op();val e=f.run(second,cb=false);f.time++;f.callback.invoke(f.gatt,f.c,0);val b=save(root,"historical-ambiguous",f);check(callback(b,1).getInt("retainedObjectPairMatches")==2&&callback(b,1).getString("context")=="AMBIGUOUS_HISTORY");check(e.completions==1);check(row(b,1).getString("boundary")=="DOWNSTREAM_COMPLETE_OBSERVED") /* library UUID semantics unchanged, NOT an ATT callback assignment */}
  test("cancel-then-late-callback-unassigned") {root->val f=Fixture();val e=f.run(cb=false);e.cancellation!!.cancel();f.time++;f.callback.observers=false;f.callback.invoke(f.gatt,f.c,0);val b=save(root,"cancel-late",f);check(row(b).getString("boundary")=="CANCEL_FLAG_OBSERVED");check(callback(b).getString("context")=="LATE_OR_DUPLICATE_UNASSIGNED")}
  test("bounded-overflow-explicit-loss-stalled-unknown") {root->val f=Fixture();repeat(40){f.op()};repeat(70){f.callback.invoke(f.gatt,f.c,0)};val b=save(root,"explicit-eviction-loss",f);check(b.getJSONArray("operations").length()==32&&b.getJSONArray("rawReadCallbacks").length()==64);check(b.getJSONObject("loss").getLong("operationEvictions")==8L&&b.getJSONObject("loss").getLong("callbackEvictions")==6L);check(row(b).getString("boundary")=="UNKNOWN_WITH_LOSS_OR_INACTIVE");check(callback(b).getString("context")=="UNKNOWN_HISTORY_EVICTED")}
  test("busy-loss-retains-positive-api-but-stall-unknown") {root->val f=Fixture();f.run(cb=false);f.callback.observers=false;busy(f.lane){f.callback.invoke(f.gatt,f.c,0)};val b=save(root,"explicit-busy-loss",f);check(b.getJSONObject("loss").getLong("busy")==1L&&row(b).getString("androidReadReturned")=="TRUE"&&row(b).getString("boundary")=="UNKNOWN_WITH_LOSS_OR_INACTIVE")}
  test("clock-inactive-and-interruption-unknown") {root->val f=Fixture();f.run(cb=false);f.time=500;val b=save(root,"clock-loss",f);check(b.getJSONObject("loss").getLong("clock")>0&&row(b).getString("boundary")=="UNKNOWN_WITH_LOSS_OR_INACTIVE");f.time=2000;f.lane.begin();f.op();check(save(root,"capture-interruption",f).getJSONObject("loss").getLong("captureInterruptions")==1L);f.lane.end();check(save(root,"inactive-capture",f).getString("coverage")=="UNKNOWN_WITH_LOSS_OR_INACTIVE")}
  test("store-interruption-checksum-closed-schema-and-budgets") {root->val f=Fixture();f.run(cb=false);save(root,"integrity-before",f);val dir=File(root,"probe");val file=File(dir,RECEIPT_READ_PROBE_FILENAME);val original=file.readBytes();val part=File(dir,".$RECEIPT_READ_PROBE_FILENAME.part");part.writeText("SYNTHETIC_INTERRUPTION");check(ReceiptReadProbeStore(dir,io).read().contains("INTERRUPTED_CHECKPOINT"));check(runCatching{ReceiptReadProbeStore(dir,io).publish(f.lane.snapshot(),"JS_DEADLINE",0.0,10024.0)}.isFailure);check(file.readBytes().contentEquals(original));part.delete();val envelope=JSONObject(file.readText());val b=body(ReceiptReadProbeStore(dir,io).read());b.put("inventedPayload","forbidden");val bytes=b.toString().toByteArray();envelope.put("body_base64",java.util.Base64.getEncoder().encodeToString(bytes)).put("body_sha256",hash(bytes));file.writeText(envelope.toString());check(runCatching{ReceiptReadProbeStore(dir,io).read()}.isFailure);file.writeBytes(original);val e2=JSONObject(file.readText()).put("body_sha256","0".repeat(64));file.writeText(e2.toString());check(runCatching{ReceiptReadProbeStore(dir,io).read()}.isFailure);file.writeBytes(ByteArray(66*1024+1));check(runCatching{ReceiptReadProbeStore(dir,io).read()}.isFailure)}
  test("missing-file-not-negative-and-invalid-js-refused") {root->val store=ReceiptReadProbeStore(File(root,"empty"),io);check(store.read().contains("PROCESS_LOSS_UNKNOWN"));val f=Fixture();for(v in listOf(-1.0,0.5,Double.NaN,Double.POSITIVE_INFINITY))check(runCatching{store.publish(f.lane.snapshot(),"JS_DEADLINE",v,10024.0)}.isFailure);check(runCatching{store.publish(f.lane.snapshot(),"BAD",0.0,10024.0)}.isFailure);check(runCatching{store.publish(f.lane.snapshot(),"JS_DEADLINE",0.0,Double.NaN)}.isFailure)}
  test("exact-isolated-reader-method-selectable-body-no-save-cache") {root->val f=Fixture();f.run();val dir=File(root,"hugr_receipt_read_probes_v1");val report=ReceiptReadProbeStore(dir,io).publish(f.lane.snapshot(),"JS_SETTLED_TIMELY",0.0,198.0);val context=object:android.content.ContextWrapper(null){override fun getNoBackupFilesDir()=root;override fun getApplicationContext():Context=this};val reader=ReadReaderSourceSlice(context);reader.invoke();check(reader.output.text==report);check(!reader.save.isEnabled&&!reader.share.isEnabled&&reader.currentReadProbe.isEnabled);File(artifacts,"isolated-reader.SYNTHETIC.full-body.txt").writeText(reader.output.text)}
  test("synthetic-old-incident-independent-and-unchanged") {root->val old=ReceiptIncidentDiagnosticStore(File(root,"old"),io);val fixture=File(System.getProperty("hugr.read.oldfixture")).readText();check(old.publish(fixture).acknowledgedAtomicPublication);val original=old.readAll();val hashes=File(root,"old").listFiles()!!.associate{it.name to hash(it.readBytes())};val f=Fixture();f.run();save(root,"fresh-read-old-incident-separate",f,"JS_SETTLED_TIMELY");check(old.readAll()==original);check(hashes.all{(name,h)->hash(File(File(root,"old"),name).readBytes())==h});File(artifacts,"old-incident.SYNTHETIC.unchanged.utf8.txt").writeText(original)}
  val result=JSONObject().put("status","PASS_FOCUSED_READ_SOURCE_CLASSES_WITH_EXPLICIT_DOUBLES_REAL_STORE_REOPEN").put("caseCount",results.size).put("cases",org.json.JSONArray(results)).put("actualRxRuntime",false).put("AndroidDevice",false).put("physicalComparatorReplay",false)
  File(artifacts,"READ_FOCUSED_RESULT.json").writeText(result.toString(2)+"\n");println(result.toString(2))
 }
}
