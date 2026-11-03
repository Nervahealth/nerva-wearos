package health.hugr.sourcejournal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.polidea.rxandroidble2.internal.connection.CallbackSourceSlice
import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteObserver
import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteTrace
import health.hugr.sourcejournal.nativeobservation.NativeObservationFrameSerializer
import health.hugr.sourcejournal.nativeobservation.NativeObservationAnchor
import java.io.*
import java.lang.reflect.Proxy
import java.nio.file.*
import java.nio.channels.FileChannel
import java.util.UUID
import org.json.JSONObject

object CallbackEntryFocusedProbe {
 private val tx="hugr-receipt-2-1"
 private val receipt=UUID(0x9999999999994999UL.toLong(),0x8999999999999999UL.toLong())
 private val io=object:ReceiptIncidentFileIo {
  override fun writeAndSync(file:File,bytes:ByteArray){ FileOutputStream(file).use{it.write(bytes);it.fd.sync()} }
  override fun atomicReplace(from:File,to:File){Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
  override fun syncDirectory(directory:File){FileChannel.open(directory.toPath(),StandardOpenOption.READ).use{it.force(true)}}
  override fun readFully(file:File)=file.readBytes()
 }
 private fun backend(trace:HugrReceiptWriteTrace)= Proxy.newProxyInstance(HugrReceiptWriteObserver.Backend::class.java.classLoader,arrayOf(HugrReceiptWriteObserver.Backend::class.java)){_,method,args ->
  val m=trace.javaClass.methods.single{it.name==method.name && it.parameterCount==method.parameterCount}
  m.invoke(trace,*(args?:emptyArray()))
 } as HugrReceiptWriteObserver.Backend
 private fun primaryBody():String {
  val m=RetentionCorrectionFocusedProbe::class.java.declaredMethods.single{it.name=="mainSnapshot"};m.isAccessible=true
  return m.invoke(RetentionCorrectionFocusedProbe,"synthetic-callback-old","synthetic-callback-epoch",listOf("hugr-receipt-1-1",tx),2L) as String
 }
 private fun save(root:File,name:String,report:String){
  val cache=File(root,"cache-$name")
  val copy=requireNotNull(prepareReceiptDiagnosticPrivateCopy(cache,report))
  val stream=ByteArrayOutputStream();copyReceiptDiagnosticPrivateReport(copy,stream)
  check(stream.toByteArray().contentEquals(report.toByteArray(Charsets.UTF_8)))
  File(System.getProperty("hugr.callback.artifacts"),"$name.SYNTHETIC.saved.utf8.txt").writeBytes(stream.toByteArray())
 }
 @JvmStatic fun main(args:Array<String>){
  val root=Files.createTempDirectory("hugr-callback-synthetic-").toFile()
  try{
   val trace=HugrReceiptWriteTrace{1000L};check(trace.beginCapture(1,2))
   HugrReceiptWriteObserver.installBackendForTest(backend(trace))
   val gatt=BluetoothGatt();val matched=BluetoothGattCharacteristic(receipt);val unmatched=BluetoothGattCharacteristic(receipt)
   val single=Any();val operation=Any()
   check(trace.nativeEntry(tx));trace.associateReturnedSingle(single,operation,gatt,matched);trace.bindTransactionToSingle(tx,single)
   trace.androidWriteCallEntry(operation);trace.androidWriteCallReturned(operation,true)
   CallbackSourceSlice.invoke(gatt,unmatched,0)
   check(trace.snapshot().unassociatedRawCallbacks()==1L)
   check(trace.snapshot().events().none{it.boundary()==HugrReceiptWriteTrace.Boundary.RAW_CALLBACK_ENTRY})
   check(CallbackSourceSlice.nativeCalls==1 && CallbackSourceSlice.values==1)
   val primary=ReceiptIncidentDiagnosticStore(root,io);check(primary.publish(primaryBody()).acknowledgedAtomicPublication)
   val verified=primary.verifiedNativeWriteMain("synthetic-callback-old")
   val a=verified.anchor
   val frame=NativeObservationFrameSerializer().serialize(NativeObservationAnchor(a.incidentPseudonym,a.connectionEpoch,a.mainFirstFailure,a.mainFirstFailureAtMonotonicMs,a.mainFirstCauseFingerprint),trace.snapshotForTransactions(setOf(tx)))
   ReceiptNativeWriteFramePublisher(root,io,ReceiptNativeWriteVerifiedMainProvider{primary.verifiedNativeWriteMain(it)}).publish(frame)
   val read=ReceiptNativeWriteObservationStore(root,io,ReceiptNativeWriteVerifiedMainProvider{primary.verifiedNativeWriteMain(it)}).read(verified)
   val body=JSONObject(requireNotNull(read.body));check(body.getJSONArray("events").toString().contains("RAW_CALLBACK_ENTRY").not())
   save(root,"unmatched-old-path",primary.readAll())
   println("ORIGINAL_ACTUAL_METHOD_SLICE_TO_SAVED_OUTPUT missing_entry=YES unassociated=1 save_exact=YES native_dispatch_double=1 value_double=1")
   if(System.getProperty("hugr.callback.mode")=="baseline")error("EXPECTED_FAILURE: callback entered exact source method but old observer/recorder/save path has no per-entry record")
   println("PATCHED_TESTS_NOT_IMPLEMENTED")
  }finally{HugrReceiptWriteObserver.resetProductionBackendOffCritical();root.deleteRecursively()}
 }
}
