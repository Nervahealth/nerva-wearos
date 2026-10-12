# Watch Main / Saved Diagnostics task isolation — source/test closure

## Result and authority

**SOURCE/TEST COMPLETE — correction not yet installed.** Neil authorized focused launcher/task-isolation source/tests and demonstrated correction only on 2026-10-12. No APK assembly, signing, version change, installation, device launch, recording retry, Phone Connect, private-data transfer or N1 action was performed.

Installed devices remain Watch79 / Phone61. The physical Watch79 recording attempt remains **NOT ACCEPTED / INCOMPLETE EVIDENCE**; its current receipt state is unknown. Phone61 source and its admitted APK remain unchanged.

The supplied video directly corroborates a blue striped Main-icon tap followed by Saved Diagnostics. This is not attributed to Neil selecting the wrong icon. The installed manifest has separate launcher activities sharing the default application task affinity. Android documents restoration of an existing task's top activity on launcher selection. This is a source-supported explanation consistent with the video, not measured device-task-stack proof.

## Demonstrated configuration defect and smallest correction

The new merged-manifest regression establishes that both launchers previously had `com.hugr.wearos` affinity. A deliberately limited manifest-driven NEW_TASK restoration model, using actual PackageManager metadata and real Activity controllers, reproduces both wrong-screen directions:

- Saved Diagnostics → Home → Main selection restores Saved Diagnostics.
- Main → Home → Saved Diagnostics selection restores Main.

These are **synthetic task-selection results**, not a captured Android ActivityTaskManager trace.

The complete production change is exactly two manifest attributes:

| Existing component | Corrected task affinity |
|---|---|
| `.MainActivity` | `com.hugr.wearos.main` |
| `.SavedDiagnosticActivity` | `com.hugr.wearos.diagnostics` |

Both remain standard (`LAUNCH_MULTIPLE`) launch mode. Component identities, icon/label, process, permissions, services, all other launchers and legacy/frozen behavior are unchanged. No CLEAR_TASK, CLEAR_TOP, singleTask, finishAffinity, finishAndRemoveTask or runtime navigation was introduced.

This separates task selection, **not app storage or recording identity**. Both activities still use their existing app-owned stores. It neither stops/clears a current recording, acknowledges retained bytes, nor permits a second Start against an unresolved run.

## Validation

| Check | Result |
|---|---|
| Before correction, new focused suite | **3 tests failed**: shared affinity plus both wrong-screen modeled routes |
| After correction, strengthened focused suite | **3/3 pass**, no failures/skips |
| Full Watch unit/Android-facing suite | **233 total: 229 pass, 4 fixture-dependent skips, 0 failures/errors** |
| Other manifest semantics | **Exactly unchanged** after removing the two intended affinity attributes |
| Watch production Kotlin/resources/version | **Exactly unchanged** from pre-task baseline |
| Phone61 runtime/native/UI/tests/configuration | **Exactly unchanged** from pre-task baseline |

The test invokes actual MainActivity and SavedDiagnosticActivity lifecycles/views, actual FreshRunRuntime admission/journal/receipt storage, and actual diagnostic persistence/readback in disposable synthetic app roots. It covers both launcher directions, repeated Home/reentry, warm Activity reuse, Main configuration recreation, and Activity destruction. Both a STARTED admitted session and a FINALIZED/unacknowledged session remain the same in-memory session with byte-identical receipts, unchanged record index and hashes of **nonempty actual production journal files**, and byte-identical saved report sets. Passive navigation requests no services, permissions, Share or other activity.

Initial test compilation required only moving two pure helpers into their companion scope; no production change was made for that harness correction. Before-failure XML, after-passing XML, full-suite XML, scope checks and artifact checks remain under `/home/ubuntu/HUGR_WATCH_LAUNCHER_ISOLATION_2026_10_12/validation/`.

### Completed full-workflow validation reused, not rerun or upgraded to hardware proof

The previously completed six-phase joined acceptance remains applicable because recording/service/receipt/protocol/Phone code is byte-unchanged. Its summary SHA-256 is `83a6378e2bad40f662a8a068a03e640a1791c0401fff6101fe8bb4db860b1de5`:

Watch recording fixture → Phone ordinary canonical ingestion/exact ACK bytes → Watch acceptance of those Phone-generated ACKs → production native extraction → native writer/verifier/durable report/restart/offline reader → complete Phone deterministic suite: **6/6 phases PASS**, including **94/94 native tests**. This is explicitly **synthetic coverage**, not radio/sensor/private-custody evidence. The full Watch suite was rerun for the manifest-dependent Activity environment; no unnecessary Phone retest or rebuild was performed.

Unchanged installed-artifact identities were rechecked:

- Watch79 SHA-256 `91d3337b963c7fb31c510ba58cab8f59cd4e9ee62899b2f4762bedc5383ce5fb`.
- Phone61 SHA-256 `61b9a674fea6b705563fcf80157390ae8d6b7fac36f696e8ad041c960cbd2c5c`.

Protected SourceJournal, recording/finalization logic, exact ACK/completion receipt, current-session selection and all frozen boundaries are unchanged. Test data is synthetic and disposable; no physical private roots were inspected.

## Limits and next complete-workflow decision

Robolectric does not execute the real ActivityTaskManager task-selection algorithm. The in-test model derives task keys from actual merged manifest affinity; it does not invent real Android task IDs or validate physical Wear launcher flags. Warm/recreated tasks created under corrected metadata are covered. **Removal or migration of an already-existing pre-update shared task/back stack is not proved.** The correction deliberately does not clear that stack or destroy the admitted run to manufacture a green result.

The source-supported launcher explanation does not physically diagnose the first receipt-unavailable error, the Watch79 black screen or recording final state. The captured historical Watch78 second-Start exception remains source-matched; it is not a new Watch79 failure.

**One next decision:** authorize one greater-version Watch-only candidate build/admission incorporating these task affinities and the already-tested fresh-flow corrections; keep Phone61 unchanged. No such build is authorized by this source-only closure. Its later first physical gate should be a separately authorized **passive Main receipt-state check**, not another Start: intended Main screen/current receipt, no controls, first result/failure or 60 seconds, Phone untouched. If an old diagnostic task is still restored, stop without icon exploration or broad task clearing.

The current recording's receipt still governs admission. STARTED or FINALIZED/unacknowledged needs a specific preservation-respecting current-session decision before a fresh run; do not automatically clear it. FAILED/COMPLETED may permit later admission but does not qualify the prior recording.

The controlling objective remains one complete fresh five-minute acceptance → controlled finalization → full canonical custody → attributable Watch-origin exact ACK/completion receipt → one durable Phone result → offline recheck after app restart, then the duration ladder and existing live-observation/Watchtower milestone. No indefinite historical Watch69 reconstruction or new observer infrastructure is a prerequisite.

## Preservation and sovereign statuses

Apply source-owned receipt → Master → no affected private proposal/accepted S4 mutation → Alignment last → bounded coherent commits → controlled non-force GitHub mirror and independent head/tree readback. Private media, physiological payloads and credentials are excluded. Exact remote confirmation is supplied by the separate preservation readback only after it succeeds.

- Watch69 **NOT QUALIFIED**.
- Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**.
- Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

Sources already read for the mechanism: [Android tasks and back stack](https://developer.android.com/guide/components/activities/tasks-and-back-stack) and [Android activity manifest reference](https://developer.android.com/guide/topics/manifest/activity-element).
