from pathlib import Path
import hashlib,json,subprocess
root=Path('/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03');p=Path('/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone')
base=json.loads((root/'PREFLIGHT_SHA256.json').read_text())
sj='modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/'
allowed={'lib/receiptIncidentDiagnostic.ts','modules/hugr-source-journal/index.ts',sj+'HUGRSourceJournalModule.kt',sj+'ReceiptReadProbeStore.kt',sj+'HUGRSavedReceiptDiagnosticsActivity.kt'}
changed=[];post={}
for path,h in base.items():
 f=Path(path);assert f.is_file(),path
 actual=hashlib.sha256(f.read_bytes()).hexdigest();post[path]=actual
 if actual!=h:changed.append(path)
assert set(changed)=={str(p/rel) for rel in allowed},changed
assert not subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=p,text=True).splitlines()
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=p,text=True).strip()=='d4af9210bc346607e346d766e5ad4eca6e589c6d'
assert subprocess.run(['git','diff','--check'],cwd=p,capture_output=True).returncode==0
# Bridge and wrapper changes are entirely confined to READ registration / argument forwarding.
for rel,start,end in [('modules/hugr-source-journal/index.ts','/** READ-only source-hook snapshot;', '\nexport interface NativeWatchRunReceiptResult'),(sj+'HUGRSourceJournalModule.kt','    // READ-only detached checkpoint;', '\n    AsyncFunction("persistWatchRunReceipt")')]:
 old=subprocess.check_output(['git','show','HEAD:'+rel],cwd=p,text=True);new=(p/rel).read_text()
 def remove(s):a=s.index(start);b=s.index(end,a);return s[:a]+s[b:]
 assert remove(old)==remove(new),rel
activity=(p/(sj+'HUGRSavedReceiptDiagnosticsActivity.kt')).read_text();assert 'setTextIsSelectable(true)' in activity and 'ReceiptReadProbeStore(applicationContext).read()' in activity
rel=sj+'HUGRSavedReceiptDiagnosticsActivity.kt';old=subprocess.check_output(['git','show','HEAD:'+rel],cwd=p,text=True);new=(p/rel).read_text()
assert old.replace('HUGR PHONE READ CURRENT PROBE V1\\noutcome=UNKNOWN\\ncode=READ_CHECKPOINT_INTEGRITY_OR_IO_FAILURE\\nnegativeClaim=NOT_MADE', 'HUGR PHONE READ CURRENT PROBE V2\\noutcome=UNKNOWN\\ncode=READ_CHECKPOINT_INTEGRITY_OR_IO_FAILURE\\nJS_ORIGIN=UNAVAILABLE_NO_CURRENT_BODY\\nnegativeClaim=NOT_MADE')==new
checks=['exact_five_production_paths_only','all_other_preflight_files_byte_identical','original_Phone73_candidate_and_harness_outputs_unchanged','HEAD_unchanged_no_commit','diff_check','bridge_wrapper_only_READ_blocks_changed','selectable_reader_only_READ_error_label_changed','native_observer_callbacks_transport_timeouts_WRITE_primary_model_and_stores_unchanged']
(root/'POSTFLIGHT_SHA256.json').write_text(json.dumps(post,indent=2)+'\n')
result={'status':'PASS','checks':checks,'caseCount':len(checks),'changedProductionPaths':sorted(allowed),'preflightFileCount':len(base),'unchangedFileCount':len(base)-len(changed)}
(root/'test-results'/'SOURCE_GUARDS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
