> SYNTHETIC SOFTWARE REGRESSION ONLY — NOT A REAL RECORDING.

# HUGR selected-run export independent inventory

**Verifier outcome:** `VERIFIED_OFFLINE_CONTRACT_V1`  
**Package SHA-256:** `9b0e7501f5a4c3fbf594d8f0b49e15ed1096f07ff8f4a00fbeb80d9ef9f9ed2e`  
**Package bytes:** `10697347`  
**Canonical records:** `801505`

This is an **offline byte/format/inventory verification result**, not a physiological, clock-calibration, beat-chronology, RMSSD, scientific, clinical, or origin-authentication claim. Canonical payload values are intentionally not rendered.

## Identity and package binding

- Source session: `5e4ada87-535a-4dac-b629-f4d07bf2f040`
- Run ID: `9b8e464c-e2aa-4a68-9c12-81a1139e8f1b`
- Receipt format: `V2`; completed requested duration: `28800000` ms
- Manifests / exact Watch accepted ACKs: `480` / `480`
- Exporter build observed in provenance: `health.hugr.sourcejournal.custodyharness.test` / `unknown`
- Producer SDK build: `unknown` (**separate from current exporter build; `unknown` means not stored**).

## Canonical stream inventory

| Code | Stream | Records | Source timestamp min/max (ms) | Shared timestamp observed | Nonmonotonic in file-order observed | Units established by source schema | Payload/status treatment |
|---:|---|---:|---|---|---|---|---|
| 1 | CARDIAC | 160301 | 1800000000000 / 1800028799855 | false | false | IBI source field is milliseconds when present; heart-rate unit is not asserted by this reader. | opaque source payload; schema may carry kind/value/status and callback/batch metadata. No IBI chronology qualification or RMSSD is computed. |
| 2 | EDA | 160301 | 1800000000035 / 1800028799891 | false | false | skin conductance source field: microsiemens (µS) | opaque source payload; source schema may carry conductance and delivery/batch metadata. |
| 3 | ACCEL | 160301 | 1800000000071 / 1800028799927 | false | false | raw source x/y/z integer-axis unit not established by this reader | opaque source payload; source schema may carry x/y/z and delivery/batch metadata. |
| 4 | SKIN_TEMP | 160301 | 1800000000107 / 1800028799963 | false | false | object and ambient temperature source fields: degrees Celsius (°C) | opaque source payload; source schema may carry object/ambient temperatures, status, and delivery/batch metadata. |
| 5 | DEVICE_HEALTH | 160301 | 1800000000143 / 1800028799999 | false | false | not applicable / schema-specific status metadata | opaque device-health/status metadata; values are intentionally not rendered. |

## Finite-reader limits

- ZIP input ≤ 570425344 bytes; extracted member aggregate ≤ 570425344 bytes; canonical bytes ≤ 536870912; records ≤ 2000000.
- Manifests ≤ 2048; manifest ranges ≤ 10240; receipt ≤ 2097152 bytes; report/JSON ≤ 8388608 bytes.
- ZIP paths, duplicate entries, symlinks, encryption, CRC/read failures, unbounded compression ratios, duplicate JSON keys, non-finite JSON values, malformed UUIDs, and input ambiguity fail closed.
- Record ordering is verified from the canonical byte stream; bounded SQLite scratch is used for stream-sequence ordering and no payload values are retained in the output.

## Scope caveat

Readability or successful verification **does not establish physiological truth, qualified IBI chronology, RMSSD, device-clock origin/accuracy, full historical report-chain requalification, or authenticated origin**. The package ZIP is integrity-checked but is not encrypted/authenticated origin evidence.
