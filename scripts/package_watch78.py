#!/usr/bin/env python3
"""Stage the source-built Watch78 APK with the unchanged admitted compatibility DEX.

Signing values are read from the existing local Gradle configuration only in memory;
secrets are passed to apksigner through the child process environment and never printed.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

root = Path('/home/ubuntu/HUGR_WATCH69_ORDINARY_ONLY_2026_10_03/worktree')
build_root = Path('/home/ubuntu/HUGR_WATCH78_PHONE61_COMPATIBLE_BUILD_2026_10_10')
base = root / 'app/build/outputs/apk/debug/app-debug.apk'
old = Path('/home/ubuntu/HUGR_WATCH77_DURABLE_DIAGNOSTICS_2026_10_08/artifacts/HUGR_Watch77w_0.77.0_durable-diagnostics-historical-resume-candidate.apk')
compat = root / 'app/egress-compat-classes2.dex'
unsigned = build_root / 'staging/unsigned-compat.apk'
aligned = build_root / 'staging/aligned-compat.apk'
final = build_root / 'artifacts/HUGR_Watch78w_0.78.0_compatible-fresh-run-receipt-candidate.apk'
bt = Path('/home/ubuntu/android-sdk-watch-recovery-2026-10-03/build-tools/34.0.0')

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

assert base.is_file() and old.is_file() and compat.is_file()
with zipfile.ZipFile(old) as z:
    admitted_compat_sha = sha(z.read('classes5.dex'))
assert admitted_compat_sha == sha(compat.read_bytes()), 'compatibility payload must match admitted Watch77'
with zipfile.ZipFile(base) as z:
    dex = [n for n in z.namelist() if re.fullmatch(r'classes[0-9]*\.dex', n)]
    assert set(dex) == {'classes.dex', 'classes2.dex', 'classes3.dex', 'classes4.dex'}, dex
    with zipfile.ZipFile(unsigned, 'w', allowZip64=True) as output:
        for info in z.infolist():
            name = info.filename
            if name.startswith('META-INF/') and (name.endswith(('.SF', '.RSA', '.DSA')) or name == 'META-INF/MANIFEST.MF'):
                continue
            output.writestr(info, z.read(info))
        addition = zipfile.ZipInfo('classes5.dex')
        addition.compress_type = zipfile.ZIP_STORED
        addition.external_attr = 0o644 << 16
        output.writestr(addition, compat.read_bytes())

subprocess.run([str(bt / 'zipalign'), '-f', '-p', '4', str(unsigned), str(aligned)], check=True, stdout=subprocess.DEVNULL)
gradle = (root / 'app/build.gradle').read_text()
signing = gradle.split('signingConfigs {', 1)[1].split('defaultConfig {', 1)[0]

def value(field: str, pattern: str) -> str:
    match = re.search(pattern, signing)
    if not match:
        raise RuntimeError(f'Existing Gradle signing field unavailable: {field}')
    return match.group(1)

keystore = (root / 'app' / value('storeFile', r"storeFile\s+file\('([^']+)'\)")).resolve()
assert keystore.is_file(), 'existing signing identity unavailable'
child_env = os.environ.copy()
child_env['HUGR_BUILD_KS_PASS'] = value('storePassword', r"storePassword\s+'([^']+)'" )
child_env['HUGR_BUILD_KEY_PASS'] = value('keyPassword', r"keyPassword\s+'([^']+)'" )
alias = value('keyAlias', r"keyAlias\s+'([^']+)'" )
subprocess.run([
    str(bt / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', alias,
    '--ks-pass', 'env:HUGR_BUILD_KS_PASS', '--key-pass', 'env:HUGR_BUILD_KEY_PASS',
    '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'false',
    '--v3-signing-enabled', 'true', '--out', str(final), str(aligned),
], check=True, env=child_env, stdout=subprocess.DEVNULL)
with zipfile.ZipFile(final) as z:
    assert z.testzip() is None
    assert z.namelist().count('classes5.dex') == 1
    assert sha(z.read('classes5.dex')) == admitted_compat_sha
print(f'STAGED {final}')
print(f'COMPAT_DEX_SHA256 {admitted_compat_sha}')
print(f'APK_SHA256 {sha(final.read_bytes())}')
