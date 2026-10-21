# Phone65 managed build and artifact admission — sanitized lane result

**Status: PASS.** Exactly one existing-project, existing-signer managed EAS Android `preview` internal-APK job was submitted and finished. The resulting APK passed both required local compiled admissions, was uploaded once with its checksum sidecar, and independent CDN readback proved complete byte equality.

| Control | Result |
|---|---|
| Controlled source | clean `4aad9d1c2dd7a8b0812adb35dfe4a15ee778ceb0`, tree `41f5ec51156caec4176360ee91176b139d21c67b`; preserved upstream `9b429855ff5b08360afd81fd90dbbe885bd76380` |
| Independent remote proof | all three preserved trees and all changed-byte checks: PASS |
| Unchanged runtime/protected hashes | `313` runtime + `2` protected: PASS |
| New committed build stage | `5fab7b55bace9ae592f377df1733fc01a2204899`; `165` runtime inputs + exactly 2 metadata entries |
| Stage custody | source → working stage → committed bytes PASS; dependency symlink ignored/not tracked; no committed symlink |
| Native staging boundary | root generated Android/iOS excluded; local Expo module Android included |
| Managed build | one job `b1ec4fbe-5d32-4b8d-8a36-d297085fd986`; Android / `preview` / project `d0c3af97-5a78-4ba1-bc8b-8a21f169ce44` / code 65 / frozen credentials: FINISHED |
| Signer | `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811`: PASS |
| Local parent admission | PASS |
| Independent selected-export compiled admission | PASS |
| APK | `152064082` bytes; SHA-256 `49b5c06dc6796feb1ef777353ee567e0e9207bdfe2b5de317b3cf38763c115ae` |
| CDN independent equality | full 1 MiB chunk comparison, SHA-256, byte count, and sidecar equality: PASS |

## Compiled boundary facts

- The selected exporter Activity/provider contract, read-only provider/save/share guards, pending/state/copy/save/share safeguards, namespace-aware runtime test exclusion, and exact 8 MiB/API-28 cursor-window proof passed.
- The selected component remains component-isolated, while normal React Native/Expo native Application bootstrap is retained. The direct `MainApplication.onCreate()` inspection found no direct writer, pending-recovery, or BLE reference. This is **not** a claim that generic initialization, Expo lifecycle behavior, or JS evaluation is absent.

## Boundaries retained

No source change/commit, account/project/signer/credential/cache/permission change, device or Watch action, installation, launch, app-private-root access, runtime, sensing, collection, export, BLE, transfer, replay, or receipt mutation occurred. Raw EAS/upload responses and EAS archive URL remain private mode-600 files. This receipt intentionally contains no URLs. Original Phone64 stages/artifact remain historical and unchanged. The disclosed wider-native-suite timeout is not a fresh full-suite PASS;19 focused native tests,11 separate-reader tests and three native-produced synthetic ZIPs retain their earned results.
