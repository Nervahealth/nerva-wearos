# Watch84 replacement offline-cache recognition diagnosis — 23 October 2026

## Finding

**The selected cache is the restored target, and the four exact JAR payloads exist and verify.  That is not enough to establish that Gradle can select those payloads in offline mode.**

The retained redacted failure is from `:app:checkDebugDuplicateClasses` while resolving `:app:debugRuntimeClasspath`.  For each of the four authorized coordinates it reports a transform input JAR and then `No cached version available for offline mode`:

| Exact coordinate | Verified JAR bytes | Verified SHA-1 | Failure reports offline-unavailable |
|---|---:|---|---|
| `androidx.wear.tiles:tiles-proto:1.2.0` | 45,726 | `3090e207e4ebd46e46c159822ab293c9fc4a42c6` | Yes |
| `androidx.wear.protolayout:protolayout-proto:1.0.0` | 1,739,194 | `abb132807aa40f10fb02639c770289e87366d550` | Yes |
| `androidx.concurrent:concurrent-futures:1.1.0` | 25,987 | `50b7fb98350d5f42a4e49704b03278542293ba48` | Yes |
| `androidx.resourceinspection:resourceinspection-annotation:1.0.1` | 2,336 | `8c21f8ff5d96d5d52c948707f7e4d6ca6773feef` | Yes |

`dependencies/dependency-restoration-proof.json` is `status: PASS`.  For all four it records exact payload readback, exact POM-coordinate identity, and ZIP CRC PASS; it identifies the target payload root as:

```text
/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home/caches/modules-2/files-2.1
```

The unchanged Watch84 driver selects the parent `GRADLE_USER_HOME` exactly:

```text
/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home
```

Thus the driver-selected `modules-2/files-2.1` location is the same location proven by the restoration record.  This rules out a simple *different Gradle user home* explanation for this failure.

## What the bounded cache inspection does and does not show

The selected cache has Gradle `metadata-2.105` with `descriptors`, `module-artifact.bin`, `module-metadata.bin`, and `resource-at-url.bin`.  Each exact coordinate also has a repository-keyed `descriptor.bin` path beneath `metadata-2.105/descriptors`; the path-key suffix is the same for all four.  Literal coordinate text is present in `resource-at-url.bin` for all four.  The payload tree presently also contains named POM and `.module` files for the coordinates.

These are **presence observations only**.  The binary metadata was not decoded, changed, deleted, or regenerated.  In particular, this inspection cannot prove that the selected repository identity, resource-to-artifact mapping, metadata freshness/negative-cache state, variant/attribute selection, or transform input lookup is valid for the failing `debugRuntimeClasspath` request.

> **Payload existence** means the verified JAR bytes are in `files-2.1`.
>
> **Offline recognizability** additionally requires Gradle's repository-scoped cache metadata to recognize the selected module/version/artifact for the exact repository and requested attributes.  The failure proves the latter was unavailable to this offline resolution attempt; it does not identify which metadata record or state is defective.

Accordingly, there is no evidence here for a dependency-version, repository-definition, source, SDK, signer, or binary-JAR change.  Nor is an exact binary-cache cause proven.

## Smallest proposed remedy — separate approval required

If further action is authorized, the smallest technically appropriate remedy is **one Gradle-managed, exact-coordinate, dependency-only metadata-resolution operation** in the *same* `GRADLE_USER_HOME`, not manual cache surgery:

1. Use the existing Gradle distribution and the selected `/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home`.
2. Do **not** run `assemble`, any Android build task, signing, artifact admission, or the Watch84 driver.
3. Resolve four detached, non-transitive configurations containing only the four exact `group:artifact:version` coordinates, through the already-authorized public repository route.  Match the runtime-library/JAR attributes observed in the failure; use JVM 8 for the three reported with JVM 8 and JVM 7 for `concurrent-futures`.
4. Run that dedicated resolution **without `--offline` and with a metadata refresh request** (for example, `--refresh-dependencies`), so Gradle—not a hand-written `.bin` edit—can reconcile repository-scoped metadata and any negative/stale cache state.  It must be constrained to those four configurations and capture a sanitized resolved-component/artifact receipt.
5. Stop after that resolution receipt.  If Gradle needs to reacquire a resource, permit only the same four exact authorized public coordinates and verify their immutable digests against the restoration proof; do not broaden the graph or substitute versions/repositories.

This necessarily requires a **new explicit network/Gradle-resolution authorization**.  It cannot be accomplished by another offline command, and it must not become an implicit build retry.  The replacement authority states that no further automatic build attempt is authorized; after a successful metadata-only receipt, stop for explicit instruction before any Watch84 build.

## Explicitly not recommended

- Do not copy, rename, delete, patch, or hand-edit Gradle cache payloads, descriptor files, or `*.bin` metadata.
- Do not clear caches, reset the original failed-stage marker, alter repositories or dependency versions, edit source/build configuration, or access signing material.
- Do not run Gradle in offline mode as a purported repair; it cannot reconcile an unavailable repository-scoped cache state.
- Do not rebuild, sign, or admit an APK as part of this remedy.

## Inspection boundary and limitations

This was a bounded **read-only** diagnosis.  No Gradle command (including a dependency task) was run; no cache, source, build configuration, credentials, signing material, network state, build, or artifact was touched.  The conclusion is limited to the reported failure, the restoration proof, the driver's public `GRADLE_USER_HOME` selection, and bounded cache filenames/path presence.  It deliberately does not claim an exact cache-binary root cause.
