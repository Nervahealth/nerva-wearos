package com.hugr.wearos

import java.io.File

/**
 * Names the isolated storage scope for the next ordinary recording line.
 *
 * This scope is deliberately distinct from the legacy retained journal and from
 * the frozen completed-package root. It owns only new ordinary-run records and
 * their bounded causal metadata. It never discovers, opens, traverses, hashes,
 * migrates, acknowledges, prunes, or deletes either legacy root.
 */
internal object FreshOrdinaryRunScope {
    // v1 may contain the unclassified Watch68 ordinary session. The bounded
    // Watch69 candidate never reopens it; it owns a new, versioned scope.
    const val JOURNAL_DIRECTORY = "fresh_ordinary_source_journal_v2"
    const val CAUSAL_DIRECTORY = "fresh_ordinary_causal_flight_recorder_v2"

    fun journalRoot(filesDir: File): File = File(filesDir, JOURNAL_DIRECTORY)

    fun causalRoot(filesDir: File): File = File(filesDir, CAUSAL_DIRECTORY)
}
