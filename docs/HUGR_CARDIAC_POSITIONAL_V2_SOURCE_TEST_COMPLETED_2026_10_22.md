# CARDIAC positional V2 source/test closure — 22 October 2026

## Authority and disposition

21 October 2026 23:00:35 Europe/Oslo: narrow input-fidelity source/test implementation and existing decoder/verifier/private export compatibility; no build/install/collection, historical gate rerun, additional receipt export or architecture review. Disposition: **FOCUSED CURRENT-SOURCE/COMPATIBILITY TESTS PASSED; NO CANDIDATE BUILT.** Publication is established only by the separate independent remote-readback proof; local commits alone are not remote preservation.

## Bounded implementation

Prospective CARDIAC input-shape payload V2 kind3; unchanged canonical framing V1, original V1 encoder body/bytes, transport and ACK/report/finalization rules. Read raw value/status fields once; preserve original positional indices, lengths/field states and null/non-Int/type/access markers. Genuine SDK Int32 values/statuses, including zero and error codes, retain exact signed bits. Null/type/access states do not become fabricated physiological values. Unsupported Number/object values are marked, not coerced or stringified.

Capture is capped at4096 original slots per point; oversize raw lengths remain explicit with truncation, not silently complete. Chunk20 slots maximum:245-byte payload/293-byte canonical record/329-byte DATA_FRAME fits existing minimum ATT364. Each chunk binds the actual appended V1 HR record index. Original i32 point coordinates remain exact; a pre-release explicit flag is required for an existing V1 u16 anchor projection. Unknown flags, inconsistent batch fields, slot markers, missing/duplicate chunks and changed anchors fail closed.

Malformed future V1 compatibility projections remain in canonical custody with anomaly marking but are not emitted into legacy physiological measurement consumers. V2 shape records are never HR/IBI observations or additional native beats. Live Phone custody writes exact bytes first, then recognises V2 metadata without feeding it to accepted physiological-event consumers.

Native passive shape verification stores only bounded group metadata in invocation-owned scratch SQLite: one loaded group,26-byte bitmap at most205 chunks, fixed64KiB page cache,256-row write batches, original source tables never attached or written. The existing eight-member private ZIP route carries original canonical bytes/receipt/report unchanged; optional schema provenance and README are additions within the existing members, not a new exporter or receipt export. Independent V1/V2 decoder/verifier checks exact byte membership and completeness; private derivative retains file order, record/source identities, original slots, raw flags/batching/status markers and explicitly marked filler cells.

## Executed synthetic evidence

| Boundary | Earned result |
| --- | --- |
| Final Watch pure-JVM producer/codec/journal/frame tests | 53 tests; failures0/errors0/skips0 |
| Final selected Phone native SQLite/schema/export production tests | 16 tests; failures0/errors0/skips0 |
| Phone TypeScript affected live decoder/ingestor tests |26 pass; complete source suite163 pass/3 fixture-dependent skips |
| Independent Python V2/V1 export reader suite |32 pass; two affected derivative tests separately retested after final raw-marker additions |
| Executed Watch→independent decoder |212 exact payload round-trips; includes malformed slots/field access/batching/oversize and explicit coordinate projection |
| Native writer→manifest→completed receipt/report→restart→export→independent reader |Two actual native-produced synthetic ZIPs PASS against separately reconstructed pre-export input bytes; original source/report byte hashes unchanged |
| Existing V1/history/build/frozen/adapter scope |Exact V1 encoder body and all other tracked files outside the whitelist unchanged |

The composed chain retains executed Watch V2 payload bytes and original anchor identities; a synthetic V1 prefix supplies complete record coverage from index1 instead of weakening the completed-run gate. These are software/source tests, not Samsung callback/hardware behaviour, real-record repair or physiological qualification.

## Retained failures and release limits

The first focused native continuation compiled current Kotlin but the emulated JIT worker failed to connect within120 seconds before any test. Its zero-test failure remains preserved. The interpreter-mode run executed31 tests with21 failures: newly disk-backed tests needed the existing Robolectric/native SQLite annotations; the disposable runner did not create the explicit output parent; the joined fixture lacked all required streams/source-prefix coverage. These specific setup/fixture faults were corrected without weakening production completion/report predicates. Native V2 interpretation was also confined to CARDIAC to preserve historical opaque other-stream bytes. The affected run then passed14/16, including both actual production-package chains; two remaining unit checks were retested only: a finite test-only maximum-fixture budget under emulation and exact earlier anchor rejection plus missing-anomaly rejection. Final unique affected disposition16 PASS, identical production source across runs. The already-passed9 scratch tests were not repeated. All failed logs/XML and the prior wider-native-suite NOT COMPLETE limit remain preserved. Production duration/verifier/export budgets unchanged; an emulation unit deadline exceedance is not corruption.

Full Watch Android callback compilation is not earned: the admitted build-lane egress compile-only prerequisite was missing after reset. No substitute/new project/signer/stub has been generated. Recover that exact existing prerequisite and complete whole-Android compilation before any separately authorized candidate build. The changed V2 inventory/transport workload has not undergone an eight-hour capacity/performance run; prior V1 duration results are preserved, not relabelled as V2 results.

## Smallest coordinated proposal and boundaries

Propose one signer/package/project-continuous Watch84 + Phone66 pair, only after full prebuild source compilation and affected integration checks pass. Phone65 live decoder is V1-only and is not a compatible prospective V2 receiver; no Watch-only candidate or unnecessary receipt-export change is proposed. Version/package/project/signer files remain unchanged in this task.

Endpoint source continuation completed privately and separately; no endpoint finalization/flush correction is included. Native IBI ordering/non-overlap/continuity and beat timing remain unresolved. Provider question prepared privately, not sent. Scientific selection/window/exclusion-bias ownership stays with the clarification agent; no RMSSD calculation/qualification, baseline update, learning, Loop A or N1.

Original physical five-minute acceptance and all historical receipts/bytes/results are unchanged. Preserve T2.2, accepted baseline adapter/RMSSD companion, October reconciliation and S4. Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

## Preservation

Source-owned receipt/technical contract → Master → no authorized/affected private proposal → Alignment last → bounded coherent source/control commits → reviewed both-repository restored-login-only preflight/non-force mirror → independent exact branch/tree/changed-blob readback. Only source, synthetic tests/contract and sanitized completion are staged. Real recordings/physiological findings/private endpoint report/provider correspondence remain outside Git. Source backup is not canonical recording backup.

END_OF_SOURCE_TEST_RECEIPT
