from pathlib import Path
import hashlib,json,subprocess,re
root=Path('/home/ubuntu/HUGR_PHONE_READ_PATH_CORRECTION_2026_11_03');p=Path('/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone');base=json.loads((root/'BASELINE_SOURCE_HASHES.json').read_text());out=root/'test-results'/'SOURCE_GUARDS.json'
rx='modules/hugr-rxandroidble-observation/android/src/main/java/com/polidea/rxandroidble2/internal/';sj='modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/'
allowed={'lib/receiptIncidentDiagnostic.ts','modules/hugr-source-journal/index.ts',rx+'connection/RxBleGattCallback.java',rx+'operations/CharacteristicReadOperation.java',rx+'util/QueueReleasingEmitterWrapper.java',sj+'HUGRSavedReceiptDiagnosticsActivity.kt',sj+'HUGRSourceJournalModule.kt',sj+'nativeobservation/NativeObservationModuleLifecycle.java',rx+'util/HugrReceiptReadObserver.java',sj+'ReceiptReadProbeStore.kt'}
changed=[x['path'] for x in base['files'] if hashlib.sha256((p/x['path']).read_bytes()).hexdigest()!=x['sha256']]
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=p,text=True).splitlines()
assert set(changed+untracked)==allowed,(changed,untracked)
for rel in changed:assert (root/'preimages'/rel).read_bytes()==subprocess.check_output(['git','show',base['head']+':'+rel],cwd=p)
checks=['tracked_change_allowlist_exact','all_modified_preimages_exact_head_bytes']
# Removing READ hook statements reproduces the exact accepted downstream wrapper (WRITE calls/order intact).
rel=rx+'util/QueueReleasingEmitterWrapper.java';new=(p/rel).read_text();old=(root/'preimages'/rel).read_text();assert re.sub(r'^\s*HugrReceiptReadObserver\.(complete|error|value|cancel)\(actualOperation\);\n','',new,flags=re.M)==old;checks.append('downstream_wrapper_exact_baseline_after_read_hooks_removed')
rel=rx+'connection/RxBleGattCallback.java';new=(p/rel).read_text();old=(root/'preimages'/rel).read_text();assert new.replace('            com.polidea.rxandroidble2.internal.util.HugrReceiptReadObserver.callbackEntry(gatt, characteristic, status);\n','')==old;checks.append('callback_class_exact_baseline_except_first_read_hook')
rel=rx+'internal/SingleResponseOperation.java' # deliberately use canonical path below
rel=rx+'SingleResponseOperation.java';assert hashlib.sha256((p/rel).read_bytes()).hexdigest()==next(x['sha256'] for x in base['files'] if x['path']==rel);checks.append('single_response_subscription_timeout_order_byte_unchanged')
for suffix in ['util/HugrReceiptWriteObserver.java','util/HugrReceiptWriteTrace.java','util/HugrCallbackEntryLane.java','operations/CharacteristicWriteOperation.java']:
 rel=rx+suffix;assert hashlib.sha256((p/rel).read_bytes()).hexdigest()==next(x['sha256'] for x in base['files'] if x['path']==rel)
for suffix in ['ReceiptCallbackProbeStore.kt','ReceiptIncidentDiagnosticStore.kt','ReceiptNativeWriteObservationStore.kt']:
 rel=sj+suffix;assert hashlib.sha256((p/rel).read_bytes()).hexdigest()==next(x['sha256'] for x in base['files'] if x['path']==rel)
checks.append('write_observer_ring_timing_serializer_and_legacy_incident_store_byte_unchanged')
activity=(p/(sj+'HUGRSavedReceiptDiagnosticsActivity.kt')).read_text();a=activity.index('  private fun readCurrentReadProbe()');b=activity.index('\n  private fun createDocument()',a);reader=activity[a:b];assert 'ReceiptReadProbeStore(applicationContext).read()' in reader and 'prepareReceiptDiagnosticPrivateCopy' not in reader and 'output.text = report' in reader and 'setTextIsSelectable(true)' in activity;checks.append('read_retrieval_existing_selectable_text_no_cache_save_dependency')
observer=(p/(rx+'util/HugrReceiptReadObserver.java')).read_text();assert 'tryLock()' in observer and 'UNASSIGNED' in observer;assert not any(s in observer for s in ['java.io.','android.util.Log','io.reactivex.','Thread.sleep']);checks.append('read_hook_no_io_logging_rx_subscription_or_sleep')
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=p,text=True).strip()==base['head'];assert subprocess.run(['git','diff','--check'],cwd=p,capture_output=True).returncode==0;checks+=['head_not_committed_or_changed','git_diff_check']
result={'status':'PASS','caseCount':len(checks),'checks':checks,'changed':sorted(changed),'added':sorted(untracked),'allOtherTrackedFilesByteUnchanged':True};out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
