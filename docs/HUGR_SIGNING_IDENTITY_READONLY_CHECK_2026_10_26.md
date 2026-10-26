# HUGR signing-identity read-only check — 26 October 2026

A bounded read-only check of already-preserved Watch84 admission evidence establishes the current development candidate's signing-certificate identity. This does **not** establish the planned production-distribution signer or its Samsung registration match.

Package: `com.hugr.wearos`, Watch84 `0.84.0-positional-v2-duration-ladder-candidate`. The retained admission signer output identifies the certificate as **Android Debug**, and the compiled manifest is **debuggable**. Existing candidate admission and signer continuity remain unchanged; this candidate is not thereby production-publication-ready.

Public certificate SHA-256: `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706`. APK-file SHA-256: `e0525b5e09c2682de23ff8bc2a26bb10e765d2e308c0e96bf39576a80c0a20db`. These are distinct facts; the file checksum is not the certificate fingerprint.

Samsung's public documentation requires a registered package/distributed-app signing certificate combination. Google distinguishes the certificate used on distributed APKs from a potentially different upload certificate, and states that most stores including Google Play do not accept debug-certificate-signed apps for publication. See https://developer.samsung.com/health/sensor/guide/app-verification.html and https://developer.android.com/studio/publish/app-signing.

**Missing evidence:** actual planned production App Signing certificate and actual Samsung registered fingerprint for the same package. No definitive match is claimed. Smallest next read-only input, if the existing app is configured in Play Console: package and SHA-256 from **App signing key certificate**, not Upload key certificate, together with existing Samsung registration evidence. If not configured, report that without enrolling/changing keys. Public fingerprints may be shared; private keys, credentials and keystores must not be exposed.

The supplied provider screenshot and detailed interpretation remain **private/off GitHub**, preserving its original bytes. This sanitized receipt contains only local artifact status, public rules, and missing-evidence status. No original provider wording, media or personal information is staged.

No build, signing, key generation/rotation, Play configuration, store upload/release, provider submission, installation, device launch, Bluetooth, private recording access, diagnostic retry, receipt change or cardiac-drain implementation occurred. Phone66, Watch84 artifacts and all earlier markers/evidence remain unchanged. Current Watch run state remains UNKNOWN; diagnostics preservation followed by the bounded current-receipt observation remains separately authorized and pending. Diagnostic source/test results and the full Expo module-bridge compile limit are unchanged.

Preservation uses this source-owned receipt, Master then Alignment last, bounded existing-branch documentary commits, controlled both-repository write preflight and non-force mirrors with exact head/tree/changed-byte readback. No affected VIS-COUNT proposal is modified. T2.2, adapter/RMSSD companion, October reconciliation, S4, scientific ownership, endpoint/drain, learning, Loop A and IDC remain separate. Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

END_OF_SIGNING_IDENTITY_READONLY_CHECK
