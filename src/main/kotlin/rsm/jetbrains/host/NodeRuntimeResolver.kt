package rsm.jetbrains.host

import rsm.jetbrains.platform.CpuArch
import rsm.jetbrains.platform.PathManager
import rsm.jetbrains.platform.SystemInfo
import java.io.File
import java.nio.file.Files

/**
 * Resolves the ABSOLUTE path of the bundled Node 22 sidecar for the current target.
 *
 * Security invariants (frozen):
 *   - Absolute resolved path only.
 *   - NEVER inspects PATH.
 *   - NEVER executes 'which node' or 'where node'.
 *   - NEVER accepts a user-supplied runtime path.
 *   - NEVER downloads at runtime.
 *   - NEVER substitutes another architecture / OS.
 *   - Fails closed (throws RsmRuntimeUnavailable) if the expected binary is missing.
 */
class RsmRuntimeUnavailable(message: String) : RuntimeException(message)

enum class NativeTarget(val id: String, val nodeBinary: String) {
    WINDOWS_X64("windows-x64", "node.exe"),
    MACOS_X64("macos-x64", "node"),
    MACOS_ARM64("macos-arm64", "node"),
    LINUX_X64("linux-x64", "node");

    companion object {
        fun forCurrentHost(): NativeTarget = when {
            SystemInfo.isWindows && CpuArch.CURRENT == CpuArch.X86_64 -> WINDOWS_X64
            SystemInfo.isMac     && CpuArch.CURRENT == CpuArch.X86_64 -> MACOS_X64
            SystemInfo.isMac     && CpuArch.CURRENT == CpuArch.ARM64  -> MACOS_ARM64
            SystemInfo.isLinux   && CpuArch.CURRENT == CpuArch.X86_64 -> LINUX_X64
            else -> throw RsmRuntimeUnavailable(
                "Unsupported host: os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")}"
            )
        }
    }
}

object NodeRuntimeResolver {
    private const val PLUGIN_DIR_NAME = "rsm-jetbrains"

    /**
     * @return absolute path of the bundled node binary for the current target.
     * @throws RsmRuntimeUnavailable if the binary is not present or not executable.
     */
    fun resolve(): File {
        val target  = NativeTarget.forCurrentHost()
        val plugins = File(PathManager.getPluginsPath()).absoluteFile
        val binary  = plugins
            .resolve(PLUGIN_DIR_NAME)
            .resolve("node-runtimes")
            .resolve(target.id)
            .resolve(target.nodeBinary)
            .absoluteFile

        if (!binary.exists()) {
            throw RsmRuntimeUnavailable("bundled Node binary not present: $binary")
        }
        if (!binary.isFile) {
            throw RsmRuntimeUnavailable("bundled Node path is not a regular file: $binary")
        }
        // POSIX executable bit check (Windows: skip; ACLs govern).
        if (!SystemInfo.isWindows && !Files.isExecutable(binary.toPath())) {
            throw RsmRuntimeUnavailable("bundled Node binary is not executable: $binary")
        }
        return binary
    }
}
