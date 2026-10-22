#!/usr/bin/env python3
"""One explicitly approved fresh-stage replacement. Original driver stays unchanged."""
from pathlib import Path
import os,sys,json,hashlib,importlib.util,subprocess,time,threading,re,argparse
os.umask(0o077)
D=Path('/home/ubuntu/HUGR_WATCH84_REPLACEMENT_2026_10_23');B=Path('/home/ubuntu/HUGR_WATCH84_PHONE66_LADDER_2026_10_22');R=Path('/home/ubuntu/HUGR_PHONE_EXPORT_SCRATCH_CORRECTION_2026_10_20/watch')
SHA=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
ORIGINAL_MARKER_SHA='4178829a99df868af34b4c3e380248e2e9f5a4c1852f2708c430d4902b60be34'
DEPENDENCY_PROOF_SHA='073b65cad842e5cfb421041b7efce3527e5bcba4dadc166656af8b896546fee0'
CACHE=Path('/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1')
PINS=[('androidx.wear.tiles','tiles-proto','1.2.0','3090e207e4ebd46e46c159822ab293c9fc4a42c6',45726,'6561349e6bc836166212c5b8fd51876c91c25519191ec32fbc4217def39b588a'),('androidx.wear.protolayout','protolayout-proto','1.0.0','abb132807aa40f10fb02639c770289e87366d550',1739194,'28fc4c57860e46d029fa589c8bbbe0edb91cdf03932fffbba01c96c00e50a454'),('androidx.concurrent','concurrent-futures','1.1.0','50b7fb98350d5f42a4e49704b03278542293ba48',25987,'0ce067c514a0d1049d1bebdf709e344ed3266fe9744275682937cdcb13334e9e'),('androidx.resourceinspection','resourceinspection-annotation','1.0.1','8c21f8ff5d96d5d52c948707f7e4d6ca6773feef',2336,'8cff870ec6fb31db48a52f4a792335b4bf8de07e03bd37823181526433ccd5cb')]
def git(*a):return subprocess.check_output(['git',*a],cwd=R).decode().strip()
def load_driver():
 path=B/'watch84-preparation/watch84_fresh_stage_build_driver.py';s=importlib.util.spec_from_file_location('unchanged_watch84_original_driver',path);m=importlib.util.module_from_spec(s);sys.modules[s.name]=m;s.loader.exec_module(m);return m

def preflight():
 assert SHA(B/'watch84-build-stage/build-attempt.json')==ORIGINAL_MARKER_SHA,'ORIGINAL_MARKER_CHANGED'
 assert SHA(D/'dependencies/dependency-restoration-proof.json')==DEPENDENCY_PROOF_SHA,'DEPENDENCY_PROOF_CHANGED'
 original=json.loads((B/'watch84-build-stage/build-attempt.json').read_text());assert git('rev-parse',original['sourceHead']+':app')==git('rev-parse','HEAD:app'),'ORIGINAL_APPLICATION_TREE_CHANGED'
 for g,a,v,h,n,d in PINS:
  p=CACHE/g/a/v/h/(a+'-'+v+'.jar');assert p.is_file() and not p.is_symlink() and p.stat().st_size==n and SHA(p)==d,'PINNED_CACHE_READBACK_CHANGED'
 m=load_driver();m.tool_pins();m.predecessor_compatibility_dex()
 verified=m.verify_gate_bundle(R,B/'final-release/four-rung-compositional-proof.json',B/'final-release/all-four-independent-reader-proof.json',D/'authority-independent-remote-readback.json')
 assert not (D/'replacement-invocation.json').exists(),'REPLACEMENT_ALREADY_INVOKED_NO_AUTOMATIC_DUPLICATE'
 assert not (D/'replacement-stage').exists() and not (D/'artifacts').exists(),'REPLACEMENT_STAGE_OR_ARTIFACT_EXISTS'
 return m,verified,original

def main(execute=False):
 m,verified,original=preflight();print('WATCH84_REPLACEMENT_PREFLIGHT_PASS_ORIGINAL_APP_TREE_AND_MARKER_EXACT',flush=True)
 if not execute:return
 (D/'replacement-invocation.json').write_text(json.dumps({'status':'ONE_REPLACEMENT_INVOKED_NO_AUTOMATIC_RETRY','time':m.now(),'originalStage':str(B/'watch84-build-stage'),'originalMarkerSha256':ORIGINAL_MARKER_SHA,'originalSourceHead':original['sourceHead'],'originalSourceTree':original['sourceTree'],'originalAppTree':git('rev-parse',original['sourceHead']+':app'),'replacementSource':verified['source'],'dependencyProofSha256':DEPENDENCY_PROOF_SHA,'originalDriverSha256':SHA(B/'watch84-preparation/watch84_fresh_stage_build_driver.py'),'phoneRebuildAuthorized':False},indent=2)+'\n')
 stage=D/'replacement-stage';m.stage_runtime(R,stage,verified)
 (stage/'separate-replacement-authority.json').write_text(json.dumps({'authorizedAt':'2026-10-23T00:59:39+02:00','authoritySha256':SHA(D/'HUGR_WATCH84_REPLACEMENT_BUILD_AUTHORITY_2026_10_23.md'),'originalMarkerSha256':ORIGINAL_MARKER_SHA,'separatelyAuthorizedReplacement':True,'originalStageNotReused':True},indent=2)+'\n')
 original_run=m.run_no_output
 def capture_only_readonly_validator(command,env,cwd,timeout,code):
  if code!='READ_ONLY_WATCH84_ARTIFACT_ADMISSION_STOP':return original_run(command,env,cwd,timeout,code)
  try:
   p=subprocess.run(command,cwd=cwd,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=timeout)
  except subprocess.TimeoutExpired:
   (D/'readonly-admission-stop.json').write_text(json.dumps({'code':'READ_ONLY_WATCH84_ARTIFACT_ADMISSION_STOP','cause':'READ_ONLY_TOOL_DEADLINE'},indent=2)+'\n');raise m.Stop(code)
  if p.returncode:
   match=re.search(rb'WATCH84_CANDIDATE_ADMISSION_STOP:([^\r\n]{1,2000})',p.stderr)
   cause=match.group(1).decode('utf-8','replace') if match else 'READ_ONLY_INSPECTOR_NONZERO_WITHOUT_PUBLIC_STOP'
   (D/'readonly-admission-stop.json').write_text(json.dumps({'code':code,'cause':cause,'returnCode':p.returncode,'apkUnchangedByInspector':True},indent=2)+'\n');raise m.Stop(code)
 m.run_no_output=capture_only_readonly_validator
 stop=threading.Event();start=time.monotonic()
 def progress():
  while not stop.wait(60):print('WATCH84_REPLACEMENT_ACTIVE elapsedSeconds='+str(int(time.monotonic()-start)),flush=True)
 t=threading.Thread(target=progress,daemon=True);t.start()
 try:
  print('WATCH84_SOLE_REPLACEMENT_BUILD_AND_ADMISSION_START',flush=True);result=m.build_once(stage,R,D/'artifacts')
  assert SHA(B/'watch84-build-stage/build-attempt.json')==ORIGINAL_MARKER_SHA
  (D/'replacement-result.json').write_text(json.dumps({'status':'PASS','separatelyAuthorizedReplacement':True,'buildsInThisReplacement':1,'originalMarkerUnchanged':True,'originalApplicationTreeUnchanged':True,'phoneRebuilt':False,'driverResult':result},indent=2)+'\n');print('WATCH84_REPLACEMENT_BUILD_AND_ARTIFACT_ADMISSION_PASS',flush=True)
 except m.Stop as e:
  (D/'replacement-stop.json').write_text(json.dumps({'status':'STOP','cause':str(e),'elapsedSeconds':time.monotonic()-start,'buildAttemptMarkerExists':(stage/'build-attempt.json').exists(),'finalApkExists':(D/'artifacts/HUGR_Watch84w_0.84.0_positional-v2-duration-ladder-candidate.apk').exists(),'originalMarkerUnchanged':SHA(B/'watch84-build-stage/build-attempt.json')==ORIGINAL_MARKER_SHA,'noFurtherBuildAttemptAuthorized':True},indent=2)+'\n');print('WATCH84_REPLACEMENT_STOP:'+str(e),flush=True);raise SystemExit(1)
 finally:stop.set();t.join(timeout=1)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');a=p.parse_args();main(a.execute)
