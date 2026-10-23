# Watch84 two-stage dependency-only recovery — terminal summary

**Terminal status: STOP.** The released two-command boundary was consumed. No further Gradle action was run after the recorded offline-preflight failure.

## Stage 1 — exact-four online metadata/artifact resolution

**PASS** using pinned Gradle 8.1 (`6cef1128…bce2c9e`), the same isolated `GRADLE_USER_HOME` (`/home/ubuntu/jobs/job_SSW3Rmkp/gradle-user-home`), and the actual Watch repository model in its declared order:

1. `google()` → Google Maven / `https://dl.google.com/dl/android/maven2/`
2. `mavenCentral()` → MavenRepo / `https://repo.maven.apache.org/maven2/`
3. `maven { url 'https://developer.samsung.com/repo/maven/' }`

The no-plugin project resolved **only four non-transitive configurations**, each with runtime/library/JAR attributes and the observed JVM target (8, 8, 7, 8). Each emitted exactly its project root plus its pinned component and exactly one artifact. All four resolved JAR byte counts/SHA-256 values and the already-established POM coordinate/SHA-256 identities match the restoration proof.

| Coordinate | JAR bytes | SHA-256 |
|---|---:|---|
| `androidx.wear.tiles:tiles-proto:1.2.0` | 45,726 | `6561349e…39b588a` |
| `androidx.wear.protolayout:protolayout-proto:1.0.0` | 1,739,194 | `28fc4c57…e50a454` |
| `androidx.concurrent:concurrent-futures:1.1.0` | 25,987 | `0ce067c5…334e9e` |
| `androidx.resourceinspection:resourceinspection-annotation:1.0.1` | 2,336 | `8cff870e…ccd5cb` |

## Stage 2 — actual application offline `debugRuntimeClasspath` preflight

**STOP — no actual graph receipt produced.** The command was a no-build task injected by an init script and invoked with `--offline` against actual `:app:debugRuntimeClasspath`. It failed in the reporting-only init hook—not on a Gradle dependency-resolution error—when the hook tried to read nonexistent `ComponentSelectionReason.description`; Gradle 8.1 reports the supported property as `descriptions`.

> `No such property: description for class: org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons$DefaultComponentSelectionReason`

The task had already obtained `cfg.incoming.resolutionResult` before that reporting-property failure, but it did not write its structured component/artifact graph and did not reach artifact-view enumeration. Therefore this is **not sufficient to claim** `actualDebugRuntimeClasspathOfflinePass=true`, nor does it identify a missing dependency or application configuration requirement. The permitted actual preflight was consumed; no repaired/repeated preflight was run.

## Preservation

The final receipt records exact before/after equality for:

- Watch Git `HEAD` `8f9e769e1f1f41f6620be0364b63b4c9b445e28b`; root tree `872b123c4651821f02d0059053eaa8c898933e66`; app subtree `5a14a3c09d5037e9b4c24f680de76e630d333139`.
- `app/build.gradle` SHA-256 `7c327aa9955b1d98211cf578b39258518098deaedf23a61618839711e216ed29` and the other enumerated Watch build/config inputs.
- Original and replacement attempt/stop markers, including the original `build-attempt.json` SHA-256 `4178829a99df868af34b4c3e380248e2e9f5a4c1852f2708c430d4902b60be34`.

No build, compilation, signing, artifact admission, Phone66 action, device/IDC/private-data action, source edit, or configuration edit was performed.

## Evidence paths

- Final machine receipt: `watch84-two-stage-dependency-recovery-result.json`
- Online exact-four structured receipt: `online-exact-four-resolution.json`
- Online bounded sanitized log: `online-exact-four-gradle.sanitized.log`
- Actual offline bounded sanitized failure: `actual-debug-runtime-offline-gradle.sanitized.log`
- Prepared fail-closed runner (SHA-256 `723e4170…619081d`): `run_watch84_two_stage_dependency_recovery.py`

The **smallest blocker** is the init-hook reporting API mismatch. It is not an app source/configuration change or a missing-dependency finding. A repeat or revised actual-classpath preflight requires fresh explicit authority.
