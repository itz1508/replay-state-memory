package rsm.jetbrains

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import rsm.jetbrains.context.RunTracker
import rsm.jetbrains.host.RsmState

class RunTrackerTest {

    @Test
    fun `READY to ACTIVE starts a run — return to READY plus idle closes it`() {
        var virtualNow = 1_000L
        val tracker = RunTracker(idleGraceMillis = 500L, nowSupplier = { virtualNow })
        tracker.onStateChange(RsmState.READY, RsmState.ACTIVE)
        assertNotNull(tracker.currentRunId())
        tracker.onStateChange(RsmState.ACTIVE, RsmState.READY)
        virtualNow += 501L
        tracker.tick()
        assertNull(tracker.currentRunId())
    }

    @Test
    fun `re-enter ACTIVE cancels the pending idle boundary`() {
        var t = 0L
        val tracker = RunTracker(idleGraceMillis = 500L, nowSupplier = { t })
        tracker.onStateChange(RsmState.READY, RsmState.ACTIVE)
        val runId = tracker.currentRunId()
        tracker.onStateChange(RsmState.ACTIVE, RsmState.READY)
        // Bumped active before the idle window elapses
        tracker.onStateChange(RsmState.READY, RsmState.ACTIVE)
        t += 700L
        tracker.tick()
        assertEquals(runId, tracker.currentRunId())
    }

    @Test
    fun `ERROR during ACTIVE terminates the run immediately`() {
        val tracker = RunTracker(idleGraceMillis = 500L)
        tracker.onStateChange(RsmState.READY, RsmState.ACTIVE)
        assertNotNull(tracker.currentRunId())
        tracker.onStateChange(RsmState.ACTIVE, RsmState.ERROR)
        assertNull(tracker.currentRunId())
    }
}
