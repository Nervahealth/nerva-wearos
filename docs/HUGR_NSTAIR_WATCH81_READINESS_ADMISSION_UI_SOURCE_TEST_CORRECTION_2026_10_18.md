# Watch81 readiness, admission attribution and readable UI — source/test correction

**Latest disposition: SOURCE/TEST COMPLETE — PASS_SYNTHETIC_ONLY. No APK build or device action.**

## Authority and retained physical evidence

Neil authorized this focused source/test investigation and correction at **22:36 Europe/Oslo, 18 October 2026**. No APK build, candidate version/signing change, installation, app launch, connection, new recording, on-device receipt mutation or existing-run closeout is included. The earlier WIP source/receipt checkpoint is retained in Git history; this completed result supersedes its pending disposition, not its historical evidence.

Watch81 and Phone62 were operator-reported installed with Settings checked and neither app opened. The subsequently authorized passive Watch Main read showed a clipped **STARTED** heading at 60 seconds. Neil clarified that the same heading was already visible **before** his earlier approximately 18:03 Start press. He reasonably interpreted it as session startup/readiness. The screen did not make the distinction clear and hid essential details below oversized fixed controls. This is a product/readout ambiguity, not an operator-blame diagnosis.

The actual source/run/revision behind the latest physical heading remains unobserved. The earlier Phone connected, discovered services and subscribed before reporting FAILED; neither an unchanged heading nor a Start press establishes a newly admitted Watch recording, actual sensors, its timer, finalization, canonical custody or exact Watch ACK/completion. That physical attempt remains **STOP / NOT QUALIFIED**. The receipt-only proposal remains held; no existing device receipt was automatically failed, rewritten or qualified.

## Demonstrated source problem

The existing storage/runtime admission guard rejects active or receipt-only runtime and persisted STARTED/FINALIZED receipts before generating a new run. That guard remains unchanged. Main's previous button eligibility did not apply the persisted-phase guard, and its generic STARTED heading could describe an existing receipt without proving new admission. Three oversized fixed buttons plus a fixed switch also left the separately weighted evidence scroller with inadequate space.

Nine independent actual-Activity test cases failed by assertion against exact original Watch head `9a77c417f30ddbd068011a07c20ad55cb7996a5c` and passed against the correction. This is source-matched UI/admission evidence; it does not identify the initiating physical cause or invent a historical run identity.

## Implemented bounded correction

Production changes are limited to `MainActivity.kt` and the new pure `FreshRunReadinessPresentation.kt`. The correction:

1. Offers **READY TO START** only after a successful selected-receipt read, for absent/FAILED/COMPLETED receipt with no active or receipt-only runtime. Saved STARTED/FINALIZED instead show **PREVIOUS RUN UNRESOLVED** and lock fresh Start. The existing runtime admission guard is not weakened.
2. Rechecks the actual selected receipt asynchronously at the Start boundary. A STARTED receipt appearing after a prior READY view cannot proceed into new admission or be displayed as the new run.
3. Distinguishes pending admission, inherited process-local state and a newly admitted run. **NEW RUN ADMITTED** requires both actual source ID and run ID returned by production admission to match the subsequently observed receipt. A button press, an old receipt, a same-run/foreign-source receipt or Activity recreation cannot supply that confirmation. A later FAILED phase is prominently **STOP**, while finalization/app ACK completion explicitly keep Phone verification separate.
4. Places the heading, compact state/identity, exact source/run/revision detail and existing controls in one whole-content scroll. Critical decision evidence precedes the controls; no zero-height weighted evidence area remains. Actual round-display readability still requires physical observation.
5. Preserves serialized background receipt reads, actual volatile runtime action guards, defensive sample quiescence on read failure, repeat-action/terminal containment, equal-state non-flashing refresh and pause/resume behavior. No journal, clock, sensor/SDK, lifecycle, source wire, manifest, exact ACK, signing, version or egress policy was changed. Phone production is unchanged.

Receipt-only and retained delivery remain separately explicit existing actions; availability is not device authorization, and neither is invoked automatically.

Test changes are limited to new presentation/actual-Activity regressions, the existing refresh test's independent-control expectations, and two existing startup/runtime contract tests. The latter replaced obsolete English-label and direct-handler text assertions with equivalent stronger passive-no-service/no-permission and delegated-active-runtime-gate checks. No failing preservation/ACK guard was removed.

## Validation results

| Validation | Result |
|---|---|
| Exact original-baseline actual-Activity regression | 9 assertion failures / 9 cases — expected RED |
| Corrected focused model, Activity, refresh, Android admission and service-flow tests | 33 passed; 0 failed; 0 skipped |
| Complete Watch unit suite | 252 total: 248 passed, 0 failed/errors, 4 explicitly skipped |
| Complete six-phase joined acceptance runner | All six phases passed |
| Production native Phone writer/verifier/report/restart/offline tests | 96 passed; 0 failed/skipped |
| Complete Phone deterministic suite with actual Watch-wire fixture | 150 total: 148 passed, 0 failed, 2 explicitly skipped |
| Existing production/protected blob identity checks | 118 unchanged, excluding authorized Main replacement/new pure model |

Focused counts are subsets of the full Watch suite, not additional independent test totals. The four Watch skips are the older optional `WatchPhoneWireIntegrationTest` producer/verifier and historical producer/verifier cases; the current fresh actual-wire joined chain was explicitly exercised. The two Phone skips remain explicitly reported, not silently counted as passes.

The six joined phases use the actual Watch writer and serialized notification fixture → Phone exact verification/ACK → real Watch acceptance of Phone-produced ACK → production native custody/report/restart/offline reader → full Phone deterministic suite. This is simulated execution of production paths, not hardware radio/sensor, Android power-loss durability or physiological/clinical validation.

The first baseline setup lacked an ignored compile-stub jar in the disposable checkout. Exact already-preserved vendor/compatibility/test compile binaries were copied and checksum matched; no missing source was reconstructed. The first full Watch run passed the 33 focused cases but flagged two obsolete text assertions, which were updated as described and retained as a successive validation snapshot. The final complete run is GREEN.

Detailed machine-readable evidence is preserved beside this receipt under `docs/evidence/watch81-readiness-admission-ui-2026-10-18/`: authority/baselines, assertion-based RED result, final validation summary and unchanged production identities. Synthetic test storage is disposable; no private device receipt, physiological payload, uploaded photograph/video or Word document was used or changed.

## Exact remaining boundary and next proposal

The corrected source is **not inside the installed Watch81 APK**. No new candidate was built. Phone62 and both installed artifacts remain unchanged.

The next proposed engineering action is one signer-continuous **Watch-only candidate build and artifact admission**, leaving Phone62 unchanged. Installation and a passive current-receipt read would remain separately authorized. That read must expose the existing source/run/revision/phase before any decision about its unresolved state. The correction does not permit another Start through a saved STARTED/FINALIZED receipt, silently close it, claim finalization, or qualify the earlier attempt.

The end-to-end objective remains one fresh five-minute recording → controlled finalization → exact verified delivery → attributable Watch ACK/completion → durable Phone report → restart/offline custody recheck. Only after that passes physically should the duration ladder and existing live-observation bridge advance.

## Standing preservation and historical boundaries

Source-owning receipt → Master Task List → affected private proposal only if authorized (accepted S4 unchanged) → Alignment last → bounded commits on the existing preservation branches → controlled both-repository write preflight and non-force GitHub mirror → independent exact remote head/tree/changed-byte readback. Earlier WIP/history is retained. No main merge, force push, new repository, credentials/private media/physiological data in publication.

Watch69: **NOT QUALIFIED**. Watch68 v1: **UNCLASSIFIED / NOT VERIFIED**. Watch58: **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

No N1, engine/clarification implementation, Phone migration, recruitment, new collection, frozen/legacy operation or broader diagnostic platform is authorized by this result.

## Unchanged validation limitation

This Watch-only correction does not resolve the previously documented Phone repository-wide TypeScript errors in archived fixture/snapshot paths. Whole-repository TypeScript was not rerun here; Phone production source is unchanged. The passed Phone deterministic/native/joined tests above must not be represented as a clean whole-repository TypeScript result.
