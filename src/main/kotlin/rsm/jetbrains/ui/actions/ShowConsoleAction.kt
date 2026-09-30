package rsm.jetbrains.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import rsm.jetbrains.ui.RsmNotifications

/**
 * Frontend action: opens the RSM tool window on the right rail.
 */
class ShowConsoleAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        if (project == null) {
            RsmNotifications.info("RSM Console",
                "Open a project first. Then View \u2192 Tool Windows \u2192 RSM.")
            return
        }
        val tw = ToolWindowManager.getInstance(project).getToolWindow("RSM")
        if (tw != null) {
            tw.show(null)
        } else {
            RsmNotifications.info("RSM Console", "Tool window not registered (unexpected).")
        }
    }
}
