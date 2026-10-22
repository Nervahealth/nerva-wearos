# Watch84 two-stage recovery authority — 23 October 2026

User authorization: 2026-10-23T01:56:41+02:00.

## Authorized ordered work

1. Gradle-managed online dependency resolution for only the same four pinned public coordinates, using the existing isolated Gradle cache, repository identity and Gradle version. Verify resolved artifacts against established coordinate/POM identity and immutable digests.
2. Only if that succeeds, verify the unchanged application's actual `debugRuntimeClasspath` resolves **offline**. Detached configuration success is insufficient. If this preflight passes, proceed directly to **one new fresh-stage Watch84 replacement build and full artifact admission**, using the preserved app source/build configuration/SDK/compatibility DEX and existing signer. No intervening approval is required when both conditions pass.

Pins: `androidx.wear.tiles:tiles-proto:1.2.0`; `androidx.wear.protolayout:protolayout-proto:1.0.0`; `androidx.concurrent:concurrent-futures:1.1.0`; `androidx.resourceinspection:resourceinspection-annotation:1.0.1`.

Both earlier failed attempts and their immutable markers remain unchanged. This attempt must have a separate authority, stage, marker and terminal result. No manual Gradle binary metadata edits, whole-cache clearing, dependency version/repository change or app scope expansion. If actual classpath exposes another missing coordinate or requires a configuration change, capture its exact requirement and **stop before building**. No automatic further build attempts.

Phone66 remains artifact-admitted and must not be rebuilt or changed. Reuse passed source/test evidence with its recorded compositional/API-27 limits. No lower-rung or ingestion replay without a concrete dependency.

## Identity and immutable anchors

Original Watch application source commit `3787cc43cf4506feb0fcde5db65e35952e6ea4b3`, root tree `c7ced0c36befdf606ddbf69ff528d35c8fe9e31d`; later source heads differ only by preserved documentation/tooling. Actual current source head/tree and original app-tree equality must be checked before stage and build.

Original first Watch attempt marker SHA-256: `4178829a99df868af34b4c3e380248e2e9f5a4c1852f2708c430d4902b60be34`. Previous replacement marker and result are preserved at the prior replacement directory, not reset or reused. Dependency proof SHA-256: `073b65cad842e5cfb421041b7efce3527e5bcba4dadc166656af8b896546fee0`.

Watch candidate: `com.hugr.wearos`, versionCode 84, `0.84.0-positional-v2-duration-ladder-candidate`, existing signer SHA-256 `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706`.

Phone66: `health.hugr.app`, versionCode 66, `2.0.66-positional-v2-duration-ladder-candidate`, 152,097,114 bytes, APK SHA-256 `8978c8d64e19420b47ebd45b9a7898b07749114644ddb5df5aecde6ed653accf`, existing signer SHA-256 `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`.

## Separate gates

On Watch admission success return the definitive compatible pair, exact hashes and installation instructions **for review**. No installation, launcher activity, Bluetooth/Connect action, physical recording, private physiological-record access, export of real records, IDC work or Samsung endpoint/drain/semantic change is authorized. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window policy, learning and Loop A remain unchanged.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

## Preservation

Source-owned receipt first, Master then Alignment last; no private VIS-COUNT proposal change. Bounded documentation commits, controlled non-force mirror with both-repository write preflight, then independent exact head/tree/changed-blob readback. Local commit is not remote preservation. No APKs, private recordings, credentials, keystores, IDC or provider correspondence staged.

END_OF_WATCH84_TWO_STAGE_RECOVERY_AUTHORITY
