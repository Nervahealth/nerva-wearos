# Phone JS-origin correction — source/local-test receipt

**3 November 2026 · PASS at the bounded local source/test boundary · STOP before build.** This corrects diagnostic attribution only, not Bluetooth delivery or a physical root cause. Parent owns Git preservation, version/candidate availability, one conditional additional build and APK admission.

## Implementation

The existing primary incident snapshot supplies a frozen, validated copy of `incidentPseudonym + connectionEpoch + localOrdinal + operationKind=RECEIPT_READ`, with optional already-allowlisted `pageOffset` (integer 0–999999). This is captured before settlement can change/evict the retained row and carried with the existing one-active/one-coalesced-pending READ outcome. No transaction key or time equality is used as identity. The native wrapper/bridge is explicitly named `checkpointCurrentReceiptReadProbeV2` and takes the originating-reference JSON as its fourth argument.

New saved body/envelope formats are `HUGR_PHONE_READ_CURRENT_PROBE_V2` / `HUGR_PHONE_READ_PROBE_FILE_V2`. The nested reference format is `HUGR_PHONE_READ_JS_ORIGIN_V1`, labelled exclusively `JS_ORIGIN`. Closed fields, exact primitive types and bounds are validated without coercion. Missing, invalid or mismatched references have explicit `ABSENT`, `INVALID` or `MISMATCH` states and no reference; malformed/disallowed/private fields are not echoed. The label identifies only the JS requester of the detached snapshot, never its individual native rows or callbacks. No native-row association, ATT identity, clock conversion or JS/native counter equality is claimed.

The existing READ filename/root remain in use to avoid a new store/selector route. Legacy V1 envelopes/bodies are checksum-reopened and displayed as V1 with `JS_ORIGIN=ABSENT_LEGACY_V1`; their saved bytes are untouched by passive reading. New reader failures explicitly show V2 / unavailable current origin. The existing selectable full-body reader and unrelated controls remain unchanged otherwise.

Exactly five production paths changed under `/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone`:

- `lib/receiptIncidentDiagnostic.ts`
- `modules/hugr-source-journal/index.ts`
- `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/HUGRSourceJournalModule.kt`
- `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/ReceiptReadProbeStore.kt`
- `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/HUGRSavedReceiptDiagnosticsActivity.kt` (one READ failure-text label only)

No production file was added. `CHANGED_SOURCE_IDENTITIES.json` records exact baseline/postflight hashes; `SOURCE_DELTA.patch` is the final local diff. HEAD remains `d4af9210bc346607e346d766e5ad4eca6e589c6d`; no commit was made.

## Executed local tests

| Check | Result | Exact evidence |
|---|---|---|
| Copied `tests/run-read-focused-probe.sh`: direct javac + Kotlin; production READ/SingleResponse/downstream-wrapper classes, observer, exact callback, changed store and bridge/reader slices | **22/22 PASS** | `test-results/patched-20261103T143442Z-302320/{execution,javac,kotlinc,probe}.log`, `synthetic-artifacts/READ_FOCUSED_RESULT.json` |
| Copied `tests/run-read-integration-checks.sh`: complete isolated reader/store compilation and lifecycle Java compilation | **PASS**, zero compiler error lines | Same output directory, `full-reader-compile.log`, `lifecycle-compile.log` |
| Copied `tests/run-read-coordinator.cjs`: actual coordinator/wrapper evaluation plus strict narrow TS typecheck | **12/12 PASS** | `test-results/ts/READ_COORDINATOR_RESULT.json`, `execution.log` |
| Actual TS coordinator → wrapper arguments → newly compiled real READ serializer/store → file write/fsync → atomic move/directory fsync → checksum reopen → exact reader method | **2/2 PASS**, plus interruption qualification | `final-two-case/run-freshness.sh`, `EXECUTION_RESULT.json`, `BRIDGE_JVM_TRANSCRIPT.json`, `execution.log` |
| Source/protection guards | **8/8 PASS** | `test-results/SOURCE_GUARDS.json`, `PREFLIGHT_SHA256.json`, `POSTFLIGHT_SHA256.json` |
| Old incident report byte identity | **3/3 PASS** | `PROTECTED_OLD_INCIDENT_IDENTITY_RECEIPT.json` |

Focused checks retain original timing/loss/callback cases and add closed origin schema/type/bounds/private-field handling, missing/invalid/mismatch output, same ordinal/page across distinct epochs, and actual saved V1 reader compatibility. Coordinator checks include same generated local transaction key/page/ordinal across epochs, one-active/one-latest-pending coalescing, immutable B reference under an explicit model/snapshot mutation seam, and invalid/mismatched primary-origin abstention without selecting another row. Duplicate/late outcomes still cannot create a fresh checkpoint; refusal remains detached with no retry.

### Two decision-bearing real saved-output cases

Both final cases use A/page3840 success at JS `198.75` and B/page4224 deadline at JS `10224.75`. Primary incident times truncate to `198` / `10224`; origin assertions use scoped fields, not time equality. Native fixture times are independent and unchanged.

1. **Delayed A, then B both publish.** A is held in flight while B becomes pending. Real delayed A is stored/reopened with ordinal1/page3840 even though its native snapshot already contains both independent native fixture rows. Intermediate A bytes/text are retained. B subsequently replaces it and the saved/readable V2 body positively labels ordinal2/page4224 under the correct JS incident/epoch.
2. **B refused before store entry.** The actual queue invokes A then B with their separate origins. Refused B creates no saved B body/file or `.part`; A's before/after report text and file bytes are identical and explicitly label ordinal1/page3840, not B. A alone still cannot prove B was never attempted; the refusal observation is a harness seam. A separate controlled direct-store replay of B's already-observed arguments actually writes/fsyncs a serializer-generated part file and interrupts before rename. The final A hash stays exact, but reopened reader output is UNKNOWN / `INTERRUPTED_CHECKPOINT_PRIOR_NOT_CURRENT`, not a silently current A or B.

All final case evidence is beneath `/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03/final-two-case/`. Decision-bearing retained files are:

- `case1-delayed-A-then-B/A-delayed.hugr-probe` and `after-delayed-A-before-B-publication.reader.utf8.txt`
- `case1-delayed-A-then-B/B-current.hugr-probe`, `B-current.body.exact.json`, `after-B-publication.reader.utf8.txt`
- `case2-B-refused-A-retained/after-delayed-A-before-B-publication.hugr-probe`, `after-B-refusal-A-intact.hugr-probe` and corresponding reader text
- `case2-B-refused-A-retained/B-interrupted-part-qualification.retained-real.part` and `B-interrupted-part-reader-STOP.reader.utf8.txt`

`SAVED_BODY_HASHES.json` contains hashes of 19 real saved envelopes, exact body extracts, part/primary copies. No saved READ JSON was handwritten as positive proof.

## Failures and unexecuted boundaries

Initial copied focused tests passed the original 19 cases. The first extended focused run failed a **test expectation**: string `"1"` was incorrectly classified as an invalid pseudonym/epoch, despite being permitted by the existing bounded opaque-token policy. No production change followed from that failure; the test input was corrected to an actually disallowed space-bearing token. Concrete failing log, source reconstruction and compilation outputs remain at `test-results/patched-20261103T143057Z-301838/`; `LOCAL_ATTEMPTS.json` names the exact preserved directory and cause. Later 22-case runs passed. Four earlier execution tool-routing errors occurred before test start and are also recorded. Earlier independent acceptance outputs and every original harness/result remain untouched.

Full Phone application/Expo/native-module registration compilation, actual Rx runtime timeout/scheduler/disposal behavior, Android lifecycle/radio/fsync crash durability, physical select/copy/clipboard transfer, general disabled Save/cache, APK/Gradle/version/build/signing and any device/Cloud work were **NOT EXECUTED**. No full-suite, app-runtime or physical PASS is asserted. The exact READ bridge body with four typed arguments compiles with its Context/snapshot collaborators; the full Expo module itself was not compiled.

Declared seams: Expo/React Native registration, Android GATT/characteristic/callback and Rx filter/first/map/error collaborators are local doubles. Timeout is identity in the existing Rx seam, not a tested scheduler. Native fixture observations exercise the actual local production READ classes/callback method but are **not** mapped to JS A/B. Context/worker/UI are doubles around the exact reader method. Production serialization/schema/reopen logic is real; sandbox `FileOutputStream.fd.sync`, atomic `Files.move`, directory `FileChannel.force`, and checksum readback are real filesystem operations. Android `Os.rename/fsync` platform behavior is not tested.

## Preservation and claim ceiling

Final source guards show **4756 preflight files: exactly five permitted source changes, 4751 byte-identical files**. This includes original Phone73 candidate, original READ/freshness harnesses and results, old precompiled outputs, local jars and the two current entry points. Watch and controls were not written. Primary model, original incident store, WRITE report/observer/timing paths, native READ observer/raw callback hooks, transport, subscriptions, timeouts, dependencies, package/version/build/profile inputs and general Save/cache route retain their baseline source identities.

The existing protected custody receipt was reused only for metadata and fresh SHA-256/length comparison, with no raw-content reinterpretation:

- Phone71 saved diagnostic copy: SHA-256 `5d0c74626fd976d1d00a490b26c4254bc0f62fbca2b202d454b0477f13492172`.
- Phone72 active diagnostic copy: SHA-256 `f3534bbf032e6daacbf3ecabc5d53991449f023b2a282235b58f459f9cf86e56`.
- Phone72 WRITE callback probe copy: SHA-256 `e5ff20e6f046eeb4eb2f66064d18b937d65b43975c45bc35218405568d050746`.

Raw callbacks remain `UNASSIGNED_NO_ATT_REQUEST_ID`. All detached-snapshot, coalescing, capture/loss/inactive, process-loss, negative-claim and independent-clock caveats remain. JS-origin labelling earns no native/ATT/Watch association, receipt delivery, Bluetooth root cause, physiological qualification, recording readiness or higher stair.

**Recommendation:** the local diagnostic-attribution proof warrants the parent's **one conditional additional Phone candidate**, only after standing preservation and fresh availability/admission checks. Do not amend/reuse/cancel the original Phone73 candidate, broaden dependencies, or infer device authority. No build was started here. Stop at the parent's separately verified APK boundary.

## Final saved-envelope hash receipt

- `case1-delayed-A-then-B/A-delayed.hugr-probe`: file SHA-256 `08c7517edb104db4a7ecfb2acdbb30014e26d74f2ce4f330d3ffed1e01df5900`; body SHA-256 `f2db60d1e74ecfe41d17c6e4194ba3fdaa5bd2448a03a721a9ec47c3107b9626`.
- `case1-delayed-A-then-B/B-current.hugr-probe`: file SHA-256 `706fafc4b922a1697b1be778ac03a8c651bcd88cfa37b8a716617eb68dd4509c`; body SHA-256 `8daf37739f7b4a786a30658790219aac2bb433ce26a56b5371cb1876480ece29`.
- `case2-B-refused-A-retained/after-B-refusal-A-intact.hugr-probe`: file SHA-256 `0d582622582bcd2919b3aaca92048a3f6acbc701ad17069c0efe24b6a9605c06`; body SHA-256 `1988485a438b65c3f90bdf7c8e1c5f683abb3c860df54fcb48053c2e0efd0a68`.
