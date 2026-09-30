package rsm.jetbrains.context

import rsm.jetbrains.host.RsmState
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * IDE-side abstraction: aggregates consecutive rsm_replay calls into a "run".
 * The RSM/host/agent stack has no run concept (see plan §1.4) — this class is
 * pure IDE bookkeeping, never sent over MCP.
 *
 * Boundary rules (plan §2.9):
 *   - A run STARTS when RsmState transitions READY → ACTIVE.
 *   - A run ENDS   when state returns to READY and stays for >= idleGraceMillis
 *     with no new ACTIVE transition.
 *
 * The listener API lets the tool window and Auto Feed react to run start / end
 * without polling.
 */
class RunTracker(
    private val idleGraceMillis: Long = 5_000L,
    private val nowSupplier: () -> Long = { System.currentTimeMillis() },
) {
    data class RunRecord(
        val runId: String,
        val startedAt: Instant,
        var endedAt: Instant?,
        var callCount: Int,
    ) {
        fun isActive(): Boolean = endedAt == null
    }

    private val runs = CopyOnWriteArrayList<RunRecord>()
    private val currentRun = AtomicReference<RunRecord?>(null)
    private val idleBoundaryAt = AtomicReference<Long?>(null)
    private val listeners = CopyOnWriteArrayList<Listener>()

    interface Listener {
        fun onRunStart(run: RunRecord) {}
        fun onRunEnd(run: RunRecord) {}
    }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    /**
     * Feed a state transition. Called from RsmRuntimeService whenever its state
     * changes so the tracker can update run boundaries.
     */
    fun onStateChange(previous: RsmState, current: RsmState) {
        if (previous != RsmState.ACTIVE && current == RsmState.ACTIVE) {
            // Enter ACTIVE — start a new run if none open, otherwise continue current.
            if (currentRun.get() == null) {
                val r = RunRecord(
                    runId = "run-${nowSupplier() / 1000}-${runs.size + 1}",
                    startedAt = Instant.now(),
                    endedAt = null,
                    callCount = 0,
                )
                runs.add(r)
                currentRun.set(r)
                idleBoundaryAt.set(null)
                fireStart(r)
            } else {
                // Extra ACTIVE within an open run: cancel any pending idle boundary.
                idleBoundaryAt.set(null)
            }
        } else if (previous == RsmState.ACTIVE && current == RsmState.READY) {
            // Leaving ACTIVE — arm idle countdown.
            idleBoundaryAt.set(nowSupplier() + idleGraceMillis)
        }
        // Any transition into a non-READY error terminates the run immediately.
        if (previous == RsmState.ACTIVE && (current == RsmState.ERROR || current == RsmState.UNAVAILABLE)) {
            endCurrentIfAny()
        }
    }

    /** Ping — called by the tool window's 1-Hz ticker to close idle-elapsed runs. */
    fun tick() {
        val boundary = idleBoundaryAt.get() ?: return
        if (nowSupplier() >= boundary) endCurrentIfAny()
    }

    /** Increment the current run's replay counter. */
    fun onCallCompleted() {
        currentRun.get()?.callCount = (currentRun.get()?.callCount ?: 0) + 1
    }

    fun currentRunId(): String? = currentRun.get()?.runId
    fun lastRun(): RunRecord? = runs.lastOrNull { !it.isActive() } ?: runs.lastOrNull()
    fun all(): List<RunRecord> = runs.toList()

    private fun endCurrentIfAny() {
        val r = currentRun.getAndSet(null) ?: return
        r.endedAt = Instant.now()
        idleBoundaryAt.set(null)
        fireEnd(r)
    }

    private fun fireStart(r: RunRecord) {
        for (l in listeners) try { l.onRunStart(r) } catch (_: Throwable) { }
    }
    private fun fireEnd(r: RunRecord) {
        for (l in listeners) try { l.onRunEnd(r) } catch (_: Throwable) { }
    }
}
