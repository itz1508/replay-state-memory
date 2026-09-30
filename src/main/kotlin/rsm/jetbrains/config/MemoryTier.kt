package rsm.jetbrains.config

import java.lang.management.ManagementFactory

/**
 * Node --max-old-space-size tier. Values in MB.
 *
 * Frozen tiers (see PLANS/RSM_JETBRAINS_0_2_0_PLAN.md §2.7):
 *   AUTO     — derived from com.sun.management.OperatingSystemMXBean.getTotalMemorySize()
 *   MB_256   — for RAM ≤ 8 GiB
 *   MB_512   — for RAM in 8..24 GiB
 *   MB_1024  — for RAM ≥ 24 GiB (hard cap so the sidecar never exceeds 1 GiB)
 */
enum class MemoryTier(val megabytes: Int) {
    AUTO(-1),
    MB_256(256),
    MB_512(512),
    MB_1024(1024);

    /** Resolves AUTO to a concrete tier based on total physical memory. Others map to themselves. */
    fun resolve(): MemoryTier =
        if (this != AUTO) this else fromTotalBytes(totalPhysicalMemoryBytes())

    companion object {
        /** Byte thresholds. 1 GiB = 1024^3 bytes. */
        private const val GIB_8:  Long = 8L  * 1024L * 1024L * 1024L
        private const val GIB_24: Long = 24L * 1024L * 1024L * 1024L

        @JvmStatic
        fun fromTotalBytes(total: Long): MemoryTier = when {
            total <= 0L    -> MB_512            // conservative fallback when unknown
            total <= GIB_8 -> MB_256
            total < GIB_24 -> MB_512
            else           -> MB_1024
        }

        @JvmStatic
        fun totalPhysicalMemoryBytes(): Long = try {
            val bean = ManagementFactory.getOperatingSystemMXBean()
            // com.sun.management.OperatingSystemMXBean exposes getTotalMemorySize() on JDK 17+;
            // older getTotalPhysicalMemorySize() also exists on Oracle/Adoptium. Reflect defensively.
            val method = bean.javaClass.methods
                .firstOrNull { m ->
                    (m.name == "getTotalMemorySize" || m.name == "getTotalPhysicalMemorySize")
                        && m.parameterCount == 0
                        && m.returnType == java.lang.Long.TYPE
                }
            (method?.invoke(bean) as? Long) ?: -1L
        } catch (_: Throwable) {
            -1L
        }

        /**
         * Parse a user-facing tier string (case-insensitive). Accepts `"auto"`, `"256mb"`,
         * `"512mb"`, `"1gb"` / `"1024mb"`. Anything else falls back to AUTO.
         */
        @JvmStatic
        fun parse(s: String?): MemoryTier = when (s?.trim()?.lowercase()) {
            null, "", "auto" -> AUTO
            "256mb", "256"   -> MB_256
            "512mb", "512"   -> MB_512
            "1gb", "1024mb", "1024" -> MB_1024
            else             -> AUTO
        }
    }
}
