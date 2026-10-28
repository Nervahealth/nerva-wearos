# HUGR GitHub write access restored — 28 October 2026

**The 403 repository-write blocker is resolved.** The protected HUGR GitHub Preservation API credential authenticated as the existing **Nervahealth** account. Actual Git-blob writes returned HTTP 201 on both `Nervahealth/nerva-wearos` and `Nervahealth/nerva-mobile`. This is real write verification, not an inference from read access or repository permission flags.

The prior connection returned `Resource not accessible by integration` on the Watch repository. That failure remains recorded. No token value was displayed, copied into repository source, or placed in the clinician app. The protected API route avoids the missing temporary write-login file and the task-injected `GH_TOKEN` precedence problem. Future preservation should use the controlled mirror or its reviewed protected-transport successor, with the same bounded branch scope and both-repository preflight.

## Held preservation completed

The already-created local commits were resumed without duplication, in source-owner order and with Master then Alignment last. Independent API readback reconstructed the remote root-tree bytes and verified every allowed blob's exact bytes, byte count, identity and SHA-256.

The held reset STOP and surviving source evidence were preserved at these remote heads:

- Watch: `26a8ad5c5ea1acd98baba13508153dc5e6dd0786`, tree `50626f7a11dec38712e3e484eeb13700eb6c96de`.
- Phone: `95ace5b92dcd7b658d4df718c20dc3195c67dbf1`, tree `19d56103cf113329549941859a61ff1dbae9024b`.
- Controls: `b0b81fbd4a6838cd80d976e08a625d46756fd077`, tree `e2153fdff9c973079ca64fb5ac47d420fd24603e`.

The exact held-phase proof is `RESET_STOP_EXACT_REMOTE_READBACK.json`. This repair receipt and its sanitized tooling evidence are an additive documentary closeout; its later final branch proof is `GITHUB_WRITE_REPAIR_FINAL_REMOTE_READBACK.json`. Neither changes the earlier STOP or fallback.

The protected transport reuses the hash-pinned controlled mirror's planning, linear-history, unique-tree and non-force compare-and-readback safeguards. Its **14 offline adapter tests passed**, and an independent source-only safety review found no material issue. These are preservation-tool tests, not native-observation application tests.

## What remains

The missing final native-hook/recorder tests and corrected recorder bytes were not recreated by restoring GitHub access. Surviving unfinished native sources are preserved separately as **unverified**, not integrated runtime code. The next authorized engineering work is the separately identified post-reset Phone-only source/test continuation: reconstruct missing tests, establish exact-version native generation and hook/recorder binding, then verify the bounded native-to-durable publication and isolated Save/Share path.

No build, installation, device action, reconnect, recording, private-record publication, force push or main merge occurred in this repair. The five-minute physical custody PASS remains unchanged; the latest Watch85/Phone69 30-minute attempt remains **STOP / NOT PASSED**. Watch69 remains `NOT QUALIFIED`; Watch68 v1 remains `UNCLASSIFIED / NOT VERIFIED`; Watch58 remains `TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED`.
