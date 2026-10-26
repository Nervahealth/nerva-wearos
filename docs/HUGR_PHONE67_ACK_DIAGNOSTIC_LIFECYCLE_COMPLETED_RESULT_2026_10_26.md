# Phone67 ACK rejection and missing diagnostics — completed source/test result

**26 October 2026. Scope:** Neil’s focused Phone67 investigation and permission to correct a demonstrated diagnostic-lifecycle defect. **No build, installation, physical attempt, Bluetooth operation, private-record access or endpoint change.**

## Decision

A **real Phone diagnostic publication defect is demonstrated and narrowly corrected in source**. It is not an ACK-transport correction and not a demonstrated cure for either physical failure. The Watch85/Phone67 thirty-minute gate remains **STOPPED / NOT PASSED**; two-hour/eight-hour physical qualification remains downstream. The successful five-minute recording and earned synthetic capacity results remain unchanged.

| Question | Completed finding | Remaining limit |
|---|---|---|
| What produces `SOURCE_ACK_WRITE_REJECTED`? | Phone’s write-with-response promise rejected for a reason other than the typed operation deadline; `bluetooth.ts` wraps that rejection with this label. | The label does not establish Android ATT dispatch, a Watch ACK rejection, response arrival or a live connection at settlement. The underlying native error/status was not retained in the supplied evidence. |
| Why can a Phone incident fail to save? | Actual receipt-read diagnostic rows carry the preceding bounded R1 requested page offset. Original Kotlin accepted this field only on an R1-write row. It rejects such a frozen incident before file publication. | This is a sufficient software mechanism for missing diagnostics, but the exact snapshot and native publication result from the failed physical run are unavailable. Process/prepublication loss and other publisher failures are not excluded. |
| What does the correction address? | Diagnostic producer/validator compatibility only. | It neither repairs nor explains the ACK write rejection. |

## Exact source path and correction

Production trace: source-manifest verification → Phone ACK intent → write-with-response promise → rejected settlement → incident freeze → detached Expo publication → native strict validation → atomic file publication → isolated native reader.

`lib/bluetooth.ts:1036–1038` provides the **preceding requested R1 offset** to `RECEIPT_PAGE_READ`. The diagnostic model serializes it as `RECEIPT_READ.pageOffset`. Original `ReceiptIncidentDiagnosticStore.kt:333–336` permits it only on `R1_WRITE`. Strict validation occurs before root/file publication.

**Only two functional production files changed:**

1. `lib/receiptIncidentDiagnosticModel.ts`: retain the bounded offset only on `R1_WRITE` and `RECEIPT_READ`; omit it for all other kinds.
2. `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/ReceiptIncidentDiagnosticStore.kt`: accept exactly those two kinds while retaining `0..999999`, strict keys/types/identities, frozen-state requirements, bounded storage, checksum/reopen and isolation safeguards.

Four permanent regression files were integrated. `bluetooth.ts`, detached publisher, native bridge registration, Watch code, transport/custody, deadlines, retry/cancellation, packages, app versions, dependencies, lockfile and signing were not changed. The admitted Watch85/Phone67 APKs remain byte-identical; **the installed Phone67 does not contain this correction**.

## Earned tests and failed-before proof

| Executed evidence | Result | What it establishes |
|---|---|---|
| Actual JavaScript model snapshot through original Kotlin store | **Expected FAIL:** `RECEIPT_INCIDENT_PAGE_OFFSET_KIND_INVALID` before saving | Cross-language publication mismatch, not merely a mocked exporter flag. |
| Same snapshot through corrected Kotlin store | **PASS: 2/2** cached Kotlin/Robolectric tests | Publication and isolated-store read/reopen succeed; a C1 row with that field remains rejected. |
| Actual production Bluetooth/model lifecycle with fake clock/adapters | **PASS: 16 cases** | Rejection → freeze → detached publication, deadline/cancel/late-result containment, disconnect-before/after-settlement and deferred publisher ordering. Injected faults are not hardware evidence. |
| Focused model/publisher suite | **PASS: 12 TAP tests plus publisher seams** | Offset allowlist, bounded retention, false/missing/mismatched/rejected publication results and explicit loss/no recursive retry. |
| Targeted regressions after controlled-source integration | **PASS** | The final integrated source is byte-bound to the tested correction; no capacity-ladder rerun. |

Original failures, baseline sources, invocations, synthetic fixture, exact hashes and full logs are retained with the correction. Direct JVM/Robolectric tests are **not a full Expo app compile or physical Android/React Native/BLE execution**. The existing native-store tests retain interrupted-temp/publication-loss coverage; actual OS process death before publication remains unobservable from a missing report.

A late result, false acknowledgement or publisher rejection remains explicit uncertainty. Absence of a saved report **never proves request non-arrival**. Local operation counters, requested offsets, JS transaction IDs and clocks are not cross-device correlation keys.

## The minimum next evidence — not another blind APK retry

The unresolved acquisition/transport question is now specific: **did the failed source-control write reject before Android dispatched it, or did Android report a GATT/link failure after dispatch?** The missing evidence is the native write result/error status and same-attempt disconnect sequence, not comprehensive Phone–Watch clock correlation.

**Recommended next move: one focused Android/Wear OS/BLE engineering session with direct ADB access to the Honor.** A personal Android-capable computer and USB/ADB, or a qualified engineer with that setup, is the practical route. No equipment purchase or access is assumed or authorized here.

Before any separately authorized reproduction, prove that a narrowly filtered, payload-free trace reaches a known local file and survives disconnect. Capture only:

- Phone source-control operation submission/result and sanitized BLE/native error codes;
- Android write enqueue/dispatch evidence, `onCharacteristicWrite` status if delivered, and same-attempt disconnect callback;
- native diagnostic publisher validation/atomic-reopen acknowledgement, followed by the complete isolated-reader result.

Stop at the first failure/terminal result or the existing finite gate horizon. Do not reconnect or retry. Retrieve the saved local trace after disconnect without relaunching Main. If this trace cannot be made reliable, **stop before recording** and resolve that concrete setup blocker with the engineer.

Only if the Android trace leaves actual remote request/response delivery unresolved is a bounded private HCI trace warranted. It can contain sensitive data and needs separate authority. Watch-local handler evidence may then answer a named remaining question; it is not a blanket prerequisite. No guessed equal-counter/offset/clock association is permitted.

This session is a focused transport escalation, not another architecture audit or telemetry platform. A future Phone-only candidate containing the publication correction may be appropriate after its compilation/admission is separately authorized; keep Watch85 unchanged unless new evidence identifies a concrete dependency.

## Run preservation and clarification continuity

The failed Watch84 receipt, the stopped Watch85 attempt and supplied private evidence are preserved without rewrite, recovery, new Start or manual closeout. Watch85’s post-STOP saved-run phase has not been newly assessed under this source/test task. Before an update/new recording, a separately authorized bounded passive current-receipt read must establish its saved state; no hidden closeout is implied.

The next **successful**, independently verified V2 export would retain original canonical bytes, original CARDIAC value/status slots and anomaly markers, parent/manifest membership, Watch receipt, durable Phone report and available build provenance. It would unlock the clarification agent’s existing descriptive/selection-window admission work. It would not by itself qualify RMSSD, native cross-callback semantics, endpoint completeness or learning. The clarification worksheet is not a prerequisite for the physical custody ladder. Samsung cardiac-drain work remains separate and unimplemented here.

## Preservation

Source-owned result/authority and bounded source/test evidence → Master Task List → Alignment last → coherent bounded commits → controlled both-repository write preflight and non-force mirrors → independent exact remote head/tree/changed-byte readback. Private physiological records, reports/media, provider correspondence, APKs, caches and credentials are excluded. No VIS-COUNT/portal/IDC action.

Historical literals remain: Watch69 **`NOT QUALIFIED`**; Watch68 v1 **`UNCLASSIFIED / NOT VERIFIED`**; Watch58 **`TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`**. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, scientific selection/window ownership, baseline/learning and Loop A remain unchanged.
