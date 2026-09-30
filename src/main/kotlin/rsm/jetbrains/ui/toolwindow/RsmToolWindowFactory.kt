package rsm.jetbrains.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import rsm.jetbrains.host.RsmRuntimeService
import javax.swing.JPanel
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Builds the four RSM tool window tabs on first activation. Registered in
 * plugin.xml via <toolWindow id="RSM" anchor="right" factoryClass="..."/>.
 *
 * Lazy — the platform only calls createToolWindowContent() when the user first
 * clicks the strip icon or invokes View \u2192 Tool Windows \u2192 RSM.
 */
class RsmToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
            ?: RsmRuntimeService()  // best-effort fallback

        val cf = ContentFactory.getInstance()

        toolWindow.contentManager.addContent(
            cf.createContent(buildRuntimePanel(svc), "Runtime", false)
        )
        toolWindow.contentManager.addContent(
            cf.createContent(buildSourcesPanel(svc), "Sources", false)
        )
        toolWindow.contentManager.addContent(
            cf.createContent(buildTracePanel(svc), "Trace", false)
        )
        toolWindow.contentManager.addContent(
            cf.createContent(buildSettingsPanel(), "Settings", false)
        )
    }

    private fun buildRuntimePanel(svc: RsmRuntimeService): JPanel {
        val tab = RuntimeTab(svc)
        val panel = JPanel(BorderLayout())
        val stateLabel = JLabel("RSM state: ${svc.state().name}")
        val statusLine = JLabel(tab.statusLine())
        val info = JTextArea(
            "Session: ${tab.session().sessionId}\n" +
            "Attached sources: ${tab.session().activeSources}\n" +
            "Total context: ${tab.session().contextBytes} B\n" +
            "Replays this session: ${tab.session().replays}\n"
        ).apply { isEditable = false }
        val top = JPanel()
        top.layout = BorderLayout()
        top.add(stateLabel, BorderLayout.NORTH)
        top.add(statusLine, BorderLayout.CENTER)
        panel.add(top, BorderLayout.NORTH)
        panel.add(JScrollPane(info), BorderLayout.CENTER)
        return panel
    }

    private fun buildSourcesPanel(svc: RsmRuntimeService): JPanel {
        val panel = JPanel(BorderLayout())
        val text = JTextArea().apply { isEditable = false }
        text.text = if (svc.drawer.list().isEmpty())
            "No attached sources yet.\nUse RSM: Register Source (Ctrl/Cmd+Shift+A) to add one."
        else
            svc.drawer.list().joinToString("\n") { e ->
                "${e.displayName} (source_id=${e.sourceId}, ${e.sizeBytes} B, status=${e.lastReplayStatus})"
            }
        panel.add(JScrollPane(text), BorderLayout.CENTER)
        return panel
    }

    private fun buildTracePanel(svc: RsmRuntimeService): JPanel {
        val panel = JPanel(BorderLayout())
        val text = JTextArea().apply { isEditable = false }
        text.text = if (svc.recorder.snapshot().isEmpty())
            "No MCP calls yet. Invoke RSM: Replay Source to record one."
        else
            svc.recorder.snapshot().joinToString("\n") { r ->
                val status = if (r.isFinished()) (if (r.isError) "\u2716" else "\u2714") else "..."
                "$status ${r.tool} runId=${r.runId ?: "-"} callId=${r.callId} duration=${r.durationMs ?: "-"}ms"
            }
        panel.add(JScrollPane(text), BorderLayout.CENTER)
        return panel
    }

    private fun buildSettingsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val text = JTextArea(
            "Full editable settings live at:\n" +
            "  Settings \u2192 Tools \u2192 RSM\n\n" +
            "This tab is a preview; edit values in the main Settings dialog."
        ).apply { isEditable = false }
        panel.add(JScrollPane(text), BorderLayout.CENTER)
        return panel
    }
}
