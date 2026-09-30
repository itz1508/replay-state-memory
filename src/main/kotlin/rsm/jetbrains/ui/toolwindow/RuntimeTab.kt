package rsm.jetbrains.ui.toolwindow

import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.host.RsmState

/**
 * Runtime tab — status pill + compact operational strip + Source & Context
 * drawer + Auto Feed toggle + Session sub-panel + Start/Stop/Restart buttons.
 *
 * In this shim compilation environment the tab is a headless data view that
 * exposes what the real Swing/JBUI panel would render. The IDE build swaps
 * this out for a Kotlin UI DSL v2 `panel { ... }`.
 */
class RuntimeTab(private val svc: RsmRuntimeService) {

    /** Snapshot rendered by the header. */
    data class HeaderSnapshot(
        val state: RsmState,
        val pid: Long?,
        val uptimeSeconds: Long,
        val restartsUsed: Int,
        val restartsBudget: Int = 1,
        val memoryTierMb: Int?,
    )

    /** Snapshot rendered by the Session sub-panel. */
    data class SessionSnapshot(
        val sessionId: String,
        val lastRunId: String?,
        val contextBytes: Long,
        val activeSources: Int,
        val replays: Int,
    )

    private val startedAtMillis: Long = System.currentTimeMillis()
    private val sessionId: String = "sess-" + java.util.UUID.randomUUID().toString().take(8)
    private var replays: Int = 0

    init {
        svc.addStateListener { _, cur ->
            if (cur == RsmState.ACTIVE) replays++
        }
    }

    fun header(): HeaderSnapshot {
        val pid = svc.pid()
        val uptimeSec =
            if (pid == null) 0L
            else (System.currentTimeMillis() - startedAtMillis) / 1000
        return HeaderSnapshot(
            state = svc.state(),
            pid = pid,
            uptimeSeconds = uptimeSec,
            restartsUsed = svc.restartsUsed(),
            memoryTierMb = null,   // filled by SettingsTab in real Swing panel
        )
    }

    fun session(): SessionSnapshot = SessionSnapshot(
        sessionId = sessionId,
        lastRunId = svc.runs.lastRun()?.runId,
        contextBytes = svc.drawer.contextBytes(),
        activeSources = svc.drawer.size(),
        replays = replays,
    )

    fun drawerEntries() = svc.drawer.list()

    /** Text summary line shown in the compact status strip. */
    fun statusLine(): String {
        val h = header()
        val parts = mutableListOf("● ${h.state.name}")
        if (h.pid != null)   parts += "pid ${h.pid}"
        parts += "uptime ${fmtDuration(h.uptimeSeconds)}"
        parts += "restarts ${h.restartsUsed}/${h.restartsBudget}"
        return parts.joinToString("  ·  ")
    }

    private fun fmtDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
