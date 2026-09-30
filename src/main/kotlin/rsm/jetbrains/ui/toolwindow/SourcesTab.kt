package rsm.jetbrains.ui.toolwindow

import rsm.jetbrains.context.DrawerEntry
import rsm.jetbrains.host.RsmRuntimeService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sources tab — table over ContextDrawer entries, plus toolbar actions.
 * In this shim environment we expose the actions as methods so headless tests
 * can drive them. The real IDE build wraps this in a `JBTable` under a
 * `SimpleToolbarDecorator`.
 */
class SourcesTab(private val svc: RsmRuntimeService) {

    data class Row(
        val sourceId: String,
        val display: String,
        val kind: DrawerEntry.Kind,
        val status: DrawerEntry.ReplayStatus,
        val ageSeconds: Long,
        val sizeBytes: Long,
    )

    fun rows(): List<Row> {
        val now = System.currentTimeMillis()
        return svc.drawer.list().map { e ->
            Row(
                sourceId = e.sourceId,
                display = e.displayName,
                kind = e.kind,
                status = e.lastReplayStatus,
                ageSeconds = (now - e.attachedAt.toEpochMilli()) / 1000,
                sizeBytes = e.sizeBytes,
            )
        }
    }

    /** Register a bare reference (no file staging). Used by the small dialog "Register…". */
    fun registerReference(reference: String) {
        require(reference.isNotBlank())
        svc.register(buildJsonObject { put("reference", reference) })
    }

    /** Replay a row's source_id. Delegates to the runtime service and returns nothing. */
    fun replay(sourceId: String) { svc.replay(sourceId) }

    /** Remove a row. Detach from drawer (moves file to history) then rsm_remove on the wire. */
    fun remove(sourceId: String) {
        try {
            svc.remove(sourceId)
        } finally {
            svc.drawer.detach(sourceId, runId = svc.runs.currentRunId())
        }
    }

    /** Clear-all: remove every entry, one at a time (rsm_remove is idempotent). */
    fun clearAll() {
        for (e in svc.drawer.list()) remove(e.sourceId)
    }
}
