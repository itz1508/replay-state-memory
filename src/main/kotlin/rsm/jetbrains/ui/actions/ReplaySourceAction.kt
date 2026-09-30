package rsm.jetbrains.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.ui.RsmNotifications

/**
 * Frontend action: replay a previously-registered source_id.
 */
class ReplaySourceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: run { RsmNotifications.error("RSM replay", "service not registered"); return }
        val sid = Messages.showInputDialog(
            e.project,
            "source_id to replay (returned by rsm_register):",
            "RSM: Replay Source",
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (sid.isEmpty()) return
        try {
            val r = svc.replay(sid)
            RsmNotifications.info("RSM replay", "sent, result present=${r["result"] != null}")
        } catch (t: Throwable) {
            RsmNotifications.error("RSM replay failed", t.message ?: t.javaClass.simpleName)
        }
    }
}
