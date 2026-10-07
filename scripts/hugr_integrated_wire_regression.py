#!/usr/bin/env python3
"""Run a synthetic, source-to-source Watch -> Phone -> Watch recovery test.

Usage: python3 scripts/hugr_integrated_wire_regression.py --phone /absolute/phone/repository --out /new/scratch/directory
Never points at device stores; the synthetic journal is created only in --out.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phone', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    watch = Path(__file__).resolve().parents[1]
    phone = args.phone.resolve(strict=True)
    out = args.out.resolve()
    if out.exists():
        parser.error('--out must be a new, empty synthetic-only directory')
    if not (phone / 'tests/watchPhoneWireIntegration.test.ts').is_file():
        parser.error('--phone must be the controlled Phone59 source repository')
    if out == watch or out == phone or watch in out.parents or phone in out.parents:
        parser.error('--out must be outside both source repositories')
    out.mkdir(parents=True)
    env = dict(os.environ, HUGR_WIRE_WORKSPACE=str(out),
               JAVA_HOME='/usr/lib/jvm/java-17-openjdk-amd64',
               ANDROID_HOME='/home/ubuntu/android-sdk-watch-recovery-2026-10-03')

    def run(label: str, cwd: Path, command: list[str], phase: str) -> None:
        command_env = dict(env, HUGR_WIRE_PHASE=phase)
        with (out / f'{label}.log').open('w') as logfile:
            completed = subprocess.run(command, cwd=cwd, env=command_env,
                                       stdout=logfile, stderr=subprocess.STDOUT, check=False)
        if completed.returncode:
            print(f'{label} FAILED (exit {completed.returncode}); see {out / (label + ".log")}', file=sys.stderr)
            raise SystemExit(completed.returncode)
        print(f'{label}: PASS')

    test = 'com.hugr.wearos.WatchPhoneWireIntegrationTest'
    gradle = ['./gradlew', ':app:testDebugUnitTest', '--no-daemon', '--no-build-cache',
              '--rerun-tasks', '--console=plain']
    run('watch_produce', watch, gradle + ['--tests', f'{test}.producer'], 'produce')
    if not (out / 'watch_wire.tsv').is_file():
        raise RuntimeError('Watch producer did not emit synthetic wire fixture')
    run('phone_ingest', phone, ['node', '--experimental-strip-types', '--experimental-specifier-resolution=node',
                               '--test', 'tests/watchPhoneWireIntegration.test.ts'], 'phone')
    if not (out / 'phone_acks.tsv').is_file():
        raise RuntimeError('Phone test did not emit ACKs')
    run('watch_verify', watch, gradle + ['--tests', f'{test}.verifier'], 'verify')
    print('PASS: three manifests in order; two manifest-only ACKs; final data after durable append;')
    print('      full-hash, full-session 59-byte Phone ACKs accepted and all Watch segments cleared.')
    print('SIMULATED ONLY: Phone durable-store model; Android native SQLite, physical BLE service,')
    print('                15-minute lifetime and on-device custody not tested.')
    print(f'Synthetic evidence/logs: {out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
