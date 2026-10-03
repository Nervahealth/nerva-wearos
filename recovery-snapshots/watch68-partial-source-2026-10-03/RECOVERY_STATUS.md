# Watch68 Partial Source Preservation Snapshot

This is an **as-is source snapshot**, preserved before further reconciliation. It is not a test-passing or buildable branch.

- Origin: `/home/ubuntu/HUGR_WATCH52W_EXACT_PARENT_FIRST_FRAME_2026_09_16/worktree`
- Baseline: `origin/main` at `0e1c10808635d6a1ac3fb25ff58451e0521bf805`
- Excluded: `.gradle/`, `build/`, `local.properties`, APKs, credentials, keystores, device/private data.
- Known gap: `app/src/main/java/com/hugr/wearos/ExactRangeReadback.kt` is absent. The ordinary-runtime contract requires historical SHA-256 `5464fac5cd399fa1627071f139e0724a8135bff5d40a34e9d34fb994c4448ca6`; no substitute has been created.
- `RetainedTimingExporter.kt` and `RetainedTimingExportActivity.kt` are retained exactly as found in the source mirror.
- No test result is claimed.
