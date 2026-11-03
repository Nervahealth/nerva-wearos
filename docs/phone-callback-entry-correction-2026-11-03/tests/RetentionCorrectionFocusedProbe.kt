package health.hugr.sourcejournal

import com.polidea.rxandroidble2.internal.util.HugrReceiptWriteTrace
import health.hugr.sourcejournal.nativeobservation.NativeObservationAnchor
import health.hugr.sourcejournal.nativeobservation.NativeObservationCoordinator
import health.hugr.sourcejournal.nativeobservation.NativeObservationFrame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow JVM regression probe for the real publisher, real two stores, and saved-report path.
 *
 * Every value here is a synthetic closed-schema diagnostic fact. It represents neither physiology
 * nor a physical cause, and contains no tokens, private identifiers, payload bytes, device data,
 * addresses, UUIDs, or real report content. This is intentionally an executable probe rather than
 * a reusable test framework.
 */
object RetentionCorrectionFocusedProbe {
  private const val INCIDENT = "synthetic-retention-a"
  private const val EPOCH = "synthetic-epoch-a"
  private const val FIRST_TX = "hugr-receipt-1-1"
  private const val TARGET_TX = "hugr-receipt-2-1"
  private const val FIRST_TOKEN = 10L
  private const val TARGET_TOKEN = 20L
  private const val FIRST_OPERATION = 1L
  private const val TARGET_OPERATION = 2L

  /** Real JVM filesystem boundary: data write+fd sync, atomic move, directory force, reread. */
  private val linuxIo = object : ReceiptIncidentFileIo {
    override fun writeAndSync(file: File, bytes: ByteArray) {
      FileOutputStream(file).use { output ->
        output.write(bytes)
        output.flush()
        output.fd.sync()
      }
    }

    override fun atomicReplace(from: File, to: File) {
      Files.move(
        from.toPath(),
        to.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    }

    override fun syncDirectory(directory: File) {
      FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) }
    }

    override fun readFully(file: File): ByteArray = file.readBytes()
  }

  @JvmStatic
  fun main(args: Array<String>) {
    runCase("ordinary_complete_no_omissions") { ordinaryCompleteCase() }
    runCase("oversized_target_chain_retained_after_reopen") { oversizedRetentionCase() }
    runCase("mapping_cap_reduces_unused_rows_without_target_loss") { mappingCapacityCase() }
    runCase("prior_ambiguous_mapping_is_not_weakened_by_current_local") { priorAmbiguityCase() }
    runCase("two_native_ids_for_one_transaction_are_retained_as_ambiguous") { transactionMappingUnionCase() }
    runCase("missing_primary_reference_is_null_and_not_heuristically_selected") { missingPrimaryReferenceCase() }
    runCase("unsupported_late_nonraw_row_is_explicit_loss") { unsupportedLateRowCase() }
    runCase("unfit_protected_set_refuses_write_and_preserves_prior") { unfitProtectedSetCase() }
    println("FOCUSED_RETENTION_PROBE_PASS cases=8 synthetic_only=YES")
  }

  private fun runCase(name: String, block: () -> Unit) {
    println("FOCUSED_CASE_BEGIN name=$name")
    try {
      block()
      println("FOCUSED_CASE_PASS name=$name")
    } catch (failure: Throwable) {
      println("FOCUSED_CASE_FAIL name=$name type=${failure::class.java.name} message=${failure.message}")
      throw failure
    }
  }

  /** Ordinary input must be complete: no omission, no truncation, exact primary untouched. */
  private fun ordinaryCompleteCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    val primaryBody = mainSnapshot(INCIDENT, EPOCH)
    check(primary.publish(primaryBody).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val frame = ordinaryFrame(verified)
    val publisher = publisher(root, primary)

    check(publisher.publish(frame).isChanged)
    val read = nativeRead(root, primary, verified)
    check(read.status == "CAPTURED" && read.verifiedSidecarAccessible)
    val body = JSONObject(requireNotNull(read.body))
    val events = body.getJSONArray("events")
    val overflow = body.getJSONObject("overflow")
    check(events.length() == 10) { "ordinary sidecar omitted an event" }
    check(!overflow.getBoolean("traceWindowOverflow")) { "ordinary sidecar claimed loss" }
    check(!overflow.getBoolean("bodyLimitTruncated")) { "ordinary sidecar claimed body truncation" }
    check(overflow.getLong("droppedEventCount") == 0L)
    check(overflow.getLong("droppedMappingCount") == 0L)
    check(eventOrdinals(events) == (1L..10L).toSet())
    check(body.getJSONObject("firstNativeFault").getLong("eventOrdinal") == 5L)
    assertCompleteUtf8SavedOutput("ordinary_complete_no_omissions", root, primary, primaryBody, requireNotNull(read.body))
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "optional native work altered primary" }
  }

  /**
   * Freeze a legitimate preexisting first native fault, then exercise 128 typed synthetic rows.
   * The target primary reference is TARGET_TX. Its prebind, write, nonzero callback, terminal
   * error, cancellation, release/cleanup, and adjacent before/after context sit late in the
   * frame, so newest-first trimming in the immutable baseline drops them.
   */
  private fun oversizedRetentionCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    val primaryBody = mainSnapshot(INCIDENT, EPOCH)
    check(primary.publish(primaryBody).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val publisher = publisher(root, primary)

    // First sidecar makes the original local fault immutable on the next revision.
    check(publisher.publish(ordinaryFrame(verified)).isChanged)
    val firstBody = requireNotNull(nativeRead(root, primary, verified).body)
    val frozenFault = JSONObject(firstBody).getJSONObject("firstNativeFault")
    check(frozenFault.getString("state") == "VALUE" && frozenFault.getLong("eventOrdinal") == 5L)

    val oversized = oversizedFrame(verified)
    check(oversized.events().size == 128) { "fixture must be exactly 128 typed native events" }
    check(publisher.publish(oversized).isChanged)

    // Real optional-sidecar reopen, followed by real primary report rendering.
    val read = nativeRead(root, primary, verified)
    check(read.status == "CAPTURED" && read.verifiedSidecarAccessible)
    val body = JSONObject(requireNotNull(read.body))
    val events = body.getJSONArray("events")
    val ordinals = eventOrdinals(events)
    val overflow = body.getJSONObject("overflow")
    // Seal the real renderer/private-save UTF-8 bytes before any deliberate retention assertion can fail.
    assertCompleteUtf8SavedOutput("oversized_target_chain_retained_after_reopen", root, primary, primaryBody, requireNotNull(read.body))
    check(body.toString().toByteArray(StandardCharsets.UTF_8).size <= RECEIPT_NATIVE_WRITE_MAX_BODY_BYTES)
    check(overflow.getBoolean("traceWindowOverflow")) { "oversized source frame must disclose loss" }

    // Original fault survives as the frozen first native fault rather than being rewritten UNKNOWN.
    val afterFault = body.getJSONObject("firstNativeFault")
    check(afterFault.getString("state") == "VALUE")
    check(afterFault.getLong("eventOrdinal") == frozenFault.getLong("eventOrdinal"))
    check(afterFault.getString("boundary") == "ANDROID_WRITE_CALL_RETURN")
    check(afterFault.getString("eventStatus") == "API_CALL_RETURNED_FALSE")

    // Whole-window endpoints; selected target chain; exact prebind and adjacent context.
    requiredOrdinals().forEach { required ->
      check(required in ordinals) { "retention lost protected ordinal=$required retained=$ordinals" }
    }
    assertTargetFacts(events)
    val targetOperation = "native-op-$TARGET_TOKEN-$TARGET_OPERATION"
    val mappings = body.getJSONArray("transactionMappings")
    check((0 until mappings.length()).any { index ->
      val mapping = mappings.getJSONObject(index)
      mapping.getString("bleTransactionId") == TARGET_TX && mapping.getString("nativeOperationId") == targetOperation
    }) { "retention lost selected target mapping" }

    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "optional native work altered primary" }

    // Regression: later independent frame must not silently discard prior protected target facts.
    val later = laterUnrelatedFrame(verified)
    check(publisher.publish(later).isChanged)
    val laterRead = nativeRead(root, primary, verified)
    val laterSidecar = requireNotNull(laterRead.body)
    assertCompleteUtf8SavedOutput("oversized_subsequent_target_chain_retained", root, primary, primaryBody, laterSidecar)
    val laterBody = JSONObject(laterSidecar)
    val laterOrdinals = eventOrdinals(laterBody.getJSONArray("events"))
    targetChainAndAdjacentOrdinals().forEach { required ->
      check(required in laterOrdinals) { "later frame lost prior protected ordinal=$required" }
    }
    val laterFault = laterBody.getJSONObject("firstNativeFault")
    check(laterFault.getString("state") == "VALUE" && laterFault.getLong("eventOrdinal") == 5L)
    check(laterBody.getString("captureStatus") == "UNKNOWN") { "unavailable target must remain explicit unknown capture" }
    check(laterBody.getJSONObject("overflow").getBoolean("traceWindowOverflow")) { "prior known loss lower bound was cleared" }
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "later optional native work altered primary" }
  }

  /** More than 32 mappings: remove only unused noncritical rows and retain the primary-selected mapping. */
  private fun mappingCapacityCase() = withSyntheticRoot { root ->
    val transactions = listOf(FIRST_TX, TARGET_TX) + (3L..33L).map { "hugr-receipt-${it + 10L}-1" }
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    check(primary.publish(mainSnapshot(INCIDENT, EPOCH, transactions)).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val mappings = mutableListOf(
      NativeObservationFrame.TransactionMapping(
        FIRST_TX,
        FIRST_TOKEN,
        FIRST_OPERATION,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
      NativeObservationFrame.TransactionMapping(
        TARGET_TX,
        TARGET_TOKEN,
        TARGET_OPERATION,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
    )
    transactions.drop(2).forEachIndexed { index, transaction ->
      mappings += NativeObservationFrame.TransactionMapping(
        transaction,
        100L + index,
        100L + index,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      )
    }
    check(mappings.size == 33)
    val events = mappings.mapIndexed { index, mapping ->
      when (mapping.exactTransactionId()) {
        FIRST_TX -> firstFaultEvent(5L)
        TARGET_TX -> targetEvent(
          20L,
          HugrReceiptWriteTrace.Boundary.ANDROID_WRITE_CALL_ENTRY,
          HugrReceiptWriteTrace.EventStatus.RECORDED,
          HugrReceiptWriteTrace.OperationStage.ANDROID_CALL_ENTERED,
        )
        else -> targetLikeEvent(
          ordinal = index.toLong() + 30L,
          transaction = mapping.exactTransactionId(),
          hasOperation = true,
          token = mapping.localToken(),
          operation = mapping.operationOrdinal(),
          boundary = HugrReceiptWriteTrace.Boundary.ADAPTER_ENTRY,
          status = HugrReceiptWriteTrace.EventStatus.RECORDED,
          stage = HugrReceiptWriteTrace.OperationStage.REGISTERED,
          androidReturn = NativeObservationFrame.BooleanValue.absent(),
          callback = HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
          rawStatus = NativeObservationFrame.LongValue.absent(),
          release = NativeObservationFrame.BooleanValue.absent(),
        )
      }
    }
    val frame = frame(
      verified,
      events,
      NativeObservationFrame.CaptureState.CAPTURED,
      mappings = mappings,
    )
    check(publisher(root, primary).publish(frame).isChanged)
    val read = nativeRead(root, primary, verified)
    val body = JSONObject(requireNotNull(read.body))
    val outputMappings = body.getJSONArray("transactionMappings")
    val overflow = body.getJSONObject("overflow")
    check(outputMappings.length() == 32) { "mapping cap not enforced" }
    check(overflow.getLong("droppedMappingCount") >= 1L && overflow.getBoolean("traceWindowOverflow")) {
      "mapping-cap loss was not explicit"
    }
    check((0 until outputMappings.length()).any { index ->
      outputMappings.getJSONObject(index).getString("bleTransactionId") == TARGET_TX
    }) { "primary-selected target mapping was dropped at mapping cap" }
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "mapping-cap publication altered primary" }
  }

  /** A later LOCAL claim may not downgrade a verified prior AMBIGUOUS association. */
  private fun priorAmbiguityCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    check(primary.publish(mainSnapshot(INCIDENT, EPOCH)).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val publisher = publisher(root, primary)
    check(publisher.publish(ordinaryFrame(verified, NativeObservationFrame.MappingAssociation.AMBIGUOUS_NO_ATT_MATCH)).isChanged)
    // Patched materialization merges association rank before equality check; an unchanged publication is valid.
    publisher.publish(ordinaryFrame(verified, NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY))
    val mappings = JSONObject(requireNotNull(nativeRead(root, primary, verified).body)).getJSONArray("transactionMappings")
    val target = (0 until mappings.length()).map(mappings::getJSONObject).single {
      it.getString("bleTransactionId") == TARGET_TX && it.getString("nativeOperationId") == "native-op-$TARGET_TOKEN-$TARGET_OPERATION"
    }
    check(target.getString("association") == "AMBIGUOUS") { "prior ambiguous association was weakened" }
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "association merge altered primary" }
  }

  /** A prior/current union of distinct native IDs for one transaction must remain schema-valid and ambiguous. */
  private fun transactionMappingUnionCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    check(primary.publish(mainSnapshot(INCIDENT, EPOCH)).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val publisher = publisher(root, primary)
    check(publisher.publish(ordinaryFrame(verified)).isChanged)
    val laterMappings = listOf(
      NativeObservationFrame.TransactionMapping(
        FIRST_TX, FIRST_TOKEN, FIRST_OPERATION,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
      NativeObservationFrame.TransactionMapping(
        TARGET_TX, TARGET_TOKEN + 1L, TARGET_OPERATION + 1L,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
    )
    check(publisher.publish(frame(verified, ordinaryEvents(), NativeObservationFrame.CaptureState.CAPTURED, mappings = laterMappings)).isChanged)
    val mappings = JSONObject(requireNotNull(nativeRead(root, primary, verified).body)).getJSONArray("transactionMappings")
    val targetRows = (0 until mappings.length()).map(mappings::getJSONObject).filter { it.getString("bleTransactionId") == TARGET_TX }
    check(targetRows.size == 2) { "prior/current target native-ID union was discarded" }
    check(targetRows.all { it.getString("association") == "AMBIGUOUS" }) { "target native-ID union was not normalized ambiguous" }
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "mapping union altered primary" }
  }

  /** A validated UNKNOWN first-cause reference yields null selection authority; no recency/clock guess is permitted. */
  private fun missingPrimaryReferenceCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    val primaryBody = mainSnapshot(INCIDENT, EPOCH, reference = null)
    check(primary.publish(primaryBody).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    // Reflection preserves preimage compilation: the immutable baseline data class predates this field.
    val selectionGetter = verified.javaClass.methods.singleOrNull { it.name == "getFailedTransactionId" }
      ?: throw IllegalStateException("patched verified-main missing failedTransactionId selection authority")
    check(selectionGetter.invoke(verified) == null) { "UNKNOWN primary reference was guessed into a target transaction" }
    check(publisher(root, primary).publish(ordinaryFrame(verified)).isChanged)
    val read = nativeRead(root, primary, verified)
    val body = JSONObject(requireNotNull(read.body))
    check(body.getString("captureStatus") == "CAPTURED")
    check(!body.getJSONObject("overflow").getBoolean("traceWindowOverflow"))
    assertCompleteUtf8SavedOutput("missing_primary_reference_no_heuristic", root, primary, primaryBody, requireNotNull(read.body))
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "null-reference optional publication altered primary" }
  }

  /** A late non-RAW callback boundary is outside the V1 closed schema and must be explicitly lost, never relabeled. */
  private fun unsupportedLateRowCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    val primaryBody = mainSnapshot(INCIDENT, EPOCH)
    check(primary.publish(primaryBody).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val lateUnsupported = targetLikeEvent(
      ordinal = 11L,
      transaction = TARGET_TX,
      hasOperation = true,
      token = TARGET_TOKEN,
      operation = TARGET_OPERATION,
      boundary = HugrReceiptWriteTrace.Boundary.ACTIVE_CANCEL_FLAG_SET,
      status = HugrReceiptWriteTrace.EventStatus.CANCEL_FLAG_SET,
      stage = HugrReceiptWriteTrace.OperationStage.CANCEL_REQUESTED,
      androidReturn = NativeObservationFrame.BooleanValue.absent(),
      callback = HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
      rawStatus = NativeObservationFrame.LongValue.absent(),
      release = NativeObservationFrame.BooleanValue.absent(),
      late = true,
    )
    check(publisher(root, primary).publish(frame(verified, ordinaryEvents() + lateUnsupported, NativeObservationFrame.CaptureState.CAPTURED)).isChanged)
    val read = nativeRead(root, primary, verified)
    val body = JSONObject(requireNotNull(read.body))
    val overflow = body.getJSONObject("overflow")
    check(11L !in eventOrdinals(body.getJSONArray("events"))) { "unsupported late row was relabeled into closed schema" }
    check(overflow.getLong("droppedEventCount") == 1L && overflow.getBoolean("traceWindowOverflow")) {
      "unsupported late row was not explicit loss"
    }
    check(body.getString("captureStatus") == "UNKNOWN")
    check(body.getJSONObject("firstNativeFault").getString("state") == "UNKNOWN")
    assertCompleteUtf8SavedOutput("unsupported_late_nonraw_row_explicit_loss", root, primary, primaryBody, requireNotNull(read.body))
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "unsupported-late optional publication altered primary" }
  }

  /** If protected facts alone exceed a bounded store limit, fail before writing and leave acknowledged files exact. */
  private fun unfitProtectedSetCase() = withSyntheticRoot { root ->
    val primary = ReceiptIncidentDiagnosticStore(root, linuxIo)
    val primaryBody = mainSnapshot(INCIDENT, EPOCH)
    check(primary.publish(primaryBody).acknowledgedAtomicPublication)
    val primaryFile = File(root, "incident-$INCIDENT.hugr-incident")
    val primaryBefore = primaryFile.readBytes()
    val verified = primary.verifiedNativeWriteMain(INCIDENT)
    val publisher = publisher(root, primary)
    check(publisher.publish(ordinaryFrame(verified)).isChanged)
    val sidecar = File(root, "native-write-$INCIDENT.hugr-native-write")
    val sidecarBefore = sidecar.readBytes()

    val failure = runCatching { publisher.publish(unfitProtectedFrame(verified)) }.exceptionOrNull()
    check(failure is IllegalStateException && failure.message == "RECEIPT_NATIVE_WRITE_BODY_CANNOT_RETAIN_CRITICAL_CONTEXT") {
      "protected-set overflow did not fail explicitly before write: $failure"
    }
    check(sidecar.readBytes().contentEquals(sidecarBefore)) { "unfit protected set mutated prior sidecar" }
    check(primaryFile.readBytes().contentEquals(primaryBefore)) { "unfit protected set altered primary" }
    val priorCanonicalBody = String(
      java.util.Base64.getDecoder().decode(
        JSONObject(String(sidecarBefore, StandardCharsets.UTF_8)).getString("body_base64"),
      ),
      StandardCharsets.UTF_8,
    )
    val read = nativeRead(root, primary, verified)
    check(requireNotNull(read.body) == priorCanonicalBody) { "prior sidecar readback is no longer exact canonical content" }
  }

  private fun publisher(root: File, primary: ReceiptIncidentDiagnosticStore): ReceiptNativeWriteFramePublisher =
    ReceiptNativeWriteFramePublisher(root, linuxIo, ReceiptNativeWriteVerifiedMainProvider { incident ->
      primary.verifiedNativeWriteMain(incident)
    })

  private fun nativeRead(
    root: File,
    primary: ReceiptIncidentDiagnosticStore,
    verified: ReceiptNativeWriteVerifiedMain,
  ): ReceiptNativeWriteRead =
    ReceiptNativeWriteObservationStore(root, linuxIo, ReceiptNativeWriteVerifiedMainProvider { incident ->
      primary.verifiedNativeWriteMain(incident)
    }).read(verified)

  /** The supplied source exposes readAll(), not a readAllSavedIncidents/completeReport wrapper. */
  private fun assertCompleteUtf8SavedOutput(caseName: String, root: File, primary: ReceiptIncidentDiagnosticStore, main: String, sidecar: String) {
    val report = primary.readAll()
    check(report.contains(main)) { "complete saved report omitted verified primary" }
    check(report.contains(sidecar)) { "complete saved report omitted reopened native sidecar" }
    check(report.endsWith("END REPORT\n")) { "complete saved report boundary missing" }
    sealSyntheticOutput(caseName, report, sidecar)
    val cache = File(root.parentFile, "synthetic-private-cache")
    try {
      val copy = requireNotNull(prepareReceiptDiagnosticPrivateCopy(cache, report))
      val stream = ByteArrayOutputStream()
      copyReceiptDiagnosticPrivateReport(copy, stream)
      check(stream.toByteArray().contentEquals(report.toByteArray(StandardCharsets.UTF_8))) {
        "saved UTF-8 output differs from complete report"
      }
      check(copy.readBytes().contentEquals(report.toByteArray(StandardCharsets.UTF_8))) {
        "private saved UTF-8 file differs from complete report"
      }
    } finally {
      cache.deleteRecursively()
    }
  }

  private fun sealSyntheticOutput(caseName: String, report: String, sidecar: String) {
    val artifactRoot = System.getProperty("hugr.retention.artifacts")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
    val caseRoot = File(artifactRoot, caseName)
    check(caseRoot.mkdirs()) { "synthetic artifact case directory already exists or cannot be created" }
    val reportFile = File(caseRoot, "complete-saved-report.utf8.txt")
    val nativeFile = File(caseRoot, "reopened-native-sidecar.canonical.json")
    reportFile.writeBytes(report.toByteArray(StandardCharsets.UTF_8))
    nativeFile.writeBytes(sidecar.toByteArray(StandardCharsets.UTF_8))
    check(reportFile.readBytes().contentEquals(report.toByteArray(StandardCharsets.UTF_8))) { "sealed report readback mismatch" }
    check(nativeFile.readBytes().contentEquals(sidecar.toByteArray(StandardCharsets.UTF_8))) { "sealed sidecar readback mismatch" }
  }

  private fun ordinaryFrame(
    verified: ReceiptNativeWriteVerifiedMain,
    targetAssociation: NativeObservationFrame.MappingAssociation = NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
  ): NativeObservationFrame = frame(
    verified,
    ordinaryEvents(),
    NativeObservationFrame.CaptureState.CAPTURED,
    targetAssociation = targetAssociation,
  )

  private fun oversizedFrame(verified: ReceiptNativeWriteVerifiedMain): NativeObservationFrame {
    val events = mutableListOf<NativeObservationFrame.Event>()
    for (ordinal in 1L..128L) {
      events += when (ordinal) {
        5L -> firstFaultEvent(ordinal)
        109L -> genericEvent(ordinal, boundary = HugrReceiptWriteTrace.Boundary.QUEUE_DEQUEUE,
          status = HugrReceiptWriteTrace.EventStatus.RECORDED, stage = HugrReceiptWriteTrace.OperationStage.DEQUEUED)
        110L -> genericEvent(ordinal, transaction = TARGET_TX, boundary = HugrReceiptWriteTrace.Boundary.ADAPTER_ENTRY,
          status = HugrReceiptWriteTrace.EventStatus.RECORDED, stage = HugrReceiptWriteTrace.OperationStage.REGISTERED)
        111L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.ANDROID_WRITE_CALL_ENTRY,
          HugrReceiptWriteTrace.EventStatus.RECORDED, HugrReceiptWriteTrace.OperationStage.ANDROID_CALL_ENTERED)
        112L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.ANDROID_WRITE_CALL_RETURN,
          HugrReceiptWriteTrace.EventStatus.API_ACCEPTED_ONLY, HugrReceiptWriteTrace.OperationStage.ANDROID_CALL_RETURNED,
          androidReturn = NativeObservationFrame.BooleanValue.value(true))
        113L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.RAW_CALLBACK_ENTRY,
          HugrReceiptWriteTrace.EventStatus.RAW_STATUS_NONZERO, HugrReceiptWriteTrace.OperationStage.CALLBACK_SEEN,
          callback = HugrReceiptWriteTrace.CallbackAssociation.LOCAL_SUBSCRIBER_ONLY,
          rawStatus = NativeObservationFrame.LongValue.value(133L))
        114L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.OPERATION_TERMINAL_ERROR,
          HugrReceiptWriteTrace.EventStatus.TERMINAL_ERROR, HugrReceiptWriteTrace.OperationStage.TERMINAL_SEEN)
        115L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.ACTIVE_CANCEL_FLAG_SET,
          HugrReceiptWriteTrace.EventStatus.CANCEL_FLAG_SET, HugrReceiptWriteTrace.OperationStage.CANCEL_REQUESTED)
        116L -> targetEvent(ordinal, HugrReceiptWriteTrace.Boundary.RELEASE_CALL_RETURN,
          HugrReceiptWriteTrace.EventStatus.RELEASE_RETURNED, HugrReceiptWriteTrace.OperationStage.RELEASED,
          release = NativeObservationFrame.BooleanValue.value(true))
        117L -> genericEvent(ordinal, transaction = TARGET_TX, boundary = HugrReceiptWriteTrace.Boundary.PROMISE_REJECT_ENTER,
          status = HugrReceiptWriteTrace.EventStatus.PROMISE_REJECT_ENTER, stage = HugrReceiptWriteTrace.OperationStage.RELEASED)
        118L -> genericEvent(ordinal, transaction = TARGET_TX, boundary = HugrReceiptWriteTrace.Boundary.ADAPTER_DISPOSE,
          status = HugrReceiptWriteTrace.EventStatus.ADAPTER_DISPOSED, stage = HugrReceiptWriteTrace.OperationStage.RELEASED)
        119L -> genericEvent(ordinal, boundary = HugrReceiptWriteTrace.Boundary.QUEUE_WORKER_RELEASE,
          status = HugrReceiptWriteTrace.EventStatus.RECORDED, stage = HugrReceiptWriteTrace.OperationStage.RELEASED)
        else -> genericEvent(ordinal)
      }
    }
    return frame(verified, events, NativeObservationFrame.CaptureState.CAPTURED)
  }

  /** Later snapshot deliberately has no target rows; it must retain prior protected target evidence. */
  private fun laterUnrelatedFrame(verified: ReceiptNativeWriteVerifiedMain): NativeObservationFrame =
    frame(
      verified,
      (129L..138L).map { genericEvent(it) },
      NativeObservationFrame.CaptureState.CAPTURED,
      targetOutcome = HugrReceiptWriteTrace.TransactionEvidenceState.UNKNOWN_LOSS_OR_EVICTION,
    )

  private fun unfitProtectedFrame(verified: ReceiptNativeWriteVerifiedMain): NativeObservationFrame {
    val mappings = mutableListOf(
      NativeObservationFrame.TransactionMapping(
        FIRST_TX, FIRST_TOKEN, FIRST_OPERATION,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
    )
    repeat(32) { index ->
      mappings += NativeObservationFrame.TransactionMapping(
        TARGET_TX,
        TARGET_TOKEN + index,
        TARGET_OPERATION + index,
        NativeObservationFrame.MappingAssociation.AMBIGUOUS_NO_ATT_MATCH,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      )
    }
    val events = mutableListOf<NativeObservationFrame.Event>(firstFaultEvent(5L))
    mappings.drop(1).forEachIndexed { mappingIndex, mapping ->
      listOf(
        HugrReceiptWriteTrace.Boundary.ADAPTER_ENTRY to HugrReceiptWriteTrace.EventStatus.RECORDED,
        HugrReceiptWriteTrace.Boundary.OPERATION_BOUND to HugrReceiptWriteTrace.EventStatus.RECORDED,
        HugrReceiptWriteTrace.Boundary.QUEUE_ADMISSION to HugrReceiptWriteTrace.EventStatus.RECORDED,
        HugrReceiptWriteTrace.Boundary.QUEUE_DEQUEUE to HugrReceiptWriteTrace.EventStatus.RECORDED,
      ).take(if (mappingIndex == 31) 3 else 4).forEachIndexed { boundaryIndex, fact ->
        events += targetLikeEvent(
          ordinal = 1_000L + mappingIndex * 4L + boundaryIndex,
          transaction = TARGET_TX,
          hasOperation = true,
          token = mapping.localToken(),
          operation = mapping.operationOrdinal(),
          boundary = fact.first,
          status = fact.second,
          stage = if (boundaryIndex == 3) HugrReceiptWriteTrace.OperationStage.DEQUEUED else HugrReceiptWriteTrace.OperationStage.REGISTERED,
          androidReturn = NativeObservationFrame.BooleanValue.absent(),
          callback = HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
          rawStatus = NativeObservationFrame.LongValue.absent(),
          release = NativeObservationFrame.BooleanValue.absent(),
        )
      }
    }
    check(events.size == 128)
    return frame(verified, events, NativeObservationFrame.CaptureState.CAPTURED, mappings = mappings)
  }

  private fun frame(
    verified: ReceiptNativeWriteVerifiedMain,
    events: List<NativeObservationFrame.Event>,
    capture: NativeObservationFrame.CaptureState,
    targetOutcome: HugrReceiptWriteTrace.TransactionEvidenceState = HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
    targetAssociation: NativeObservationFrame.MappingAssociation = NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
    mappings: List<NativeObservationFrame.TransactionMapping> = listOf(
      NativeObservationFrame.TransactionMapping(
        FIRST_TX, FIRST_TOKEN, FIRST_OPERATION,
        NativeObservationFrame.MappingAssociation.LOCAL_SUBSCRIBER_ONLY,
        HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE,
      ),
      NativeObservationFrame.TransactionMapping(
        TARGET_TX, TARGET_TOKEN, TARGET_OPERATION,
        targetAssociation,
        targetOutcome,
      ),
    ),
  ): NativeObservationFrame {
    val anchor = verified.anchor
    return NativeObservationFrame(
      NativeObservationAnchor(
        anchor.incidentPseudonym,
        anchor.connectionEpoch,
        anchor.mainFirstFailure,
        anchor.mainFirstFailureAtMonotonicMs,
        anchor.mainFirstCauseFingerprint,
      ),
      capture,
      true,
      true,
      true,
      1L,
      listOf(
        NativeObservationFrame.TransactionOutcome(FIRST_TX, HugrReceiptWriteTrace.TransactionEvidenceState.AVAILABLE),
        NativeObservationFrame.TransactionOutcome(TARGET_TX, targetOutcome),
      ),
      mappings,
      events,
      NativeObservationFrame.FirstNativeFault.value(
        FIRST_TX,
        5L,
        HugrReceiptWriteTrace.FailureKind.ANDROID_WRITE_CALL_RETURNED_FALSE,
        HugrReceiptWriteTrace.Boundary.ANDROID_WRITE_CALL_RETURN,
        HugrReceiptWriteTrace.EventStatus.API_CALL_RETURNED_FALSE,
      ),
      NativeObservationFrame.Overflow(0L, 0L, 0L, false, false),
    )
  }

  private fun ordinaryEvents(): List<NativeObservationFrame.Event> =
    (1L..10L).map { ordinal -> if (ordinal == 5L) firstFaultEvent(ordinal) else genericEvent(ordinal) }

  private fun firstFaultEvent(ordinal: Long): NativeObservationFrame.Event = targetLikeEvent(
    ordinal = ordinal,
    transaction = FIRST_TX,
    hasOperation = true,
    token = FIRST_TOKEN,
    operation = FIRST_OPERATION,
    boundary = HugrReceiptWriteTrace.Boundary.ANDROID_WRITE_CALL_RETURN,
    status = HugrReceiptWriteTrace.EventStatus.API_CALL_RETURNED_FALSE,
    stage = HugrReceiptWriteTrace.OperationStage.ANDROID_CALL_RETURNED,
    androidReturn = NativeObservationFrame.BooleanValue.value(false),
    callback = HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
    rawStatus = NativeObservationFrame.LongValue.absent(),
    release = NativeObservationFrame.BooleanValue.absent(),
  )

  private fun targetEvent(
    ordinal: Long,
    boundary: HugrReceiptWriteTrace.Boundary,
    status: HugrReceiptWriteTrace.EventStatus,
    stage: HugrReceiptWriteTrace.OperationStage,
    androidReturn: NativeObservationFrame.BooleanValue = NativeObservationFrame.BooleanValue.absent(),
    callback: HugrReceiptWriteTrace.CallbackAssociation = HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
    rawStatus: NativeObservationFrame.LongValue = NativeObservationFrame.LongValue.absent(),
    release: NativeObservationFrame.BooleanValue = NativeObservationFrame.BooleanValue.absent(),
  ): NativeObservationFrame.Event = targetLikeEvent(
    ordinal, TARGET_TX, true, TARGET_TOKEN, TARGET_OPERATION, boundary, status, stage,
    androidReturn, callback, rawStatus, release,
  )

  private fun genericEvent(
    ordinal: Long,
    transaction: String? = null,
    boundary: HugrReceiptWriteTrace.Boundary = HugrReceiptWriteTrace.Boundary.ADAPTER_ENTRY,
    status: HugrReceiptWriteTrace.EventStatus = HugrReceiptWriteTrace.EventStatus.RECORDED,
    stage: HugrReceiptWriteTrace.OperationStage = HugrReceiptWriteTrace.OperationStage.REGISTERED,
  ): NativeObservationFrame.Event = targetLikeEvent(
    ordinal, transaction, false, 0L, 0L, boundary, status, stage,
    NativeObservationFrame.BooleanValue.absent(), HugrReceiptWriteTrace.CallbackAssociation.NOT_APPLICABLE,
    NativeObservationFrame.LongValue.absent(), NativeObservationFrame.BooleanValue.absent(),
  )

  private fun targetLikeEvent(
    ordinal: Long,
    transaction: String?,
    hasOperation: Boolean,
    token: Long,
    operation: Long,
    boundary: HugrReceiptWriteTrace.Boundary,
    status: HugrReceiptWriteTrace.EventStatus,
    stage: HugrReceiptWriteTrace.OperationStage,
    androidReturn: NativeObservationFrame.BooleanValue,
    callback: HugrReceiptWriteTrace.CallbackAssociation,
    rawStatus: NativeObservationFrame.LongValue,
    release: NativeObservationFrame.BooleanValue,
    late: Boolean = false,
  ): NativeObservationFrame.Event = NativeObservationFrame.Event(
    ordinal,
    transaction,
    hasOperation,
    token,
    operation,
    NativeObservationFrame.LongValue.value(10_000L + ordinal),
    HugrReceiptWriteTrace.NativeClockState.VALID,
    HugrReceiptWriteTrace.RegisteredOperationKind.HUGR_RECEIPT_WRITE,
    boundary,
    status,
    stage,
    if (hasOperation) HugrReceiptWriteTrace.QueueOwnerKind.RECEIPT_WRITE else HugrReceiptWriteTrace.QueueOwnerKind.NOT_RECORDED,
    callback,
    androidReturn,
    rawStatus,
    NativeObservationFrame.LongValue.absent(),
    NativeObservationFrame.EnumValue.absent<HugrReceiptWriteTrace.TimeoutUnit>(),
    release,
    late,
  )

  private fun assertTargetFacts(events: JSONArray) {
    val byOrdinal = (0 until events.length()).associate { index ->
      val event = events.getJSONObject(index)
      event.getLong("eventOrdinal") to event
    }
    val expected = mapOf(
      111L to ("ANDROID_WRITE_CALL_ENTRY" to "RECORDED"),
      112L to ("ANDROID_WRITE_CALL_RETURN" to "API_ACCEPTED_ONLY"),
      113L to ("RAW_CALLBACK_ENTRY" to "RAW_STATUS_NONZERO"),
      114L to ("OPERATION_TERMINAL_ERROR" to "TERMINAL_ERROR"),
      115L to ("ACTIVE_CANCEL_FLAG_SET" to "CANCEL_FLAG_SET"),
      116L to ("RELEASE_CALL_RETURN" to "RELEASE_RETURNED"),
      118L to ("ADAPTER_DISPOSE" to "ADAPTER_DISPOSED"),
    )
    expected.forEach { (ordinal, fact) ->
      val event = requireNotNull(byOrdinal[ordinal]) { "missing target event ordinal=$ordinal" }
      check(event.getString("boundary") == fact.first && event.getString("eventStatus") == fact.second) {
        "target event changed ordinal=$ordinal"
      }
    }
    check(byOrdinal.getValue(110L).getJSONObject("nativeOperationId").getString("state") == "ABSENT") {
      "prebind target exact-ID event was not retained as absent-operation context"
    }
    check(byOrdinal.getValue(113L).getJSONObject("callbackStatus").getLong("value") == 133L)
    check(byOrdinal.getValue(116L).getJSONObject("releaseCancelFlag").getBoolean("value"))
  }

  private fun targetChainAndAdjacentOrdinals(): Set<Long> = setOf(5L, 109L, 110L, 111L, 112L, 113L, 114L, 115L, 116L, 117L, 118L, 119L)
  private fun requiredOrdinals(): Set<Long> = setOf(1L, 128L) + targetChainAndAdjacentOrdinals()

  private fun eventOrdinals(events: JSONArray): Set<Long> =
    (0 until events.length()).map { index -> events.getJSONObject(index).getLong("eventOrdinal") }.toSet()

  private fun mainSnapshot(
    incident: String,
    epoch: String,
    transactions: List<String> = listOf(FIRST_TX, TARGET_TX),
    reference: Long? = 2L,
  ): String {
    check(transactions.size in 2..64 && transactions[0] == FIRST_TX && transactions[1] == TARGET_TX)
    val operations = JSONArray()
    transactions.forEachIndexed { index, transaction -> operations.put(mainOperation(index.toLong() + 1L, transaction)) }
    val unknown = tag()
    val error = JSONObject()
      .put("libraryCategory", tag())
      .put("errorCode", tag())
      .put("attErrorCode", tag())
      .put("androidErrorCode", tag())
    val firstCause = JSONObject()
      .put("ordinal", 1L)
      .put("atMonotonicMs", tag())
      .put("operationOrdinal", if (reference == null) tag() else JSONObject().put("state", "VALUE").put("value", reference))
      .put("stage", "RECEIPT_OPERATION")
      .put("outcome", "DEADLINE")
      .put("origin", "APPLICATION_GUARD")
      .put("error", error)
      .put("classifier", tag())
      .put("lifecycle", tag())
      .put("lastSuccessfulStage", unknown)
    return JSONObject()
      .put("format", "HUGR_PHONE_RECEIPT_INCIDENT_V3")
      .put("incidentPseudonym", incident)
      .put("connectionEpoch", epoch)
      .put("operationLimit", 64L)
      .put("lateSettlementReserve", 8L)
      .put("revision", 1L)
      .put("frozen", true)
      .put("firstFailure", "DEADLINE")
      .put("firstFailureAtMonotonicMs", 10L)
      .put("nativeCorrelation", "CORRELATION_UNRESOLVED")
      .put("nativeCorrelationReason", "BLE_PLX_TRANSACTION_ID_IS_NOT_ATT_REQUEST_ID_NO_EQUAL_COUNTER_OFFSET_OR_CLOCK_JOIN")
      .put("crashBeforeCheckpoint", "UNKNOWN_NOT_OBSERVABLE_NO_REPORT_IS_NOT_NON_ARRIVAL")
      .put("publication", JSONObject()
        .put("format", "HUGR_PHONE_RECEIPT_INCIDENT_PUBLICATION_V1")
        .put("currentBodyCommit", "NOT_CLAIMED_BY_BODY_METADATA")
        .put("acknowledgement", JSONObject.NULL)
        .put("knownLoss", JSONObject.NULL))
      .put("overflow", JSONObject()
        .put("evictedCompletedRows", 0L)
        .put("lostNewOperations", 0L)
        .put("lateSettlementWithoutRetainedRow", 0L)
        .put("traceWindowOverflow", false))
      .put("operations", operations)
      .put("diagnosticContext", JSONObject()
        .put("firstCause", firstCause)
        .put("preceding", JSONArray())
        .put("subsequent", JSONArray())
        .put("lastSuccessfulStage", tag())
        .put("contextOverflow", JSONObject()
          .put("precedingEvicted", 0L)
          .put("subsequentDropped", 0L)
          .put("truncated", false))
        .put("processLoss", "UNKNOWN_NOT_OBSERVABLE"))
      .toString()
  }

  private fun mainOperation(ordinal: Long, transaction: String): JSONObject = JSONObject()
    .put("localOrdinal", ordinal)
    .put("operationKind", "R1_WRITE")
    .put("localTransactionId", transaction)
    .put("pageOffset", 0L)
    .put("jsSubmittedAtMonotonicMs", ordinal)
    .put("settledAtMonotonicMs", 10L)
    .put("pending", false)
    .put("deadlineObserved", true)
    .put("disconnectObserved", false)
    .put("nativeAttDispatch", "UNKNOWN")

  private fun tag(): JSONObject = JSONObject().put("state", "UNKNOWN")

  private inline fun withSyntheticRoot(block: (File) -> Unit) {
    val root = Files.createTempDirectory("hugr-synthetic-retention-").toFile()
    try {
      block(root)
    } finally {
      root.deleteRecursively()
    }
  }
}
