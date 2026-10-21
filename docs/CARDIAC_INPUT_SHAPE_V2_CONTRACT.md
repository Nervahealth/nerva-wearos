# CARDIAC positional input-shape V2 — implementation contract

Scope: additive prospective CARDIAC payload evidence only. Canonical envelope VERSION=1 unchanged; V1 payload encoder/decoder/historical bytes unchanged. No sensing, transport, finalization, ACK, duration budget, clinical adapter/window/RMSSD change. Native consecutiveness is NOT established. No build/install/collection. All test records synthetic.

## Wire layout

All integers little endian. V2 payload header is exactly 45 bytes, equivalent to Python struct `<BBBHiiiqBBiiiiH`:

1. u8 payload version = 2
2. u8 evidence kind = 3 (INPUT_SHAPE, not HR/IBI measurement)
3. u8 flags: existing screen/flush/batch bits0..2; bit3 shape anomaly; bit4 original slots truncated; bit5 explicit legacy HR point-metadata saturation. No other bits. This bit5 is a pre-release contract amendment; no V2 APK/real recording has been built or substituted.
4. u16 batchSize (existing delivery metadata, bounded same as V1)
5. i32 callbackId (>=0)
6. i32 original pointIndex (>=0)
7. i32 original pointCount (>0; index<count)
8. i64 anchorRecordIndex (>0): ACTUAL canonical V1 HR recordIndex for this point, captured from the existing append return. No invented identifier. Group identity is source session + this anchor.
9. u8 valuesFieldState
10. u8 statusesFieldState
11. i32 originalValuesLength (-1 when not LIST, >=0 when LIST)
12. i32 originalStatusesLength (same)
13. i32 capturedSlotCount = min(max(0, original lengths), 4096)
14. i32 chunkStartIndex (0-based; multiples of20)
15. u16 chunkSlotCount (0..20)

Each chunk appends exactly `chunkSlotCount` slots, 10 bytes/slot: u8 valueKind, u8 statusKind, i32 valueBytes, i32 statusBytes. Slot original index is `chunkStartIndex+localIndex`; no reindexing/compaction.

Field-state codes: 0 NULL_OR_ABSENT (getter cannot distinguish absent from NULL; make this limit explicit), 1 LIST (empty LIST length0 differs from NULL), 2 NON_LIST, 3 ACCESS_ERROR (getter/size access threw). Never arbitrary-object toString/reflection/serialization.

Slot-kind codes: 0 ABSENT (position beyond that original LIST length, or field NULL_OR_ABSENT/NON_LIST), 1 NULL, 2 INT32 (genuine Kotlin Int/java.lang.Integer; exact signed bits including 0 and -1), 3 NON_INT_NUMBER (Long, Double, etc.; do not convert), 4 OTHER_TYPE (includes Boolean/string/object), 5 ACCESS_ERROR. For field state ACCESS_ERROR, every slot captured through the opposite list carries ACCESS_ERROR, not ABSENT: the unknown field cannot justify a missing-position claim. Getter/size/index ordinary exceptions become explicit markers; fatal JVM Errors propagate rather than masquerading as input-shape anomalies. A non-INT32 slot's i32 cell MUST be zero filler, interpreted as absent numeric value, never a native zero/status. Null and unsupported types are evidence markers, not Samsung statuses.

MAX_CAPTURED_SLOTS=4096; MAX_SLOTS_PER_CHUNK=20; max payload245, canonical293, single DATA_FRAME329 <= existing admitted minimum ATT364. Large batched lists are chunked (do not use realtime four-item limit). Only malicious/oversized shape beyond4096 is explicitly truncated: original lengths retained, captured count4096, bit4 set; never silently complete. Both empty/NULL fields still emit ONE chunk start0,count0. Number of chunks=max(1,ceil(captured/20)). All chunks have exactly matching group metadata and flags, contiguous starts/counts with no duplicate/missing captured slots. Last chunk carries the remainder, including20 at exact multiple. V2 chunk record's source timestamp equals its HR anchor parent timestamp, not individual beat time.

Anomaly bit3 is true iff any of: states differ, NON_LIST/ACCESS_ERROR field, unequal original lengths for two lists, truncation, or any captured NULL/NON_INT_NUMBER/OTHER_TYPE/ACCESS_ERROR slot. Both NULL fields and both empty lists by themselves are NOT anomalies. ABSENT in asymmetric shapes already covered by state/length mismatch. Flags are consistent across all chunks (compute once over bounded captured shape). Decoder validates known flags and length/state/kind/filler/position consistency. Batch bit2 is set iff declared batchSize >1. Bit5 is set iff original pointIndex or pointCount exceeds65535; V2 retains their exact i32 values, while only the existing V1 anchor projects to saturated u16. Unmarked/spurious saturation is rejected. This documents projection, not equivalence of native events; the actual canonical anchor record index still uniquely binds the group. Truncation must be exact for declared original maximum. A complete captured set can still represent an explicitly incomplete raw shape; never admit native adjacency from either.

## Producer integration

Read both raw IBI fields ONCE per point before normalization; capture bounded immutable shape and exact Int slots, including per-point raw NULL/empty/non-list states. V1 clean integer lists retain their existing payload encoding. Preserve existing historical V1 encoders and bytes; malformed future inputs must not make synthetic fallback values/statuses masquerade as genuine. Bound any legacy compatibility projection using captured shape rather than traversing unbounded raw lists. Original values/status indices live in V2. Emit V1 HR anchor using existing append; retain its actual identity; V2 chunks go through `appendRequiredSourceRecord` and existing finalized no-new-samples guard. Do not change transports or broadcast V2 kind3 as a physiological measurement. Retain malformed V1 projections in canonical custody, but suppress their measurement broadcast and Phone accepted-event route before values/statuses enter physiological storage/telemetry. This is structural input-fidelity containment, not mixed-status scientific selection. Tests must lock malformed legacy behavior as derivative/anomalous, not native.

## Consumer/export integration

Existing Phone custody/report/writer rules continue exact opaque canonical byte custody. Export carries canonical original bytes unchanged through SAME eight-member ZIP V1; no SQLite or extra receipt export. Add a schema declaration/README within existing provenance contract (add optional bounded supported payload schema key, Python verifier backwards-compatible with absence) and a narrow passive V2 schema check in the existing canonical export pass if practical; never call pending recovery or mutation.

Independent decoder/verifier validates V2 locally and captured group completeness/HR anchor binding across records in RECORD-INDEX order, not file order; preserve original physical offsets and source sequence. Group metadata/anchor/source timestamp, batch size and lower-four delivery/anomaly flags must match actual V1 HR anchor. Only bit5 explicitly permits the V1 u16 point projection for out-of-range original V2 i32 coordinates; no implicit saturation is accepted, and ordinary test coordinates remain in range. V1 true25-byte/known-kind decode remains exact; historical opaque fixtures may remain opaque, never falsely decode. Unknown payload versions/kinds are not silently treated as V1/qualified. A nonempty captured shape never permits a trailing zero-slot chunk; an empty captured group is exactly one chunk.

Use SQLite-backed grouping or bounded streaming/index traversal, not whole-recording in-memory payload accumulation. Reject duplicate/missing chunk ranges, changed anchors, wrong flags/state/length/kinds/nonzero fillers and unknown V2 kind. Structurally valid declared raw truncation remains byte-custody-verifiable but explicitly incomplete input-shape evidence. No selection/window/physiological admission policy.

## Mandatory synthetic evidence

Clean zero/Error signed boundaries; unequal lists/null shifted positions (including equal input lengths but nulls at different offsets); field NULL vs empty vs non-list/access failure; noninteger Number/Boolean/arbitrary-object without conversion/stringification; batched >4 and >20/40 exact boundaries; max4096 and >4096 explicit truncation; exact encode/decode slots and chunk metadata; complete journal -> DATA_FRAME -> Phone writer -> manifest -> report -> export -> independent decoder verification; frame fit at existing ATT364; original V1 bytes/readability/mixed-version packages and unchanged original synthetic files; reject partial/duplicate/tampered groups. No old physiological ZIP gate rerun or records repair.
