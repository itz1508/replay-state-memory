package rsm.jetbrains.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rsm.jetbrains.host.RsmRuntimeService
import rsm.jetbrains.ui.RsmNotifications

/**
 * Frontend action: quick-register a reference via MCP.
 */
class RegisterSourceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: run { RsmNotifications.error("RSM register", "service not registered"); return }
        val ref = Messages.showInputDialog(
            e.project,
            "Reference to register (opaque string, e.g. \"demo.hello\"):",
            "RSM: Register Source",
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (ref.isEmpty()) return
        try {
            val r = svc.register(buildJsonObject { put("reference", ref) })
            RsmNotifications.info("RSM register", "sent, result present=${r["result"] != null}")
        } catch (t: Throwable) {
            RsmNotifications.error("RSM register failed", t.message ?: t.javaClass.simpleName)
        }
    }
}
