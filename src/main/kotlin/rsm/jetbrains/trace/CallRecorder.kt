package rsm.jetbrains.trace

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One captured MCP call. Everything is a plain data class so it can be serialized
 * to JSONL for export (Trace tab -> "Export as .jsonl"; see plan §2.11).
 *
 * `callId` is the correlation id documented in E2E_TRACE_VERIFICATION.md; matched
 * against the 12 log-line prefixes in `#rsm.jetbrains` for full end-to-end tracing.
 */
data class CallRecord(
    val callId: String,
    val tool: String,
    val request: JsonObject,
    val response: JsonObject?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val durationMs: Long?,
    val isError: Boolean,
    val runId: String?,
    val errorMessage: String?,
) {
    fun isFinished(): Boolean = finishedAt != null
}

/**
 * Backend-only, append-only call log. Application-service scoped: one recorder
 * per IDE launch. The Trace tab reads via [snapshot] every 500 ms while visible.
 */
class CallRecorder {
    private val records = CopyOnWriteArrayList<CallRecord>()
    private val listeners = CopyOnWriteArrayList<(CallRecord) -> Unit>()

    /** Register a new in-flight call. Returns the assigned callId. */
    fun begin(tool: String, request: JsonObject, runId: String? = null): String {
        val id = UUID.randomUUID().toString()
        val record = CallRecord(
            callId = id,
            tool = tool,
            request = request,
            response = null,
            startedAt = Instant.now(),
            finishedAt = null,
            durationMs = null,
            isError = false,
            runId = runId,
            errorMessage = null,
        )
        records.add(record)
        fireListeners(record)
        return id
    }

    /** Mark a call as finished with its response. */
    fun end(callId: String, response: JsonObject?, isError: Boolean, error: String? = null) {
        val idx = records.indexOfFirst { it.callId == callId }
        if (idx < 0) return
        val prev = records[idx]
        val now = Instant.now()
        val duration = now.toEpochMilli() - prev.startedAt.toEpochMilli()
        val updated = prev.copy(
            response = response,
            finishedAt = now,
            durationMs = duration,
            isError = isError,
            errorMessage = error,
        )
        records[idx] = updated
        fireListeners(updated)
    }

    /** Read-only snapshot of all records so far, most recent last. */
    fun snapshot(): List<CallRecord> = records.toList()

    /** Wipe the log. Called by "Clear history" in the Trace tab. */
    fun clear() { records.clear() }

    /** Records matching a run id, in order. */
    fun byRunId(runId: String): List<CallRecord> = records.filter { it.runId == runId }

    /**
     * Prune finished records older than [olderThanMillis]. Called on IDE start to
     * honor RsmSettingsState.traceRetentionDays.
     */
    fun pruneOlderThan(olderThanMillis: Long) {
        val cutoff = Instant.now().toEpochMilli() - olderThanMillis
        records.removeAll { it.finishedAt != null && it.finishedAt.toEpochMilli() < cutoff }
    }

    fun addListener(l: (CallRecord) -> Unit) { listeners.add(l) }
    fun removeListener(l: (CallRecord) -> Unit) { listeners.remove(l) }

    private fun fireListeners(r: CallRecord) {
        for (l in listeners) {
            try { l(r) } catch (_: Throwable) { /* isolate one bad listener */ }
        }
    }
}
