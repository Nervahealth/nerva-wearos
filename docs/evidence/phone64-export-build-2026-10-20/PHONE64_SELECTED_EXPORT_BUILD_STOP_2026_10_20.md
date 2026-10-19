# Phone64 selected export build — pre-submission STOP

## Disposition

**STOPPED before the sole EAS submission.** No Phone64 build job, managed archive, APK, artifact admission, upload, CDN readback, device action, installation, launch, export, collection, or Watch action occurred.

## Exact evidence

The exact authorized staging helper, `P/scripts/stage_phone64.py`, created and committed the disposable stage. Its source-to-stage-to-committed-runtime comparison passed for all **172** staged runtime files; the controlled source stayed clean at `f6cee13bb9d0574910a725113162552a1289d386` / tree `acc112ff454967ce3b103611ea600cdb097d7466`, with exact preservation remote head `542f554984185284407082d20661ae90d9d25092`. Root generated `android/` and `ios/` are absent, and local Expo-module Android sources are present.

The stage contract nevertheless failed before submission: `node_modules` is a symlink, but it is also committed in the disposable stage Git tree as index mode `120000` (blob `5dca6225b2a8780ae2df09f9c682d3e4e066510a`). The helper writes `/node_modules/` before creating that symlink, then runs `git add .`; Git does not exclude the symlink under that directory-only ignore rule. This contradicts the required **ignored/excluded node_modules symlink** upload custody condition.

## Preserved candidate facts

| Field | Value |
|---|---|
| Package | `health.hugr.app` |
| Version | `64` / `2.0.64-selected-run-private-export-candidate` |
| Managed project | `d0c3af97-5a78-4ba1-bc8b-8a21f169ce44` |
| Expected public signer | `a1780089becaf58563daa5dfa16c5626928a13ce56a7dc232732e64a8df4e811` |
| Disposable stage commit | `0f3801c7911fb33fb07f547b4354d06e91364f48` |
| Disposable stage tree | `7f402b711da151269c6c53b2853c722cc90359d8` |
| Existing EAS auth preflight | PASS, silent only |

## Required disposition

No controlled helper, runtime source, signer, account, project, credentials, cache, or token was modified. The committed stage must not be submitted. Parent review/authority is required before any altered staging path could be considered; this task made no resubmission or repair attempt.
