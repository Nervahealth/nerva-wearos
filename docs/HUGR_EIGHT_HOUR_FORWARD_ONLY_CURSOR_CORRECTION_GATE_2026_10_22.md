# Eight-hour terminal cursor correction — validation gate

## Captured cause and measurement

One necessary replay of the exact existing synthetic eight-hour writer fixture captured `failureReason` and `custodyFailureCode` as `DURATION_OR_CANCELLATION_BUDGET_EXCEEDED`. The original failure and its exact quiescent state are preserved. Canonical custody bytes are still present with their external hash; a failed qualification report's MISSING status is not evidence of deletion.

No second ingestion was needed for pass measurement. The existing test-only private-pass measurement was run on a read-only disposable copy of the exact retained writer state (967,830 records, 78,936,626 canonical bytes, 478 manifests). One measurement JUnit test passed, 409.676 seconds; this is not terminal admission. The unchanged cumulative 180-second deadline failed during file-offset coverage after 898,123 rows. Initial metadata/hash took 1.157 s; plans 0.023 s; index+manifests 57.706 s (55.278 s cursor); isolated offset coverage 141.755 s (139.532 s cursor); isolated stream/sequence/gaps 67.617 s (66.430 s cursor); isolated final hash 0.322 s. The isolated stages are diagnostic only and do not widen a production attempt.

## Narrow correction

The existing selected cursor exposes only `next()`, and all three core passes traverse forward. Android 28's pinned public SQLiteCursor implementation normally refills a backward-overlapping window. `setFillWindowForwardOnly(true)`, after successful cursor-window adoption and before first movement, changes only refill placement. It avoids filling rows already consumed on a one-way traversal. This is a measured-bottleneck correction with an independently reviewed API mechanism, not a claim that all query/sort work is removed or that the complete workflow now fits.

The fixed 8 MiB window, exact selected-session SQL projection/predicate/orders, malformed/type/null checks, per-row SHA/CRC/header proof in both byte-owning passes, manifests, file coverage, sequence/gaps, stream highest-state comparisons, initial/final hashing and all production/JUnit/phase budgets remain unchanged. API <28 and generic cursors retain their original behavior. Failure after window adoption closes only the owning cursor; pre-adoption failure closes the caller-owned window. No silent default-refill fallback is introduced.

Existing multi-refill, permuted-order, foreign-session, malformed-projection and EOF/closure equivalence tests are retained and strengthened to require the forward-only flag; a generic-cursor ownership test is added. Native affected tests and production terminal/report/restart/offline/export/independent-reader validation remain PENDING. Source contracts: 163 passed, zero failed, three explicit fixture-dependent skips. The initial command using absent tsx executed zero tests and is retained as a setup failure; the existing dependency-free native Node test route earned the result.

## Retained failure remains history

The original failed COMPLETED receipt deliberately cannot be silently promoted via `NO_CUSTODY_FAILURE_CANNOT_BECOME_VERIFIED`. This gate is unchanged and is not being diagnosed as a defect. Any affected terminal test over retained writer bytes must use an explicitly labelled fresh disposable synthetic report envelope, not erase or rewrite the failed report history. Both original failed state and all lower-rung passes remain immutable. Such reconstruction would compose existing writer/ACK proof with new terminal/report/export evidence; it is not a fresh ingestion run and must not be described as one.

No candidate build is yet admitted or submitted. No installation, physical recording, real-data operation, IDC change, Samsung provider contact, cutoff/drain change, receipt-export extension or native-adjacency/RMSSD/baseline/learning/Loop A/N1 qualification. Samsung's sequence and final-batch questions remain separate. T2.2, accepted adapter/companion, October reconciliation, S4 and five-minute physical evidence unchanged.

Receipt first → Master → Alignment last → bounded commits → both-repository write preflight → reviewed controlled non-force mirror → independent exact remote head/tree/blob readback. No affected private proposal. Source preservation is not canonical recording backup.

Watch69 NOT QUALIFIED; Watch68 v1 UNCLASSIFIED / NOT VERIFIED; Watch58 TERMINAL QUALIFIED BY APP / EXACT ARTIFACT NOT YET INDEPENDENTLY VERIFIED.

END_OF_FORWARD_ONLY_CURSOR_GATE
