# Finish the recording instrument and restore live N1 observation

**2026-10-09. Source inspection, dependency reconciliation and implementation plan only.** Neil’s endorsed note authorizes this preparation, not app changes, builds, device actions, private-data transfer or observer deployment. Watch77 and installed Honor Phone59 remain unchanged. Phone60 replacement is admitted, not reported installed. This plan supersedes any suggestion that retrospective reports alone fulfil the live-observation objective; earlier records remain history.

## Immediate decision

**Use the Samsung/Fold as the future primary Phone host, but first finish the existing-session evidence decision on the Honor.** The Honor holds the actual accepted-record binary and SQLite custody metadata from the attempt. Installing the same APK on another phone does not move that custody. A cloned icon, login or app is not verified source-store continuity. No general technical reason was found that requires the Honor indefinitely; actual Samsung runtime/SDK/permission/companion behavior still needs admission.

The next physical boundary remains the admitted, ordinary in-place Phone59→Phone60 update **on Honor**, followed by separately authorized offline **HUGR Saved Source Evidence** readback. Neither requires repairing Bluetooth merely to inspect stored evidence. Do not open Main HUGR, Connect or Watch77, uninstall, clear data, transfer the watch, change pairing or switch Bluetooth under this plan. The existing device gate is `HUGR_NSTAIR_PHONE60_PHYSICAL_READBACK_GATE_2026_10_08.md`.

After that evidence decision, prepare a separately reviewed Samsung host gate: establish the actual Samsung model/Android version and Watch software; use the same package/signer; retain Honor app/data; determine whether HUGR BLE alone or a companion transfer is necessary. Samsung documents a watch-transfer function, but explicitly warns that depending on model/software reconnecting can reset the Watch and remove data [1] [2]. **Never accept reset, uninstall, restore or clearing as a workaround.** General Samsung backup guidance does not prove HUGR’s frozen/private roots are backed up. Future Samsung sessions need explicit Phone-install/source-instance identity; do not let a cloned scientific-memory queue silently reuse an identity while producing different events. Old Honor credentials and custody must not be silently copied or reattributed. Once the selected host is admitted and its connection verified, keeping Honor Bluetooth off is a reasonable single-host operating arrangement, not evidence that it caused previous failures. Any such change remains separately authorized.

## What is real now, and what is missing

### Watch→Phone recording and delivery

`HealthSensorService.kt` appends accepted sensor records under the bounded-run/lifecycle gate before broadcasting canonical bytes. `SourceJournal.kt` and ordinary GATT preserve session/index/stream/sequence/source time and exact manifest/ACK rules. Finalized recovery forbids new sensing. The physical Watch77 diagnostic shows the actual session, GATT connection/resume and app-recorded **EXACT_ACK_COMPLETED**. Its tail does not contain the complete endpoint/full-hash ACK history, and the durable completion marker was not independently read back. Watch69 remains **NOT QUALIFIED**.

The Honor production native module retains accepted canonical bytes plus SQLite record, manifest/range, gap/resume and lifecycle metadata. Phone custody is distinct from the Watch’s app completion assertion. A grey companion listing does not prove collection stopped; a Connected label does not prove delivery.

### Exactly what Phone60 contributes

The sole admitted replacement APK is `HUGR_Phone60p_2.0.60_saved-source-evidence_REPLACEMENT.apk`, SHA-256 `10c135f734a7e07ca144b9ffef630a19f1cebf395b6c7581d78cbd96b7d1c98f`, package `health.hugr.app`, code60/version `2.0.60-saved-source-evidence-candidate`, same Phone59 signer. The rejected first artifact must never be installed.

Phone60 adds an isolated, offline native reader of **one existing source session**. It recomputes selected canonical rows and full manifests against copied bytes, checks gaps/coverage and original before/after fingerprints, and shows full range/count/bytes/digest plus per-stream canonical-row counts/source-time bounds. Its UI corrects misleading segment-versus-whole-recording status. This is useful for the present evidence decision without BLE.

**It is not the future durable general run report.** The reader’s report is displayed in memory; disposable snapshots are removed. It is hard-bound to the existing session and has short-run budgets. It neither persists a reusable run report nor provides synchronized duration, acquisition/finalization boundaries, full ACK history, Watch completion proof, live physiology, portal upload or observer access. Per-stream rows are not beat/sample counts or physiological quality. It explicitly reports missing proof as unknown. Source algorithms, BLE, sync and Watch77 were not changed.

### Existing Phone→Watchtower layer

`scientificMemorySync.ts` adapts `shadowLearningRuntime.ts` events, stores an AsyncStorage queue, uploads at most100 events per attempt, and persists exact event-ID/hash acknowledgements using a scoped ingestion credential. `dataSync.ts` starts a five-minute JS sync timer and attempts immediate/final sync; disconnect stops that timer. These mechanisms are coded, not demonstrated reliable with screens asleep on the actual devices.

Crucially, `bluetooth.ts` emits durable `watch_source_record`/manifest events, but the current `WatchConnect.tsx` handlers primarily update UI state. They do **not** turn the canonical recording/manifest/finalization chain into the scientific-memory run stream. Live low-rate health/context/shadow events are a different layer. Replay returns before feeding the current live physiology path, correctly preventing old data from appearing live. Keep that distinction.

`scientificMemoryCore.ts` already has deterministic envelopes/hashes/lineage and explicit missingness. However its adapted events use Phone source identity/time, date-based fallback session grouping, unknown Phone monotonic clock/clock uncertainty and empty raw-artifact references. This is not yet a synchronized, reverse-traceable Watch run bridge.

### Portal and Manus

The preserved portal at source head `d72a6ff85b09df0a72e2c741383d1b2247247e46` contains scoped ingest, immutable event storage, protected queries, as-of replay/export and deterministic monitor rules. `/n1` refreshes accepted events every30 seconds. The monitor runs on ingestion or an explicit dashboard/query request; it is not currently an unattended silence alarm when uploads and viewers stop. The current dashboard captures its time-window end at mount/range change, so repeated refresh can exclude later live events. Fix this concrete live-view gap before claiming a live dashboard.

There is no deployed/tested Manus observer in this chain. Start with **on-demand, read-only access to a specifically authorized run and as-of boundary**, via the protected evidence interface. Existing device credentials authorize ingestion, not analyst reads. A narrowly scoped authenticated read surface/access route is needed; do not repurpose an ingest token or expose the private ledger publicly. Periodic/event-triggered analysis is an optional later explicit deployment, not part of this preparation. Deterministic recording/reporting must remain useful without Manus.

## Milestone 1 — dependable recording without routine Watch filming

Implement the smallest **app-owned, persistent Phone run report**, reusing the native verifier and custody tables. Put append-only report/evidence records in an isolated app-owned store, never rewrite original canonical bytes or invent missing history. Record actual Watch source session and Phone host/connection lineage, separately from experiment and report IDs.

The report must show available streams and eligible sample coverage where decoded/known; source and receipt timing with uncertainty; gaps; acquisition/stop/finalization evidence; manifest count/bytes/full hashes and custody recomputation; attempted ACK versus independently observed Watch acceptance; completion/failure reason; last observation age; builds. Unknown duration, absent streams, unknown tail and absent ACK history must remain explicit. A closed GATT link must not be labelled a sensor failure. Current sensor liveness must not be derived from replay. Finalization must not be inferred from UI indexes.

For **future** runs, persist ACK-intent/write outcomes and explicit terminal evidence at existing boundaries. If the existing Watch completion output cannot supply the required session/endpoint/hash linkage, propose only that demonstrated missing bridge; Watch77 is unchanged by default. Do not start a broad telemetry or transport redesign.

First use Phone60 to determine the old run’s actual evidence. A successful canonical snapshot must be reconciled with the supplied Watch report and original recording evidence. If missing historical ACK/completion artifacts prevent independent qualification, state the exact remaining gap and make a bounded evidence decision; **do not fabricate them, repeat already-completed recovery or hold all future rehearsal indefinitely while trying to recreate lost history**. A new rehearsal requires separate approval and does not retroactively qualify Watch69.

## Milestone 2 — live inspection and iteration during Book One N1

Reuse the same custody/run events as a bounded **Watch→Phone→scientific-memory→Watchtower** bridge. Emit low-rate run-health/control/manifest events plus suitable physiological Observation windows and explicit person-provided context. Preserve original Watch session/index/sequence/time, receipt time, quality/missingness and links to retained canonical ranges. Raw payload upload is not automatically needed or authorized; scope any raw artifact access separately. A downsampled portal view is not complete raw custody.

Decouple durable upload work from the connection/view lifecycle. Persist bounded pending batches and acknowledgements, resume safely after Phone restart/disconnect, and drain backlog independently of new sensor arrivals. Use the existing application’s runtime, not Manus to keep collection alive. Do not simply start the historical `HUGRForegroundService` as a sync helper: it also performs phone sensing/fusion and has haptic code. Reuse only the governed ordinary transport lifetime or a narrow non-sensing worker after reviewing Android requirements. Its presence is not proof that JS timers survive screen sleep.

Retain existing server hashes/idempotency/protected queries and monitor rules. Add run-specific acquisition/transport/custody/receipt states, actual freshness/latency and bounded pagination so a truncated5000-event view is not mistaken for completeness. Correct the moving time window; use stable as-of cutoffs for historical replay. Show the last server receipt and explicit empty/stale/unknown states. If unattended silence detection is needed, deploy a separately reviewed application-owned deterministic check; it must not depend on an LLM being awake.

**Latency:** existing normal batch cadence can add up to roughly five minutes before network/OS delay, plus up to30 seconds for dashboard refresh when its live window is fixed. Backlog over100 events takes additional attempts; outage/background suspension can make delay unbounded. Do not advertise this as seconds-level debugging. Proposed starting target: low-rate fault/health changes and bounded Observation windows queued promptly, normal flush within30 seconds and dashboard refresh within30 seconds; target about one minute under healthy admitted conditions. This is a proposed measured target, not demonstrated performance. Always display observed source→Phone→server→viewer delays and timing uncertainty; tune cadence using battery/load measurements.

## Shadow-engine iteration, without contaminating N2

The existing shadow core freezes predictions before reports, scores before learning updates and records comparator versions/cutoffs. It does not constitute a configurable engine tournament or authorization for foreground Suki/haptic changes. Reuse it with an append-only engine/configuration registry: code/artifact/config hashes, version, effective time and sequence, eligibility/quality rules, training/input cutoff and change reason. Preserve every issued output, uncertainty/abstention and disagreement; never overwrite original interpretations.

Tag computation explicitly **LIVE_ISSUED** versus **RETROSPECTIVE_REPLAY**, with source references and input availability cutoff. N1 can tune and compare authorized shadow candidates. Freeze selected finalists and the evaluation protocol before using untouched later N2 evidence; no N2-driven tuning without invalidating that holdout. Live prediction ahead of a report is not independently established awareness lead time.

Two concrete existing persistence gaps matter before relying on this layer: `initializeShadowLearning` resets/replaces invalid stored state, and its queued persistence catches write failures. Correct preservation/failure handling and test it so research history is retained and a failed durable write is not reported successful. These are named N1-layer corrections, not evidence that current canonical Watch/Phone bytes were lost. Existing envelopes also need explicit source/artifact links and clock unknowns retained; do not fill unknown clocks with assumed zero error.

## One rehearsal sequence with two linked acceptance points

**Before devices:** synthetic native and joined-wire tests prove report persistence/restart, no source mutation, accurate missingness/failure, exact ACK/completion separation, offline queue/drain/restart, idempotent ingest, authorization, live-window progression, pagination, engine/config immutability and live-versus-replay cutoffs. Test malformed/corrupt state and failed writes without real private data. Build/admit only necessary candidates under later explicit authority.

**Recording acceptance:** one separately approved short fresh run with ordinary screen sleep, controlled stop/finalization/delivery. Neil performs the normal start and planned stop, not routine scrolling/filming. The persistent Phone report must explain the entire outcome after screen sleep/reopen, including a failure, with retained evidence and no new samples after finalization. Longer-duration recording need not wait for the full AI/portal stack once this reporting/lifecycle acceptance passes.

**Live-observation acceptance before long Book One N1:** during a separately authorized short run, observe incoming authorized windows, a deliberately bounded benign connectivity/network interruption, queued recovery, fresh/stale states, a context entry and shadow outputs in Watchtower. No disconnect/pairing/fault injection is authorized by this plan. Record measured observation delays and time to identify the failed boundary from saved structured evidence. Demonstrate on-demand Manus read-only access to that exact run/cutoff and denial outside scope, then repeat the relevant monitoring with Manus absent. Camera use is exception evidence, not the routine data path. A chosen shadow configuration change must appear as a new effective-version event; replay results must be distinguishable from prior live outputs. Only then claim a shorter in-run debugging/learning cycle.

This is a narrow extension of the existing architecture, not a new telemetry programme. The full operator objective is **start → live inspect when needed → stop → durable report**, not two weeks of blind collection or eight hours of filming.

## Preparation completed and limits

Today’s focused rerun of existing synthetic components: Phone scientific-memory/shadow/adapter **18/18 pass**; portal integrity/authorization/monitor **14/14 pass**. These do not test the missing bridge, actual upload, SQLite custody on Honor, Samsung runtime, screen-sleep stability or observer access. Prior Phone60 admission retains108 Node tests106pass2existing skips, native29/29 and five-phase synthetic joined-wire pass. No new implementation, build, device action, private-data query/upload, observer or schedule was created here.

Authoritative source-owned plan → identical copies → Master with both milestones and present device frontier → affected private VIS-COUNT proposal only → Alignment last → bounded commits/non-force mirror/exact remote readback. Accepted S4 remains immutable. Known-denied Atlas access is not retried; a sanitized proposal is prepared for its Inbox and remains **NOT SUBMITTED**, rather than claiming the live Atlas updated. GitHub publication readback is retained outside commits to avoid self-reference.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## References

[1]: https://www.samsung.com/africa_en/support/mobile-devices/how-to-reconnect-your-samsung-galaxy-watch-or-buds-series-to-a-new-phone/ "Samsung: reconnecting Galaxy Watch to a new phone, including data-reset caveat"
[2]: https://www.att.com/device-support/article/140374/samsung/galaxy-watch8-classic/ "AT&T: Galaxy Watch8 Classic transfer-to-new-phone system route"

Source inspection anchors: Watch `HealthSensorService.kt`, `SourceJournal.kt`, `BleGattService.kt`, physical Watch77 receipt; Phone `bluetooth.ts`, `WatchConnect.tsx`, `dataSync.ts`, `scientificMemoryCore.ts`, `scientificMemorySync.ts`, `shadowLearningRuntime.ts`, passive Activity/core and Phone60 final admission; portal `scientificMemory.ts`, `scientificMemoryDb.ts`, `scientificMonitor.ts`, `N1Monitor.tsx`, `visCount.ts`. These source facts are not live deployment validation.
