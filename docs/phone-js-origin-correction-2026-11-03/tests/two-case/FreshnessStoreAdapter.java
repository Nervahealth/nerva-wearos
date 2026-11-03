package health.hugr.sourcejournal;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.ContextWrapper;
import com.polidea.rxandroidble2.internal.connection.RxBleGattCallback;
import com.polidea.rxandroidble2.internal.operations.ReadDriver;
import com.polidea.rxandroidble2.internal.util.HugrReceiptReadObserver;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.Cancellable;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Task-local offline adapter. Reuses compiled production path + declared existing doubles.
 * JS bridge arguments arrive unchanged. Fixture native rows NEVER acquire JS identities.
 */
public final class FreshnessStoreAdapter {
  static File caseRoot;
  static long nativeTime;
  static HugrReceiptReadObserver.Lane lane;
  static final String READ_FILE="current-read-probe-v1.hugr-probe";
  static final ReceiptIncidentFileIo IO=new ReceiptIncidentFileIo(){
    public void writeAndSync(File f,byte[] b){try(FileOutputStream s=new FileOutputStream(f)){s.write(b);s.flush();s.getFD().sync();}catch(IOException e){throw new RuntimeException(e);}}
    public void atomicReplace(File a,File b){try{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){throw new RuntimeException(e);}}
    public void syncDirectory(File d){try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}catch(IOException e){throw new RuntimeException(e);}}
    public byte[] readFully(File f){try{return Files.readAllBytes(f.toPath());}catch(IOException e){throw new RuntimeException(e);}}
  };
  static File readRoot(){return new File(caseRoot,"hugr_receipt_read_probes_v1");}
  static void save(String n,String t)throws Exception{Files.writeString(new File(caseRoot,n).toPath(),t);}
  static String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",v));return s.toString();}
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  static String reader() {
    Context context=new ContextWrapper(null){public File getNoBackupFilesDir(){return caseRoot;}public Context getApplicationContext(){return this;}};
    ReadReaderSourceSlice r=new ReadReaderSourceSlice(context);r.invoke();
    check(!r.getSave().isEnabled()&&!r.getShare().isEnabled()&&r.getCurrentReadProbe().isEnabled(),"reader state");
    String text=r.getOutput().getText();check(text.equals(new ReceiptReadProbeStore(readRoot(),IO).read()),"reader exact full body");return text;
  }
  static void nativeFixture(boolean success){
    nativeTime+=100;BluetoothGatt g=new BluetoothGatt();BluetoothGattCharacteristic c=new BluetoothGattCharacteristic(new UUID(0x9999999999994999L,0x8999999999999999L));RxBleGattCallback cb=new RxBleGattCallback();ReadDriver op=new ReadDriver(cb,g,c);
    ObservableEmitter<byte[]> e=new ObservableEmitter<byte[]>(){public void setCancellable(Cancellable v){}public void onNext(byte[] v){}public void onComplete(){}public boolean tryOnError(Throwable t){return true;}};
    g.onRead=()->{if(success)cb.invoke(g,c,0);};op.run(e,()->{});nativeTime+=100;
  }
  static JSONObject command(JSONObject q)throws Exception{
    String op=q.getString("op");String label=q.optString("label","result");
    if(op.equals("init")){caseRoot=new File(q.getString("root"));check(caseRoot.mkdirs(),"fresh task-local case");nativeTime=501000;lane=new HugrReceiptReadObserver.Lane(()->nativeTime);lane.begin();HugrReceiptReadObserver.installForTest(lane);nativeFixture(true);return new JSONObject().put("ok",true);}
    if(op.equals("native-stalled")){nativeFixture(false);return new JSONObject().put("ok",true);}
    if(op.equals("read")){String text=reader();save(label+".reader.utf8.txt",text);File f=new File(readRoot(),READ_FILE);if(f.exists())Files.copy(f.toPath(),new File(caseRoot,label+".hugr-probe").toPath(),StandardCopyOption.REPLACE_EXISTING);return new JSONObject().put("ok",true).put("text",text).put("fileSha256",f.exists()?hash(Files.readAllBytes(f.toPath())):JSONObject.NULL);}
    if(op.equals("read-publish")||op.equals("interrupted-read-publish")){
      ReceiptIncidentFileIo use=IO;
      if(op.startsWith("interrupted"))use=new ReceiptIncidentFileIo(){public void writeAndSync(File f,byte[] b){IO.writeAndSync(f,b);throw new IllegalStateException("SYNTHETIC_STOP_AFTER_REAL_PART_WRITE_FSYNC_BEFORE_RENAME");}public void atomicReplace(File a,File b){IO.atomicReplace(a,b);}public void syncDirectory(File d){IO.syncDirectory(d);}public byte[] readFully(File f){return IO.readFully(f);}};
      String error=null,rendered=null;try{rendered=new ReceiptReadProbeStore(readRoot(),use).publish(lane.snapshot(),q.getString("outcome"),q.getDouble("coalesced"),q.getDouble("at"),q.has("originJson")?q.getString("originJson"):null);}catch(Throwable t){error=t.toString();}
      if(error!=null){save(label+".error.txt",error+"\n");File part=new File(readRoot(),"."+READ_FILE+".part");if(part.exists())Files.copy(part.toPath(),new File(caseRoot,label+".retained-real.part").toPath(),StandardCopyOption.REPLACE_EXISTING);return new JSONObject().put("ok",false).put("error",error);}
      save(label+".publish-render.utf8.txt",rendered);File file=new File(readRoot(),READ_FILE);byte[] bytes=Files.readAllBytes(file.toPath());JSONObject envelope=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));byte[] body=Base64.getDecoder().decode(envelope.getString("body_base64"));check(hash(body).equals(envelope.getString("body_sha256")),"real body hash");Files.write(new File(caseRoot,label+".body.exact.json").toPath(),body);Files.copy(file.toPath(),new File(caseRoot,label+".hugr-probe").toPath(),StandardCopyOption.REPLACE_EXISTING);String reopened=reader();check(rendered.equals(reopened),"publish/reopened selectable source output");save(label+".reader.utf8.txt",reopened);return new JSONObject().put("ok",true).put("fileSha256",hash(bytes)).put("bodySha256",hash(body));
    }
    if(op.equals("primary-publish")){
      ReceiptIncidentPublication p=new ReceiptIncidentDiagnosticStore(new File(caseRoot,"primary"),IO).publish(q.getString("body"));save(label+".primary.bridge-body.exact.json",q.getString("body"));String rendered=new ReceiptIncidentDiagnosticStore(new File(caseRoot,"primary"),IO).readAll();save(label+".primary.reopened.utf8.txt",rendered);for(File f:new File(caseRoot,"primary").listFiles())if(f.getName().endsWith(".hugr-incident"))Files.copy(f.toPath(),new File(caseRoot,label+".primary.hugr-incident").toPath(),StandardCopyOption.REPLACE_EXISTING);return new JSONObject().put("incidentPseudonym",p.getIncidentPseudonym()).put("revision",p.getRevision()).put("commitState",p.getCommitState()).put("acknowledgedAtomicPublication",p.getAcknowledgedAtomicPublication()).put("reportBytes",p.getReportBytes());
    }
    throw new IllegalArgumentException(op);
  }
  public static void main(String[] args)throws Exception{
    BufferedReader in=new BufferedReader(new InputStreamReader(System.in));String line;
    while((line=in.readLine())!=null){JSONObject q=new JSONObject(line);JSONObject response;try{response=command(q);}catch(Throwable t){t.printStackTrace(System.err);response=new JSONObject().put("fatal",t.toString());}response.put("id",q.getInt("id"));System.out.println(response.toString());System.out.flush();}
  }
}
