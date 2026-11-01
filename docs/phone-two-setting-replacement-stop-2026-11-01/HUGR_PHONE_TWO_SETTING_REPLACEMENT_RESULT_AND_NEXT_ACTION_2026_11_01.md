# Phone70 replacement: old bottleneck passed, release-input verification STOP

**One replacement managed build ran. It failed and produced no APK. No artifact was admitted and no retry follows.** The two-setting workaround moved this invocation past the earlier JFrog/classpath refusal; it did not complete the release build. The remaining error is a concrete verification-metadata gap, not a demonstrated application-source or CMake defect.

## What changed and executed

The only candidate change was two preview-profile environment entries: `EAS_BUILD_DISABLE_MAVEN_CACHE="1"` and `GRADLE_USER_HOME="/tmp/gradle-home"`. A separate copy and the actual EAS upload view matched all402 accepted files apart from that exact profile delta. No old Gradle home was copied; source/native/lockfile/dependency versions/checksum policy/package/project/account/signer remained unchanged. Cloud's focused fresh-home compatibility check passed; the existing managed signer was frozen for submission, but no resulting APK certificate exists to admit.

Replacement job: `ab77977c-93ec-4db2-ba9b-ae4e5a7ab393`. Original failed job: `5af744a2-8b6d-496b-94ba-84873aa18d5d`, retained unchanged. The current job query found no other Phone70 candidate job before submission; Minor explicitly reused the unproduced Phone70 identity, with a NEW exclusive replacement marker and jobID. Exactly one replacement submission occurred.

Package `health.hugr.app`; versionCode70; versionName`2.0.70-native-write-observation-diagnostic-candidate`; existing project`d0c3af97-5a78-4ba1-bc8b-8a21f169ce44`.

Worker log lines17 and21 directly report both effective environment values; line76 contains the selected profile. The actual command, line331, was:

```text
# cwd: /home/expo/workingdir/build/android
gradlew :app:assembleRelease --dependency-verification=strict
```

RUN_GRADLEW log boundary: **2026-11-01 00:14:13.999–00:17:50.405 UTC** (**01:14:13.999–01:17:50.405 Europe/Oslo**). Gradle reports `BUILD FAILED in 3m 35s`, `119 actionable tasks:119 executed`. Managed job completed ERRORED at00:17:55.766UTC. These are worker/log/job timestamps, not a device clock or a renewed compilation ceiling.

## What the experiment demonstrated

The complete781-row worker log has no JFrog, build-info-extractor or Artifactory mention. The former42-leaf initial `classpath` failure did not recur; normal release tasks were reached. This is a result of this combined-settings invocation, not proof of the historical injection mechanism, which individual setting mattered, permanent removal of every initialization layer, or universal success.

The first reported actionable failure at00:17:49.808UTC is:

```text
Execution failed for task ':react-native-health-connect:parseReleaseLocalResources'.
Dependency verification failed for configuration ':react-native-health-connect:androidApis'.
11 artifacts failed verification.
```

All11 are POM metadata files, and their components/artifact checksum leaves are absent from the unchanged approved policy. This is **missing trust metadata, not an observed checksum mismatch or proof of bad artifact contents**. The worker also reports the same11 leaves under Worklets, Screens and Skia CMake/classpath tasks; those secondary reports do not independently establish CMake or application-source defects.

The exact rejected coordinates are below; each rejected file is the named artifact's `.pom` metadata, not its compiled JAR:

- MavenRepo: `org.codehaus.groovy:groovy:3.0.22`.
- MavenRepo: `org.apache.httpcomponents:httpclient:4.5.6`.
- Google: `com.android.tools.external.com-intellij:intellij-core:31.12.0`.
- Google: `com.android.tools.external.com-intellij:kotlin-compiler:31.12.0`.
- Google: `com.android.tools.lint:lint:31.12.0`.
- Google: `com.android.tools.lint:lint-api:31.12.0`.
- Google: `com.android.tools.lint:lint-checks:31.12.0`.
- Google: `com.android.tools.lint:lint-gradle:31.12.0`.
- Google: `com.android.tools:play-sdk-proto:31.12.0`.
- MavenRepo: `org.reactivestreams:reactive-streams:1.0.2`.
- Google: `com.android.tools.external.org-jetbrains:uast:31.12.0`.

## Evidence custody and preserved successes

The402 local candidate input files remained byte-exact after the failed job. Verification policy SHA256`3073af1ae64f3fb0a2d7d99393514da5df1e3ba8c41c5dd66c5ee46ef0b16675`; two-setting eas.json SHA256`1a72c59347dd79decd76ad2f0a7244875c525966a52cef1d3aa02b535be43e58`. Source-owned processor SHA256`64d9e9860b567cdcff7c8b850fdccd9462d0b925b597b6573d7f3d8be27a6ec2`; source closure SHA256`3f5c65d4ec0e039118956e95fe68224eb595c8894a817a8fffe168c3e2d2a52e`. Raw worker log retained privately:32,213 compressed bytes SHA256`0bc39c62fb94cbab35be096b443d5d092232195359555b601722dbd7a353632b`;231,009 decoded bytes SHA256`6308ca3217ec4ef1c57ac5a72646002bf3aff2d12080a5ae5e70a63fb7db102e`. Sanitized first-error derivative and full11-leaf policy comparison accompany the specialist capsule.

The prior genuine strict-offline full application Kotlin/Java compilation PASS, resource/AAPT2 PASS and single-provider PASS remain earned, not repeated or invalidated by this packaging-input failure. They are not a release APK PASS. Both prior compilation markers, the original managed failure and this consumed replacement attempt remain retained.

## My recommended next action

**Keep this conventional/EAS route and make one specialist-owned release-input completion decision, rather than reopen JFrog or the custom containment programme.** Ask the build owner to verify the exact11 reported metadata files against their authoritative repositories and integrity evidence, identify any corresponding release-specific artifact/transitive leaves needed by the unchanged graph, and propose one bounded checksum-only admission batch. Do not blindly bless the old42 JFrog files, disable verification or assume adding just these11 POMs proves release-input completeness. An exact scoped preparation/admission and any next managed build require separate authority; none started here.

The specialist request is reproducible from this exact command, profile and job. A version/source change, unbounded new dependency set or inability to complete the pinned release-input set in one bounded preparation is a concrete stop/handoff—not another tooling loop.

No APK exists to install. Watch85 is unchanged; no device, Bluetooth, recording/export, physiology, IDC, scientific inventory or historical plugin investigation. Acquisition coordination stays with Clarification independently. After an APK is admitted, preserve the existing run and prepare the informative diagnostic check before a separately authorized30-minute→2-hour→8-hour staircase. Neither this workaround nor future packaging success proves the physical receipt timeout is fixed.
