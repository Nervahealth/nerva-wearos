# Watch80 combined fresh-flow and launcher correction — build/admission

## Current state

**FINAL — the sole Watch80 APK is built and artifact-admitted; uploaded APK and checksum sidecar fully byte-read back MATCH.** Phone61 source/runtime/APK unchanged. No installation, device launch, connection, recording, private transfer or N1 is authorized by this build instruction. The unknown current Watch79 receipt remains a conditional physical admission dependency, not silently bypassed.

## Authority and scope

Neil's 2026-10-12 successor instruction, recovered from this task's user-authored history:

> Authorize focused launcher/task-isolation source/test work, combined with the completed Watch79 fresh-flow corrections.
>
> Reproduce blue Main → Diagnostics → Home → blue Main, and the reverse information-launcher route. Make each launcher reliably open its intended surface without automatically starting sensing or BLE, clearing tasks broadly, deleting diagnostics, or altering existing journals/receipts.
>
> Keep Phone61 unchanged. Integrate the already-tested startup, Health handoff, timer and original-exception capture fixes into the same successor.
>
> Resolve the current-run dependency explicitly: establish a reliable passive receipt-read route, then use its result to select the preservation-respecting next action. Do not clear an unresolved run or assume an update permits another fresh Start.
>
> After the focused and joined tests pass, build and admit one combined corrected Watch candidate, with the complete short-recording procedure ready alongside it. No installation or device action under this instruction.
>
> Keep the target fixed: one start → actual sensor recording → controlled finalization → verified delivery → durable Phone result → offline recheck. Avoid another diagnostic-only build or routine filming.

## Integrated correction and proof limits

The exact production Kotlin is the previously tested combined source: trusted Android app-directory anchor; serialized worker receipt/admission I/O; repeat-Start/permission callback containment; visible latched failure; unchanged receipt redraw suppression; foreground-return stability; same-run Health timer handoff before Samsung connection; original foreground/startup/capability/tracker/asynchronous connection exception captured before quiescence/cleanup, attributed to actual run/source; late callbacks cannot reopen the finalized recording.

The manifest gives Main `com.hugr.wearos.main` and Saved Diagnostics `com.hugr.wearos.diagnostics`, each `documentLaunchMode="always"` with standard launch mode. Android's documented policy requests NEW_DOCUMENT plus MULTIPLE_TASK rather than reusing a pre-update shared task. No CLEAR_TASK/CLEAR_TOP, singleTask, finishAffinity, clearing/deleting old tasks, report deletion or journal migration. Both Activities remain in the same app process and share the existing serialized run guard.

Before this policy, the five-case focused regression had four failures for missing always-document policy. After it: all five pass, including synthetic pre-update task preservation, both launcher directions/Home and actual Activity/store lifecycle calls. A no-op test configurationChange was corrected to a real orientation change; production lifecycle was not altered to make that test pass. Full Watch: **235 total, 231 pass, 4 fixture-dependent skips, 0 failures/errors**.

Fresh joined acceptance: **all six phases PASS** — actual Watch writer fixture → Phone wire ingestion/exact ACK generation → actual Watch acceptance of Phone ACKs → byte-identical native production-method extraction → native writer/verifier/receipt/report/restart/offline recheck → all Phone deterministic tests. **94 native tests, 0 failures/errors/skips**. Full totals and source hashes are in the build workspace validation and joined summary. This is synthetic Android/SQLite and Linux test-filesystem coverage, not Samsung hardware, Android ActivityTaskManager/Wear launcher, real radio, sensor performance or real private custody proof.

Prior distinction is immutable: the captured historical Watch78 **second-Start** exception is source-matched to BoundedNormalStartupGate.deferFreshScope. The historical **first receipt-unavailable error is not physically diagnosed**. Neither claim is replaced by these tests. Watch79's actual recording/finalization/current receipt remains unresolved; the supplied later diagnostics were historical Watch78 reports.

## Complete acceptance evidence trace with unchanged Phone61

| Acceptance field | Production evidence path |
|---|---|
| Source/run identity | FreshRunRuntime.admit creates isolated per-run v3 journal; SourceJournal actual UUID and separate random run UUID enter durable Watch receipt; Phone selects only that receipt source |
| Timing and scope | Watch receipt wall/elapsed/boot/start/duration, supported/started masks; actual canonical records and Phone recomputed stream counts; unavailable canonical PPG remains explicitly live-only |
| Actual records | Health callbacks → guarded FreshRunSession.append → canonical journal → current native Phone writer; STARTED alone proves admission, not records |
| Controlled finalization | Health same-run timer, quiescence/drain/force-sync/finalize → exact final manifest inventory and receipt clocks; no samples after boundary |
| Canonical custody | Native Phone exact manifest range/count/bytes/full SHA recomputation and durable report publication before ACK eligibility |
| Watch ACK acceptance | FreshRunSession.acceptAck validates actual session/queued endpoint/full hash against journal; Watch receipt records exact accepted tuples, not Phone write success |
| Final completion | Watch receipt COMPLETED only after controlled finalization and all exact ACKs; Phone fetches paged receipt, validates digest/inventory and recomputes custody, stores receipt proof before terminal result |
| Durable local result | PhoneCustodyRunReportStore integrity-checked atomic per-run publication; result COMPLETE_VERIFIED/PARTIAL/FAILED with exact evidence reasons |
| Offline restart | Honor Force stop without clear; separate Saved Source Evidence reads integrity-selected report, rechecks same canonical bytes and Watch receipt proof, never reconnects or begins sensing |
| Failure | Current Watch saved original-exception report / failed receipt or native Phone failure report; absent/unmatched old diagnostics remain unknown |

No demonstrated new Phone dependency was found. Exact Phone61 compiled source-stage and APK identity remain the admitted baseline; no Phone rebuild is included.

## Current-run decision before any fresh Start

**Reliable passive route in source:** corrected Main reads only FreshRunRuntime.current on its serialized background worker. It opens only the small selected v3 receipt; no source journal/retained v2/legacy/frozen traversal, service, permission path, sensing, BLE or receipt publication follows from Main launch. Distinct always-document task policy is verified in the manifest and synthetic tests; physical routing remains to be observed.

- Current receipt absent, COMPLETED or FAILED: source permits a separately authorized fresh admission into a new run directory, preserving previous journal/receipt bytes. Record its visible phase first; do not infer an absent run from a generic ready heading.
- STARTED or FINALIZED: **STOP before Start/Phone Connect.** Existing admission intentionally rejects unresolved state. Do not clear, overwrite, fail, acknowledge or use receipt-only automatically. Return the phase/source/run for one concrete preservation-respecting decision.
- Read error/old wrong viewer/no result in 60 seconds: STOP. No inferred readiness or retry.

The current-run state is a real conditional physical dependency, not a reason to rebuild Phone or reconstruct Watch69 indefinitely. This build does not bypass it. `Serve current fresh receipt only` is not passive: on process-interrupted STARTED/FINALIZED it marks the receipt FAILED while preserving bytes. It is therefore explicitly excluded from the passive read and requires its own reviewed authority if needed.

## Candidate and preservation

Intended sole candidate: package `com.hugr.wearos`, code **80**, version **0.80.0-combined-fresh-flow-launcher-candidate**, filename `HUGR_Watch80w_0.80.0_combined-fresh-flow-launcher-candidate.apk`. Existing signer only; protected SourceJournal SHA-256 `24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361`; retained compatibility DEX SHA-256 `a2477754c9e4b10a9d91584ca2281b4190a9b0776411fec19970dd47f96ab784`, runtime egress unvalidated and excluded.

Source-owning receipt → synchronized copies → Master → private VIS-COUNT only if affected/authorized (no accepted S4 change, no denied-access retry) → Alignment last → diff/consistency → bounded commits → controlled both-repository write preflight/non-force GitHub mirror/exact head and tree readback. Local files/commits alone are not remote preservation.

Prepared procedure: `HUGR_NSTAIR_WATCH80_PHONE61_RECEIPT_FIRST_PHYSICAL_GATE_2026_10_12.md`.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

After this named fresh short run is physically accepted, advance to separately scoped **duration ladder → eight hours → existing live-observation/Watchtower bridge**. Full observer stack is not a prerequisite for short acceptance; no new telemetry infrastructure is added here.

## Final artifact admission — 2026-10-13

One source APK assembly and one final compatibility staging/signing operation; no second candidate or Phone build. Exact source used for the build: Watch local commit `136bc470d052066c229d839f96b3ec8bd03ae9ca`, remotely preserved before build with shared tree `00d0ffdd756926de35aa032b65173f018fd6e546`.

- Sole APK: `HUGR_Watch80w_0.80.0_combined-fresh-flow-launcher-candidate.apk`
- Local admitted artifact: `/home/ubuntu/HUGR_WATCH80_COMBINED_CORRECTION_2026_10_12/artifacts/HUGR_Watch80w_0.80.0_combined-fresh-flow-launcher-candidate.apk`
- Package: `com.hugr.wearos`; code `80`; version `0.80.0-combined-fresh-flow-launcher-candidate`.
- Size: **12,926,725 bytes**.
- Full APK SHA-256: **`cdc5647769ad1de9eb909259b3d1edebc2bfe428bfb80141a9b593de687445e2`**.
- Signer SHA-256: `fc91d26565d61b0a1c67db4dd0d358c8377c65b2fcb1ede00283b6c7b90c1706`, independently identical to admitted Watch79 predecessor.
- Sole install URL: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/uRsKWFoPZblCUccb.apk
- Checksum sidecar URL: https://files.manuscdn.com/user_upload_by_module/session_file/310519663237475822/JsugIffBrERmBliv.sha256
- Downloaded APK compared byte-for-byte with admitted APK, full SHA matched; downloaded checksum compared byte-for-byte with local sidecar. **PASS**.
- ZIP integrity/unique entry names, alignment, unique DEX classes (**8,165 definitions**), exact compiled fresh bridge/startup/timer/capture hooks and both exact Activity affinity/document policy attributes: **PASS**.
- Protected SourceJournal and compatibility DEX exact identities above: **PASS**. Compatibility egress runtime remains unvalidated/excluded.
- Completed Watch source suite: **235 total, 231 pass, 4 fixture-dependent skips, 0 failures/errors**.
- Completed unchanged Phone deterministic suite: **142 total, 140 pass, 2 fixture-dependent skips, 0 failures**.
- Completed joined suite: **all six phases PASS**, native **94/94 pass**; real code/SQLite test adapter and actual cross-language ACK fixtures, still synthetic only.

No physical success is claimed. Installation/current receipt routing/real SDK sensor callbacks/service timer/radio/custody/report restart proof remain physical acceptance requirements. Prepared gate is receipt-first and conditionally stops on STARTED/FINALIZED/error; no automatic receipt-only failure conversion or new-run bypass. Historical Watch78 first error remains physically undiagnosed.

## 2026-10-13 00:37 BST — installation verification and passive current receipt authority

Neil: “Verify Watch80 installation and read the current receipt status without starting a new run.” This authorizes only Settings version verification, followed conditionally on exact Watch80 version by one passive blue Main HUGR receipt read, first stable result/error or 60 seconds, no retry. No installation/update is inferred from a request to verify installation; if Settings still shows 79 or another version, stop and report rather than install or launch. No device observation/result has yet been supplied for Watch80.

Phone61 and Bluetooth settings remain untouched. All Watch buttons/switches, Start, receipt-only, retained delivery, service start, sensing, new recording, Connect/reconnect, pairing/reset, data clear/uninstall, legacy/frozen actions, Share/export/private upload and N1 remain excluded. Saved Diagnostics launch/Next/Share is not included in this narrower authority. A passive Main launch may write ordinary startup breadcrumbs but must not change the selected run receipt or canonical source bytes.

Operator readback requested: Settings version, correct Main versus diagnostics surface, exact Fresh phase/failure or ready/STOP text, source/run if readily visible, manifest/accepted-ACK counts/revision if visible, whether any control was pressed or unexpected behavior occurred. No routine video or full private report upload is requested. STARTED/FINALIZED, FAILED/COMPLETED or absent each ends this observation; none authorizes a new Start. Physical installation identity and receipt state remain **PENDING OPERATOR OBSERVATION**, not confirmed by source/APK checks.

### Operator update — 2026-10-13 00:38 BST

Neil reports: “Normal updatw settingas applisy says 0.80.0 combined fresh.” Record normal in-place update and Settings-observed `0.80.0 combined fresh` as **operator-confirmed**, consistent with the admitted version prefix; do not claim an independent installed-APK checksum/signature read. The complete version suffix, any post-update launch/control use, and absence of unexpected warnings are not independently observed or explicitly confirmed here. No duplicate installation is needed. Next authorized step is the one passive blue Main receipt read, within 60 seconds, no controls/Phone/Bluetooth/Saved Diagnostics. Current receipt status remains pending; no new recording authorized.

## 2026-10-13 00:55 BST — passive Main photo: selected receipt STARTED

Neil: “This is what showd after pressing main hugr.” Supplied photo `IMG_20261013_005417.webp` SHA-256 `9e703df7cf825b3f43ba4d652318bfc4d3cd90a9a447518a20d754b736cf4035` visibly shows the Main three controls (START FRESH 5 MINUTES / SERVE CURRENT FRESH RECEIPT ONLY / EXPLICIT RETAINED V2 DELIVERY), not Saved Diagnostics. Heading is clipped at its left/right edges but **STARTED · no recorded fai…** is legible and agrees with the Main receipt renderer's `Fresh STARTED · no recorded failure` format. Record **selected receipt phase STARTED observed**, correct Main surface observed for this tap. This supports the intended launcher route for this one physical observation; reverse launcher direction/pre-update task mechanism are not independently proved.

Source/run UUID, revision, stream masks, manifest/accepted-ACK counts, receipt clocks and full failure field are outside this photo. Do not invent them or attribute this receipt exactly to Watch79/Watch69. The installed80 was operator-confirmed separately. STARTED is durable admission state, **not evidence of sensing now, actual recorded physiological bytes, ongoing service, clean finalization, ACK or acceptance**. A passive Main launch reads that state; it does not itself create a new run. A carried earlier run is consistent with prior history, but exact attribution/control history remains unconfirmed. Whether every control stayed untouched is to be clarified from recollection; no new device check requested.

The authorized passive gate ends here. **No new Start, Phone Connect, receipt-only, retained delivery, relaunch, diagnostics, Bluetooth or clearing action.** Existing admission deliberately rejects STARTED/FINALIZED. Button appearance alone does not prove admission permission or that a button was pressed.

Existing next-route dependency, source inspection only: `FreshRunRuntime.openReceiptOnly` requires no active in-memory session and no current receipt-only mode; for selected STARTED/FINALIZED it increments revision and durably sets **FAILED / PROCESS_INTERRUPTED** while retaining canonical journal and earlier receipt versions. The GATT receipt-only branch promotes/initializes Bluetooth and serves receipt, but does not start Health or attach sourceJournal for canonical delivery. Thus it is **not a passive read**, not controlled finalization/qualification, and not authorized now. A later explicit interrupted-run closeout can be reviewed once no fresh Start since update is confirmed; no new APK or historical Watch69 reconstruction is required by this STARTED observation alone. Do not automatically convert the receipt or assume that this route proved missing source bytes.

No production change, build, device action by the assistant, physiological/media publication or qualification change. Source-owning receipt→Master→no affected private S4/Atlas operation→Alignment last→documentation commits/non-force GitHub/independent exact-tree readback. Photo itself excluded from repositories. Full fresh short acceptance then duration ladder/eight hours/live Watchtower remains the objective.

Watch69 **NOT QUALIFIED**; Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**; Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## 2026-10-13 01:03 BST — no Watch80 Start confirmed; interrupted-receipt closeout proposed only

Neil explicitly confirms that since installing Watch80 he has only opened Main, with no Start press and no other Watch control pressed. Therefore the observed STARTED receipt was carried into this update, not admitted by a new operator Start on80. Exact source/run identity and historical producer version remain unobserved. This resolves the control-history question; no retry or correction is performed.

**Precision correction to the 00:55 addendum:** `FreshRunReceiptStore.publish` atomically replaces the selected `receipt` file; it does **not** archive earlier receipt versions. The earlier phrase “retaining … earlier receipt versions” was incorrect. The proposed operation preserves source/run IDs, timing, masks, manifest inventory, accepted-ACK tuples and source journal bytes; it increments the revision and replaces the current receipt phase/failure with FAILED/PROCESS_INTERRUPTED. Prior STARTED observation remains in this source-owned record, not an invented on-device receipt-version archive. No historical acquisition/finalization/ACK proof is created.

Proposed exact one-shot closeout, **awaiting explicit user authority**: if Main is no longer displayed, open blue Main once; only while selected heading is STARTED with no recorded failure and no unexpected state, press **Serve current fresh receipt only** once. No Start/retained controls. Runtime rejects an active in-memory session; no force-stop workaround/retry. Existing route changes the small selected v3 receipt as above, starts foreground GATT receipt-only advertising, no Health start/no sensing/no source journal read/delivery. Observe first FAILED/PROCESS_INTERRUPTED or failure, or 60 seconds without it; no repeated presses/relaunch/Phone/Connect. Then leave it untouched for its existing two-minute receipt-only service lifetime (allow 30 seconds for observed scheduling, no claim of physical service stop without evidence); do not authorize a new recording in this closeout. Unexpected prompt/error/state stops further interaction, no report upload/share/diagnostic read. No Bluetooth-setting/pairing/reset/clear/legacy/frozen/v2/N1 operation. Phone61 stays unopened and unchanged.

Next fresh five-minute acceptance remains separate and depends on this closeout result and service inactivity. No new build, broad audit or historical Watch69 reconstruction is warranted by the confirmed carried STARTED receipt alone. Durable Phone report/offline restart proof and later duration ladder/live Watchtower remain visible milestones. All historical qualification literals unchanged.

## 2026-10-13 02:17 BST — one-shot interrupted-receipt closeout AUTHORIZED, result pending

Neil: “Authorize the one-shot interrupted-receipt closeout only.” This explicitly accepts the exact reviewed 01:06 proposal: one Main open only if no longer displayed; only matching STARTED/no recorded failure permits one **Serve current fresh receipt only** press. Expected new selected receipt **FAILED / PROCESS_INTERRUPTED**, revision+1 with original IDs/timing/masks/manifest/ACK fields and journal bytes preserved. Current receipt file is atomically replaced, not historical-version archived. Temporary foreground receipt-only GATT advertising is included; no Health start, sensing, canonical source delivery or new recording. Runtime active-session rejection remains fail-closed; no force-stop workaround.

First expected result/error/unexpected state or **60 seconds from the press**, no retry/relaunch. Regardless of early result, leave Watch untouched until **2 minutes 30 seconds from the press** for the existing two-minute receipt-only service deadline; elapsed waiting is not independent hardware shutdown proof. If matching STARTED state is absent, do not press and report. Any prompt, error, black screen, crash, flashing or unexpected state stops further controls. Report exact heading/reason and any warning, whether the single press occurred, and whether the untouched interval elapsed; no routine video or private report upload.

Phone61 remains unopened and unchanged. No Start, Connect/reconnect, retained-v2 delivery, Bluetooth changes, pairing/reset, clear/uninstall, Saved Diagnostics, Share/export/private transfer, legacy/frozen action, observer or N1 execution. No subsequent five-minute rehearsal is included. Source/control authorization record only; no physical action or new outcome has been performed/observed by the assistant. Existing STARTED receipt remains the last physical evidence until Neil reports the closeout outcome. No build or production edit required.

Standing closeout automatic: source-owned receipt → synchronized copies → Master → no affected authorized private Atlas/S4 change → Alignment last → documentation-only coherent commits → controlled non-force GitHub mirror and independent exact-tree readback. Historical qualification literals unchanged; fresh recording acceptance/durable Phone result/offline recheck → duration ladder/eight hours/live Watchtower milestones retained.
