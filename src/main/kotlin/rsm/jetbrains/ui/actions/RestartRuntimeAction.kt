package rsm.jetbrains.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.ui.RsmNotifications

/**
 * Frontend action: user-initiated restart. Resets the auto-restart budget.
 */
class RestartRuntimeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: run {
                RsmNotifications.error("RSM restart failed", "service not registered")
                return
            }
        svc.restart(svc.defaultAgentEntry())
        RsmNotifications.info("RSM runtime restart", "state=${svc.state().name}")
    }
}
