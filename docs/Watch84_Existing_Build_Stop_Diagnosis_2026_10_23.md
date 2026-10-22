# Watch84 sole `:app:assembleDebug` stop — retained-evidence diagnosis

**Diagnosis date:** 2026-10-22  
**Scope:** Read-only reconstruction from retained Watch84 files and Gradle daemon log. **No Gradle command, build, signing operation, source edit, installation, retry, device action, or credential/keystore/signing-configuration inspection was performed.**

## Exact root cause

The sole Watch84 `:app:assembleDebug` invocation stopped during Gradle dependency resolution for `:app:checkDebugDuplicateClasses`, **before compilation, DEXing, packaging, signing, or artifact admission**.

The invocation was explicitly offline. Its configured Gradle user cache did **not contain the required JAR payloads** for four runtime-classpath dependencies, so Gradle could not resolve `:app:debugRuntimeClasspath` in offline mode:

| Missing cached runtime JAR | Retained Gradle diagnostic |
|---|---|
| `androidx.wear.tiles:tiles-proto:1.2.0` | No cached version available for offline mode |
| `androidx.wear.protolayout:protolayout-proto:1.0.0` | No cached version available for offline mode |
| `androidx.concurrent:concurrent-futures:1.1.0` | No cached version available for offline mode |
| `androidx.resourceinspection:resourceinspection-annotation:1.0.1` | No cached version available for offline mode |

> **Root cause:** an incomplete retained Gradle dependency cache for the required **debug runtime classpath** under an enforced `--offline` build route. This is a dependency-cache availability/resolution failure, not a Kotlin/Java compilation failure, DEX failure, APK packaging failure, signing failure, or candidate-artifact validation result.

## Direct evidence and timing

| Evidence | Established fact |
|---|---|
| `watch84-build-stage/build-attempt.json` | The fail-closed marker was written at **2026-10-22T21:14:13Z**, names exactly `:app:assembleDebug`, and records `STARTED_OUTCOME_NOT_YET_KNOWN_NO_RETRY_BY_DRIVER`. |
| Retained daemon log `gradle-user-home/daemon/8.1/daemon-162682.out.log` | The corresponding log spans **21:14:15.743Z–21:14:33.055Z**; it reports `Execution failed for task ':app:checkDebugDuplicateClasses'`, failed `:app:debugRuntimeClasspath` resolution, the four missing cached JARs above, and `BUILD FAILED in 19s`. |
| `final-release/watch84-sole-build-output.txt` | The driver’s only retained public stop is exactly `WATCH84_FRESH_STAGE_BUILD_DRIVER_STOP:SOLE_ASSEMBLE_DEBUG_BUILD_STOP`. |
| Retained cache filesystem | For each of the four named coordinates, the version directory exists and has a cached POM but **zero cached `.jar` payloads**. This independently supports the daemon’s offline-cache diagnosis. |
| `watch84-artifacts/` | Exists but has **zero entries**. There is no `build-finished.json`, final APK, artifact-admission receipt, or local-build result. |

Evidence binding (SHA-256): daemon log `a0d882e401f47d67b4263248b1713b4f4cab90e15cdaafb902a4367ca4e3ed93`; build-attempt marker `4178829a99df868af34b4c3e380248e2e9f5a4c1852f2708c430d4902b60be34`; retained stop output `05b1035caec3bf507350103e7a212ec04ce52ff6b6ffd1530fc3ff8cbddaa611`.

## Public source/tool locators

| Locator | Relevance |
|---|---|
| `watch84-preparation/watch84_fresh_stage_build_driver.py:651-656` | Defines the sole Gradle invocation: Gradle 8.1, `--offline`, `--no-daemon`, `:app:assembleDebug`; its child stdout/stderr are deliberately discarded. |
| `watch84-preparation/watch84_fresh_stage_build_driver.py:641-649` | Writes the durable started marker before Gradle and creates the otherwise empty artifact directory. |
| `watch84-preparation/watch84_fresh_stage_build_driver.py:556-565` | Converts any non-zero child exit to the stable `SOLE_ASSEMBLE_DEBUG_BUILD_STOP` without retaining child output. |
| `watch84-preparation/watch84_fresh_stage_build_driver.py:657-701` | Shows all post-assemble steps are downstream: APK presence, DEX checks, alignment, existing signing route, read-only artifact admission, sidecar, result, and finished marker. None was reached. |
| `watch/app/build.gradle:58-60` | Public direct declarations for `androidx.wear:wear:1.3.0`, `androidx.wear.tiles:tiles:1.2.0`, and `androidx.wear.watchface:watchface:1.2.0`; the failure concerns runtime transitive resolution from this dependency graph. |
| `HUGR_WATCH84_PHONE66_FINAL_COMPOSITIONAL_BUILD_SOURCE_GATE_2026_10_22.md:5-8, 26-33` | Controlling Watch84 authority: permits one local Watch assemble only after preserved gates/staging, forbids automatic second builds, and treats a validator/tooling stop as not authorizing another build. |
| `HUGR_MASTER_TASK_LIST.md:3827-3829` | Latest task-list release record: one conditional Watch assemble after the named source/gate conditions, with no artifact claim before it completes. |

## What is established

1. **One actual, offline `:app:assembleDebug` was started** after the driver’s pre-Gradle marker was committed.
2. It **failed at `:app:checkDebugDuplicateClasses` because debug runtime dependencies could not be resolved from the retained offline cache**.
3. The precise absent artifacts are the four public coordinates listed above; their JAR files were absent from the configured retained cache.
4. The failure occurred before source compilation or any artifact-producing phase. Therefore it does not evidence a source/compiler error or any failure in downstream APK/DEX/alignment/signing/admission steps.
5. The driver’s no-retry guard is engaged: the durable build-attempt marker remains and there is no completed artifact output.

## What remains unresolved / not established

- Why the four JAR payloads were absent from the retained cache (for example, cache population/provenance) is **not established** by these retained files.
- Whether a network-enabled dependency retrieval, an independently prepared complete cache, or any other future route would resolve the issue is **not tested and not inferred**.
- No Watch84 APK exists from this invocation. Package output, DEX composition, alignment, signer continuity, artifact SHA/size, compiled predicates, and read-only artifact admission are all **unreached**, not failed or passed.
- This diagnosis does not alter the governing one-build/no-automatic-retry boundary. It authorizes **no corrective action**.

## Security and boundary note

This report intentionally contains no credential values, keystore paths or contents, raw signing configuration, private signing material, or secret environment values. It makes no claim about devices, clinician work, physiological data, or real evidence collection.
