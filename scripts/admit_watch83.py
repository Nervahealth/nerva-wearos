#!/usr/bin/env python3
"""Read-only Watch83 APK admission; no device operation or source-store access."""
from pathlib import Path
import argparse,hashlib,json,re,zipfile,importlib.util,subprocess
TOOLS=Path('/home/ubuntu/android-sdk-watch-recovery-2026-10-03/build-tools/34.0.0')
SIGNER='fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706'
COMPAT='a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784'
SOURCE='24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361'

def cmd(*a):return subprocess.check_output([str(x) for x in a],stderr=subprocess.STDOUT).decode('utf-8',errors='replace')
def main():
 a=argparse.ArgumentParser();a.add_argument('apk',type=Path);a.add_argument('--out',type=Path,required=True);o=a.parse_args();o.out.mkdir(parents=True,exist_ok=True)
 root=Path(__file__).resolve().parents[1]
 spec=importlib.util.spec_from_file_location('dex_reader',Path('/home/ubuntu/HUGR_PHONE_SOURCE_RECOVERY_2026_10_03/repository/scripts/admit_phone61.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 apk=o.apk.resolve();sha=hashlib.sha256(apk.read_bytes()).hexdigest();badge=cmd(TOOLS/'aapt2','dump','badging',apk)
 assert "package: name='com.hugr.wearos' versionCode='83' versionName='0.83.0-duration-ladder-candidate'" in badge
 cert=cmd(TOOLS/'apksigner','verify','--verbose','--print-certs',apk);assert 'certificate SHA-256 digest: '+SIGNER in cert
 cmd(TOOLS/'zipalign','-c','-p','4',apk);manifest=cmd(TOOLS/'aapt','dump','xmltree',apk,'AndroidManifest.xml')
 assert 'com.hugr.wearos.MainActivity' in manifest and 'HUGR Saved Diagnostics' in manifest
 assert hashlib.sha256((root/'app/src/main/java/com/hugr/wearos/SourceJournal.kt').read_bytes()).hexdigest()==SOURCE
 defs=[];runtime=b''
 with zipfile.ZipFile(apk) as z:
  assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
  for n in z.namelist():
   if re.fullmatch(r'classes\d*\.dex',n):
    d=z.read(n);defs.extend(m.classes(d));runtime+=d
  assert hashlib.sha256(z.read('classes5.dex')).hexdigest()==COMPAT
 assert len(defs)==len(set(defs)),'Duplicate classes'
 for c in ['FreshRunRuntime','FreshRunSession','FreshRunReceipt','FreshRunReceiptStore','FreshRunReceiptPager','BleGattService','SourceJournal','SavedDiagnosticActivity']:
  assert defs.count('Lcom/hugr/wearos/'+c+';')==1,'Missing/duplicate Watch class: '+c
 for t in [b'receiptRootAtTrustedAnchor',b'freshReadPending',b'freshTerminalFailure',b'freshAdmissionScheduled',b'freshActionBlocked',b'latchFreshFailure',b'HUGR-ExplicitFreshAdmission',b'HUGR-FreshReceiptRead',b'Fresh admission failed',b'Start fresh 5 minutes',b'Serve current fresh receipt only',b'99999999-9999-4999-8999-999999999999',b'HUGR_RUN_RECEIPT_V1',b'fresh_ordinary_runs_v3',b'pendingAck',b'START_FRESH_FIVE_MINUTES']:
  assert t in runtime,'Missing compatible Watch behavior: '+t.decode()
 # Validate compiled launcher policy on exactly the two intended Activities.
 for component,affinity in [('MainActivity','com.hugr.wearos.main'),('SavedDiagnosticActivity','com.hugr.wearos.diagnostics')]:
  blocks=re.split(r'(?=E: activity\b)',manifest)
  block=next(b for b in blocks if 'com.hugr.wearos.'+component+'"' in b)
  assert 'taskAffinity' in block and affinity in block
  assert re.search(r'documentLaunchMode.*0x2\b',block), 'Missing always-document policy: '+component
  assert 'clearTaskOnLaunch' not in block and 'finishOnTaskLaunch' not in block
 for token in [b'freshReadinessResolved',b'freshReceiptDisplayed',b'isRecordingStopScheduled',b'bindAttempt',b'failFreshInitialization',b'saveFreshDiagnostic',b'HEALTH_INITIALIZATION_FAILED',b'SDK_CAPABILITY_FAILED',b'SDK_CONNECTION_FAILED']:
  assert token in runtime, 'Missing combined flow token: '+token.decode()
 for token in [b'freshReconnectAvailable',b'freshReconnectUsed',b'freshConnectedOnce']:
  assert token in runtime, 'Missing corrected fresh transport policy: '+token.decode()
 for name in ['FreshRunReadinessPresenter','FreshRunReadinessPresentation','FreshRunReadinessInput','FreshRunUiIdentity','FreshRunObservedReceipt']:
  assert defs.count('Lcom/hugr/wearos/'+name+';')==1, 'Missing/duplicate readiness class: '+name
 for token in [b'beginFreshStartRecheck',b'completeFreshStartRecheck',b'freshAdmissionIdentity',b'renderFreshReadiness',b'freshStartBlocked',b'READY TO START',b'PREVIOUS RUN UNRESOLVED',b'NEW RUN ADMISSION PENDING',b'NEW RUN ADMITTED',b'CURRENT RUN FAILED',b'PHONE CHECK SEPARATE']:
  assert token in runtime, 'Missing readiness correction token: '+token.decode()
 for name in ['FreshRunActivityAdmissionDisplayTest','FreshRunActivityAdmissionDisplayTestNoBackupShadow','FreshRunReadinessPresentationTest','FreshRunActivityRefreshTest']:
  assert 'Lcom/hugr/wearos/'+name+';' not in defs, 'Test-only class packaged: '+name
 for name in ['WatchFreshRunDurationPolicy']:
  assert defs.count('Lcom/hugr/wearos/'+name+';')==1
 for token in [b'HUGR_RUN_RECEIPT_V2', b'START_FRESH_DURATION_LADDER', b'fresh_duration_ms', b'selectedFreshDurationMs']:
  assert token in runtime, 'Missing duration policy inclusion: '+token.decode()
 for name,text in [('apk-badging.txt',badge),('apk-signer.txt',cert),('apk-manifest.txt',manifest)]: (o.out/name).write_text(text)
 meta={'apk':str(apk),'bytes':apk.stat().st_size,'sha256':sha,'package':'com.hugr.wearos','versionCode':83,'versionName':'0.83.0-duration-ladder-candidate','signerSha256':SIGNER,'compatibilityDexSha256':COMPAT,'protectedSourceSha256':SOURCE,'classDefinitions':len(defs),'uniqueClasses':'PASS','compiledFreshRunReceiptBridge':'PASS','compiledCorrectedFreshStartup':'PASS','compiledReadinessAdmissionPresentation':'PASS','zip':'PASS','alignment':'PASS'}
 (o.out/'artifact-admission.json').write_text(json.dumps(meta,indent=2)+'\n');apk.with_suffix(apk.suffix+'.sha256').write_text(sha+'  '+apk.name+'\n');print(json.dumps(meta,indent=2))
if __name__=='__main__':main()
