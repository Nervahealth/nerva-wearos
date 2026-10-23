#!/usr/bin/env python3
"""Read-only admission validator for proposed Watch84 only.

This tool is deliberately inert until a candidate APK exists.  It never builds,
signs, repacks, uploads, submits, installs, launches, or writes beside the APK;
only the caller-supplied --out directory receives an inspection receipt.

It rejects any non-continuous package/version/signer/archive and requires signed
DEX evidence for the prospective CARDIAC positional-input V2 representation plus
existing V1/V2 duration, hard-cutoff, and exact ACK guards.  It is an artifact
predicate checker, not a device/runtime/physiology qualification.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any

from candidate_admission_common import (
    AdmissionStop, Deadline, DexInspector, component_blocks, contains_text, has_dex_conditional,
    has_dex_scalar, has_direct_member_call, method_block, output_json, read_archive_identity,
    require, sha256_file, tool_paths,
)

PACKAGE = "com.hugr.wearos"
VERSION_CODE = 84
VERSION_NAME = "0.84.0-positional-v2-duration-ladder-candidate"
# Authoritative Watch83 artifact-admission receipt (not an installed-device query).
WATCH83_SIGNER_SHA256 = "fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706"
WATCH83_RECEIPT = Path("/home/ubuntu/HUGR_DURATION_LADDER_EXTENSION_2026_10_19/validation/watch83-build-lane-result.json")
# The historical compatibility runtime is admitted only as this exact Watch83
# classes5.dex byte sequence.  A candidate may not replace, regenerate, move,
# duplicate, or merely approximate it.
WATCH83_COMPAT_DEX_NAME = "classes5.dex"
WATCH83_COMPAT_DEX_SHA256 = "a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784"
EXPECTED_DEX_NAMES = {"classes.dex", "classes2.dex", "classes3.dex", "classes4.dex", WATCH83_COMPAT_DEX_NAME}

SHAPE = "Lcom/hugr/wearos/CardiacInputShape;"
SHAPE_COMPANION = "Lcom/hugr/wearos/CardiacInputShape$Companion;"
SHAPE_CODEC = "Lcom/hugr/wearos/CardiacInputShapeCodec;"
FRESH_RUNTIME = "Lcom/hugr/wearos/FreshRunRuntime;"
FRESH_SESSION = "Lcom/hugr/wearos/FreshRunSession;"
DURATION_POLICY = "Lcom/hugr/wearos/WatchFreshRunDurationPolicy;"
SOURCE_CODEC = "Lcom/hugr/wearos/SourcePayloadCodec;"
HEALTH_SERVICE = "Lcom/hugr/wearos/HealthSensorService;"


def _method_or_empty(block: str, name: str) -> str:
    try:
        return method_block(block, name)
    except AdmissionStop:
        return ""


def _dex_instruction_with_literal(method: str, opcode: str, value: int) -> bool:
    """Match a typed instruction in this selected signed method, never whole-DEX text.

    dexdump can render const-wide/32 operands as an unsigned hexadecimal comment
    rather than the source decimal long (for example 300000 as ``#000493e0``).
    """
    decimal = rf"#(?:int|long)\s+{re.escape(str(value))}\b"
    encoded = rf"#{value & 0xffffffff:08x}\b" if value >= 0 else "(?!)"
    return bool(re.search(
        rf"(?mi)^.*\|\s*[0-9a-f]+:\s+{re.escape(opcode)}\b[^\n]*(?:{decimal}|{encoded})",
        method,
    ))


def _dex_instruction_at(method: str, offset: str, opcode: str, value: int) -> bool:
    """Require one exact selected-method instruction offset/opcode/typed literal."""
    decimal = rf"#(?:int|long)\s+{re.escape(str(value))}\b"
    encoded = rf"#{value & 0xffffffff:08x}\b" if value >= 0 else "(?!)"
    return bool(re.search(
        rf"(?mi)^.*\|{re.escape(offset)}:\s+{re.escape(opcode)}\b[^\n]*(?:{decimal}|{encoded})",
        method,
    ))


def _dex_instruction_text_at(method: str, offset: str, instruction_text: str) -> bool:
    """Require exact instruction text at one program offset in the selected method."""
    return bool(re.search(
        rf"(?mi)^.*\|{re.escape(offset)}:\s+.*{re.escape(instruction_text)}",
        method,
    ))


def _ordered_tokens(method: str, tokens: tuple[str, ...]) -> bool:
    """Require exact selected-method evidence in source-matched order."""
    cursor = 0
    for token in tokens:
        position = method.find(token, cursor)
        if position < 0:
            return False
        cursor = position + len(token)
    return True


def _ordered_const_values(method: str, values: tuple[int, ...]) -> bool:
    """Require each closed-ladder scalar as a const instruction in the specified order."""
    positions: list[int] = []
    for value in values:
        decimal = rf"#(?:int|long)\s+{re.escape(str(value))}\b"
        encoded = rf"#{value & 0xffffffff:08x}\b"
        match = re.search(
            rf"(?mi)^.*\|\s*[0-9a-f]+:\s+const(?:[-/][a-z0-9]+)*\b[^\n]*(?:{decimal}|{encoded})",
            method,
        )
        if match is None:
            return False
        positions.append(match.start())
    return positions == sorted(positions)


def watch_predicate_report(
    shape_class: str,
    shape_companion: str,
    shape_codec: str,
    fresh_runtime: str,
    fresh_session: str,
    duration_policy: str,
    source_codec: str,
    runtime: bytes,
) -> dict[str, bool]:
    """Frozen, narrow signed-representation predicates; absence always fails closed."""
    capture = _method_or_empty(shape_companion, "capture")
    chunks = _method_or_empty(shape_class, "chunks")
    encode = _method_or_empty(shape_codec, "encode")
    decode = _method_or_empty(shape_codec, "decode")
    complete = _method_or_empty(shape_codec, "validateCompleteGroup")
    accepts = _method_or_empty(duration_policy, "accepts")
    lifetime = _method_or_empty(duration_policy, "lifetimeMs")
    duration_initializer = _method_or_empty(duration_policy, "<clinit>")
    admit_ladder = _method_or_empty(fresh_runtime, "admitLadder")
    accepts_samples = _method_or_empty(fresh_session, "acceptsSamples")
    append = _method_or_empty(fresh_session, "append")
    accept_ack = _method_or_empty(fresh_session, "acceptAck")
    source_input_shape = _method_or_empty(source_codec, "cardiacInputShape")
    source_decode_shape = _method_or_empty(source_codec, "decodeCardiacInputShape")

    return {
        # Raw getter snapshot is bounded and supports only explicit marker kinds; no coercion proof.
        "rawShapeCaptureOnceBeforeNormalization": all(token in capture for token in (
            "CardiacInputShape$Companion;.readRawField:",
            "CardiacInputShape$Companion;.snapshot:",
        )),
        "rawShapeExplicitInt32OnlyAndBoundedSlots": all(token in (shape_class + shape_companion + shape_codec) for token in (
            "CardiacInputSlotKind;.INT32:",
            "CardiacInputSlotKind;.NON_INT_NUMBER:",
            "CardiacInputSlotKind;.OTHER_TYPE:",
            "CardiacInputSlotKind;.ACCESS_ERROR:",
        )),
        # Kotlin constants are local signed-Dalvik operands. Selected methods prove
        # V2/kind-3, 45-byte header, 10-byte slots and each 0x08/0x10/0x20 flag path.
        "rawShapeCodecExactV2Kind3HeaderSlotsAndFlags": (
            has_direct_member_call(source_input_shape, "Lcom/hugr/wearos/CardiacInputShapeCodec;.encode:")
            and has_direct_member_call(source_decode_shape, "Lcom/hugr/wearos/CardiacInputShapeCodec;.decode:")
            and all(token in encode for token in (
                "Ljava/nio/ByteBuffer;.allocate:", "Ljava/nio/ByteOrder;.LITTLE_ENDIAN:",
                "Ljava/nio/ByteBuffer;.order:", "Ljava/nio/ByteBuffer;.put:",
                "Ljava/nio/ByteBuffer;.putShort:", "Ljava/nio/ByteBuffer;.putInt:",
                "Ljava/nio/ByteBuffer;.putLong:",
            ))
            and all(_dex_instruction_at(encode, offset, opcode, value) for offset, opcode, value in (
                ("0056", "const/4", 2), ("005b", "const/4", 3),
                ("001b", "const/16", 8), ("002a", "const/16", 16),
                ("0041", "const/16", 32), ("0048", "mul-int/lit8", 10),
                ("004a", "add-int/lit8", 45), ("0005", "and-int/lit8", -64),
            ))
            and all(token in decode for token in (
                "Ljava/nio/ByteBuffer;.wrap:", "Ljava/nio/ByteOrder;.LITTLE_ENDIAN:",
                "Ljava/nio/ByteBuffer;.order:", "Ljava/nio/ByteBuffer;.get:",
                "Ljava/nio/ByteBuffer;.getShort:", "Ljava/nio/ByteBuffer;.getInt:",
                "Ljava/nio/ByteBuffer;.getLong:",
            ))
            and all(_dex_instruction_at(decode, offset, opcode, value) for offset, opcode, value in (
                ("002a", "const/4", 2), ("002d", "const/4", 3),
                ("000b", "const/16", 45), ("00f0", "const/16", 4096),
                ("012f", "const/16", 20), ("003b", "and-int/lit8", -64),
                ("0219", "and-int/lit8", 8), ("0104", "and-int/lit8", 16),
                ("00b6", "and-int/lit8", 32), ("0116", "rem-int/lit8", 20),
                ("013d", "mul-int/lit8", 10), ("0141", "const/16", 45),
                ("0143", "add-int/lit8", 45),
            ))
            and has_dex_conditional(decode)
        ),
        "rawShapeRejectsDeletedOrMismatchedProjectionAndGroup": all(token in (decode + complete + chunks) for token in (
            "legacy coordinate saturation must be explicit",
            "Nonempty shape cannot have a trailing empty chunk",
            "group metadata differs between chunks",
            "group has duplicate or missing chunk ranges",
        )),
        # Retain the ordered closed four-rung set and fixed delivery grace; V1 is separate.
        "closedV2DurationAndLifetime": (
            _ordered_const_values(duration_initializer, (300000, 1800000, 7200000, 28800000))
            and "Ljava/util/Set;.contains:" in accepts
            and _ordered_tokens(lifetime, (
                "WatchFreshRunDurationPolicy;.accepts:(J)Z", "if-eqz",
                "#000dbba0", "add-long/2addr", "return-wide",
            ))
            and _dex_instruction_with_literal(lifetime, "const-wide/32", 900000)
            and "Unadmitted fresh V2 duration" in lifetime
        ),
        "ladderAdmissionBindsV2Receipt": all(token in admit_ladder for token in (
            "WatchFreshRunDurationPolicy;.accepts:", "FreshRunReceiptVersion;.V2:",
        )),
        # Exact elapsed cutoff must guard the selected append path before journal append.
        "hardSampleCutoffGuardsAppend": (
            all(_dex_instruction_text_at(accepts_samples, offset, instruction) for offset, instruction in (
                ("000f", "FreshRunPhase;.STARTED:"),
                ("0021", "FreshRunReceipt;.getStartElapsedMs:"),
                ("0025", "cmp-long v0, v0, v2"), ("0027", "if-ltz v0, 0048"),
                ("003e", "FreshRunReceipt;.getDurationMs:"),
                ("0042", "cmp-long v0, v0, v2"), ("0044", "if-gez v0, 0048"),
            ))
            and all(_dex_instruction_text_at(append, offset, instruction) for offset, instruction in (
                ("000b", "FreshRunSession;.acceptsSamples:()Z"),
                ("0010", "if-nez v0, 0014"), ("0013", "return-object v1"),
                ("0033", "SourceJournal;.append:"),
            ))
        ),
        # Exact queued tuple + source + manifest SHA + no duplicate before deletion/receipt update.
        "exactAckGuardPrecedesCompletion": all(token in accept_ack for token in (
            "SourceSegmentAcknowledgement;.getCumulativeRecordIndex:",
            "FreshRunReceipt;.getSourceId:",
            "SourceSegmentManifest;.getSha256Hex:",
            "FreshRunReceiptStore;.pendingAck:",
            "SourceJournal;.acknowledgeCompletedSegment:",
            "ACK_DELETE_FAILED",
        )),
        "v1CompatibilityRetained": all(contains_text(runtime, token) for token in (
            "HUGR_RUN_RECEIPT_V1", "START_FRESH_FIVE_MINUTES", "HUGR_RUN_RECEIPT_V2",
            "START_FRESH_DURATION_LADDER", "fresh_duration_ms",
        )),
        # Evidence shape must remain non-measurement; this is literal signed-code/UI predicate only.
        "shapeNotBroadcastAsPhysiologicalMeasurement": all(contains_text(runtime, token) for token in (
            "CARDIAC_EVIDENCE_DATA", "contractAnomaly", "INPUT_SHAPE",
        )),
    }


def require_watch_predicates(report: dict[str, bool]) -> None:
    failed = [name for name, passed in report.items() if not passed]
    require(not failed, "WATCH84_COMPILED_PREDICATE_MISSING_OR_MISMATCH:" + ",".join(failed))


def predecessor_receipt() -> dict[str, Any]:
    try:
        receipt = json.loads(WATCH83_RECEIPT.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise AdmissionStop("WATCH83_AUTHORITATIVE_RECEIPT_UNAVAILABLE") from error
    artifact = receipt.get("artifact") if isinstance(receipt, dict) else None
    require(isinstance(artifact, dict), "WATCH83_AUTHORITATIVE_RECEIPT_INVALID")
    require(artifact.get("package") == PACKAGE and artifact.get("versionCode") == 83,
            "WATCH83_AUTHORITATIVE_RECEIPT_IDENTITY_INVALID")
    require(artifact.get("signerPublicSha256") == WATCH83_SIGNER_SHA256,
            "WATCH83_AUTHORITATIVE_SIGNER_BASELINE_INVALID")
    return {
        "receipt": str(WATCH83_RECEIPT),
        "artifactSha256": artifact.get("sha256"),
        "versionCode": artifact.get("versionCode"),
        "versionName": artifact.get("versionName"),
        "signerSha256": artifact.get("signerPublicSha256"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="proposed Watch84 APK; read only")
    parser.add_argument("--out", type=Path, required=True, help="new/reusable non-artifact receipt directory")
    parser.add_argument("--inspection-deadline-seconds", type=int, default=1200)
    args = parser.parse_args()
    apk = args.apk.resolve()
    out = args.out.resolve()
    require(apk.is_file() and not apk.is_symlink(), "WATCH84_APK_NOT_REGULAR_FILE")
    require(out != apk.parent and out not in apk.parents, "WATCH84_OUTPUT_MUST_NOT_BE_ARTIFACT_DIRECTORY")
    baseline = predecessor_receipt()
    tools = tool_paths()
    deadline = Deadline(args.inspection_deadline_seconds)
    badging, certificates, manifest = read_archive_identity(apk, tools, deadline=deadline)
    expected = f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' versionName='{VERSION_NAME}'"
    require(expected in badging, "WATCH84_PACKAGE_VERSION_IDENTITY_MISMATCH")
    require(f"certificate SHA-256 digest: {WATCH83_SIGNER_SHA256}" in certificates,
            "WATCH84_SIGNER_NOT_WATCH83_CONTINUITY")
    require("com.hugr.wearos.MainActivity" in manifest, "WATCH84_MAIN_LAUNCHER_MISSING")

    inspector = DexInspector(apk, tools, deadline=deadline)
    require(set(inspector.dex_by_name) == EXPECTED_DEX_NAMES,
            "WATCH84_DEX_COMPOSITION_NOT_EXACTLY_SOURCE_BUILT_FOUR_PLUS_ADMITTED_COMPAT")
    compat = inspector.dex_by_name.get(WATCH83_COMPAT_DEX_NAME)
    require(compat is not None and hashlib.sha256(compat).hexdigest() == WATCH83_COMPAT_DEX_SHA256,
            "WATCH84_COMPATIBILITY_DEX_NOT_EXACT_WATCH83_ADMITTED_BYTES")
    inspector.require_unique_classes((
        SHAPE, SHAPE_COMPANION, SHAPE_CODEC, FRESH_RUNTIME, FRESH_SESSION,
        DURATION_POLICY, SOURCE_CODEC, HEALTH_SERVICE,
    ))
    # No test fixture/test runner is permitted in signed production DEX.
    forbidden_suffixes = ("CardiacInputShapeTest;", "FreshRunReceiptTest;", "WatchFreshRunDurationPolicyTest;")
    forbidden_prefixes = ("Lorg/junit/", "Ljunit/", "Lorg/robolectric/", "Landroidx/test/")
    forbidden = [item for item in inspector.definitions
                 if item.endswith(forbidden_suffixes) or item.startswith(forbidden_prefixes)]
    require(not forbidden, "WATCH84_PACKAGED_TEST_RUNTIME:" + ",".join(forbidden))

    _, shape_class = inspector.owner(SHAPE)
    _, shape_companion = inspector.owner(SHAPE_COMPANION)
    _, shape_codec = inspector.owner(SHAPE_CODEC)
    _, fresh_runtime = inspector.owner(FRESH_RUNTIME)
    _, fresh_session = inspector.owner(FRESH_SESSION)
    _, duration_policy = inspector.owner(DURATION_POLICY)
    _, source_codec = inspector.owner(SOURCE_CODEC)
    report = watch_predicate_report(
        shape_class, shape_companion, shape_codec, fresh_runtime, fresh_session,
        duration_policy, source_codec, inspector.runtime,
    )
    require_watch_predicates(report)

    out.mkdir(parents=True, exist_ok=True)
    (out / "apk-badging.txt").write_text(badging, encoding="utf-8")
    (out / "apk-signer.txt").write_text(certificates, encoding="utf-8")
    (out / "apk-manifest.txt").write_text(manifest, encoding="utf-8")
    for name, block in {
        "watch84-shape.dexdump.txt": shape_class,
        "watch84-shape-companion.dexdump.txt": shape_companion,
        "watch84-shape-codec.dexdump.txt": shape_codec,
        "watch84-fresh-runtime.dexdump.txt": fresh_runtime,
        "watch84-fresh-session.dexdump.txt": fresh_session,
        "watch84-duration-policy.dexdump.txt": duration_policy,
        "watch84-source-payload-codec.dexdump.txt": source_codec,
    }.items():
        (out / name).write_text(block, encoding="utf-8")
    result: dict[str, Any] = {
        "status": "PASS",
        "scope": "READ_ONLY_PROPOSED_WATCH84_COMPILED_ARTIFACT_ADMISSION_ONLY",
        "apk": str(apk), "bytes": apk.stat().st_size, "sha256": sha256_file(apk),
        "package": PACKAGE, "versionCode": VERSION_CODE, "versionName": VERSION_NAME,
        "signerSha256": WATCH83_SIGNER_SHA256,
        "compatibilityDex": {"entry": WATCH83_COMPAT_DEX_NAME, "sha256": WATCH83_COMPAT_DEX_SHA256},
        "samePackageStrictlyNewVersionAndWatch83Signer": "PASS",
        "authoritativeWatch83Baseline": baseline,
        "archive": {"zipCrc": "PASS", "pageAlignment": "PASS", "uniqueDexClasses": "PASS"},
        "compiledCardiacInputShape": report,
        "dexInspection": {
            "deadlineSeconds": args.inspection_deadline_seconds,
            "strategy": "content_keyed_unique_Dex_owner_full_disassembly",
            "fullDexDisassemblies": inspector.owner_cache_entries,
            "historicalNaiveWholeApkMultiDexPerDescriptor": "FORBIDDEN",
            "historical300SecondInspectionBudget": "FORBIDDEN",
        },
        "nonclaims": [
            "No build, signing, submission, upload, installation, launch, device, recording, egress, private-root, or physiological-data action occurred.",
            "Signed-DEX predicate presence/structure is not proof of runtime execution, Samsung callback behavior, duration completion, ACK delivery, or physiological qualification.",
            "CARDIAC INPUT_SHAPE V2 is raw positional metadata; this admission makes no native-adjacency, IBI chronology, RMSSD, clinical, baseline, learning, Loop A, or N1 claim.",
            "V1 compatibility is required; this tool does not authorize or qualify either V1 or V2 runtime behavior.",
        ],
    }
    output_json(out, "watch84-candidate-artifact-admission.json", result)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except AdmissionStop as stop:
        print("WATCH84_CANDIDATE_ADMISSION_STOP:" + str(stop), file=sys.stderr)
        raise SystemExit(1)
