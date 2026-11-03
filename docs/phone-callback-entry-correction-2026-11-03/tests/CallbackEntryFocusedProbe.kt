package health.hugr.sourcejournal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.polidea.rxandroidble2.internal.connection.CallbackSourceSlice
import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteObserver
import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteTrace
import com.polidea.rxandroidble2.internal.util.HugrCallbackEntryLane
import health.hugr.sourcejournal.nativeobservation.NativeObservationFrameSerializer
import health.hugr.sourcejournal.nativeobservation.NativeObservationAnchor
import java.io.*
import java.lang.reflect.Proxy
import java.nio.file.*
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import java.security.MessageDigest
import org.json.JSONObject

/** Exact production METHOD slice with declared collaborator doubles; not real Android or RxJava. */
object CallbackEntryFocusedProbe {
 private val tx="hugr-receipt-2-1"
 private val receipt=UUID(0x9999999999994999UL.toLong(),0x8999999999999999UL.toLong())
 private val results=mutableListOf<String>()
 private val io=object:ReceiptIncidentFileIo {
  override fun writeAndSync(file:File,bytes:ByteArray){FileOutputStream(file).use{it.write(bytes);it.fd.sync()}}
  override fun atomicReplace(from:File,to:File){Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
  override fun syncDirectory(directory:File){FileChannel.open(directory.toPath(),StandardOpenOption.READ).use{it.force(true)}}
  override fun readFully(file:File)=file.readBytes()
 }
 private fun backend(trace:HugrReceiptWriteTrace)=Proxy.newProxyInstance(HugrReceiptWriteObserver.Backend::class.java.classLoader,arrayOf(HugrReceiptWriteObserver.Backend::class.java)){_,method,args->
  trace.javaClass.methods.single{it.name==method.name && it.parameterCount==method.parameterCount}.invoke(trace,*(args?:emptyArray()))
 } as HugrReceiptWriteObserver.Backend
 private fun primaryBody():String {
  val m=RetentionCorrectionFocusedProbe::class.java.declaredMethods.single{it.name=="mainSnapshot"};m.isAccessible=true
  return m.invoke(RetentionCorrectionFocusedProbe,"synthetic-callback-old","synthetic-callback-epoch",listOf("hugr-receipt-1-1",tx),2L) as String
 }
 private fun save(root:File,name:String,report:String):File {
  val copy=requireNotNull(prepareReceiptDiagnosticPrivateCopy(File(root,"cache-$name"),report))
  val stream=ByteArrayOutputStream();copyReceiptDiagnosticPrivateReport(copy,stream)
  check(stream.toByteArray().contentEquals(report.toByteArray(Charsets.UTF_8)))
  val f=File(System.getProperty("hugr.callback.artifacts"),"$name.SYNTHETIC.saved.utf8.txt");f.writeBytes(stream.toByteArray());return f
 }
 private fun body(report:String)=JSONObject(report.lines().first{it.startsWith("{")})
 private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 private class Fixture {
  var time=1000L
  val trace=HugrReceiptWriteTrace{time}
  val gatt=BluetoothGatt();val characteristic=BluetoothGattCharacteristic(receipt)
  init {check(trace.beginCapture(1,2));HugrReceiptWriteObserver.installBackendForTest(backend(trace));HugrReceiptWriteObserver.installCallbackEntryTraceForTest(trace)}
  fun register(transaction:String=tx,g:BluetoothGatt=gatt,c:BluetoothGattCharacteristic=characteristic):Any {
   val single=Any();val operation=Any();check(trace.nativeEntry(transaction))
   trace.associateReturnedSingle(single,operation,g,c);trace.bindTransactionToSingle(transaction,single)
   trace.androidWriteCallEntry(operation);trace.androidWriteCallReturned(operation,true);return operation
  }
  fun callback(g:BluetoothGatt=gatt,c:BluetoothGattCharacteristic=characteristic,status:Int=0){CallbackSourceSlice.invoke(g,c,status)}
 }
 private fun checkCase(name:String,block:(File)->Unit) {
  val root=Files.createTempDirectory("hugr-callback-$name-").toFile()
  try {block(root);results.add(name);println("PASS $name")}
  finally {CallbackSourceSlice.throwLogger=false;CallbackSourceSlice.observers=true;HugrReceiptWriteObserver.installCallbackEntryTraceForTest(null);HugrReceiptWriteObserver.resetProductionBackendOffCritical();root.deleteRecursively()}
 }
 private fun assertSaved(root:File,name:String,f:Fixture,outcome:String="JS_SETTLED_TIMELY"):JSONObject {
  val rendered=ReceiptCallbackProbeStore(File(root,"probe-store"),io).publish(f.trace.callbackEntryLane().snapshot(),outcome)
  val reopened=ReceiptCallbackProbeStore(File(root,"probe-store"),io).read();check(reopened==rendered)
  val file=save(root,name,reopened);check(file.readText()==rendered)
  val b=body(reopened);check(b.getString("negativeClaim")=="NOT_MADE_NO_ENTRY_IS_NOT_NON_ARRIVAL")
  check(b.getString("processLoss")=="UNKNOWN_BEFORE_CHECKPOINT_MEMORY_IS_NOT_DURABLE")
  check(b.getString("scope")=="NATIVE_CAPTURE_EPOCH_NOT_EXACT_ATT_OR_JS_WINDOW")
  return b
 }
 private fun lockDuring(lock:ReentrantLock,action:()->Unit) {
  val held=CountDownLatch(1);val release=CountDownLatch(1)
  val thread=Thread{lock.lock();try{held.countDown();release.await()}finally{lock.unlock()}}
  thread.start();held.await();try{action()}finally{release.countDown();thread.join(2000);check(!thread.isAlive)}
 }
 private fun privateLock(target:Any,name:String):ReentrantLock=target.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(target) as ReentrantLock}
 @JvmStatic fun main(args:Array<String>) {
  checkCase("unmatched_actual_hook_to_separate_saved_output") {root->
   val f=Fixture();f.register();f.callback(c=BluetoothGattCharacteristic(receipt))
   check(f.trace.snapshot().unassociatedRawCallbacks()==1L)
   check(f.trace.snapshot().events().none{it.boundary()==HugrReceiptWriteTrace.Boundary.RAW_CALLBACK_ENTRY})
   val incidentRoot=File(root,"old-incidents");val primary=ReceiptIncidentDiagnosticStore(incidentRoot,io);check(primary.publish(primaryBody()).acknowledgedAtomicPublication)
   val verified=primary.verifiedNativeWriteMain("synthetic-callback-old");val a=verified.anchor
   val frame=NativeObservationFrameSerializer().serialize(NativeObservationAnchor(a.incidentPseudonym,a.connectionEpoch,a.mainFirstFailure,a.mainFirstFailureAtMonotonicMs,a.mainFirstCauseFingerprint),f.trace.snapshotForTransactions(setOf(tx)))
   ReceiptNativeWriteFramePublisher(incidentRoot,io,ReceiptNativeWriteVerifiedMainProvider{primary.verifiedNativeWriteMain(it)}).publish(frame)
   val old=primary.readAll();save(root,"unmatched-old-path-still-unchanged",old)
   val prior=incidentRoot.listFiles()!!.filter{it.isFile}.associate{it.name to hash(it.readBytes())}
   val b=assertSaved(root,"unmatched-new-path",f);val e=b.getJSONArray("entries").getJSONObject(0)
   check(b.getLong("attemptedCallbacks")==1L && b.getJSONArray("entries").length()==1)
   check(e.getString("association")=="UNMATCHED" && e.getString("timing")=="UNCERTAIN")
   check(e.getString("characteristicClass")=="SELECTED_RECEIPT" && e.getString("characteristicIdentity")=="DIFFERENT")
   check(prior.all{(name,h)->hash(File(incidentRoot,name).readBytes())==h});check(primary.readAll()==old)
  }
  checkCase("fresh_success_probe_preserves_old_incident_and_sidecar") {root->
   val f=Fixture();f.register();val incidentRoot=File(root,"old-incidents");val primary=ReceiptIncidentDiagnosticStore(incidentRoot,io);check(primary.publish(primaryBody()).acknowledgedAtomicPublication)
   val v=primary.verifiedNativeWriteMain("synthetic-callback-old");val a=v.anchor
   ReceiptNativeWriteFramePublisher(incidentRoot,io,ReceiptNativeWriteVerifiedMainProvider{primary.verifiedNativeWriteMain(it)}).publish(NativeObservationFrameSerializer().serialize(NativeObservationAnchor(a.incidentPseudonym,a.connectionEpoch,a.mainFirstFailure,a.mainFirstFailureAtMonotonicMs,a.mainFirstCauseFingerprint),f.trace.snapshotForTransactions(setOf(tx))))
   val prior=incidentRoot.listFiles()!!.filter{it.isFile}.associate{it.name to hash(it.readBytes())};val old=primary.readAll()
   f.callback();val b=assertSaved(root,"fresh-successful-probe",f);val e=b.getJSONArray("entries").getJSONObject(0)
   check(e.getString("timing")=="TIMELY_NATIVE_PENDING_WINDOW" && e.getString("association")=="LOCAL_IDENTITY_ONLY_NO_ATT_MATCH")
   check(b.getString("observedOutcome")=="JS_SETTLED_TIMELY");check(prior.all{(n,h)->hash(File(incidentRoot,n).readBytes())==h});check(primary.readAll()==old)
  }
  checkCase("late_after_observed_cancel") {root->val f=Fixture();f.register();f.trace.cancelCallEnter(tx);f.time=1010;f.callback();val e=assertSaved(root,"late-callback",f,"JS_DEADLINE").getJSONArray("entries").getJSONObject(0);check(e.getString("timing")=="LATE_AFTER_CANCEL_OR_TERMINAL");check(e.getString("association")=="AMBIGUOUS_NO_ATT_MATCH")}
  checkCase("duplicate_is_ambiguous_and_uncertain") {root->val f=Fixture();f.register();f.callback();f.callback();val rows=assertSaved(root,"duplicate",f).getJSONArray("entries");check(rows.length()==2);check(rows.getJSONObject(1).getString("timing")=="UNCERTAIN");check(rows.getJSONObject(1).getString("association")=="AMBIGUOUS_NO_ATT_MATCH")}
  checkCase("two_identity_candidates_never_select_one") {root->val f=Fixture();f.register();f.register("hugr-receipt-2-2");f.callback();val e=assertSaved(root,"ambiguous",f).getJSONArray("entries").getJSONObject(0);check(e.getInt("retainedIdentityMatches")==2 && e.getString("timing")=="UNCERTAIN" && e.getString("association")=="AMBIGUOUS_NO_ATT_MATCH")}
  checkCase("unexpected_characteristic_and_nonzero_status_survive") {root->val f=Fixture();f.register();f.callback(c=BluetoothGattCharacteristic(UUID(0L,3L)),status=5);val e=assertSaved(root,"unexpected-characteristic",f,"JS_SETTLED_REJECTED").getJSONArray("entries").getJSONObject(0);check(e.getInt("rawStatus")==5 && e.getString("characteristicClass")=="OTHER" && e.getString("timing")=="UNCERTAIN")}
  checkCase("logger_exception_cannot_erase_first_entry") {root->val f=Fixture();f.register();CallbackSourceSlice.throwLogger=true;check(runCatching{f.callback()}.isFailure);CallbackSourceSlice.throwLogger=false;check(assertSaved(root,"logger-exception",f).getJSONArray("entries").length()==1)}
  checkCase("ordinary_128_event_ring_crowding_does_not_erase_lane") {root->val f=Fixture();val op=f.register();f.callback();repeat(300){f.trace.androidWriteCallEntry(op);f.trace.androidWriteCallReturned(op,true)};check(f.trace.snapshot().events().size<=128);check(assertSaved(root,"crowded-ring",f).getJSONArray("entries").length()==1)}
  checkCase("reserved_first32_last32_and_explicit_overflow") {root->val f=Fixture();f.register();repeat(100){f.time++;f.callback()};val b=assertSaved(root,"overflow",f);val rows=b.getJSONArray("entries");check(rows.length()==64 && rows.getJSONObject(0).getLong("sequence")==1L && rows.getJSONObject(31).getLong("sequence")==32L && rows.getJSONObject(32).getLong("sequence")==69L && rows.getJSONObject(63).getLong("sequence")==100L);check(b.getJSONObject("loss").getLong("overflow")==36L);check(b.getString("coverage")=="UNKNOWN_CAPTURE_GAP_OR_LOSS");for(i in 0 until rows.length())check(rows.getJSONObject(i).getString("timing")=="UNCERTAIN");check(File(File(root,"probe-store"),RECEIPT_CALLBACK_PROBE_FILENAME).length()<=66*1024)}
  checkCase("busy_admission_is_counted_not_negative_evidence") {root->val f=Fixture();f.register();lockDuring(privateLock(f.trace.callbackEntryLane(),"lock")){f.callback()};val b=assertSaved(root,"busy",f);check(b.getJSONArray("entries").length()==0 && b.getJSONObject("loss").getLong("busy")==1L && b.getString("coverage")=="UNKNOWN_CAPTURE_GAP_OR_LOSS")}
  checkCase("association_busy_keeps_raw_entry_uncertain") {root->val f=Fixture();f.register();lockDuring(privateLock(f.trace,"recorderLock")){f.callback()};val b=assertSaved(root,"association-busy",f);check(b.getJSONArray("entries").length()==1 && b.getJSONArray("entries").getJSONObject(0).getString("timing")=="UNCERTAIN");check(b.getJSONObject("loss").getLong("associationGap")==1L)}
  checkCase("clock_regression_downgrades_all_timing") {root->val f=Fixture();f.register();f.callback();f.time=500;f.callback();val b=assertSaved(root,"clock-regression",f);check(b.getJSONObject("loss").getLong("clock")>0);for(i in 0..1)check(b.getJSONArray("entries").getJSONObject(i).getString("timing")=="UNCERTAIN")}
  checkCase("capture_restart_marks_gap_and_prior_entry_eviction") {root->val f=Fixture();f.register();f.callback();check(f.trace.beginCapture(1,3));f.register();f.callback();val b=assertSaved(root,"capture-restart",f);check(b.getJSONObject("loss").getLong("captureInterruptions")==1L && b.getJSONObject("loss").getLong("evicted")==1L && b.getString("coverage")=="UNKNOWN_CAPTURE_GAP_OR_LOSS")}
  checkCase("inactive_capture_has_unknown_process_loss") {root->val f=Fixture();f.trace.endCapture();f.callback();val b=assertSaved(root,"inactive",f);check(b.getJSONObject("loss").getLong("disabled")==1L && b.getJSONArray("entries").length()==0 && b.getString("coverage")=="UNKNOWN_CAPTURE_GAP_OR_LOSS")}
  checkCase("interrupted_checkpoint_does_not_relabel_prior_saved_file") {root->val f=Fixture();f.register();f.callback();assertSaved(root,"before-interruption",f);val old=File(File(root,"probe-store"),RECEIPT_CALLBACK_PROBE_FILENAME).readBytes();File(File(root,"probe-store"),".$RECEIPT_CALLBACK_PROBE_FILENAME.part").writeText("SYNTHETIC_INTERRUPTION");check(ReceiptCallbackProbeStore(File(root,"probe-store"),io).read().contains("INTERRUPTED_CHECKPOINT"));check(runCatching{ReceiptCallbackProbeStore(File(root,"probe-store"),io).publish(f.trace.callbackEntryLane().snapshot(),"JS_DEADLINE")}.isFailure);check(File(File(root,"probe-store"),RECEIPT_CALLBACK_PROBE_FILENAME).readBytes().contentEquals(old))}
  checkCase("closed_schema_rejects_false_timely_unmatched_even_with_rehashed_envelope") {root->val f=Fixture();f.register();f.callback(c=BluetoothGattCharacteristic(receipt));val b=assertSaved(root,"schema-good",f);b.getJSONArray("entries").getJSONObject(0).put("timing","TIMELY_NATIVE_PENDING_WINDOW");val bytes=b.toString().toByteArray();val file=File(File(root,"probe-store"),RECEIPT_CALLBACK_PROBE_FILENAME);val envelope=JSONObject(file.readText());envelope.put("body_base64",java.util.Base64.getEncoder().encodeToString(bytes)).put("body_sha256",hash(bytes));file.writeText(envelope.toString());check(runCatching{ReceiptCallbackProbeStore(File(root,"probe-store"),io).read()}.isFailure)}
  checkCase("callback_collaborator_control_flow_preserved") {root->val f=Fixture();f.register();for(status in listOf(0,5))for(observers in listOf(true,false)){CallbackSourceSlice.observers=observers;val n=CallbackSourceSlice.nativeCalls;val v=CallbackSourceSlice.values;val e=CallbackSourceSlice.errors;val l=CallbackSourceSlice.loggingCalls;f.callback(status=status);check(CallbackSourceSlice.nativeCalls==n+1 && CallbackSourceSlice.loggingCalls==l+1);check(CallbackSourceSlice.values==v+(if(observers && status==0)1 else 0));check(CallbackSourceSlice.errors==e+(if(observers && status!=0)1 else 0))};assertSaved(root,"completion-source-slice",f)}
  checkCase("disabled_lane_entry_is_explicit_unknown") {root->val f=Fixture();val disabled=HugrReceiptWriteTrace.disabled();HugrReceiptWriteObserver.installCallbackEntryTraceForTest(disabled);f.callback();val report=ReceiptCallbackProbeStore(File(root,"probe-store"),io).publish(disabled.callbackEntryLane().snapshot(),"JS_SETTLED_TIMELY");val b=body(report);check(!b.getBoolean("enabled") && b.getJSONArray("entries").length()==0 && b.getJSONObject("loss").getLong("disabled")==1L);save(root,"disabled",report)}
  checkCase("selector_exception_cannot_remove_entry") {root->val f=Fixture();f.register();val bad=object:BluetoothGattCharacteristic(receipt){override fun getUuid():UUID{throw IllegalStateException("SYNTHETIC_SELECTOR_EXCEPTION")}};check(runCatching{f.callback(c=bad)}.isFailure);val b=assertSaved(root,"selector-exception",f);check(b.getJSONArray("entries").length()==1 && b.getJSONObject("loss").getLong("associationGap")==1L && b.getJSONArray("entries").getJSONObject(0).getString("characteristicClass")=="UNKNOWN")}
  checkCase("invalid_coalescing_and_oversized_file_are_refused") {root->val f=Fixture();f.register();f.callback();val store=ReceiptCallbackProbeStore(File(root,"probe-store"),io);for(v in listOf(-1.0,0.5,Double.NaN,Double.POSITIVE_INFINITY))check(runCatching{store.publish(f.trace.callbackEntryLane().snapshot(),"JS_SETTLED_TIMELY",v)}.isFailure);val report=store.publish(f.trace.callbackEntryLane().snapshot(),"JS_SETTLED_TIMELY",3.0);check(body(report).getLong("coalescedOutcomes")==3L);val file=File(File(root,"probe-store"),RECEIPT_CALLBACK_PROBE_FILENAME);file.writeBytes(ByteArray(66*1024+1));check(runCatching{store.read()}.isFailure)}
  val json=JSONObject().put("status","PASS_FOCUSED_METHOD_SLICE_AND_REAL_STORE_SAVE_PATH").put("caseCount",results.size).put("cases",org.json.JSONArray(results)).put("actualRxRuntime",false).put("actualAndroidCallback",false).put("physicalTransport",false)
  File(System.getProperty("hugr.callback.artifacts"),"FOCUSED_CASE_RESULTS.json").writeText(json.toString(2)+"\n")
  println("ALL_FOCUSED_CASES_PASS count=${results.size} FULL_RX_RUNTIME=NOT_EARNED DEVICE=NONE")
 }
}
