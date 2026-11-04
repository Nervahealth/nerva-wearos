# Exact failed-run read-only inventory reader adoption — 4 November 2026

Neil authorized adopting the exact tested one-file reader into maintained Phone source and retaining its passing integration testcase, then answering actual-store availability from existing evidence only. STOP before any APK or device access. This receipt supersedes only the earlier proposal-only source-adoption status; historical test and failed-run results remain unchanged.

## Exact maintained source change

Only production path `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/HUGRSavedSourceEvidenceActivity.kt` changes: preimage SHA-256 `4cbc75c3f4b70aec83fe428dd07e6f215236b50e91eef0e05eee37ad6a67869f`; tested/adopted postimage `9513c09a330f78899e65b84aea6fb7fb08b582956d2dd9154162f692feb993c0`. The added FAILED/no-custody branch invokes the existing isolated selected-source reader and keeps durable report identity stable across readback. No writer, transport, dependency, version, timeout, ACK or custody-promotion behavior changes.

The output separates current Phone byte/manifest equality and verified contiguous boundary from historical Phone ACK intent and retained Watch receipt claims. FAILED stays FAILED; final custody remains unavailable; final tail extent stays UNKNOWN. A changed report or stopped canonical proof withholds the current boundary rather than borrowing a historical marker or ACK endpoint. The normal journal, recovery, BLE and sensor paths are not invoked by this inventory read.

Retained testcase: `modules/hugr-source-journal/android/src/test/java/health/hugr/sourcejournal/FailedPartialInventoryProductionIntegrationTest.kt`, byte-identical to the executed testcase. Existing `ReportDirectoryLinuxShadow.kt` is unchanged. The test requires a caller-selected disposable evidence-output directory through `hugr.storage.output`; it does not point at real stores. Full execution receipts and generated fictional files stay private; this lane preserves only nonsensitive source/test code and a sanitized test receipt.

## Earned native integration result, not rerun here

One actual current-production append/native SQLite/proposed isolated-reader testcase passed on 4 November 2026 at 08:54:21.934730–08:55:01.905200 UTC: one test, zero failures/errors/skips, no retry. Native Robolectric 4.12.2 / SDK28 / SQLite 3.32.2 reused already available cached inputs. The complete production storage/schema body and full proposed reader were exercised; only unexercised Expo/metadata/diagnostics types had explicit compile-only adapters. Real Linux directory force/atomic rename was used by the existing test shadow; Android physical power-loss durability is not established.

Two fictional 52-byte records were actually appended into a 104-byte accepted file and two SQLite rows. Repeating record 1 returned accepted zero/duplicate one, adding no bytes or rows. After Phone ACK INTENT and durable failure, close/fresh isolated read recomputed exact row, byte, manifest and stream-range equality through record 2 while retaining FAILED/custody unavailable/tail unknown. A preserved pristine copy preceded damage to one CRC byte of record 1 in the disposable working file. Record 2 stayed exact and both rows remained, but fresh read stopped at `CANONICAL_ROW_BYTE_HASH_MISMATCH`, with boundary UNKNOWN and no current equality claim across the damage. All read-before/read-after source/report/index/database/WAL/SHM evidence was identical; FAILED and ACK facts were unchanged. Authored fictional Watch claims were not physical Watch acceptance.

No new compile, test execution or native-runtime action is performed by this source-adoption step. Exact code adoption is verified by byte equality to the admitted native-test inputs, not another test count or physical result.

## Existing actual Phone74 evidence availability

The bounded existing custody-receipt and filename inventory found rendered reports/diagnostics/media and documentary/source/artifact materials, **not an authorized source-bound copy of the actual failed-run canonical Phone store set**. In the checked retained scope there is no selected accepted binary plus coherent source-journal SQLite/database sidecars and durable report/index copy sufficient for offline reader execution. Historical recording parents, source/APK packages and newly generated fictional SQLite stores are not this actual failed-run store copy.

The actual Phone74 failure cannot be inventoried offline from those rendered reports. `canonical_stores_opened=NO` and `canonical_custody_available=NO` describe the earlier reader route, not empty or lost device data. Actual device-store survival remains UNKNOWN / NOT ADMITTED. No actual accepted file, database or real record was opened in this assessment. STOP: no new store retrieval, device access, APK, reconnect or recording follows.

## Preservation and closed boundary

Standing preservation order: source-owning Phone reader/test/receipt first, documentary Watch mirror, Master continuation next, Alignment last. Only existing authorized preservation branches, both-repository actual blob-write preflight, non-force ref updates and independent exact head/tree/blob-byte readback. Local adoption or a private archive alone is not remote preservation. Generated fixtures/databases, raw reports/diagnostics/media, actual run identifiers, APKs, keys and credentials remain private/off GitHub.

Installed Phone74 and Watch85 remain unchanged. No new version, build, signing, installation, device/Cloud action, BLE/status-133 investigation, failure reset, ACK change, real-record inspection or inference occurred. Transport fault remains OPEN separately; safe resume, physical retained-data custody and dependable thirty-minute capture/rolling delivery/final custody remain unearned. Clarification Book One and VIS-COUNT are unchanged and independent.

END EXACT FAILED READER ADOPTION RECEIPT
