package com.hugr.wearos

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test

class FreshRunReceiptTest {
    private class Rig(val root: File = Files.createTempDirectory("fresh-v3-").toFile(), val fault: (String) -> Unit = {}) {
        var elapsed = 1000L; var wall = 1_800_000_000_000L
        val store = FreshRunReceiptStore(File(root,"fresh_ordinary_runs_v3"), fault = fault)
        val run = UUID.randomUUID()
        val journal = SourceJournal(store.sourceRoot(run), 10, { Long.MAX_VALUE }, { wall }, maxSegmentAgeMs = 60_000)
        val initial = FreshRunReceipt(journal.watchBootSessionId, run, startWallMs = wall, startElapsedMs = elapsed, bootCount = 10)
        val session: FreshRunSession
        val records = mutableListOf<WatchSourceRecord>()
        val sealed = mutableListOf<SourceSegmentManifest>()
        init { store.createInExistingNewRun(initial); session = FreshRunSession(journal,store,initial,{elapsed},{wall}) }
        fun begin(supported: Int = 31, started: Int = 31) { assertTrue(session.begin()); session.scope(supported,started) }
        fun batch() { SourceStreamCode.entries.forEach { records += requireNotNull(session.append(it, wall, byteArrayOf(1,2,3,4))) } }
        fun batchValid() {
            val payloads = mapOf(
                SourceStreamCode.CARDIAC to SourcePayloadCodec.cardiac(1,60,0,1,0,1,-1,0,false,"REALTIME",1,true),
                SourceStreamCode.EDA to SourcePayloadCodec.eda(0.5f,"REALTIME",1,true),
                SourceStreamCode.ACCEL to SourcePayloadCodec.accel(10,20,1000,"REALTIME",1,true),
                SourceStreamCode.SKIN_TEMP to SourcePayloadCodec.skinTemperature(33.5f,24f,0,"REALTIME",1,true),
                SourceStreamCode.DEVICE_HEALTH to SourcePayloadCodec.deviceHealth(80,14,27,1,77,5,0,0,0,0,0,517,0,false,0,0,0,0),
            )
            SourceStreamCode.entries.forEach { records += requireNotNull(session.append(it,wall,payloads.getValue(it))) }
        }
        fun seal(): SourceSegmentManifest { val m = requireNotNull(journal.finalizeActiveSegment()); session.captureManifests(); sealed += m; return m }
        fun ack(m: SourceSegmentManifest) { assertTrue(session.acceptAck(SourceSegmentAcknowledgement(initial.sourceId,m.lastRecordIndex,m.sha256Hex),m.lastRecordIndex)) }
        fun finishTime() { elapsed += 300_000; wall += 300_000 }
        fun close() { runCatching { journal.close() }; root.deleteRecursively() }
    }
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected fail-closed rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    private fun copyReplica(source: File, target: File) {
        require(!target.exists())
        require(source.copyRecursively(target, overwrite = false))
    }
    @Test fun `sealed streaming ACK survives deletion then final inventory survives restart and lost C1 response`() {
        val r = Rig(); try {
            r.begin(); r.batch(); val first = r.seal(); r.ack(first)
            assertFalse(r.journal.hasFinalizedSegments(r.initial.sourceId))
            assertEquals(1,r.session.snapshot().manifests.size); assertEquals(1,r.session.snapshot().acks.size)
            r.elapsed += 1000; r.wall += 1000; r.batch()
            r.finishTime(); r.session.quiesce(); r.session.finalizeRecording()
            assertEquals(FreshRunPhase.FINALIZED,r.session.snapshot().phase)
            val final = r.journal.finalizedManifests().single(); r.ack(final)
            val terminal = r.session.snapshot(); assertEquals(FreshRunPhase.COMPLETED,terminal.phase)
            assertEquals(2,terminal.manifests.size); assertEquals(2,terminal.acks.size)
            val reopened = FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3")).current()!!
            assertArrayEquals(terminal.bytes(),reopened.bytes())
            val pager = FreshRunReceiptPager { reopened }; pager.request("R1:0")
            val confirm = "C1:${terminal.sourceId}:${FreshRunReceipt.digest(terminal.bytes())}"
            assertTrue(pager.confirms(confirm)); pager.reset(); assertFalse(pager.confirms(confirm))
            pager.request("R1:0"); assertTrue(pager.confirms(confirm))
            assertNull(r.session.append(SourceStreamCode.EDA,r.wall,byteArrayOf(9)))
            assertArrayEquals(terminal.bytes(),r.store.current()!!.bytes())
        } finally { r.close() }
    }
    @Test fun `fully drained sealed inventory completes from durable finalization without fabricated post final ACK`() {
        val r=Rig();try{
            r.begin();r.batchValid();val only=r.seal();r.ack(only)
            assertFalse(r.journal.hasFinalizedSegments(r.initial.sourceId))
            r.finishTime();r.session.finalizeRecording()
            val completed=r.session.snapshot()
            assertEquals(FreshRunPhase.COMPLETED,completed.phase)
            assertEquals(1,completed.manifests.size);assertEquals(1,completed.acks.size)
            assertTrue(completed.acks.single().elapsed < completed.finalizedElapsedMs)
            assertNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,byteArrayOf(7)))
        }finally{r.close()}
    }
    @Test fun `no automatic samples and five minute elapsed boundary rejects late callbacks and metadata`() {
        val r=Rig(); try {
            assertNull(r.session.append(SourceStreamCode.EDA,r.wall,byteArrayOf(1)))
            r.begin(); r.batch(); val index=r.journal.latestRecordIndex()
            r.elapsed += 299_999; assertNotNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,byteArrayOf(1)))
            r.elapsed++; assertFalse(r.session.acceptsSamples()); assertNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,byteArrayOf(1)))
            r.session.quiesce(); r.wall += 300_000; r.session.finalizeRecording()
            assertEquals(index+1,r.journal.latestRecordIndex()); assertFalse(r.session.begin())
        } finally { r.close() }
    }
    @Test fun `ACK exact endpoint hash and source queued tuple rejects leave canonical intact`() {
        val r=Rig(); try {
            r.begin();r.batch();val m=r.seal(); val ack=SourceSegmentAcknowledgement(r.initial.sourceId,m.lastRecordIndex,m.sha256Hex)
            rejects { r.session.acceptAck(ack.copy(watchBootSessionId=UUID.randomUUID()),m.lastRecordIndex) }
            rejects { r.session.acceptAck(ack.copy(completedSegmentSha256="0".repeat(64)),m.lastRecordIndex) }
            rejects { r.session.acceptAck(ack,m.lastRecordIndex+1) }
            assertTrue(r.journal.hasFinalizedSegments(r.initial.sourceId)); assertTrue(r.session.snapshot().acks.isEmpty())
            r.ack(m); rejects { r.session.acceptAck(ack,m.lastRecordIndex) }
        } finally { r.close() }
    }
    @Test fun `pre ACK receipt write failure never deletes source and post validator publication failure never qualifies`() {
        for (afterDelete in listOf(false,true)) {
            var enabled=false; var writes=0
            val r=Rig(fault={ stage -> if(enabled && stage=="WRITE") { writes++; if(writes==(if(afterDelete) 2 else 1)) error("synthetic IO") } })
            try {
                r.begin();r.batch();val m=r.seal();enabled=true
                rejects { r.ack(m) }
                assertEquals(!afterDelete,r.journal.hasFinalizedSegments(r.initial.sourceId))
                assertNull(r.session.append(SourceStreamCode.EDA,r.wall,byteArrayOf(1)))
                if (afterDelete) rejects { r.store.current() }
                else assertEquals(FreshRunPhase.FAILED,r.store.current()!!.phase)
            } finally { r.close() }
        }
    }
    @Test fun `publication fsync rename readback failures fail closed without previous success fallback`() {
        for (stage in listOf("WRITE","RENAME","DIRECTORY_SYNC","READBACK")) {
            var enabled=false
            val r=Rig(fault={ if(enabled && it==stage) error("synthetic IO") })
            try { r.begin(31,15);enabled=true; rejects { r.session.scope(31,31) }; rejects { r.session.snapshot() } }
            finally { r.close() }
        }
    }
    @Test fun `interrupted different run selection never falls back to prior completed receipt`() {
        val r=Rig();try {
            r.begin();r.batch();r.finishTime();r.session.finalizeRecording();r.ack(r.journal.finalizedManifests().single())
            var writes=0
            val broken=FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3"),fault={if(it=="WRITE" && ++writes==2) error("synthetic IO")})
            rejects { broken.create(r.initial.copy(runId=UUID.randomUUID(),sourceId=UUID.randomUUID())) }
            rejects { r.store.current() }
        }finally{r.close()}
    }
    @Test fun `page snapshot immutable bounded MTU strict offsets and exact terminal C1`() {
        val r=Rig();try {
            r.begin();r.batch();r.seal(); var latest=r.session.snapshot()
            val pager=FreshRunReceiptPager{latest}; rejects{pager.request("R1:1")};pager.request("R1:0")
            rejects{pager.frame(23)}
            val old=latest.bytes(); val first=pager.frame(517);assertTrue(first.size<=428)
            val header=ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN);assertEquals(0x31524748,header.int);assertEquals(old.size,header.int);assertEquals(0,header.int)
            r.batch();r.seal();latest=r.session.snapshot()
            val joined=java.io.ByteArrayOutputStream();joined.write(first.copyOfRange(44,first.size));var offset=first.size-44
            while(offset<old.size){pager.request("R1:$offset");val frame=pager.frame(100);assertTrue(frame.size<=99);joined.write(frame.copyOfRange(44,frame.size));offset+=frame.size-44}
            assertArrayEquals(old,joined.toByteArray()); rejects{pager.request("R1:${old.size}")};rejects{pager.request("R1:-1")};rejects{pager.request("R1:01")}
            assertFalse(pager.confirms("C1:${latest.sourceId}:${FreshRunReceipt.digest(old)}"))
            pager.reset();rejects{pager.frame()}
        }finally{r.close()}
    }
    @Test fun `no receipt zero frame and unsafe symlink oversize corruption stop`() {
        val pager=FreshRunReceiptPager{null};pager.request("R1:0");assertArrayEquals(ByteArray(32),pager.frame().copyOfRange(12,44));assertEquals(44,pager.frame().size)
        val r=Rig();try{
            val receiptFile=File(r.root,"fresh_ordinary_runs_v3/${r.run}/receipt")
            receiptFile.appendText("x");rejects{r.store.current()}
            rejects{FreshRunReceipt.parse(ByteArray(65_537))}
            val outside=Files.createTempDirectory("fresh-external-").toFile()
            val link=File(r.root,"symlink");Files.createSymbolicLink(link.toPath(),outside.toPath())
            rejects{FreshRunReceiptStore(link).current()};outside.deleteRecursively()
        }finally{r.close()}
    }
    @Test fun `dangling transaction gates and receipt tmp symlink fail closed`() {
        listOf("selection_pending","publication_pending","ack_pending").forEach { gate ->
            val r=Rig();try{
                r.begin();r.batchValid()
                val gateFile=if(gate=="selection_pending") File(r.root,"fresh_ordinary_runs_v3/$gate") else File(r.root,"fresh_ordinary_runs_v3/${r.run}/$gate")
                gateFile.writeText("interrupted")
                rejects{r.store.current()}
            }finally{r.close()}
        }
        val r=Rig();try{
            r.begin();r.batchValid()
            val outside=Files.createTempDirectory("fresh-tmp-external-").toFile()
            val tmp=File(r.root,"fresh_ordinary_runs_v3/${r.run}/receipt.tmp")
            Files.createSymbolicLink(tmp.toPath(),outside.toPath())
            rejects{r.session.scope(31,30)}
            outside.deleteRecursively()
        }finally{r.close()}
    }
    @Test fun `unsupported missing source empty sensor and duration failures never complete despite ACK`() {
        for (mode in listOf("UNSUPPORTED","START_FAILED","MISSING","EMPTY","SHORT")) {
            val r=Rig();try{
                r.begin(if(mode=="UNSUPPORTED")15 else 31,if(mode=="UNSUPPORTED" || mode=="START_FAILED")15 else 31)
                when(mode){
                    "UNSUPPORTED"-> r.batch()
                    "START_FAILED"-> r.batch()
                    "MISSING"-> {r.records+=r.session.append(SourceStreamCode.EDA,r.wall,byteArrayOf(1))!!}
                    "EMPTY"-> {r.records+=r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,byteArrayOf(1))!!}
                    else->r.batch()
                }
                val m=r.seal();r.ack(m);if(mode!="SHORT")r.finishTime();r.session.finalizeRecording()
                assertEquals(mode,FreshRunPhase.FAILED,r.session.snapshot().phase)
            }finally{r.close()}
        }
    }
    @Test fun `fresh v3 receipt only isolation never touches v2 v1 frozen or legacy sentinel roots`() {
        val r=Rig();try{
            val names=listOf("fresh_ordinary_source_journal_v2","fresh_ordinary_source_journal_v1","build45_source_journal","exact_range_readbacks")
            names.forEach{File(r.root,it).mkdirs();File(r.root,"$it/sentinel").writeText("UNCHANGED-$it")}
            r.begin();r.batch();r.finishTime();r.session.finalizeRecording();r.ack(r.journal.finalizedManifests().single())
            val prior=names.associateWith{File(r.root,"$it/sentinel").readBytes()}
            val reopened=FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3")); val pager=FreshRunReceiptPager{reopened.current()};pager.request("R1:0");pager.frame()
            names.forEach{assertArrayEquals(prior[it],File(r.root,"$it/sentinel").readBytes())}
            val other=UUID.randomUUID();assertNotEquals(r.store.sourceRoot(r.run),r.store.sourceRoot(other))
        }finally{r.close()}
    }
    @Test fun `concurrent callback and quiescence serialize with no append after controlled finalization`() {
        val r=Rig();try{
            r.begin();r.batch(); val latch=CountDownLatch(1)
            val worker=Thread{latch.await();repeat(30){r.session.append(SourceStreamCode.EDA,r.wall,byteArrayOf(1))}}
            worker.start();latch.countDown();r.session.quiesce();worker.join();val index=r.journal.latestRecordIndex()
            r.finishTime();r.session.finalizeRecording();repeat(10){assertNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,byteArrayOf(1)))}
            assertEquals(index,r.journal.latestRecordIndex())
        }finally{r.close()}
    }
    @Test fun `codec rejects changed final inventory foreign source revision regression and corrupt bytes`() {
        val r=Rig();try{
            r.begin();r.batch();r.finishTime();r.session.finalizeRecording();val receipt=r.session.snapshot()
            rejects{r.store.publish(receipt.copy(revision=receipt.revision))}
            rejects{r.store.publish(receipt.copy(revision=receipt.revision+1,sourceId=UUID.randomUUID()))}
            rejects{r.store.publish(receipt.copy(revision=receipt.revision+1,manifests=emptyList()))}
            rejects{FreshRunReceipt.parse(receipt.bytes()+byteArrayOf(13))}
            rejects{FreshRunReceipt.parse(receipt.bytes().toString(Charsets.US_ASCII).replace("scope\t31", "scope\t63").toByteArray())}
        }finally{r.close()}
    }
    @Test fun `produce real Watch canonical joined fixture streaming acceptance finalized completion restart`() {
        if(System.getenv("HUGR_FRESH_VERIFY_FIXTURE")=="1") return
        val output=System.getenv("HUGR_FRESH_FIXTURE_DIR")?:return
        val dir=File(output);dir.mkdirs();val r=Rig();try{
            r.begin(); File(dir,"started.tsv").writeBytes(r.session.snapshot().bytes())
            r.batchValid();val first=r.seal();r.ack(first);File(dir,"streaming-acked.tsv").writeBytes(r.session.snapshot().bytes())
            r.elapsed+=1000;r.wall+=1000;r.batchValid();r.finishTime();r.session.finalizeRecording()
            val final=r.journal.finalizedManifests().single();r.sealed+=final;File(dir,"finalized.tsv").writeBytes(r.session.snapshot().bytes())
            r.journal.forceSync();copyReplica(r.root,File(dir,"watch-verifier-root"))
            r.ack(final)
            val completed=r.store.current()!!;File(dir,"completed.tsv").writeBytes(completed.bytes());File(dir,"restart.tsv").writeBytes(FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3")).current()!!.bytes())
            File(dir,"source-id.txt").writeText("${r.initial.sourceId}\n")
            File(dir,"canonical-records.base64").writeText(r.records.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(it.canonicalBytes())})
            File(dir,"manifest-frames.base64").writeText(r.sealed.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(it)))})
            File(dir,"ack-frames.base64").writeText(r.sealed.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeSegmentAcknowledgement(SourceSegmentAcknowledgement(r.initial.sourceId,it.lastRecordIndex,it.sha256Hex)))})
            File(dir,"receipt-digest.txt").writeText(FreshRunReceipt.digest(completed.bytes())+"\n")
        }finally{r.close()}
    }
    @Test fun `verify Phone exact ACK frames against preserved real Watch pre final ACK replica`() {
        if(System.getenv("HUGR_FRESH_VERIFY_FIXTURE")!="1") return
        val dir=File(requireNotNull(System.getenv("HUGR_FRESH_FIXTURE_DIR")))
        val root=File(dir,"watch-verifier-root")
        val store=FreshRunReceiptStore(File(root,"fresh_ordinary_runs_v3"))
        val before=store.current()!!
        assertEquals(FreshRunPhase.FINALIZED,before.phase)
        val journal=SourceJournal(store.sourceRoot(before.runId),before.bootCount,{Long.MAX_VALUE},{before.finalizedWallMs})
        try{
            assertEquals(before.sourceId,journal.watchBootSessionId)
            val session=FreshRunSession(journal,store,before,{before.finalizedElapsedMs},{before.finalizedWallMs})
            val phoneAcks=File(dir,"phone-exact-acks.base64").readLines().filter{it.isNotBlank()}.map{
                SourceReplayProtocol.decodeSegmentAcknowledgement(Base64.getDecoder().decode(it))
            }
            assertEquals(before.manifests.size,phoneAcks.size)
            val expected=before.manifests.associateBy{it.lastRecordIndex}
            assertEquals(expected.keys,phoneAcks.map{it.cumulativeRecordIndex}.toSet())
            val prior=before.acks.associateBy{it.last}
            val retainedBefore=journal.latestRecordIndex()
            phoneAcks.forEach { ack ->
                val manifest=requireNotNull(expected[ack.cumulativeRecordIndex])
                assertEquals(before.sourceId,ack.watchBootSessionId);assertEquals(manifest.sha256Hex,ack.completedSegmentSha256)
                if(prior.containsKey(ack.cumulativeRecordIndex)) {
                    assertEquals(prior.getValue(ack.cumulativeRecordIndex).hash,ack.completedSegmentSha256)
                } else assertTrue(session.acceptAck(ack,ack.cumulativeRecordIndex))
            }
            val completed=session.snapshot()
            assertEquals(FreshRunPhase.COMPLETED,completed.phase)
            assertEquals(before.sourceId,completed.sourceId)
            assertEquals(retainedBefore,journal.latestRecordIndex())
            assertFalse(journal.hasFinalizedSegments(before.sourceId))
            assertNull(session.append(SourceStreamCode.DEVICE_HEALTH,before.finalizedWallMs,byteArrayOf(9)))
            assertArrayEquals(File(dir,"completed.tsv").readBytes(),completed.bytes())
        }finally{journal.close()}
    }
}
