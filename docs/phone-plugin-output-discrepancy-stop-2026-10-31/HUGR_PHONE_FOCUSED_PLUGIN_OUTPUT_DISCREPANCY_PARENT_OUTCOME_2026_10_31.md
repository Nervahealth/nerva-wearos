# Focused Expo plugin resolution — full application compile not reached

**Application compilation EXECUTED: NO. PASSED: NO result.** The sole replacement remains UNUSED and its marker absent. Provider verification was not executed. This is a resource/configuration STOP, not a failed full-application compile. The focused resolution did not achieve a usable plugin in the subsequent normal application configuration; Phone-only candidate readiness is not earned.

## Demonstrated result and correction

Cloud observed11selected Kotlin source files, including the declared `ExpoModulesGradlePlugin.kt` and the autolinking implementation. It captured source/output/JAR-directory facts rather than inferring the cause from an empty JAR. The historical comparison state justifying UP-TO-DATE was not recovered.

Only the selected Kotlin compiler and JAR task were made out-of-date. Both normal actions executed; no source, dependency, version or JAR-input wiring changed. They produced40classes and one Kotlin module. All41compiled contents were byte/SHA/CRC-matched to the66,679-byte producer JAR SHA`ba01155fd9e8e24967fb77980a28450794598525897b95737fd436494355e2fd`, including its declared implementation. This demonstrates that the selected unchanged plugin inputs could produce the required compiled artifact. It does not establish why the earlier outputs were missing.

The application observer then hit its own demonstrated API mistake: `PluginManager.withPlugin` supplies AppliedPlugin metadata, not an actual Plugin instance. The correction used public `PluginContainer.getPlugin(String)` with the existing exact project/descriptor/implementation checks. The original exception and secondary release-component error remain preserved. Regeneration was not repeated; the corrected observer was put into the subsequent ordinary resource invocation.

## Actual remaining failure

The strict-offline resource command targeting`:app:processDebugResources` launched and exited1without timeout. During evaluation of`:expo`, frozen`android/build.gradle` line1, Gradle failed to apply`com.android.internal.version-check` because it could not find`expo.modules.plugin.ExpoModulesGradlePlugin` in the selected transformed-original plugin JAR. Exact namespace/Gradle argv, stderr/stdout and task records are retained in`verified-return/focused/coherent-build-evidence/resource.result.json` and companion logs. Raw`ACTUAL_APPLICATION_BUILD_FAILURE` is unchanged history for this resource/configuration command.

Before that invocation, the regenerated-artifact bindings matched. During it, selected Kotlin compilation was again UP-TO-DATE/didWorkfalse while its JAR action ran with a missing-class warning. Afterwards the same41verified files were absent at the same regeneration class path, and the same producer JAR path held the641-byte descriptor-only artifact SHA`56463305bb688e169ca7f3714e8c48ac66975b7cfc5f1d5f3a3db9c70879605e`. The historical regeneration remains valid at its recorded time, but its active artifact-reuse receipt is invalidated. Normal plugin application, actual AAPT2 resource use/fresh outputs, provider verification and full-app compilation were not earned.

## Cause and exact path limits

**Loss cause remains UNKNOWN.** Cloud’s one bounded pinned-code/task-record assessment found a possible unqualified task-path history key and conditional previous-output cleanup, but did not prove a shared store, matching history entry, cleanup execution or deletion target. Cache collision is not adopted as the cause. Recorded dev-launcher compilation starts after the selected plugin JAR warning in this invocation; it is not a demonstrated antecedent. Staging/mount/cleanup contribution and the later resource-run compared FileCollection/JAR visitation remain unproven.

The successful regeneration/current discrepancy refer to`N/build/included/53fb11f0b42fdd47/root/classes/kotlin/main` and the matching`root/libs/expo-module-gradle-plugin.jar`;`N` is the existing`executable-compile-route-223348-2026-10-30`workspace. The earlier input/output observation’s accepted-facts record names a distinct historical class/JAR-root projection under the parent`direct-resource-request-163340-2026-10-30/build/included/...`. Do not conflate that historical observation with the regeneration/current path or infer a deletion mechanism from this difference. The detailed absolute paths and timing contexts remain exact in the returned records.

There is no demonstrated corrective cause that warrants another regeneration, cache/history deletion, relocation or guessed wiring change. The same Cloud lane remains STOPPED. The unresolved decision is whether a further narrowly authorized output/state observation can identify the actual path/state transition, or whether obtaining a trustworthy compile requires a separately reviewed build-state/layout method. This record authorizes neither a new run nor a substantial instrumentation redesign. It does not call for another task catalogue, exhaustive graph or general assurance programme.

## Integrity, custody and boundaries

Parent data-only reopen verified outer349,334bytes SHA`18f8e109bd046fb1f3590c0d2e6823e0f8752be60b6124734b6408ed0c54c0cf`, all5members/4payloads; inner352,447bytes SHA`a2ecf7e7096c900bace50685ea6201b5c037bfe2894a94e20e429a9a23caf4a3`, all94members/93payloads. Exact size/SHA/CRC/safe sets/UTF8 passed. Detached Cloud review was reused; no returned code or build binaries were executed by the parent.

Parent admission compared current350controlled source files,242native production postimages/protected identities and prior tool/provider anchors. Actual Cloud postflight728source/native/BLE/config associations and tool/provider/policy/cache-inode identities remained exact. Original429-byte consumed marker SHA`22da98375904dacee01de5f15ba277004bff14ca26adf5335d543a3c83f5af37` remains unchanged. Prepared AAPT2 POM/JAR/executable and disposable f982 policy retain accepted identities; preparation is not actual resource use. No source/dependency/version/permission/policy/deadline changes, global cache deletion, second regeneration, APK/AAB/DEX/signing/install/device/Watch85/recording/export/physiology/IDC actions occurred.

Watch85 remains current unchanged; Watch69/68/58 remain historical frozen labels. Existing physical PASS and failed-run evidence survive. Runtime capture/failure-path retention, rolling delivery, radio/Watch acceptance, correlation and physiology remain untested by this lane. The diagnostic/recording export brief stays PARKED for the candidate/test-planning checkpoint.

Preserve this sanitized documentary result Phone source-owned first, Master next, Alignment last, using both-repository actual blob-write preflight and exact non-force head/tree/blob readback. Keep private result archives, cache/tool binaries and recordings off GitHub. Remote preservation is a separate result from compilation.
