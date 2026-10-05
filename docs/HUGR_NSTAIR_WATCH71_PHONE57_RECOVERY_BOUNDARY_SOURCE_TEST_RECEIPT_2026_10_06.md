# Watch71 ↔ Phone57 recovery-boundary source/test receipt — 2026-10-06

## Scope and status

> **SOURCE / TEST CORRECTION GREEN — NO BUILD, INSTALLATION, LAUNCH, RETRY, RECONNECT, PAIRING CHANGE, SENSING, NEW RECORDING, ACKNOWLEDGEMENT, VERIFIER, EGRESS, OR LEGACY/FROZEN-ROOT OPERATION OCCURRED IN THIS WORK.**

This record implements the narrowly authorised investigation of the Watch71 retained-finalized-delivery recovery boundary and the Phone57 ordinary Bluetooth/black-screen boundary.

The existing Watch69 five-minute ordinary run remains **NOT QUALIFIED**. The final expected Phone canonical verification, exact terminal-manifest acknowledgement, and Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED` marker have not been observed.

Watch58 remains exactly:

> `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`

No Watch58, legacy, unclassified Watch68, egress, or exact-readback path was opened, changed, enumerated, acknowledged, copied, exported, or otherwise operated by this work.

## Observed physical boundary being investigated

The relevant earlier observation is intentionally narrow:

1. The operator reported the ordinary in-place installation of the admitted Watch71 artifact, with neither HUGR app opened at installation.
2. In the subsequent bounded recovery attempt, the Watch rendered its ordinary preservation message that retained legacy journal and frozen-package material remain outside normal startup. This presentation is **not** proof that a retained Watch69 v2 terminal manifest was selected.
3. Phone57 was reported unable to find/connect to the Watch despite the operating-system Bluetooth pairing surface showing connected. The operator later reported a black Phone HUGR screen after restarting the Phone app.
4. No attributable Watch71 causal GATT readiness evidence, Phone scan/connection/discovery result, final manifest equality result, exact final acknowledgement, or Watch delivery-completion marker was captured.

Therefore this observation cannot establish where Phone57 stopped, whether Watch71 advertised HUGR GATT, whether retained v2 finalised data was selected, or why Phone57 rendered black.

## Source findings

### 1. Watch71 retained-target selection and no-target fallback

`MainActivity.probeRetainedFinalizedDeliveryInBackground()` derives its target only from the isolated fresh v2 `SourceJournal` through `RetainedFinalizedDeliveryRecovery.select(...)`. It does not derive it from the mutable normal-startup marker and does not recreate the historic bounded-run UUID.

That source path supports this limited statement:

- a process restart or marker overwrite **does not itself prevent** selection when an unacknowledged finalized manifest remains in the v2 journal;
- a `null` selector result does **not** prove on-device deletion, acknowledgement, corruption, or that the earlier Watch69 run was never stored.

The investigation established a correction: prior no-target handling fell through to `beginFreshOrdinaryScopeAfterRetainedDeliveryProbe()`. In a recovery-only attempt this could reach ordinary permission/sensor startup. It was contrary to the stated no-new-sensing recovery boundary.

**Correction:** a no-target outcome now records the new mutable diagnostic stage `RETAINED_DELIVERY_TARGET_UNAVAILABLE`, renders `Finalized delivery recovery unavailable`, and stops. It does not request permissions, create a Health service, start sensing, append a source record, acknowledge/delete a segment, activate egress, or open a legacy/frozen root.

This marker is diagnostic metadata only and is deliberately not used for retained-delivery eligibility.

### 2. Watch standard GATT advertiser/server boundary

Before this correction, normal Watch GATT readiness was visible only through logs. The standard runtime did not emit causal events showing whether it had:

1. opened the GATT server;
2. received a successful service-added callback;
3. received an advertiser-start success callback; or
4. failed at any of those stages.

The correction adds bounded causal events with no sensor or source-record operation:

| Causal event | Meaning | Does **not** establish |
|---|---|---|
| `GATT_SERVER_OPENED` | Android returned a standard GATT server | a registered service, advertising, Phone discovery, or data delivery |
| `GATT_SERVICE_READY` | service-added callback succeeded | advertising, Phone discovery, or delivery |
| `GATT_SERVICE_FAILED` | service registration failed/rejected | a reason beyond its recorded status code |
| `GATT_ADVERTISING_READY` | advertiser start callback succeeded | Phone discovery, connection, service discovery, resume, equality, or acknowledgement |
| `GATT_ADVERTISING_FAILED` | standard GATT readiness/advertiser start failed | the exact vendor/OS cause unless Android supplies a code |

When the retained target is selected, Watch evidence rendering now exposes the standard readiness state without requiring the ordinary permission/sensor path. This preserves the already-selected source session; it does not synthesize, revive, or replace the historical bounded-run UUID.

### 3. Phone57 scan-to-connect boundary

Phone57’s ordinary flow is:

`startScan` → scan callback identifies HUGR/name candidate → `connectToDevice` → `device.connect` → `discoverAllServicesAndCharacteristics` → service enumeration → source notification subscription → durable resume request.

The prior reported failure is only consistent with a stop at or before discovery. It does not establish which branch occurred.

A source fault was found at the visible-control boundary: `handleConnect()` called asynchronous `startScan(20000)` without awaiting/catching a setup rejection from manager state or Android permission calls. Scan callback errors were already displayed by `startScan`, but a pre-callback rejection could escape the control handler.

**Correction:** `handleConnect()` now awaits and catches scan setup failure, renders `Bluetooth scan could not start: …`, and does not claim Watch connection/discovery/delivery.

### 4. Phone57 black-screen boundary

The reported black screen cannot be assigned a runtime cause without a captured native/JavaScript error. `CrashReporter` cannot catch a failure that happens before its children render.

A specific pre-render source failure surface was found: `RootLayout` returned `null` while waiting for fonts and neither handled a `useFonts` error nor reliably released the splash screen in that branch. A rejected/missing font result could therefore leave a black/splash-like surface without reaching `CrashReporter`.

**Correction:** the root layout now:

- contains `preventAutoHideAsync` and `hideAsync` failures;
- releases the splash path when fonts either load or report an error;
- presents a plain visible startup failure rather than `null` on a font-load error; and
- retains the existing deferred, internally caught motion-cache path.

This correction is a source-established escape from one plausible black-screen path. It does **not** claim that the operator’s Phone57 black screen was caused by fonts, Bluetooth, the retained session, or any specific native exception.

## Deterministic test evidence

| Validation | Result |
|---|---:|
| New Watch no-target fail-closed/GATT-readiness contract, RED before implementation | FAIL as expected |
| New Phone root/scan-start safety contracts, RED before implementation | FAIL as expected |
| Focused Watch: ordinary runtime, SourceJournal, and normal-marker suites | **PASS** |
| Full Watch JVM suite (`testDebugUnitTest --rerun-tasks --no-daemon`) | **144 passed; 0 failures; 0 errors; 0 skipped** |
| Focused Phone source ingestion, delivery-status, and startup safety tests | **16 passed; 0 failed** |
| Full Phone deterministic source-learning suite | **77 passed; 0 failed** |
| `git diff --check` for Watch and Phone corrections | **PASS** |

The broad Phone TypeScript command remains non-green solely because tracked historical `docs/evidence/harnesses/...` and `recovery-snapshots/...` files reference unavailable relative modules and contain existing implicit-`any` diagnostics. It reported **no diagnostics in** `app/_layout.tsx`, `components/WatchConnect.tsx`, or `tests/phone57StartupSafety.test.ts` after this correction.

## Candidate preparation and next physical boundary

No new candidate was built in this source/test-only scope. The next candidate pair, if separately authorised, must be built from these preserved corrections with the existing package/signer continuities and greater version codes:

- **Watch72 (proposed, not built):** retained-target no-sensing fail-closed result plus standard GATT causal readiness display.
- **Phone58 (proposed, not built):** startup fallback display plus visible scan-setup rejection.

The smallest later physical recovery observation would be deliberately split:

1. Install only the separately admitted Watch72/Phone58 artifacts; stop at normal update results.
2. Open ordinary Watch HUGR once. Do not start a recording, grant new permission, enter egress, or open a legacy/frozen tool.
3. If Watch shows `Finalized delivery recovery unavailable`, capture that first screen and stop. It identifies only that the current v2 selector found no target; it does not license a fresh run or prove deletion.
4. If Watch shows finalised recovery, capture the standard GATT readiness line. Stop if it shows `SERVICE FAILED` or `ADVERTISING FAILED`.
5. Only if Watch shows `ADVERTISING READY` should a separately authorised Phone connection/resume step be considered. Phone success still requires HUGR service discovery, manifest equality, exact acknowledgement, and Watch `BOUNDED_RUN_DELIVERY_ACKNOWLEDGED`.

No physical step is authorised by this receipt.

## Preservation state at source receipt time

- Watch source branch: `preservation/watch68-partial-source-2026-10-03`, pre-closeout head `fe8860feb3aceb3713d4a8f328449fff1089e95a`.
- Phone source branch: `preservation/phone-control-and-source-2026-10-03`, pre-closeout head `03ec05ed8fa3b106de563b068caa235f8a354791`.
- Control branch: `preservation/watch69-control-records-2026-10-03`, pre-closeout head `683b222848cd0bd6f868880e37fe079dff1ab141`.

The standing closeout sequence is pending: source-owned receipt → Master Task List → private Atlas Inbox proposal → Alignment Surface last → consistency review → coherent commits/pushes/remote readback. GitHub write confirmation will be recorded only after remote branch heads are actually resolved.
