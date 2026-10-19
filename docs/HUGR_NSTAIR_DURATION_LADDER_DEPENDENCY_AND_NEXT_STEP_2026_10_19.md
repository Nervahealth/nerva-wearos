# From the passed five-minute gate to eight hours and N1

**19 October 2026. Read-only source findings and recommendation—not implementation, build or device authority.**

## Decision

The next physical target should be **one 30-minute rehearsal**, then **one two-hour rehearsal**, then **one eight-hour feasibility run**, conditional on complete evidence at each rung and acceptable battery/storage/quality. Avoid repeating five-minute runs or inventing a large new debugging programme without a demonstrated regression.

**First we need a compatible duration-and-capacity extension from the proven Watch82/Phone62 source baseline. The currently installed binaries cannot qualify these longer runs unchanged.** Earlier “use this pair” wording meant preserve this tested baseline and workflow; it did not establish longer-duration capability. The passed five-minute result remains valid and preserved.

## Concrete source restrictions

| Boundary | Current restriction | Why a longer run needs a correction |
| --- | --- | --- |
| Watch admission/receipt | Five-minute action/default; `FreshRunReceipt.parse` requires exactly300000ms | A new duration cannot be admitted/read as an arbitrary timer change. |
| Watch lifetime | `FreshRunSession.MAX_LIFETIME_MS`20min from Start | A30min recording cannot retain the current complete delivery lifetime. |
| Phone duration contract | Parser ceiling20min; COMPLETED/qualification requires exactly5min |30min,2hr and8hr receipts cannot qualify unchanged. |
| Phone receipt observation |20min total deadline from connection | It would expire before a30min final receipt. Per-GATT-operation deadlines are distinct and must not be casually relaxed. |
| Custody/report/offline read |25000 selected canonical records;64 manifests/ACK inventory | Already too small for the first proposed rung at today's observed rate. |
| Larger-report handling | Receipt64KiB; durable report128KiB,512 revisions/session,4096 index entries,256 Phone-ACK entries; output character and45sec reader budgets | All must be exercised at scaled inventories; merely raising a timer or one record cap is insufficient. |

Watch source references: `app/src/main/java/com/hugr/wearos/FreshRunRuntime.kt:15,35–59`; `FreshRunSession.kt:56,110–111`; `FreshRunReceipt.kt:20,63–75,108`; actual acquisition timer consumes the admitted receipt's remaining duration. Phone references under `modules/hugr-source-journal/android/src/main/java/health/hugr/sourcejournal/`: `WatchRunReceipt.kt:61–85,106–165`; `PhoneCanonicalEvidenceCore.kt:21–31,137–165`; `HUGRSourceJournalModule.kt:444–510`; `PhoneCustodyRunReportStore.kt:31–44,501–543,707–723`; `HUGRSavedSourceEvidenceActivity.kt:168–200,203–265,339–388,484–603`. Phone orchestration: `lib/bluetooth.ts:829–880,1420–1427`.

Illustrative **linear projections from one five-minute run**, not guaranteed rates or engineering ceilings:

| Duration | Canonical records | Accepted data, MiB | Manifest inventory |
| --- | ---: | ---: | ---: |
|30min|50094|3.09|36|
|2hr|200376|12.35|144|
|8hr|801504|49.42|576|

Thus30min already exceeds25000 records; two/eight hours also exceed64 manifests. Disk space alone is not the main demonstrated limit. Worst-case rates, metadata growth, revisions, verification memory/work and delivery backlog must receive bounded tests and headroom. Projected volume is not proof of battery, physical acquisition or throughput.

## Recommended single engineering package

After explicit approval, extend the existing pipeline, not replace it:

1. Introduce an explicit bounded duration contract for **5min /30min /2hr /8hr**, with the selected duration bound to source/run identity, admission, timer, receipt and Phone qualification. Preserve old five-minute receipt/report readability and accepted evidence; no silent rewrite of historical receipts.
2. Keep controlled stop → quiescence → drain → sync/finalization → exact per-manifest ACK → attributable Watch completion unchanged in meaning. Separate recording duration from a named bounded finalization/delivery grace. Preserve fail-closed inactive/unresolved receipt admission guards.
3. Make custody, receipt inventory, durable revisions and offline verification scale safely through eight-hour-sized fixtures. Bounded memory/work is required; do not simply remove ceilings, suppress checks, omit ACK history or replace full verification with metadata labels.
4. Run production-chain RED-to-GREEN tests for actual longer-duration restrictions; retain five-minute regressions. Exercise selected-duration-minus-one failure, unsupported/over-ceiling durations, large inventories, gaps/corruption/rejected ACK, timer expiry, disconnect, restart and non-mutating offline recheck. Include screen/service-lifetime source tests; distinguish those from physical sleeping-screen/battery evidence.
5. If the complete joined chain and budgets pass, prepare **one signer-continuous compatible candidate pair supporting the ladder** and one concise30min physical card. Stop before build if a concrete dependency prevents the entire acceptance workflow. Do not ship a timer-only or diagnostic-only candidate. No version numbers/artifacts are promised by this planning record.

Source/test work and candidate build require approval; installation and each physical rung remain separate. No source or tests changed in this planning pass.

## Physical ladder after admission

The30min gate should reuse fresh-Main preparation, existing Honor ownership, one explicit new run/one ordinary Connect, automatic stop, a durable Phone COMPLETE_VERIFIED result and success-only restart/offline recheck. Screens should sleep normally; no keep-awake dependence or routine Watch filming. Record start/end battery and any relevant gaps/failure through the bounded evidence route. Preparation/STOP deadlines and transport interruption policy must be named in the final card, not improvised during collection.

Two hours then tests sustained lifetime and growing data/backlog/verification costs. Eight hours tests waking-day feasibility. At every rung require the selected duration, exact custody, matching Watch ACK/completion and offline recheck, not merely service survival. Unexpected integrity or lifecycle failure stops advancement; a narrowly diagnosed correction is different from an automatic retry. Known live freshness labels must remain distinct from saved-run failure.

## Transition to N1

Eight-hour technical success is necessary evidence for sustained collection, **not itself N1 or physiological/scientific validation**. PPG remains live-only in the current canonical contract; no complete raw-PPG claim is inherited.

Alongside the physical ladder, keep the **existing** minimal observation bridge visible: reviewed/consented qualified evidence handoff with clock uncertainty and lineage into the protected scientific-memory/Watchtower route, then an on-demand bounded observer view. No new telemetry platform or autonomous action layer is a prerequisite for the30min rehearsal; no live portal access/deployment/private transfer is authorized here.

Before actual N1, pin its question, available streams, missingness/quality criteria, context/check-in linkage, timing uncertainty, consent and any fixed/manual Suki interaction contract. Engine/clarification research may inform that separate protocol, but no adaptive policy, recruitment, clinical claim or N1 execution follows from duration success.

## Suggested approval wording

> Authorize the minimal duration-ladder source/test extension from the preserved Watch82/Phone62 baseline, covering5min,30min,2hr and8hr duration contracts and bounded custody/report/offline-verification capacity. Preserve the passed five-minute evidence and all exact-ACK/finalization/unresolved-receipt safeguards. Run focused, full and complete joined regressions plus scaled budget tests. After all necessary checks pass, authorize one signer-continuous compatible candidate pair and a concise30min acceptance procedure; report an indispensable dependency before shipping an incomplete pair. No installation, device action, recording, private-data transfer, portal deployment, engine/clarification implementation or N1 execution is included. Apply standing non-force GitHub preservation and exact remote readback automatically.

Standing closeout applies to this planning result: source-owning record → Master → authorized affected private proposal only (none; accepted S4 unchanged) → Alignment last → bounded documentation commits → controlled both-repository write preflight/non-force mirror → independent head/tree/record-byte readback. Raw physiological/media data, credentials and direct identifiers unrelated to engineering are excluded.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**. No earlier run is requalified.
