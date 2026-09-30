package rsm.jetbrains.ui.toolwindow

import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.trace.CallRecord
import java.io.File
import java.io.PrintWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Trace tab — history over CallRecorder, plus request/response/log viewers.
 * See plan §2.11 — this is the surface that operationalizes the E2E trace
 * verification criterion.
 */
class TraceTab(private val svc: RsmRuntimeService) {

    fun records(): List<CallRecord> = svc.recorder.snapshot()

    fun clear() = svc.recorder.clear()

    /**
     * Export the current history as a JSONL file under RSM_HOME/exports/traces/.
     * Returns the absolute path written.
     */
    fun exportJsonl(target: File): File {
        target.parentFile?.mkdirs()
        val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
        PrintWriter(target).use { out ->
            for (r in svc.recorder.snapshot()) {
                val row: JsonObject = buildJsonObject {
                    put("callId",       r.callId)
                    put("tool",         r.tool)
                    put("startedAt",    r.startedAt.toString())
                    put("finishedAt",   r.finishedAt?.toString() ?: "")
                    put("durationMs",   r.durationMs ?: -1L)
                    put("isError",      r.isError)
                    put("runId",        r.runId ?: "")
                    if (r.errorMessage != null) put("errorMessage", r.errorMessage)
                }
                out.println(json.encodeToString(JsonObject.serializer(), row))
            }
        }
        return target.absoluteFile
    }

    /** Callers use this to lay out the copy-correlation-id right-click menu. */
    fun copyCorrelationId(record: CallRecord): String = "rsm.callId=${record.callId}"
}
