> SYNTHETIC CORRECTION REGRESSION ONLY — NOT THE REAL FIVE-MINUTE RECORDING.

# HUGR selected-run export independent inventory

**Verifier outcome:** `VERIFIED_OFFLINE_CONTRACT_V1`  
**Package SHA-256:** `2fa48ed18be3cbbdaf455dfc38722cebe93da1874db7d843a67b79d78c4dd80e`  
**Package bytes:** `5655`  
**Canonical records:** `5`

This is an **offline byte/format/inventory verification result**, not a physiological, clock-calibration, beat-chronology, RMSSD, scientific, clinical, or origin-authentication claim. Canonical payload values are intentionally not rendered.

## Identity and package binding

- Source session: `c1ed1c54-a2e1-4a2c-a3eb-7b8a9e04c621`
- Run ID: `63c6f16e-69f4-43e7-a857-dba8ce47bf3e`
- Receipt format: `V1`; completed requested duration: `300000` ms
- Manifests / exact Watch accepted ACKs: `1` / `1`
- Exporter build observed in provenance: `health.hugr.sourcejournal.custodyharness.test` / `unknown`
- Producer SDK build: `unknown` (**separate from current exporter build; `unknown` means not stored**).

## Canonical stream inventory

| Code | Stream | Records | Source timestamp min/max (ms) | Shared timestamp observed | Nonmonotonic in file-order observed | Units established by source schema | Payload/status treatment |
|---:|---|---:|---|---|---|---|---|
| 1 | CARDIAC | 1 | 10001 / 10001 | false | false | IBI source field is milliseconds when present; heart-rate unit is not asserted by this reader. | opaque source payload; schema may carry kind/value/status and callback/batch metadata. No IBI chronology qualification or RMSSD is computed. |
| 2 | EDA | 1 | 10002 / 10002 | false | false | skin conductance source field: microsiemens (µS) | opaque source payload; source schema may carry conductance and delivery/batch metadata. |
| 3 | ACCEL | 1 | 10003 / 10003 | false | false | raw source x/y/z integer-axis unit not established by this reader | opaque source payload; source schema may carry x/y/z and delivery/batch metadata. |
| 4 | SKIN_TEMP | 1 | 10004 / 10004 | false | false | object and ambient temperature source fields: degrees Celsius (°C) | opaque source payload; source schema may carry object/ambient temperatures, status, and delivery/batch metadata. |
| 5 | DEVICE_HEALTH | 1 | 10005 / 10005 | false | false | not applicable / schema-specific status metadata | opaque device-health/status metadata; values are intentionally not rendered. |

## Finite-reader limits

- ZIP input ≤ 570425344 bytes; extracted member aggregate ≤ 570425344 bytes; canonical bytes ≤ 536870912; records ≤ 2000000.
- Manifests ≤ 2048; manifest ranges ≤ 10240; receipt ≤ 2097152 bytes; report/JSON ≤ 8388608 bytes.
- ZIP paths, duplicate entries, symlinks, encryption, CRC/read failures, unbounded compression ratios, duplicate JSON keys, non-finite JSON values, malformed UUIDs, and input ambiguity fail closed.
- Record ordering is verified from the canonical byte stream; bounded SQLite scratch is used for stream-sequence ordering and no payload values are retained in the output.

## Scope caveat

Readability or successful verification **does not establish physiological truth, qualified IBI chronology, RMSSD, device-clock origin/accuracy, full historical report-chain requalification, or authenticated origin**. The package ZIP is integrity-checked but is not encrypted/authenticated origin evidence.
