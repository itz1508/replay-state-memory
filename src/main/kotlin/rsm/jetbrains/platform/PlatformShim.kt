// rsm.jetbrains.platform — thin adapters over IntelliJ Platform types.
// These stay stable across Platform versions and let the rest of the plugin code
// use short names without importing com.intellij.* everywhere.
package rsm.jetbrains.platform

import java.io.File

// ---- PathManager: delegates to com.intellij.openapi.application.PathManager ----
object PathManager {
    @JvmStatic
    fun getPluginsPath(): String = com.intellij.openapi.application.PathManager.getPluginsPath()
}

// ---- SystemInfo: delegates to com.intellij.openapi.util.SystemInfo ----
object SystemInfo {
    val isWindows: Boolean get() = com.intellij.openapi.util.SystemInfo.isWindows
    val isMac: Boolean     get() = com.intellij.openapi.util.SystemInfo.isMac
    val isLinux: Boolean   get() = com.intellij.openapi.util.SystemInfo.isLinux
}

// ---- CpuArch: mirrors com.intellij.util.system.CpuArch ----
enum class CpuArch { X86_64, ARM64, OTHER;
    companion object {
        val CURRENT: CpuArch by lazy {
            val name = System.getProperty("os.arch").lowercase()
            when {
                name.contains("amd64") || name.contains("x86_64") || name.contains("x64") -> X86_64
                name.contains("aarch64") || name.contains("arm64") -> ARM64
                else -> OTHER
            }
        }
    }
}

// ---- ParentEnvironmentType: mirrors com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType ----
enum class ParentEnvironmentType { SYSTEM, CONSOLE, NONE;
    fun toPlatform(): com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType = when (this) {
        SYSTEM  -> com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.SYSTEM
        CONSOLE -> com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.CONSOLE
        NONE    -> com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.NONE
    }
}

// ---- GeneralCommandLine: delegates to com.intellij.execution.configurations.GeneralCommandLine ----
class GeneralCommandLine {
    private val delegate = com.intellij.execution.configurations.GeneralCommandLine()

    fun withExePath(path: String): GeneralCommandLine { delegate.exePath = path; return this }
    fun withParameters(vararg params: String): GeneralCommandLine { delegate.addParameters(*params); return this }
    fun withParentEnvironmentType(t: ParentEnvironmentType): GeneralCommandLine {
        delegate.withParentEnvironmentType(t.toPlatform())
        return this
    }
    fun withWorkDirectory(dir: File?): GeneralCommandLine { delegate.workDirectory = dir; return this }

    fun getExePath(): String? = delegate.exePath
    fun getParameters(): List<String> = delegate.parametersList.parameters.toList()
    fun getParentEnvironmentType(): ParentEnvironmentType = when (delegate.parentEnvironmentType) {
        com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.SYSTEM  -> ParentEnvironmentType.SYSTEM
        com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.CONSOLE -> ParentEnvironmentType.CONSOLE
        com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.NONE    -> ParentEnvironmentType.NONE
    }

    fun createProcess(): Process = delegate.createProcess()
}

// ---- NotificationsBus: delegates to com.intellij.notification.Notifications.Bus ----
object NotificationsBus {
    @JvmStatic
    fun notify(groupId: String, title: String, content: String, type: String) {
        val ntype = when (type.uppercase()) {
            "ERROR"   -> com.intellij.notification.NotificationType.ERROR
            "WARNING" -> com.intellij.notification.NotificationType.WARNING
            else      -> com.intellij.notification.NotificationType.INFORMATION
        }
        val notification = com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup(groupId)
            .createNotification(title, content, ntype)
        com.intellij.notification.Notifications.Bus.notify(notification)
    }
}

// ---- Logger: delegates to com.intellij.openapi.diagnostic.Logger ----
class Logger(private val delegate: com.intellij.openapi.diagnostic.Logger) {
    fun info(msg: String)  { delegate.info(msg) }
    fun warn(msg: String)  { delegate.warn(msg) }
    fun error(msg: String, t: Throwable? = null) {
        if (t != null) delegate.error(msg, t) else delegate.error(msg)
    }
    companion object {
        @JvmStatic
        fun getInstance(category: String): Logger =
            Logger(com.intellij.openapi.diagnostic.Logger.getInstance(category))
    }
}
