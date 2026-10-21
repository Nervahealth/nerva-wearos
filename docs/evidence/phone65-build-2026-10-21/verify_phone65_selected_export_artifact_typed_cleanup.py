#!/usr/bin/env python3
"""Read-only Phone65 selected completed-run-export compiled APK admission validator.

This is complementary to the parent-owned Phone64 admission script.  It verifies the
signed APK's archive, identity, existing duration-ladder proof, retained source-evidence
guard, and the new *compiled* selected-export activity/provider/exporter contract.
It never builds, signs, stages, uploads, installs, launches, or changes the APK.

Phone65 identity is fixed by the separately authorized single-candidate admission: versionCode 65,
versionName 2.0.65-selected-run-export-scratch-candidate, existing package/project/signer only.
This validator reads artifacts only; it never builds, signs, stages, uploads, installs, launches,
or modifies an APK or a sibling hash file.
"""
from __future__ import annotations

import argparse
import os
import shutil
import hashlib
import json
import re
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path
from typing import Any

# The runner must point this at the supplied Android build-tools directory.  The legacy fallback
# is retained solely for a historically provisioned runner; it is deliberately not downloaded,
# replaced, or otherwise provisioned by this validator.
TOOLS = Path(os.environ.get(
    'HUGR_ANDROID_BUILD_TOOLS_DIR',
    '/home/ubuntu/android-sdk-watch-recovery-2026-10-03/build-tools/34.0.0',
))
QEMU_X86_64 = os.environ.get('HUGR_QEMU_X86_64', 'qemu-x86_64')
X86_64_SYSROOT = os.environ.get(
    'HUGR_X86_64_SYSROOT',
    '/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/x86_64-sysroot',
)
PACKAGE = 'health.hugr.app'
VERSION_CODE = 65
VERSION_NAME = '2.0.65-selected-run-export-scratch-candidate'
SIGNER = 'a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811'
APPLICATION = 'Lhealth/hugr/app/MainApplication;'
SOURCE_EVIDENCE_ACTIVITY = 'Lhealth/hugr/sourcejournal/HUGRSavedSourceEvidenceActivity;'
WRITER = 'Lhealth/hugr/sourcejournal/HUGRSourceJournalModule;'
SELECTED_ACTIVITY = 'Lhealth/hugr/sourcejournal/SelectedCompletedRunExportActivity;'
SELECTED_EXPORTER = 'Lhealth/hugr/sourcejournal/SelectedCompletedRunExporter;'
SELECTED_PROVIDER = 'Lhealth/hugr/sourcejournal/SelectedRunExportProvider;'
SELECTED_REGISTRY = 'Lhealth/hugr/sourcejournal/SelectedRunExportArtifactRegistry;'
DURATION_CURSOR = 'Lhealth/hugr/sourcejournal/BoundedSelectedSnapshotKt;'
SCRATCH_SPACE = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;'
SCRATCH_CLEANUP_VISITOR = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace$cleanup$1;'


def invoke(*args: object) -> str:
    """Run one supplied read-only inspection tool, using QEMU only for x86 host tools."""
    command = [str(value) for value in args]
    environment = os.environ.copy()
    executable = Path(command[0])
    # ARM64 binfmt may return exit 255 rather than ENOEXEC for an x86 ELF. Detect the
    # executable header before running; shell wrappers such as apksigner remain direct.
    is_elf = executable.is_file() and executable.read_bytes()[:4] == b'\x7fELF'
    host_is_x86 = os.uname().machine.lower() in ('x86_64', 'amd64')
    if is_elf and not host_is_x86:
        qemu = shutil.which(QEMU_X86_64)
        if not qemu or not Path(X86_64_SYSROOT).is_dir():
            raise RuntimeError('X86_INSPECTION_TOOL_REQUIRES_QEMU_AND_SYSROOT')
        environment['QEMU_LD_PREFIX'] = X86_64_SYSROOT
        command = [qemu, *command]
    return subprocess.check_output(command, stderr=subprocess.STDOUT, env=environment).decode(
        'utf-8', errors='replace'
    )


def read_uleb(data: bytes, position: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        if position >= len(data):
            raise ValueError('DEX_ULEB_OUT_OF_RANGE')
        byte = data[position]
        position += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, position
        shift += 7
        if shift > 35:
            raise ValueError('DEX_ULEB_INVALID')


def dex_classes(data: bytes) -> list[str]:
    """Read DEX class descriptors without decoding/repacking the artifact."""
    if not data.startswith(b'dex\n'):
        raise ValueError('NOT_DEX')
    string_size, string_offset = struct.unpack_from('<II', data, 56)
    strings: list[str] = []
    for index in range(string_size):
        offset = struct.unpack_from('<I', data, string_offset + 4 * index)[0]
        _, offset = read_uleb(data, offset)
        terminator = data.index(b'\0', offset)
        strings.append(data[offset:terminator].decode('utf-8', errors='replace'))
    type_size, type_offset = struct.unpack_from('<II', data, 64)
    types = [strings[struct.unpack_from('<I', data, type_offset + 4 * index)[0]] for index in range(type_size)]
    class_size, class_offset = struct.unpack_from('<II', data, 96)
    return [types[struct.unpack_from('<I', data, class_offset + 32 * index)[0]] for index in range(class_size)]


def contains_text(payload: bytes, text: str) -> bool:
    # DEX is UTF-8; React/Hermes bundles may preserve selected UI text as UTF-16LE.
    return text.encode('utf-8') in payload or text.encode('utf-16le') in payload


def class_block(dump: str, descriptor: str) -> str:
    starts = [match.start() for match in re.finditer(r'(?m)^Class #\d+', dump)] + [len(dump)]
    for left, right in zip(starts, starts[1:]):
        block = dump[left:right]
        if f"Class descriptor  : '{descriptor}'" in block:
            return block
    raise AssertionError(f'COMPILED_CLASS_NOT_FOUND:{descriptor}')


def method_block(block: str, name: str) -> str:
    starts = [match.start() for match in re.finditer(r'(?m)^\s*name\s*: ', block)] + [len(block)]
    for left, right in zip(starts, starts[1:]):
        candidate = block[left:right]
        if re.search(rf"(?m)^\s*name\s*:\s*['\"]?{re.escape(name)}['\"]?\s*$", candidate):
            return candidate
    raise AssertionError(f'COMPILED_METHOD_NOT_FOUND:{name}')


def component_blocks(xmltree: str, tag: str, required_name: str) -> list[str]:
    """Extract one compiled manifest component from `aapt dump xmltree` text."""
    lines = xmltree.splitlines(keepends=True)
    result: list[str] = []
    for index, line in enumerate(lines):
        match = re.match(r'^(\s*)E: ' + re.escape(tag) + r'\b', line)
        if not match:
            continue
        indent = len(match.group(1))
        end = len(lines)
        for cursor in range(index + 1, len(lines)):
            sibling = re.match(r'^(\s*)E: \S+', lines[cursor])
            if sibling and len(sibling.group(1)) <= indent:
                end = cursor
                break
        candidate = ''.join(lines[index:end])
        if required_name in candidate:
            result.append(candidate)
    return result


def attr_is(block: str, attribute: str, value: str) -> bool:
    """Accept aapt's Raw form and its compiled scalar form for this exact attribute."""
    line = next((row for row in block.splitlines() if f'android:{attribute}' in row), None)
    if line is None:
        return False
    if f'(Raw: "{value}")' in line or f'="{value}"' in line:
        return True
    if value == 'true':
        return bool(re.search(r'0xffffffff\b', line, re.I))
    if value == 'false':
        return bool(re.search(r'(?:0x0\b|0x00000000\b)', line, re.I))
    # Android's compiled manifest stores these enums/flags as exact integers,
    # not Raw strings: ActivityInfo LAUNCH_SINGLE_TASK=2, portrait=1, and
    # CONFIG_ORIENTATION|CONFIG_SCREEN_SIZE=0x80|0x400. Reject other scalars.
    scalars = {('launchMode', 'singleTask'): 2,
               ('screenOrientation', 'portrait'): 1,
               ('configChanges', 'orientation|screenSize'): 0x480}
    expected = scalars.get((attribute, value))
    encoded = re.search(r'=\(type 0x1[01]\)(0x[0-9a-f]+)\s*$', line, re.I)
    if expected is not None and encoded:
        return int(encoded.group(1), 16) == expected
    return False


def require_attr(block: str, attribute: str, value: str, component: str) -> None:
    assert attr_is(block, attribute, value), f'MANIFEST_{component}_{attribute}_NOT_EXACT:{value}'


_DEX_DISASSEMBLY_BY_CONTENT: dict[str, str] = {}


def dump_class_for_descriptor(dex_by_name: dict[str, bytes], descriptor: str) -> tuple[str, str]:
    """Locate exactly one signed-DEX definition, then decode its exact owner once.

    Cache keys are SHA-256 of the actual DEX bytes, never a path, file name,
    claimed descriptor or preceding APK. All class/method/predicate parsers
    remain unchanged. Missing/duplicate definitions still fail closed.
    """
    owners: list[tuple[str, bytes]] = []
    definitions = 0
    for dex_name, dex_bytes in dex_by_name.items():
        count = dex_classes(dex_bytes).count(descriptor)
        definitions += count
        if count:
            owners.append((dex_name, dex_bytes))
    assert definitions == 1 and len(owners) == 1, f'COMPILED_CLASS_DEX_LOCATION_NOT_UNIQUE:{descriptor}'
    dex_name, dex_bytes = owners[0]
    key = hashlib.sha256(dex_bytes).hexdigest()
    if key not in _DEX_DISASSEMBLY_BY_CONTENT:
        print(f'COMPILED_DEX_INSPECTION decoding unique owner {dex_name}', flush=True)
        with tempfile.NamedTemporaryFile(suffix='.dex') as temporary:
            temporary.write(dex_bytes)
            temporary.flush()
            _DEX_DISASSEMBLY_BY_CONTENT[key] = invoke(TOOLS / 'dexdump', '-d', temporary.name)
    return dex_name, class_block(_DEX_DISASSEMBLY_BY_CONTENT[key], descriptor)


def require_methods(block: str, names: tuple[str, ...], descriptor: str) -> dict[str, str]:
    return {name: method_block(block, name) for name in names}


def has_int_one(method: str) -> bool:
    # `SQLiteDatabase.OPEN_READONLY` is the Android constant integer 1.  It must appear in
    # the same compiled method as the unique SQLiteDatabase.openDatabase call below.
    return bool(re.search(r'(?:#int\s+1\b|\b0x0*1\b)', method, re.I))


def has_pre28_running_app_processes_branch(method: str) -> bool:
    """Require the actual DEX getter invocation for Kotlin's runningAppProcesses property."""
    return (
        'Landroid/app/ActivityManager;' in method
        and 'Landroid/app/ActivityManager;.getRunningAppProcesses:()Ljava/util/List;' in method
    )


def method_or_empty(block: str, name: str) -> str:
    """Return one exact compiled method block, or empty for a fail-closed report row."""
    try:
        return method_block(block, name)
    except AssertionError:
        return ''


def count_token(payload: str, token: str) -> int:
    """Count an exact compiled spelling without inferring source-level behavior."""
    return payload.count(token)


def exact_method_signature_or_empty(block: str, name: str, signature: str) -> str:
    """Select one exact overload; never the first generic bridge or a near-match."""
    starts = [match.start() for match in re.finditer(r'(?m)^\s*name\s*: ', block)] + [len(block)]
    matches = []
    for left, right in zip(starts, starts[1:]):
        candidate = block[left:right]
        named = re.search(rf"(?m)^\s*name\s*:\s*['\"]?{re.escape(name)}['\"]?\s*$", candidate)
        typed = re.search(rf"(?m)^\s*type\s*:\s*['\"]{re.escape(signature)}['\"]\s*$", candidate)
        if named and typed:
            matches.append(candidate)
    return matches[0] if len(matches) == 1 else ''


def cleanup_bridge_invokes_typed_callback(block: str, name: str, typed: str) -> bool:
    erased = typed.replace('Ljava/nio/file/Path;', 'Ljava/lang/Object;', 1)
    bridge = exact_method_signature_or_empty(block, name, erased)
    return all(token in bridge for token in (
        'BRIDGE SYNTHETIC', 'check-cast', 'Ljava/nio/file/Path;',
        SCRATCH_CLEANUP_VISITOR + '.' + name + ':' + typed,
        'invoke-virtual',
    ))


def scratch_predicate_report(
    scratch_class: str, cleanup_visitor: str, exporter_class: str, runtime: bytes
) -> dict[str, bool]:
    """Return deterministic, method-local Phone65 scratch-space predicates.

    Each row names the exact signed-Dex representation expected from the approved source
    correction. An absent/renamed/unknown representation is false and the caller stops; no
    source-level or APK-wide-string substitute is accepted for a method-local predicate.
    """
    constructor = method_or_empty(scratch_class, '<init>')
    ensure_created = method_or_empty(scratch_class, 'ensureCreated')
    write_new = method_or_empty(scratch_class, 'writeNew')
    publish_new = method_or_empty(scratch_class, 'publishNew')
    cleanup = method_or_empty(scratch_class, 'cleanup')
    require_directory = method_or_empty(scratch_class, 'requireDirectory')
    copy_original = method_or_empty(exporter_class, 'copyOriginalSelectedReport')
    copy_snapshot = method_or_empty(exporter_class, 'copySelectedSourceSnapshot')
    copy_exact = method_or_empty(exporter_class, 'copyExact')
    write_scratch = method_or_empty(exporter_class, 'writeScratchBytes')
    prepare = method_or_empty(exporter_class, 'prepare')
    path_attributes = '(Ljava/nio/file/Path;Ljava/nio/file/attribute/BasicFileAttributes;)Ljava/nio/file/FileVisitResult;'
    path_exception = '(Ljava/nio/file/Path;Ljava/io/IOException;)Ljava/nio/file/FileVisitResult;'
    visitor_pre = exact_method_signature_or_empty(cleanup_visitor, 'preVisitDirectory', path_attributes)
    visitor_file = exact_method_signature_or_empty(cleanup_visitor, 'visitFile', path_attributes)
    visitor_post = exact_method_signature_or_empty(cleanup_visitor, 'postVisitDirectory', path_exception)
    no_follow = 'Ljava/nio/file/LinkOption;.NOFOLLOW_LINKS:'
    require_member_call = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.requireMember:'
    ensure_call = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.ensureCreated:'
    write_call = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.writeNew:'
    publish_call = 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.publishNew:'
    return {
        'scratchClassPresent': bool(scratch_class),
        # The supplied cache leaf must itself be non-link/NOFOLLOW and then canonicalised once.
        'cacheToRealPathAndLeafNoFollow': all(token in constructor for token in (
            'Ljava/nio/file/Files;.isSymbolicLink:',
            'Ljava/nio/file/Files;.isDirectory:',
            'Ljava/nio/file/Path;.toRealPath:',
            no_follow,
        )),
        # Existing cache/scratch/run leaves are rechecked as non-link directories and may not
        # change canonical identity. This is the source-matched managed-link rejection.
        'rejectLeafAndManagedLinks': all(token in require_directory for token in (
            'Ljava/nio/file/Files;.isSymbolicLink:',
            'Ljava/nio/file/Files;.isDirectory:',
            'Ljava/nio/file/Path;.toRealPath:',
            no_follow,
        )) and 'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.requireDirectory:' in ensure_created,
        'ensureCreatedSingleLevelCollisionRecheck': all(token in ensure_created for token in (
            'Ljava/nio/file/Files;.createDirectory:',
            'Ljava/nio/file/FileAlreadyExistsException;',
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.requireDirectory:',
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.validateRun:',
        )),
        'writeNewCreateNewNoFollowDurableAndRechecked': all(token in write_new for token in (
            'Ljava/nio/channels/FileChannel;.open:',
            'Ljava/nio/file/StandardOpenOption;.CREATE_NEW:',
            'Ljava/nio/file/StandardOpenOption;.WRITE:',
            no_follow,
            'Ljava/nio/channels/FileChannel;.force:',
        )) and count_token(write_new, require_member_call) >= 2,
        'publishNewCollisionGuardAndRecheck': (
            'Ljava/nio/file/Files;.move:' in publish_new
            and count_token(publish_new, require_member_call) >= 3
        ),
        # Two-argument walkFileTree has no FOLLOW_LINKS option. Visitor methods must constrain
        # cleanup to runRoot and delete only encountered children/root, never cache/parent.
        'boundedCleanupNoRootFollow': all(token in cleanup for token in (
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.validateRun:',
            'Ljava/nio/file/Files;.walkFileTree:(Ljava/nio/file/Path;Ljava/nio/file/FileVisitor;)',
            SCRATCH_CLEANUP_VISITOR,
        )) and all(token in (visitor_pre + visitor_file + visitor_post) for token in (
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;',
            'Ljava/nio/file/Files;.delete:',
        )) and all(cleanup_bridge_invokes_typed_callback(cleanup_visitor, name, signature)
                   for name, signature in (('preVisitDirectory', path_attributes),
                                           ('visitFile', path_attributes),
                                           ('postVisitDirectory', path_exception)))
          and all('EXPORT_SCRATCH_CLEANUP_FAILED' in method
                  and '.access$getRunRoot$p:' in method
                  and 'Lkotlin/jvm/internal/Intrinsics;.areEqual:' in method
                  and 'if-eqz' in method
                  for method in (visitor_pre, visitor_file, visitor_post))
          and 'Ljava/nio/file/Path;.getParent:' in visitor_file
          and 'Ljava/nio/file/Files;.delete:' in visitor_file
          and 'Ljava/nio/file/Files;.delete:' in visitor_post
          and '.access$validateRun:' in visitor_post,
        'exporterUsesScratchSpaceNotOldAncestorRoutine': all(token in prepare for token in (
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.<init>:',
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;.cleanup:',
        )) and ensure_call in copy_original and ensure_call in copy_snapshot
          and write_call in copy_exact and publish_call in copy_exact and write_call in write_scratch
          and 'requireNoSymlinkAncestors' not in exporter_class,
        'scratchFailureTermsPresent': all(contains_text(runtime, term) for term in (
            'EXPORT_SCRATCH_ROOT_UNSAFE', 'EXPORT_SCRATCH_MEMBER_UNSAFE',
            'EXPORT_SCRATCH_COLLISION', 'EXPORT_SCRATCH_WRITE_FAILED',
            'EXPORT_SCRATCH_PUBLICATION_FAILED', 'EXPORT_SCRATCH_CLEANUP_FAILED',
        )),
    }


def require_scratch_predicates(report: dict[str, bool]) -> None:
    failures = [name for name, passed in report.items() if not passed]
    assert not failures, 'SCRATCH_DEX_REPRESENTATION_UNSUPPORTED_OR_PREDICATE_MISSING:' + ','.join(failures)


def write_text(path: Path, contents: str) -> None:
    path.write_text(contents, encoding='utf-8')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path, help='candidate Phone65 APK; inspected read-only')
    parser.add_argument('--expected-version-name', default=VERSION_NAME,
                        help='fixed Phone65 versionName; any other value is rejected')
    parser.add_argument('--out', type=Path, required=True,
                        help='new/reusable validation output directory (not an app-private root)')
    args = parser.parse_args()

    apk = args.apk.resolve()
    out = args.out.resolve()
    if not apk.is_file():
        raise SystemExit('APK_NOT_FOUND')
    if args.expected_version_name != VERSION_NAME:
        raise SystemExit('PHONE65_EXPECTED_VERSION_NAME_MISMATCH')
    for tool in ('aapt', 'aapt2', 'apksigner', 'zipalign', 'dexdump'):
        assert (TOOLS / tool).is_file(), f'INSPECTION_TOOL_MISSING:{tool}'
    out.mkdir(parents=True, exist_ok=True)

    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    badging = invoke(TOOLS / 'aapt2', 'dump', 'badging', apk)
    certs = invoke(TOOLS / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    xmltree = invoke(TOOLS / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
    expected_badge = (
        f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' "
        f"versionName='{VERSION_NAME}'"
    )
    assert expected_badge in badging, 'PHONE65_PACKAGE_VERSION_IDENTITY_MISMATCH'
    assert "sdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging, 'SDK_IDENTITY_MISMATCH'
    assert f'certificate SHA-256 digest: {SIGNER}' in certs, 'SIGNER_MISMATCH'
    invoke(TOOLS / 'zipalign', '-c', '-p', '4', apk)

    # Exact compiled manifest component contract.  This is deliberately component-scoped:
    # it makes no claims about unrelated app components or Android Application lifecycle.
    activity_blocks = component_blocks(xmltree, 'activity', 'SelectedCompletedRunExportActivity')
    provider_blocks = component_blocks(xmltree, 'provider', 'SelectedRunExportProvider')
    assert len(activity_blocks) == 1, 'SELECTED_EXPORT_ACTIVITY_MANIFEST_COUNT_NOT_ONE'
    assert len(provider_blocks) == 1, 'SELECTED_EXPORT_PROVIDER_MANIFEST_COUNT_NOT_ONE'
    activity_manifest, provider_manifest = activity_blocks[0], provider_blocks[0]
    for attribute, value in (
        ('name', 'health.hugr.sourcejournal.SelectedCompletedRunExportActivity'),
        ('exported', 'true'), ('label', 'HUGR Selected Completed-Run Export'),
        ('launchMode', 'singleTask'), ('taskAffinity', 'health.hugr.app.selectedRunExport'),
        ('screenOrientation', 'portrait'), ('configChanges', 'orientation|screenSize'),
        ('process', ':selectedRunExport'),
    ):
        require_attr(activity_manifest, attribute, value, 'SELECTED_ACTIVITY')
    assert activity_manifest.count('E: intent-filter') == 1, 'SELECTED_ACTIVITY_LAUNCHER_FILTER_COUNT_NOT_ONE'
    assert 'android.intent.action.MAIN' in activity_manifest, 'SELECTED_ACTIVITY_MAIN_MISSING'
    assert 'android.intent.category.LAUNCHER' in activity_manifest, 'SELECTED_ACTIVITY_LAUNCHER_MISSING'
    for attribute, value in (
        ('name', 'health.hugr.sourcejournal.SelectedRunExportProvider'),
        ('authorities', 'health.hugr.app.selectedRunExport'), ('exported', 'false'),
        ('process', ':selectedRunExport'), ('grantUriPermissions', 'true'),
    ):
        require_attr(provider_manifest, attribute, value, 'SELECTED_PROVIDER')
    # The narrow provider declares no generic read/write permission or path/tree grant surface.
    for forbidden in ('android:permission', 'android:readPermission', 'android:writePermission',
                      'E: path-permission', 'E: grant-uri-permission'):
        assert forbidden not in provider_manifest, f'BROAD_PROVIDER_MANIFEST_SURFACE:{forbidden}'
    assert 'FileProvider' not in provider_manifest, 'BROAD_FILEPROVIDER_SUBSTITUTION'
    write_text(out / 'compiled-android-manifest.xmltree.txt', xmltree)
    write_text(out / 'apk-badging.txt', badging)
    write_text(out / 'apk-signer.txt', certs)

    definitions: list[str] = []
    runtime = b''
    dex_by_name: dict[str, bytes] = {}
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None, 'APK_ZIP_CRC_FAILURE'
        names = archive.namelist()
        assert len(names) == len(set(names)), 'APK_DUPLICATE_ZIP_ENTRY'
        for name in names:
            if re.fullmatch(r'classes\d*\.dex', name):
                payload = archive.read(name)
                dex_by_name[name] = payload
                definitions.extend(dex_classes(payload))
                runtime += payload
            elif name.endswith('.bundle'):
                runtime += archive.read(name)
    assert dex_by_name, 'APK_DEX_MISSING'
    assert len(definitions) == len(set(definitions)), 'DUPLICATE_DEX_CLASS_DEFINITION'

    # Retain the Phone63 production classes and add the exact selected-export native definitions.
    required_classes = (
        APPLICATION, SOURCE_EVIDENCE_ACTIVITY, WRITER,
        SELECTED_ACTIVITY, SELECTED_EXPORTER, SELECTED_PROVIDER, SELECTED_REGISTRY,
        'Lhealth/hugr/sourcejournal/DurationLadderPolicy;', DURATION_CURSOR,
        'Lhealth/hugr/sourcejournal/SQLiteSelectedSnapshotRecordStream;',
        'Lhealth/hugr/sourcejournal/PhoneCustodyRunReportStore;',
        'Lhealth/hugr/sourcejournal/WatchRunReceipt;',
        'Lhealth/hugr/sourcejournal/PhoneWatchReceiptBridgeKt;',
        'Lhealth/hugr/sourcejournal/WatchRunReceiptParser;',
        SCRATCH_SPACE, SCRATCH_CLEANUP_VISITOR,
    )
    for descriptor in required_classes:
        assert definitions.count(descriptor) == 1, f'MISSING_OR_DUPLICATE_REQUIRED_CLASS:{descriptor}'

    # Keep all earlier duration-ladder compiled terms.  The API-28/8-MiB local signed-DEX proof
    # is performed below, rather than treating source or an APK-wide string as proof.
    duration_terms = (
        'HUGR_RUN_RECEIPT_V2', 'configureSelectedRecordCursorWindow',
        'SNAPSHOT_CURSOR_WINDOW_ALLOCATION_OR_CONFIGURATION_FAILURE', 'hugr-selected-record',
        'HistoricalReportChainStamp', 'ParsedReceiptCacheEntry', 'native_verified_manifest',
        'verifiedCustodyReportInputFromStableSelectedSourceSnapshot',
        'SOURCE_CHANGED_DURING_VERIFICATION', 'DURATION_OR_CANCELLATION_BUDGET_EXCEEDED',
        'MAX_DURATION_MS', 'Hermes',
    )
    missing_duration_terms = [term for term in duration_terms if not contains_text(runtime, term)]
    assert not missing_duration_terms, 'MISSING_COMPILED_DURATION_TERMS:' + ','.join(missing_duration_terms)
    # Retain the prior Phone63 source-evidence/report/receipt integration predicates.  These are
    # package-wide signed-runtime terms, intentionally separate from the new exporter contract.
    inherited_phone63_terms = (
        'HUGR_RUN_RECEIPT_V1', 'pendingReceipt', 'persistWatchRunReceipt',
        'getSourceResumeStateForSession',
        'Indexed accepted source bytes missing; no append or resume allowed', 'WATCH_RECEIPT',
        'No current live beats', 'LAST SEGMENT ACK EVENT', 'SAVED RUN:',
        '99999999-9999-4999-8999-999999999999',
        'finishWithCleanup',
        'SOURCE_INGESTION_FAILURE', 'SOURCE_SUBSCRIPTION_FAILURE',
        'SOURCE_NOTIFY_TRANSPORT_ENDED', 'SOURCE_MANIFEST_IDENTITY_REJECTED',
        'Critical manifest range identity collision at ',
    )
    missing_inherited_phone63_terms = [term for term in inherited_phone63_terms if not contains_text(runtime, term)]
    assert not missing_inherited_phone63_terms, 'MISSING_INHERITED_PHONE63_COMPILED_TERMS:' + ','.join(missing_inherited_phone63_terms)

    # Exact selected exporter semantics represented in compiled runtime bytes.  Individual
    # function-local proofs below establish the provider mode/path, read-only DB, and lifecycle.
    exporter_terms = (
        'HUGR_SELECTED_MANIFESTS_V1', 'HUGR_SELECTED_RUN_EXPORT_V1',
        'EXPORT_SELECTED_SOURCE_INVALID', 'EXPORT_SELECTED_RUN_INVALID',
        'EXPORT_SELECTED_REPORT_UNAVAILABLE', 'EXPORT_SELECTED_SOURCE_REPORT_MISMATCH',
        'EXPORT_SELECTED_RUN_MISMATCH', 'EXPORT_RUN_NOT_COMPLETED',
        'EXPORT_RUN_NOT_COMPLETE_VERIFIED', 'EXPORT_PHONE_CUSTODY_UNAVAILABLE',
        'EXPORT_WATCH_RECEIPT_MISSING', 'EXPORT_WATCH_CUSTODY_FAILURE',
        'EXPORT_RECEIPT_REPORT_INVENTORY_MISMATCH', 'EXPORT_RECEIPT_ACK_INVENTORY_MISMATCH',
        'EXPORT_SELECTED_REPORT_HASH_MISMATCH', 'EXPORT_ORIGINAL_REPORT_BODY_HASH_MISMATCH',
        'EXPORT_ORIGINAL_RECEIPT_INTEGRITY_MISMATCH',
        'EXPORT_PERSISTED_MANIFEST_TUPLE_MISMATCH', 'EXPORT_FRESH_VERIFICATION_REPORT_MISMATCH',
        'EXPORT_PENDING_APPEND_PRESENT', 'EXPORT_REPORT_PUBLICATION_PENDING',
        'EXPORT_DATABASE_ROLLBACK_JOURNAL_PRESENT', 'EXPORT_DATABASE_WAL_SHM_SET_INCOMPLETE',
        'EXPORT_SOURCE_CHANGED_DURING_SNAPSHOT', 'EXPORT_REPORT_CHANGED_DURING_SNAPSHOT',
        'EXPORT_SOURCE_CHANGED_DURING_PREPARATION', 'EXPORT_SNAPSHOT_DATABASE_OPEN_FAILED',
        'EXPORT_PACKAGE_BYTE_BUDGET_EXCEEDED', 'EXPORT_PACKAGE_IO_DURATION_OR_CANCELLATION_BUDGET_EXCEEDED',
        'full_parent_no_omissions_no_reorder_no_record_reserialization',
        'canonical.bin', 'manifests.json', 'watch-receipt.txt', 'phone-report.hugr-report',
        'provenance.json', 'README.md', 'package.json', 'checksums.sha256',
    )
    missing_exporter_terms = [term for term in exporter_terms if not contains_text(runtime, term)]
    assert not missing_exporter_terms, 'MISSING_COMPILED_SELECTED_EXPORT_TERMS:' + ','.join(missing_exporter_terms)

    # Preserve the pre-existing sourceEvidence Application guard exactly as a separate fact.
    # It must never be interpreted as a :selectedRunExport guard; the plugin names only
    # :sourceEvidence and leaves ordinary Application startup unchanged.
    assert contains_text(runtime, 'isHugrSourceEvidenceProcess'), 'SOURCE_EVIDENCE_APPLICATION_GUARD_MISSING'
    source_activity_blocks = component_blocks(xmltree, 'activity', 'HUGRSavedSourceEvidenceActivity')
    assert len(source_activity_blocks) == 1, 'SOURCE_EVIDENCE_ACTIVITY_MANIFEST_COUNT_NOT_ONE'
    require_attr(source_activity_blocks[0], 'process', ':sourceEvidence', 'SOURCE_EVIDENCE_ACTIVITY')

    # Exclude test source sets/frameworks from the *packaged* production runtime.  This extends,
    # rather than weakens, the Phone63 exclusion list with the selected-export test fixture.
    project_test_suffixes = (
        'ReportDirectoryLinuxShadow;', 'PhoneCanonicalEvidenceStreamingTest;',
        'BoundedCursorWindowCapacityTest;', 'BoundedSnapshotCursorProjectionTest;',
        'CanonicalBufferedReadBoundaryTest;', 'DurationLadderPolicyTest;',
        'EightHourTerminalCapacityTest;', 'HUGRSavedSourceEvidenceActivityTest;',
        'PendingAppendJournalTest;', 'PhoneCanonicalEvidenceCoreTest;',
        'PhoneCustodyRunReportStoreTest;', 'PhoneDurableRunOfflineIntegrationTest;',
        'PhoneProductionCustodyIntegrationTest;', 'PhoneWatchReceiptProductionIntegrationTest;',
        'SourceGapTrackerTest;', 'TerminalVerifierPassMeasurementTest;',
        'V2PhoneAckReportBoundaryTest;', 'VerifiedReportChainStampTest;', 'WatchRunReceiptTest;',
        'SelectedCompletedRunExportTest;', 'SelectedRunExportScratchSpaceTest;',
    )
    framework_prefixes = ('Lorg/robolectric/', 'Lorg/junit/', 'Ljunit/', 'Landroidx/test/', 'Lorg/mockito/')
    packaged_tests = [descriptor for descriptor in definitions
                      if descriptor.startswith(framework_prefixes) or descriptor.endswith(project_test_suffixes)]
    packaged_framework_strings = [prefix for prefix in framework_prefixes if prefix.encode() in runtime]
    assert not packaged_tests, 'PACKAGED_TEST_CLASS:' + ','.join(packaged_tests)
    assert not packaged_framework_strings, 'PACKAGED_TEST_RUNTIME:' + ','.join(packaged_framework_strings)

    # Actual direct superclasses and function-local compiled-code proofs.
    application_dex, application = dump_class_for_descriptor(dex_by_name, APPLICATION)
    source_activity_dex, source_activity = dump_class_for_descriptor(dex_by_name, SOURCE_EVIDENCE_ACTIVITY)
    activity_dex, activity = dump_class_for_descriptor(dex_by_name, SELECTED_ACTIVITY)
    exporter_dex, exporter = dump_class_for_descriptor(dex_by_name, SELECTED_EXPORTER)
    provider_dex, provider = dump_class_for_descriptor(dex_by_name, SELECTED_PROVIDER)
    registry_dex, registry = dump_class_for_descriptor(dex_by_name, SELECTED_REGISTRY)
    cursor_dex, cursor = dump_class_for_descriptor(dex_by_name, DURATION_CURSOR)
    scratch_dex, scratch_class = dump_class_for_descriptor(dex_by_name, SCRATCH_SPACE)
    scratch_visitor_dex, scratch_visitor = dump_class_for_descriptor(dex_by_name, SCRATCH_CLEANUP_VISITOR)
    assert "Superclass        : 'Landroid/app/Activity;'" in activity, 'SELECTED_ACTIVITY_NOT_DIRECT_ANDROID_ACTIVITY'
    assert "Superclass        : 'Landroid/app/Activity;'" in source_activity, 'SOURCE_EVIDENCE_ACTIVITY_NOT_DIRECT_ANDROID_ACTIVITY'
    assert "Superclass        : 'Landroid/content/ContentProvider;'" in provider, 'SELECTED_PROVIDER_NOT_DIRECT_CONTENT_PROVIDER'
    assert "Superclass        : 'Landroid/app/Application;'" in application, 'MAIN_APPLICATION_SUPERCLASS_MISMATCH'

    guard_method = method_block(application, 'isHugrSourceEvidenceProcess')
    assert 'Landroid/os/Build$VERSION;' in guard_method and 'SDK_INT' in guard_method, 'SOURCE_EVIDENCE_GUARD_API_BRANCH_MISSING'
    # Kotlin source property `runningAppProcesses` compiles to this Android getter invoke.
    # Do not search signed DEX for source-property spelling; require the precise invoke owner/name.
    assert has_pre28_running_app_processes_branch(guard_method), 'SOURCE_EVIDENCE_GUARD_PRE28_BRANCH_MISSING'
    assert ':sourceEvidence' in guard_method, 'SOURCE_EVIDENCE_GUARD_SCOPE_MISSING'
    assert ':selectedRunExport' not in guard_method, 'UNEXPECTED_SELECTED_RUN_APPLICATION_GUARD_CLAIM'
    # This is an affirmative boundary, not a failure: :selectedRunExport does *not* take the
    # retained :sourceEvidence early return. Its Application onCreate therefore performs the
    # ordinary React-native/Expo native bootstrap and lifecycle dispatch. The selected Activity
    # is nevertheless a direct android.app.Activity, not a ReactActivity. Do not misdescribe
    # this component isolation as an Application-process guard or as zero Application startup.
    application_on_create = method_block(application, 'onCreate')
    assert 'Lcom/facebook/react/ReactNativeApplicationEntryPoint;.loadReactNative:' in application_on_create, 'NORMAL_APPLICATION_REACT_NATIVE_BOOTSTRAP_MISSING'
    assert 'Lexpo/modules/ApplicationLifecycleDispatcher;.onApplicationCreate:' in application_on_create, 'NORMAL_APPLICATION_EXPO_LIFECYCLE_DISPATCH_MISSING'
    # Direct-call proof only. It establishes no direct MainApplication edge to these HUGR paths;
    # it does not claim that generic Expo package lifecycle behavior is absent.
    direct_application_unsafe_refs = tuple(token for token in (
        'Lhealth/hugr/sourcejournal/PhoneSourceJournal;',
        'Lhealth/hugr/sourcejournal/HUGRSourceJournalModule;',
        'recoverPendingAppends', 'Lcom/bleplx/', 'Landroid/bluetooth/',
    ) if token in application_on_create)
    assert not direct_application_unsafe_refs, 'DIRECT_APPLICATION_UNSAFE_DEPENDENCY:' + ','.join(direct_application_unsafe_refs)

    activity_methods = require_methods(activity, (
        'prepareSelected', 'createDocument', 'sharePrivately', 'onActivityResult',
        'onDestroy', 'writePreparedToDestination', 'discardPreparedArtifact',
    ), SELECTED_ACTIVITY)
    exporter_methods = require_methods(exporter, (
        'prepare', 'validateCompletedSelection', 'validateFreshCustody',
        'verifyCopiedSelectedSource', 'rejectPendingOrUnsafeSource',
        'rejectPendingOrUnsafeReports', 'copyOriginalSelectedReport',
        'decodeOriginalReceipt', 'renderManifests', 'renderPackageJson', 'writeExactZip',
    ), SELECTED_EXPORTER)
    provider_methods = require_methods(provider, ('getType', 'openFile', 'query', 'insert', 'delete', 'update'), SELECTED_PROVIDER)
    registry_methods = require_methods(registry, ('register', 'resolve', 'revoke', 'cleanExpired', 'expire'), SELECTED_REGISTRY)

    readonly_db_method = exporter_methods['verifyCopiedSelectedSource']
    assert 'Landroid/database/sqlite/SQLiteDatabase;.openDatabase:' in readonly_db_method, 'READONLY_DB_OPEN_CALL_MISSING'
    assert has_int_one(readonly_db_method), 'READONLY_DB_OPEN_FLAG_ONE_MISSING'
    assert 'Landroid/database/sqlite/SQLiteDatabase;.close:' in readonly_db_method, 'READONLY_DB_CLOSE_MISSING'
    assert 'getWritableDatabase' not in exporter and 'OPEN_READWRITE' not in exporter, 'WRITABLE_DATABASE_REFERENCE_IN_EXPORTER'
    assert 'copyExact' in exporter_methods['copyOriginalSelectedReport'], 'ORIGINAL_REPORT_EXACT_COPY_CALL_MISSING'
    assert 'sha256File' in exporter_methods['copyOriginalSelectedReport'], 'ORIGINAL_REPORT_HASH_CHECK_MISSING'
    # `decodeOriginalReceipt` validates the envelope/base64/hash before returning original bytes.
    # The mandatory parse-and-byte-for-byte-reserialize check occurs in `validateFreshCustody`,
    # after the copied snapshot's fresh custody proof is available; require it at that true DEX site.
    assert 'WatchRunReceiptParser;.parse:' in exporter_methods['validateFreshCustody'], 'ORIGINAL_RECEIPT_PARSE_INTEGRITY_CHECK_MISSING'
    assert 'WatchRunReceiptParser;.sha256:' in exporter_methods['decodeOriginalReceipt'], 'ORIGINAL_RECEIPT_HASH_CHECK_MISSING'
    assert 'JSONArray' in exporter_methods['renderManifests'], 'MANIFEST_SIDECAR_RENDER_MISSING'
    assert 'canonical_record_count' in exporter_methods['renderPackageJson'], 'PACKAGE_RECORD_COUNT_RENDER_MISSING'

    # The actual private Activity has distinct save and share paths. `FLAG_GRANT_READ_URI_PERMISSION`
    # compiles to an integer; the exact provider's mode/path code and the compiled UI text below
    # provide the complementary signed-byte proof rather than inferring a broad URI grant.
    assert 'Landroid/content/Intent;.createChooser:' in activity_methods['sharePrivately'], 'SHARE_CHOOSER_CALL_MISSING'
    assert 'Landroid/content/ClipData;.newRawUri:' in activity_methods['sharePrivately'], 'SHARE_EXACT_CLIP_URI_MISSING'
    assert 'Landroid/content/Intent;.addFlags:' in activity_methods['sharePrivately'], 'SHARE_READ_GRANT_FLAG_CALL_MISSING'
    assert 'Landroid/content/Intent;.addFlags:' in activity_methods['createDocument'], 'SAVE_DOCUMENT_WRITE_GRANT_CALL_MISSING'
    assert 'Landroid/content/ContentResolver;.openOutputStream:' in activity_methods['writePreparedToDestination'], 'SAVE_DESTINATION_WRITE_MISSING'
    # The inherited Activity method is invoked under the concrete Activity's DEX owner here;
    # require its exact URI/flag signature rather than a source-level superclass owner spelling.
    assert re.search(
        r'L(?:android/app/Activity|health/hugr/sourcejournal/SelectedCompletedRunExportActivity);'
        r'\.revokeUriPermission:\(Landroid/net/Uri;I\)V',
        activity_methods['discardPreparedArtifact'],
    ), 'EXACT_URI_REVOCATION_MISSING'
    assert 'Ljava/util/concurrent/ExecutorService;.shutdownNow:' in activity_methods['onDestroy'], 'DESTROY_WORKER_CANCELLATION_MISSING'
    assert 'discardPreparedArtifact' in activity_methods['onActivityResult'], 'SAVE_CANCEL_PRIVATE_CLEANUP_MISSING'

    assert re.search(r'const-string\s+v\d+, "r"', provider_methods['openFile']), 'PROVIDER_READ_MODE_LITERAL_MISSING'
    assert 'Landroid/os/ParcelFileDescriptor;.open:' in provider_methods['openFile'], 'PROVIDER_READONLY_OPEN_MISSING'
    # OpenableColumns.DISPLAY_NAME/SIZE are final Java String constants, so DEX inlines their
    # exact API values rather than retaining an OpenableColumns owner reference.  Both values
    # are required together; the query's rejection string is separately required below.
    assert '"_display_name"' in provider_methods['query'] and '"_size"' in provider_methods['query'], 'PROVIDER_NARROW_METADATA_MISSING'
    assert 'resolve' in provider_methods['getType'] and 'resolve' in provider_methods['openFile'], 'PROVIDER_EXACT_URI_RESOLUTION_MISSING'
    assert 'Landroid/net/Uri;.getScheme:' in registry_methods['resolve'], 'EXACT_URI_SCHEME_GUARD_MISSING'
    assert 'Landroid/net/Uri;.getAuthority:' in registry_methods['resolve'], 'EXACT_URI_AUTHORITY_GUARD_MISSING'
    assert 'Landroid/net/Uri;.getPathSegments:' in registry_methods['resolve'], 'EXACT_URI_PATH_GUARD_MISSING'
    assert 'Ljava/lang/SecurityException;' in registry_methods['resolve'], 'EXACT_URI_FAIL_CLOSED_EXCEPTION_MISSING'
    assert 'Ljava/util/concurrent/ScheduledExecutorService;.schedule:' in registry_methods['register'], 'EXACT_GRANT_EXPIRY_SCHEDULE_MISSING'
    assert 'cleanExpired' in registry_methods['resolve'], 'EXPIRED_URI_FAIL_CLOSED_CHECK_MISSING'
    assert 'deleteExactFile' in registry_methods['revoke'] and 'deleteExactFile' in registry_methods['expire'], 'GRANT_CLEANUP_DELETE_MISSING'

    # Phone65-only correction proof. These checks are direct compiled method/local-class
    # predicates for the focused cache-scratch lifecycle; they do not inspect private storage.
    scratch_report = scratch_predicate_report(scratch_class, scratch_visitor, exporter, runtime)
    require_scratch_predicates(scratch_report)

    required_ui_provider_terms = (
        'Private explicit export only. Select exact source session and run ID.',
        'No latest fallback. No recovery, BLE, network, automatic upload, or source mutation.',
        'Save cancelled. No external copy was written; prepared private package removed.',
        'Share chooser opened. The exact read-only package URI has a short explicit-share expiry;',
        'Exact selected export artifact URI required', 'Selected export artifact unavailable',
        'Selected export is read-only', 'Only OpenableColumns display name and size are available',
        'Read-only selected export', 'application/zip',
    )
    missing_ui_provider_terms = [term for term in required_ui_provider_terms if not contains_text(runtime, term)]
    assert not missing_ui_provider_terms, 'MISSING_COMPILED_SAVE_SHARE_PROVIDER_TERMS:' + ','.join(missing_ui_provider_terms)

    # The original Phone63 signed-method proof: exactly 8 MiB, API >= 28, SQLiteCursor and the
    # (String,long) CursorWindow constructor in configureSelectedRecordCursorWindow.
    cursor_method = method_block(cursor, 'configureSelectedRecordCursorWindow')
    has_8m = bool(re.search(r'8388608|0x00800000|0x800000|#00800000', cursor_method))
    has_28 = bool(re.search(r'(?<!\d)28(?!\d)|0x1c', cursor_method, re.IGNORECASE))
    has_sdk = 'Landroid/os/Build$VERSION;' in cursor_method and 'SDK_INT' in cursor_method
    cursor_proof = {
        'dex': cursor_dex,
        'method': 'configureSelectedRecordCursorWindow',
        'exactWindowBytes8388608': has_8m,
        'apiAtLeast28Branch': has_28 and has_sdk,
        'sdkIntRead': has_sdk,
        'sqliteCursorGuard': 'Landroid/database/sqlite/SQLiteCursor;' in cursor_method,
        'cursorWindowType': 'Landroid/database/CursorWindow;' in cursor_method,
        'cursorWindowName': 'hugr-selected-record' in cursor_method,
        'cursorWindowStringLongConstructor': 'CursorWindow;.<init>:(Ljava/lang/String;J)V' in cursor_method,
    }
    assert all(value for key, value in cursor_proof.items() if key not in ('dex', 'method')), 'CURSOR_WINDOW_DEX_PROOF_FAILED'

    # Store sanitized inspectable proof excerpts; this never writes adjacent to the candidate APK.
    write_text(out / 'application-source-evidence-guard.dexdump.txt', application)
    write_text(out / 'application-on-create.dexdump.txt', application_on_create)
    write_text(out / 'source-evidence-activity.dexdump.txt', source_activity)
    write_text(out / 'selected-export-activity.dexdump.txt', activity)
    write_text(out / 'selected-exporter.dexdump.txt', exporter)
    write_text(out / 'selected-export-provider.dexdump.txt', provider)
    write_text(out / 'selected-export-registry.dexdump.txt', registry)
    write_text(out / 'configure-selected-record-cursor-window.dexdump.txt', cursor_method)
    write_text(out / 'selected-run-export-scratch-space.dexdump.txt', scratch_class)
    write_text(out / 'selected-run-export-scratch-cleanup-visitor.dexdump.txt', scratch_visitor)

    result: dict[str, Any] = {
        'status': 'PASS',
        'scope': 'READ_ONLY_COMPILED_APK_ADMISSION_ONLY',
        'apk': str(apk), 'sha256': digest, 'bytes': apk.stat().st_size,
        'package': PACKAGE, 'versionCode': VERSION_CODE,
        'versionName': VERSION_NAME, 'signerSha256': SIGNER,
        'archive': {'zipCrc': 'PASS', 'pageAlignment': 'PASS', 'duplicateZipEntries': 'PASS'},
        'compiledClasses': len(definitions), 'uniqueClassDefinitions': True,
        'manifest': {
            'selectedActivity': 'PASS', 'launcherMain': 'PASS',
            'selectedProvider': 'PASS', 'sameSelectedRunExportProcess': 'PASS',
            'packageBoundAuthority': 'health.hugr.app.selectedRunExport',
            'providerNonExportedReadGrantNarrowSurface': 'PASS',
        },
        'selectedNativeDefinitions': {
            'activityDirectSuperclassAndroidActivity': 'PASS',
            'providerDirectSuperclassContentProvider': 'PASS',
            'exporter': 'PASS', 'registry': 'PASS',
            'inheritedPhone63ReportReceiptWriterCompatibility': 'PASS',
            'readonlyDisposableDatabase': 'PASS',
            'phone65ScratchSpace': scratch_report,
            'completedPendingAndMutationRefusals': 'PASS',
            'originalFullCopyAndImmutableReceiptReportManifestChecks': 'PASS',
            'explicitSaveShareCancelExpiryModePathGuards': 'PASS',
        },
        'sourceEvidenceApplicationGuard': {
            'compiledAndSourceEvidenceScoped': 'PASS',
            'sourceEvidenceActivityDirectSuperclassAndroidActivity': 'PASS',
            'scope': ':sourceEvidence only; no assertion that it guards :selectedRunExport',
        },
        'selectedRunExportApplicationStartup': {
            'ordinaryApplicationOnCreateRuns': 'PASS',
            'reactNativeNativeBootstrapDirectCall': 'PASS',
            'expoApplicationLifecycleDispatchDirectCall': 'PASS',
            'directMainApplicationWriterPendingRecoveryOrBleReference': 'ABSENT',
            'scope': 'Direct MainApplication onCreate inspection only; generic Expo package lifecycle behavior and JS evaluation are not asserted absent.',
        },
        'durationLadder': {
            'requiredCompiledTerms': list(duration_terms),
            'cursorWindowSignedDexProof': cursor_proof,
            'runtimeTestExclusions': 'PASS',
        },
        'nonclaims': [
            'No build, upload, staging, signing, installation, launch, device, export, record, APK alteration, adjacent APK hash-file write, or app-private-root action occurred.',
            'This proves compiled artifact predicates only; it does not prove Android chooser/provider behavior, an actual save/share, or any real package/export.',
            'Separate selected-process components do not prove an Application-process bypass. The retained Application guard is scoped to :sourceEvidence; this validator makes no :selectedRunExport Application-guard claim.',
            'The selected process executes ordinary MainApplication native React/Expo bootstrap and lifecycle dispatch. This validator does not claim that no app JS or generic Expo lifecycle code can run; it proves only no direct MainApplication writer, pending-recovery, or BLE reference.',
            'No physiological, research, report, canonical-record, receipt, or ZIP-export bytes were opened or inspected.',
        ],
    }
    (out / 'phone65-selected-export-compiled-artifact-proof.json').write_text(
        json.dumps(result, indent=2) + '\n', encoding='utf-8'
    )
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
