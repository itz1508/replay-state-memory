package rsm.jetbrains.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.ui.RsmNotifications

/**
 * Frontend action: shows the current 5-state runtime status as an INFO balloon.
 * Extends the real com.intellij.openapi.actionSystem.AnAction.
 */
class ShowStatusAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
        val state = svc?.state()?.name ?: "OFF"
        val pid = svc?.pid()
        val extra = if (pid != null) "  ·  pid=$pid" else ""
        RsmNotifications.info("RSM runtime state", state + extra)
    }
}
