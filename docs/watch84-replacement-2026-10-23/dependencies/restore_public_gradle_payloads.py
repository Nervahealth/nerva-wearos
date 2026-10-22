#!/usr/bin/env python3
"""Restore exactly four verified public Google Maven JARs into a retained Gradle cache.

This program does not invoke Gradle or alter project source, build configuration,
cache metadata, POM descriptors, signing material, or any artifact other than the
four target JAR payload paths.  It fetches only hard-coded Google Maven URLs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET
import zipfile

REPOSITORY = "https://dl.google.com/dl/android/maven2"
CACHE_ROOT = Path("/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1")
DEFAULT_OUT = Path("/home/ubuntu/HUGR_WATCH84_REPLACEMENT_2026_10_23/dependencies")
TARGETS = (
    ("androidx.wear.tiles", "tiles-proto", "1.2.0"),
    ("androidx.wear.protolayout", "protolayout-proto", "1.0.0"),
    ("androidx.concurrent", "concurrent-futures", "1.1.0"),
    ("androidx.resourceinspection", "resourceinspection-annotation", "1.0.1"),
)
ALGORITHMS = ("sha256", "sha1", "md5")


class RestoreFailure(RuntimeError):
    pass


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def digest_bytes(payload: bytes) -> dict[str, str]:
    return {algorithm: hashlib.new(algorithm, payload).hexdigest() for algorithm in ALGORITHMS}


def regular_file(path: Path, label: str) -> None:
    try:
        item = path.lstat()
    except FileNotFoundError as error:
        raise RestoreFailure(f"{label}_MISSING:{path}") from error
    if stat.S_ISLNK(item.st_mode) or not stat.S_ISREG(item.st_mode):
        raise RestoreFailure(f"{label}_NOT_REGULAR_OR_IS_SYMLINK:{path}")


def safe_existing_directory(path: Path, label: str) -> None:
    try:
        item = path.lstat()
    except FileNotFoundError as error:
        raise RestoreFailure(f"{label}_MISSING:{path}") from error
    if stat.S_ISLNK(item.st_mode) or not stat.S_ISDIR(item.st_mode):
        raise RestoreFailure(f"{label}_NOT_DIRECTORY_OR_IS_SYMLINK:{path}")


def safe_coordinate_directory(group: str, artifact: str, version: str) -> Path:
    """Return a lexical cache directory, creating only missing target ancestors."""
    safe_existing_directory(CACHE_ROOT, "CACHE_ROOT")
    current = CACHE_ROOT
    for name in (group, artifact, version):
        if name in {"", ".", ".."} or "/" in name or "\\" in name:
            raise RestoreFailure("UNSAFE_COORDINATE_COMPONENT")
        candidate = current / name
        if candidate.exists() or candidate.is_symlink():
            safe_existing_directory(candidate, "CACHE_COORDINATE_ANCESTOR")
        else:
            candidate.mkdir(mode=0o700)
            safe_existing_directory(candidate, "NEW_CACHE_COORDINATE_ANCESTOR")
        current = candidate
    try:
        current.relative_to(CACHE_ROOT)
    except ValueError as error:
        raise RestoreFailure("CACHE_PATH_ESCAPES_ROOT") from error
    return current


def get_url(url: str, optional_404: bool = False) -> tuple[int, bytes | None]:
    """GET only a fixed HTTPS URL; allow a sidecar 404 to be evidence of absence."""
    if not url.startswith(REPOSITORY + "/"):
        raise RestoreFailure("SOURCE_URL_OUTSIDE_GOOGLE_MAVEN")
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "Watch84-authorized-dependency-restorer/1.0"})
            with urlopen(request, timeout=45) as response:
                status = getattr(response, "status", response.getcode())
                payload = response.read()
            if status != 200:
                raise RestoreFailure(f"UNEXPECTED_HTTP_STATUS:{status}:{url}")
            return status, payload
        except HTTPError as error:
            if error.code == 404 and optional_404:
                return 404, None
            if error.code in {429, 500, 502, 503, 504} and attempt < 2:
                time.sleep(attempt + 1)
                continue
            raise RestoreFailure(f"HTTP_{error.code}:{url}") from error
        except (URLError, TimeoutError, OSError) as error:
            if attempt < 2:
                time.sleep(attempt + 1)
                continue
            raise RestoreFailure(f"NETWORK_ERROR:{url}") from error
    raise RestoreFailure(f"NETWORK_RETRY_EXHAUSTED:{url}")


def sidecar_fact(base_url: str, algorithm: str, computed: str) -> dict[str, object]:
    url = base_url + "." + algorithm
    status, raw = get_url(url, optional_404=True)
    result: dict[str, object] = {"url": url, "httpStatus": status}
    if status == 404:
        result.update({"availability": "UNAVAILABLE_404", "publishedDigest": None, "matchesComputed": None})
        return result
    assert raw is not None
    text = raw.decode("utf-8", errors="strict").strip()
    token = text.split()[0].lower() if text.split() else ""
    expected_length = hashlib.new(algorithm).digest_size * 2
    if len(token) != expected_length or any(character not in "0123456789abcdef" for character in token):
        raise RestoreFailure(f"PUBLISHED_{algorithm.upper()}_SIDECAR_MALFORMED:{url}")
    if token != computed:
        raise RestoreFailure(f"PUBLISHED_{algorithm.upper()}_MISMATCH:{url}")
    result.update({
        "availability": "AVAILABLE", "publishedDigest": token,
        "publishedRaw": text, "matchesComputed": True,
    })
    return result


def source_urls(group: str, artifact: str, version: str) -> tuple[str, str]:
    group_path = group.replace(".", "/")
    prefix = f"{REPOSITORY}/{group_path}/{artifact}/{version}/{artifact}-{version}"
    return prefix + ".jar", prefix + ".pom"


def pom_coordinates(payload: bytes, expected: tuple[str, str, str]) -> dict[str, object]:
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as error:
        raise RestoreFailure("REMOTE_POM_XML_INVALID") from error
    def find_text(node: ET.Element, local_name: str) -> str | None:
        found = node.find("{*}" + local_name)
        if found is None or found.text is None:
            return None
        return found.text.strip()
    parent = root.find("{*}parent")
    parent_group = find_text(parent, "groupId") if parent is not None else None
    parent_version = find_text(parent, "version") if parent is not None else None
    group = find_text(root, "groupId") or parent_group
    artifact = find_text(root, "artifactId")
    version = find_text(root, "version") or parent_version
    actual = (group, artifact, version)
    if actual != expected:
        raise RestoreFailure(f"REMOTE_POM_COORDINATE_MISMATCH:{actual!r}")
    return {
        "effectiveCoordinates": {"group": group, "artifact": artifact, "version": version},
        "exactCoordinateMatch": True,
        "rootDeclaredGroup": find_text(root, "groupId"),
        "rootDeclaredArtifact": artifact,
        "rootDeclaredVersion": find_text(root, "version"),
        "parentGroup": parent_group,
        "parentVersion": parent_version,
    }


def cached_pom_equality(coordinate_dir: Path, filename: str, remote: bytes) -> dict[str, object]:
    paths = sorted(coordinate_dir.rglob(filename))
    checked: list[dict[str, object]] = []
    for path in paths:
        # Only POM descriptors under the exact coordinate are inspected, never altered.
        regular_file(path, "CACHED_POM")
        payload = path.read_bytes()
        equal = payload == remote
        checked.append({"path": str(path), "bytes": len(payload), "sha256": digest_bytes(payload)["sha256"], "equalToRemote": equal})
        if not equal:
            raise RestoreFailure(f"CACHED_POM_BYTES_DIFFER_FROM_REMOTE:{path}")
    return {
        "descriptorsFound": len(checked),
        "allEqualToRemote": all(item["equalToRemote"] for item in checked),
        "descriptors": checked,
    }


def jar_validation(payload: bytes) -> dict[str, object]:
    try:
        from io import BytesIO
        with zipfile.ZipFile(BytesIO(payload)) as archive:
            bad = archive.testzip()
            if bad is not None:
                raise RestoreFailure(f"JAR_CRC_FAILURE_MEMBER:{bad}")
            members = archive.namelist()
            if len(members) != len(set(members)):
                raise RestoreFailure("JAR_DUPLICATE_MEMBER_NAMES")
            manifest_name = "META-INF/MANIFEST.MF"
            manifest_present = manifest_name in members
            manifest_sha = None
            version_info: dict[str, str] = {}
            if manifest_present:
                manifest = archive.read(manifest_name)
                manifest_sha = digest_bytes(manifest)["sha256"]
                # Manifest folding is irrelevant to the small version attribute set;
                # retain only present version-related fields without fabricating one.
                unfolded = manifest.decode("utf-8", errors="replace").replace("\r\n ", "").replace("\n ", "")
                for line in unfolded.splitlines():
                    if ":" not in line:
                        continue
                    key, value = line.split(":", 1)
                    if key in {"Implementation-Version", "Specification-Version", "Bundle-Version", "Bundle-SymbolicName", "Automatic-Module-Name"}:
                        version_info[key] = value.strip()
            return {
                "zipOpen": True, "crcCheck": "PASS", "memberCount": len(members),
                "memberList": members, "manifestPresent": manifest_present,
                "manifestSha256": manifest_sha, "versionInfo": version_info,
                "versionInfoPresent": bool(version_info),
            }
    except zipfile.BadZipFile as error:
        raise RestoreFailure("JAR_NOT_A_VALID_ZIP") from error


def install_or_verify_jar(coordinate_dir: Path, filename: str, sha1: str, payload: bytes) -> dict[str, object]:
    if len(sha1) != 40 or any(character not in "0123456789abcdef" for character in sha1):
        raise RestoreFailure("INVALID_COMPUTED_SHA1")
    hash_dir = coordinate_dir / sha1
    if hash_dir.exists() or hash_dir.is_symlink():
        safe_existing_directory(hash_dir, "CACHE_JAR_HASH_DIRECTORY")
    else:
        hash_dir.mkdir(mode=0o700)
        safe_existing_directory(hash_dir, "NEW_CACHE_JAR_HASH_DIRECTORY")
    target = hash_dir / filename
    expected_path = CACHE_ROOT / coordinate_dir.relative_to(CACHE_ROOT) / sha1 / filename
    if target != expected_path:
        raise RestoreFailure("CACHE_TARGET_PATH_COMPOSITION_MISMATCH")
    created = False
    if target.exists() or target.is_symlink():
        regular_file(target, "EXISTING_CACHE_JAR")
        if target.read_bytes() != payload:
            raise RestoreFailure(f"EXISTING_CACHE_JAR_BYTES_DIFFER:{target}")
    else:
        # O_EXCL / xb prevents silent overwrite if the cache is concurrently changed.
        try:
            with target.open("xb") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            target.chmod(0o600)
            created = True
        except FileExistsError as error:
            raise RestoreFailure(f"CACHE_JAR_RACE_AT_TARGET:{target}") from error
    regular_file(target, "CACHE_JAR_READBACK")
    readback = target.read_bytes()
    if readback != payload:
        raise RestoreFailure(f"CACHE_JAR_READBACK_BYTES_DIFFER:{target}")
    return {
        "path": str(target), "createdByThisRun": created,
        "readbackBytes": len(readback), "readbackSha256": digest_bytes(readback)["sha256"],
        "readbackSha1": digest_bytes(readback)["sha1"], "exactByteReadback": True,
    }


def write_new(path: Path, text: str) -> None:
    if path.exists() or path.is_symlink():
        raise RestoreFailure(f"OUTPUT_FILE_EXISTS_OR_IS_SYMLINK:{path}")
    with path.open("x", encoding="utf-8") as handle:
        handle.write(text)
        handle.flush()
        os.fsync(handle.fileno())
    path.chmod(0o600)


def receipt_markdown(proof: dict[str, object]) -> str:
    rows = proof["dependencies"]
    lines = [
        "# Watch84 public dependency restoration receipt",
        "",
        "**Status: PASS.** Exactly four requested JAR payloads were retrieved from Google Maven, matched every available published SHA-256/SHA-1/MD5 sidecar, passed ZIP CRC validation, and were read back unchanged from the isolated retained Gradle cache.",
        "",
        "Google’s Android repository documentation identifies Google Maven and its `https://dl.google.com/dl/android/maven2/` alternative URL as the source for AndroidX and Wear OS artifacts: [Android remote repositories](https://developer.android.com/build/remote-repositories).",
        "",
    ]
    for item in rows:
        lines.extend([
            f"- `{item['coordinate']}` — SHA-256 `{item['sha256']}`; SHA-1 `{item['sha1']}`; {item['bytes']} bytes.",
            f"  Cache readback: `{item['cachePath']['path']}`.",
        ])
    lines.extend([
        "",
        "The existing cached POM descriptor for each coordinate exactly matched the authoritative POM. No PGP/signature assertion was made; checksum sidecars were treated only as published digest metadata. No Gradle, build, source, configuration, or signing action was performed.",
        "",
    ])
    return "\n".join(lines)


def run(out: Path, script_path: Path) -> None:
    if out.exists() or out.is_symlink():
        raise RestoreFailure(f"OUTPUT_DIRECTORY_ALREADY_EXISTS_OR_IS_SYMLINK:{out}")
    safe_existing_directory(out.parent, "OUTPUT_PARENT")
    out.mkdir(mode=0o700)
    proof: dict[str, object] = {
        "schemaVersion": "1.0",
        "status": "PASS",
        "createdAt": utc_now(),
        "scope": "EXACTLY_FOUR_AUTHORIZED_PUBLIC_GRADLE_JAR_PAYLOADS",
        "repositoryAuthority": {
            "documentationURL": "https://developer.android.com/build/remote-repositories",
            "repositoryBaseURL": REPOSITORY,
            "repositoryBaseIsGoogleMavenAlternativeURL": True,
        },
        "cacheRoot": str(CACHE_ROOT),
        "noGradleBuildSourceConfigurationOrSigningAction": True,
        "noPgpOrOtherSignatureProofAsserted": True,
        "dependencies": [],
    }
    try:
        with tempfile.TemporaryDirectory(prefix="watch84-public-maven-", dir="/home/ubuntu/jobs/job_ADAttvFc"):
            for group, artifact, version in TARGETS:
                coordinate = f"{group}:{artifact}:{version}"
                coordinate_dir = safe_coordinate_directory(group, artifact, version)
                jar_url, pom_url = source_urls(group, artifact, version)
                jar_http, jar = get_url(jar_url)
                pom_http, pom = get_url(pom_url)
                assert jar is not None and pom is not None
                jar_digests = digest_bytes(jar)
                pom_digests = digest_bytes(pom)
                jar_sidecars = {algorithm: sidecar_fact(jar_url, algorithm, jar_digests[algorithm]) for algorithm in ALGORITHMS}
                pom_sidecars = {algorithm: sidecar_fact(pom_url, algorithm, pom_digests[algorithm]) for algorithm in ALGORITHMS}
                pom_fact = pom_coordinates(pom, (group, artifact, version))
                cached = cached_pom_equality(coordinate_dir, f"{artifact}-{version}.pom", pom)
                jar_fact = jar_validation(jar)
                installed = install_or_verify_jar(coordinate_dir, f"{artifact}-{version}.jar", jar_digests["sha1"], jar)
                item = {
                    "coordinate": coordinate,
                    "sourceURLs": {"jar": jar_url, "pom": pom_url},
                    "sourceHttp": {"jar": jar_http, "pom": pom_http},
                    "publicationChecksumFacts": {"jar": jar_sidecars, "pom": pom_sidecars},
                    "sha256": jar_digests["sha256"], "sha1": jar_digests["sha1"], "md5": jar_digests["md5"], "bytes": len(jar),
                    "pom": {"sha256": pom_digests["sha256"], "sha1": pom_digests["sha1"], "md5": pom_digests["md5"], "bytes": len(pom), **pom_fact, "cachedPOMEquality": cached},
                    "jarValidation": jar_fact,
                    "cachePath": installed,
                }
                proof["dependencies"].append(item)
        if len(proof["dependencies"]) != 4:
            raise RestoreFailure("EXACT_FOUR_DEPENDENCY_CARDINALITY_FAILED")
        # Preserve the reusable exact tool alongside its proof, then write final output.
        script_target = out / "restore_public_gradle_payloads.py"
        shutil.copyfile(script_path, script_target)
        script_target.chmod(0o700)
        write_new(out / "dependency-restoration-proof.json", json.dumps(proof, indent=2, sort_keys=True) + "\n")
        write_new(out / "dependency-receipt.md", receipt_markdown(proof))
    except Exception:
        # A partial output is more hazardous than useful, and target JARs are never removed.
        # Preserve any created cache payloads for forensic exact readback rather than deleting cache content.
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="new output directory for proof, receipt, and reusable script")
    args = parser.parse_args()
    try:
        run(args.out, Path(__file__).resolve())
    except RestoreFailure as error:
        print("DEPENDENCY_RESTORATION_BLOCKER:" + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
