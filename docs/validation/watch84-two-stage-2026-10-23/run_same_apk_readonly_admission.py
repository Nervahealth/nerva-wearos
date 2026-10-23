#!/usr/bin/env python3
"""One same-APK read-only admission; no build/sign/upload/device actions."""
from pathlib import Path
import datetime, hashlib, json, os, subprocess, sys
D = Path('/home/ubuntu/HUGR_WATCH84_TWO_STAGE_RECOVERY_2026_10_23')
W = Path('/home/ubuntu/HUGR_PHONE_EXPORT_SCRATCH_CORRECTION_2026_10_20/watch')
B = Path('/home/ubuntu/HUGR_WATCH84_PHONE66_LADDER_2026_10_22')
APK = D/'artifacts/HUGR_Watch84w_0.84.0_positional-v2-duration-ladder-candidate.apk'
PHONE = B/'phone66-artifacts/HUGR_Phone66p_2.0.66_positional-v2-duration-ladder-candidate.apk'
V = D/'validator-correction/admit_watch84_candidate_corrected.py'
OUT = D/'final-readonly-admission'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args):
    return subprocess.check_output(['git','-C',str(W),*args]).decode().strip()
def custody():
    markers = json.loads((D/'earlier-attempt-custody.json').read_text())
    assert all(sha(Path(k)) == v for k,v in markers.items())
    assert APK.stat().st_size == 13008645 and sha(APK) == 'e0525b5e09c2682de23ff8bc2a26bb10e765d2e308c0e96bf39576a80c0a20db'
    assert PHONE.stat().st_size == 152097114 and sha(PHONE) == '8978c8d64e19420b47ebd45b9a7898b07749114644ddb5df5aecde6ed653accf'
    assert sha(B/'artifact-preparation/admit_watch84_candidate.py') == '9870129cc578d53ad7df0d7f6cb284366e03abe7087a438cf0fd276a1d2fabc3'
    assert sha(V) == 'cab0b4d7a5c47e9ea0d6e3fb657228728cee8909c8b1e67b3203c8269cd58f8d'
    assert git('rev-parse','HEAD') == '8f9e769e1f1f41f6620be0364b63b4c9b445e28b'
    assert git('rev-parse','HEAD:app') == '5a14a3c09d5037e9b4c24f680de76e630d333139'
    for owner in ('watch','phone','controls'):
        assert not subprocess.check_output(['git','-C',str(W.parent/owner),'status','--porcelain']).strip()
    cfg = json.loads((D/'actual-offline-reporting-continuation-result.json').read_text())['before']['application']['applicationBuildConfiguration']
    assert all(sha(W/k) == v['sha256'] for k,v in cfg.items())
    return {'watchApkSha256':sha(APK),'watchApkBytes':APK.stat().st_size,'phone66Sha256':sha(PHONE),'earlierMarkers':markers,'initialAdmissionStopSha256':sha(D/'readonly-admission-stop.json'),'newBuildMarkerSha256':sha(D/'new-build-stage/build-attempt.json'),'originalValidatorSha256':sha(B/'artifact-preparation/admit_watch84_candidate.py'),'correctedValidatorSha256':sha(V),'applicationHead':git('rev-parse','HEAD'),'applicationTree':git('rev-parse','HEAD^{tree}'),'applicationAppTree':git('rev-parse','HEAD:app'),'buildConfiguration':cfg}
if __name__ == '__main__':
    os.umask(0o077)
    before = custody()
    OUT.mkdir(exist_ok=False)
    (OUT/'readonly-invocation.json').write_text(json.dumps({'started':datetime.datetime.now(datetime.timezone.utc).isoformat(),'before':before,'buildActions':0,'sameApkOnly':True},indent=2)+'\n')
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', HUGR_ANDROID_BUILD_TOOLS_DIR='/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/android-sdk/build-tools/34.0.0', HUGR_QEMU_X86_64='/usr/bin/qemu-x86_64-static', HUGR_X86_64_SYSROOT='/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/x86_64-sysroot', JAVA_HOME='/usr/lib/jvm/java-17-openjdk-arm64')
    with (OUT/'full-readonly-admission.log').open('wb') as log:
        result = subprocess.run([sys.executable,str(V),str(APK),'--out',str(OUT),'--inspection-deadline-seconds','1200'],env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1250)
    after = custody()
    assert before == after, 'READ_ONLY_CUSTODY_CHANGED_STOP'
    receipt = OUT/'watch84-candidate-artifact-admission.json'
    passed = result.returncode == 0 and receipt.is_file() and json.loads(receipt.read_text())['status'] == 'PASS'
    (OUT/'same-apk-continuation-result.json').write_text(json.dumps({'status':'PASS' if passed else 'STOP','returnCode':result.returncode,'before':before,'after':after,'allCustodyUnchanged':before==after,'receipt':str(receipt) if receipt.exists() else None,'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),'additionalBuilds':0,'deviceActions':0},indent=2)+'\n')
    print('WATCH84_SAME_APK_FULL_READ_ONLY_ADMISSION_' + ('PASS' if passed else 'STOP'),flush=True)
    raise SystemExit(0 if passed else 1)
