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
    private class Rig(
        val root: File = Files.createTempDirectory("fresh-v3-").toFile(),
        val fault: (String) -> Unit = {},
        val durationMs: Long = WatchFreshRunDurationPolicy.FIVE_MINUTES_MS,
        val version: FreshRunReceiptVersion = FreshRunReceiptVersion.V1,
    ) {
        var elapsed = 1000L; var wall = 1_800_000_000_000L
        val store = FreshRunReceiptStore(File(root,"fresh_ordinary_runs_v3"), fault = fault)
        val run = UUID.randomUUID()
        val journal = SourceJournal(store.sourceRoot(run), 10, { Long.MAX_VALUE }, { wall }, maxSegmentAgeMs = 60_000)
        val initial = FreshRunReceipt(journal.watchBootSessionId, run, durationMs = durationMs, startWallMs = wall, startElapsedMs = elapsed, bootCount = 10, version = version)
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
        fun finishTime() { elapsed += durationMs; wall += durationMs }
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
    @Test fun `receipt inventory capture observes finalized index without consuming GATT manifest queue`() {
        val r=Rig();try{
            r.begin();r.batchValid();val manifest=requireNotNull(r.journal.finalizeActiveSegment())
            r.session.captureManifests()
            assertEquals(listOf(manifest),r.journal.drainNewlyFinalizedManifests())
            val receiptManifest = r.session.snapshot().manifests.single()
            assertTrue(FreshRunReceiptStore.sameInventory(listOf(manifest), listOf(receiptManifest)))
            assertEquals("receipt_${manifest.firstRecordIndex}_${manifest.lastRecordIndex}", receiptManifest.segmentId)
            assertNotEquals(manifest.segmentId, receiptManifest.segmentId)
        }finally{r.close()}
    }
    @Test fun `V2 late callbacks never append at exact selected duration`() {
        val r=Rig(durationMs=WatchFreshRunDurationPolicy.TWO_HOURS_MS,version=FreshRunReceiptVersion.V2);try{
            r.begin();r.batchValid();r.elapsed+=r.initial.durationMs-1
            assertNotNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,fixturePayload(SourceStreamCode.DEVICE_HEALTH)))
            r.elapsed++
            assertFalse(r.session.acceptsSamples())
            assertNull(r.session.append(SourceStreamCode.DEVICE_HEALTH,r.wall,fixturePayload(SourceStreamCode.DEVICE_HEALTH)))
        }finally{r.close()}
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
    @Test fun `V2 closed duration ladder binds exact duration and absolute delivery deadline while V1 remains readable`() {
        val source=UUID.randomUUID(); val v1=FreshRunReceipt(source,UUID.randomUUID(),startWallMs=1_800_000_000_000L,startElapsedMs=1_000L,bootCount=10)
        assertTrue(v1.bytes().toString(Charsets.US_ASCII).startsWith("HUGR_RUN_RECEIPT_V1\n"))
        assertEquals(FreshRunReceiptVersion.V1,FreshRunReceipt.parse(v1.bytes()).version)
        listOf(WatchFreshRunDurationPolicy.FIVE_MINUTES_MS,WatchFreshRunDurationPolicy.THIRTY_MINUTES_MS,WatchFreshRunDurationPolicy.TWO_HOURS_MS,WatchFreshRunDurationPolicy.EIGHT_HOURS_MS).forEach { duration ->
            val v2=v1.copy(runId=UUID.randomUUID(),durationMs=duration,version=FreshRunReceiptVersion.V2)
            assertTrue(v2.bytes().toString(Charsets.US_ASCII).startsWith("HUGR_RUN_RECEIPT_V2\n"))
            assertEquals(duration,FreshRunReceipt.parse(v2.bytes()).durationMs)
            assertEquals(v2.startElapsedMs+duration+WatchFreshRunDurationPolicy.DELIVERY_GRACE_MS,v2.deliveryDeadlineElapsedMs())
        }
        rejects { v1.copy(durationMs=600_000L,version=FreshRunReceiptVersion.V2).bytes() }
        rejects { v1.copy(durationMs=600_000L).bytes() }
    }
    @Test fun `V2 duration minus one accepts and exact duration rejects late sample while finalization remains exact`() {
        val root=Files.createTempDirectory("fresh-v2-boundary-").toFile(); var elapsed=1_000L; var wall=1_800_000_000_000L
        val store=FreshRunReceiptStore(File(root,"runs")); val run=UUID.randomUUID(); val journal=SourceJournal(store.sourceRoot(run),10,{Long.MAX_VALUE},{wall},maxSegmentAgeMs=Long.MAX_VALUE)
        try{
            val initial=FreshRunReceipt(journal.watchBootSessionId,run,durationMs=WatchFreshRunDurationPolicy.THIRTY_MINUTES_MS,startWallMs=wall,startElapsedMs=elapsed,bootCount=10,version=FreshRunReceiptVersion.V2)
            store.createInExistingNewRun(initial); val session=FreshRunSession(journal,store,initial,{elapsed},{wall})
            assertTrue(session.begin());session.scope(31,31);elapsed+=initial.durationMs-1;assertNotNull(session.append(SourceStreamCode.DEVICE_HEALTH,wall,byteArrayOf(1)))
            elapsed++;assertNull(session.append(SourceStreamCode.DEVICE_HEALTH,wall,byteArrayOf(1)));session.quiesce();session.finalizeRecording()
            assertEquals(FreshRunPhase.FAILED,session.snapshot().phase);assertEquals("STREAM_MISSING",session.snapshot().failure)
        }finally{journal.close();root.deleteRecursively()}
    }
    @Test fun `V2 textual 2048 manifest and ACK full five-range inventory parses and round-trips under two MiB`() {
        val source=UUID.randomUUID();val manifests=(0 until WatchFreshRunDurationPolicy.MAX_MANIFESTS).map { index ->
            val first=index*5L+1;val ranges=SourceStreamCode.entries.associateWith { SourceStreamRange(1,first,first) }
            SourceSegmentManifest("fixture_$index",source,first,first+4,5,240,"%064x".format(index+1),ranges)
        };val acks=manifests.map { FreshAcceptedAck(it.lastRecordIndex,it.sha256Hex,29_700_000L) }
        val receipt=FreshRunReceipt(source,UUID.randomUUID(),revision=2,phase=FreshRunPhase.FINALIZED,durationMs=WatchFreshRunDurationPolicy.EIGHT_HOURS_MS,startWallMs=1_800_000_000_000L,startElapsedMs=1_000L,finalizedWallMs=1_800_028_800_000L,finalizedElapsedMs=28_801_000L,bootCount=10,supportedMask=31,startedMask=31,manifests=manifests,acks=acks,version=FreshRunReceiptVersion.V2)
        val bytes=receipt.bytes(); val text=bytes.toString(Charsets.US_ASCII)
        assertTrue("actual V2 inventory must fit reconciled ceiling: ${bytes.size}",bytes.size<=2*1024*1024)
        assertTrue(text.startsWith("HUGR_RUN_RECEIPT_V2\n"));assertTrue(text.lineSequence().filter{it.startsWith("m\t")}.all{it.split('\t').size==7})
        assertTrue(text.lineSequence().filter{it.startsWith("a\t")}.all{it.split('\t').size==4})
        val parsed=FreshRunReceipt.parse(bytes);assertEquals(2_048,parsed.manifests.size);assertEquals(2_048,parsed.acks.size);assertArrayEquals(bytes,parsed.bytes())
    }
    @Test fun `V2 rejects unsupported duration and every finite capacity overflow while V1 bounds stay exact`() {
        val source=UUID.randomUUID();val base=FreshRunReceipt(source,UUID.randomUUID(),startWallMs=1,startElapsedMs=1,bootCount=1,version=FreshRunReceiptVersion.V2)
        rejects{base.copy(durationMs=299_999L).bytes()}; rejects{base.copy(durationMs=28_800_001L).bytes()}
        val manifest=SourceSegmentManifest("m",source,1,1,1,48,"a".repeat(64),mapOf(SourceStreamCode.DEVICE_HEALTH to SourceStreamRange(1,1,1)))
        rejects{base.copy(manifests=List(WatchFreshRunDurationPolicy.MAX_MANIFESTS+1){manifest.copy(firstRecordIndex=it.toLong()+1,lastRecordIndex=it.toLong()+1)}).bytes()}
        assertEquals(65_536,FreshRunReceipt.MAX_BYTES);assertEquals(64,FreshRunReceipt.MAX_V1_INVENTORY);assertEquals(1_200_000L,FreshRunReceipt.MAX_V1_LIFETIME_MS)
    }
    @Test fun `receipt pager permits seven digit grammar and fixes snapshot SHA at R1 zero`() {
        val r=Rig();try{
            r.begin();r.batchValid();r.seal();val frozen=r.session.snapshot();var reads=0
            val pager=FreshRunReceiptPager{reads++;frozen};pager.request("R1:0");val first=pager.frame()
            assertEquals(1,reads);pager.request("R1:1");val second=pager.frame();assertEquals(1,reads)
            assertArrayEquals(first.copyOfRange(12,44),second.copyOfRange(12,44))
            rejects{pager.request("R1:10000000")};rejects{pager.request("R1:0000001")}
        }finally{r.close()}
    }
    private fun fixturePayload(stream: SourceStreamCode): ByteArray = when(stream){
        SourceStreamCode.CARDIAC -> SourcePayloadCodec.cardiac(1,60,0,1,0,1,-1,0,false,"REALTIME",1,true)
        SourceStreamCode.EDA -> SourcePayloadCodec.eda(0.5f,"REALTIME",1,true)
        SourceStreamCode.ACCEL -> SourcePayloadCodec.accel(10,20,1000,"REALTIME",1,true)
        SourceStreamCode.SKIN_TEMP -> SourcePayloadCodec.skinTemperature(33.5f,24f,0,"REALTIME",1,true)
        SourceStreamCode.DEVICE_HEALTH -> SourcePayloadCodec.deviceHealth(80,14,27,1,77,5,0,0,0,0,0,517,0,false,0,0,0,0)
    }
    private fun cardiacPositionalV2FixtureEnabled(): Boolean = System.getenv("HUGR_CARDIAC_POSITIONAL_V2")=="1"
    private val syntheticPositionalMinimumBaseRecords = 4_178

    /**
     * Counts are deliberately fixture-local. Values and statuses below are
     * synthetic codec inputs, not a physiological model or an SDK guarantee.
     * The only accepted position evidence is the current production capture,
     * V1 projection and V2 encoder invoked below.
     */
    private class SyntheticPositionalInventory {
        var baselineProjectionRecords = 0L
        var v2Groups = 0L
        var v2Chunks = 0L
        var truncatedGroups = 0L
        var batch45Groups = 0L
        var fieldNullGroups = 0L
        var fieldNonListGroups = 0L
        var slotTypeMarkerGroups = 0L
    }

    private data class SyntheticJournalInventory(
        val canonicalRecords: Long,
        val sourceBytes: Long,
        val perStream: Map<SourceStreamCode, Long>,
        val v1HeartRateRecords: Long,
        val v1IbiProjectionRecords: Long,
        val v1AnomalousProjectionRecords: Long,
        val v2Groups: Long,
        val v2Chunks: Long,
        val v2TruncatedGroups: Long,
        val v2Batch45Groups: Long,
        val v2FieldNullGroups: Long,
        val v2FieldNonListGroups: Long,
        val v2SlotTypeMarkerGroups: Long,
        val manifests: List<SourceSegmentManifest>,
    )

    /** A deliberately non-list synthetic marker; it must never be stringified or coerced. */
    private class SyntheticNonListMarker

    private fun setSyntheticFixtureTime(r: Rig, baselineRecordsBefore: Long, baselineTarget: Int) {
        require(baselineRecordsBefore in 0 until baselineTarget.toLong())
        val offset = if (baselineTarget == 1) 0L else {
            baselineRecordsBefore * (r.initial.durationMs - 1L) / (baselineTarget - 1L)
        }
        r.elapsed = r.initial.startElapsedMs + offset
        r.wall = r.initial.startWallMs + offset
        require(r.elapsed - r.initial.startElapsedMs in 0 until r.initial.durationMs)
    }

    private fun appendSyntheticNonCardiac(r: Rig, inventory: SyntheticPositionalInventory, stream: SourceStreamCode) {
        require(stream != SourceStreamCode.CARDIAC)
        requireNotNull(r.session.append(stream, r.wall, fixturePayload(stream)))
        inventory.baselineProjectionRecords += 1L
    }

    /**
     * Mirrors the current callback path: capture raw shape once, append actual
     * V1 HR/IBI projection, then anchor all current V2 chunks to the actual
     * appended HR record. V2 records are source metadata, never beat records.
     */
    private fun appendSyntheticPositionalCardiac(
        r: Rig,
        inventory: SyntheticPositionalInventory,
        label: String,
        callbackId: Int,
        valuesGetter: () -> Any?,
        statusesGetter: () -> Any?,
        deliveryMode: String = "REALTIME",
        batchSize: Int = 1,
        screenOn: Boolean = true,
    ) {
        val shape = CardiacInputShape.capture(valuesGetter, statusesGetter)
        val point = SamsungCardiacPoint(
            sourceTimestamp = r.wall,
            // These fixture literals are synthetic codec fields, not HR claims.
            heartRate = 60 + (callbackId % 17),
            heartRateStatus = if (callbackId % 7 == 0) -1 else 0,
            ibiValuesMs = emptyList(),
            ibiStatuses = emptyList(),
            inputShape = shape,
        )
        val projection = flattenCardiacBatch(callbackId, listOf(point))
        require(projection.size == shape.capturedSlotCount + 1)
        projection.forEach { record ->
            val appended = requireNotNull(
                r.session.append(
                    SourceStreamCode.CARDIAC,
                    record.sourceTimestamp,
                    SourcePayloadCodec.cardiac(
                        kind = record.kind.wireCode,
                        value = record.value,
                        status = record.status,
                        callbackId = record.callbackId,
                        pointIndex = record.pointIndex,
                        pointCount = record.pointCount,
                        listIndex = record.listIndex,
                        listCount = record.listCount,
                        contractAnomaly = record.contractAnomaly,
                        deliveryMode = deliveryMode,
                        batchSize = batchSize,
                        screenOn = screenOn,
                    ),
                ),
            )
            inventory.baselineProjectionRecords += 1L
            if (record.kind != CardiacEvidenceKind.HEART_RATE) return@forEach
            val decoded = shape.chunks(
                callbackId = record.callbackId,
                pointIndex = record.pointIndex,
                pointCount = record.pointCount,
                anchorRecordIndex = appended.recordIndex,
            ).map { chunk ->
                val payload = SourcePayloadCodec.cardiacInputShape(chunk, deliveryMode, batchSize, screenOn)
                requireNotNull(r.session.append(SourceStreamCode.CARDIAC, appended.sourceTimestampMs, payload))
                SourcePayloadCodec.decodeCardiacInputShape(payload)
            }
            require(decoded.all { it.anchorRecordIndex == appended.recordIndex })
            CardiacInputShapeCodec.validateCompleteGroup(decoded)
            inventory.v2Groups += 1L
            inventory.v2Chunks += decoded.size.toLong()
            if (shape.truncated) inventory.truncatedGroups += 1L
            if (batchSize == 45) inventory.batch45Groups += 1L
            if (shape.valuesField.state == CardiacInputFieldState.NULL_OR_ABSENT ||
                shape.statusesField.state == CardiacInputFieldState.NULL_OR_ABSENT
            ) inventory.fieldNullGroups += 1L
            if (shape.valuesField.state == CardiacInputFieldState.NON_LIST ||
                shape.statusesField.state == CardiacInputFieldState.NON_LIST
            ) inventory.fieldNonListGroups += 1L
            if (shape.slots.any {
                    it.valueKind !in setOf(CardiacInputSlotKind.INT32, CardiacInputSlotKind.ABSENT) ||
                        it.statusKind !in setOf(CardiacInputSlotKind.INT32, CardiacInputSlotKind.ABSENT)
                }
            ) inventory.slotTypeMarkerGroups += 1L
        }
        // The labels are intentionally only fixture labels; no data value is
        // assigned physiology or SDK provenance by this test.
        require(label.startsWith("synthetic_"))
    }

    private fun appendSyntheticNormalCardiac(r: Rig, inventory: SyntheticPositionalInventory, callbackId: Int, slots: Int) {
        require(slots in 1..5)
        appendSyntheticPositionalCardiac(
            r, inventory, "synthetic_normal_${slots}_ibi_slots", callbackId,
            valuesGetter = { List(slots) { 700 + callbackId + it } },
            statusesGetter = { List(slots) { if ((callbackId + it) % 4 == 0) -1 else 0 } },
        )
    }

    /** Produces an exact baseline V1-projection count plus additive V2 metadata chunks. */
    private fun appendSyntheticPositionalWorkload(r: Rig, baselineTarget: Int): SyntheticPositionalInventory {
        require(baselineTarget >= syntheticPositionalMinimumBaseRecords) {
            "Synthetic positional fixture needs $syntheticPositionalMinimumBaseRecords V1-projection records"
        }
        val inventory = SyntheticPositionalInventory()
        var callbackId = 1
        fun atNextBaselineRecord(block: () -> Unit) {
            setSyntheticFixtureTime(r, inventory.baselineProjectionRecords, baselineTarget)
            block()
            require(inventory.baselineProjectionRecords <= baselineTarget.toLong())
        }
        (1..5).forEach { slots -> atNextBaselineRecord { appendSyntheticNormalCardiac(r, inventory, callbackId++, slots) } }
        atNextBaselineRecord {
            appendSyntheticPositionalCardiac(
                r, inventory, "synthetic_batch45_three_chunks", callbackId++,
                valuesGetter = { List(45) { 800 + it } },
                statusesGetter = { List(45) { if (it % 11 == 0) -1 else 0 } },
                deliveryMode = "FLUSH", batchSize = 45, screenOn = false,
            )
        }
        atNextBaselineRecord {
            appendSyntheticPositionalCardiac(
                r, inventory, "synthetic_field_null_with_paired_status_slots", callbackId++,
                valuesGetter = { null }, statusesGetter = { listOf(0, -1) },
            )
        }
        atNextBaselineRecord {
            appendSyntheticPositionalCardiac(
                r, inventory, "synthetic_field_non_list_with_paired_status_slots", callbackId++,
                valuesGetter = { SyntheticNonListMarker() }, statusesGetter = { listOf(0, -1) },
            )
        }
        atNextBaselineRecord {
            appendSyntheticPositionalCardiac(
                r, inventory, "synthetic_member_type_and_null_markers", callbackId++,
                valuesGetter = { listOf<Any?>(710, 711L, "synthetic-other-type", null) },
                statusesGetter = { listOf<Any?>(0, 2.5, false, -1) },
            )
        }
        atNextBaselineRecord {
            appendSyntheticPositionalCardiac(
                r, inventory, "synthetic_oversized_4097_original_4096_captured", callbackId++,
                valuesGetter = { List(4_097) { 900 + it } },
                statusesGetter = { List(4_097) { if (it % 19 == 0) -1 else 0 } },
            )
        }
        SourceStreamCode.entries.filter { it != SourceStreamCode.CARDIAC }.forEach { stream ->
            atNextBaselineRecord { appendSyntheticNonCardiac(r, inventory, stream) }
        }
        require(inventory.baselineProjectionRecords == syntheticPositionalMinimumBaseRecords.toLong())
        require(inventory.v2Chunks == 216L) // 5 normal + 3 batch45 + 3 marked groups + 205 capped oversize chunks.
        require(inventory.truncatedGroups == 1L && inventory.batch45Groups == 1L)
        require(inventory.fieldNullGroups == 1L && inventory.fieldNonListGroups == 1L)
        require(inventory.slotTypeMarkerGroups >= 1L)

        // A cycle has V1 projections 2+3+4+5+6 plus four real non-CARDIAC codec fields = 24.
        while (baselineTarget.toLong() - inventory.baselineProjectionRecords >= 24L) {
            (1..5).forEach { slots -> atNextBaselineRecord { appendSyntheticNormalCardiac(r, inventory, callbackId++, slots) } }
            SourceStreamCode.entries.filter { it != SourceStreamCode.CARDIAC }.forEach { stream ->
                atNextBaselineRecord { appendSyntheticNonCardiac(r, inventory, stream) }
            }
        }
        for (slots in 5 downTo 1) {
            while (baselineTarget.toLong() - inventory.baselineProjectionRecords >= slots + 1L) {
                atNextBaselineRecord { appendSyntheticNormalCardiac(r, inventory, callbackId++, slots) }
            }
        }
        var residualStream = SourceStreamCode.EDA
        while (inventory.baselineProjectionRecords < baselineTarget) {
            atNextBaselineRecord { appendSyntheticNonCardiac(r, inventory, residualStream) }
            residualStream = when (residualStream) {
                SourceStreamCode.EDA -> SourceStreamCode.ACCEL
                SourceStreamCode.ACCEL -> SourceStreamCode.SKIN_TEMP
                SourceStreamCode.SKIN_TEMP -> SourceStreamCode.DEVICE_HEALTH
                else -> SourceStreamCode.EDA
            }
        }
        require(inventory.baselineProjectionRecords == baselineTarget.toLong())
        return inventory
    }

    /** Re-reads current source-journal bytes before ACK deletion; no summary count is a fixture claim. */
    private fun inspectSyntheticPositionalJournal(
        r: Rig,
        inventory: SyntheticPositionalInventory,
        manifests: List<SourceSegmentManifest>,
    ): SyntheticJournalInventory {
        val perStream = SourceStreamCode.entries.associateWith { 0L }.toMutableMap()
        val expectedSequence = SourceStreamCode.entries.associateWith { 1L }.toMutableMap()
        val heartRateAnchors = HashSet<Long>()
        val pendingInputShape = mutableListOf<DecodedCardiacInputShapeChunk>()
        var expectedIndex = 1L
        var v1HeartRate = 0L; var v1Ibi = 0L; var v1Anomalous = 0L
        var v2Groups = 0L; var v2Chunks = 0L; var v2Truncated = 0L; var v2Batch45 = 0L
        var v2FieldNull = 0L; var v2FieldNonList = 0L; var v2SlotMarkers = 0L
        fun finishInputShapeGroup() {
            if (pendingInputShape.isEmpty()) return
            CardiacInputShapeCodec.validateCompleteGroup(pendingInputShape)
            val first = pendingInputShape.first()
            v2Groups += 1L
            if (first.truncated) v2Truncated += 1L
            if (first.batchSize == 45) v2Batch45 += 1L
            if (first.valuesFieldState == CardiacInputFieldState.NULL_OR_ABSENT ||
                first.statusesFieldState == CardiacInputFieldState.NULL_OR_ABSENT
            ) v2FieldNull += 1L
            if (first.valuesFieldState == CardiacInputFieldState.NON_LIST ||
                first.statusesFieldState == CardiacInputFieldState.NON_LIST
            ) v2FieldNonList += 1L
            if (pendingInputShape.any { chunk -> chunk.slots.any { slot ->
                    slot.valueKind !in setOf(CardiacInputSlotKind.INT32, CardiacInputSlotKind.ABSENT) ||
                        slot.statusKind !in setOf(CardiacInputSlotKind.INT32, CardiacInputSlotKind.ABSENT)
                } }
            ) v2SlotMarkers += 1L
            pendingInputShape.clear()
        }
        manifests.forEach { manifest ->
            var cursor = manifest.firstRecordIndex - 1L
            while (cursor < manifest.lastRecordIndex) {
                val block = r.journal.readRecordsAfter(r.initial.sourceId, cursor, manifest.lastRecordIndex, 25_000)
                require(block.isNotEmpty()) { "Synthetic fixture journal range missing" }
                block.forEach { record ->
                    require(record.recordIndex == expectedIndex++)
                    require(record.watchBootSessionId == r.initial.sourceId)
                    require(record.sourceSequence == expectedSequence.getValue(record.stream))
                    expectedSequence[record.stream] = record.sourceSequence + 1L
                    perStream[record.stream] = perStream.getValue(record.stream) + 1L
                    if (record.stream != SourceStreamCode.CARDIAC) {
                        finishInputShapeGroup()
                    } else when ((record.payload.firstOrNull()?.toInt() ?: -1) and 0xFF) {
                        1 -> {
                            finishInputShapeGroup()
                            require(record.payload.size == 25)
                            if ((record.payload[2].toInt() and 0x08) != 0) v1Anomalous += 1L
                            when (record.payload[1].toInt() and 0xFF) {
                                CardiacEvidenceKind.HEART_RATE.wireCode -> {
                                    heartRateAnchors += record.recordIndex; v1HeartRate += 1L
                                }
                                CardiacEvidenceKind.IBI.wireCode -> v1Ibi += 1L
                                else -> error("Unknown synthetic V1 CARDIAC kind")
                            }
                        }
                        2 -> {
                            val decoded = SourcePayloadCodec.decodeCardiacInputShape(record.payload)
                            require(decoded.anchorRecordIndex in heartRateAnchors) {
                                "Synthetic V2 input-shape metadata lacks actual V1 HR anchor"
                            }
                            pendingInputShape += decoded
                            v2Chunks += 1L
                        }
                        else -> error("Unknown synthetic CARDIAC payload version")
                    }
                }
                cursor = block.last().recordIndex
            }
        }
        finishInputShapeGroup()
        require(expectedIndex - 1L == r.journal.latestRecordIndex())
        require(manifests.sumOf { it.recordCount } == r.journal.latestRecordIndex())
        require(manifests.zipWithNext().all { (a,b) -> a.lastRecordIndex + 1L == b.firstRecordIndex })
        require(perStream.values.all { it > 0L })
        require(v2Groups == inventory.v2Groups && v2Chunks == inventory.v2Chunks)
        require(v2Truncated == inventory.truncatedGroups && v2Batch45 == inventory.batch45Groups)
        require(v2FieldNull == inventory.fieldNullGroups && v2FieldNonList == inventory.fieldNonListGroups)
        require(v2SlotMarkers == inventory.slotTypeMarkerGroups)
        return SyntheticJournalInventory(
            canonicalRecords = expectedIndex - 1L,
            sourceBytes = manifests.sumOf { it.byteCount },
            perStream = perStream.toMap(),
            v1HeartRateRecords = v1HeartRate,
            v1IbiProjectionRecords = v1Ibi,
            v1AnomalousProjectionRecords = v1Anomalous,
            v2Groups = v2Groups,
            v2Chunks = v2Chunks,
            v2TruncatedGroups = v2Truncated,
            v2Batch45Groups = v2Batch45,
            v2FieldNullGroups = v2FieldNull,
            v2FieldNonListGroups = v2FieldNonList,
            v2SlotTypeMarkerGroups = v2SlotMarkers,
            manifests = manifests,
        )
    }

    private fun writeSyntheticPositionalFixtureSummary(
        dir: File,
        r: Rig,
        baselineTarget: Int,
        observed: SyntheticJournalInventory,
    ) {
        require(observed.canonicalRecords == baselineTarget.toLong() + observed.v2Chunks)
        val expectedBaseRateNumerator = 801_505L
        val expectedBaseRateDurationMs = WatchFreshRunDurationPolicy.EIGHT_HOURS_MS
        val text = buildString {
            append("{\n")
            append("  \"fixture_kind\": \"synthetic_current_source_journal_cardiac_positional_v2\",\n")
            append("  \"synthetic_only\": true,\n")
            append("  \"not_real_records_or_sdk_guarantee\": true,\n")
            append("  \"actual_source_identity\": {\"watch_boot_session_id\": \"").append(r.initial.sourceId)
                .append("\", \"fresh_run_id\": \"").append(r.initial.runId).append("\"},\n")
            append("  \"duration_ms\": ").append(r.initial.durationMs).append(",\n")
            append("  \"workload_derivation\": {\n")
            append("    \"baseline_v1_projection_records_from_HUGR_FRESH_RECORD_COUNT\": ").append(baselineTarget).append(",\n")
            append("    \"legacy_reference_records\": ").append(expectedBaseRateNumerator).append(",\n")
            append("    \"legacy_reference_duration_ms\": ").append(expectedBaseRateDurationMs).append(",\n")
            append("    \"legacy_reference_records_per_second_rational\": \"").append(expectedBaseRateNumerator)
                .append("/").append(expectedBaseRateDurationMs / 1_000L).append("\",\n")
            append("    \"additive_v2_metadata_records\": ").append(observed.v2Chunks).append(",\n")
            append("    \"actual_canonical_records\": ").append(observed.canonicalRecords).append("\n")
            append("  },\n")
            append("  \"source_journal_observed_before_ack_deletion\": {\n")
            append("    \"canonical_records\": ").append(observed.canonicalRecords).append(",\n")
            append("    \"segment_bytes\": ").append(observed.sourceBytes).append(",\n")
            append("    \"streams\": {")
            SourceStreamCode.entries.forEachIndexed { index, stream ->
                if (index > 0) append(", ")
                append("\"").append(stream.name).append("\": ").append(observed.perStream.getValue(stream))
            }
            append("},\n")
            append("    \"manifests\": [\n")
            observed.manifests.forEachIndexed { index, manifest ->
                append("      {\"first_record_index\": ").append(manifest.firstRecordIndex)
                    .append(", \"last_record_index\": ").append(manifest.lastRecordIndex)
                    .append(", \"record_count\": ").append(manifest.recordCount)
                    .append(", \"byte_count\": ").append(manifest.byteCount)
                    .append(", \"sha256\": \"").append(manifest.sha256Hex).append("\"}")
                if (index < observed.manifests.lastIndex) append(',')
                append('\n')
            }
            append("    ]\n")
            append("  },\n")
            append("  \"v1_projection_observed\": {\"heart_rate_records\": ").append(observed.v1HeartRateRecords)
                .append(", \"ibi_projection_records\": ").append(observed.v1IbiProjectionRecords)
                .append(", \"anomalous_projection_records\": ").append(observed.v1AnomalousProjectionRecords)
                .append("},\n")
            append("  \"v2_input_shape_metadata_observed\": {\"groups\": ").append(observed.v2Groups)
                .append(", \"chunks\": ").append(observed.v2Chunks)
                .append(", \"truncated_4097_original_4096_captured_groups\": ").append(observed.v2TruncatedGroups)
                .append(", \"batch45_three_chunk_groups\": ").append(observed.v2Batch45Groups)
                .append(", \"null_field_groups\": ").append(observed.v2FieldNullGroups)
                .append(", \"non_list_field_groups\": ").append(observed.v2FieldNonListGroups)
                .append(", \"slot_null_or_type_marker_groups\": ").append(observed.v2SlotTypeMarkerGroups)
                .append("},\n")
            append("  \"canonical_sequence_state_safety\": {\"record_indices_contiguous\": true, \"per_stream_sequences_contiguous\": true, \"all_five_streams_present\": true, \"v2_anchors_actual_v1_hr_records\": true, \"v2_groups_complete\": true, \"v2_metadata_is_not_beats\": true}\n")
            append("}\n")
        }
        File(dir, "synthetic-cardiac-positional-v2-summary.json").writeText(text)
    }

    private fun fixtureDuration(): Long = (System.getenv("DURATION_MS") ?: System.getenv("HUGR_FRESH_DURATION_MS"))?.toLongOrNull()
        ?: WatchFreshRunDurationPolicy.FIVE_MINUTES_MS
    private fun defaultSyntheticPositionalBaselineCount(durationMs: Long): Int = when(durationMs) {
        WatchFreshRunDurationPolicy.FIVE_MINUTES_MS -> 8_349
        WatchFreshRunDurationPolicy.THIRTY_MINUTES_MS -> 50_095
        WatchFreshRunDurationPolicy.TWO_HOURS_MS -> 200_375
        WatchFreshRunDurationPolicy.EIGHT_HOURS_MS -> 801_505
        else -> error("Closed duration ladder rejected before fixture count")
    }
    private fun fixtureRecordCount(): Int = (System.getenv("RECORD_COUNT") ?: System.getenv("HUGR_FRESH_RECORD_COUNT"))?.toIntOrNull()
        ?: if(cardiacPositionalV2FixtureEnabled()) defaultSyntheticPositionalBaselineCount(fixtureDuration()) else 10
    private fun writeFixtureWire(dir: File, journal: SourceJournal, manifests: List<SourceSegmentManifest>) {
        File(dir,"canonical-records.base64").bufferedWriter().use { canonical ->
            File(dir,"source-wire-notifications.base64").bufferedWriter().use { wire ->
                manifests.forEachIndexed { manifestIndex, manifest ->
                    var cursor=manifest.firstRecordIndex-1
                    while(cursor<manifest.lastRecordIndex){
                        val block=journal.readRecordsAfter(manifest.watchBootSessionId,cursor,manifest.lastRecordIndex,25_000)
                        require(block.isNotEmpty()){"Fixture source range missing"};block.forEach{canonical.append(Base64.getEncoder().encodeToString(it.canonicalBytes())).append('\n')}
                        SourceReplayProtocol.buildDataFrames(block,replay=manifestIndex>0,maximumAttPayloadBytes=514).forEach{
                            wire.append(Base64.getEncoder().encodeToString(it)).append('\n')
                        };cursor=block.last().recordIndex
                    }
                    wire.append(Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(manifest)))).append('\n')
                }
            }
        }
        File(dir,"manifest-frames.base64").bufferedWriter().use { out -> manifests.forEach {
            out.append(Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(it)))).append('\n')
        }}
        File(dir,"ack-frames.base64").bufferedWriter().use { out -> manifests.forEach {
            out.append(Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeSegmentAcknowledgement(SourceSegmentAcknowledgement(it.watchBootSessionId,it.lastRecordIndex,it.sha256Hex)))).append('\n')
        }}
    }
    private fun writeLegacyFiveMinuteFixtureWire(dir: File, r: Rig) {
        File(dir,"canonical-records.base64").writeText(r.records.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(it.canonicalBytes())})
        File(dir,"manifest-frames.base64").writeText(r.sealed.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(it)))})
        val notifications = r.sealed.flatMapIndexed { index, manifest ->
            val records = r.records.filter { it.recordIndex in manifest.firstRecordIndex..manifest.lastRecordIndex }
            SourceReplayProtocol.buildDataFrames(records, replay = index > 0, maximumAttPayloadBytes = 514) +
                listOf(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(manifest)))
        }
        File(dir,"source-wire-notifications.base64").writeText(notifications.joinToString("\n",postfix="\n"){Base64.getEncoder().encodeToString(it)})
        File(dir,"ack-frames.base64").writeText(r.sealed.joinToString("\n", postfix="\n") { manifest ->
            Base64.getEncoder().encodeToString(
                SourceReplayProtocol.encodeSegmentAcknowledgement(
                    SourceSegmentAcknowledgement(r.initial.sourceId, manifest.lastRecordIndex, manifest.sha256Hex),
                ),
            )
        })
    }
    @Test fun `produce real Watch canonical joined fixture streaming acceptance finalized completion restart`() {
        org.junit.Assume.assumeTrue("SKIPPED_GENERATION_DURING_ACK_ONLY_PHASE", System.getenv("HUGR_FRESH_VERIFY_FIXTURE")!="1")
        val configured=System.getenv("HUGR_FRESH_FIXTURE_DIR")
        org.junit.Assume.assumeTrue("SKIPPED_MISSING_EXPLICIT_SYNTHETIC_WATCH_FIXTURE", !configured.isNullOrBlank())
        val output=requireNotNull(configured);val duration=fixtureDuration();val count=fixtureRecordCount()
        require(WatchFreshRunDurationPolicy.accepts(duration)&&count>0&&count<=WatchFreshRunDurationPolicy.MAX_CANONICAL_RECORDS)
        val dir=File(output);dir.mkdirs()
        // Preserve the historical writer's V1 five-minute/ten-record sequence
        // byte-for-byte. Ladder cases use V2 and a bounded journal reread rather
        // than retaining duplicate in-memory record arrays.
        if(duration==WatchFreshRunDurationPolicy.FIVE_MINUTES_MS&&count==10&&!cardiacPositionalV2FixtureEnabled()){
            val r=Rig();try{
                r.begin();File(dir,"started.tsv").writeBytes(r.session.snapshot().bytes())
                r.batchValid();val first=r.seal();r.ack(first);File(dir,"streaming-acked.tsv").writeBytes(r.session.snapshot().bytes())
                r.elapsed+=1000;r.wall+=1000;r.batchValid();r.finishTime();r.session.finalizeRecording()
                val final=r.journal.finalizedManifests().single();r.sealed+=final;File(dir,"finalized.tsv").writeBytes(r.session.snapshot().bytes())
                r.journal.forceSync();copyReplica(r.root,File(dir,"watch-verifier-root"));writeLegacyFiveMinuteFixtureWire(dir,r);r.ack(final)
                val completed=r.store.current()!!;File(dir,"completed.tsv").writeBytes(completed.bytes());File(dir,"restart.tsv").writeBytes(FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3")).current()!!.bytes())
                File(dir,"source-id.txt").writeText("${r.initial.sourceId}\n");File(dir,"receipt-digest.txt").writeText(FreshRunReceipt.digest(completed.bytes())+"\n")
            }finally{r.close()};return
        }
        val r=Rig(durationMs=duration,version=FreshRunReceiptVersion.V2);try{
            r.begin();File(dir,"started.tsv").writeBytes(r.session.snapshot().bytes())
            val positional = if(cardiacPositionalV2FixtureEnabled()) appendSyntheticPositionalWorkload(r,count) else null
            if(positional==null) repeat(count){index->
                val offset=if(count==1)0L else index.toLong()*(duration-1L)/(count-1L)
                r.elapsed=r.initial.startElapsedMs+offset;r.wall=r.initial.startWallMs+offset
                val stream=SourceStreamCode.entries[index%SourceStreamCode.entries.size]
                requireNotNull(r.session.append(stream,r.wall,fixturePayload(stream)))
            }
            File(dir,"streaming-acked.tsv").writeBytes(r.session.snapshot().bytes())
            r.elapsed=r.initial.startElapsedMs+duration;r.wall=r.initial.startWallMs+duration
            r.session.finalizeRecording();val before=r.session.snapshot();val manifests=before.manifests
            require(manifests.isNotEmpty());File(dir,"finalized.tsv").writeBytes(before.bytes());r.journal.forceSync();copyReplica(r.root,File(dir,"watch-verifier-root"))
            if(positional!=null) require(manifests.size>=2) { "Synthetic positional fixture requires at least two virtual-time source manifests" }
            writeFixtureWire(dir,r.journal,manifests)
            positional?.let { writeSyntheticPositionalFixtureSummary(dir,r,count,inspectSyntheticPositionalJournal(r,it,manifests)) }
            manifests.filter{m->before.acks.none{it.last==m.lastRecordIndex}}.forEach{r.ack(it)}
            val completed=r.store.current()!!;File(dir,"completed.tsv").writeBytes(completed.bytes());File(dir,"restart.tsv").writeBytes(FreshRunReceiptStore(File(r.root,"fresh_ordinary_runs_v3")).current()!!.bytes())
            File(dir,"source-id.txt").writeText("${r.initial.sourceId}\n");File(dir,"receipt-digest.txt").writeText(FreshRunReceipt.digest(completed.bytes())+"\n")
        }finally{r.close()}
    }
    @Test fun `verify Phone exact ACK frames against preserved real Watch pre final ACK replica`() {
        org.junit.Assume.assumeTrue("SKIPPED_MISSING_EXPLICIT_SYNTHETIC_ACK_VERIFY_PHASE", System.getenv("HUGR_FRESH_VERIFY_FIXTURE")=="1")
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
