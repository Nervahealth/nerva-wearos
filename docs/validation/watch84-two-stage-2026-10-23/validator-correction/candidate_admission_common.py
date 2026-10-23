#!/usr/bin/env python3
"""Shared read-only helpers for proposed Watch84 / Phone66 artifact admission.

This module neither builds, signs, stages, uploads, installs, launches, nor modifies
an APK.  Callers supply a candidate APK and an output directory; only that output
directory and OS temporary files used for read-only dexdump invocation are written.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import time
import zipfile
from typing import Any, Iterable


class AdmissionStop(RuntimeError):
    """Fail closed with a stable public stop token; never include command output."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise AdmissionStop(code)


def sha256_file(path: Path, block_bytes: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(block_bytes), b""):
            digest.update(block)
    return digest.hexdigest()


def contains_text(payload: bytes, text: str) -> bool:
    """DEX strings are UTF-8; selected Hermes assets can retain UTF-16LE strings."""
    return text.encode("utf-8") in payload or text.encode("utf-16le") in payload


def read_uleb(data: bytes, position: int) -> tuple[int, int]:
    value = shift = 0
    while True:
        if position >= len(data):
            raise AdmissionStop("DEX_ULEB_OUT_OF_RANGE")
        byte = data[position]
        position += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, position
        shift += 7
        if shift > 35:
            raise AdmissionStop("DEX_ULEB_INVALID")


def dex_classes(data: bytes) -> list[str]:
    """Read signed DEX descriptors without decoding, transforming, or repacking it."""
    require(data.startswith(b"dex\n"), "APK_DEX_NOT_DEX")
    try:
        string_size, string_offset = struct.unpack_from("<II", data, 56)
        strings: list[str] = []
        for index in range(string_size):
            offset = struct.unpack_from("<I", data, string_offset + 4 * index)[0]
            _, offset = read_uleb(data, offset)
            terminator = data.index(b"\0", offset)
            strings.append(data[offset:terminator].decode("utf-8", errors="replace"))
        type_size, type_offset = struct.unpack_from("<II", data, 64)
        types = [strings[struct.unpack_from("<I", data, type_offset + 4 * index)[0]]
                 for index in range(type_size)]
        class_size, class_offset = struct.unpack_from("<II", data, 96)
        return [types[struct.unpack_from("<I", data, class_offset + 32 * index)[0]]
                for index in range(class_size)]
    except (IndexError, ValueError, struct.error) as error:
        raise AdmissionStop("APK_DEX_DESCRIPTOR_TABLE_INVALID") from error


class Deadline:
    """One total inspection budget; prevents per-class full-APK re-disassembly loops."""

    def __init__(self, seconds: int) -> None:
        require(seconds > 0, "INSPECTION_DEADLINE_INVALID")
        self.seconds = seconds
        self._until = time.monotonic() + seconds

    def remaining(self) -> float:
        remaining = self._until - time.monotonic()
        if remaining <= 0:
            raise AdmissionStop("COMPILED_INSPECTION_DEADLINE_STOP")
        return remaining

    def call(self, command: Iterable[object], environment: dict[str, str]) -> str:
        args = [str(value) for value in command]
        executable = Path(args[0])
        host_is_x86 = os.uname().machine.lower() in ("x86_64", "amd64")
        is_elf = executable.is_file() and executable.read_bytes()[:4] == b"\x7fELF"
        if is_elf and not host_is_x86:
            qemu = shutil.which(environment.get("HUGR_QEMU_X86_64", "qemu-x86_64"))
            sysroot = Path(environment.get("HUGR_X86_64_SYSROOT", ""))
            require(qemu is not None and sysroot.is_dir(), "X86_INSPECTION_TOOL_REQUIRES_QEMU_AND_SYSROOT")
            environment = environment.copy()
            environment["QEMU_LD_PREFIX"] = str(sysroot)
            args = [qemu, *args]
        try:
            output = subprocess.check_output(
                args,
                stderr=subprocess.STDOUT,
                env=environment,
                timeout=max(1, int(self.remaining())),
            )
        except subprocess.TimeoutExpired as error:
            raise AdmissionStop("COMPILED_INSPECTION_DEADLINE_STOP") from error
        except subprocess.CalledProcessError as error:
            # Do not disclose arbitrary artifact/tool output in public receipts.
            raise AdmissionStop("READ_ONLY_INSPECTION_TOOL_STOP") from error
        return output.decode("utf-8", errors="replace")


def class_block(dump: str, descriptor: str) -> str:
    starts = [match.start() for match in re.finditer(r"(?m)^Class #\d+", dump)] + [len(dump)]
    matches = [dump[left:right] for left, right in zip(starts, starts[1:])
               if f"Class descriptor  : '{descriptor}'" in dump[left:right]]
    require(len(matches) == 1, "COMPILED_CLASS_BLOCK_NOT_UNIQUE:" + descriptor)
    return matches[0]


def method_block(block: str, name: str) -> str:
    starts = [match.start() for match in re.finditer(r"(?m)^\s*name\s*:\s*", block)] + [len(block)]
    matches = []
    for left, right in zip(starts, starts[1:]):
        candidate = block[left:right]
        if re.search(rf"(?m)^\s*name\s*:\s*['\"]?{re.escape(name)}['\"]?\s*$", candidate):
            matches.append(candidate)
    require(len(matches) == 1, "COMPILED_METHOD_NOT_UNIQUE_OR_MISSING:" + name)
    return matches[0]


def exact_method_signature(block: str, name: str, signature: str) -> str:
    """Return exactly one name+signature overload; never silently select a bridge."""
    starts = [match.start() for match in re.finditer(r"(?m)^\s*name\s*:\s*", block)] + [len(block)]
    matches = []
    for left, right in zip(starts, starts[1:]):
        candidate = block[left:right]
        named = re.search(rf"(?m)^\s*name\s*:\s*['\"]?{re.escape(name)}['\"]?\s*$", candidate)
        typed = re.search(rf"(?m)^\s*type\s*:\s*['\"]{re.escape(signature)}['\"]\s*$", candidate)
        if named and typed:
            matches.append(candidate)
    return matches[0] if len(matches) == 1 else ""


def method_or_empty_signature(block: str, name: str, signature: str) -> str:
    """Fail closed for a missing/ambiguous selected Kotlin/JVM member signature."""
    return exact_method_signature(block, name, signature)


def has_dex_scalar(method: str, value: int) -> bool:
    """Require an actual DEX const instruction, never an ASCII numeric string.

    Kotlin ``const val`` fields are normally inlined at callers.  A DEX string-pool
    search for their decimal spelling is therefore both unsound and often impossible.
    This deliberately accepts only dexdump's typed ``#int``/``#long`` operand form on
    a const instruction in the selected method (or its owner class initializer).
    """
    return bool(re.search(
        rf"(?mi)^.*\bconst(?:[-/][a-z0-9]+)*\b[^\n]*#(?:int|long)\s+{re.escape(str(value))}\b",
        method,
    ))


def has_dex_conditional(method: str) -> bool:
    """Require at least one Dalvik conditional branch in the selected method."""
    return bool(re.search(r"(?mi)^.*\bif-(?:eq|ne|lt|ge|gt|le)(?:z)?\b", method))


def has_direct_member_call(method: str, owner_and_member: str) -> bool:
    """Require the dexdump spelling for a direct selected-member invocation."""
    return bool(re.search(
        rf"(?mi)^.*\binvoke[-/a-z]*\b[^\n]*{re.escape(owner_and_member)}",
        method,
    ))


def ordered_direct_member_calls(method: str, owner_and_members: tuple[str, ...]) -> bool:
    """Require each direct call and its source-matched order in one method block."""
    positions = [method.find(token) for token in owner_and_members]
    return all(position >= 0 for position in positions) and positions == sorted(positions)


class DexInspector:
    """Content-keyed, unique-owner signed-DEX inspector.

    Descriptor inventories are read from every DEX.  `dexdump -d` is then invoked
    only for a DEX that is proven to own a requested descriptor, and that complete
    signed DEX disassembly is cached by its SHA-256 content.  This deliberately
    replaces the historical naive whole-APK/multi-DEX-per-descriptor loop.  The
    deadline is one total 1,200-second budget by default, not 300 seconds per DEX.
    """

    def __init__(self, apk: Path, tools: Path, deadline: Deadline | None = None,
                 deadline_seconds: int = 1200) -> None:
        self.apk = apk
        self.tools = tools
        self.deadline = deadline or Deadline(deadline_seconds)
        self.environment = os.environ.copy()
        self.dex_by_name: dict[str, bytes] = {}
        self.definitions: list[str] = []
        self.runtime = b""
        self._disassembly_by_content: dict[str, str] = {}
        self._owners_by_descriptor: dict[str, list[tuple[str, bytes]]] = {}
        try:
            with zipfile.ZipFile(apk) as archive:
                require(archive.testzip() is None, "APK_ZIP_CRC_FAILURE")
                names = archive.namelist()
                require(len(names) == len(set(names)), "APK_DUPLICATE_ZIP_ENTRY")
                for name in names:
                    self.deadline.remaining()
                    if re.fullmatch(r"classes\d*\.dex", name):
                        payload = archive.read(name)
                        self.dex_by_name[name] = payload
                        classes = dex_classes(payload)
                        self.definitions.extend(classes)
                        for descriptor in classes:
                            self._owners_by_descriptor.setdefault(descriptor, []).append((name, payload))
                        self.runtime += payload
                    elif name.endswith((".bundle", ".jsbundle")):
                        self.runtime += archive.read(name)
        except zipfile.BadZipFile as error:
            raise AdmissionStop("APK_ARCHIVE_INVALID") from error
        require(bool(self.dex_by_name), "APK_DEX_MISSING")
        require(len(self.definitions) == len(set(self.definitions)), "DUPLICATE_DEX_CLASS_DEFINITION")

    def owner(self, descriptor: str) -> tuple[str, str]:
        owners = self._owners_by_descriptor.get(descriptor, [])
        require(len(owners) == 1,
                "COMPILED_CLASS_DEX_LOCATION_NOT_UNIQUE:" + descriptor)
        dex_name, payload = owners[0]
        content_hash = hashlib.sha256(payload).hexdigest()
        if content_hash not in self._disassembly_by_content:
            try:
                with tempfile.NamedTemporaryFile(suffix=".dex") as temporary:
                    temporary.write(payload)
                    temporary.flush()
                    self._disassembly_by_content[content_hash] = self.deadline.call(
                        [self.tools / "dexdump", "-d", temporary.name], self.environment
                    )
            except OSError as error:
                raise AdmissionStop("READ_ONLY_DEX_TEMPORARY_STOP") from error
        return dex_name, class_block(self._disassembly_by_content[content_hash], descriptor)

    def require_unique_classes(self, descriptors: Iterable[str]) -> None:
        for descriptor in descriptors:
            require(self.definitions.count(descriptor) == 1,
                    "MISSING_OR_DUPLICATE_REQUIRED_CLASS:" + descriptor)

    @property
    def owner_cache_entries(self) -> int:
        return len(self._disassembly_by_content)


def output_json(out: Path, filename: str, body: dict[str, Any]) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out / filename).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def tool_paths() -> Path:
    tools = Path(os.environ.get(
        "HUGR_ANDROID_BUILD_TOOLS_DIR",
        "/home/ubuntu/android-sdk-watch-recovery-2026-10-03/build-tools/34.0.0",
    ))
    for name in ("aapt", "aapt2", "apksigner", "zipalign", "dexdump"):
        require((tools / name).is_file(), "INSPECTION_TOOL_MISSING:" + name)
    return tools


def read_archive_identity(apk: Path, tools: Path, deadline: Deadline | None = None,
                          deadline_seconds: int = 1200) -> tuple[str, str, str]:
    """Return badging, certificate output, and compiled manifest using read-only tools."""
    deadline = deadline or Deadline(deadline_seconds)
    environment = os.environ.copy()
    badging = deadline.call([tools / "aapt2", "dump", "badging", apk], environment)
    certificates = deadline.call([tools / "apksigner", "verify", "--verbose", "--print-certs", apk], environment)
    deadline.call([tools / "zipalign", "-c", "-p", "4", apk], environment)
    manifest = deadline.call([tools / "aapt", "dump", "xmltree", apk, "AndroidManifest.xml"], environment)
    return badging, certificates, manifest


def component_blocks(xmltree: str, tag: str, required_name: str) -> list[str]:
    lines = xmltree.splitlines(keepends=True)
    found: list[str] = []
    for index, line in enumerate(lines):
        match = re.match(r"^(\s*)E: " + re.escape(tag) + r"\b", line)
        if not match:
            continue
        indent = len(match.group(1))
        end = len(lines)
        for cursor in range(index + 1, len(lines)):
            sibling = re.match(r"^(\s*)E: \S+", lines[cursor])
            if sibling and len(sibling.group(1)) <= indent:
                end = cursor
                break
        candidate = "".join(lines[index:end])
        if required_name in candidate:
            found.append(candidate)
    return found


def attr_is(block: str, attribute: str, value: str) -> bool:
    line = next((row for row in block.splitlines() if f"android:{attribute}" in row), None)
    if line is None:
        return False
    if f'(Raw: "{value}")' in line or f'="{value}"' in line:
        return True
    if value == "true":
        return bool(re.search(r"0xffffffff\b", line, re.I))
    if value == "false":
        return bool(re.search(r"(?:0x0\b|0x00000000\b)", line, re.I))
    scalars = {
        ("launchMode", "singleTask"): 2,
        ("screenOrientation", "portrait"): 1,
        ("configChanges", "orientation|screenSize"): 0x480,
    }
    expected = scalars.get((attribute, value))
    encoded = re.search(r"=\(type 0x1[01]\)(0x[0-9a-f]+)\s*$", line, re.I)
    return expected is not None and encoded is not None and int(encoded.group(1), 16) == expected


def verify_source_stage_live_js_v2(stage_root: Path, manifest_path: Path) -> dict[str, Any]:
    """Verify exact staged JS byte custody, deliberately *not* DEX execution.

    This representation is available when an APK embeds no inspectable JS bundle.
    It proves only that named staged source bytes hash to the supplied source-stage
    manifest and contain the ordered durable-retain-then-V2-metadata-exclusion path.
    Candidate/build binding remains a separate stage-to-build custody gate.
    """
    require(stage_root.is_dir() and manifest_path.is_file() and not stage_root.is_symlink() and not manifest_path.is_symlink(),
            "LIVE_JS_SOURCE_STAGE_CUSTODY_UNAVAILABLE")
    try:
        rows = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise AdmissionStop("LIVE_JS_SOURCE_STAGE_MANIFEST_INVALID") from error
    require(isinstance(rows, list), "LIVE_JS_SOURCE_STAGE_MANIFEST_INVALID")
    indexed = {row.get("path"): row for row in rows if isinstance(row, dict) and isinstance(row.get("path"), str)}
    require(len(indexed) == len(rows), "LIVE_JS_SOURCE_STAGE_MANIFEST_DUPLICATE_OR_INVALID_MEMBER")
    files = ("lib/build45SourceIngestion.ts", "lib/build45SourceProtocol.ts")
    hashes: dict[str, str] = {}
    source_bytes: dict[str, bytes] = {}
    for relative in files:
        row = indexed.get(relative)
        require(isinstance(row, dict), "LIVE_JS_SOURCE_STAGE_MEMBER_MISSING:" + relative)
        source = (stage_root / relative).resolve()
        require(stage_root.resolve() in source.parents and source.is_file() and not source.is_symlink(),
                "LIVE_JS_SOURCE_STAGE_MEMBER_UNSAFE:" + relative)
        payload = source.read_bytes()
        digest = hashlib.sha256(payload).hexdigest()
        require(row.get("sha256") == digest and row.get("bytes") == len(payload),
                "LIVE_JS_SOURCE_STAGE_BYTE_MISMATCH:" + relative)
        hashes[relative] = digest
        source_bytes[relative] = payload
    ingestion = source_bytes["lib/build45SourceIngestion.ts"]
    durable = ingestion.find(b"await this.store.appendCanonical")
    decode = ingestion.find(b"const payload = decodeBuild45SourcePayload")
    exclusion = ingestion.find(b"payload.stream === 'CARDIAC_INPUT_SHAPE') continue")
    require(durable >= 0 and decode > durable and exclusion > decode,
            "LIVE_JS_V2_DURABLE_RETAIN_THEN_METADATA_EXCLUSION_MISSING")
    protocol = source_bytes["lib/build45SourceProtocol.ts"]
    require(b"CARDIAC_INPUT_SHAPE" in protocol and b"view.getUint8(0) === 2" in protocol,
            "LIVE_JS_V2_PROTOCOL_DECLARATION_MISSING")
    return {
        "representation": "SOURCE_STAGE_EXACT_BYTE_CUSTODY_ONLY",
        "notDexRuntimeExecution": True,
        "notClaimedAsBundleExecution": True,
        "stageRoot": str(stage_root.resolve()),
        "manifest": str(manifest_path.resolve()),
        "manifestSha256": sha256_file(manifest_path),
        "members": hashes,
        "orderedDurableRetentionThenV2MetadataExclusion": "PASS",
    }


def compiled_bundle_live_js_v2(archive: Path) -> dict[str, Any] | None:
    """Find an APK-embedded JS/Hermes bundle; presence is not execution evidence."""
    try:
        with zipfile.ZipFile(archive) as zipped:
            candidates = [name for name in zipped.namelist() if name.endswith((".bundle", ".jsbundle"))]
            matched = []
            for name in candidates:
                payload = zipped.read(name)
                if b"CARDIAC_INPUT_SHAPE" in payload and b"appendCanonical" in payload:
                    matched.append({"entry": name, "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload)})
    except zipfile.BadZipFile as error:
        raise AdmissionStop("APK_ARCHIVE_INVALID") from error
    if not matched:
        return None
    return {
        "representation": "APK_EMBEDDED_COMPILED_BUNDLE_PRESENCE",
        "bundleEntries": matched,
        "notDexRuntimeExecution": True,
        "notClaimedAsExecuted": True,
        "orderedDispatchMarkerPresence": "PASS",
    }
