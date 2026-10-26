# Watch85 / Phone67 candidate tooling acceptance

**Date:** 26 October 2026  
**Status:** **STATIC TOOLING ACCEPTED — NO BUILD STARTED**

## What was accepted

Candidate-specific successor tooling was reviewed and accepted under the already authorized coordinated Watch85 / Phone67 candidate-build lane. It is custody-bound to the previously preserved identity reservation:

| Side | Reserved identity | Tooling gate accepted |
|---|---|---|
| Watch85 | `com.hugr.wearos` / `85` / `0.85.0-receipt-diagnostic-correlation-candidate` | Fresh stage, actual app `debugRuntimeClasspath` offline preflight before the one assembly marker; Watch83 exact `classes5.dex` continuity; pinned Gradle 8.1/JDK 17/SDK route; owner/method-specific compiled diagnostic predicates. |
| Phone67 | `health.hugr.app` / `67` / `2.0.67-receipt-diagnostic-correlation-candidate` | Exact 17-file diagnostic custody; isolated source stage; exactly one existing-project preview/internal APK submission guarded by a durable intent; owner/method-specific signed native/bridge diagnostic predicates. |

The Phone67 bridge prerequisite remains narrowly resolved: `:hugr-source-journal:compileDebugKotlin` and its required offline recompile passed in the retained isolated continuation, after one Gradle-managed recovery of a verified truncated Maven artifact. The earlier aggregate Expo bridge STOP is retained and is superseded **only** for this named compiler gate. This does not establish managed EAS equivalence, APK validity, runtime execution, device behavior or cross-device correlation.

The Watch85 configuration baseline proves that the Watch84→Watch85 configuration delta is exactly the two reserved version fields. The other five listed build/configuration members and `app/build.gradle` excluding the two identity lines match the former Watch84 source baseline exactly.

## Static verification

- Watch tooling static/negative suite: **14/14 PASS**.
- Phone tooling static/negative suite: **10/10 PASS**.
- One direct Watch static-test invocation stopped only because it was started outside the tooling module-import path (`candidate_admission_common` could not be imported). The exact failure is retained; the unchanged test then passed 14/14 when run from the prepared tooling directory with its declared import path. No Gradle, Android build, source/tooling edit, signer, network, device or artifact operation occurred in either invocation.

The manifest [`candidate-tooling-acceptance-manifest.json`](candidate-tooling-acceptance-manifest.json) binds the exact SHA-256 values of the reviewed tooling, test logs and configuration baseline. The control repository preserves the executable successor tooling and static test inputs under `scripts/watch85_phone67_candidate/`; task-local copies remain the active invocation originals for this candidate lane.

## Remaining mandatory preconditions before the build markers

1. Reconfirm current clean Watch/Phone/controls source and exact remote preservation proof.
2. Run the Watch driver’s single genuine `:app:debugRuntimeClasspath` offline preflight. Its marker is created before Gradle; any fail/timeout/ambiguous outcome stops the Watch build with no retry.
3. Only if that actual Watch classpath gate passes may the Watch assembly marker and one Watch build be created.
4. The Phone runner must separately recheck current clean remote Phone proof, the 17 exact diagnostic hashes, staged identity-only configuration, and the narrow bridge continuation before it writes its one remote-submission intent.
5. Each artifact must then pass its candidate-specific **read-only** admission. Any failed or ambiguous artifact admission stops that side; no rebuild is automatic.

## Limits preserved

No Watch85 or Phone67 build, signing, EAS submission, APK download, installation, launch, Bluetooth/HCI action, recording, export, receipt mutation, private-data access, endpoint/drain change or physical test has occurred. The existing Watch84 receipt remains **FAILED / DELIVERY_DEADLINE** and the physical 30-minute gate remains **NOT PASSED**. The diagnostics improve local evidence retention, not the unresolved timeout cause or exact Phone↔Watch causal mapping.

The existing physical five-minute PASS and synthetic 5-minute/30-minute/2-hour/8-hour proof retain their prior scopes and limits. No capacity ladder was rerun. Samsung endpoint drain, native trace feasibility, IBI/RMSSD selection and chronology, baseline/learning/Loop A, IDC and N1 remain separate.

Watch69 remains `NOT QUALIFIED`; Watch68 v1 remains `UNCLASSIFIED / NOT VERIFIED`; Watch58 remains `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## Addendum — retained cache-proof representation correction

Before Gradle, marker creation or artifact work, the first Watch85 lane invocation stopped at `EXACT_RUNTIME_CACHE_PROOF_DEPENDENCY_SCOPE_INVALID`. The runtime stage still contains only its pre-Gradle intent and runtime manifest; no `classpath-preflight-attempt.json`, build marker, output/artifact directory, Gradle invocation or signer operation exists.

The cause was proven tooling representation only. The retained exact-four dependency receipt stores each verified artifact under `dependencies[*].cachePath`; the successor driver incorrectly expected a nonexistent `dependencies[*].cache` object. The four retained coordinates, cache paths, bytes and SHA-256 values exactly matched the separately retained actual Watch `debugRuntimeClasspath` success proof. The parser was changed only from `row.get("cache")` to `row.get("cachePath")`; it still requires exactly the four pinned coordinates, the pinned receipt SHA-256, regular cache artifacts, their declared bytes and their declared SHA-256 values.

A focused negative static regression now rejects the obsolete representation key. The Watch static suite is **15/15 PASS** and an actual read-only invocation of the corrected cache-proof parser reports `RETAINED_EXACT_FOUR_CACHEPATH_REPRESENTATION_PASS`. The unchanged Phone suite remains **10/10 PASS**. The earlier import-path setup STOP and this pre-marker cache-proof STOP remain preserved as history.

This correction does not relax any runtime graph, dependency, source, configuration, signing, admission, deadline or physical evidence condition. The single genuine actual classpath preflight and one Watch85 build remain unconsumed.
