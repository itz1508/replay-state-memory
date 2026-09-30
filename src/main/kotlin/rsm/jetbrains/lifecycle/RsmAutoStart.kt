package rsm.jetbrains.lifecycle

import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.config.RsmHome
import rsm.jetbrains.config.RsmSettingsState
import rsm.jetbrains.host.RsmRuntimeService
import java.io.File

/**
 * Zero-configuration first-run bootstrap.
 */
object RsmAutoStart {

    fun run() {
        val home = RsmHome.ensure()
        seedDemoSourceIfEmpty(home)

        val settings = ApplicationManager.getApplication().getService(RsmSettingsState::class.java)
            ?.data() ?: return
        if (!settings.autoStart) return

        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: return
        val entry = svc.defaultAgentEntry()
        if (!entry.exists()) return

        svc.startFor(
            entry,
            nodeArgs  = svc.nodeArgsFromSettings(settings),
            agentArgs = svc.agentArgsFromSettings(settings),
        )
    }

    private fun seedDemoSourceIfEmpty(home: File) {
        val sources = home.resolve("storage/sources")
        if (sources.exists() && (sources.listFiles()?.isNotEmpty() == true)) return
        sources.mkdirs()
        val demo = sources.resolve("demo.hello")
        if (!demo.exists()) demo.writeText("Hello, RSM!")
    }
}
