package rsm.jetbrains.host

import rsm.jetbrains.platform.GeneralCommandLine
import rsm.jetbrains.platform.ParentEnvironmentType
import java.io.File

/**
 * Spawns the bundled Node sidecar with a HERMETIC environment.
 *
 * Invariants:
 *   - Uses GeneralCommandLine with withParentEnvironmentType(NONE).
 *   - The exe path is an ABSOLUTE File already validated by NodeRuntimeResolver.
 *   - No PATH inheritance.
 *
 * v0.2.0: additive `nodeArgs` parameter for Node-side flags placed BEFORE the
 * agent entry (e.g. `--max-old-space-size=512`). Callers who don't need it can
 * omit — the default is an empty list, preserving the 0.1.0 argv shape exactly.
 */
class ProcessLauncher(
    private val nodeBinary: File,
    private val agentEntry: File,
    private val agentArgs: List<String> = emptyList(),
    private val nodeArgs: List<String>  = emptyList(),
    private val workDir: File? = null,
) {
    init {
        require(nodeBinary.isAbsolute) { "nodeBinary must be absolute: $nodeBinary" }
        require(agentEntry.isAbsolute) { "agentEntry must be absolute: $agentEntry" }
    }

    fun buildCommandLine(): GeneralCommandLine {
        val allParams = mutableListOf<String>()
        allParams.addAll(nodeArgs)
        allParams.add(agentEntry.absolutePath)
        allParams.addAll(agentArgs)
        return GeneralCommandLine()
            .withExePath(nodeBinary.absolutePath)
            .withParameters(*allParams.toTypedArray())
            .withParentEnvironmentType(ParentEnvironmentType.NONE)
            .withWorkDirectory(workDir)
    }

    fun spawn(): Process = buildCommandLine().createProcess()
}
