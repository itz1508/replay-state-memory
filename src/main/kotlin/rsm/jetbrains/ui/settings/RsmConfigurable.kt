package rsm.jetbrains.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.FormBuilder
import rsm.jetbrains.config.RsmSettingsState
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Settings \u2192 Tools \u2192 RSM. Registered via
 * <applicationConfigurable parentId="tools" instance="..." displayName="RSM"/>.
 */
class RsmConfigurable : Configurable {

    private val state: RsmSettingsState =
        ApplicationManager.getApplication().getService(RsmSettingsState::class.java)
            ?: RsmSettingsState()

    private var working: RsmSettingsState.Data = state.data().copy()
    private var panel: JPanel? = null

    private lateinit var autoStart: JCheckBox
    private lateinit var autoRestart: JCheckBox
    private lateinit var autoFeed: JCheckBox
    private lateinit var allowAll: JCheckBox
    private lateinit var memoryTier: JComboBox<String>
    private lateinit var logLevel: JComboBox<String>
    private lateinit var sourceStoreSpec: JTextField

    override fun getDisplayName(): String = "RSM"

    override fun createComponent(): JComponent? {
        working = state.data().copy()

        autoStart       = JCheckBox("Auto-start runtime on IDE launch", working.autoStart)
        autoRestart     = JCheckBox("Auto-restart runtime on failure (at-most-once per session)", working.autoRestart)
        autoFeed        = JCheckBox("Auto-feed: clean attached sources after run boundary",     working.autoFeed)
        allowAll        = JCheckBox("Allow all references (testing only, no permission gate)",  working.allowAll)
        memoryTier      = JComboBox(arrayOf("auto", "256mb", "512mb", "1gb")).apply { selectedItem = working.memoryTier }
        logLevel        = JComboBox(arrayOf("INFO", "DEBUG")).apply { selectedItem = working.logLevel }
        sourceStoreSpec = JTextField(working.sourceStoreSpec, 40)

        panel = FormBuilder.createFormBuilder()
            .addComponent(autoStart)
            .addComponent(autoRestart)
            .addComponent(autoFeed)
            .addComponent(allowAll)
            .addLabeledComponent("Memory tier:", memoryTier)
            .addLabeledComponent("Log level:",   logLevel)
            .addLabeledComponent("Source store spec (blank = fs-store under RSM_HOME):", sourceStoreSpec)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel
    }

    override fun isModified(): Boolean {
        val cur = state.data()
        return cur.autoStart       != autoStart.isSelected      ||
               cur.autoRestart     != autoRestart.isSelected    ||
               cur.autoFeed        != autoFeed.isSelected       ||
               cur.allowAll        != allowAll.isSelected       ||
               cur.memoryTier      != (memoryTier.selectedItem as String) ||
               cur.logLevel        != (logLevel.selectedItem as String)   ||
               cur.sourceStoreSpec != sourceStoreSpec.text
    }

    override fun apply() {
        working.autoStart       = autoStart.isSelected
        working.autoRestart     = autoRestart.isSelected
        working.autoFeed        = autoFeed.isSelected
        working.allowAll        = allowAll.isSelected
        working.memoryTier      = memoryTier.selectedItem as String
        working.logLevel        = logLevel.selectedItem as String
        working.sourceStoreSpec = sourceStoreSpec.text
        state.replace(working.copy())
    }

    override fun reset() {
        working = state.data().copy()
        autoStart.isSelected      = working.autoStart
        autoRestart.isSelected    = working.autoRestart
        autoFeed.isSelected       = working.autoFeed
        allowAll.isSelected       = working.allowAll
        memoryTier.selectedItem   = working.memoryTier
        logLevel.selectedItem     = working.logLevel
        sourceStoreSpec.text      = working.sourceStoreSpec
    }
}
