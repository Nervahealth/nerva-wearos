#!/usr/bin/env python3
"""Source-only selected export tests, actual eight-hour synthetic fixture and independent reader."""
from pathlib import Path
import os,subprocess,json,time,xml.etree.ElementTree as E,hashlib,shutil,importlib.util,zipfile
B=Path('/home/ubuntu/HUGR_SELECTED_RUN_EXPORT_2026_10_20');P=Path('/home/ubuntu/HUGR_PHONE_SOURCE_RECOVERY_2026_10_03/repository');H=B/'validation/native-harness';V=B/'validation'
fixture=Path('/home/ubuntu/HUGR_DURATION_LADDER_EXTENSION_2026_10_19/validation/fixture-8hr')
OUT=V/'synthetic-export-output';OUT.mkdir(exist_ok=True)
env=os.environ.copy();env.update(JAVA_HOME='/usr/lib/jvm/java-17-openjdk-amd64',ANDROID_HOME='/home/ubuntu/android-sdk-watch-recovery-2026-10-03',PYTHONDONTWRITEBYTECODE='1',HUGR_EXPORT_TEST_OUTPUT=str(OUT));env.pop('HUGR_EXPORT_FIXTURE',None);env.pop('HUGR_JOINED_RUN_FIXTURE',None);env.pop('HUGR_FRESH_FIXTURE_DIR',None)
results={'scope':'SYNTHETIC SOURCE/TEST ONLY; no APK/device/real export','phases':[],'packages':[]}
def run(label,args,cwd=P,extra=None):
 print('START',label,flush=True);e=env.copy();e.update(extra or {});t=time.monotonic()
 with (V/(label+'.log')).open('w') as f:r=subprocess.run(args,cwd=cwd,env=e,stdout=f,stderr=f,timeout=1800)
 row={'label':label,'exit_code':r.returncode,'seconds':round(time.monotonic()-t,3)};results['phases'].append(row);save();assert r.returncode==0,label+' FAILED; inspect saved log, do not build';print('PASS',label,row['seconds'],flush=True)
def save():(V/'export-validation-summary.json').write_text(json.dumps(results,indent=2)+'\n')
def counts(label,require_only=None):
 root=H/'build/test-results/testDebugUnitTest';dst=V/(label+'-xml');shutil.copytree(root,dst,dirs_exist_ok=False);total=failed=skipped=0;skip_names=[]
 for f in dst.glob('TEST-*.xml'):
  tree=E.parse(f).getroot();total+=int(tree.get('tests','0'));failed+=int(tree.get('failures','0'))+int(tree.get('errors','0'));skipped+=int(tree.get('skipped','0'))
  for c in tree.findall('testcase'):
   if c.find('skipped') is not None:skip_names.append({'class':tree.get('name'),'test':c.get('name')})
 assert failed==0 and total>0
 if require_only:assert total==1 and skipped==0 and any(require_only in f.read_text() for f in dst.glob('TEST-*.xml'))
 results[label]={'tests':total,'passed':total-skipped,'skipped':skipped,'failures_errors':failed,'skip_names':skip_names};save()
baseline=json.loads((B/'authority-and-baseline.json').read_text())
def protection():
 for name,sha in baseline['protected'].items():assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==sha,'PROTECTED SOURCE/EVIDENCE CHANGED'
protection()
run('independent-reader-complete',['python3','-m','unittest','-v','scripts/tests/test_verify_hugr_export.py'])
run('refresh-native-extraction',['python3',str(P/'scripts/prepare_phone_custody_harness.py'),'--out',str(H)])
run('native-export-complete-suite',['./gradlew','testDebugUnitTest','--no-daemon','--rerun-tasks','--console=plain'],cwd=H)
counts('native-export-complete-suite')
# No synthetic package is taken from the independent reader's handcrafted fixtures.
run('native-export-actual-eight-hour-fixture',['./gradlew','testDebugUnitTest','--tests','health.hugr.sourcejournal.SelectedCompletedRunExportTest.existingEightHourFixtureProductionWriterExportsSelectedPackage','--no-daemon','--rerun-tasks','--console=plain'],cwd=H,extra={'HUGR_EXPORT_FIXTURE':str(fixture)})
counts('native-export-actual-eight-hour-fixture','existingEightHourFixtureProductionWriterExportsSelectedPackage')
spec=importlib.util.spec_from_file_location('independent_export',P/'scripts/verify_hugr_export.py');reader=importlib.util.module_from_spec(spec);spec.loader.exec_module(reader)
packages=sorted(OUT.rglob('*.zip'));selected={}
for file in packages:
 with zipfile.ZipFile(file) as z:
  if len(z.infolist())!=8:continue
  receipt=z.read('watch-receipt.txt');parsed=reader.parse_receipt(receipt)
  version=parsed['version'];records=sum(x['recordCount'] for x in parsed['manifests'])
  kind='8hr' if records==801_505 else version.lower()
  if kind in ['v1','v2','8hr']:selected[kind]=file
assert set(selected)=={'v1','v2','8hr'},'Exporter-produced V1, V2 and exact eight-hour packages all required'
for kind,file in selected.items():
 print('INDEPENDENT VERIFY',kind,flush=True);t=time.monotonic()
 with zipfile.ZipFile(file) as z:
  report=reader.parse_report(z.read('phone-report.hugr-report'),z.read('watch-receipt.txt'));receipt=reader.parse_receipt(z.read('watch-receipt.txt'))
 result=reader.verify(file,report['source'],receipt['run'],report['acceptedSha'],report['acceptedBytes']);assert result['outcome']=='VERIFIED_OFFLINE_CONTRACT_V1'
 if kind=='8hr':assert result['canonical']['count']==801505 and result['manifestCount']==480 and result['watchAcceptedAckCount']==480
 destination=V/(kind+'-independent-inventory');destination.mkdir(exist_ok=False)
 (destination/'inventory.json').write_text(json.dumps(result,indent=2)+'\n');(destination/'inventory.md').write_text('> SYNTHETIC SOFTWARE REGRESSION ONLY — NOT A REAL RECORDING.\n\n'+reader.render_inventory_markdown(result))
 row={'kind':kind,'synthetic_package':str(file),'package_sha256':result['packageSha256'],'package_bytes':result['packageBytes'],'canonical_bytes':result['canonical']['bytes'],'canonical_sha256':result['canonical']['sha256'],'records':result['canonical']['count'],'manifests':result['manifestCount'],'exact_watch_accepted_acks':result['watchAcceptedAckCount'],'independent_seconds':round(time.monotonic()-t,3),'streams':result['canonical']['streams'],'verified':True}
 results['packages'].append(row);save();print('INDEPENDENT PASS',kind,row['records'],row['manifests'],row['independent_seconds'],flush=True)
run('phone-complete-regressions',['bash','-c','node --experimental-strip-types --experimental-specifier-resolution=node --test tests/*.test.ts'],extra={'HUGR_FRESH_FIXTURE_DIR':'/home/ubuntu/HUGR_DURATION_LADDER_EXTENSION_2026_10_19/validation/fixture-5min'})
protection();results['protected_source_and_physical_pass_unchanged']=True;results['status']='SOURCE/TEST HANDOFF VERIFIED; REAL HANDOFF NOT COMPLETE; BUILD NOT AUTHORIZED';save();print(results['status'],flush=True)
