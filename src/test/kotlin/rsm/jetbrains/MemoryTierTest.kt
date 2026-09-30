package rsm.jetbrains

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import rsm.jetbrains.config.MemoryTier

class MemoryTierTest {

    @Test
    fun `tiers below or equal to 8 GiB → 256 MB`() {
        val gib = 1024L * 1024L * 1024L
        assertEquals(MemoryTier.MB_256, MemoryTier.fromTotalBytes(4 * gib))
        assertEquals(MemoryTier.MB_256, MemoryTier.fromTotalBytes(8 * gib))
    }

    @Test
    fun `tiers between 8 and 24 GiB → 512 MB`() {
        val gib = 1024L * 1024L * 1024L
        assertEquals(MemoryTier.MB_512, MemoryTier.fromTotalBytes(9 * gib))
        assertEquals(MemoryTier.MB_512, MemoryTier.fromTotalBytes(16 * gib))
    }

    @Test
    fun `tiers 24 GiB and above → 1 GB hard cap`() {
        val gib = 1024L * 1024L * 1024L
        assertEquals(MemoryTier.MB_1024, MemoryTier.fromTotalBytes(24 * gib))
        assertEquals(MemoryTier.MB_1024, MemoryTier.fromTotalBytes(64 * gib))
        assertEquals(MemoryTier.MB_1024, MemoryTier.fromTotalBytes(256 * gib))
    }

    @Test
    fun `parse accepts auto and canonical strings`() {
        assertEquals(MemoryTier.AUTO,   MemoryTier.parse(null))
        assertEquals(MemoryTier.AUTO,   MemoryTier.parse(""))
        assertEquals(MemoryTier.AUTO,   MemoryTier.parse("auto"))
        assertEquals(MemoryTier.MB_256, MemoryTier.parse("256mb"))
        assertEquals(MemoryTier.MB_512, MemoryTier.parse("512mb"))
        assertEquals(MemoryTier.MB_1024, MemoryTier.parse("1gb"))
        assertEquals(MemoryTier.MB_1024, MemoryTier.parse("1024mb"))
    }

    @Test
    fun `unknown fallback tier is AUTO`() {
        assertEquals(MemoryTier.AUTO, MemoryTier.parse("weird-string"))
    }

    @Test
    fun `resolve concretizes AUTO on current machine`() {
        val t = MemoryTier.AUTO.resolve()
        assertTrue(t == MemoryTier.MB_256 || t == MemoryTier.MB_512 || t == MemoryTier.MB_1024)
    }
}
