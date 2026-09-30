package rsm.jetbrains.lifecycle

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.host.RsmRuntimeService

/**
 * Called by the IntelliJ Platform right before the IDE process exits. We stop
 * the Node sidecar cleanly (SIGTERM \u2192 2 s \u2192 SIGKILL) so the child does not
 * outlive its parent. Registered via <applicationListener topic="AppLifecycleListener"/>
 * in plugin.xml.
 */
class RsmShutdownListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: return
        try { svc.stop() } catch (_: Throwable) { /* best effort */ }
    }
}
