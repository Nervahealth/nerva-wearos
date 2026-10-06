# Watch72 ↔ Phone58 diagnostic-route external-source notes — 2026-10-06

> **Scope:** Official-tooling evidence only for the bounded diagnostic-route assessment. This note does not authorize device setup, debugging, connection, export, sensing, delivery, acknowledgement, egress, or legacy/frozen-root access.

## Official sources consulted

| Source | Verified relevant point | Route implication |
|---|---|---|
| [Android: Capture and read bug reports](https://developer.android.com/studio/debug/bug-report) | Android bug reports contain device logs, stack traces and diagnostic information. Device-side capture requires Developer options; Android states that a report can be generated through the device’s **Take bug report** option and then shared from the resulting notification. | A phone-only emergency fallback exists for a native/pre-render failure, but it is operator-driven and can contain broad device diagnostics. It is not suitable for automatic HUGR export or ordinary physiological evidence handling. |
| [Android Debug Bridge](https://developer.android.com/tools/adb) | Wireless debugging requires Developer options, device/workstation pairing through QR/pairing code and the same wireless network. Android documents that debug authorization is granted to a particular workstation and can be revoked. | Pairing/OS Bluetooth alone does **not** create a Manus or app debugging bridge. Any ADB route needs a separately authorized developer setup on an approved workstation; it is not available from the current HUGR interaction. |
| [Android: Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging) | The official guidance says Bluetooth debugging is no longer supported from Wear OS 3; Wear debugging uses Wi-Fi/ADB. | The paired Honor phone is not an independent Watch debugging host merely because it is paired to the Watch. No Watch debugging route is assumed without separately configured Wi-Fi developer access. |
| [Samsung Health Sensor SDK: Connect Watch](https://developer.samsung.com/health/sensor/guide/connect-watch.html) | Samsung’s Watch debugging guidance uses the Watch Developer options **Wireless debugging** and a pairing code/IP/port. | This independently confirms a separate developer-access setup is required; it is not part of normal HUGR BLE recovery. |
| [Expo: Troubleshoot build errors and crashes](https://docs.expo.dev/build-reference/troubleshooting/) | Expo distinguishes successful builds from runtime crashes/hangs, directs developers to production error logs, and recommends narrowing faults from actual captured errors. | The Phone should surface bounded app-local state first; native/pre-render faults still require a platform diagnostic capture rather than inference from a black screen. |

## Boundary notes

- These sources establish tool capabilities and setup requirements, **not** that either target device is configured, reachable, paired for debugging, or authorized for debugging.
- Android bug reports may include unrelated system/app logs and device diagnostics. They must be kept local and privacy-reviewed; no raw report upload is implied.
- No source supports exposing ADB publicly or granting unattended remote device access through a normal Bluetooth pairing.
