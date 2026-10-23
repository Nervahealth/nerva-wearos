# Watch84 bounded read-only validator correction — 2026-10-23

## Scope and identity

- **APK inspected read-only:** `/home/ubuntu/HUGR_WATCH84_TWO_STAGE_RECOVERY_2026_10_23/artifacts/HUGR_Watch84w_0.84.0_positional-v2-duration-ladder-candidate.apk`
- **APK SHA-256:** `e0525b5e09c2682de23ff8bc2a26bb10e765d2e308c0e96bf39576a80c0a20db`
- **Bytes:** `13008645`
- **Inspection:** one content-keyed unique `classes3.dex` owner disassembly with the supplied 1,200-second budget and pinned tool environment. The four requested descriptors all resolve uniquely to the same signed `classes3.dex` content (`af2737bf7b3bb1695dcf1daec67fc65b9c995c246ec4b15c55947efb51fe1b01`), so it was dumped **once**.
- **Not performed:** build, signing, repack, archive mutation, installation, launch, device/IDC/contact/research action, or full candidate admission.

## Concrete diagnosis: representation-only, not an APK guard defect

The original validator produced false failures for these three predicates because its expected textual representation differs from the signed `dexdump` form. The preserved Watch source was read solely to interpret the selected signed paths; it was not changed.

| Original failed predicate | Signed method evidence | Exact mismatch | Correction |
|---|---|---|---|
| `rawShapeCodecExactV2Kind3HeaderSlotsAndFlags` | `CardiacInputShapeCodec.encode/decode`, bounded by `SourcePayloadCodec.cardiacInputShape/decodeCardiacInputShape` | The old scalar helper accepts only `const` opcodes. In the signed code, header/slot arithmetic and bit guards are necessarily `add-int/lit8`, `mul-int/lit8`, `rem-int/lit8`, and `and-int/lit8` operands. | Require the exact selected method offsets/opcodes/literals for V2/kind 3, 45-byte header, 10-byte slots, 4,096 cap, and `0x08`/`0x10`/`0x20` flag paths, plus the source boundary calls. |
| `closedV2DurationAndLifetime` | `WatchFreshRunDurationPolicy.<clinit>/accepts/lifetimeMs` | `dexdump` renders signed `const-wide/32` long operands using their unsigned hexadecimal payload comments (`#000493e0`, `#001b7740`, `#006ddd00`, `#01b77400`, `#000dbba0`), not `#long` decimal form demanded by `has_dex_scalar`. | Require ordered actual const instructions for the four exact rungs, selected `Set.contains`, selected acceptance branch, exact 900,000-ms grace operand, add, return, and rejection text. |
| `hardSampleCutoffGuardsAppend` | `FreshRunSession.acceptsSamples/append` | The old predicate expects source local names (`durationMs`) and `if-ge`. Signed DEX has member invocation and Dalvik comparison spelling: `FreshRunReceipt.getDurationMs`, `cmp-long`, then `if-gez`; it also has the negative-elapsed `if-ltz` guard. | Require exact selected instruction offsets for `STARTED`, elapsed-start comparison, negative elapsed reject, duration getter, cutoff compare, `if-gez` reject, and the `acceptsSamples → reject return → SourceJournal.append` order. |

The exact signed methods preserve all named requirements. This is therefore a validator-representation correction only. It is **not** a broad audit, an acceptance relaxation, or a runtime/physiology claim.

## New local correction only

- **Ready corrected validator:** `admit_watch84_candidate_corrected.py`
- **Unchanged local common copy:** `candidate_admission_common.py` (identical SHA-256 to original: `043d4ed65f66963a59c920fc21dc2c89c2fe833691d78807db5f54cc765b5fef`)
- **Original validator retained unchanged:** `/home/ubuntu/HUGR_WATCH84_PHONE66_LADDER_2026_10_22/artifact-preparation/admit_watch84_candidate.py` (SHA-256 `9870129cc578d53ad7df0d7f6cb284366e03abe7087a438cf0fd276a1d2fabc3`)
- The corrected validator retains every unrelated original requirement. It does not use loose whole-DEX presence checks for these method predicates.

## Evidence and focused tests

- `signed-method-evidence/signed-method-extraction.json`: APK identity, single owner-Dex content identity, and original three false results.
- `signed-method-evidence/*.dexdump.txt`: only the selected signed method blocks used by the three corrected paths.
- `tests/test_watch84_signed_fixture_representation.py`: eight focused offline tests. The fixture hashes lock the exact extracted signed blocks; tests fail closed when each required codec scalar/flag/header/slot operand, duration rung/order/grace/lifetime branch, or cutoff/append guard/order is deleted or altered.
- `focused-signed-fixture-tests.log`: final **8 tests, 0 failures**.

No final full admission was run. The parent should perform the full gate with the corrected validator and the preserved pinned environment.
