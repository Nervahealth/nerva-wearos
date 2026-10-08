# Phone60 — proposed physical readback gate

**Prepared, not executed or authorized by the engineering instruction.** Watch77 stays unchanged. This gate reads the existing ordinary session on Honor offline; it does not need a Bluetooth reconnect and must not start another recording.

## A. Separately authorize installation only

Use only the Phone60 APK named in the completed source-owned admission receipt, once its package/version/signer/SHA and uploaded-byte readback are admitted.

1. On **Honor**, install it as an ordinary in-place **HUGR update**. Keep the existing app/data. No uninstall, clear, downgrade or reset.
2. Stop at installer result; **do not press Open**. Stop on any unexpected package/signature warning or request to uninstall/clear; no retry.
3. Settings → Apps → HUGR: confirm **2.0.60-saved-source-evidence-candidate**. Report result/version and any warning. Leave Watch77 and both normal Main HUGR apps unopened.

## B. Separately authorize passive existing-session readback

After installation is confirmed, proposed authority is:

> Authorize one offline passive Phone60 readback on Honor: open **HUGR Saved Source Evidence** once and read its local report. No normal Main HUGR/Watch launch, Connect/reconnect, sensing, new recording, pairing/reset, legacy/frozen access, Share/export/private-data transfer or N1 execution. Stop at the first report or failure, or 60 seconds without a report; no retry.

1. Open **HUGR Saved Source Evidence** — the separate labelled launcher with an **information “i” icon**, **not normal HUGR**. No Watch action is needed; leave Bluetooth settings alone.
2. Allow up to **60 seconds** for its one-shot report (worker has a 45-second budget).
3. Read header through **END REPORT** locally. Check target actual source session `3c9e99e9-9430-480e-9b5a-ae00990b6b22`, outcome/code, before/after source hashes, selected counts/missing ranges, each manifest's range/count/bytes/full expected/actual SHA/stream ranges, and historical `verified` versus recomputation. Per-stream counts are canonical rows, not individual beats. Do not Share/export or upload screenshots/video containing private data under this gate; external evidence transfer remains separate.
4. **STOP** on any prompt, blank/crash, no report at 60 seconds, source-change/corruption/budget/cleanup failure. Keep the first status; do not relaunch/reconnect/clear/repair.

## Interpretation

- **CANONICAL_EQUALITY_MATCH**: the copied selected indexed Phone bytes agree with selected stored manifest metadata; original before/after full-byte fingerprints match. Not independently a whole-recording qualification or acquisition-quality proof.
- **PARTIAL_EVIDENCE**: selected records can be checked, but gaps or manifest coverage remain incomplete. Do not repeat delivery or recording on this result alone.
- **STOP**: a named boundary failed; investigate that evidence, not a speculative new build.
- Phone snapshot cannot invent historical ACK receipt or overwritten Watch marker history. Reconcile any later authorized report evidence with the existing Watch saved exact-completion report before deciding whether the short recording is qualified.

**Stale current sensor/beat indicators are separate from these saved records.** Recovery intentionally produces no current sensor stream. This readback determines what is actually stored; it does not claim nothing was lost or that all streams exist.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
