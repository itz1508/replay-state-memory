package rsm.jetbrains

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import rsm.jetbrains.trace.CallRecorder

class CallRecorderTest {

    @Test
    fun `begin returns a fresh callId and records an in-flight entry`() {
        val r = CallRecorder()
        val id = r.begin("rsm_replay", buildJsonObject { put("source_id", "greeting.hello") })
        val snap = r.snapshot()
        assertEquals(1, snap.size)
        assertEquals(id, snap[0].callId)
        assertEquals("rsm_replay", snap[0].tool)
        assertNull(snap[0].finishedAt)
    }

    @Test
    fun `end sets finishedAt and duration`() {
        val r = CallRecorder()
        val id = r.begin("rsm_register", buildJsonObject { put("reference", "x") })
        Thread.sleep(2)
        r.end(id, response = buildJsonObject { put("result", buildJsonObject { }) }, isError = false)
        val rec = r.snapshot()[0]
        assertNotNull(rec.finishedAt)
        assertTrue((rec.durationMs ?: -1) >= 0)
    }

    @Test
    fun `records tagged with same runId are grouped`() {
        val r = CallRecorder()
        val idA = r.begin("rsm_replay", buildJsonObject { }, runId = "run-A")
        r.end(idA, buildJsonObject { }, false)
        val idB = r.begin("rsm_replay", buildJsonObject { }, runId = "run-A")
        r.end(idB, buildJsonObject { }, true)
        val idC = r.begin("rsm_replay", buildJsonObject { }, runId = "run-B")
        r.end(idC, buildJsonObject { }, false)
        assertEquals(2, r.byRunId("run-A").size)
        assertEquals(1, r.byRunId("run-B").size)
    }

    @Test
    fun `clear removes all records`() {
        val r = CallRecorder()
        r.begin("rsm_remove", buildJsonObject { })
        r.clear()
        assertTrue(r.snapshot().isEmpty())
    }
}
