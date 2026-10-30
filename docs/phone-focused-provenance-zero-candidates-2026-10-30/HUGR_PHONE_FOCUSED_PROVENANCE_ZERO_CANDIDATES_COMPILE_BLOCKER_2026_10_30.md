# Full application compile blocked: custom provenance walk found zero candidates

**The replacement full Android/Expo application compile did not run.** The one focused check authorized at 15:49:36 on 30 October ran once, saved the requested findings, and stopped at the unchanged custom provenance guard. The replacement compile remains unused. This is not evidence of a HUGR source defect or a package incompatibility.

The specific failure is:

> HUGR bounded bootstrap STOP: actual task-input chain must reach exactly one configuration

The parent independently verified the exact returned ZIP and all 41 members, including 40 declared payloads. It then checked the actual saved witness, raw logs, terminal receipt, instrument change and current controlled Phone source. No returned tool was executed in the parent.

## What the focused check actually found

The actual task being observed was `:expo:compileDebugLibraryResources`. Its implementation and pinned base class came from the expected AGP 8.12.0 JAR. The actual task input reported AAPT2 version `8.12.0-13700139`.

There was one configured source object, of type `ResolutionBackedFileCollection`. The existing bounded walk visited that one object at depth zero and recovered **zero distinct Configuration candidates**. Its queue drained without hitting the unchanged maximum depth of eight or maximum forty nodes. The witness records the object's three private fields: `artifacts`, `lenient` and `resolutionHost`.

The existing filter excludes `resolutionHost`. The primitive `lenient` field cannot yield an allowed child object. The witness does not record the value, access outcome or child class of `artifacts`. Therefore the exact runtime reason that this remaining field did not lead to an admitted child is unknown. We do not infer that no configuration exists anywhere in Gradle. Nor does the one-node result imply that a bigger traversal budget would help.

The instrument now saves its safe witness using a fresh file and a forced complete write before evaluating the unchanged walk-completion and one-configuration conditions. Its candidate count, class metadata and traversal findings were retained successfully. This is **instrument capture PASS**, not selector provenance PASS and not native Phone diagnostic capture on a device.

The terminal parent receipt records one child invocation, exit 1, no timeout and no retry. The saved witness and raw stderr preserve the first cause. The separate structured first-STOP file and final selector-proof file were not produced. We do not relabel their absence as successful structured retention. The JSONL has 40 installed-guard events, 61 included after-task states and one main-selector-before-action event; those 61 states are not 61 newly executed original actions. Previously earned bootstrap outputs were reused.

## What completed and what remains

The corrected six-case harness and its real exact-identity rejection before downstream effects remain PASS. The earlier 78 schema cases and 27 Jar-manifest checks remain earned. The seven isolated test packages are installed. The nine-Jar/seven-descriptor bootstrap evidence is preserved. None of those checks or package installations was repeated in this focused continuation.

The actual task/code/version observations and the new durable bounded witness are now earned. **Private configuration recovery is still FAIL.** Exact AAPT2 preparation, strict offline resource-tool use, the current actual app graph/provider checks and the one clean replacement full application compile were not invoked. Fresh application, source-journal, Rx-provider and BLE bridge classes are not established by this result.

The original application compile remains a separately preserved consumed failure caused by missing offline AAPT2. Its 429-byte marker is unchanged. The replacement application invocation remains UNUSED. The conditional AAPT2 checksum-policy derivative remains unapplied; the operational baseline policy is unchanged.

## Is the custom requirement necessary?

The private reflection walk is **our chosen evidence mechanism**, not an intrinsic requirement that AGP imposes on Android compilation. It attempts to recover the internal Configuration object from the task-facing file collection without resolving files. That depends on private Gradle implementation fields, access behavior and our safe-field exclusions. An ordinary resource compilation does not require an external observer to recover that object by this method.

This conclusion is supported by both the retained pinned AGP API/bytecode evidence and the focused static disassembly of four relevant classes. Those show the actual resource task's `getAapt2()` interface, version and binary-directory accessors, and the relevant task-creation/AAPT2 factory surfaces. These static facts do not yet prove complete runtime coordinate/classifier selection, available artifacts or actual resource-tool use.

The current criterion therefore stays failed. It must not be called PASS, disabled silently, or bypassed on the strength of a plausible version or configuration name.

## Shortest technically sound next route — proposal, not execution authority

I recommend replacing **only the private Configuration-object recovery criterion** with an explicit operational evidence chain:

1. Bind the actual Expo resource task and its task input to the pinned AGP code. Establish the precise existing AAPT2 coordinate, version and Linux classifier through that source-bound request path, not a fabricated configuration or a name-only inference.
2. Prepare only the already-approved exact official AAPT2 POM and Linux archive through normal Gradle resolution in the isolated cache. Apply only the previously reviewed checksum admission. Match the materialized task input and extracted executable to those approved archive bytes; finding an unrelated matching JAR elsewhere in a cache is insufficient.
3. Demonstrate that the actual bound resource task uses that exact tool strictly offline and produces fresh declared outputs. A skipped, UP-TO-DATE or FROM-CACHE task alone is insufficient proof of fresh resource-tool use. Normal file materialization belongs to this explicitly admitted offline-use stage, not the metadata-only reflection observation.
4. If those gates pass, retain the exact Phone source/configuration, strict verification, one local Rx provider/no duplicate and clean-class requirements, then run the one still-unused replacement full Android/Expo application compilation. Keep the original failed attempt and all its evidence intact.

This replaces an internal-object identity claim with direct evidence of the exact requested tool and its actual use. It deliberately would **not** prove recovery of a particular private Configuration object. It would not relax source identity, checksum verification, offline-use requirements, class freshness, package identity, signing or any HUGR transport/custody rule.

**No further reflection retry is recommended.** The alternative needs explicit approval because it changes the proof criterion, not because another routine compilation permission is needed. If approved, it should be one bounded same-lane continuation through the remaining conditional gates, with no further package/reinstallation/metadata programme or unrelated test repetition. The current source-bound request and extraction/use details still need to be evidenced; a later concrete integrity or scope failure must be preserved rather than guessed away.

## Exact source and tool bindings

Current controlled Phone head at result admission: `fb2029e43a7afc266695832b59746d73ce70858c`. Parent direct byte checks passed for all 350 selected source files and all 242 native postimages. The reviewed incorporated runtime delta remains 200 paths; no new runtime edit occurred. The inherited narrow before/after custody records agree for 132 configuration-related and four BLE bindings, 23 frozen artifacts and 51 global/protected bindings. That is not a fresh traversal of every historical tree.

Watch source and Watch85 remain unchanged. The source-owner worktrees were clean before result documentation. The phone's package/lock/app/deadline intent is unchanged.

- Actual AGP JAR SHA-256: `ac19aabdfb6736ed49697e107ba0d824c6f5078e203f2bce911e8a6bba6d91cb`.
- Actual requested AAPT2 scalar version: `8.12.0-13700139`; complete runtime selector/classifier linkage remains a future gate.
- Previously approved AAPT2 POM SHA-256: `259aa3f57b4758b17cf4902525fc391d7d09671d8fa5e156aed426dce281ded8`.
- Previously approved Linux archive SHA-256: `df85593b92c25199515750a30b14744acc42537b2d1176be61b248111484134f`.
- Unchanged operational verification policy SHA-256: `a609e4f845ba357dd9f3223957377687799ecca021d3480811e6e888acd7797e`.
- Unapplied conditional policy SHA-256: `f982356c25b42387ae01a076a8ec2c1260f236bb023ac1c4f19ee5e9ac4352fb`.
- Focused guard SHA-256: `97a9390931065156103ac76d6bd709fdbe7a3543591bc31fc239619f402c6f16`.
- Focused parent runner SHA-256: `4061231409dfaf8820a418afdc081b3706a1a1dae161e6c8ea705d49e432ca50`.
- Saved witness SHA-256: `2e44a4c2f8e13b8ecf0e8281ec48a0cd080220b786405a336e67311277b2d8cf`.
- Focused spent marker: 7,920 bytes, SHA-256 `aedfc6ac1e73913057e3c42194b963fe8cbb4485fcffd44709481f039f6720a6`.
- Original consumed app marker: 429 bytes, SHA-256 `22da98375904dacee01de5f15ba277004bff14ca26adf5335d543a3c83f5af37`.
- Returned evidence ZIP: 169,537 bytes, SHA-256 `1aa0c1755b5a4d6a1c4c679bd1ebbfc7d1c559312c8cb15deecbf7e3d50e1f6b`.

The actual command retained pinned Gradle/JDK flags, strict offline dependency verification, one worker and disabled automatic SDK/JDK downloads. It ran only `:hugrObserveActualExpoAapt2SelectorCorrectedOnly`, not an application compilation target. No broad toolchain revalidation was undertaken.

## Evidence and preservation boundary

Exact source-owned result documents, selected bounded witness/receipt/log evidence, Master next and Alignment last are prepared for standing non-force preservation with both-repository actual write preflight and independent remote head/tree/blob readback. The final remote proof is a separate receipt; preparation alone is not remote preservation PASS. Tool/cache binaries, private recordings and credentials are excluded from publication.

The full focused evidence is retained unchanged in `HUGR_Phone_Focused_Provenance_Findings_And_Compile_Blocker_2026_10_30.zip`. The returned report, witness, terminal receipt and raw stderr are under `verified-return/`. Parent integrity and source/witness verification are `PARENT_RETURN_INTEGRITY.json` and `PARENT_FOCUSED_RESULT_VERIFICATION.json`.

The [original focused authority](AUTHORITY.md), [prior conditional source assessment](PROVENANCE_REQUIREMENT_ASSESSMENT.md), and [returned actual necessity assessment](verified-return/focused/necessity-assessment/NEED_AND_SIMPLER_ROUTE_ASSESSMENT.md) remain distinct records. The earlier assessment's pending-result wording is historical; the zero-candidate outcome above is the current observation.

There was no APK build/admission, signing, installation, app launch, Watch/Bluetooth/device/recording/physiological/IDC action or source/package/dependency/deadline/transport/ACK/custody change. Runtime native diagnostic capture and physical failure-path retention remain UNTESTED. Compilation, even when it eventually passes, will not demonstrate a physical timeout cure. The five-minute physical custody PASS and synthetic limits remain preserved. Watch69 remains NOT QUALIFIED; Watch68 remains UNCLASSIFIED / NOT VERIFIED; Watch58 remains TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

### Concurrent documentary baseline retained

The initial result-document preparation stopped before writing repository files because another authorized task had advanced the control-document branch. That exact pre-write STOP is retained. The bounded read-only comparison found unchanged Phone/Watch refs and one linear, append-compatible 26-document control update. Only those exact already-published documents were imported locally, with complete tree and byte readback and no remote updates or runtime change. Its source-owned BL-01 contract and coordination records expressly leave this native compilation lane independent. This result's Master and Alignment entries follow the imported baseline; neither task's history is overwritten.

Inspected drift-map SHA-256: `7a2bac374fb6e09fd5dc0d9be7795cf6efec017ead9fa44c2ff6fa696763bd3a`. Exact existing control head: `a4ee4f25937506f0b4f98e9fb041e301a9cdf255`; tree: `1a90c094fb54c207d2779142a15f38bf6fa67a5c`. This import is documentary custody, not another dependency or tooling investigation.
