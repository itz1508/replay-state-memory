package rsm.jetbrains.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.config.RsmSettingsState

/**
 * Settings tab — read/write mirror of the same PersistentStateComponent used by
 * Settings \u2192 Tools \u2192 RSM. Uses a working copy pattern so Apply/Reset behave
 * predictably.
 */
class SettingsTab {

    private val state: RsmSettingsState =
        ApplicationManager.getApplication().getService(RsmSettingsState::class.java)
            ?: RsmSettingsState()

    /** Live working copy — the tab's inputs bind to this. */
    var working: RsmSettingsState.Data = state.data().copy()
        private set

    fun apply() {
        val next = working.copy()
        state.replace(next)
    }

    fun reset() {
        working = state.data().copy()
    }

    fun isModified(): Boolean {
        val cur = state.data()
        return cur.autoStart          != working.autoStart          ||
               cur.autoRestart        != working.autoRestart        ||
               cur.memoryTier         != working.memoryTier         ||
               cur.sourceStoreSpec    != working.sourceStoreSpec    ||
               cur.allowList          != working.allowList          ||
               cur.allowAll           != working.allowAll           ||
               cur.autoFeed           != working.autoFeed           ||
               cur.traceRetentionDays != working.traceRetentionDays ||
               cur.logLevel           != working.logLevel           ||
               cur.folderMaxDepth     != working.folderMaxDepth     ||
               cur.folderMaxFiles     != working.folderMaxFiles
    }
}
