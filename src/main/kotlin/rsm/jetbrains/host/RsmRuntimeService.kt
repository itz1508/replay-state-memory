package rsm.jetbrains.host

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rsm.jetbrains.config.MemoryTier
import rsm.jetbrains.config.RsmHome
import rsm.jetbrains.config.RsmSettingsState
import rsm.jetbrains.context.ContextDrawer
import rsm.jetbrains.context.RunTracker
import rsm.jetbrains.logging.RsmLogger
import rsm.jetbrains.mcp.McpStdioClient
import rsm.jetbrains.trace.CallRecorder
import rsm.jetbrains.ui.RsmNotifications
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * The frozen 5-state runtime lifecycle for the RSM sidecar.
 *
 * OFF          — service constructed, no process started (default).
 * READY        — process spawned, MCP initialize handshake succeeded.
 * ACTIVE       — at least one MCP call has completed.
 * UNAVAILABLE  — sidecar reported an operational miss (e.g. permission denied,
 *                source-store miss) but the transport is alive.
 * ERROR        — runtime spawn failed, transport died, or an unrecoverable
 *                condition was observed. Auto-restart is attempted AT MOST
 *                ONCE per session before landing here permanently.
 */
enum class RsmState { OFF, READY, ACTIVE, UNAVAILABLE, ERROR }

/**
 * Backend-only application service. Runs on the JetBrains IDE backend in every
 * deployment mode (local, Gateway, Split Mode); never on the thin client.
 *
 * v0.2.0: still owns the 5-state machine + at-most-one auto-restart. Now also
 * owns the CallRecorder (for the Trace tab), the ContextDrawer (for the Sources
 * tab), and the RunTracker (for Auto Feed run boundaries). All additions are
 * pure IDE-side abstractions — no wire-protocol change.
 *
 * The Gradle build binds this class through plugin.xml as
 *   <applicationService serviceImplementation="rsm.jetbrains.host.RsmRuntimeService"/>
 */
class RsmRuntimeService {
    private val logger = RsmLogger.get()
    private val stateRef = AtomicReference(RsmState.OFF)
    private var process: Process? = null
    private var client:  McpStdioClient? = null
    private var restartCount: Int = 0
    private val maxRestartsPerSession = 1
    private val stateListeners = CopyOnWriteArrayList<(RsmState, RsmState) -> Unit>()

    /** Companion collaborators — kept on the application-service instance. */
    val recorder: CallRecorder = CallRecorder()
    val drawer:   ContextDrawer = ContextDrawer()
    val runs:     RunTracker    = RunTracker()

    init {
        // Wire the run tracker to state changes.
        addStateListener { prev, cur -> runs.onStateChange(prev, cur) }
    }

    fun state(): RsmState = stateRef.get()

    /**
     * Subscribe to state transitions. Returns nothing — remove via
     * [removeStateListener]. Listener may not throw; exceptions are swallowed.
     */
    fun addStateListener(listener: (RsmState, RsmState) -> Unit) { stateListeners.add(listener) }
    fun removeStateListener(listener: (RsmState, RsmState) -> Unit) { stateListeners.remove(listener) }

    /** PID of the child Node process, or null if none. */
    fun pid(): Long? = process?.takeIf { it.isAlive }?.pid()

    /** Auto-restart budget used this session (0 or 1). */
    fun restartsUsed(): Int = restartCount

    // ---- New: settings-driven startup ---------------------------------------

    /**
     * Resolve the bundled rsm-agent entry point from PathManager.getPluginsPath().
     * Mirrors NodeRuntimeResolver's convention.
     */
    fun defaultAgentEntry(): File =
        File(rsm.jetbrains.platform.PathManager.getPluginsPath())
            .resolve("rsm-jetbrains").resolve("rsm-agent").resolve("dist").resolve("agent.js")
            .absoluteFile

    /**
     * Compute the argv passed to `rsm-agent` from the settings snapshot.
     * See plan §2.6 for the exact contract.
     */
    fun agentArgsFromSettings(settings: RsmSettingsState.Data): List<String> {
        val out = mutableListOf<String>()
        val spec = settings.sourceStoreSpec.ifBlank {
            "fs-store:" + RsmHome.sourcesDir().absolutePath
        }
        out += "--source-store"
        out += spec
        if (settings.allowAll) {
            out += "--allow-all"
        } else {
            for (a in settings.allowList) {
                if (a.isNotBlank()) {
                    out += "--allow"
                    out += a
                }
            }
        }
        if (settings.logLevel.equals("DEBUG", ignoreCase = true)) out += "--verbose"
        return out
    }

    /**
     * Compute the argv passed to `node` BEFORE the agent entry, from the settings
     * snapshot. Currently just the memory tier flag (see plan §2.7).
     */
    fun nodeArgsFromSettings(settings: RsmSettingsState.Data): List<String> {
        val tier = MemoryTier.parse(settings.memoryTier).resolve()
        return listOf("--max-old-space-size=${tier.megabytes}")
    }

    // ---- 5-state lifecycle (unchanged externally) ---------------------------

    /** Lazy startup entry point (called by any user-facing action). */
    @Synchronized
    fun startFor(agentEntry: File) {
        startFor(agentEntry, emptyList(), emptyList())
    }

    /**
     * Extended startup with explicit Node args (pre-entry) and rsm-agent args
     * (post-entry). Callers typically get these from [nodeArgsFromSettings] and
     * [agentArgsFromSettings].
     */
    @Synchronized
    fun startFor(agentEntry: File, nodeArgs: List<String>, agentArgs: List<String>) {
        if (stateRef.get() != RsmState.OFF && stateRef.get() != RsmState.ERROR) return
        try {
            val node = NodeRuntimeResolver.resolve()
            val proc = ProcessLauncher(
                nodeBinary = node,
                agentEntry = agentEntry,
                agentArgs  = agentArgs,
                nodeArgs   = nodeArgs,
            ).spawn()
            process = proc
            val c = McpStdioClient.forProcess(proc)
            c.initialize()
            val tools = c.listTools()
            require(tools.toSet().containsAll(McpStdioClient.ALLOWED_TOOLS)) {
                "rsm-agent did not advertise all expected tools: $tools"
            }
            client = c
            transitionTo(RsmState.READY)
            logger.info("RSM sidecar READY (pid=${proc.pid()})")
        } catch (u: RsmRuntimeUnavailable) {
            transitionTo(RsmState.ERROR)
            RsmNotifications.error("RSM runtime unavailable", u.message ?: "missing binary")
            logger.error("resolve failed", u)
        } catch (t: Throwable) {
            transitionTo(RsmState.ERROR)
            RsmNotifications.error("RSM runtime failed to start", t.javaClass.simpleName + ": " + t.message)
            logger.error("startup failed", t)
        }
    }

    /**
     * At-most-one automatic restart per session. Subsequent failures stay in ERROR;
     * the user must invoke rsm.restartRuntime explicitly.
     */
    @Synchronized
    fun autoRestartOnce(agentEntry: File): Boolean {
        if (restartCount >= maxRestartsPerSession) return false
        restartCount++
        stopInternal()
        transitionTo(RsmState.OFF)
        startFor(agentEntry)
        return true
    }

    /** Manual restart, invoked by RestartRuntimeAction. Resets the auto-restart budget. */
    @Synchronized
    fun restart(agentEntry: File) {
        stopInternal()
        restartCount = 0
        transitionTo(RsmState.OFF)
        startFor(agentEntry)
    }

    /** Manual restart with settings-derived args. */
    @Synchronized
    fun restart(agentEntry: File, nodeArgs: List<String>, agentArgs: List<String>) {
        stopInternal()
        restartCount = 0
        transitionTo(RsmState.OFF)
        startFor(agentEntry, nodeArgs, agentArgs)
    }

    // ---- MCP tool calls with trace recording --------------------------------

    fun register(reference: JsonObject): JsonObject = call("rsm_register", reference)
    fun replay(sourceId: String):        JsonObject = call("rsm_replay",   buildJsonObject { put("source_id", sourceId) })
    fun remove(sourceId: String):        JsonObject = call("rsm_remove",   buildJsonObject { put("source_id", sourceId) })

    private fun call(tool: String, args: JsonObject): JsonObject {
        val c = client ?: error("RSM sidecar is not started (state=${state()})")
        val callId = recorder.begin(tool = tool, request = args, runId = runs.currentRunId())
        return try {
            val res = c.callTool(tool, args)
            val result = res["result"]
            val isError = result?.let { (it as? JsonObject)?.get("isError")?.toString() == "true" } ?: false
            transitionTo(if (state() == RsmState.READY) RsmState.ACTIVE else state())
            if (isError) transitionTo(RsmState.UNAVAILABLE)
            recorder.end(callId, response = res, isError = isError)
            runs.onCallCompleted()
            // Update drawer with replay outcome
            if (tool == "rsm_replay") {
                val sid = args["source_id"]?.toString()?.trim('"')
                if (sid != null) drawer.onReplayResult(sid, ok = !isError)
            }
            res
        } catch (t: Throwable) {
            recorder.end(callId, response = null, isError = true, error = t.message)
            throw t
        }
    }

    @Synchronized
    fun stop() = stopInternal()

    private fun stopInternal() {
        val p = process ?: return
        try {
            if (p.isAlive) {
                p.destroy()                          // SIGTERM
                val exited = p.waitFor(2_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!exited) p.destroyForcibly()     // SIGKILL after 2s
            }
        } catch (t: Throwable) {
            logger.warn("stop error: ${t.message}")
        } finally {
            process = null
            client  = null
            transitionTo(RsmState.OFF)
        }
    }

    // ---- Internal helpers ---------------------------------------------------

    private fun transitionTo(next: RsmState) {
        val prev = stateRef.getAndSet(next)
        if (prev != next) fireStateChange(prev, next)
    }

    private fun fireStateChange(prev: RsmState, next: RsmState) {
        for (l in stateListeners) {
            try { l(prev, next) } catch (_: Throwable) { /* one bad listener must not brick the state machine */ }
        }
    }
}
