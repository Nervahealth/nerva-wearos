# Phone custody — production-chain correction and bounded route decision

**2026-10-10. Source/test closure. No APK build, installation, device read, reconnect, new recording, original-store access, private-data transfer or N1 execution.** Installed Phone60 on Honor and Watch77 remain unchanged. Current source corrections are not in either installed APK.

## Concrete outcome

**A real production persistence defect was reproduced and corrected.** With SQLite rows already indexing a session, an absent accepted binary followed by duplicate-only replay previously opened `FileOutputStream(file, true)`, created an empty binary, counted the records as duplicates, and returned the old durable cursor. This does not explain how bytes first became absent and is **not an ACK bypass**: native manifest verification still requires the actual range bytes and matching hash. No claim is made that this path occurred on Honor.

The correction rejects indexed-but-missing or indexed-but-short accepted bytes before pending-marker publication/direct append, before pending-recovery tail truncation, and before returning a stored resume cursor. It neither reconstructs acknowledged bytes from metadata nor permits the Watch to delete against an index alone. The presence/extent gate is not a substitute for full canonical verification. Failed recovery initialization closes its database handle; missing-file verification closes its cursor. Valid pending append recovery still restores genuinely uncommitted pending bytes and removes only an unindexed tail under the existing protocol.

**The bounded offline metadata branch is also implemented/tested.** When the exact selected accepted file is absent but a safe stable database envelope exists, the passive reader copies only that envelope into disposable cache, queries only the literal selected session, and reports selected row count/index range, claimed indexed byte sum/extent, manifest/historical-verified counts and a selected resume cursor if present. Its terminal result remains `STOP / SELECTED_ACCEPTED_SOURCE_MISSING`; canonical equality is explicitly unknown. It never enumerates foreign sessions or accepted files. Database metadata is not proof of recording bytes, acquisition, exact ACK or completion.

A second demonstrated reader defect was corrected: presence alone of a **stable zero-byte** rollback sidecar blocked an otherwise valid offline read. SQLite TRUNCATE commits can leave precisely such a non-hot sidecar [1] [2]. The reader preserves and copies it, opens only the disposable SQLite copy, and compares full original fingerprints before copy, after copy and after verification. Every nonempty rollback journal, pending append, incomplete WAL/SHM pair, detected source change, copy mismatch, invalid schema or cleanup failure still stops. Zero length is not proof of historical cleanliness, complete writer quiescence or future stability.

## Identity and storage findings

The original saved Watch frames `f06`/`f07` visibly agree with Phone60's selected actual source session. The reader's target was not demonstrated wrong. Kotlin UUID parsing uses little-endian Java MSB/LSB halves and agrees with Watch serialization and the TypeScript/native manifest path. No historical bounded-run UUID is substituted.

The production writer takes Expo's React context, then its **applicationContext**. It uses `filesDir/build45_phone_source_journal/accepted_<actual-source-session>.bin`; its SQLite helper uses `getDatabasePath("build45_source_journal.db")`, schema version3. Phone60's isolated native Activity uses its applicationContext and exactly the same relative names. The separate `:sourceEvidence` process does not statically create a separate app-private storage container.

Production-chain tests construct the writer using Application/context-wrapper conventions, let it create the schema and accepted path itself, then invoke its actual append, persistence, verification, restart and readback methods. This establishes those source storage conventions—not the exact Android user/profile/package/root used during the historical physical attempt. That runtime continuity remains unknown.

Source inspection found no routine accepted-binary deletion, relocation/rotation, package-replaced reset hook or destructive native migration. Error rollback truncates to the pre-append length; pending recovery truncates an unindexed tail only. Seven-day legacy `hugr_raw_` AsyncStorage retention is a different namespace. Known schema upgrades are additive. Existing Phone59 APK inspection confirms production module inclusion and storage literals, not full source-to-DEX semantic identity. Phone59 provenance is `d1eb5213cb252d6bc9bb2886f05a3c7434dd8966`. Exact backup-resource policy/actual restore history was not established and is not investigated indefinitely here.

Bluetooth separation can interrupt delivery while apart; it does not explain disappearance of a previously durable local file. The absence cause remains unresolved. No operator blame, deletion claim or no-loss guarantee is warranted.

## What passed

- **Production-chain reproduction before correction:** 7 tests,6 pass,1 expected failing missing-file duplicate test. The before-result is retained in `validation/production-custody-before.xml` outside GitHub; it is not overwritten as a pass.
- **Complete synthetic native suite after correction:** **42/42 pass**,0 failures/errors/skips:13 production-custody integration,11 passive-reader,10 canonical core,5 gap tracker,3 pending journal. Final strengthened selected metadata assertions reran **13/13 pass**.
- **Complete Phone Node suite:**112 total,**110 pass**,2 pre-existing fixture-dependent skips,0 failures.
- **Existing joined Watch→Phone→Watch regression:**all five phases pass. Historical C8184/R8182 three manifest-only exact ACKs with per-ACK restart/no new records; current case stored Phone records, manifest-only progression and final durable data/full-session/full-hash59-byte ACK. Phone custody in this joined harness is still a deterministic model, **not** native Android/radio proof. The native production-chain suite separately exercises actual SQLite/filesystem adapter code under Robolectric.
- Diff checks pass; Phone BLE/ACK wire/ingestion/UUID protocol, UI, app identity configuration and Watch production app source are unchanged by this package.

Production tests do not pre-position accepted files at the reader's path or invent native tables. The harness extracts **byte-identical production `PhoneSourceJournal`/`JournalDatabase` method bodies**, omitting only Expo entrypoint and `Record/@Field` parameter scaffolding to run standalone. Full production source SHA-256 `7f42aa8810e6c4a08b8e6d003b94a6a4b6df1f5cbd8c55e299843234c2b2dddf`; extracted exact IO SHA-256 `35e6c2e7f0c876833b412efd5d1ba9bf8d645940f60c3fc36cf27423bbd2c52e`. Module registration itself, physical process death, OS power loss, signed in-place update and actual Honor storage are not proven by this harness.

Cases include production-created files/schema; three segment verifier/ACK-eligibility boundaries with duplicate replay/restart; actual binary full hashes; missing/corrupt canonical rejection despite historical verified flags; wrong-session data never substituted; missing/short indexed custody blocked before append/resume; pending restart refusing to reconstruct already-indexed lost bytes; real uncommitted pending recovery; additive schema reopen; actual production TRUNCATE journal commit; nonempty/changed sidecar rejection; cache-only metadata classification; and exact original before/after readback.

## Route decision — advance the instrument, not indefinite reconstruction

**Preserve Watch69 as NOT QUALIFIED and end repeated delivery/historical-proof reconstruction as the default.** This investigation advances future reliability because it fixes a real missing-custody handling flaw and establishes the production writer→verifier→restart→offline reader chain. It does not recover historical bytes or independently qualify Watch69.

One later optional bounded local metadata read can settle whether this exact selected index exists in Honor's current container. If rows exist but its file does not, report **indexed custody without canonical bytes**; do not reconstitute them from offsets/hashes. If selected rows also do not exist, report **selected index and binary absent here, identity/location/nonretention unresolved**; do not enumerate other sessions, clone a phone, or begin open-ended recovery. Neither result blocks all future fresh-session rehearsal indefinitely.

**Move toward a fresh isolated short rehearsal after the real persistence correction and the minimal durable-run-report acceptance are implemented/admitted.** Phone60 and this patch provide a one-session in-memory report, not the reusable durable report required for future runs. That limitation remains explicit; this source closure is not permission or readiness to record now.

Next acceptance is exactly: **record → controlled finalization → exact verified delivery → durable Phone report → successful offline read after app restart**. It must bind actual source session, canonical range/count/bytes/full hash, Phone custody, exact ACK and independently attributable Watch completion, preserve unknowns and failure reason, and admit no samples after finalization. A segment ACK, historical verified flag or index equality is insufficient. Synthetic report-storage/restart/failure tests and a later physical restart read are required before acceptance.

Keep both milestones visible:
1. **Dependable recording instrument:** smallest app-owned durable per-run report and exact terminal evidence, no routine Watch filming; longer runs follow this acceptance.
2. **Live Watchtower observation:** source-linked run-health/windows, durable upload, truthful moving live view and scoped read-only Manus access. Reuse preserved architecture; the full observer stack is **not** a prerequisite for the short recording acceptance. No observer/server/runtime work was performed here.

## One next action and prepared gate

**Next action for approval: finish the minimal durable Phone run-report source/tests and make one combined signer-continuous, greater-version Phone61 candidate containing that reporting plus this tested custody correction.** Do not build a standalone historical diagnostic candidate just to extend Watch69 recovery. The prepared selected-only metadata branch can travel in the same useful candidate, with its local read optional and not a prerequisite for future rehearsal. Same package/managed project/signing route; no replacement account/key; exact upload native-module custody and compiled launcher guards must be checked. App configuration remains code60 in this source/test closure; bump only under build authority. Watch77 remains unchanged unless a named exact-completion evidence bridge proves necessary and is separately reviewed. Do not install a rebuilt code60 artifact or treat it as the already-admitted Phone60 APK.

The bounded reporting completion must persist app-owned immutable per-session report evidence outside canonical stores. Reuse native verified manifests/ranges/custody; capture ACK intent and write success/failure at existing boundaries with actual connection lineage, never call them Watch acceptance. Preserve full session/count/bytes/hash, available acquisition/finalization evidence, unknowns and terminal failure. A selected-session passive viewer must reopen the retained report and recompute current canonical custody without BLE/recovery after app restart. Tests must exercise writer→verifier→report persistence→restart→offline reader, failed fsync/publication, missing/corrupt bytes, repeated report/ACK handling and no sensing/foreign-source access. This is a prepared implementation contract, **not implemented in the current custody patch**. A report must remain incomplete if Watch completion attribution is not available; do not invent it or weaken next-run acceptance.

No build or device operation is authorized/performed by this closure. The separate proposed physical gate is `HUGR_NSTAIR_PHONE_CUSTODY_BOUNDED_READBACK_GATE_2026_10_10.md`: after its own artifact admission and installation authority, one local Honor information-icon read, no Main/Connect/Bluetooth/Watch action, first report/failure or60seconds, no retry/no share/upload. It is preparation for review, not executable authority.

## Preservation and boundaries

Source-owned receipt → identical controlled copies → Master → affected private VIS-COUNT proposal only → Alignment last → consistency → bounded commits → controlled non-force both-repository GitHub write preflight/mirror/exact tree readback. AcceptedS4 remains immutable; known-denied Atlas access is not retried. No private video/screenshot/report payload, database fingerprint, credentials or physiological bytes enter GitHub. Readback evidence is retained outside commits to avoid self-reference. Remote preservation is only claimed after actual readback.

Watch69 **NOT QUALIFIED**. Watch68 v1 **UNCLASSIFIED / NOT VERIFIED**. Watch58 `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.

## References

[1]: https://www.sqlite.org/lockingv3.html "SQLite hot-journal criteria and rollback locking"
[2]: https://sqlite.org/atomiccommit.html "SQLite atomic commit and zero-length TRUNCATE journal semantics"
