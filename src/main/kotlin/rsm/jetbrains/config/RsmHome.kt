package rsm.jetbrains.config

import rsm.jetbrains.platform.SystemInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Location of the plugin's on-disk state — logs, source-store, exports.
 * IDE settings (auto-start, auto-restart, memory tier, ...) live under
 * PersistentStateComponent, NOT here. See PLANS/RSM_JETBRAINS_0_2_0_PLAN.md §2.5.
 *
 * Resolution order:
 *   1. `RSM_HOME` environment variable (advanced/CI override).
 *   2. Platform default:
 *      - Windows: %APPDATA%\JetBrains\RSM   (fallback %USERPROFILE%\AppData\Roaming\JetBrains\RSM)
 *      - macOS:   ~/Library/Application Support/JetBrains/RSM
 *      - Linux:   $XDG_CONFIG_HOME/JetBrains/RSM (fallback ~/.config/JetBrains/RSM)
 */
object RsmHome {
    private const val VENDOR = "JetBrains"
    private const val PRODUCT = "RSM"

    /**
     * Returns the resolved RSM Home path as an ABSOLUTE File. Guaranteed not to be blank.
     * Does not create any directory — call [ensure] for that.
     */
    @JvmStatic
    fun path(): File {
        val override = System.getenv("RSM_HOME")?.takeIf { it.isNotBlank() }
        if (override != null) return File(override).absoluteFile

        val userHome = System.getProperty("user.home")
            ?: error("user.home is not set")
        val base: File = when {
            SystemInfo.isWindows -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData)
            }
            SystemInfo.isMac -> File(userHome, "Library/Application Support")
            else /* Linux and other POSIX */ -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(userHome, ".config").absolutePath
                File(xdg)
            }
        }
        return base.resolve(VENDOR).resolve(PRODUCT).absoluteFile
    }

    /** Every subdirectory of the tree, relative to `path()`. Keep in sync with the plan §2.5. */
    private val subdirs: List<String> = listOf(
        "config",
        "logs",
        "logs/traces",
        "storage",
        "storage/sources",
        "storage/registrations",
        "storage/cache",
        "state",
        "exports",
        "exports/traces",
    )

    /**
     * Ensures the RSM Home directory tree exists. Idempotent — safe to call on every
     * IDE launch. Returns the resolved root path for chaining. Never throws for
     * pre-existing directories; only throws if a required dir cannot be created
     * (permissions / read-only filesystem).
     */
    @JvmStatic
    fun ensure(): File {
        val root = path()
        val rootPath: Path = root.toPath()
        Files.createDirectories(rootPath)
        for (sub in subdirs) {
            Files.createDirectories(rootPath.resolve(sub))
        }
        return root
    }

    // Well-known children — call after ensure() when you need one of them.
    @JvmStatic fun configDir():        File = path().resolve("config")
    @JvmStatic fun logsDir():          File = path().resolve("logs")
    @JvmStatic fun tracesDir():        File = path().resolve("logs/traces")
    @JvmStatic fun sourcesDir():       File = path().resolve("storage/sources")
    @JvmStatic fun registrationsDir(): File = path().resolve("storage/registrations")
    @JvmStatic fun cacheDir():         File = path().resolve("storage/cache")
    @JvmStatic fun stateDir():         File = path().resolve("state")
    @JvmStatic fun exportsDir():       File = path().resolve("exports")
    @JvmStatic fun exportsTracesDir(): File = path().resolve("exports/traces")
}
