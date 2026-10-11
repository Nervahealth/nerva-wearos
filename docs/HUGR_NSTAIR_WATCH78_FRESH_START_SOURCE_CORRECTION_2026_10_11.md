# Watch78 fresh-start source/test correction — 2026-10-11

## Result

**Two startup defects demonstrated and corrected; complete applicable synthetic tests pass. No new APK built or installed.** Installed Watch78 and Phone61 are unchanged. Physical Watch exception and any saved report remain unobserved by this task.

## Current authority and physical evidence

Neil at 12:09 CEST: `Started never appeared phone not opened. Authorize the focused Watch78 fresh-start source/test investigation and correction of any demonstrated defect, plus one passive local Saved Diagnostics read. No build, installation or recording retry.`

This confirms STARTED was not visibly observed and Phone was not opened. It does not prove sensors never started, any source finalization, custody loss, or an Android crash mechanism. Earlier screenshot shows generic receipt-unavailable/interrupted-I/O/no-completion text. Permission observations remain unreported.

## Demonstrated defects and bounded corrections

### 1. Trusted Android app-directory alias rejected before admission

Installed Watch78 passes raw `Context.noBackupFilesDir` to `FreshRunReceiptStore`, which rejects every symlink ancestor and noncanonical absolute spelling. A disposable `/data/user/0 -> /data/data` app-directory fixture reproduces `Symbolic receipt path` at `store.current()` **before any source journal is created**. Prior tests used canonical temporary directories and missed this convention. Public Context API does not promise symlink-free/canonical spelling.

Correction: resolve **only the OS-supplied no-backup anchor**; reject symlinks at the no-backup and immediate app directory themselves, require the resolved directory, then retain the original strict checks for receipt root, UUID run directory, source, receipt/pointer and pending/temporary children. No arbitrary selected child is canonicalized into acceptance. `SourceJournal.kt`, checksum and interrupted-publication rules remain unchanged.

This demonstrates a source incompatibility for that storage convention, not that the actual Watch used that spelling or that it was the physical first exception.

### 2. Failed admission leaves an unsafe repeat-action gate

Original admission failure reset `freshRequested=false` while the one-way startup gate remained `FRESH_SCOPE_READY`. A second Start could call `deferFreshScope()`, which requires `NOT_STARTED`, throwing uncaught on the UI thread. This is a demonstrated repeat-action defect; without a captured physical trace it is not asserted as the observed Android tray-return mechanism.

Correction: latch the activity's first terminal failure, disable all three fresh actions, do not reset the failed request into a re-admittable state, and schedule admission only once despite repeated permission callbacks. Keep the STOP visible across refresh/lifecycle rendering.

### Exception capture and serialized bounded readiness

- Use existing durable diagnostic capture for admission/read/service-start exceptions **before cleanup**; append no private paths/messages/payloads.
- Move small receipt I/O to one background worker and serialize `current()` with runtime admission/publication.
- Block/visibly disable fresh actions during a pending receipt read, closing the reviewed read-failure/admission race. A real worker held on the runtime monitor plus actual Activity controls proves no new gate/admission/session is scheduled by a concurrent tap.
- Preserve the primary service-start exception even if receipt-failure persistence itself fails; quiesce active source before cleanup.

No general diagnostic system, transport redesign, deletion, missing-byte reconstruction, historical receipt backfill or corruption bypass was added.

## Validation

| Validation | Final result | Meaning |
|---|---|---|
| Raw alias before-fix reproduction | Passed expected rejection | Original strict store rejects before journal creation. |
| Complete Watch unit suite | **217 total: 213 pass, 4 existing fixture-dependent skips; 0 failures/errors** | Includes 2 path tests and 6 Robolectric Android-context/Activity methods. |
| Complete Phone deterministic suite | **142 total: 139 pass, 3 fixture-dependent skips; 0 failures** | Phone61 production source unchanged. |
| Complete joined acceptance | **All six phases PASS** | Actual Watch-generated canonical/wire fixtures → Phone wire exact ACK → Watch accepts Phone ACK → exact production native writer/verifier/report → restart/offline recheck → complete Phone tests. |
| Joined native custody/report tests | **94/94; 0 failures/errors/skips** | Real production method bodies and storage conventions, synthetic data only. |
| Protected source/artifact identity | Passed | SourceJournal, receipt/session protocol, BLE exact ACK/finalization, Health service, manifest, Phone61 production source and admitted Watch78/Phone61 APK checksums unchanged. |

Android-context tests exercise the actual `FreshRunRuntime.admit/current`, alias-resolved first receipt and **revision-2 replacement publication**, same source/run identity after interrupted teardown, no automatic pre-begin samples, selected corruption/pending/symlink rejection, Main async failure and repeat-action blocking.

Test-only fixture capacity was set through `ShadowStatFs`; production journal's capacity requirement was not changed. Robolectric's directory shadow uses actual disposable Linux directory `FileChannel.force(true)` and atomic rename, not no-op durability. These remain synthetic Android/Linux filesystem tests, not hardware `Os`, power-loss, service, radio, real sensors or private custody proof. Detailed notes: `HUGR_NSTAIR_WATCH78_STARTUP_ANDROID_TEST_NOTES_2026_10_11.md`.

One earlier joined run failed at compile because new Robolectric tests arrived before their dependency edit; retained at `joined/03-watch-accepts-phone-ack.log`. Stable-source successor `joined-validated/summary.json` passes all phases. One full-suite source-string assertion expected the old inline button guard; it was updated to inspect the shared equivalent guard, including active session, receipt-only, terminal failure and pending read. No exclusion was relaxed. The independent review's intermediate 3/5 Android result preceded the fixture-capacity repair; final XML was copied to `validation/watch-final-xml` and includes all six passing Android methods.

## Prepared next boundary — no build now

The only already-authorized physical next action is **one passive local Watch `HUGR Saved Diagnostics` read**, separate information icon, not Main HUGR. Read the first report locally and stop at first report/failure or 60 seconds without a report. No Next/retry, Share, private report upload, Phone launch, Bluetooth setting change, Start/receipt-only/retained controls or recording operation. Check report time/stage/outcome locally; an old report or no report is not evidence of this fresh-start exception. Installed Watch78 did not durably capture every caught startup exception, so absence is possible. Do not initiate another attempt to produce one.

After that result, review one same-package/signer greater-version **Watch-only** corrected candidate if warranted; Phone61 already contains the compatible custody/report bridge and needs no new artifact for these startup corrections. Build/admission and installation/retry are separate authority boundaries and have **not** occurred here. Do not clear pending gates, migrate or re-encode existing source to make a candidate pass.

Complete physical acceptance remains: fresh five-minute recording → controlled finalization → exact verified canonical delivery → **Watch-origin** source-session-bound accepted ACK and completion → one durable Phone result → offline custody recheck after app restart. Phone write response alone never proves Watch acceptance. Routine Watch filming is not the acceptance mechanism; duration ladder and existing live Watchtower bridge follow physical short-run acceptance. Optional Watch69 archaeology is not a prerequisite.

## Preservation and sovereign states

Bounded corrected Watch production/tests and copied receipt/control changes follow source-owned receipt → Master → private VIS-COUNT only if authorized/affected → Alignment last → coherent commits → non-force controlled GitHub mirror plus independent exact-tree readback. Existing private proposal and accepted S4 are unchanged; denied Atlas access is not retried. GitHub identifiers/readback are supplied separately after verification; written local files alone do not establish remote preservation.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

No physical journal/private physiological bytes, frozen/legacy roots, device change, build/signing, reset, migration, new recording, live ACK, egress, private-data transfer or N1 execution performed.
