# Phone64 managed build and artifact admission — sanitized lane result

**Status: PASS.** Exactly one existing-project, existing-signer managed EAS Android `preview` internal-APK job was submitted and finished. The resulting APK passed both required local compiled admissions, was uploaded once with its checksum sidecar, and independent CDN readback proved complete byte equality.

| Control | Result |
|---|---|
| Controlled source | clean `643f3a0fb63767a1c2dc05310a74488739ddaf99`, tree `9d045cc1b69f4c764cfa117fdb1caa459d6a8203`; preserved upstream `a55f33dc32bee7b1834b9f131068bd71fad0dd37` |
| Independent remote proof | all three preserved trees and all changed-byte checks: PASS |
| Unchanged runtime/protected hashes | `75` runtime + `13` protected: PASS |
| New committed build stage | `7c0bef5b7424f21b4332c05e6f8d32e79e34ee01`; `157` runtime inputs + exactly 2 metadata entries |
| Stage custody | source → working stage → committed bytes PASS; dependency symlink ignored/not tracked; no committed symlink |
| Native staging boundary | root generated Android/iOS excluded; local Expo module Android included |
| Managed build | one job `b5af5d64-6054-4297-a268-17f77ea53bdf`; Android / `preview` / project `d0c3af97-5a78-4ba1-bc8b-8a21f169ce44` / code 64 / frozen credentials: FINISHED |
| Signer | `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`: PASS |
| Local parent admission | PASS |
| Independent selected-export compiled admission | PASS |
| APK | `152064086` bytes; SHA-256 `a19542b8628af1c95aff0996fb9ce2a7a43e2eb769d7c8e7f1dc184ba23335bc` |
| CDN independent equality | full 1 MiB chunk comparison, SHA-256, byte count, and sidecar equality: PASS |

## Compiled boundary facts

- The selected exporter Activity/provider contract, read-only provider/save/share guards, pending/state/copy/save/share safeguards, namespace-aware runtime test exclusion, and exact 8 MiB/API-28 cursor-window proof passed.
- The selected component remains component-isolated, while normal React Native/Expo native Application bootstrap is retained. The direct `MainApplication.onCreate()` inspection found no direct writer, pending-recovery, or BLE reference. This is **not** a claim that generic initialization, Expo lifecycle behavior, or JS evaluation is absent.

## Boundaries retained

No source change/commit, account/project/signer/credential/cache/permission change, device or Watch action, installation, launch, app-private-root access, runtime, sensing, collection, export, BLE, transfer, replay, or receipt mutation occurred. Raw EAS/upload responses and EAS archive URL remain private mode-600 files. This receipt intentionally contains no URLs. Historical `phone64-stage` and `stage-contract-smoke` remain unsubmitted and unchanged.
