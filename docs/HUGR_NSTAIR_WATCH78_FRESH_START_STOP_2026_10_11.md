# Watch78 fresh-start observation — STOP, 2026-10-11

## Current result

**Fresh five-minute physical acceptance stopped at startup; no complete recording or Phone result established.**

Neil reports at 10:13 CEST that pressing Start fresh 5 minutes shows receipt unavailable, and another press crashes the app or returns it to the app tray. The supplied photo shows the fresh-action buttons and the visible suffix `receipt unavailable / interrupted IO · no completion`. Treat the second press and tray return as reported facts; the photo alone does not establish an Android crash/ANR, precise first failure, timing, new source identity or collection state. No blame or Bluetooth-cause inference.

The one-shot gate stops here. Do not press Start again, reopen Main, connect Phone, reinstall, clear data, alter pairing/Bluetooth, start receipt-only/retained-v2 delivery, or initiate another recording. Phone61 opening/Connect, permissions shown, and whether STARTED was ever visible are not reported yet; ask Neil from memory, not through another device operation. Observation STOP does not prove sensors never started or that any admitted session finalized.

## Bounded read-only source check

Checked the controlled Watch78 source, without modifying production code, running a new recording/test/build, or accessing physical journals:

- `MainActivity.kt:484–495`: fresh rendering calls `FreshRunRuntime.current` inside `runCatching`. **Any thrown exception** produces `Fresh receipt unavailable / interrupted IO · no completion`. This is a generic rendering/read failure label, not a uniquely diagnosed exception or proof of lost canonical bytes.
- `FreshRunRuntime.current` uses the active session snapshot if present; otherwise it reads the isolated v3 receipt store. Store checks include filesystem-path safety, pending-publication gates, checksummed envelopes, UUID selection and receipt validation. The screen cannot distinguish these failures.
- `MainActivity.startAllServices:353–379` admits a fresh session in a worker before requesting GATT service start. Its admission exception is caught and reduced to `Fresh admission failed · no sensing`, while resetting `freshRequested`; that catch does not call durable diagnostic capture. Rendering can subsequently replace the short admission status with the generic unavailable-receipt label. Therefore an absent diagnostic exception would not prove admission succeeded.
- The Start click handler declines a further press while freshRequested/session/receiptOnly is active. Some failure paths reset the flag. A reported further press/tray return is not enough to locate which state or code path was active.
- `HugrApplication` retains a best-effort uncaught-exception capture. A real uncaught Java/Kotlin crash may therefore leave an actionable Saved Diagnostics report, but existence, relevance and completeness are not established. A report may be old. No Saved Diagnostics opening/read is yet authorized for this failure.

**No concrete root cause has been demonstrated.** Do not claim filesystem corruption, a path-alias bug, unsupported storage API, missing sensor permission, Bluetooth failure, or loss of recording bytes from this screen. Existing JVM/joined tests remain source evidence; this physical startup failure is not contradicted or overridden by their prior success.

## Smallest proposed next action — requires Neil's explicit authority

One focused source/test reproduction of the actual Android fresh admission → receipt publication → UI-read boundary, including errors/repeated-action containment and production storage conventions. Correct only a demonstrated defect and preserve its tests/source. If useful, separately authorized one passive local opening of **HUGR Saved Diagnostics**, not Main: latest matching Watch78 report header/stage/outcome/exception locally, stop at first report/failure or 60 seconds, no retry, no Share/export/private report upload. Do not treat absent or stale saved reports as proof.

No implementation correction, APK build/admission, installation, new run, physical retry, data transfer or N1 follows from this observation. Ask for the needed authority before corrections. Keep the complete instrument objective: fresh recording → controlled finalization → exact ordinary delivery → Watch-origin accepted ACK/completion → durable Phone report → offline restart custody recheck. Duration ladder and existing live Watchtower bridge remain later milestones, not prerequisites for resolving this startup stop.

## Evidence and preservation boundaries

Raw photo remains private in the supplied upload location and is not copied to GitHub. Preserve this sanitized observation and bounded source interpretation only, then Master, affected proposal only if authorized, Alignment last, bounded coherent commits and non-force GitHub head/exact-tree readback. Accepted S4 remains unchanged; do not retry denied Atlas access.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`. Legacy/frozen/previous Watch69 source bytes are not accessed or changed. No private physiological values, credential or signing material is included.
