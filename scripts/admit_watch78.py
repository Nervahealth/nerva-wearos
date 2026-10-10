#!/usr/bin/env python3
"""Read-only Watch78 APK admission; no device operation or source-store access."""
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
 assert "package: name='com.hugr.wearos' versionCode='78' versionName='0.78.0-compatible-fresh-run-receipt-candidate'" in badge
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
 for t in [b'Start fresh 5 minutes',b'Serve current fresh receipt only',b'99999999-9999-4999-8999-999999999999',b'HUGR_RUN_RECEIPT_V1',b'fresh_ordinary_runs_v3',b'pendingAck',b'START_FRESH_FIVE_MINUTES']:
  assert t in runtime,'Missing compatible Watch behavior: '+t.decode()
 for name,text in [('apk-badging.txt',badge),('apk-signer.txt',cert),('apk-manifest.txt',manifest)]: (o.out/name).write_text(text)
 meta={'apk':str(apk),'bytes':apk.stat().st_size,'sha256':sha,'package':'com.hugr.wearos','versionCode':78,'versionName':'0.78.0-compatible-fresh-run-receipt-candidate','signerSha256':SIGNER,'compatibilityDexSha256':COMPAT,'protectedSourceSha256':SOURCE,'classDefinitions':len(defs),'uniqueClasses':'PASS','compiledFreshRunReceiptBridge':'PASS','zip':'PASS','alignment':'PASS'}
 (o.out/'artifact-admission.json').write_text(json.dumps(meta,indent=2)+'\n');apk.with_suffix(apk.suffix+'.sha256').write_text(sha+'  '+apk.name+'\n');print(json.dumps(meta,indent=2))
if __name__=='__main__':main()
