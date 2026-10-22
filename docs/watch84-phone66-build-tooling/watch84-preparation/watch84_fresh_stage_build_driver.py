#!/usr/bin/env python3
"""Watch84 parent-gated fresh-stage build driver.

This is tooling only.  ``preflight`` is the only mode suitable before a parent
has released the final Watch84 source snapshot and all gates.  It copies only
an allowlisted, tracked runtime source custody mirror and writes no source
file.  ``build-once`` is deliberately fail-closed: it requires the preflight
intent, creates a durable started marker *before* Gradle, and refuses every
subsequent invocation for that stage.  It has no upload, installation, device,
ADB, collection, export, test, or source-version mutation operation.

A successful build-once path is exactly:
  parent gate proofs -> source/runtime custody stage -> one assembleDebug ->
  verify source-built four DEX -> append admitted classes5.dex verbatim ->
  QEMU-pinned zipalign -> existing signer in process memory -> read-only
  compiled-artifact admission -> local SHA-256 sidecar.

The caller must already have an authorized versionCode 84 / versionName source
snapshot.  This program never edits build.gradle, package/version fields,
source, Git state, signing configuration, credentials, or keystores.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from datetime import datetime, timezone
from typing import Any, Iterable

# Immutable public lineage pins.  No password, alias, keystore path, or private
# signing material is included in any receipt written by this driver.
PACKAGE = "com.hugr.wearos"
VERSION_CODE = 84
VERSION_NAME = "0.84.0-positional-v2-duration-ladder-candidate"
PREDECESSOR_APK = Path(
    "/home/ubuntu/HUGR_DURATION_LADDER_EXTENSION_2026_10_19/artifacts/"
    "HUGR_Watch83w_0.83.0_duration-ladder-candidate.apk"
)
PREDECESSOR_APK_SHA256 = "c9a5cbdaf090563c6666c98ae628878e0399f1e586fc238a07a2389fc399386e"
WATCH83_SIGNER_SHA256 = "fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706"
COMPAT_DEX_NAME = "classes5.dex"
COMPAT_DEX_SHA256 = "a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784"
COMPILE_ONLY_JAR = "app/libs/egress-compile-stubs.jar"
COMPILE_ONLY_JAR_SHA256 = "b2b512ee0c7cd3e6b1028d6abe0138dfe63a364185a421b1dd4e26c768d142cf"
SDK_AAR = "app/libs/samsung-health-sensor-api-1.4.1.aar"
SDK_AAR_SHA256 = "893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c"

# The only tool identities admitted by the full Android compile proof.  These
# are read in place; neither binary, sysroot, cache, nor JAR is copied to a
# source-stage/artifact directory.
PREPARATION_ROOT = Path(__file__).resolve().parent
ROOT = PREPARATION_ROOT.parent
COMPILE_ROUTE = ROOT / "compile-route"
AAPT2_LAUNCHER = COMPILE_ROUTE / "aapt2"
AAPT2_LAUNCHER_SHA256 = "49c24fff0034ff5b70ac22fac2688644dc8ff1917a262fe7850146557e5fce6f"
AAPT2_REAL = COMPILE_ROUTE / "aapt2.real-maven-8.1.0-10154469"
AAPT2_REAL_SHA256 = "3a72be5143d8272f702c5929c22273133d2465129f43c63818288b4e9a1a79c3"
GRADLE = Path("/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/gradle/gradle-8.1/bin/gradle")
GRADLE_SHA256 = "6cef1128c64a1170397d884668501e39fac228267f94d98aad57674a0bce2c9e"
ARM_JAVA_HOME = Path("/usr/lib/jvm/java-17-openjdk-arm64")
ARM_JAVA = ARM_JAVA_HOME / "bin/java"
ARM_JAVA_SHA256 = "43c4d195769fe77e563f1e87b8c08ba1735e042296059b9069dc5e3fed043817"
QEMU_X86_64 = Path("/usr/bin/qemu-x86_64-static")
QEMU_X86_64_SHA256 = "de72ec1143909f0f495a53c36d9215cb86e2952517aad87044c741a1088dc60f"
X86_SYSROOT = Path("/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/x86_64-sysroot")
ANDROID_SDK = Path("/home/ubuntu/jobs/job_SSW3Rmkp/toolchain/android-sdk")
BUILD_TOOLS = ANDROID_SDK / "build-tools/34.0.0"
APT = BUILD_TOOLS / "aapt"
APT_SHA256 = "add6b9d489325c4bf9d2240cdcf03c86d638780579b18a93d8efc4b499757f74"
SDK_AAPT2 = BUILD_TOOLS / "aapt2"
SDK_AAPT2_SHA256 = "760aa057e89e3940c3e944e6bec61eda864ade42fd9c0a3113735e329657705f"
ZIPALIGN = BUILD_TOOLS / "zipalign"
ZIPALIGN_SHA256 = "f3d6af57b3fed5759de2b6dd9c8cc8ecd16d4b35a2c363d7250a7bb0cd97a089"
DEXDUMP = BUILD_TOOLS / "dexdump"
DEXDUMP_SHA256 = "4115253cd1433c1836ae8a80b96e045e3b9ce9964166a4fc007b48258eb7333a"
APKSIGNER_JAR = BUILD_TOOLS / "lib/apksigner.jar"
APKSIGNER_JAR_SHA256 = "eefdd6aed9db9fb849e4c98a50d8741e19d1b674ba6547220bcb9c3ed152123a"
GRADLE_USER_HOME = Path("/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home")
VALIDATOR = ROOT / "artifact-preparation/admit_watch84_candidate.py"
SIGNER_PREFLIGHT = ROOT / "build-prerequisites/existing-watch-signing-route-proof.json"
SIGNER_PREFLIGHT_SHA256 = "7a7c0b72612135cbbf00f3d5b98f0b1b24290dc6d883d2ea94799e26aba127c4"
COMPILE_PROOF = ROOT / "compile-route/compile-proof.json"
COMPILE_PROOF_SHA256 = "e6fe25674bb4f2e7e1b0d8115a0f9676e1fd21c7608cae784bc2acd6924a952d"

# Build configuration is intentionally not copied: its signing fields may be
# secret.  Runtime source plus the two public compiler inputs are all that may
# enter a source-stage custody mirror.
RUNTIME_PREFIX = "app/src/main/"
RUNTIME_EXACT = {SDK_AAR, COMPILE_ONLY_JAR}
EXPECTED_BASE_DEX = {"classes.dex", "classes2.dex", "classes3.dex", "classes4.dex"}
EXPECTED_FINAL_DEX = EXPECTED_BASE_DEX | {COMPAT_DEX_NAME}
SIGNATURE_SUFFIXES = (".SF", ".RSA", ".DSA")


class Stop(RuntimeError):
    """Stable fail-closed stop.  Messages must never contain child output."""


def now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def fail(code: str) -> None:
    raise Stop(code)


def sha256_file(path: Path, block_bytes: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(block_bytes), b""):
            digest.update(block)
    return digest.hexdigest()


def is_sha(value: object) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-f]{40,64}", value))


def json_load(path: Path, code: str) -> dict[str, Any]:
    try:
        loaded = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise Stop(code) from error
    if not isinstance(loaded, dict):
        fail(code)
    return loaded


def json_write_new(path: Path, body: dict[str, Any]) -> None:
    if path.exists() or path.is_symlink():
        fail("OUTPUT_RECEIPT_ALREADY_EXISTS")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as handle:
        json.dump(body, handle, indent=2, sort_keys=True)
        handle.write("\n")


def git_capture(source: Path, *args: str) -> str:
    try:
        completed = subprocess.run(
            ["git", "-C", str(source), *args],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=60,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise Stop("SOURCE_GIT_INSPECTION_STOP") from error
    if completed.returncode != 0:
        fail("SOURCE_GIT_INSPECTION_STOP")
    return completed.stdout.decode("utf-8", errors="strict").strip()


def git_state(source: Path) -> dict[str, str]:
    if not source.is_dir() or source.is_symlink():
        fail("SOURCE_ROOT_UNAVAILABLE_OR_SYMLINK")
    state = {
        "root": str(source.resolve()),
        "branch": git_capture(source, "branch", "--show-current"),
        "head": git_capture(source, "rev-parse", "HEAD"),
        "tree": git_capture(source, "rev-parse", "HEAD^{tree}"),
        "status": git_capture(source, "status", "--porcelain=v1"),
    }
    if not state["branch"] or state["status"]:
        fail("SOURCE_SNAPSHOT_NOT_CLEAN_PRESERVATION_BRANCH")
    return state


def tracked_files(source: Path) -> set[str]:
    output = git_capture(source, "ls-files", "-z")
    return {item for item in output.split("\0") if item}


def runtime_allowlist(source: Path) -> list[str]:
    tracked = tracked_files(source)
    candidates = sorted(
        path for path in tracked if path.startswith(RUNTIME_PREFIX) or path in RUNTIME_EXACT
    )
    if not candidates or not RUNTIME_EXACT.issubset(candidates):
        fail("RUNTIME_STAGE_REQUIRED_TRACKED_MEMBER_ABSENT")
    for relative in candidates:
        item = source / relative
        if not item.is_file() or item.is_symlink():
            fail("RUNTIME_STAGE_MEMBER_NOT_REGULAR_TRACKED_FILE")
    # Refuse rather than silently broadening the runtime stage allowlist.
    forbidden = [
        path for path in candidates
        if "/src/test/" in path or path.endswith(".apk") or path.endswith(".aab")
        or "keystore" in path.lower() or path.endswith((".jks", ".p12", ".pem", ".key"))
    ]
    if forbidden:
        fail("RUNTIME_STAGE_FORBIDDEN_MEMBER")
    return candidates


def parse_public_identity(source: Path) -> dict[str, object]:
    """Read only identity tokens; never return/record the signing block."""
    build = source / "app/build.gradle"
    if not build.is_file() or build.is_symlink():
        fail("SOURCE_BUILD_CONFIGURATION_UNAVAILABLE")
    text = build.read_text(encoding="utf-8")
    code = re.search(r"(?m)^\s*versionCode\s+(\d+)\s*$", text)
    name = re.search(r"(?m)^\s*versionName\s+['\"]([^'\"]+)['\"]\s*$", text)
    package = re.search(r"(?m)^\s*applicationId\s+['\"]([^'\"]+)['\"]\s*$", text)
    if not code or not name or not package:
        fail("SOURCE_PUBLIC_IDENTITY_ABSENT")
    identity: dict[str, object] = {
        "package": package.group(1), "versionCode": int(code.group(1)), "versionName": name.group(1),
    }
    if identity != {"package": PACKAGE, "versionCode": VERSION_CODE, "versionName": VERSION_NAME}:
        fail("SOURCE_NOT_AUTHORIZED_WATCH84_IDENTITY")
    return identity


def validate_compile_inputs(source: Path, tracked: set[str]) -> dict[str, object]:
    values = ((COMPILE_ONLY_JAR, COMPILE_ONLY_JAR_SHA256), (SDK_AAR, SDK_AAR_SHA256))
    output: dict[str, object] = {}
    for relative, expected in values:
        item = source / relative
        if relative not in tracked or not item.is_file() or item.is_symlink():
            fail("SOURCE_COMPILE_INPUT_NOT_TRACKED_REGULAR")
        actual = sha256_file(item)
        if actual != expected:
            fail("SOURCE_COMPILE_INPUT_PIN_MISMATCH")
        output[relative] = {"sha256": actual, "bytes": item.stat().st_size}
    try:
        with zipfile.ZipFile(source / COMPILE_ONLY_JAR) as archive:
            entries = set(archive.namelist())
    except zipfile.BadZipFile as error:
        raise Stop("EXACT_COMPILE_ONLY_JAR_INVALID") from error
    required = {
        "com/hugr/wearos/EvidenceEgressGattRuntimeMode.class",
        "com/hugr/wearos/EvidenceEgressGattStartPolicy.class",
        "com/hugr/wearos/EvidenceEgressActivation.class",
        "com/hugr/wearos/EvidenceEgressVolatileDiagnostics.class",
        "com/hugr/wearos/EvidenceEgressContract.class",
    }
    if not required.issubset(entries):
        fail("EXACT_COMPILE_ONLY_JAR_MEMBER_MISMATCH")
    output[COMPILE_ONLY_JAR] = {
        **output[COMPILE_ONLY_JAR],
        "requiredClassMembers": sorted(required),
        "exactOriginalNoSubstituteOrGeneratedStub": True,
    }
    compile_proof = json_load(COMPILE_PROOF, "FULL_ANDROID_COMPILE_PROOF_UNREADABLE")
    if sha256_file(COMPILE_PROOF) != COMPILE_PROOF_SHA256 or compile_proof.get("status") != "FULL_ANDROID_COMPILE_PASS":
        fail("FULL_ANDROID_COMPILE_PROOF_PIN_OR_STATUS_MISMATCH")
    prerequisite = compile_proof.get("recoveredCompilePrerequisite")
    route = compile_proof.get("aapt2Route")
    if (not isinstance(prerequisite, dict) or prerequisite.get("recoveredJarSha256") != COMPILE_ONLY_JAR_SHA256
            or not isinstance(route, dict) or route.get("launcherSha256") != AAPT2_LAUNCHER_SHA256
            or route.get("executedRealAapt2Sha256") != AAPT2_REAL_SHA256):
        fail("FULL_ANDROID_COMPILE_PROOF_ROUTE_OR_PREREQUISITE_MISMATCH")
    signer_proof = json_load(SIGNER_PREFLIGHT, "EXISTING_SIGNER_PREFLIGHT_UNREADABLE")
    if (sha256_file(SIGNER_PREFLIGHT) != SIGNER_PREFLIGHT_SHA256
            or signer_proof.get("status") != "EXISTING_SIGNER_CONTINUITY_PREFLIGHT_PASS"
            or signer_proof.get("predecessorApkSha256") != PREDECESSOR_APK_SHA256
            or signer_proof.get("expectedCertificateSha256") != WATCH83_SIGNER_SHA256
            or signer_proof.get("credentialValuesExposed") is not False
            or signer_proof.get("keyRegenerated") is not False):
        fail("EXISTING_SIGNER_PREFLIGHT_PIN_OR_CONTINUITY_MISMATCH")
    output["existingFullAndroidCompile"] = {"path": str(COMPILE_PROOF), "sha256": COMPILE_PROOF_SHA256, "status": "FULL_ANDROID_COMPILE_PASS"}
    output["existingSignerPreflight"] = {"path": str(SIGNER_PREFLIGHT), "sha256": SIGNER_PREFLIGHT_SHA256, "status": "EXISTING_SIGNER_CONTINUITY_PREFLIGHT_PASS"}
    return output


def require_status_pass(proof: dict[str, Any], code: str) -> None:
    value = proof.get("status")
    if not isinstance(value, str) or not value.startswith("PASS"):
        fail(code)


def rung_fields(row: dict[str, Any]) -> dict[str, Any]:
    """Support runner's mapping form and a normalized parent proof form."""
    result = row.get("native_joined_export", row.get("nativeJoinedExport", {}))
    if not isinstance(result, dict):
        return {}
    actual = result.get("result", result)
    return actual if isinstance(actual, dict) else {}


def validate_four_rung_proof(path: Path) -> dict[str, object]:
    proof = json_load(path, "FOUR_RUNG_V2_PROOF_UNREADABLE")
    require_status_pass(proof, "FOUR_RUNG_V2_CHAIN_NOT_PASS")
    rungs = proof.get("rungs")
    if not isinstance(rungs, dict) or set(rungs) != {"5min", "30min", "2hr", "8hr"}:
        fail("FOUR_RUNG_V2_EXACT_RUNGS_REQUIRED")
    expected_duration = {"5min": 300000, "30min": 1800000, "2hr": 7200000, "8hr": 28800000}
    verified: dict[str, object] = {}
    for name, duration in expected_duration.items():
        row = rungs.get(name)
        if not isinstance(row, dict):
            fail("FOUR_RUNG_V2_RUNG_INVALID")
        fixture = row.get("fixture")
        actual = rung_fields(row)
        if not isinstance(fixture, dict) or not actual:
            fail("FOUR_RUNG_V2_JOINED_PROOF_MISSING")
        if int(fixture.get("duration_ms", -1)) != duration or int(actual.get("durationMs", -1)) != duration:
            fail("FOUR_RUNG_V2_DURATION_MISMATCH")
        if int(fixture.get("record_count", 0)) <= 0 or int(actual.get("recordCount", 0)) <= 0:
            fail("FOUR_RUNG_V2_RECORD_CARDINALITY_INVALID")
        if actual.get("syntheticOnly") is not True:
            fail("FOUR_RUNG_V2_NOT_SYNTHETIC_ONLY")
        if actual.get("originalStateByteUnchanged") is not True:
            fail("FOUR_RUNG_V2_ORIGINAL_STATE_MUTATION")
        if actual.get("completedReportRestartOffline") != "PASS":
            fail("FOUR_RUNG_V2_REPORT_RESTART_OFFLINE_NOT_PASS")
        verified[name] = {
            "durationMs": duration,
            "recordCount": int(actual["recordCount"]),
            "manifestCount": int(actual.get("manifestCount", 0)),
            "watchAcceptedAckCount": int(actual.get("watchAcceptedAckCount", 0)),
            "completedReportRestartOffline": "PASS",
        }
    return {"path": str(path.resolve()), "sha256": sha256_file(path), "rungs": verified}


def validate_independent_reader_proof(path: Path) -> dict[str, object]:
    """Require an explicit parent-owned independent reader, not the native writer."""
    proof = json_load(path, "INDEPENDENT_READER_PROOF_UNREADABLE")
    require_status_pass(proof, "INDEPENDENT_READER_NOT_PASS")
    if proof.get("independentReader") is not True or proof.get("allFourRungs") is not True:
        fail("INDEPENDENT_READER_EXPLICIT_FOUR_RUNG_PASS_REQUIRED")
    if proof.get("writerOrNativeHarnessOnly") is True:
        fail("INDEPENDENT_READER_CANNOT_BE_NATIVE_WRITER")
    return {
        "path": str(path.resolve()), "sha256": sha256_file(path),
        "independentReader": True, "allFourRungs": True,
        "scope": proof.get("scope", "PARENT_OWNED_INDEPENDENT_READER"),
    }


def validate_publication_proof(path: Path, source_state: dict[str, str]) -> dict[str, object]:
    proof = json_load(path, "THREE_REPOSITORY_PUBLICATION_PROOF_UNREADABLE")
    require_status_pass(proof, "THREE_REPOSITORY_PUBLICATION_NOT_PASS")
    if proof.get("allThreeExactTreesAndChangedBytes") is not True:
        fail("THREE_REPOSITORY_EXACT_TREE_BLOB_PROOF_REQUIRED")
    rows = proof.get("branches")
    if not isinstance(rows, list) or len(rows) != 3:
        fail("THREE_REPOSITORY_BRANCH_CARDINALITY_INVALID")
    indexed = {row.get("owner"): row for row in rows if isinstance(row, dict) and isinstance(row.get("owner"), str)}
    if set(indexed) != {"watch", "phone", "controls"}:
        fail("THREE_REPOSITORY_OWNER_SET_INVALID")
    for owner, row in indexed.items():
        if row.get("status") != "PASS" or not all(is_sha(row.get(key)) for key in ("localHead", "remoteHead", "exactTree")):
            fail("THREE_REPOSITORY_BRANCH_PROOF_INVALID")
        blobs = row.get("changedBlobs")
        if not isinstance(blobs, list) or not blobs or any(not isinstance(blob, dict) or blob.get("exactByteMatch") is not True for blob in blobs):
            fail("THREE_REPOSITORY_CHANGED_BLOB_PROOF_INVALID")
    watch = indexed["watch"]
    if (watch.get("branch") != source_state["branch"] or watch.get("localHead") != source_state["head"]
            or watch.get("exactTree") != source_state["tree"]):
        fail("WATCH_SOURCE_NOT_BOUND_TO_FRESH_PUBLICATION_PROOF")
    return {
        "path": str(path.resolve()), "sha256": sha256_file(path), "watchBranch": watch["branch"],
        "watchHead": watch["localHead"], "watchTree": watch["exactTree"],
        "allThreeExactTreesAndChangedBytes": True,
    }


def verify_gate_bundle(source: Path, four_rung: Path, independent: Path, publication: Path) -> dict[str, Any]:
    state = git_state(source)
    identity = parse_public_identity(source)
    tracked = tracked_files(source)
    inputs = validate_compile_inputs(source, tracked)
    gates = {
        "fourRungV2Chain": validate_four_rung_proof(four_rung),
        "independentReader": validate_independent_reader_proof(independent),
        "freshThreeRepositoryPublication": validate_publication_proof(publication, state),
    }
    return {"source": state, "identity": identity, "compileInputs": inputs, "gates": gates}


def safe_new_directory(path: Path, code: str) -> None:
    if path.exists() or path.is_symlink():
        fail(code)
    if not path.parent.is_dir() or path.parent.is_symlink():
        fail("OUTPUT_PARENT_UNAVAILABLE_OR_SYMLINK")
    path.mkdir(mode=0o700, parents=False)


def stage_runtime(source: Path, stage: Path, verified: dict[str, Any]) -> dict[str, Any]:
    safe_new_directory(stage, "SOURCE_STAGE_MUST_BE_NEW")
    stage.chmod(0o700)
    mirror = stage / "runtime"
    mirror.mkdir(mode=0o700)
    receipt: list[dict[str, object]] = []
    for relative in runtime_allowlist(source):
        origin = source / relative
        target = mirror / relative
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        shutil.copyfile(origin, target)
        origin_hash, target_hash = sha256_file(origin), sha256_file(target)
        if origin_hash != target_hash:
            fail("RUNTIME_STAGE_EXACT_BYTE_COPY_MISMATCH")
        target.chmod(0o600)
        receipt.append({"path": relative, "bytes": origin.stat().st_size, "sha256": origin_hash})
    manifest = {
        "schemaVersion": "1.0",
        "status": "SOURCE_STAGE_READY_NOT_BUILT",
        "createdAt": now(),
        "source": verified["source"], "identity": verified["identity"],
        "gates": verified["gates"], "compileInputs": verified["compileInputs"],
        "runtimeOnlyTrackedFiles": receipt,
        "sourceBuildConfigurationNotCopied": True,
        "excluded": ["app/src/test", "APKs/AABs", "keys/keystores", "Gradle caches", "private physiology", "private exports"],
        "noStubOrSubstitute": True,
        "noBuildSigningUploadVersionOrSourceMutation": True,
    }
    json_write_new(stage / "runtime-stage-manifest.json", manifest)
    intent = {
        "schemaVersion": "1.0", "status": "PREPARED_NOT_AUTHORIZATION_TO_BUILD", "createdAt": now(),
        "sourceHead": verified["source"]["head"], "sourceTree": verified["source"]["tree"],
        "stageManifestSha256": sha256_file(stage / "runtime-stage-manifest.json"),
        "buildStarted": False,
        "oneBuildRule": "build-once creates a STARTED marker before Gradle and refuses every later invocation for this stage",
        "futureBuildSourceMustEqualStageHeadAndTree": True,
        "artifactInspectionAndSidecarOnlyAfterOneSuccessfulActualBuild": True,
    }
    json_write_new(stage / "build-intent.json", intent)
    return {
        "stage": str(stage.resolve()),
        "stageManifest": str((stage / "runtime-stage-manifest.json").resolve()),
        "stageManifestSha256": intent["stageManifestSha256"],
        "runtimeMemberCount": len(receipt),
    }


def source_stage_manifest(stage: Path, source: Path) -> dict[str, Any]:
    """Re-read all staged runtime bytes before the sole build; no source copy occurs."""
    if not stage.is_dir() or stage.is_symlink():
        fail("SOURCE_STAGE_UNAVAILABLE_OR_SYMLINK")
    if (stage / "build-attempt.json").exists():
        fail("BUILD_ALREADY_STARTED_OR_OUTCOME_UNCERTAIN_NO_AUTOMATIC_DUPLICATE")
    manifest_path = stage / "runtime-stage-manifest.json"
    intent_path = stage / "build-intent.json"
    manifest = json_load(manifest_path, "SOURCE_STAGE_MANIFEST_UNREADABLE")
    intent = json_load(intent_path, "BUILD_INTENT_UNREADABLE")
    if manifest.get("status") != "SOURCE_STAGE_READY_NOT_BUILT" or intent.get("buildStarted") is not False:
        fail("SOURCE_STAGE_NOT_PREPARED_FOR_SINGLE_BUILD")
    state = git_state(source)
    staged_state = manifest.get("source")
    if not isinstance(staged_state, dict) or state.get("head") != staged_state.get("head") or state.get("tree") != staged_state.get("tree"):
        fail("BUILD_SOURCE_NOT_IDENTICAL_TO_PARENT_APPROVED_STAGE")
    if intent.get("stageManifestSha256") != sha256_file(manifest_path):
        fail("BUILD_INTENT_STAGE_MANIFEST_BINDING_MISMATCH")
    runtime = manifest.get("runtimeOnlyTrackedFiles")
    if not isinstance(runtime, list) or not runtime:
        fail("SOURCE_STAGE_RUNTIME_INVENTORY_INVALID")
    stage_root = stage / "runtime"
    for item in runtime:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            fail("SOURCE_STAGE_RUNTIME_INVENTORY_INVALID")
        relative = item["path"]
        staged = stage_root / relative
        current = source / relative
        if (not staged.is_file() or staged.is_symlink() or not current.is_file() or current.is_symlink()
                or staged.stat().st_size != item.get("bytes")
                or sha256_file(staged) != item.get("sha256")
                or sha256_file(current) != item.get("sha256")):
            fail("SOURCE_STAGE_OR_BUILD_SOURCE_RUNTIME_BYTE_MISMATCH")
    return manifest


def tool_pins() -> dict[str, object]:
    pins = (
        (AAPT2_LAUNCHER, AAPT2_LAUNCHER_SHA256),
        (AAPT2_REAL, AAPT2_REAL_SHA256),
        (GRADLE, GRADLE_SHA256),
        (ARM_JAVA, ARM_JAVA_SHA256),
        (QEMU_X86_64, QEMU_X86_64_SHA256),
        (APT, APT_SHA256),
        (SDK_AAPT2, SDK_AAPT2_SHA256),
        (ZIPALIGN, ZIPALIGN_SHA256),
        (DEXDUMP, DEXDUMP_SHA256),
        (APKSIGNER_JAR, APKSIGNER_JAR_SHA256),
    )
    values: dict[str, object] = {}
    for path, expected in pins:
        if not path.is_file() or path.is_symlink() or sha256_file(path) != expected:
            fail("PINNED_TOOL_INPUT_MISMATCH_OR_UNAVAILABLE")
        values[str(path)] = {"sha256": expected, "bytes": path.stat().st_size}
    if not X86_SYSROOT.is_dir() or X86_SYSROOT.is_symlink() or not ANDROID_SDK.is_dir() or ANDROID_SDK.is_symlink():
        fail("PINNED_TOOL_ROUTE_DIRECTORY_UNAVAILABLE")
    if not GRADLE_USER_HOME.is_dir() or GRADLE_USER_HOME.is_symlink():
        fail("PINNED_GRADLE_CACHE_UNAVAILABLE")
    return values


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def predecessor_compatibility_dex() -> bytes:
    if not PREDECESSOR_APK.is_file() or PREDECESSOR_APK.is_symlink() or sha256_file(PREDECESSOR_APK) != PREDECESSOR_APK_SHA256:
        fail("WATCH83_ADMITTED_ARTIFACT_PIN_MISMATCH")
    try:
        with zipfile.ZipFile(PREDECESSOR_APK) as archive:
            if archive.testzip() is not None or archive.namelist().count(COMPAT_DEX_NAME) != 1:
                fail("WATCH83_COMPATIBILITY_DEX_UNAVAILABLE")
            payload = archive.read(COMPAT_DEX_NAME)
    except zipfile.BadZipFile as error:
        raise Stop("WATCH83_ADMITTED_ARTIFACT_INVALID") from error
    if sha256_bytes(payload) != COMPAT_DEX_SHA256:
        fail("WATCH83_COMPATIBILITY_DEX_PIN_MISMATCH")
    return payload


def dex_map(apk: Path, exact_names: set[str]) -> dict[str, str]:
    try:
        with zipfile.ZipFile(apk) as archive:
            if archive.testzip() is not None:
                fail("APK_ZIP_CRC_FAILURE")
            names = archive.namelist()
            dex_names = {name for name in names if re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)}
            if dex_names != exact_names or len(names) != len(set(names)):
                fail("SOURCE_BUILT_DEX_COMPOSITION_UNEXPECTED")
            return {name: sha256_bytes(archive.read(name)) for name in sorted(dex_names)}
    except zipfile.BadZipFile as error:
        raise Stop("APK_ARCHIVE_INVALID") from error


def require_dex_map(actual: dict[str, str], expected: dict[str, str], code: str) -> None:
    if actual != expected:
        fail(code)


def write_unsigned(base: Path, unsigned: Path, compatibility: bytes) -> None:
    if unsigned.exists() or unsigned.is_symlink():
        fail("UNSIGNED_OUTPUT_ALREADY_EXISTS")
    try:
        with zipfile.ZipFile(base) as original, zipfile.ZipFile(unsigned, "x", allowZip64=True) as output:
            for info in original.infolist():
                name = info.filename
                if name.startswith("META-INF/") and (name.endswith(SIGNATURE_SUFFIXES) or name == "META-INF/MANIFEST.MF"):
                    continue
                output.writestr(info, original.read(info))
            addition = zipfile.ZipInfo(COMPAT_DEX_NAME)
            addition.compress_type = zipfile.ZIP_STORED
            addition.external_attr = 0o644 << 16
            output.writestr(addition, compatibility)
    except zipfile.BadZipFile as error:
        raise Stop("SOURCE_BUILT_APK_ARCHIVE_INVALID") from error


def run_no_output(command: list[str], env: dict[str, str], cwd: Path, timeout: int, code: str) -> None:
    """Never save or print subprocess output: signing/build configuration may be sensitive."""
    try:
        result = subprocess.run(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise Stop(code) from error
    if result.returncode != 0:
        fail(code)


def signed_with_existing_route(aligned: Path, final: Path, build_source: Path) -> None:
    """Read existing config in memory only; do not write/print the signing values."""
    build_file = build_source / "app/build.gradle"
    try:
        gradle = build_file.read_text(encoding="utf-8")
        signing = gradle.split("signingConfigs {", 1)[1].split("defaultConfig {", 1)[0]
    except (OSError, IndexError) as error:
        raise Stop("EXISTING_AUTHORIZED_SIGNING_ROUTE_UNAVAILABLE") from error

    def field(label: str, pattern: str) -> str:
        match = re.search(pattern, signing)
        if not match:
            fail("EXISTING_AUTHORIZED_SIGNING_FIELD_UNAVAILABLE:" + label)
        return match.group(1)

    # Values remain local variables / child environment only.  Neither is
    # emitted to stdout, JSON, a log, an archive, or an exception message.
    keystore = (build_source / "app" / field("storeFile", r"storeFile\s+file\('([^']+)'\)")).resolve()
    alias = field("keyAlias", r"keyAlias\s+'([^']+)'")
    store_password = field("storePassword", r"storePassword\s+'([^']+)'")
    key_password = field("keyPassword", r"keyPassword\s+'([^']+)'")
    if not keystore.is_file() or keystore.is_symlink() or final.exists():
        fail("EXISTING_AUTHORIZED_SIGNING_IDENTITY_UNAVAILABLE")
    env = os.environ.copy()
    env["HUGR_BUILD_KS_PASS"] = store_password
    env["HUGR_BUILD_KEY_PASS"] = key_password
    run_no_output([
        str(ARM_JAVA), "-jar", str(APKSIGNER_JAR), "sign", "--ks", str(keystore),
        "--ks-key-alias", alias, "--ks-pass", "env:HUGR_BUILD_KS_PASS",
        "--key-pass", "env:HUGR_BUILD_KEY_PASS", "--v1-signing-enabled", "false",
        "--v2-signing-enabled", "false", "--v3-signing-enabled", "true", "--out", str(final), str(aligned),
    ], env, build_source, 600, "EXISTING_AUTHORIZED_APKSIGNER_ROUTE_STOP")


def make_build_environment() -> dict[str, str]:
    env = os.environ.copy()
    # Prevent accidental use of arbitrary caller credentials in this local lane.
    for key in ("GH_TOKEN", "GITHUB_TOKEN", "EXPO_TOKEN", "EAS_ACCESS_TOKEN"):
        env.pop(key, None)
    env.update({
        "JAVA_HOME": str(ARM_JAVA_HOME), "ANDROID_HOME": str(ANDROID_SDK),
        "ANDROID_SDK_ROOT": str(ANDROID_SDK), "GRADLE_USER_HOME": str(GRADLE_USER_HOME),
        "HUGR_ANDROID_BUILD_TOOLS_DIR": str(BUILD_TOOLS),
        "HUGR_QEMU_X86_64": str(QEMU_X86_64), "HUGR_X86_64_SYSROOT": str(X86_SYSROOT),
        "PYTHONDONTWRITEBYTECODE": "1",
    })
    return env


def build_once(stage: Path, build_source: Path, artifact_dir: Path) -> dict[str, Any]:
    # The marker is written before any build command.  Thus a killed/ambiguous
    # Gradle process is never silently retried by this program.
    stage_manifest = source_stage_manifest(stage, build_source)
    gates = stage_manifest.get("gates")
    if not isinstance(gates, dict):
        fail("SOURCE_STAGE_GATE_BUNDLE_INVALID")
    try:
        current = verify_gate_bundle(
            build_source,
            Path(gates["fourRungV2Chain"]["path"]),
            Path(gates["independentReader"]["path"]),
            Path(gates["freshThreeRepositoryPublication"]["path"]),
        )
    except (KeyError, TypeError) as error:
        raise Stop("SOURCE_STAGE_GATE_BUNDLE_INVALID") from error
    if current["source"] != stage_manifest.get("source") or current["identity"] != stage_manifest.get("identity"):
        fail("BUILD_SOURCE_OR_GATE_STATE_CHANGED_AFTER_STAGE")
    if artifact_dir.exists() or artifact_dir.is_symlink():
        fail("ARTIFACT_OUTPUT_DIRECTORY_MUST_BE_NEW")
    if not artifact_dir.parent.is_dir() or artifact_dir.parent.is_symlink():
        fail("ARTIFACT_OUTPUT_PARENT_UNAVAILABLE_OR_SYMLINK")
    tools = tool_pins()
    compatibility = predecessor_compatibility_dex()
    attempt = stage / "build-attempt.json"
    json_write_new(attempt, {
        "schemaVersion": "1.0", "status": "STARTED_OUTCOME_NOT_YET_KNOWN_NO_RETRY_BY_DRIVER",
        "startedAt": now(), "sourceHead": current["source"]["head"], "sourceTree": current["source"]["tree"],
        "stageManifestSha256": sha256_file(stage / "runtime-stage-manifest.json"),
        "gradleTask": ":app:assembleDebug", "oneBuildRule": True,
    })
    safe_new_directory(artifact_dir, "ARTIFACT_OUTPUT_DIRECTORY_MUST_BE_NEW")
    artifact_dir.chmod(0o700)
    env = make_build_environment()
    # This is the sole actual build invocation and uses the exact Gradle/JDK/
    # AAPT2/QEMU route already proven by compile-proof.  No upload follows.
    run_no_output([
        str(GRADLE), "--offline", "--no-daemon", "--console=plain",
        "-Pandroid.aapt2FromMavenOverride=" + str(AAPT2_LAUNCHER), ":app:assembleDebug",
    ], env, build_source, 3600, "SOLE_ASSEMBLE_DEBUG_BUILD_STOP")
    base = build_source / "app/build/outputs/apk/debug/app-debug.apk"
    if not base.is_file() or base.is_symlink():
        fail("SOLE_BUILD_OUTPUT_APK_ABSENT")
    before = dex_map(base, EXPECTED_BASE_DEX)
    unsigned = artifact_dir / "unsigned-compat.apk"
    aligned = artifact_dir / "aligned-compat.apk"
    final = artifact_dir / "HUGR_Watch84w_0.84.0_positional-v2-duration-ladder-candidate.apk"
    write_unsigned(base, unsigned, compatibility)
    expected = dict(before)
    expected[COMPAT_DEX_NAME] = COMPAT_DEX_SHA256
    require_dex_map(dex_map(unsigned, EXPECTED_FINAL_DEX), expected, "DEX_CHANGED_WHILE_ADDING_EXACT_COMPATIBILITY_PAYLOAD")
    run_no_output([str(QEMU_X86_64), "-L", str(X86_SYSROOT), str(ZIPALIGN), "-f", "-p", "4", str(unsigned), str(aligned)],
                  env, artifact_dir, 600, "PINNED_QEMU_ZIPALIGN_ROUTE_STOP")
    require_dex_map(dex_map(aligned, EXPECTED_FINAL_DEX), expected, "DEX_CHANGED_BY_ZIPALIGN")
    signed_with_existing_route(aligned, final, build_source)
    require_dex_map(dex_map(final, EXPECTED_FINAL_DEX), expected, "DEX_CHANGED_BY_EXISTING_SIGNER_ROUTE")
    # Admission is inspection-only and occurs only after all prior byte checks.
    inspection = artifact_dir / "inspection"
    run_no_output([sys.executable, str(VALIDATOR), str(final), "--out", str(inspection)], env, artifact_dir, 1500,
                  "READ_ONLY_WATCH84_ARTIFACT_ADMISSION_STOP")
    proof = json_load(inspection / "watch84-candidate-artifact-admission.json", "WATCH84_ARTIFACT_ADMISSION_RECEIPT_UNAVAILABLE")
    if proof.get("status") != "PASS":
        fail("WATCH84_ARTIFACT_ADMISSION_NOT_PASS")
    sidecar = final.with_suffix(final.suffix + ".sha256")
    if sidecar.exists() or sidecar.is_symlink():
        fail("ARTIFACT_SIDECAR_ALREADY_EXISTS")
    final_sha = sha256_file(final)
    sidecar.write_text(final_sha + "  " + final.name + "\n", encoding="utf-8")
    sidecar.chmod(0o600)
    result = {
        "schemaVersion": "1.0", "status": "LOCAL_SINGLE_BUILD_AND_READ_ONLY_ADMISSION_PASS_NO_UPLOAD",
        "builtAt": now(), "source": current["source"], "identity": current["identity"],
        "gates": current["gates"], "pinnedTools": tools,
        "predecessor": {"apkSha256": PREDECESSOR_APK_SHA256, "compatibilityDexSha256": COMPAT_DEX_SHA256,
                        "signerSha256": WATCH83_SIGNER_SHA256},
        "dexEquality": {"sourceBuiltFourDex": before, "afterExactCompatAppend": dex_map(unsigned, EXPECTED_FINAL_DEX),
                        "afterQemuZipalign": dex_map(aligned, EXPECTED_FINAL_DEX), "afterExistingSigner": dex_map(final, EXPECTED_FINAL_DEX)},
        "artifact": {"path": str(final.resolve()), "sha256": final_sha, "bytes": final.stat().st_size,
                     "sha256Sidecar": str(sidecar.resolve()), "readOnlyAdmission": str((inspection / "watch84-candidate-artifact-admission.json").resolve())},
        "noUploadInstallDeviceCollectionOrExternalService": True,
        "noSourceVersionOrSourceTrackedByteMutationByDriver": git_state(build_source) == current["source"],
    }
    json_write_new(artifact_dir / "watch84-local-build-result.json", result)
    # The successful terminal record does not erase the pre-command STARTED marker.
    (stage / "build-finished.json").write_text(json.dumps({"status": "FINISHED", "result": str((artifact_dir / "watch84-local-build-result.json").resolve()), "finishedAt": now()}, indent=2) + "\n", encoding="utf-8")
    return result


def preflight(args: argparse.Namespace) -> dict[str, Any]:
    verified = verify_gate_bundle(args.source, args.four_rung_proof, args.independent_reader_proof, args.publication_proof)
    staged = stage_runtime(args.source, args.stage, verified)
    return {"status": "PREPARED_PARENT_GATED_RUNTIME_STAGE_ONLY", **staged,
            "noBuildSigningArtifactUploadInstallDeviceOrSourceMutation": True}


def arguments() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    prepare = commands.add_parser("preflight", help="verify parent proofs and create a runtime-only custody stage; no build")
    prepare.add_argument("--source", type=Path, required=True, help="parent-preserved clean Watch84 source snapshot")
    prepare.add_argument("--stage", type=Path, required=True, help="new non-artifact runtime-only stage directory")
    prepare.add_argument("--four-rung-proof", type=Path, required=True, help="completed parent-owned V2 four-rung runner summary")
    prepare.add_argument("--independent-reader-proof", type=Path, required=True, help="completed independent-reader PASS proof")
    prepare.add_argument("--publication-proof", type=Path, required=True, help="fresh all-three Git head/tree/blob PASS proof")
    build = commands.add_parser("build-once", help="one future build only, after a successful preflight")
    build.add_argument("--stage", type=Path, required=True, help="existing preflight stage; marked before Gradle")
    build.add_argument("--build-source", type=Path, required=True, help="parent-preserved clean Watch84 build snapshot exactly matching stage")
    build.add_argument("--artifact-dir", type=Path, required=True, help="new local artifact output directory; no upload")
    return parser


def main() -> None:
    os.umask(0o077)
    args = arguments().parse_args()
    result = preflight(args) if args.mode == "preflight" else build_once(args.stage.resolve(), args.build_source.resolve(), args.artifact_dir.resolve())
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except Stop as error:
        print("WATCH84_FRESH_STAGE_BUILD_DRIVER_STOP:" + str(error), file=sys.stderr)
        raise SystemExit(1)
