# Phone64 Selected Export — Compiled APK Admission Review

**Scope:** reusable **read-only compiled-APK validator** for one proposed Phone64 artifact. This is complementary to the parent-owned admission script. It is not a build, staging, signing, upload, installation, launch, device, export, or data-review action.

## Deliverable

`verify_phone64_selected_export_artifact.py`

**Invocation (after parent supplies one APK):**

```bash
python3 /home/ubuntu/HUGR_PHONE64_SELECTED_EXPORT_BUILD_2026_10_20/validation/verify_phone64_selected_export_artifact.py \
  /absolute/path/to/HUGR_Phone64p.apk \
  --expected-version-name 2.0.64-selected-run-private-export-candidate \
  --out /home/ubuntu/HUGR_PHONE64_SELECTED_EXPORT_BUILD_2026_10_20/validation/phone64-selected-export-compiled-proof
```

The validator was syntax-checked (`py_compile`) and its compiled-manifest component parser was exercised read-only against the existing Phone63 artifact only. **No Phone64 APK is present in this workspace and the validator has not been run on one.**

## Exact signed-artifact predicates

| Area | Compiled predicate |
|---|---|
| Archive / identity | ZIP CRC and duplicate-entry check; page alignment; `health.hugr.app`; code `64`; supplied exact version name; min/target SDK `26/36`; exact historical certificate SHA-256 `a1780089…e811`. |
| Native definitions | Exactly one each of selected exporter, `SelectedCompletedRunExportActivity`, `SelectedRunExportProvider`, registry, existing writer/report/receipt classes, duration classes, and `MainApplication`; no duplicate DEX class definitions. |
| Activity / provider manifest | Exactly one selected Activity and provider; Activity is exported `MAIN`/`LAUNCHER`, `singleTask`, portrait, selected task affinity, and `:selectedRunExport`; provider is non-exported, same `:selectedRunExport`, `grantUriPermissions=true`, authority exactly `health.hugr.app.selectedRunExport`, and has no declared broad permission/path-grant/FileProvider surface. |
| Real component inheritance | Selected Activity’s actual direct superclass is `android.app.Activity`; provider’s is `android.content.ContentProvider`. No ReactActivity assertion is inferred. |
| Selected exporter | Compiled refusal terms cover invalid/mismatched selection, not-completed/not-complete-verified, custody/receipt/inventory mismatch, pending append/publication, unsafe journal/WAL conditions, changed source/report, schema/snapshot failures, and package byte / finite-I/O limits. |
| Immutable source boundary | Selected exporter has the compiled disposable SQLite open/close path with read-only flag; rejects writable DB references; verifies original report hash + exact-copy path; verifies original decoded receipt hash/parser path; renders manifest tuple sidecar and package record count; requires the fixed eight member names and the full-parent/no-omission/no-reorder/no-reserialization term. |
| Save/share/provider guards | Separate `ACTION_CREATE_DOCUMENT` and chooser send paths; clip URI + read-grant call; save output stream; cancellation cleanup; exact URI revocation; worker shutdown; provider exact `"r"` mode; read-only descriptor; narrow OpenableColumns metadata; scheme/authority/path-segment checks, `SecurityException`, scheduled expiry, revocation/expiry deletion. |
| Preserved Phone63 duration proof | All established duration tokens and report/receipt/source-journal terms; no packaged project test classes or JUnit/Robolectric/Mockito/AndroidX-test runtime namespaces; method-local `configureSelectedRecordCursorWindow` proof of exactly `8,388,608` bytes, API-28/`SDK_INT` branch, `SQLiteCursor`, `CursorWindow`, name `hugr-selected-record`, and `(String,long)` constructor. |
| Existing source-evidence guard | Confirms compiled `isHugrSourceEvidenceProcess`, including API-28 and pre-28 branches, and confirms that it names **only** `:sourceEvidence`. It makes no claim that this guard covers `:selectedRunExport`. |

## Read-only MainApplication dependency determination

A focused read-only disassembly of the admitted Phone63 APK was performed to avoid treating the Activity source comment as proof.

**Established:** `:selectedRunExport` does not receive the `:sourceEvidence` early return. Its `MainApplication.onCreate()` therefore directly runs `ReactNativeApplicationEntryPoint.loadReactNative(...)` and `ApplicationLifecycleDispatcher.onApplicationCreate(...)`. The direct React entrypoint initializes SoLoader/default architecture/native edge-to-edge configuration; this inspected method contains no direct JS-bundle evaluation call.

**Not found in the direct compiled `MainApplication.onCreate()` path:** `PhoneSourceJournal`, `HUGRSourceJournalModule`, `recoverPendingAppends`, BLE package/API, or a direct Android Bluetooth reference. The packaged HUGR journal module’s writer is lazy and requires a React context; it does not implement an Expo `ApplicationLifecycleListener`.

**Concrete dependency / admission caveat:** the selected Activity is component-isolated but **not Application-startup-isolated**. It has normal React Native / Expo native bootstrap and generic Expo lifecycle dispatch. This contradicts any literal claim that it “never starts Expo/React”; it does **not** establish direct writer, pending-recovery, BLE, service, or JS execution from `MainApplication` alone. The validator records this positive startup fact and does not convert it into an unsupported process-guard redesign or a claim that generic Expo lifecycle code/JS is absent.

## Exact nonclaims

- No Phone64 artifact was built, uploaded, staged, signed, installed, launched, or device-tested.
- No device, physical app state, app-private root, real record, report, receipt, canonical payload, generated ZIP, share target, or saved destination was read or changed.
- A PASS will establish only the listed **signed compiled-artifact predicates**. It will not establish real chooser/provider permission behavior, save/share behavior, export success, receiver behavior, or evidence integrity of a later real package.
- The retained `:sourceEvidence` Application guard is **not** a guard for `:selectedRunExport`.
- The admitted Phone63 direct disassembly supports no assertion that the selected process avoids all React/Expo initialization. Conversely, it supports no direct `MainApplication` writer/pending-recovery/BLE/JS-execution claim.
- No claim is made about physiological truth, clock origin, beat chronology, RMSSD, learning, clinical meaning, research-data handoff, Watch behavior, or a new Watch build.

## Status

**Prepared and syntax-checked; awaiting an explicitly authorized parent-provided Phone64 APK for read-only admission.**
