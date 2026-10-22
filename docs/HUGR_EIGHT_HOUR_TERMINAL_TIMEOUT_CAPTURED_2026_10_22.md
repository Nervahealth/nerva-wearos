# Eight-hour terminal failure — actual cause captured

The one necessary native-x86 production-ingestion replay of the existing source-bound eight-hour synthetic fixture reproduced the terminal failure. The exact returned `failureReason` and durable attached receipt `custodyFailureCode` are both `DURATION_OR_CANCELLATION_BUDGET_EXCEEDED`. Native JUnit: one test, one assertion failure, no errors/skips, 1329.723 seconds. The original generic failure/JUnit and all subsequent setup and reproduction evidence are preserved separately.

Before assertion, the test captured FINALIZED (PARTIAL / WAITING_FOR_EXACT_PHONE_CUSTODY) and COMPLETED (FAILED / DURATION_OR_CANCELLATION_BUDGET_EXCEEDED) result/receipt/report fields. The canonical file is still 78,936,626 bytes and matches the original synthetic external hash; the failed report's acceptedFileBytes=0/custodyStatus=MISSING is a qualification status, not evidence that the canonical bytes were deleted. Watch exact478ACK evidence remains unchanged. The specific internal expensive pass is not yet established; no corruption, physiological, Samsung or Honor performance inference is made.

Quiescent failed-state retention succeeded: 1,920 files and 662,542,462 bytes, below the 1 GiB / 2,048-file limits. It includes unchanged canonical bytes, SQLite custody and immutable report/index state with exact per-file source/copy hashes. Large retained state stays on the isolated authorized host, not GitHub. Only sanitized synthetic result evidence and source tests may be preserved remotely.

A new opt-in entry point in the existing TerminalVerifierPassMeasurementTest reuses the retained canonical/SQLite copy without another ingestion. It reuses current private core passes, original cumulative 180-second budget, at most one isolated 180-second measurement of each incomplete pass, and existing 15-minute global test limit. Those are diagnostic measurements, not fresh intervals for a production admission. It checks original retained input hashes before/after, opens only the disposable DB read-only, stores no row list, and makes no receipt/report/ACK/qualification mutation. Original V1 measurement entry and production code remain unchanged. Measurement is pending.

No production correction, deadline increase, acceptance relaxation, cutoff/drain change, build submission, installation, physical recording, IDC change or real-data access occurs in this gate. Prior5/30/120nativecompletePASS and original8hrfailure remain; conditional Watch84/Phone66 candidates still blocked until actual affected correction/full terminal-report/restart/offline/export/independentreader succeeds. Samsung sequence/final-batch questions remain separate. T2.2, accepted adapter/RMSSD companion, October reconciliation, S4, prior private five-minute evidence unchanged.

Source-owned receipt → Master → Alignment last; bounded source/test commits → both-repository write preflight → controlled non-force mirror → independent exact remote tree/blob readback. No private proposal affected. Source preservation is not canonical recording backup.

Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

END_OF_CAPTURED_TERMINAL_TIMEOUT
