# HUGR — Engine Archaeology and Closed-Loop Clarification: New-Task Handoff

**Prepared 17 October 2026 for a separate task inside the Nerva Project.**

> **Assignment: research, architecture and planning only. No engine implementation, production changes or device action.** This handoff equips an independent research lane; it does not move the substrate gate or establish new scientific evidence.

## 1. The purpose — retain the whole aim

Neil is developing HUGR as a personalised, multimodal physiological-intelligence and compassionate-awareness platform. Its purpose is not merely emotional-state classification or collecting ever more self-reports. It should support recognition, agency and compassionate exploration, ideally requiring fewer unnecessary interactions as appropriate personalised learning develops.

The working hypothesis is that physiology, behaviour, context and subjective experience have informative relationships over time. Multiple analytical methods may reveal changes, competing explanations and uncertainties. A common clarification architecture could decide whether existing evidence already answers a question, whether an optional person-facing clarification is useful, and how an answer or non-answer should update an interpretation without erasing its original evidence.

**Information gain is not the sole objective.** Model benefit, person benefit, optional exploration and justified silence must remain distinct. Suki's decision about whether/when to invite interaction is separate from an engine's nomination of an uncertainty. A self-report is evidence with provenance—not physiological ground truth.

The original brief is preserved verbatim as `NEIL_ORIGINAL_RESEARCH_BRIEF.md` in the accompanying source pack. Use it alongside this handoff; do not substitute a generic AI-agent or telemetry project.

## 2. Exact work requested

Produce one bounded, source-referenced research package:

1. **Reconciled engine inventory:** implemented/tested, partial, recovered concept, proposed/unimplemented, or component better combined with another. Preserve original names and source references. Tests establish only the capability actually exercised.
2. **Engine–question coverage matrix:** analytical target; inputs; timescale; quality limitations; uncertainty; what existing evidence could resolve; relevant existing questions/cards; how an answer would change interpretation; and when no question is justified.
3. **Shared clarification contract:** evidence IDs, method/version, competing interpretations, uncertainty, proposed question or missing capability, expected value, timing/sensitivity/burden, response/non-response, revised interpretation, and immutable lineage. Include false-premise correction, another route, defer and abstain.
4. **Synthetic tournament design:** reuse the original compact tournament as the baseline. Compare simple baselines, selected engines/combinations, fixed versus adaptive clarification, learning with/without clarification and held-out scenarios. Address simulator leakage, independent hidden mechanisms, simultaneous influences, within-person variation, artefacts, missingness and imperfect reporting. Retain negative results; identify where independent real-world data is needed.
5. **Minimum executable demonstration proposal:** observation → competing interpretations → unresolved uncertainty → nomination → simulated answer → updated interpretation → recorded evaluation. Recommend the smallest reusable set; **do not implement it**.
6. **Prioritised implementation plan and expansion gates:** what should be built first, what may develop independently, what needs N1/N2/later participants. Preserve the distinction between N1-informed development and a more independent N2 evaluation of frozen finalists.

**Whole–part is a priority question, not a predetermined engine.** It may describe relations among observations, interpretations, modalities and timescales. Inspect the archaeology evidence before deciding whether it is a method, coordination architecture, representation or another category. Do not silently rename, merge or discard it.

Do not automatically turn every recovered concept into an engine, every catalogue item into a question card, or every uncertainty into an interruption. Existing factual context, quality and placement questions must remain distinguishable from experience controls and internal no-question outcomes.

## 3. Access: what carries over and what must be verified

Create the new task **inside the Nerva Project**. The project supplies shared instructions/files and applicable configured capabilities. A task created through the task-management capability normally inherits the originating task's selected skills/connectors/addons; a manually created task should verify its actual selections. **Credentials are resolved independently.** This document cannot grant repository permissions or promise that another task inherits private logins.

A new isolated sandbox does **not** automatically receive this task's filesystem, processes or entire conversation. Use the shared handoff, source pack, private repositories and explicitly attached task references. Do not assume historical absolute paths exist.

Initial access checklist:

- Read project instructions and `hugr-workflow`; then current Alignment, Master and latest referenced source-owned record.
- Inspect the new task's actual GitHub configuration and verify read access to the repositories below. Use the configured GitHub CLI/connector route; never paste/request/copy tokens.
- Obtain **separate read-only clones** at verified branches/commits. Do not checkout, reset or edit this substrate task's working directories.
- Read existing reports first. If a named audit is not recovered, report that exact source gap and make one bounded search through the referenced task/archive—not a reconstruction from memory or an unlimited archaeology loop.
- Treat access and authorisation as different things: reading repository source does not authorise product implementation, builds, deployment, collection or access to physiological records.

## 4. Current controlled source — do not use `main` as a substitute

These remote heads were independently read and matched to the local trees before this documentation handoff. They are **dated recovery pins**, not a claim that branches can never advance. Refresh the current governing records before substantive work.

| Role | Repository | Branch | Verified remote head |
|---|---|---|---|
| Watch source | `Nervahealth/nerva-wearos` | `preservation/watch68-partial-source-2026-10-03` | `15c8eb1fb08d2c93f742e2dfcc69b05a602e8904` |
| Phone source | `Nervahealth/nerva-mobile` | `preservation/phone-control-and-source-2026-10-03` | `b20d5be6236ad3ed631eacd8f454ccdcaca6543e` |
| Governing records | `Nervahealth/nerva-mobile` | `preservation/watch69-control-records-2026-10-03` | `874a5fd4697dd62ff382c552feb373623b807b75` |

Full local/remote IDs and shared tree identities are in `SOURCE_PROVENANCE.json` in the pack. Mirrored local and remote commit IDs can differ while exact trees match; do not call that a fork or tidy it by force-push/main merge.

Read these governing files from the **control branch**, not an older main snapshot:

- `HUGR_SHARED_ALIGNMENT_CONTROL_SURFACE.md`
- `HUGR_MASTER_TASK_LIST.md`
- `docs/HUGR_NSTAIR_WATCH80_COMBINED_CORRECTION_BUILD_ADMISSION_2026_10_12.md`
- `docs/HUGR_NSTAIR_WATCH80_PHONE61_RECEIPT_FIRST_PHYSICAL_GATE_2026_10_12.md`

For engine/source investigation, locate the current versions of `lib/fusionEngine.ts`, `lib/learning/`, context/question-policy code, `lib/scientificMemoryCore.ts`, `lib/scientificMemorySync.ts`, `lib/dataSync.ts`, and actual callers such as `components/WatchConnect.tsx`. Verify files exist; trace implementations and callers rather than inferring wiring from names or diagrams.

The portal is a separate managed project. The local source path in the substrate task is `/home/ubuntu/nerva-for-clinicians`; that is **a locator, not a portable checkout or deployment permission**. Relevant preserved concepts include the scientific-memory ledger, deterministic monitor and protected Watchtower. This research task does not need live portal/database access to design a synthetic harness.

## 5. Design/archaeology lineage — preserve source currency

### Private design archive

Repository: [Nervahealth/hugr-book-one-design-archive](https://github.com/Nervahealth/hugr-book-one-design-archive), branch `main`.

Verified discovery pin: commit `6a3ea9a75748a7d313f0b8b4264995d8f77320dc`, tree `77b98a9c5adf6f102ccb3fa1f4849051ec1390bd`.

Start with this sequence, **without repeating unchanged assessments**:

1. `2026-10-12-smart-ema-contracts-engine-reconciliation/HUGR_Smart_EMA_Interface_Contracts_and_Engine_Reconciliation_v0_2.md`
2. Its `workflow/ENGINE_CAPABILITY_ASSESSMENT.md` and source-index notes.
3. `2026-10-12-smart-ema-targeted-supplement/HUGR_Smart_EMA_v0_2_Targeted_Supplement.md` and accompanying local ZIP, which preserves the later supplied Rediscover/Executive Decision/GUI materials.
4. `2026-10-12-smart-ema-minimal-linkage/HUGR_Smart_EMA_Minimal_Linkage_Contract_and_Fictional_Walkthrough_v0_1.md`.
5. The shared `HUGR_BOOK_ONE_DESIGN_ARCHIVE_POINTER.md` and current participant candidate `packages/participant/HUGR_Book_One_Participant_History_Glyph_v08_Local.zip` when GUI details are relevant.

**Currency warning:** the archive README/work index still describe parts of the v0.7 lineage; the tree contains newer v0.8/Smart EMA material. An older index is not evidence the later work is absent.

The v0.2 capability assessment inspected mobile `c1140aa1852ae4900034c8596ac7e46506883a5c`. Its capability/absence findings are **snapshot-scoped**, not an audit of today's controlled preservation branch. The targeted supplement qualified that limitation. Compare only relevant current engine/context/question-policy paths and callers; do not restart a broad source audit.

Design activity for emotion/thought can continue independently. “Parked operational linkage” does not mean deleted design work or a requirement to wait for substrate qualification before design review. Book One prototypes and fictional walkthroughs are not live physiological inference, real haptic evidence or a validated clarification policy.

### Original tournament baseline

Source documents recovered from mobile `main` at the historical pin `c1140aa1852ae4900034c8596ac7e46506883a5c`:

- `docs/HUGR_2026_09_18_Integration/04_TOURNAMENT_ALIGNMENT/HUGR_DUAL_REPRESENTATION_TOURNAMENT_ALIGNMENT_VERDICT_2026_09_18.md`
- `docs/HUGR_2026_09_18_Integration/04_TOURNAMENT_ALIGNMENT/HUGR_AUDITOR_TOURNAMENT_MAP_HEAD_ENGINEER_REVIEW_2026_09_18.md`
- `docs/HUGR_Engine_Conditioned_Bucket_Placement_Clarification_2026_09_18.md`

Use these as historical design evidence, reconciled against later authority—not a new broad tournament execution mandate. Their exact verified bytes are included in the pack.

Also read, if relevant, [Nervahealth/nerva-idc-preexecution-repair-handoff](https://github.com/Nervahealth/nerva-idc-preexecution-repair-handoff), branch `research/idc-preexecution-repair-handoff-2026-09-23`, verified head `21f2580ac0bd0b01e00dd043e18f4f4a0c82a17d`. It is a separate research handoff, **not** the current production baseline or proof that all whole–part archaeology has been recovered.

### Referenced conversations

These IDs were discovered through the project task listing/history; attach them to the new task through supported task references where available:

- **`lTgoiePVH8ITWZENgvqMn3`** — this substrate engineering task; current source authority, preservation and physical gates. Read relevant slices, not the whole long chat by default.
- **`Oi7icFK3r8NkY9ZQcgXV1z`** — prior Book One/Smart EMA reconciliation lineage, targeted supplement and minimal-linkage work. Useful source pointers and explicit source-currency caveats.
- **`dFbQT8jBMdt3duy4xblTos`** — older HUGR context/design lineage. Historical interpretations—including water/reservoir associations—must not be imported silently into current GUI semantics.

The exact original whole–part archaeology audit has **not** been independently identified/assessed by this handoff preparation. Recover it through the prior research lineage and source references before making claims about it. Do not confuse the copied Smart EMA engine assessment or IDC handoff with that audit.

## 6. Skills and workflow for the new task

Discover the new task's available skill catalogue; read actual files before use. Relevant entry points are:

- **`hugr-workflow`** — mandatory for HUGR work; later source-owned authority and Neil's standing no-RUN-reminder preservation rule supersede historical skill examples.
- **`deep-research`** — before external searches/fetches.
- **`workflow-composer`** — before planning comparable research across five or more independent engine candidates; use structured per-item units and shared synthesis. Do not turn the whole–part question into an engine merely to fit a map.
- **`webdev-readme-mobile`** / **`webdev-readme-fullstack`** — when inspecting Phone/portal architecture.
- **`data-analysis`** — for proposed evaluation/metrics work; **`builtin-llm-models`** before any later approved bulk-LLM scripts.
- **`manus-config`** / **`manus-tasks`** — source access configuration, project handoff and authorised task references.
- Office/diagram/presentation skills only if those output formats are actually chosen.

Do not duplicate or rewrite existing skills to manufacture access. No credentials, signing identities or private device records belong in a handoff.

## 7. Protect the substrate lane and its current status

Installed pair: **Watch80 + Phone61 on the Honor delivery phone**. The newer Samsung/Fold is not authorised as a substitute custody/delivery host.

The carried Watch79 STARTED receipt was deliberately classified `FAILED / PROCESS_INTERRUPTED` through a separately authorised receipt-only closeout. That does not qualify a recording or reconstruct unavailable historical evidence.

**Latest physical authority in this task:** one fresh Watch80/Phone61 five-minute acceptance attempt plus conditional offline recheck, authorised 13 October. **No execution/result has subsequently been reported here.** The research task must not consume, retry or reinterpret this device authority.

Controlling acceptance remains:

> actual recording → controlled finalisation → exact canonical delivery → Watch-origin, source-bound ACK acceptance/completion receipt → durable Phone result → restart → successful offline custody/receipt recheck.

Phone ACK write success is not Watch acceptance. UI STARTED/connected/live values are not full acceptance. No routine Watch filming is the intended operational workflow; the durable Phone result is the short-run goal. After physical acceptance, the duration ladder/eight-hour rehearsals and existing live-observation/Watchtower bridge remain visible, without making the entire observer stack a prerequisite for this short run.

Historical status literals remain unchanged:

- Watch69: **NOT QUALIFIED**.
- Watch68 v1: **UNCLASSIFIED / NOT VERIFIED**.
- Watch58: **TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED**.

Do not access/mutate/transfer legacy or frozen roots, reconstruct unavailable historical proof indefinitely, change pairing/Bluetooth, launch apps, start sensing, activate egress, recruit participants, upload private records or execute N1 from this assignment.

## 8. Deliverable and stop discipline

Start with a concise source-access/currency statement and the existing compact tournament. Then complete the requested architecture package; don't stop at a broad checklist or ask Neil to locate source files the repositories/tasks can supply.

End with **one smallest recommended first implementation**, its acceptance tests, inputs still missing and evidence required to expand. Separate established facts, design proposals, unresolved research questions and synthetic versus real-world validity throughout.

Preserve research outputs in their own research/design lane. Do not edit these product source branches or shared executable order merely because the report recommends something. If a material decision needs integration, submit a clearly labelled proposal to the substrate lane; accepted private VIS-COUNT S4 remains immutable.

Standing product-source closeout remains automatic where applicable: source-owning receipt → Master → affected private proposal only when authorised/available → Alignment last → consistency → coherent bounded commits → controlled non-force mirror and exact independent remote-tree readback. Never claim remote preservation from local files or an archive alone. No force-push/main merge, token disclosure or repeated known-denied access attempts.

## 9. Ready-to-paste starter for the new task

> This is a separate Nerva Project research task: **HUGR Engine Archaeology Reconciliation and Closed-Loop Clarification Architecture**. Read the shared `HUGR_ENGINE_CLARIFICATION_RESEARCH_HANDOFF_2026_10_17.md`, the original brief and the accompanying source pack. Verify actual access, current control branches and source currency before conclusions. Use the prior Smart EMA reports/supplement/minimal linkage and original compact tournament; do not repeat unchanged work. Recover the named whole–part archaeology audit without substituting another report. Complete the inventory, engine–question matrix, shared contract, fair synthetic tournament and minimum demonstration/implementation plan specified in the handoff. **Research/architecture/planning only: no implementation, production edits, builds, devices, collection, deployment or private physiological-data access.** Keep the substrate lane independent and end with one smallest first implementation for separate approval.

## 10. Recovered Junkyard report — 17 October 2026 addendum

The historical **HUGR Junkyard Archaeology Report — Updated source-recovery checkpoint, 5 September 2026** is now recovered as exact uploaded Word bytes. Read `docs/research_sources/HUGR_Junkyard_Archaeology_Report_2026-09-05_UPDATED.docx` and `docs/HUGR_ENGINE_ARCHAEOLOGY_REPORT_RECOVERY_2026_10_17.md`. SHA-256: `ddb4b638d68ada942fcb433f733a674cff61df88fef4311b23e5f8966fc42168`. The original source is public user message `FeJWp4DWcJvdlwX4D0GTxy` in task `lTgoiePVH8ITWZENgvqMn3`. This is the earlier engine/runtime-fork checkpoint, not the Smart EMA assessment or a reconstruction.

This supersedes the earlier access gap **for that Junkyard engine report only**. The document does not explicitly establish a whole–part architecture; the separate original whole–part provenance remains unresolved. Its historical code/runtime claims are not a fresh audit of today's controlled source. The accompanying original source pack remains an unchanged historical snapshot. All research-only boundaries and the independent Watch80/Phone61 lane remain unchanged.

## 11. Separate whole–part audit search — 18 October 2026

A bounded same-period public-history/documentary search did **not** recover a separately identifiable whole–part audit. Read `docs/HUGR_WHOLE_PART_AUDIT_SEARCH_RESULT_2026_10_18.md`. Do not treat this as proof of nonexistence, loss, or absence of the whole–part concept; the separate-report assumption itself remains unverified. The recovered 8 September Meteorological Machine Technical Bench Review is a related whole-system/component review, not a verified substitute. The Junkyard Word report and original source pack remain unchanged. Research scope, the whole–part source/concept question and the separate Watch80/Phone61 lane are unchanged.

## 12. Exact Maximus post-handover and distinct WTF source lane — 18 October 2026

Read `docs/HUGR_MAXIMUS_WTF_SOURCE_RECOVERY_2026_10_18.md` and the exact `docs/research_sources/HUGR_Post_Fur_Ball_Maximus_Handover_2026_09_14_ORIGINAL.txt` with its provenance sidecar. Source: this task's user message `Vik55Bm5mQLAfHIgRk2GZq`, original `pasted_content_65.txt`; 59,723 bytes; SHA-256 `f2845924129bf829148f555883c52bec15a58d0c3a43536e5ebc2a96306a5df0`. This is the submitted **POST-FUR-BALL MAXIMUS CANONICAL MANUS HANDOVER**, not proven to be the earlier complete working package or original whole–part audit. It reports WTF archaeology and Maximus closed, but supplies no literal whole–part findings or original WTF report citation.

Preserve Neil's distinction: earlier `WTF_WERE_THEY_THINKING_ARCHAEOLOGY` → larger/pre-pruning `Fur Ball Maximus` lineage → later 14Sep Anti-Amputation/Observational Optionality correction; Junkyard is another related engine-code investigation, not a confirmed whole–part source. Prioritise the actual original WTF and pre-pruning Maximus materials. Audit authorship/originating task remains unverified; known task/message proves handover delivery only. Do not reconstruct findings from later summaries, erase the concept, or use historical execution instructions as present authority. Scope and separate physical lane unchanged; original ZIP unchanged; shared-project update still not claimed approved.

## 13. Primary 4–8 September alignment sequence and export coverage — 18 October 2026

Read `docs/HUGR_ALIGNMENT_CHECKPOINT_SOURCE_TRACE_2026_10_18.md`. Actual source messages establish **4Sep Safe Map/upstream alignment preparation → 5Sep frozen Junkyard checkpoint → 5Sep bounded Founder–Implementation Alignment RUN# → 8Sep resumed same work and delivered original 88-line matrix → 14Sep recovered post-Fur-Ball handover**. Fur Ball Maximus is not an established start point for all earlier detective work; its original exercise's start remains unverified. The exact RUN and matrix now reside unchanged in `docs/research_sources/`, with provenance and the narrow original-message sequence. Filename/header5Sep is RUN date; matrix delivery8Sep is evidenced separately.

A “complete” task export is not proof of project-wide research/subagent coverage. The original RUN explicitly limited archaeology to selected Safe Map and historical documents. Preserve this coverage limitation: absence there does not establish nonexistence elsewhere. The matrix is a genuine Founder–Implementation Alignment output, not an identified whole–part engine audit. It preserves historical fork rationale as UNKNOWN and query utility as a hypothesis; no explicit whole–part findings or original whole–part task ID appears. Keep that gap open; do not reconstruct or substitute later summaries. Research/physical scope, frozen statuses, originalZIP and pending shared-project approval remain unchanged.

## 14. Founder relational physiology / three-loop supplement — 18 October 2026

Read the exact `docs/research_sources/HUGR_Relational_Physiology_and_Clarification_Supplement_2026_10_18.md` and its provenance; source-owned preservation note `docs/HUGR_DISCOVERY_SUPPLEMENT_PRESERVATION_2026_10_18.md`. Uploaded `18_OCTOBER_DISCOVERY_SUPPLEMENT-3.md`, 5451bytes, SHA256 `d3e7ae367f6eabfa84c34319b5202204639d6f1f834412c73da7b6e1492e300d`, preserved unchanged. Founder-authorised research/design context only. Machine clarification need not question the human; keep independent machine clarification, optional human clarification and independent human-benefit loops, with Suki separately gating invitations and silence valid. Relational measurements/declared inspection lenses are derived representations, not new independent raw evidence, biological causality or validated interpreters. Reconcile dynamical-hypothesis contracts, engine commitments/challenges and revision lineage, provenance/availability/leakage/quality/clock/missingness controls, fair conventional/dynamical/reservoir comparisons and actual person value without building them all or amputating preserved representation/portal/whole–part work. Preserve specific negative/inconclusive historical results without overgeneralising.

Referenced conversation excerpts/six research proposals/authority notes/v7 continuityZIP are pointers only in this attachment, not newly supplied or independently verified assets. Whole–part audit gap remains unrecovered≠nonexistent. No implementation/device/source-repository/clinical authority follows from the supplement. Physical Watch80/Phone61 lane is unchanged and separate; no new task/message or project-config change.
